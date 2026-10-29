package cz.petr.bezpecnevideo;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoLinkTest {
    @Test public void acceptsSharedWatchShortsLiveAndMobileLinks() {
        for (String link : new String[]{"16LuvR2CARA", "https://youtu.be/16LuvR2CARA?si=shared",
                "https://www.youtube.com/watch?feature=shared&v=16LuvR2CARA&t=42",
                "https://m.youtube.com/watch?v=16LuvR2CARA", "youtube.com/shorts/16LuvR2CARA",
                "https://www.youtube.com/live/16LuvR2CARA?si=shared", "https://www.youtube.com/embed/16LuvR2CARA"}) {
            assertEquals(link, "16LuvR2CARA", VideoStore.extractVideoId(link));
        }
    }
    @Test public void rejectsSpoofedHostsPlaylistsAndInvalidIds() {
        for (String link : new String[]{"https://evil.youtube.com/watch?v=16LuvR2CARA",
                "https://notyoutube.com/watch?v=16LuvR2CARA", "https://evil.invalid/youtube.com/watch?v=16LuvR2CARA",
                "https://youtube.com@evil.invalid/watch?v=16LuvR2CARA", "https://youtube.com/playlist?list=abc",
                "https://youtu.be/16LuvR2CARAextra", "https://youtube.com/watch?v=bad", "javascript:alert(1)"}) {
            assertNull(link, VideoStore.extractVideoId(link));
        }
    }
}
