package tw.com.sylong.carktv.media;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import tw.com.sylong.carktv.data.Song;

public class LocalMediaScanner {
    private static final int MAX_FILES = 20000;
    private static final int MAX_DEPTH = 8;

    public static ArrayList<Song> scan(Context context) {
        ArrayList<Song> songs = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (File root : roots(context)) {
            scanRoot(root, songs, visited);
            if (songs.size() >= MAX_FILES) {
                break;
            }
        }
        return songs;
    }

    private static ArrayList<File> roots(Context context) {
        ArrayList<File> roots = new ArrayList<>();
        add(roots, Environment.getExternalStorageDirectory());
        add(roots, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
        add(roots, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
        add(roots, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        add(roots, new File(Environment.getExternalStorageDirectory(), "KTV"));
        add(roots, new File(Environment.getExternalStorageDirectory(), "Movies/KTV"));
        add(roots, new File(Environment.getExternalStorageDirectory(), "Download/KTV"));

        File[] externalFiles = context.getExternalFilesDirs(null);
        if (externalFiles != null) {
            for (File file : externalFiles) {
                if (file != null) {
                    add(roots, file);
                }
            }
        }

        File storage = new File("/storage");
        File[] storageItems = storage.listFiles();
        if (storageItems != null) {
            for (File item : storageItems) {
                String name = item.getName().toLowerCase(Locale.ROOT);
                if (!name.equals("self") && !name.equals("emulated")) {
                    add(roots, item);
                }
            }
        }
        return roots;
    }

    private static void add(ArrayList<File> roots, File file) {
        if (file != null && file.exists() && file.canRead()) {
            roots.add(file);
        }
    }

    private static void scanRoot(File root, ArrayList<Song> songs, Set<String> visited) {
        ArrayDeque<Entry> stack = new ArrayDeque<>();
        stack.push(new Entry(root, 0));
        while (!stack.isEmpty() && songs.size() < MAX_FILES) {
            Entry entry = stack.pop();
            File file = entry.file;
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (Exception ignored) {
                path = file.getAbsolutePath();
            }
            if (!visited.add(path)) {
                continue;
            }

            if (file.isDirectory()) {
                if (entry.depth >= MAX_DEPTH) {
                    continue;
                }
                File[] children = file.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    stack.push(new Entry(child, entry.depth + 1));
                }
            } else if (isMedia(file)) {
                songs.add(songFromFile(file));
            }
        }
    }

    private static Song songFromFile(File file) {
        Song song = new Song();
        String base = stripExtension(file.getName()).trim();
        String[] parts = splitName(base);
        song.artist = parts[0];
        song.title = parts[1];
        song.catalogKey = sha1(file.getAbsolutePath());
        song.number = "";
        song.language = "";
        song.category = categoryFor(file);
        song.mediaUri = Uri.fromFile(file).toString();
        song.lyricUri = findLyric(file);
        song.source = "local";
        song.license = "";
        song.updatedAt = String.valueOf(file.lastModified());
        return song;
    }

    private static String[] splitName(String base) {
        String normalized = base.replace("＿", "_").replace("－", "-");
        String[] separators = new String[]{" - ", "-", "_"};
        for (String separator : separators) {
            int index = normalized.indexOf(separator);
            if (index > 0 && index < normalized.length() - separator.length()) {
                String left = normalized.substring(0, index).trim();
                String right = normalized.substring(index + separator.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) {
                    return new String[]{left, right};
                }
            }
        }
        return new String[]{"", base};
    }

    private static String findLyric(File media) {
        File parent = media.getParentFile();
        if (parent == null) {
            return "";
        }
        String base = stripExtension(media.getName());
        String[] names = new String[]{base + ".lrc", base + ".srt", base + ".txt"};
        for (String name : names) {
            File lyric = new File(parent, name);
            if (lyric.exists() && lyric.canRead()) {
                return Uri.fromFile(lyric).toString();
            }
        }
        return "";
    }

    private static boolean isMedia(File file) {
        if (file.length() < 64 * 1024) {
            return false;
        }
        String ext = extension(file.getName());
        return ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi") ||
                ext.equals("mov") || ext.equals("m4v") || ext.equals("webm") ||
                ext.equals("mp3") || ext.equals("m4a") || ext.equals("wav") ||
                ext.equals("flac") || ext.equals("ogg") || ext.equals("kar") ||
                ext.equals("mid");
    }

    private static String categoryFor(File file) {
        String ext = extension(file.getName());
        if (ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi") ||
                ext.equals("mov") || ext.equals("m4v") || ext.equals("webm")) {
            return "Video";
        }
        return "Audio";
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return String.valueOf(value.hashCode());
        }
    }

    private static class Entry {
        final File file;
        final int depth;

        Entry(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }
}

