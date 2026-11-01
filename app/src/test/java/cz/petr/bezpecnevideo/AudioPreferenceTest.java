package cz.petr.bezpecnevideo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

public class AudioPreferenceTest {
    private AudioStream track(String language, int bitrate, AudioTrackType type) {
        return new AudioStream.Builder().setId("test").setContent("https://example.com/audio", true)
                .setAverageBitrate(bitrate).setAudioLocale(language == null ? null : Locale.forLanguageTag(language))
                .setAudioTrackType(type).build();
    }

    @Test public void czechDubWinsOverHigherBitrateForeignOriginal() {
        AudioStream english = track("en", 256, AudioTrackType.ORIGINAL);
        AudioStream czech = track("cs-CZ", 128, AudioTrackType.DUBBED);
        List<AudioStream> streams = new ArrayList<>(List.of(english, czech));
        streams.sort(AudioPreference.ORDER);
        assertSame(czech, streams.get(0));
    }

    @Test public void fallbackPrefersOriginalAndHandlesMissingLanguage() {
        AudioStream original = track(null, 128, AudioTrackType.ORIGINAL);
        AudioStream dubbed = track("de", 256, AudioTrackType.DUBBED);
        assertTrue(AudioPreference.ORDER.compare(original, dubbed) < 0);
        assertFalse(AudioPreference.isCzech(original));
    }

    @Test public void regularCzechWinsOverAudioDescription() {
        assertTrue(AudioPreference.ORDER.compare(track("cs", 128, AudioTrackType.DUBBED),
                track("cs", 256, AudioTrackType.DESCRIPTIVE)) < 0);
    }
}
