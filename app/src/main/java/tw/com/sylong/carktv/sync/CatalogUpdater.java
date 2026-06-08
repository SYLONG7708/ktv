package tw.com.sylong.carktv.sync;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import tw.com.sylong.carktv.data.Song;
import tw.com.sylong.carktv.data.SongCatalogDb;

public class CatalogUpdater {
    public static final String DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/SYLONG7708/ktv/main/catalog/catalog.json";

    private final SongCatalogDb db;
    private final String catalogUrl;

    public CatalogUpdater(Context context) {
        db = new SongCatalogDb(context);
        catalogUrl = db.getMeta("catalog_url", DEFAULT_CATALOG_URL);
    }

    public interface Callback {
        void onStatus(String status);

        void onComplete(int version, int count, boolean changed);

        void onError(Exception error);
    }

    public void updateAsync(boolean force, Callback callback) {
        Thread thread = new Thread(() -> {
            try {
                update(force, callback);
            } catch (Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        }, "catalog-update");
        thread.start();
    }

    public void update(boolean force, Callback callback) throws IOException, JSONException {
        notifyStatus(callback, "檢查雲端歌庫");
        JSONObject root = new JSONObject(downloadString(catalogUrl));
        int version = root.optInt("version", 0);
        String updatedAt = root.optString("updatedAt", nowIso());
        int localVersion = db.getCloudVersion();

        if (!force && version > 0 && version <= localVersion) {
            db.setLastSyncSummary("雲端 v" + localVersion + " 已是最新");
            if (callback != null) {
                callback.onComplete(localVersion, db.countSongs(), false);
            }
            return;
        }

        JSONArray songsJson;
        String songsUrl = root.optString("songsUrl", "");
        if (!Song.isBlank(songsUrl)) {
            String resolved = resolveUrl(catalogUrl, songsUrl);
            notifyStatus(callback, "下載歌庫清單");
            String songPayload = downloadString(resolved);
            Object parsed = new org.json.JSONTokener(songPayload).nextValue();
            if (parsed instanceof JSONArray) {
                songsJson = (JSONArray) parsed;
            } else {
                songsJson = ((JSONObject) parsed).optJSONArray("songs");
            }
        } else {
            songsJson = root.optJSONArray("songs");
        }

        if (songsJson == null) {
            songsJson = new JSONArray();
        }

        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < songsJson.length(); i++) {
            JSONObject item = songsJson.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Song song = parseSong(item, updatedAt);
            if (!Song.isBlank(song.title)) {
                songs.add(song);
            }
        }

        notifyStatus(callback, "寫入本機歌庫");
        db.replaceCloudSongs(songs, version, updatedAt);
        if (callback != null) {
            callback.onComplete(version, songs.size(), true);
        }
    }

    private static Song parseSong(JSONObject item, String updatedAt) {
        Song song = new Song();
        song.number = item.optString("number", item.optString("id", ""));
        song.catalogKey = item.optString("catalogKey", song.number);
        song.title = item.optString("title", item.optString("name", ""));
        song.artist = item.optString("artist", item.optString("singer", ""));
        song.language = item.optString("language", "");
        song.category = item.optString("category", item.optString("type", ""));
        song.mediaUri = item.optString("mediaUrl", item.optString("mediaUri", item.optString("url", "")));
        song.lyricUri = item.optString("lyricUrl", item.optString("lyricUri", ""));
        song.license = item.optString("license", "");
        song.updatedAt = item.optString("updatedAt", updatedAt);
        song.source = "cloud";
        if (Song.isBlank(song.catalogKey)) {
            song.catalogKey = song.artist + "-" + song.title + "-" + song.mediaUri;
        }
        return song;
    }

    private static void notifyStatus(Callback callback, String status) {
        if (callback != null) {
            callback.onStatus(status);
        }
    }

    private static String resolveUrl(String base, String child) throws IOException {
        URL baseUrl = new URL(base);
        return new URL(baseUrl, child).toString();
    }

    private static String downloadString(String url) throws IOException {
        byte[] bytes = downloadBytes(url);
        InputStream input = new ByteArrayInputStream(bytes);
        if (url.toLowerCase(Locale.ROOT).endsWith(".gz")) {
            input = new GZIPInputStream(input);
        }
        String text = new String(readAll(input, 30 * 1024 * 1024), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    private static byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "LongCarKTV/1.0");
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            return readAll(input, 30 * 1024 * 1024);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Catalog payload is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
