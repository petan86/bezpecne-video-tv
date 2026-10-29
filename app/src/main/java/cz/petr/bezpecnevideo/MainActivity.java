package cz.petr.bezpecnevideo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(20, 33, 61);
    private static final int ORANGE = Color.rgb(252, 163, 17);
    private static final int DARK = Color.rgb(14, 16, 21);
    private static final int SURFACE = Color.rgb(28, 31, 38);
    private static final String SLIVKI_CHANNEL = "UCAugiH1MZcHcsbnWv31JrOg";
    private static final String PAW_PATROL_CZECH_CHANNEL = "UCSVSJ1OCSv5ClFgKFTHIDew";
    private static final String CRUNCHLABS_CHANNEL = "UCY1kMZp36IQSyNx_9h4mpCg";
    private static final String EVERY_BRICK_CHANNEL = "UC_F-J5mjvV80iU9NVz32alg";
    private static final String KIDIBLI_CHANNEL = "UCUmv8pEGQInRObxBiB7ZMsg";
    private VideoStore store;
    private WatchHistory history;
    private VideoGallery currentGallery;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private PlaybackSession playbackSession;
    private long playbackGeneration;
    private final Runnable progressPoll = new Runnable() {
        @Override public void run() {
            if (webView == retainedPlayer && playbackSession != null) {
                captureProgress(false);
                playbackHandler.postDelayed(this, 5000);
            }
        }
    };
    private String activeProfile;
    private String activeChannelFilter;
    private WebView webView;
    private WebView retainedPlayer;
    private View retainedLibrary;
    private String requestedVideo;
    private final ChannelCatalog catalog = new ChannelCatalog();
    private final ExecutorService channelExecutor = Executors.newFixedThreadPool(3);
    private final Map<String, List<VideoStore.Video>> channelCache = new ConcurrentHashMap<>();
    private final Map<String, Long> channelCacheTime = new ConcurrentHashMap<>();
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean searchChoiceOpen;
    private PhoneVideoServer phoneServer;
    private boolean phoneScreen;
    private Object phoneRequest;
    private Runnable phoneTimeout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new VideoStore(this);
        history = new WatchHistory(this);
        root = new FrameLayout(this);
        setContentView(root);
        warmUpYouTube();
        showProfilePicker();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void warmUpYouTube() {
        WebView warmup = new WebView(this);
        warmup.getSettings().setJavaScriptEnabled(true);
        warmup.getSettings().setDomStorageEnabled(true);
        warmup.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        warmup.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.stopLoading();
                view.destroy();
            }
        });
        warmup.loadUrl("https://www.youtube.com/generate_204");
    }

    private void showProfilePicker() {
        currentGallery = null;
        activeProfile = null;
        LinearLayout page = page();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(title("Kdo se bude dívat?", 30));
        page.addView(space(28));
        page.addView(profileButton("🦊", "Matýsek", Color.rgb(54, 123, 245), VideoStore.MATYSEK));
        page.addView(space(18));
        page.addView(profileButton("🐸", "Filípek", Color.rgb(34, 160, 107), VideoStore.FILIPEK));
        page.addView(space(40));
        Button parent = button("⚙  Rodičovská správa", NAVY);
        parent.setOnClickListener(v -> requestPin(this::showParentArea));
        page.addView(parent, wide());
        render(page);
    }

    private Button profileButton(String emoji, String name, int color, String profile) {
        Button button = button(emoji + "   " + name, color);
        button.setTextSize(23);
        button.setMinHeight(dp(84));
        button.setOnClickListener(v -> { activeProfile = profile; activeChannelFilter = null; showLibrary(); });
        button.setContentDescription("Otevřít profil " + name);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        button.setFocusable(true);
        button.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start());
        return button;
    }

    private void showLibrary() {
        List<VideoStore.Video> videos = activeChannelFilter == null ? store.videos(activeProfile) : new ArrayList<>();
        if (VideoStore.MATYSEK.equals(activeProfile)
                && (activeChannelFilter == null || CRUNCHLABS_CHANNEL.equals(activeChannelFilter))) {
            boolean alreadyAdded = false;
            for (VideoStore.Video video : videos) {
                if ("16LuvR2CARA".equals(video.id)) { alreadyAdded = true; break; }
            }
            if (!alreadyAdded) videos.add(new VideoStore.Video("16LuvR2CARA",
                    "Inside Disney’s Secret Engineering Lab  •  CrunchLabs – český dabing", CRUNCHLABS_CHANNEL));
        }
        LinearLayout content = page();
        content.addView(tvHomeHeader());
        content.addView(space(12));
        content.addView(categoryRow());
        content.addView(space(18));
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        VideoGallery grid = new VideoGallery(this, content, footer, this::play, this::videoOptions);
        currentGallery = grid;
        content = footer;
        content.setTag(grid);
        grid.addVideos(videos, 0);
        refreshHistory();
        TextView loading = text("Načítám náhledy z povolených kanálů…", 17, Color.LTGRAY);
        loading.setPadding(0, dp(24), 0, dp(24));
        content.addView(loading, wide());
        render(grid);
        Set<String> displayed = new HashSet<>();
        for (VideoStore.Video video : videos) displayed.add(video.id);
        int channelCount = 0;
        if (VideoStore.MATYSEK.equals(activeProfile) && isChannelVisible(SLIVKI_CHANNEL)) {
            channelCount++;
            loadChannelFeed(SLIVKI_CHANNEL, "Toto je Slivki", 0, content, grid, loading, displayed);
        }
        if (VideoStore.MATYSEK.equals(activeProfile) && isChannelVisible(CRUNCHLABS_CHANNEL)) {
            channelCount++;
            loadChannelFeed(CRUNCHLABS_CHANNEL, "CrunchLabs – český dabing", 0, content, grid, loading, displayed);
        }
        if (VideoStore.MATYSEK.equals(activeProfile) && isChannelVisible(EVERY_BRICK_CHANNEL)) {
            channelCount++;
            loadChannelFeed(EVERY_BRICK_CHANNEL, "Every Brick", 1, content, grid, loading, displayed);
        }
        if (VideoStore.FILIPEK.equals(activeProfile) && isChannelVisible(PAW_PATROL_CZECH_CHANNEL)) {
            channelCount++;
            loadChannelFeed(PAW_PATROL_CZECH_CHANNEL, "Tlapková patrola – česky", 0, content, grid, loading, displayed);
        }
        if (VideoStore.FILIPEK.equals(activeProfile) && isChannelVisible(KIDIBLI_CHANNEL)) {
            channelCount++;
            loadChannelFeed(KIDIBLI_CHANNEL, "Kidibli", 0, content, grid, loading, displayed);
        }
        for (VideoStore.Channel channel : store.channels(activeProfile)) {
            if (isChannelVisible(channel.id)) {
                channelCount++;
                loadChannelFeed(channel.id, channel.title, 0, content, grid, loading, displayed);
            }
        }
        if (channelCount == 0) {
            content.removeView(loading);
            if (videos.isEmpty()) {
                TextView empty = text("Rodič zatím nepřidal žádné video.", 18, Color.LTGRAY);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(70), 0, dp(70));
                content.addView(empty, wide());
            }
        }
    }

    private boolean isChannelVisible(String channelId) {
        return activeChannelFilter == null || activeChannelFilter.equals(channelId);
    }

    private boolean isVideoAllowed(String profile, VideoStore.Video video) {
        if (profile == null) return false;
        for (VideoStore.Video approved : store.videos(profile)) if (approved.id.equals(video.id)) return true;
        String channel = video.channelId;
        if (VideoStore.MATYSEK.equals(profile) && (SLIVKI_CHANNEL.equals(channel)
                || CRUNCHLABS_CHANNEL.equals(channel) || EVERY_BRICK_CHANNEL.equals(channel))) return true;
        if (VideoStore.FILIPEK.equals(profile) && (PAW_PATROL_CZECH_CHANNEL.equals(channel) || KIDIBLI_CHANNEL.equals(channel))) return true;
        for (VideoStore.Channel approved : store.channels(profile)) if (approved.id.equals(channel)) return true;
        return false;
    }

    private void refreshHistory() {
        if (currentGallery == null || activeProfile == null) return;
        List<WatchHistory.Entry> all = history.entries(activeProfile);
        List<WatchHistory.Entry> continuing = new ArrayList<>();
        for (WatchHistory.Entry entry : all) {
            if (!entry.completed && isVideoAllowed(activeProfile, entry.video)
                    && isChannelVisible(entry.video.channelId)) continuing.add(entry);
        }
        currentGallery.history(all, continuing);
    }

    private void videoOptions(VideoStore.Video video) {
        WatchHistory.Entry entry = history.find(activeProfile, video.id);
        new AlertDialog.Builder(this).setTitle(video.title.split("  •  ", 2)[0])
                .setItems(new String[]{entry != null && !entry.completed ? "Pokračovat od " + VideoGallery.time(entry.position) : "Přehrát",
                        "Přehrát od začátku"}, (dialog, which) -> play(video, which == 1)).show();
    }

    private View tvHomeHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView playLogo = text("▶", 27, Color.WHITE);
        playLogo.setGravity(Gravity.CENTER);
        GradientDrawable red = new GradientDrawable();
        red.setColor(Color.rgb(230, 33, 23));
        red.setCornerRadius(dp(10));
        playLogo.setBackground(red);
        TextView appName = title("  Bezpečné video", 24);
        TextView profile = text(profileName(), 18, Color.WHITE);
        profile.setGravity(Gravity.CENTER);
        profile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        profile.setBackgroundResource(R.drawable.tv_button);
        profile.setFocusable(true);
        profile.setOnClickListener(v -> showProfilePicker());
        row.addView(playLogo, new LinearLayout.LayoutParams(dp(62), dp(42)));
        row.addView(appName, new LinearLayout.LayoutParams(0, dp(58), 1));
        row.addView(profile, new LinearLayout.LayoutParams(dp(150), dp(48)));
        return row;
    }

    private View categoryRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView all = categoryChip("Vše", activeChannelFilter == null, null);
        row.addView(all);
        if (VideoStore.MATYSEK.equals(activeProfile)) {
            row.addView(categoryChip("Toto je Slivki", SLIVKI_CHANNEL.equals(activeChannelFilter), SLIVKI_CHANNEL));
            row.addView(categoryChip("CrunchLabs", CRUNCHLABS_CHANNEL.equals(activeChannelFilter), CRUNCHLABS_CHANNEL));
            row.addView(categoryChip("Every Brick", EVERY_BRICK_CHANNEL.equals(activeChannelFilter), EVERY_BRICK_CHANNEL));
        }
        if (VideoStore.FILIPEK.equals(activeProfile)) {
            row.addView(categoryChip("Tlapková patrola", PAW_PATROL_CZECH_CHANNEL.equals(activeChannelFilter), PAW_PATROL_CZECH_CHANNEL));
            row.addView(categoryChip("Kidibli", KIDIBLI_CHANNEL.equals(activeChannelFilter), KIDIBLI_CHANNEL));
        }
        for (VideoStore.Channel channel : store.channels(activeProfile))
            row.addView(categoryChip(channel.title, channel.id.equals(activeChannelFilter), channel.id));
        return row;
    }

    private TextView categoryChip(String label, boolean selected, String channelId) {
        TextView chip = text(label, 16, selected ? Color.BLACK : Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            activeChannelFilter = channelId;
            showLibrary();
        });
        chip.setOnFocusChangeListener((v, focused) -> v.animate()
                .scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(100).start());
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Color.WHITE : Color.rgb(48, 50, 57));
        background.setCornerRadius(dp(10));
        chip.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
        params.setMargins(0, 0, dp(12), 0);
        chip.setPadding(dp(22), 0, dp(22), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private void loadChannelFeed(String channelId, String channelTitle, int languagePriority, LinearLayout content,
                                 VideoGallery grid, TextView loading, Set<String> displayed) {
        if (!store.apiKey().isEmpty()) {
            Button more = button("Načítám " + channelTitle + "…", NAVY);
            content.addView(more, wide());
            if (loading.getParent() == content) content.removeView(loading);
            loadCatalogPage(channelId, channelTitle, languagePriority, "", content, grid, displayed, more);
            return;
        }
        List<VideoStore.Video> cached = channelCache.get(channelId);
        if (cached != null) {
            grid.addVideos(cached, languagePriority);
            for (VideoStore.Video video : cached) displayed.add(video.id);
            if (loading.getParent() == content) content.removeView(loading);
            if (System.currentTimeMillis() - channelCacheTime.getOrDefault(channelId, 0L) < 300000) return;
        }
        channelExecutor.execute(() -> {
            List<VideoStore.Video> found = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "BezpecneVideo-TV");
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(connection.getInputStream(), "UTF-8");
                boolean inEntry = false;
                String videoId = null;
                String videoTitle = null;
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    String name = parser.getName();
                    if (event == XmlPullParser.START_TAG && "entry".equals(name)) inEntry = true;
                    else if (event == XmlPullParser.START_TAG && inEntry && "videoId".equals(name)) videoId = parser.nextText();
                    else if (event == XmlPullParser.START_TAG && inEntry && "title".equals(name)) videoTitle = parser.nextText();
                    else if (event == XmlPullParser.END_TAG && "entry".equals(name)) {
                        if (videoId != null) found.add(new VideoStore.Video(videoId,
                                (videoTitle == null ? "Video" : videoTitle) + "  •  " + channelTitle, channelId));
                        inEntry = false; videoId = null; videoTitle = null;
                    }
                }
                channelCache.put(channelId, new ArrayList<>(found));
                channelCacheTime.put(channelId, System.currentTimeMillis());
            } catch (Exception ignored) { }
            finally { if (connection != null) connection.disconnect(); }
            runOnUiThread(() -> {
                if (!isCurrentLibrary(content)) return;
                grid.addVideos(found, languagePriority);
                for (VideoStore.Video video : found) displayed.add(video.id);
                if (loading.getParent() == content) content.removeView(loading);
                if (found.isEmpty() && cached == null)
                    content.addView(text(channelTitle + ": videa se nepodařilo načíst. Zkuste kanál otevřít znovu.", 16, Color.LTGRAY), wide());
            });
        });
    }

    private void loadCatalogPage(String channelId, String title, int priority, String token,
                                 LinearLayout content, VideoGallery grid, Set<String> displayed, Button more) {
        more.setEnabled(false);
        more.setText("Načítám " + title + "…");
        String key = store.apiKey();
        channelExecutor.execute(() -> {
            try {
                ChannelCatalog.Page page = catalog.load(key, channelId, title, token);
                runOnUiThread(() -> {
                    if (!isCurrentLibrary(content)) return;
                    String firstNew = null;
                    for (VideoStore.Video video : page.videos) {
                        if (displayed.add(video.id)) {
                            if (firstNew == null) firstNew = video.id;
                        }
                    }
                    grid.addVideos(page.videos, priority);
                    if (grid.isAttachedToWindow() && !token.isEmpty() && firstNew != null) grid.focusVideo(firstNew);
                    more.setEnabled(true);
                    if (page.next.isEmpty()) {
                        more.setVisibility(View.GONE);
                        if (page.videos.isEmpty() && token.isEmpty())
                            content.addView(text(title + ": žádná veřejná videa.", 16, Color.LTGRAY), wide());
                    } else {
                        more.setText("Načíst další videa – " + title);
                        more.setOnClickListener(v -> loadCatalogPage(channelId, title, priority, page.next,
                                content, grid, displayed, more));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isCurrentLibrary(content)) return;
                    more.setEnabled(true);
                    more.setText("Zkusit znovu – " + title);
                    more.setOnClickListener(v -> loadCatalogPage(channelId, title, priority, token,
                            content, grid, displayed, more));
                    Toast.makeText(this, error instanceof java.io.IOException
                            ? "Nepodařilo se připojit k YouTube. Zkontrolujte internet."
                            : "Archiv nelze načíst. Zkontrolujte API klíč, zapnutí služby a limit v Google Cloud.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }


    private boolean isCurrentLibrary(View content) {
        return !isDestroyed() && content.getTag() == currentGallery
                && (root.getChildAt(0) == currentGallery || retainedLibrary == currentGallery);
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void play(VideoStore.Video video) {
        play(video, false);
    }

    private void play(VideoStore.Video video, boolean fromBeginning) {
        if (!video.id.matches("[A-Za-z0-9_-]{11}")) return;
        if (!isVideoAllowed(activeProfile, video)) {
            Toast.makeText(this, "Toto video už není povolené.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fromBeginning) history.remove(activeProfile, video.id);
        WatchHistory.Entry saved = history.find(activeProfile, video.id);
        playbackSession = new PlaybackSession(++playbackGeneration, activeProfile, video,
                saved == null ? 0 : saved.resumeAt());
        retainedLibrary = root.getChildAt(0);
        requestedVideo = video.id;
        if (retainedPlayer == null) {
            retainedPlayer = configuredWebView();
            retainedPlayer.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) { syncPlayer(); }
            });
            String html = "<!doctype html><html><head><meta name='referrer' content='strict-origin-when-cross-origin'>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>html,body,#player,iframe{width:100%;height:100%;margin:0;border:0;background:#000;overflow:hidden}</style>"
                    + "</head><body><div id='player'></div><script>"
                    + playerScript()
                    + "</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
            retainedPlayer.loadDataWithBaseURL("https://cz.petr.bezpecnevideo.app/", html, "text/html", "UTF-8", null);
        }
        webView = retainedPlayer;
        if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
        webView.onResume();
        syncPlayer();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        render(shell);
        playbackHandler.removeCallbacks(progressPoll);
        playbackHandler.postDelayed(progressPoll, 1000);
    }

    private String playerScript() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("player.js"), "UTF-8"))) {
            StringBuilder script = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) script.append(line).append('\n');
            return script.toString();
        } catch (java.io.IOException error) { throw new IllegalStateException("Missing player script", error); }
    }

    private void captureProgress(boolean pause) {
        PlaybackSession captured = playbackSession;
        if (retainedPlayer == null || captured == null) return;
        retainedPlayer.evaluateJavascript("window.playbackSnapshot ? playbackSnapshot(" + pause + ") : null", value -> {
            if (isDestroyed() || captured.generation != playbackGeneration) return;
            try {
                JSONObject snapshot = new JSONObject(value);
                if (!captured.video.id.equals(snapshot.optString("id"))
                        || snapshot.optLong("session", -1) != captured.generation
                        || !isVideoAllowed(captured.profile, captured.video)) return;
                history.save(captured.profile, captured.video, snapshot.getDouble("position"),
                        snapshot.getDouble("duration"), snapshot.optBoolean("ended"));
                if (pause && captured.profile.equals(activeProfile)) refreshHistory();
            } catch (Exception ignored) { }
        });
    }

    private void closePlayer() {
        if (webView == retainedPlayer && retainedPlayer != null) {
            playbackHandler.removeCallbacks(progressPoll);
            captureProgress(true);
            requestedVideo = null;
            retainedPlayer.onPause();
            if (retainedPlayer.getParent() instanceof ViewGroup)
                ((ViewGroup) retainedPlayer.getParent()).removeView(retainedPlayer);
            webView = null;
            if (retainedLibrary != null) {
                View library = retainedLibrary;
                retainedLibrary = null;
                render(library);
                return;
            }
        } else if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        if (activeProfile != null) showLibrary(); else showProfilePicker();
    }

    private void syncPlayer() {
        if (retainedPlayer != null) retainedPlayer.evaluateJavascript("if(window.selectVideo)selectVideo("
                + (requestedVideo == null ? "null" : "'" + requestedVideo + "'") + ","
                + (playbackSession == null ? 0 : playbackSession.start) + "," + playbackGeneration + ");", null);
    }

    @Override protected void onPause() {
        if (phoneScreen) closePhoneAdd();
        playbackHandler.removeCallbacks(progressPoll);
        if (webView == retainedPlayer) captureProgress(true);
        requestedVideo = null;
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (webView == retainedPlayer && playbackSession != null) {
            retainedPlayer.evaluateJavascript("if(window.resumeTracking)resumeTracking();", null);
            playbackHandler.removeCallbacks(progressPoll);
            playbackHandler.postDelayed(progressPoll, 1000);
        }
    }

    @Override protected void onDestroy() {
        stopPhoneSession();
        playbackHandler.removeCallbacks(progressPoll);
        channelExecutor.shutdownNow();
        if (webView != null && webView != retainedPlayer) webView.destroy();
        if (retainedPlayer != null) retainedPlayer.destroy();
        retainedLibrary = null;
        super.onDestroy();
    }

    private static final class PlaybackSession {
        final long generation;
        final String profile;
        final VideoStore.Video video;
        final double start;
        PlaybackSession(long generation, String profile, VideoStore.Video video, double start) {
            this.generation = generation; this.profile = profile; this.video = video; this.start = start;
        }
    }

    private void showParentArea() {
        LinearLayout content = page();
        content.addView(header("Rodičovská správa", "Hotovo", v -> showProfilePicker()));
        Button phone = button("Přidat video z mobilu – QR kód", Color.rgb(230, 33, 23));
        phone.setOnClickListener(v -> showPhoneAdd());
        content.addView(phone, wide());
        content.addView(text("Na mobilu vložíte odkaz z YouTube a vyberete dítě. Bez psaní ovladačem.", 15, Color.LTGRAY));
        content.addView(space(16));
        content.addView(title("Přidat povolené video", 25));
        content.addView(text("Vložte odkaz z YouTube a vyberte, komu se video zobrazí.", 16, Color.LTGRAY));
        content.addView(space(12));
        Button search = button("🔎  Vyhledat video přímo na YouTube", Color.rgb(230, 33, 23));
        search.setOnClickListener(v -> askSearchText());
        content.addView(search, wide());
        content.addView(text("Stačí napsat název videa; odkaz není potřeba opisovat.", 13, Color.GRAY));
        content.addView(space(14));
        EditText url = input("Odkaz na YouTube video");
        EditText videoTitle = input("Název pro dítě");
        content.addView(url, wide());
        content.addView(videoTitle, wide());
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button addMatysek = button("Přidat Matýskovi", Color.rgb(54, 123, 245));
        Button addFilipek = button("Přidat Filípkovi", Color.rgb(34, 160, 107));
        View.OnClickListener add = v -> {
            String profile = v == addMatysek ? VideoStore.MATYSEK : VideoStore.FILIPEK;
            if (store.addVideo(profile, url.getText().toString(), videoTitle.getText().toString())) {
                Toast.makeText(this, "Video bylo povoleno", Toast.LENGTH_SHORT).show();
                showParentArea();
            } else Toast.makeText(this, "Tento YouTube odkaz není platný", Toast.LENGTH_LONG).show();
        };
        addMatysek.setOnClickListener(add); addFilipek.setOnClickListener(add);
        actions.addView(addMatysek, new LinearLayout.LayoutParams(0, dp(58), 1));
        actions.addView(addFilipek, new LinearLayout.LayoutParams(0, dp(58), 1));
        content.addView(actions, wide());
        content.addView(space(28));
        Button archive = button("Celý archiv kanálů – nastavit API klíč", NAVY);
        archive.setOnClickListener(v -> configureCatalog());
        content.addView(archive, wide());
        content.addView(text(store.apiKey().isEmpty() ? "Zobrazuje se pouze krátký seznam nejnovějších videí."
                : "Archiv je zapnutý: 50 videí na stránku a tlačítko Načíst další.", 13, Color.GRAY));
        Button premium = button("YouTube Premium – přihlášení a kontrola", Color.rgb(190, 30, 45));
        premium.setOnClickListener(v -> showPremiumLogin());
        content.addView(premium, wide());
        content.addView(text("Přihlášení probíhá přímo na stránce YouTube. Aplikace heslo neukládá.", 13, Color.GRAY));
        content.addView(space(14));
        Button update = button("⬇  Zkontrolovat a nainstalovat aktualizaci", Color.rgb(42, 106, 72));
        update.setOnClickListener(v -> new AppUpdater(this).checkAndInstall());
        content.addView(update, wide());
        content.addView(text("Aktualizace se bezpečně stahují z projektu petan86/bezpecne-video-tv na GitHubu.", 13, Color.GRAY));
        content.addView(space(26));
        content.addView(title("Povolený obsah", 24));
        addManagementList(content, VideoStore.MATYSEK, "Matýsek");
        addManagementList(content, VideoStore.FILIPEK, "Filípek");
        content.addView(space(18));
        Button pin = button("Změnit rodičovský PIN", NAVY);
        pin.setOnClickListener(v -> changePin());
        content.addView(pin, wide());
        render(scroll(content));
    }

    private void askSearchText() {
        EditText query = input("Co chcete na YouTube najít?");
        new AlertDialog.Builder(this)
                .setTitle("Vyhledat video")
                .setView(query)
                .setPositiveButton("Vyhledat", (dialog, which) -> {
                    String value = query.getText().toString().trim();
                    if (!value.isEmpty()) showYouTubeSearch(value);
                })
                .setNegativeButton("Zrušit", null)
                .show();
    }

    private void showPhoneAdd() {
        stopPhoneSession();
        phoneScreen = true;
        Object request = new Object();
        phoneRequest = request;
        LinearLayout content = page();
        content.setKeepScreenOn(true);
        content.addView(header("Přidat video z mobilu", "Hotovo", v -> closePhoneAdd()));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView qr = new ImageView(this);
        qr.setContentDescription("QR kód pro jednorázové přidávání videí z mobilu");
        row.addView(qr, new LinearLayout.LayoutParams(dp(250), dp(250)));
        LinearLayout instructions = new LinearLayout(this);
        instructions.setOrientation(LinearLayout.VERTICAL);
        instructions.setPadding(dp(28), 0, 0, 0);
        instructions.addView(title("Naskenuj QR kód fotoaparátem mobilu", 24));
        instructions.addView(text("1. Mobil připoj ke stejné domácí Wi-Fi jako TV.\n2. Na YouTube: Sdílet → Kopírovat odkaz.\n3. V otevřené stránce vlož odkaz a vyber dítě.", 18, Color.WHITE));
        instructions.addView(text("Nech tuto obrazovku otevřenou. Přístup skončí za 10 minut nebo po stisku Hotovo. Používej na své domácí síti.", 15, Color.LTGRAY));
        TextView connection = text("Připravuji připojení…", 15, Color.LTGRAY);
        instructions.addView(connection);
        TextView received = text("", 16, Color.rgb(129, 228, 168));
        received.setMaxLines(2);
        instructions.addView(received);
        row.addView(instructions, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(row, wide());
        render(scroll(content));
        channelExecutor.execute(() -> {
            PhoneVideoServer started = null;
            try {
                java.net.InetAddress address = PhonePairing.address(this);
                if (address == null) throw new java.io.IOException("LAN unavailable");
                started = new PhoneVideoServer(address, readAsset("phone.html"), readAsset("phone.js"),
                        YouTubePreview::lookup, (profile, id, title) -> {
                            boolean saved = store.addVideo(profile, id, title);
                            if (saved) runOnUiThread(() -> {
                                if (phoneScreen && phoneRequest == request)
                                    received.setText("Povoleno " + (VideoStore.MATYSEK.equals(profile) ? "Matýskovi: " : "Filípkovi: ") + title);
                            });
                            return saved;
                        });
                android.graphics.Bitmap bitmap = PhonePairing.qr(started.link());
                PhoneVideoServer server = started;
                runOnUiThread(() -> {
                    if (!phoneScreen || phoneRequest != request || isDestroyed()) { server.close(); return; }
                    phoneServer = server;
                    qr.setImageBitmap(bitmap);
                    connection.setText("Připojení k TV: " + server.origin() + "\nPřístupový odkaz je v QR kódu.");
                    phoneTimeout = () -> {
                        if (phoneServer == server) {
                            closePhoneAdd();
                            Toast.makeText(this, "Přístup z mobilu vypršel. Pro další přidávání otevřete nový QR kód.", Toast.LENGTH_LONG).show();
                        }
                    };
                    playbackHandler.postDelayed(phoneTimeout, 10 * 60 * 1000L);
                });
            } catch (Exception error) {
                if (started != null) started.close();
                runOnUiThread(() -> {
                    if (!phoneScreen || phoneRequest != request || isDestroyed()) return;
                    connection.setText("Připojení se nepodařilo připravit. Připoj TV k domácí Wi-Fi nebo kabelem, pak zkus QR kód znovu. Je potřeba místní IPv4 adresa.");
                });
            }
        });
    }

    private String readAsset(String name) throws java.io.IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(name), "UTF-8"))) {
            StringBuilder value = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) value.append(line).append('\n');
            return value.toString();
        }
    }

    private void stopPhoneSession() {
        phoneScreen = false;
        phoneRequest = null;
        if (phoneTimeout != null) { playbackHandler.removeCallbacks(phoneTimeout); phoneTimeout = null; }
        if (phoneServer != null) { phoneServer.close(); phoneServer = null; }
    }

    private void closePhoneAdd() { stopPhoneSession(); showParentArea(); }

    private void configureCatalog() {
        EditText key = input("YouTube Data API klíč");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(store.apiKey());
        new AlertDialog.Builder(this).setTitle("Celý archiv kanálů")
                .setMessage("V Google Cloud zapněte YouTube Data API v3 a vytvořte API klíč. Klíč se uloží jen do této aplikace. Prázdné pole zapne původní seznam novinek.")
                .setView(key).setPositiveButton("Uložit", (dialog, which) -> {
                    store.setApiKey(key.getText().toString());
                    catalog.clear();
                    showParentArea();
                }).setNegativeButton("Zrušit", null).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showYouTubeSearch(String query) {
        searchChoiceOpen = false;
        webView = configuredWebView();
        webView.addJavascriptInterface(new ParentSearchBridge(), "BezpecneVideo");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleSearchNavigation(request.getUrl().toString(), view.getTitle());
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (handleSearchNavigation(url, view.getTitle())) view.stopLoading();
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                handleSearchNavigation(url, view.getTitle());
            }
            @Override public void onPageFinished(WebView view, String url) {
                injectTvSelection(view);
            }
        });
        String encoded = Uri.encode(query);
        webView.loadUrl("https://m.youtube.com/results?search_query=" + encoded);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.WHITE);
        shell.addView(header("Šipkami označte video a stiskněte OK", "Zpět", v -> closeSearch()), new LinearLayout.LayoutParams(-1, dp(64)));
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        render(shell);
        webView.requestFocus();
    }

    private void injectTvSelection(WebView view) {
        String script = "(function(){" +
                "if(window.__bezpecneVideoInstalled)return;window.__bezpecneVideoInstalled=true;" +
                "var style=document.createElement('style');" +
                "style.textContent='a[data-bv-selectable=true]{display:block!important} a[data-bv-selectable=true]:focus{outline:7px solid #FCA311!important;outline-offset:3px!important;background:#FFF0C7!important;border-radius:8px!important}';" +
                "document.head.appendChild(style);" +
                "function label(a){var n=a.querySelector(\"#video-title,yt-formatted-string,h3,[aria-label]\");return ((n&&(n.textContent||n.getAttribute('aria-label')))||a.textContent||'Povolené video').trim().substring(0,180);}" +
                "function bind(){document.querySelectorAll('a[href]').forEach(function(a){" +
                "var h=a.getAttribute('href')||'';var video=h.indexOf('/watch')>=0&&h.indexOf('v=')>=0;var channel=h.indexOf('/channel/UC')>=0||h.indexOf('/@')===0;" +
                "if((!video&&!channel)||a.dataset.bvBound)return;a.dataset.bvBound='1';a.dataset.bvSelectable='true';a.tabIndex=0;" +
                "a.addEventListener('click',function(e){var u=new URL(a.href,location.href);" +
                "if(video){var id=u.searchParams.get('v');if(id){e.preventDefault();e.stopImmediatePropagation();BezpecneVideo.video(id,label(a));}}" +
                "else{e.preventDefault();e.stopImmediatePropagation();BezpecneVideo.channel(u.href,label(a));}},true);" +
                "});}" +
                "bind();new MutationObserver(bind).observe(document.documentElement,{childList:true,subtree:true});" +
                "setTimeout(function(){var first=document.querySelector('a[data-bv-selectable=true]');if(first)first.focus();},900);" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private final class ParentSearchBridge {
        @JavascriptInterface public void video(String videoId, String title) {
            runOnUiThread(() -> {
                if (searchChoiceOpen) return;
                searchChoiceOpen = true;
                approveSearchResult(videoId, cleanYouTubeTitle(title));
            });
        }

        @JavascriptInterface public void channel(String url, String title) {
            runOnUiThread(() -> {
                if (searchChoiceOpen) return;
                searchChoiceOpen = true;
                resolveAndApproveChannel(url, cleanYouTubeTitle(title));
            });
        }
    }

    private boolean handleSearchNavigation(String url, String pageTitle) {
        if (url == null || searchChoiceOpen) return false;
        String videoId = VideoStore.extractVideoId(url);
        if (videoId != null) {
            searchChoiceOpen = true;
            approveSearchResult(videoId, cleanYouTubeTitle(pageTitle));
            return true;
        }
        if (url.contains("youtube.com/channel/UC") || url.contains("youtube.com/@")) {
            searchChoiceOpen = true;
            resolveAndApproveChannel(url, cleanYouTubeTitle(pageTitle));
            return true;
        }
        return false;
    }

    private void approveSearchResult(String videoId, String title) {
        String safeTitle = title == null || title.isEmpty() ? "Povolené video" : title;
        new AlertDialog.Builder(this)
                .setTitle("Komu povolit video?")
                .setMessage(safeTitle)
                .setPositiveButton("Matýskovi", (dialog, which) -> saveSearchResult(VideoStore.MATYSEK, videoId, safeTitle))
                .setNegativeButton("Filípkovi", (dialog, which) -> saveSearchResult(VideoStore.FILIPEK, videoId, safeTitle))
                .setNeutralButton("Zrušit", (dialog, which) -> searchChoiceOpen = false)
                .setOnCancelListener(dialog -> searchChoiceOpen = false)
                .show();
    }

    private void saveSearchResult(String profile, String videoId, String title) {
        store.addVideo(profile, videoId, title);
        Toast.makeText(this, "Video bylo povoleno", Toast.LENGTH_SHORT).show();
        searchChoiceOpen = false;
    }

    private void resolveAndApproveChannel(String channelUrl, String visibleTitle) {
        Toast.makeText(this, "Načítám kanál…", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String channelId = directChannelId(channelUrl);
                String channelTitle = visibleTitle;
                if (channelId == null) {
                    HttpURLConnection connection = (HttpURLConnection) new URL(channelUrl).openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) BezpecneVideo/0.1");
                    StringBuilder html = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) html.append(line);
                    }
                    Matcher idMatcher = Pattern.compile("\\\"channelId\\\":\\\"(UC[A-Za-z0-9_-]{22})\\\"").matcher(html);
                    if (idMatcher.find()) channelId = idMatcher.group(1);
                    Matcher titleMatcher = Pattern.compile("<meta property=\\\"og:title\\\" content=\\\"([^\\\"]+)\\\"").matcher(html);
                    if (titleMatcher.find()) channelTitle = titleMatcher.group(1);
                }
                if (channelId == null) throw new IllegalStateException("ID kanálu nebylo nalezeno");
                String finalId = channelId;
                String finalTitle = channelTitle == null || channelTitle.isEmpty() ? "Povolený kanál" : channelTitle;
                runOnUiThread(() -> approveChannel(finalId, finalTitle));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    searchChoiceOpen = false;
                    Toast.makeText(this, "Kanál se nepodařilo načíst.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void approveChannel(String channelId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Komu povolit celý kanál?")
                .setMessage(title + "\n\nPovolena budou i nová videa přidaná v budoucnu.")
                .setPositiveButton("Matýskovi", (dialog, which) -> saveChannel(VideoStore.MATYSEK, channelId, title))
                .setNegativeButton("Filípkovi", (dialog, which) -> saveChannel(VideoStore.FILIPEK, channelId, title))
                .setNeutralButton("Zrušit", (dialog, which) -> searchChoiceOpen = false)
                .setOnCancelListener(dialog -> searchChoiceOpen = false)
                .show();
    }

    private void saveChannel(String profile, String channelId, String title) {
        store.addChannel(profile, channelId, title);
        searchChoiceOpen = false;
        Toast.makeText(this, "Celý kanál byl povolen", Toast.LENGTH_SHORT).show();
    }

    private String directChannelId(String url) {
        Matcher matcher = Pattern.compile("/channel/(UC[A-Za-z0-9_-]{22})").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanYouTubeTitle(String title) {
        if (title == null) return "Povolené video";
        return title.replace(" - YouTube", "").trim();
    }

    private void closeSearch() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        showParentArea();
    }

    private void addManagementList(LinearLayout content, String profile, String name) {
        content.addView(title(name, 20));
        List<VideoStore.Video> videos = store.videos(profile);
        if (videos.isEmpty()) content.addView(text("Zatím bez videí", 15, Color.GRAY));
        for (VideoStore.Video video : videos) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(video.title, 16, Color.WHITE);
            Button remove = button("Odebrat", Color.rgb(180, 48, 48));
            remove.setOnClickListener(v -> { store.removeVideo(profile, video.id); showParentArea(); });
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
            row.addView(remove, new LinearLayout.LayoutParams(dp(110), dp(48)));
            content.addView(row, wide());
        }
        for (VideoStore.Channel channel : store.channels(profile)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text("Kanál: " + channel.title, 16, Color.WHITE);
            Button remove = button("Odebrat", Color.rgb(180, 48, 48));
            remove.setOnClickListener(v -> { store.removeChannel(profile, channel.id); showParentArea(); });
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
            row.addView(remove, new LinearLayout.LayoutParams(dp(110), dp(48)));
            content.addView(row, wide());
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showPremiumLogin() {
        webView = configuredWebView();
        webView.loadUrl("https://m.youtube.com/paid_memberships");
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(header("YouTube Premium", "Zpět", v -> { webView.stopLoading(); webView.destroy(); webView = null; showParentArea(); }), new LinearLayout.LayoutParams(-1, dp(64)));
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        render(shell);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView configuredWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        WebView view = new WebView(this);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setDatabaseEnabled(true);
        view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        view.getSettings().setMediaPlaybackRequiresUserGesture(false);
        view.getSettings().setOffscreenPreRaster(true);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return false; }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                customView = view; customViewCallback = callback;
                root.removeAllViews(); root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                customViewCallback.onCustomViewHidden(); customView = null; customViewCallback = null;
                if (webView != null) playRestore();
            }
        });
        return view;
    }

    private void playRestore() {
        View shell = (View) webView.getParent();
        root.removeAllViews();
        if (shell.getParent() instanceof ViewGroup) ((ViewGroup) shell.getParent()).removeView(shell);
        root.addView(shell);
    }

    private void requestPin(Runnable success) {
        EditText pin = input("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Rodičovský PIN").setView(pin)
                .setPositiveButton("Odemknout", (dialog, which) -> {
                    if (store.checkPin(pin.getText().toString())) success.run();
                    else Toast.makeText(this, "Nesprávný PIN", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Zrušit", null).show();
    }

    private void changePin() {
        EditText pin = input("Nový PIN (4–8 číslic)");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Změnit PIN").setView(pin)
                .setPositiveButton("Uložit", (dialog, which) -> {
                    String value = pin.getText().toString();
                    if (value.matches("[0-9]{4,8}")) { store.setPin(value); Toast.makeText(this, "PIN změněn", Toast.LENGTH_SHORT).show(); }
                    else Toast.makeText(this, "PIN musí mít 4 až 8 číslic", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Zrušit", null).show();
    }

    @Override public void onBackPressed() {
        if (phoneScreen) { closePhoneAdd(); return; }
        if (customView != null) {
            customViewCallback.onCustomViewHidden();
            customView = null;
            customViewCallback = null;
            closePlayer();
        }
        else if (webView != null) closePlayer();
        else if (activeProfile != null) showProfilePicker();
        else super.onBackPressed();
    }

    private LinearLayout page() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(20), dp(24), dp(20), dp(28)); v.setBackgroundColor(DARK); return v; }
    private ScrollView scroll(View view) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(view); return s; }
    private void render(View view) { root.removeAllViews(); root.addView(view, new FrameLayout.LayoutParams(-1, -1)); }
    private View header(String label, String action, View.OnClickListener listener) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView name = title(label, 21); Button b = button(action, NAVY); b.setOnClickListener(listener); row.addView(name, new LinearLayout.LayoutParams(0, dp(64), 1)); row.addView(b, new LinearLayout.LayoutParams(dp(105), dp(48))); return row; }
    private TextView title(String value, int size) { TextView t = text(value, size, Color.WHITE); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0, dp(5), 0, dp(5)); return t; }
    private Button button(String value, int color) { Button b = new Button(this); b.setText(value); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundResource(cz.petr.bezpecnevideo.R.drawable.tv_button); b.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(100).start()); return b; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(17); e.setSingleLine(true); e.setPadding(dp(12), dp(10), dp(12), dp(10)); return e; }
    private View space(int height) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height))); return v; }
    private LinearLayout.LayoutParams wide() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(5), 0, dp(5)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String profileName() { return VideoStore.MATYSEK.equals(activeProfile) ? "Matýsek" : "Filípek"; }
}
