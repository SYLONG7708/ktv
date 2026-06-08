package tw.com.sylong.carktv.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SongCatalogDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "long_ktv_catalog.db";
    private static final int DB_VERSION = 1;

    public SongCatalogDb(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE songs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "catalog_key TEXT NOT NULL," +
                "number TEXT," +
                "title TEXT NOT NULL," +
                "artist TEXT," +
                "language TEXT," +
                "category TEXT," +
                "media_uri TEXT," +
                "lyric_uri TEXT," +
                "source TEXT NOT NULL," +
                "license TEXT," +
                "updated_at TEXT," +
                "favorite INTEGER DEFAULT 0," +
                "search_text TEXT," +
                "UNIQUE(source, catalog_key) ON CONFLICT REPLACE)");
        db.execSQL("CREATE INDEX idx_songs_search ON songs(search_text)");
        db.execSQL("CREATE INDEX idx_songs_source ON songs(source)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS songs");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    public int getCloudVersion() {
        String value = getMeta("cloud_version", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public void setCloudVersion(int version) {
        setMeta("cloud_version", String.valueOf(version));
    }

    public String getLastSyncSummary() {
        return getMeta("last_sync_summary", "");
    }

    public void setLastSyncSummary(String summary) {
        setMeta("last_sync_summary", summary);
    }

    public String getMeta(String key, String fallback) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("meta", new String[]{"value"}, "key=?",
                new String[]{key}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return fallback;
    }

    public void setMeta(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceCloudSongs(List<Song> songs, int version, String updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("songs", "source=?", new String[]{"cloud"});
            for (Song song : songs) {
                song.source = "cloud";
                if (Song.isBlank(song.updatedAt)) {
                    song.updatedAt = updatedAt;
                }
                db.insertWithOnConflict("songs", null, valuesFor(song), SQLiteDatabase.CONFLICT_REPLACE);
            }
            setMetaInTransaction(db, "cloud_version", String.valueOf(version));
            setMetaInTransaction(db, "last_sync_summary", "雲端 v" + version + "，" + songs.size() + " 首");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int upsertLocalSongs(List<Song> songs) {
        SQLiteDatabase db = getWritableDatabase();
        int changed = 0;
        db.beginTransaction();
        try {
            for (Song song : songs) {
                song.source = "local";
                long result = db.insertWithOnConflict("songs", null, valuesFor(song), SQLiteDatabase.CONFLICT_REPLACE);
                if (result != -1) {
                    changed++;
                }
            }
            setMetaInTransaction(db, "last_local_scan", String.valueOf(System.currentTimeMillis()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    public List<Song> search(String query, int limit) {
        String normalized = normalize(query);
        String order = "CASE source WHEN 'local' THEN 0 ELSE 1 END, favorite DESC, artist, title";
        String sql;
        String[] args;
        if (normalized.isEmpty()) {
            sql = "SELECT * FROM songs ORDER BY " + order + " LIMIT ?";
            args = new String[]{String.valueOf(limit)};
        } else {
            sql = "SELECT * FROM songs WHERE search_text LIKE ? ORDER BY " + order + " LIMIT ?";
            args = new String[]{"%" + normalized + "%", String.valueOf(limit)};
        }

        SQLiteDatabase db = getReadableDatabase();
        ArrayList<Song> songs = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                songs.add(fromCursor(cursor));
            }
        }
        return songs;
    }

    public int countSongs() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM songs", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static void setMetaInTransaction(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static ContentValues valuesFor(Song song) {
        ContentValues values = new ContentValues();
        values.put("catalog_key", fallback(song.catalogKey, stableKey(song)));
        values.put("number", clean(song.number));
        values.put("title", fallback(song.title, "Untitled"));
        values.put("artist", clean(song.artist));
        values.put("language", clean(song.language));
        values.put("category", clean(song.category));
        values.put("media_uri", clean(song.mediaUri));
        values.put("lyric_uri", clean(song.lyricUri));
        values.put("source", fallback(song.source, "cloud"));
        values.put("license", clean(song.license));
        values.put("updated_at", clean(song.updatedAt));
        values.put("favorite", song.favorite ? 1 : 0);
        values.put("search_text", normalize(song.number + " " + song.title + " " + song.artist + " " +
                song.language + " " + song.category));
        return values;
    }

    private static Song fromCursor(Cursor cursor) {
        Song song = new Song();
        song.id = getLong(cursor, "id");
        song.catalogKey = getString(cursor, "catalog_key");
        song.number = getString(cursor, "number");
        song.title = getString(cursor, "title");
        song.artist = getString(cursor, "artist");
        song.language = getString(cursor, "language");
        song.category = getString(cursor, "category");
        song.mediaUri = getString(cursor, "media_uri");
        song.lyricUri = getString(cursor, "lyric_uri");
        song.source = getString(cursor, "source");
        song.license = getString(cursor, "license");
        song.updatedAt = getString(cursor, "updated_at");
        song.favorite = getLong(cursor, "favorite") == 1;
        return song;
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getLong(index) : 0L;
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : "";
    }

    private static String stableKey(Song song) {
        if (!Song.isBlank(song.number)) {
            return song.number;
        }
        return normalize(song.artist + "-" + song.title + "-" + song.mediaUri);
    }

    private static String fallback(String value, String fallback) {
        return Song.isBlank(value) ? fallback : value.trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("　", " ")
                .replace("_", " ")
                .replace("-", " ")
                .trim();
    }
}
