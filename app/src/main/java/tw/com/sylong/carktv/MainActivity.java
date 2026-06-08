package tw.com.sylong.carktv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import tw.com.sylong.carktv.data.Song;
import tw.com.sylong.carktv.data.SongCatalogDb;
import tw.com.sylong.carktv.media.LocalMediaScanner;
import tw.com.sylong.carktv.sync.CatalogSyncJobService;
import tw.com.sylong.carktv.sync.CatalogUpdater;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 7708;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Song> visibleSongs = new ArrayList<>();
    private final ArrayList<Song> queue = new ArrayList<>();
    private final ArrayList<String> visibleRows = new ArrayList<>();
    private final ArrayList<String> queueRows = new ArrayList<>();

    private SongCatalogDb db;
    private CatalogUpdater updater;
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView nowPlayingView;
    private TextView statusView;
    private EditText searchBox;
    private ArrayAdapter<String> songAdapter;
    private ArrayAdapter<String> queueAdapter;
    private Button playPauseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUi();

        db = new SongCatalogDb(this);
        updater = new CatalogUpdater(this);
        player = new ExoPlayer.Builder(this).build();

        buildUi();
        CatalogSyncJobService.schedule(this);
        requestMediaPermissions();
        refreshSongs("");
        syncCatalog(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void buildUi() {
        int bg = color("#111418");
        int panel = color("#1C2229");
        int panelAlt = color("#252D35");
        int text = color("#F6F8FA");
        int muted = color("#AEB7C2");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        setContentView(root);

        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setPadding(0, 0, dp(10), 0);
        root.addView(stage, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f));

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setBackgroundColor(Color.BLACK);
        stage.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nowPlayingView = label("待機", 18, text, Gravity.CENTER_VERTICAL);
        nowPlayingView.setBackgroundColor(panel);
        nowPlayingView.setPadding(dp(14), 0, dp(14), 0);
        stage.addView(nowPlayingView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(10), dp(10), dp(10), dp(10));
        side.setBackgroundColor(panel);
        root.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        side.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView title = label("Long KTV", 22, text, Gravity.CENTER_VERTICAL);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView songCountView = label(String.valueOf(db.countSongs()), 18, muted, Gravity.CENTER);
        songCountView.setId(View.generateViewId());
        header.addView(songCountView, new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.MATCH_PARENT));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setTextColor(text);
        searchBox.setHintTextColor(muted);
        searchBox.setTextSize(20);
        searchBox.setHint("搜尋歌名 / 歌手 / 編號");
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackgroundColor(panelAlt);
        side.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        songAdapter = rowAdapter(visibleRows);
        ListView songList = new ListView(this);
        songList.setDividerHeight(1);
        songList.setCacheColorHint(Color.TRANSPARENT);
        songList.setBackgroundColor(bg);
        songList.setAdapter(songAdapter);
        side.addView(songList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f));

        TextView queueTitle = label("已點歌曲", 17, muted, Gravity.CENTER_VERTICAL);
        queueTitle.setPadding(dp(4), 0, 0, 0);
        side.addView(queueTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        queueAdapter = rowAdapter(queueRows);
        ListView queueList = new ListView(this);
        queueList.setDividerHeight(1);
        queueList.setCacheColorHint(Color.TRANSPARENT);
        queueList.setBackgroundColor(bg);
        queueList.setAdapter(queueAdapter);
        side.addView(queueList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.8f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, 0);
        side.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        playPauseButton = button("播放");
        Button nextButton = button("切歌");
        Button clearButton = button("清單");
        controls.addView(playPauseButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        controls.addView(nextButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        controls.addView(clearButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(0, dp(8), 0, 0);
        side.addView(tools, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        Button syncButton = button("同步");
        Button scanButton = button("偵測");
        Button forceButton = button("更新");
        tools.addView(syncButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tools.addView(scanButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tools.addView(forceButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        statusView = label(db.getLastSyncSummary(), 14, muted, Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(4), 0, dp(4), 0);
        side.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshSongs(s.toString());
                songCountView.setText(String.valueOf(db.countSongs()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        songList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleSongs.size()) {
                addToQueue(visibleSongs.get(position), true);
            }
        });
        songList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleSongs.size()) {
                playNow(visibleSongs.get(position));
            }
            return true;
        });
        queueList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < queue.size()) {
                Song song = queue.remove(position);
                refreshQueue();
                playNow(song);
            }
        });
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        nextButton.setOnClickListener(v -> playNext());
        clearButton.setOnClickListener(v -> {
            queue.clear();
            refreshQueue();
            status("已清空已點歌曲");
        });
        syncButton.setOnClickListener(v -> syncCatalog(false));
        forceButton.setOnClickListener(v -> syncCatalog(true));
        scanButton.setOnClickListener(v -> scanLocalMedia());
    }

    private void refreshSongs(String query) {
        visibleSongs.clear();
        visibleSongs.addAll(db.search(query, 500));
        visibleRows.clear();
        for (Song song : visibleSongs) {
            String row = song.displayName();
            if (!song.hasPlayableMedia()) {
                row += "  --";
            }
            visibleRows.add(row);
        }
        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
    }

    private void addToQueue(Song song, boolean autoStart) {
        queue.add(song);
        refreshQueue();
            status("已加入：" + song.title);
        if (autoStart && !player.isPlaying() && player.getMediaItemCount() == 0) {
            playNext();
        }
    }

    private void refreshQueue() {
        queueRows.clear();
        for (int i = 0; i < queue.size(); i++) {
            queueRows.add((i + 1) + ". " + queue.get(i).displayName());
        }
        if (queueAdapter != null) {
            queueAdapter.notifyDataSetChanged();
        }
    }

    private void playNext() {
        if (queue.isEmpty()) {
            status("已點歌曲為空");
            return;
        }
        Song song = queue.remove(0);
        refreshQueue();
        playNow(song);
    }

    private void playNow(Song song) {
        if (!song.hasPlayableMedia()) {
            status("尚未綁定媒體：" + song.title);
            Toast.makeText(this, "尚未綁定影片、音訊或本地檔案", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = toUri(song.mediaUri);
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            player.play();
            nowPlayingView.setText(song.displayName());
            playPauseButton.setText("暫停");
            status("播放中");
        } catch (Exception error) {
            status(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Uri toUri(String value) {
        if (value.startsWith("/") || value.matches("^[A-Za-z]:\\\\.*")) {
            return Uri.fromFile(new File(value));
        }
        return Uri.parse(value);
    }

    private void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
            playPauseButton.setText("播放");
        } else {
            if (player.getMediaItemCount() == 0 && !queue.isEmpty()) {
                playNext();
            } else {
                player.play();
                playPauseButton.setText("暫停");
            }
        }
    }

    private void syncCatalog(boolean force) {
        status(force ? "強制更新歌庫" : "同步歌庫");
        updater.updateAsync(force, new CatalogUpdater.Callback() {
            @Override
            public void onStatus(String status) {
                mainHandler.post(() -> status(status));
            }

            @Override
            public void onComplete(int version, int count, boolean changed) {
                mainHandler.post(() -> {
                    status((changed ? "已更新" : "已是最新") + " 雲端 v" + version + " / " + count + " 首");
                    refreshSongs(searchBox.getText().toString());
                });
            }

            @Override
            public void onError(Exception error) {
                mainHandler.post(() -> status("同步失敗：" + error.getMessage()));
            }
        });
    }

    private void scanLocalMedia() {
        status("偵測本地與 USB 媒體");
        Thread thread = new Thread(() -> {
            List<Song> songs = LocalMediaScanner.scan(this);
            int changed = db.upsertLocalSongs(songs);
            mainHandler.post(() -> {
                status("已偵測 " + changed + " 首本地歌曲");
                refreshSongs(searchBox.getText().toString());
            });
        }, "local-media-scan");
        thread.start();
    }

    private void requestMediaPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            scanLocalMedia();
            return;
        }
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (permissions.isEmpty()) {
            scanLocalMedia();
        } else {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_MEDIA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA) {
            scanLocalMedia();
        }
    }

    private ArrayAdapter<String> rowAdapter(ArrayList<String> rows) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(color("#F6F8FA"));
                view.setTextSize(18);
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setMinHeight(dp(48));
                view.setPadding(dp(12), 0, dp(8), 0);
                view.setSingleLine(true);
                view.setBackgroundColor(position % 2 == 0 ? color("#151A20") : color("#1A2027"));
                return view;
            }
        };
    }

    private TextView label(String value, int sp, int color, int gravity) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setSingleLine(true);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(17);
        button.setTextColor(color("#F6F8FA"));
        button.setBackgroundColor(color("#00A88F"));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private void status(String value) {
        if (statusView != null) {
            statusView.setText(value == null ? "" : value);
        }
    }

    private void hideSystemUi() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }
}
