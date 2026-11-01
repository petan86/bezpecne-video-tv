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
    @Test public void neverFallsBackToUnrelatedVideoOwner() {
        assertThrows(Exception.class, () -> ChannelIdentity.fromPage("{\"channelId\":\"UCSVSJ1OCSv5ClFgKFTHIDew\"}"));
    }
}
