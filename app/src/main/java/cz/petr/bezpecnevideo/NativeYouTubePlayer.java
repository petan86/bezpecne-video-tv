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
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.PlayerView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, D-pad friendly YouTube playback backed by Media3 and NewPipe Extractor. */
final class NativeYouTubePlayer extends FrameLayout {
    interface Listener {
        void onProgress(double positionSeconds, double durationSeconds, boolean ended);
        void onError(String detail);
    }

    private static boolean extractorReady;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final PlayerView playerView;
    private final ProgressBar loading;
    private final TextView message;
    private final Button qualityButton;
    private final Button captionsButton;
    private final Listener listener;
    private ExoPlayer player;
    private List<VideoStream> qualities = new ArrayList<>();
    private List<AudioStream> audios = new ArrayList<>();
    private List<SubtitlesStream> subtitles = new ArrayList<>();
    private int selectedQuality;
    private int selectedSubtitle = -1;
    private long initialPositionMs;
    private boolean errorReported;

    NativeYouTubePlayer(Context context, String videoId, double startSeconds, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(context);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(false);
        addView(playerView, new LayoutParams(-1, -1));

        loading = new ProgressBar(context);
        LayoutParams spinner = new LayoutParams(dp(64), dp(64), Gravity.CENTER);
        addView(loading, spinner);
        message = new TextView(context);
        message.setTextColor(Color.WHITE); message.setTextSize(18); message.setGravity(Gravity.CENTER);
        message.setPadding(dp(24), dp(18), dp(24), dp(18));
        addView(message, new LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout actions = new LinearLayout(context);
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
                List<VideoStream> found = new ArrayList<>();
                found.addAll(info.getVideoStreams()); found.addAll(info.getVideoOnlyStreams());
                found.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                found.sort(Comparator.comparingInt(NativeYouTubePlayer::height).reversed()
                        .thenComparingInt(VideoStream::getFps).reversed());
                Set<String> labels = new HashSet<>();
                List<VideoStream> unique = new ArrayList<>();
                for (VideoStream stream : found) {
                    String key = stream.getResolution() + ":" + stream.getFps();
                    if (labels.add(key)) unique.add(stream);
                }
                if (unique.isEmpty()) throw new IllegalStateException("YouTube neposkytl použitelnou video stopu.");
                qualities = unique; audios = new ArrayList<>(info.getAudioStreams());
                audios.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                audios.sort(Comparator.comparingInt(AudioStream::getAverageBitrate).reversed());
                subtitles = new ArrayList<>(info.getSubtitles());
                subtitles.removeIf(stream -> !stream.isUrl() || stream.getContent().isEmpty());
                selectedQuality = defaultQuality(qualities);
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
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.youtube.com/");
        headers.put("Origin", "https://www.youtube.com");
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/131 Safari/537.36")
                .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(getContext(), http);
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(data);
        VideoStream video = qualities.get(selectedQuality);
        MediaItem.Builder videoItem = new MediaItem.Builder().setUri(video.getContent());
        if (selectedSubtitle >= 0 && selectedSubtitle < subtitles.size()) {
            SubtitlesStream subtitle = subtitles.get(selectedSubtitle);
            videoItem.setSubtitleConfigurations(java.util.Collections.singletonList(
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.getContent()))
                            .setMimeType(subtitleMime(subtitle)).setLanguage(subtitle.getLanguageTag())
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        }
        MediaSource source = factory.createMediaSource(videoItem.build());
        if (video.isVideoOnly() && !audios.isEmpty()) {
            MediaSource audio = factory.createMediaSource(MediaItem.fromUri(audios.get(0).getContent()));
            source = new MergingMediaSource(source, audio);
        }
        player = new ExoPlayer.Builder(getContext()).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                reportError("Media3: " + error.getErrorCodeName() + " – " + safeMessage(error));
            }
        });
        playerView.setPlayer(player); player.setMediaSource(source); player.prepare();
        player.seekTo(positionMs); player.setPlayWhenReady(true);
        qualityButton.setText("Kvalita: " + qualityLabel(video));
        captionsButton.setText(selectedSubtitle < 0 ? "Titulky: vypnuto"
                : "Titulky: " + subtitles.get(selectedSubtitle).getDisplayLanguageName());
    }

    private void chooseQuality() {
        String[] labels = new String[qualities.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = qualityLabel(qualities.get(i));
        new AlertDialog.Builder(getContext()).setTitle("Kvalita videa")
                .setSingleChoiceItems(labels, selectedQuality, (dialog, which) -> {
                    long position = position(); selectedQuality = which; playSelection(position); dialog.dismiss();
                }).setNegativeButton("Zrušit", null).show();
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
    private void reportError(String detail) {
        if (errorReported) return;
        errorReported = true; loading.setVisibility(GONE);
        message.setVisibility(VISIBLE); message.setText("Nativní stream selhal, přepínám na záložní přehrávač…\n" + detail);
        listener.onError(detail);
    }

    void release() { reportProgress(true); if (player != null) { player.release(); player = null; } loader.shutdownNow(); }
    private long position() { return player == null ? initialPositionMs : player.getCurrentPosition(); }
    private static int defaultQuality(List<VideoStream> streams) {
        for (int i = 0; i < streams.size(); i++) if (height(streams.get(i)) <= 1080) return i;
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
