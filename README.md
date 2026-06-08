# Long Car KTV

車載安卓機使用的 KTV 點歌 App。第一版重點是「可更新歌庫索引、本地快取、自動偵測本地/USB 媒體、橫向車機 UI、APK 可雲端建置」。

## Cloud Catalog

App 預設讀取：

```text
https://raw.githubusercontent.com/SYLONG7708/ktv/main/catalog/catalog.json
```

更新歌庫時修改 `catalog/catalog.json` 的 `version` 與 `songs`，推上 GitHub 後，車機端會在啟動與排程同步時下載新索引。

大型 MV、伴奏、音訊檔不建議放 GitHub。建議 `mediaUrl` 指向合法授權的 HTTPS/CDN/NAS 來源，或讓 App 自動掃描車機本地與 USB 儲存。

## Catalog Format

```json
{
  "version": 2,
  "updatedAt": "2026-06-08",
  "songs": [
    {
      "number": "100001",
      "title": "Song title",
      "artist": "Singer",
      "language": "Mandarin",
      "category": "Pop",
      "mediaUrl": "https://example.com/song.mp4",
      "lyricUrl": "https://example.com/song.lrc",
      "license": "licensed"
    }
  ]
}
```

`catalog.json` 也可以只放版本與外部歌單：

```json
{
  "version": 3,
  "songsUrl": "songs.json"
}
```

## Build

```powershell
$env:JAVA_HOME='D:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

APK 位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

