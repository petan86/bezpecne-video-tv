package cz.petr.bezpecnevideo;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, D-pad friendly YouTube playback backed by Media3 and NewPipe Extractor. */
final class NativeYouTubePlayer extends FrameLayout {
    interface Listener {
        void onProgress(double positionSeconds, double durationSeconds, boolean ended);
        void onError(String detail);
        void onEnded();
    }

    private static boolean extractorReady;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final PlayerView playerView;
    private final ProgressBar loading;
    private final TextView message;
    private final Button qualityButton;
    private final Button captionsButton;
    private final LinearLayout actions;
    private final Listener listener;
    private static final long ACTIONS_HIDE_DELAY_MS = 3_000;
    /** Highest offered video resolution (lines). */
    private static final int MAX_HEIGHT = 720;
    /**
     * Adaptive playback starts from this bandwidth guess (bits/s), so the first seconds load a low
     * quality quickly; the player steps up to 720p once enough buffer is downloaded.
     */
    private static final long START_BITRATE_ESTIMATE = 2_500_000;
    private static DefaultBandwidthMeter sharedMeter;
    /** Buffer (ms) that must be loaded before switching to a higher quality. */
    private static final int MIN_BUFFER_FOR_QUALITY_INCREASE_MS = 6_000;
    private final Runnable hideActions = this::hideActions;
    /** Video-only streams of one codec, ascending by height, played as one adaptive DASH set. */
    private List<VideoStream> ladder = new ArrayList<>();
    private long durationMs;
    private boolean adaptiveActive;
    /** 0 = automatic quality, otherwise the fixed height chosen by the user (adaptive mode). */
    private int fixedHeight;
    private int currentHeight;
    private ExoPlayer player;
    private List<VideoStream> qualities = new ArrayList<>();
    private List<AudioStream> audios = new ArrayList<>();
    private List<SubtitlesStream> subtitles = new ArrayList<>();
    private int selectedQuality;
    private int selectedSubtitle = -1;
    private long initialPositionMs;
    private boolean errorReported;
    private boolean endedReported;
    private Runnable upNextTick;

    NativeYouTubePlayer(Context context, String videoId, double startSeconds, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(context);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(false);
        addView(playerView, new LayoutParams(-1, -1));
        // Prevent the TV screensaver / ambient mode from interrupting playback after ~5 min
        // without remote input. Updated from player state once playback starts.
        setKeepScreenOn(true);

        loading = new ProgressBar(context);
        LayoutParams spinner = new LayoutParams(dp(64), dp(64), Gravity.CENTER);
        addView(loading, spinner);
        message = new TextView(context);
        message.setTextColor(Color.WHITE); message.setTextSize(18); message.setGravity(Gravity.CENTER);
        message.setPadding(dp(24), dp(18), dp(24), dp(18));
        addView(message, new LayoutParams(-1, -2, Gravity.CENTER));

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(16), dp(10), dp(16), dp(18));
        qualityButton = controlButton("Kvalita: načítám…");
        captionsButton = controlButton("Titulky");
        qualityButton.setOnClickListener(v -> chooseQuality());
        captionsButton.setOnClickListener(v -> chooseCaptions());
        qualityButton.setEnabled(false); captionsButton.setEnabled(false);
        actions.addView(qualityButton); actions.addView(captionsButton);
        addView(actions, new LayoutParams(-1, -2, Gravity.TOP));

        initialPositionMs = Math.max(0, (long) (startSeconds * 1000));
        load(videoId);
    }

    private Button controlButton(String text) {
        Button result = new Button(getContext()); result.setText(text); result.setTextSize(17);
        result.setTextColor(Color.WHITE); result.setFocusable(true); result.setAllCaps(false);
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 39, 48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(230), dp(60));
        params.setMargins(dp(8), 0, dp(8), 0); result.setLayoutParams(params);
        result.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.06f : 1f)
                .scaleY(focused ? 1.06f : 1f).setDuration(100).start());
        return result;
    }

    private void load(String videoId) {
        message.setText("Připravuji nativní přehrávač…");
        loader.execute(() -> {
            try {
                initializeExtractor();
                StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);
                audios = new ArrayList<>(info.getAudioStreams());
                audios.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                audios.sort(AudioPreference.ORDER);
                List<VideoStream> found = new ArrayList<>();
                found.addAll(info.getVideoStreams()); found.addAll(info.getVideoOnlyStreams());
                found.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                // Muxed video has its own audio, which may be in another language.
                // Use separate video so the preferred Czech track is actually played.
                if (!audios.isEmpty() && AudioPreference.isCzech(audios.get(0))
                        && found.stream().anyMatch(VideoStream::isVideoOnly)) {
                    found.removeIf(stream -> !stream.isVideoOnly());
                }
                found.sort(Comparator.comparingInt(NativeYouTubePlayer::height).reversed()
                        .thenComparing(Comparator.comparingInt(VideoStream::getFps).reversed()));
                Set<String> labels = new HashSet<>();
                List<VideoStream> unique = new ArrayList<>();
                for (VideoStream stream : found) {
                    String key = stream.getResolution() + ":" + stream.getFps();
                    if (labels.add(key)) unique.add(stream);
                }
                if (unique.isEmpty()) throw new IllegalStateException("YouTube neposkytl použitelnou video stopu.");
                // Cap resolution at 720p; if YouTube offers nothing that low, keep only the smallest stream.
                List<VideoStream> capped = new ArrayList<>();
                for (VideoStream stream : unique) if (height(stream) <= MAX_HEIGHT) capped.add(stream);
                if (capped.isEmpty()) capped.add(unique.get(unique.size() - 1));
                qualities = capped;
                subtitles = new ArrayList<>(info.getSubtitles());
                subtitles.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                selectedQuality = defaultQuality(qualities);
                durationMs = info.getDuration() > 0 ? info.getDuration() * 1000 : 0;
                ladder = audios.isEmpty() || durationMs <= 0 ? new ArrayList<>() : adaptiveLadder(info.getVideoOnlyStreams());
                post(() -> {
                    loading.setVisibility(GONE); message.setVisibility(GONE);
                    qualityButton.setEnabled(true); captionsButton.setEnabled(!subtitles.isEmpty());
                    playSelection(initialPositionMs);
                    qualityButton.requestFocus();
                });
            } catch (Exception error) {
                post(() -> reportError("extrakce: " + safeMessage(error)));
            }
        });
    }

    private static synchronized void initializeExtractor() {
        if (extractorReady) return;
        NewPipe.init(new ExtractorDownloader(), new Localization("cs", "CZ"), new ContentCountry("CZ"));
        extractorReady = true;
    }

    private void playSelection(long positionMs) {
        if (player != null) player.release();
        endedReported = false;
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.youtube.com/");
        headers.put("Origin", "https://www.youtube.com");
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ExtractorDownloader.USER_AGENT)
                .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(getContext(), http);
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(data);
        List<MediaItem.SubtitleConfiguration> subtitleConfig = new ArrayList<>();
        if (selectedSubtitle >= 0 && selectedSubtitle < subtitles.size()) {
            SubtitlesStream subtitle = subtitles.get(selectedSubtitle);
            subtitleConfig.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.getContent()))
                    .setMimeType(subtitleMime(subtitle)).setLanguage(subtitle.getLanguageTag())
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build());
        }
        MediaSource source = null;
        adaptiveActive = false;
        if (ladder.size() >= 2) {
            try {
                // One DASH adaptation set with every quality: the player switches between them at
                // segment boundaries, without restarting or a visible stall.
                // Audio goes into the same manifest when it has byte ranges: every download is then a
                // short ranged request. One open-ended request for the whole audio file gets throttled
                // by YouTube, which stalled playback after a few seconds and dragged the bandwidth
                // estimate (and so the video quality) down.
                // With captions on, keep the progressive audio item: it is what side-loads the subtitles.
                AudioStream dashAudio = subtitleConfig.isEmpty() && hasSegmentIndex(audios.get(0)) ? audios.get(0) : null;
                byte[] mpd = dashManifest(ladder, dashAudio, durationMs).getBytes(StandardCharsets.UTF_8);
                DashManifest manifest = new DashManifestParser()
                        .parse(Uri.parse("https://www.youtube.com/"), new ByteArrayInputStream(mpd));
                MediaSource dash = new DashMediaSource.Factory(data).createMediaSource(manifest,
                        new MediaItem.Builder().setUri(Uri.EMPTY).setMimeType(MimeTypes.APPLICATION_MPD).build());
                if (dashAudio != null) {
                    source = dash;
                } else {
                    // Subtitles ride along with the audio item; DefaultMediaSourceFactory side-loads them.
                    MediaSource audio = factory.createMediaSource(new MediaItem.Builder()
                            .setUri(audios.get(0).getContent()).setSubtitleConfigurations(subtitleConfig).build());
                    source = new MergingMediaSource(dash, audio);
                }
                adaptiveActive = true;
            } catch (Exception adaptiveFailed) {
                ladder = new ArrayList<>();
            }
        }
        VideoStream video = qualities.get(selectedQuality);
        if (source == null) {
            source = factory.createMediaSource(new MediaItem.Builder().setUri(video.getContent())
                    .setSubtitleConfigurations(subtitleConfig).build());
            if (video.isVideoOnly() && !audios.isEmpty()) {
                MediaSource audio = factory.createMediaSource(MediaItem.fromUri(audios.get(0).getContent()));
                source = new MergingMediaSource(source, audio);
            }
        }
        DefaultBandwidthMeter meter = bandwidthMeter(getContext());
        DefaultTrackSelector selector = new DefaultTrackSelector(getContext(), new AdaptiveTrackSelection.Factory(
                MIN_BUFFER_FOR_QUALITY_INCREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION));
        player = new ExoPlayer.Builder(getContext()).setBandwidthMeter(meter).setTrackSelector(selector).build();
        if (adaptiveActive) applyQualityConstraint();
        currentHeight = 0;
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (adaptiveActive) {
                    // Adaptive stream rejected (e.g. codec): retry once with the single-file stream.
                    long position = position();
                    ladder = new ArrayList<>();
                    post(() -> playSelection(position));
                    return;
                }
                reportError("Media3: " + error.getErrorCodeName() + " – " + safeMessage(error));
            }
            @Override public void onPlaybackStateChanged(int state) {
                updateKeepScreenOn();
                if (state == Player.STATE_ENDED && !endedReported) { endedReported = true; listener.onEnded(); }
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) { updateKeepScreenOn(); }
            @Override public void onVideoSizeChanged(VideoSize size) {
                if (size.height > 0) { currentHeight = size.height; updateQualityLabel(); }
            }
        });
        playerView.setPlayer(player); player.setMediaSource(source); player.prepare();
        player.seekTo(positionMs); player.setPlayWhenReady(true);
        updateQualityLabel();
        captionsButton.setText(selectedSubtitle < 0 ? "Titulky: vypnuto"
                : "Titulky: " + subtitles.get(selectedSubtitle).getDisplayLanguageName());
        showActions();
    }

    private void updateQualityLabel() {
        if (!adaptiveActive) { qualityButton.setText("Kvalita: " + qualityLabel(qualities.get(selectedQuality))); return; }
        String now = currentHeight > 0 ? currentHeight + "p" : "…";
        qualityButton.setText(fixedHeight == 0 ? "Kvalita: auto (" + now + ")" : "Kvalita: " + fixedHeight + "p");
    }

    /** Automatic: the whole ladder up to 720p. Fixed: only the chosen height. Applied without restarting. */
    private void applyQualityConstraint() {
        TrackSelectionParameters.Builder params = player.getTrackSelectionParameters().buildUpon()
                .clearVideoSizeConstraints();
        if (fixedHeight > 0) params.setMaxVideoSize(Integer.MAX_VALUE, fixedHeight).setMinVideoSize(0, fixedHeight);
        else params.setMaxVideoSize(Integer.MAX_VALUE, MAX_HEIGHT);
        player.setTrackSelectionParameters(params.build());
    }

    private void chooseQuality() {
        if (adaptiveActive) { chooseAdaptiveQuality(); return; }
        String[] labels = new String[qualities.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = qualityLabel(qualities.get(i));
        new AlertDialog.Builder(getContext()).setTitle("Kvalita videa")
                .setSingleChoiceItems(labels, selectedQuality, (dialog, which) -> {
                    long position = position(); selectedQuality = which; playSelection(position); dialog.dismiss();
                }).setNegativeButton("Zrušit", null).show();
    }

    private void chooseAdaptiveQuality() {
        List<Integer> heights = new ArrayList<>();
        for (int i = ladder.size() - 1; i >= 0; i--) heights.add(height(ladder.get(i)));
        String[] labels = new String[heights.size() + 1];
        labels[0] = "Automaticky (podle rychlosti sítě, max 720p)";
        int checked = 0;
        for (int i = 0; i < heights.size(); i++) {
            labels[i + 1] = heights.get(i) + "p";
            if (heights.get(i) == fixedHeight) checked = i + 1;
        }
        new AlertDialog.Builder(getContext()).setTitle("Kvalita videa")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    fixedHeight = which == 0 ? 0 : heights.get(which - 1);
                    if (player != null) applyQualityConstraint();
                    updateQualityLabel(); dialog.dismiss();
                }).setNegativeButton("Zrušit", null).show();
    }

    /**
     * Picks video-only progressive streams (≤720p) of one codec that carry byte ranges for their
     * init and index boxes, which is what a DASH on-demand representation needs. Prefers H.264,
     * which every TV decodes in hardware. Returns fewer than two streams when adaptive is not possible.
     */
    static List<VideoStream> adaptiveLadder(List<VideoStream> streams) {
        Map<String, Map<Integer, VideoStream>> byCodec = new HashMap<>();
        for (VideoStream stream : streams) {
            if (!stream.isVideoOnly() || !stream.isUrl() || stream.getContent().isEmpty()) continue;
            if (stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || stream.getFormat() == null) continue;
            ItagItem itag = stream.getItagItem();
            String codec = stream.getCodec();
            int h = height(stream);
            if (itag == null || codec == null || codec.isEmpty() || h <= 0 || h > MAX_HEIGHT) continue;
            if (stream.getInitStart() < 0 || stream.getInitEnd() <= stream.getInitStart()
                    || stream.getIndexStart() <= stream.getInitEnd() || stream.getIndexEnd() <= stream.getIndexStart()) continue;
            if (bandwidth(stream) <= 0) continue;
            String family = stream.getFormat().getMimeType() + "|" + codec.split("\\.")[0].toLowerCase(Locale.ROOT);
            Map<Integer, VideoStream> perHeight = byCodec.computeIfAbsent(family, k -> new TreeMap<>());
            VideoStream existing = perHeight.get(h);
            if (existing == null || stream.getFps() > existing.getFps()) perHeight.put(h, stream);
        }
        for (String preferred : new String[]{"avc1", "vp09", "vp9", "av01"}) {
            for (Map.Entry<String, Map<Integer, VideoStream>> entry : byCodec.entrySet()) {
                if (entry.getKey().endsWith("|" + preferred) && entry.getValue().size() >= 2)
                    return new ArrayList<>(entry.getValue().values());
            }
        }
        return new ArrayList<>();
    }

    /**
     * One meter for the whole app: the measured speed carries over to the next video (and to the
     * restart after a quality/captions change), so later videos start at the right quality at once.
     */
    private static synchronized DefaultBandwidthMeter bandwidthMeter(Context context) {
        if (sharedMeter == null) {
            sharedMeter = new DefaultBandwidthMeter.Builder(context.getApplicationContext())
                    .setInitialBitrateEstimate(START_BITRATE_ESTIMATE).build();
        }
        return sharedMeter;
    }

    private static boolean hasSegmentIndex(AudioStream stream) {
        return stream.isUrl() && !stream.getContent().isEmpty() && stream.getFormat() != null
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && stream.getInitStart() >= 0 && stream.getInitEnd() > stream.getInitStart()
                && stream.getIndexStart() > stream.getInitEnd() && stream.getIndexEnd() > stream.getIndexStart();
    }

    private static int audioBandwidth(AudioStream stream) {
        if (stream.getBitrate() > 0) return stream.getBitrate();
        if (stream.getAverageBitrate() > 0) return stream.getAverageBitrate() * 1000;
        return 128_000;
    }

    static String dashManifest(List<VideoStream> ladder, long durationMs) {
        return dashManifest(ladder, null, durationMs);
    }

    static String dashManifest(List<VideoStream> ladder, AudioStream audio, long durationMs) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" minBufferTime=\"PT1.5S\"")
                .append(" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" mediaPresentationDuration=\"PT")
                .append(String.format(Locale.US, "%.3f", durationMs / 1000d)).append("S\"><Period>")
                .append("<AdaptationSet contentType=\"video\" subsegmentAlignment=\"true\" mimeType=\"")
                .append(xmlEscape(ladder.get(0).getFormat().getMimeType())).append("\">");
        for (VideoStream stream : ladder) {
            xml.append("<Representation id=\"").append(stream.getItag()).append("\" codecs=\"")
                    .append(xmlEscape(stream.getCodec())).append("\" bandwidth=\"").append(bandwidth(stream))
                    .append("\" height=\"").append(height(stream)).append('"');
            if (stream.getWidth() > 0) xml.append(" width=\"").append(stream.getWidth()).append('"');
            if (stream.getFps() > 0) xml.append(" frameRate=\"").append(stream.getFps()).append('"');
            xml.append("><BaseURL>").append(xmlEscape(stream.getContent())).append("</BaseURL>")
                    .append("<SegmentBase indexRange=\"").append(stream.getIndexStart()).append('-').append(stream.getIndexEnd())
                    .append("\"><Initialization range=\"").append(stream.getInitStart()).append('-').append(stream.getInitEnd())
                    .append("\"/></SegmentBase></Representation>");
        }
        xml.append("</AdaptationSet>");
        if (audio != null) {
            xml.append("<AdaptationSet contentType=\"audio\" subsegmentAlignment=\"true\" mimeType=\"")
                    .append(xmlEscape(audio.getFormat().getMimeType())).append("\">")
                    .append("<Representation id=\"a").append(audio.getItag()).append('"');
            if (audio.getCodec() != null && !audio.getCodec().isEmpty())
                xml.append(" codecs=\"").append(xmlEscape(audio.getCodec())).append('"');
            xml.append(" bandwidth=\"").append(audioBandwidth(audio)).append("\"><BaseURL>")
                    .append(xmlEscape(audio.getContent())).append("</BaseURL>")
                    .append("<SegmentBase indexRange=\"").append(audio.getIndexStart()).append('-').append(audio.getIndexEnd())
                    .append("\"><Initialization range=\"").append(audio.getInitStart()).append('-').append(audio.getInitEnd())
                    .append("\"/></SegmentBase></Representation></AdaptationSet>");
        }
        return xml.append("</Period></MPD>").toString();
    }

    private static int bandwidth(VideoStream stream) {
        if (stream.getBitrate() > 0) return stream.getBitrate();
        ItagItem itag = stream.getItagItem();
        return itag == null ? 0 : itag.getAverageBitrate();
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void chooseCaptions() {
        String[] labels = new String[subtitles.size() + 1]; labels[0] = "Vypnuto";
        for (int i = 0; i < subtitles.size(); i++) labels[i + 1] = subtitles.get(i).getDisplayLanguageName()
                + (subtitles.get(i).isAutoGenerated() ? " (automatické)" : "");
        new AlertDialog.Builder(getContext()).setTitle("Titulky")
                .setSingleChoiceItems(labels, selectedSubtitle + 1, (dialog, which) -> {
                    long position = position(); selectedSubtitle = which - 1; playSelection(position); dialog.dismiss();
                }).setNegativeButton("Zrušit", null).show();
    }

    void reportProgress(boolean pause) {
        if (player == null) return;
        if (pause) player.pause();
        listener.onProgress(player.getCurrentPosition() / 1000d, Math.max(0, player.getDuration()) / 1000d,
                player.getPlaybackState() == Player.STATE_ENDED);
    }
    void resumePlayback() { if (player != null) player.play(); }

    /** Shows a countdown over the finished video, then runs {@code next}. Back (closing the player) cancels it. */
    void showUpNext(String title, Runnable next) {
        cancelUpNext();
        setKeepScreenOn(true);
        final int[] seconds = {5};
        message.setBackgroundColor(Color.argb(200, 0, 0, 0));
        message.setVisibility(VISIBLE);
        upNextTick = new Runnable() {
            @Override public void run() {
                if (seconds[0] <= 0) { upNextTick = null; next.run(); return; }
                message.setText("Další video za " + seconds[0] + " s:\n" + title + "\n\nZpět = ukončit");
                seconds[0]--;
                postDelayed(this, 1000);
            }
        };
        upNextTick.run();
    }

    void cancelUpNext() {
        if (upNextTick != null) {
            removeCallbacks(upNextTick); upNextTick = null;
            message.setVisibility(GONE); updateKeepScreenOn();
        }
    }

    /** Any remote key shows the Quality/Captions tiles again; they hide after 10 s without input. */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean navigation = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER;
        if (actions.getVisibility() != VISIBLE && player != null && navigation) {
            // First press only reveals the tiles, so it does not accidentally trigger an action.
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                showActions();
                qualityButton.requestFocus();
            }
            return true;
        }
        scheduleHideActions();
        return super.dispatchKeyEvent(event);
    }

    private void showActions() {
        actions.animate().cancel();
        actions.setAlpha(1f);
        actions.setVisibility(VISIBLE);
        scheduleHideActions();
    }

    private void scheduleHideActions() {
        removeCallbacks(hideActions);
        postDelayed(hideActions, ACTIONS_HIDE_DELAY_MS);
    }

    private void hideActions() {
        if (player == null) return; // keep visible until playback starts
        actions.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            actions.setVisibility(GONE);
            playerView.hideController();
            playerView.requestFocus();
        }).start();
    }

    /** Keep the display awake while a video is playing or buffering; allow the screensaver when paused or finished. */
    private void updateKeepScreenOn() {
        if (player == null) { setKeepScreenOn(false); return; }
        int state = player.getPlaybackState();
        setKeepScreenOn(player.getPlayWhenReady()
                && state != Player.STATE_ENDED && state != Player.STATE_IDLE);
    }
    private void reportError(String detail) {
        if (errorReported) return;
        errorReported = true; loading.setVisibility(GONE);
        message.setVisibility(VISIBLE); message.setText("Nativní stream selhal, přepínám na záložní přehrávač…\n" + detail);
        listener.onError(detail);
    }

    void release() {
        reportProgress(true);
        if (player != null) { player.release(); player = null; }
        setKeepScreenOn(false);
        removeCallbacks(hideActions);
        cancelUpNext();
        loader.shutdownNow();
    }
    private long position() { return player == null ? initialPositionMs : player.getCurrentPosition(); }
    private static int defaultQuality(List<VideoStream> streams) {
        for (int i = 0; i < streams.size(); i++) if (height(streams.get(i)) <= MAX_HEIGHT) return i;
        return streams.size() - 1;
    }
    private static int height(VideoStream stream) {
        if (stream.getHeight() > 0) return stream.getHeight();
        try { return Integer.parseInt(stream.getResolution().replaceAll("[^0-9].*", "")); }
        catch (Exception ignored) { return 0; }
    }
    private static String qualityLabel(VideoStream stream) {
        String label = stream.getResolution().isEmpty() ? height(stream) + "p" : stream.getResolution();
        if (stream.getFps() > 30) label += " " + stream.getFps() + " fps";
        if (stream.getCodec() != null) label += " · " + stream.getCodec();
        return label;
    }
    private static String subtitleMime(SubtitlesStream stream) {
        String extension = stream.getExtension().toLowerCase(Locale.ROOT);
        if ("ttml".equals(extension)) return MimeTypes.APPLICATION_TTML;
        if ("srt".equals(extension)) return MimeTypes.APPLICATION_SUBRIP;
        return MimeTypes.TEXT_VTT;
    }
    private static String safeMessage(Exception error) {
        String value = error.getMessage(); return value == null || value.length() > 180 ? error.getClass().getSimpleName() : value;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
