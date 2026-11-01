package cz.petr.bezpecnevideo;

import java.util.Comparator;
import java.util.Locale;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

final class AudioPreference {
    private AudioPreference() { }

    static boolean isCzech(AudioStream stream) {
        Locale locale = stream.getAudioLocale();
        if (locale == null) return false;
        String language = locale.getLanguage();
        return "cs".equals(language) || "ces".equals(language) || "cze".equals(language);
    }

    static final Comparator<AudioStream> ORDER = Comparator
            .comparingInt((AudioStream stream) -> isCzech(stream) ? 0 : 1)
            .thenComparingInt(stream -> stream.getAudioTrackType() == AudioTrackType.DESCRIPTIVE ? 1 : 0)
            .thenComparingInt(stream -> stream.getAudioTrackType() == AudioTrackType.ORIGINAL ? 0 : 1)
            .thenComparing(Comparator.comparingInt(AudioStream::getAverageBitrate).reversed());
}
