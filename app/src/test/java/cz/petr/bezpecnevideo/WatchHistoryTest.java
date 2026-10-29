package cz.petr.bezpecnevideo;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class WatchHistoryTest {
    private final VideoStore.Video video = new VideoStore.Video("16LuvR2CARA", "Test", "UCY1kMZp36IQSyNx_9h4mpCg");
    private SharedPreferences memoryPrefs() {
        Map<String, String> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putString")) { values.put((String) args[0], (String) args[1]); return proxy; }
                    if (method.getName().equals("apply")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getString")) return values.getOrDefault(args[0], (String) args[1]);
                    if (method.getName().equals("edit")) return editor;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test public void independentProfilesSurviveStoreRecreation() {
        SharedPreferences prefs = memoryPrefs();
        WatchHistory first = new WatchHistory(prefs);
        first.save("matysek", video, 120, 600, false);
        first.save("filipek", video, 300, 600, false);
        WatchHistory reopened = new WatchHistory(prefs);
        assertEquals(118, reopened.find("matysek", video.id).resumeAt(), 0);
        assertEquals(298, reopened.find("filipek", video.id).resumeAt(), 0);
        assertEquals(video.channelId, reopened.find("matysek", video.id).video.channelId);
    }
    @Test public void initializationAndInvalidSamplesDoNotEraseResume() {
        WatchHistory history = new WatchHistory(memoryPrefs());
        history.save("matysek", video, 120, 600, false);
        history.save("matysek", video, 0, 600, false);
        history.save("matysek", video, Double.NaN, 600, false);
        history.save("matysek", video, 50, 0, false);
        history.save("matysek", video, 700, 600, false);
        assertEquals(120, history.find("matysek", video.id).position, 0);
    }
    @Test public void finishedVideosRestartAndSeekingBackUpdatesResume() {
        WatchHistory history = new WatchHistory(memoryPrefs());
        history.save("matysek", video, 590, 600, false);
        assertTrue(history.find("matysek", video.id).completed);
        assertEquals(0, history.find("matysek", video.id).resumeAt(), 0);
        history.save("matysek", video, 180, 600, false);
        assertFalse(history.find("matysek", video.id).completed);
        assertEquals(178, history.find("matysek", video.id).resumeAt(), 0);
        history.save("matysek", video, 570, 600, true);
        assertTrue(history.find("matysek", video.id).completed);
    }
    @Test public void resetOnlyAffectsSelectedProfile() {
        WatchHistory history = new WatchHistory(memoryPrefs());
        history.save("matysek", video, 100, 600, false);
        history.save("filipek", video, 200, 600, false);
        history.remove("matysek", video.id);
        assertNull(history.find("matysek", video.id));
        assertNotNull(history.find("filipek", video.id));
    }
    @Test public void corruptEntriesAreIgnored() {
        assertTrue(WatchHistory.decode("broken").isEmpty());
        assertTrue(WatchHistory.decode("[{\"id\":\"16LuvR2CARA\",\"position\":-3,\"duration\":600,\"title\":\"x\"}]").isEmpty());
    }
    @Test public void historyIsBoundedAndMostRecentFirst() {
        WatchHistory history = new WatchHistory(memoryPrefs());
        for (int i = 0; i < 120; i++) history.save("matysek",
                new VideoStore.Video(String.format(java.util.Locale.ROOT, "%011d", i), "Test"), 30, 600, false);
        assertEquals(100, history.entries("matysek").size());
        assertEquals("00000000119", history.entries("matysek").get(0).video.id);
    }
}
