param(
    [Parameter(Mandatory = $true)]
    [string] $SourceRoot,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [string] $RawRef = "master"
)

$ErrorActionPreference = "Stop"

function Read-Tags {
    param([string] $Path)
    $tags = [ordered]@{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $tags
    }
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^#([^:]+):(.*)$') {
            $tags[$matches[1].ToUpperInvariant()] = $matches[2].Trim()
        }
    }
    return $tags
}

function Get-RawUrl {
    param([string] $RelativePath)
    $segments = $RelativePath -split '[\\/]'
    $encoded = $segments | ForEach-Object { [Uri]::EscapeDataString($_) }
    return "https://raw.githubusercontent.com/UltraStar-Deluxe/songs/$RawRef/" + ($encoded -join "/")
}

function Get-RelativePathCompat {
    param(
        [string] $BasePath,
        [string] $TargetPath
    )
    $baseFull = [IO.Path]::GetFullPath($BasePath).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $targetFull = [IO.Path]::GetFullPath($TargetPath)
    $baseUri = New-Object Uri($baseFull)
    $targetUri = New-Object Uri($targetFull)
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', [IO.Path]::DirectorySeparatorChar)
}

function Get-LicenseSummary {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return "Creative Commons / Public Domain metadata required by source repository"
    }
    $line = Get-Content -LiteralPath $Path -Encoding UTF8 |
        Where-Object { $_ -match '^\s*License:\s*(.+)$' } |
        Select-Object -First 1
    if ($line -match '^\s*License:\s*(.+)$') {
        return $matches[1].Trim()
    }
    return "See license.txt in source song folder"
}

function Resolve-MediaFile {
    param(
        [string] $Folder,
        [hashtable] $Tags,
        [bool] $PreferInstrumental
    )
    if ($PreferInstrumental) {
        foreach ($name in @("instrumental.mp3", "instrumental.ogg", "instrumental.wav", "instrumental.m4a")) {
            $path = Join-Path $Folder $name
            if (Test-Path -LiteralPath $path) {
                return $path
            }
        }
    }
    if ($Tags.Contains("MP3")) {
        $path = Join-Path $Folder $Tags["MP3"]
        if (Test-Path -LiteralPath $path) {
            return $path
        }
    }
    foreach ($name in @("audio.mp3", "audio.ogg", "audio.wav", "audio.m4a")) {
        $path = Join-Path $Folder $name
        if (Test-Path -LiteralPath $path) {
            return $path
        }
    }
    return $null
}

$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$songFiles = Get-ChildItem -LiteralPath $source -Recurse -Filter song.txt -File | Sort-Object FullName
$songs = New-Object System.Collections.Generic.List[object]
$index = 1

foreach ($songFile in $songFiles) {
    $folder = $songFile.Directory.FullName
    $songTags = Read-Tags -Path $songFile.FullName
    $instrumentalTxt = Join-Path $folder "instrumental.txt"
    $hasInstrumentalChart = Test-Path -LiteralPath $instrumentalTxt
    $lyricPath = if ($hasInstrumentalChart) { $instrumentalTxt } else { $songFile.FullName }
    $lyricTags = Read-Tags -Path $lyricPath
    $mediaPath = Resolve-MediaFile -Folder $folder -Tags $lyricTags -PreferInstrumental:$hasInstrumentalChart
    if ($null -eq $mediaPath) {
        continue
    }

    $title = if ($songTags.Contains("TITLE")) { $songTags["TITLE"] } else { $songFile.Directory.Name }
    $artist = if ($songTags.Contains("ARTIST")) { $songTags["ARTIST"] } else { "" }
    $language = if ($songTags.Contains("LANGUAGE")) { $songTags["LANGUAGE"] } else { "Unknown" }
    $licensePath = Join-Path $folder "license.txt"
    $relativeMedia = Get-RelativePathCompat -BasePath $source -TargetPath $mediaPath
    $relativeLyric = Get-RelativePathCompat -BasePath $source -TargetPath $lyricPath
    $relativeLicense = Get-RelativePathCompat -BasePath $source -TargetPath $licensePath
    $number = "CC{0:D4}" -f $index
    $mode = if ($hasInstrumentalChart) { "Instrumental" } else { "Singalong" }

    $songs.Add([ordered]@{
        number = $number
        catalogKey = "ultrastar-cc-$number"
        title = $title
        artist = $artist
        language = $language
        category = "UltraStar Creative Commons - $mode"
        mediaUrl = Get-RawUrl -RelativePath $relativeMedia
        lyricUrl = Get-RawUrl -RelativePath $relativeLyric
        license = Get-LicenseSummary -Path $licensePath
        source = "UltraStar-Deluxe/songs"
        sourceUrl = Get-RawUrl -RelativePath $relativeLyric
        licenseUrl = Get-RawUrl -RelativePath $relativeLicense
    })
    $index++
}

$payload = [ordered]@{
    version = 1
    updatedAt = (Get-Date -Format "yyyy-MM-dd")
    source = "https://github.com/UltraStar-Deluxe/songs"
    sourceRef = $RawRef
    note = "Only entries with a local audio file and license.txt in UltraStar-Deluxe/songs are included."
    songs = $songs
}

$outputDir = Split-Path -Parent $OutputPath
if ($outputDir) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}
$json = $payload | ConvertTo-Json -Depth 6
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutputPath, $json + [Environment]::NewLine, $utf8NoBom)
Write-Host "Wrote $($songs.Count) playable songs to $OutputPath"
