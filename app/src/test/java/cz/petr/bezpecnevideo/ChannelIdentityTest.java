package cz.petr.bezpecnevideo;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelIdentityTest {
    @Test public void ignoresRecommendedEnglishChannelBeforeCzechOwner() throws Exception {
        String html = "<script>var data={\"recommendation\":{\"channelId\":\"UCSVSJ1OCSv5ClFgKFTHIDew\"},"
                + "\"metadata\":{\"channelMetadataRenderer\":{\"title\":\"Tlapková Patrola - PAW Patrol Czechia\","
                + "\"description\":\"Example { braces } and \\\"quotes\\\"\",\"externalId\":\"UCW1ve7KG4v-MSKkaf0Oq1Mg\"}}};</script>";
        VideoStore.Channel channel = ChannelIdentity.fromPage(html);
        assertEquals("UCW1ve7KG4v-MSKkaf0Oq1Mg", channel.id);
        assertEquals("Tlapková Patrola - PAW Patrol Czechia", channel.title);
    }
    @Test public void recognisesChannelLinks() {
        assertEquals("https://www.youtube.com/channel/UCzkzjvNIraULEHRBseee5cQ",
                ChannelIdentity.channelUrl("https://www.youtube.com/channel/UCzkzjvNIraULEHRBseee5cQ"));
        assertEquals("https://www.youtube.com/channel/UCzkzjvNIraULEHRBseee5cQ",
                ChannelIdentity.channelUrl(" m.youtube.com/channel/UCzkzjvNIraULEHRBseee5cQ/videos?view=0 "));
        assertEquals("https://www.youtube.com/@CrunchLabs", ChannelIdentity.channelUrl("https://youtube.com/@CrunchLabs/featured"));
        assertEquals("https://www.youtube.com/c/Name", ChannelIdentity.channelUrl("https://www.youtube.com/c/Name"));
        assertEquals("https://www.youtube.com/user/Name", ChannelIdentity.channelUrl("https://www.youtube.com/user/Name"));
        assertEquals("UCzkzjvNIraULEHRBseee5cQ",
                ChannelIdentity.directId(ChannelIdentity.channelUrl("https://www.youtube.com/channel/UCzkzjvNIraULEHRBseee5cQ")));
    }
    @Test public void rejectsNonChannelLinks() {
        assertNull(ChannelIdentity.channelUrl("https://www.youtube.com/watch?v=16LuvR2CARA"));
        assertNull(ChannelIdentity.channelUrl("https://youtu.be/16LuvR2CARA"));
        assertNull(ChannelIdentity.channelUrl("https://evil.example/channel/UCzkzjvNIraULEHRBseee5cQ"));
        assertNull(ChannelIdentity.channelUrl("https://www.youtube.com/channel/UCshort"));
        assertNull(ChannelIdentity.channelUrl(""));
        assertNull(ChannelIdentity.channelUrl(null));
    }
    @Test public void neverFallsBackToUnrelatedVideoOwner() {
        assertThrows(Exception.class, () -> ChannelIdentity.fromPage("{\"channelId\":\"UCSVSJ1OCSv5ClFgKFTHIDew\"}"));
    }
}
