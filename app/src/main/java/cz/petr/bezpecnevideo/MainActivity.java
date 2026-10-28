package cz.petr.bezpecnevideo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
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
    private static final String SLIVKI_UPLOADS = "UUAugiH1MZcHcsbnWv31JrOg";
    private VideoStore store;
    private String activeProfile;
    private String activeChannelFilter;
    private WebView webView;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean searchChoiceOpen;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new VideoStore(this);
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
        LinearLayout content = page();
        content.addView(tvHomeHeader());
        content.addView(space(12));
        content.addView(categoryRow());
        content.addView(space(18));
        content.addView(title("Doporučeno pro tebe", 27));
        content.addView(space(12));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        content.addView(grid, wide());
        for (VideoStore.Video video : videos) addVideoToGrid(grid, video);
        TextView loading = text("Načítám náhledy z povolených kanálů…", 17, Color.LTGRAY);
        loading.setPadding(0, dp(24), 0, dp(24));
        content.addView(loading, wide());
        render(scroll(content));
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
                                 GridLayout grid, TextView loading, Set<String> displayed) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<VideoStore.Video> found = new ArrayList<>();
            try {
                URL url = new URL("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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
                                (videoTitle == null ? "Video" : videoTitle) + "  •  " + channelTitle));
                        inEntry = false; videoId = null; videoTitle = null;
                    }
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (content.getParent() == null) return;
                for (VideoStore.Video video : found) {
                    if (displayed.add(video.id)) addVideoToGrid(grid, video, languagePriority);
                }
                if (loading.getParent() == content) content.removeView(loading);
                if (found.isEmpty() && content.getChildCount() <= 3)
                    content.addView(text("Náhledy kanálu se nepodařilo načíst. Zkontrolujte internet.", 16, Color.LTGRAY), wide());
            });
        });
    }

    private void addVideoToGrid(GridLayout grid, VideoStore.Video video) {
        addVideoToGrid(grid, video, 0);
    }

    private void addVideoToGrid(GridLayout grid, VideoStore.Video video, int languagePriority) {
        View card = videoCard(video);
        card.setTag(languagePriority);
        int available = getResources().getDisplayMetrics().widthPixels - dp(72);
        int width = Math.max(dp(220), available / 4);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = width - dp(14);
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(dp(7), dp(7), dp(7), dp(16));
        card.setLayoutParams(params);
        int insertAt = grid.getChildCount();
        for (int i = 0; i < grid.getChildCount(); i++) {
            Object tag = grid.getChildAt(i).getTag();
            if (tag instanceof Integer && (Integer) tag > languagePriority) {
                insertAt = i;
                break;
            }
        }
        grid.addView(card, insertAt);
    }

    private View channelCard(String channelTitle, String uploadsPlaylist) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(24), dp(18), dp(24), dp(18));
        card.setBackgroundColor(Color.rgb(230, 33, 23));
        TextView icon = text("▶", 38, Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        TextView labels = text(channelTitle + "\nVšechna videa z povoleného kanálu", 20, Color.WHITE);
        labels.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(icon, new LinearLayout.LayoutParams(dp(80), dp(80)));
        card.addView(labels, new LinearLayout.LayoutParams(0, dp(90), 1));
        card.setFocusable(true);
        card.setContentDescription("Otevřít povolený kanál " + channelTitle);
        card.setOnClickListener(v -> playChannel(channelTitle, uploadsPlaylist));
        card.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(120).start());
        LinearLayout.LayoutParams params = wide();
        params.setMargins(0, 0, 0, dp(18));
        card.setLayoutParams(params);
        card.setElevation(dp(4));
        return card;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void playChannel(String channelTitle, String uploadsPlaylist) {
        webView = configuredWebView();
        String html = "<!doctype html><html><head><meta name='referrer' content='strict-origin-when-cross-origin'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>html,body{width:100%;height:100%;margin:0;background:#000;overflow:hidden}iframe{display:block;width:100%;height:100%;border:0}</style></head>" +
                "<body><iframe " +
                "src='https://www.youtube.com/embed/videoseries?list=" + uploadsPlaylist + "&autoplay=1&playsinline=1&rel=0&hl=cs&cc_load_policy=0' " +
                "title='" + channelTitle.replace("'", "") + "' frameborder='0' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>";
        webView.loadDataWithBaseURL("https://cz.petr.bezpecnevideo.app/", html, "text/html", "UTF-8", null);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        render(shell);
    }

    private View videoCard(VideoStore.Video video) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(12));
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundColor(Color.LTGRAY);
        thumbnail.setContentDescription("Náhled videa " + video.title);
        new ThumbnailLoader(thumbnail).executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR,
                "https://i.ytimg.com/vi/" + video.id + "/hqdefault.jpg");
        int imageHeight = Math.round((getResources().getDisplayMetrics().widthPixels / 4f - dp(26)) * 9f / 16f);
        card.addView(thumbnail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imageHeight));
        String[] parts = video.title.split("  •  ", 2);
        TextView label = title(parts[0], 17);
        label.setMaxLines(2);
        label.setPadding(dp(4), dp(10), dp(4), 0);
        card.addView(label, new LinearLayout.LayoutParams(-1, dp(58)));
        if (parts.length > 1) {
            TextView channel = text(parts[1], 14, Color.LTGRAY);
            channel.setPadding(dp(4), 0, dp(4), dp(4));
            card.addView(channel);
        }
        card.setBackgroundColor(SURFACE);
        card.setOnClickListener(v -> play(video));
        card.setFocusable(true);
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(120).start();
            v.setBackgroundColor(focused ? Color.rgb(92, 61, 10) : SURFACE);
        });
        card.setElevation(dp(3));
        return card;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void play(VideoStore.Video video) {
        webView = configuredWebView();
        String html = "<!doctype html><html><head><meta name='referrer' content='strict-origin-when-cross-origin'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>html,body{width:100%;height:100%;margin:0;background:#000;overflow:hidden}iframe{display:block;width:100%;height:100%;border:0}</style></head>" +
                "<body><iframe " +
                "src='https://www.youtube.com/embed/" + video.id + "?autoplay=1&playsinline=1&rel=0&hl=cs&cc_load_policy=0' " +
                "title='YouTube video player' frameborder='0' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>";
        webView.loadDataWithBaseURL("https://cz.petr.bezpecnevideo.app/", html, "text/html", "UTF-8", null);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        render(shell);
    }

    private void closePlayer() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        showLibrary();
    }

    private void showParentArea() {
        LinearLayout content = page();
        content.addView(header("Rodičovská správa", "Hotovo", v -> showProfilePicker()));
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
        if (customView != null) { webView.getWebChromeClient(); customViewCallback.onCustomViewHidden(); customView = null; showLibrary(); }
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
