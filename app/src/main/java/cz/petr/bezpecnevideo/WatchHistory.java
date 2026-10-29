package cz.petr.bezpecnevideo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class WatchHistory {
    private final SharedPreferences prefs;
    WatchHistory(Context context) { prefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE); }
    WatchHistory(SharedPreferences prefs) { this.prefs = prefs; }

    List<Entry> entries(String profile) { return decode(prefs.getString(profile, "[]")); }

    Entry find(String profile, String id) {
        for (Entry entry : entries(profile)) if (entry.video.id.equals(id)) return entry;
        return null;
    }

    void save(String profile, VideoStore.Video video, double position, double duration, boolean ended) {
        if (profile == null || !Double.isFinite(position) || !Double.isFinite(duration)
                || position < 0 || duration <= 0 || position > duration + 2) return;
        boolean completed = completed(position, duration, ended);
        if (!completed && position < 3) return;
        List<Entry> current = entries(profile);
        current.removeIf(entry -> entry.video.id.equals(video.id));
        current.add(0, new Entry(video, Math.min(position, duration), duration, completed));
        write(profile, current);
    }

    void remove(String profile, String id) {
        List<Entry> current = entries(profile);
        current.removeIf(entry -> entry.video.id.equals(id));
        write(profile, current);
    }

    private void write(String profile, List<Entry> entries) {
        prefs.edit().putString(profile, encode(entries)).apply();
    }

    static boolean completed(double position, double duration, boolean ended) {
        return ended || (duration > 0 && position >= duration * 0.95);
    }

    static String encode(List<Entry> entries) {
        JSONArray result = new JSONArray();
        for (Entry entry : entries.subList(0, Math.min(100, entries.size()))) {
            try {
                JSONObject value = new JSONObject();
                value.put("id", entry.video.id);
                value.put("title", entry.video.title);
                value.put("channel", entry.video.channelId);
                value.put("position", entry.position);
                value.put("duration", entry.duration);
                value.put("completed", entry.completed);
                result.put(value);
            } catch (Exception ignored) { }
        }
        return result.toString();
    }

    static List<Entry> decode(String value) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < Math.min(100, array.length()); i++) {
                try {
                    JSONObject item = array.getJSONObject(i);
                    String id = item.getString("id");
                    double position = item.getDouble("position"), duration = item.getDouble("duration");
                    if (!id.matches("[A-Za-z0-9_-]{11}") || !Double.isFinite(position)
                            || !Double.isFinite(duration) || position < 0 || duration <= 0 || position > duration) continue;
                    result.add(new Entry(new VideoStore.Video(id, item.getString("title"), item.optString("channel")),
                            position, duration, item.optBoolean("completed")));
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return result;
    }

    static final class Entry {
        final VideoStore.Video video;
        final double position, duration;
        final boolean completed;
        Entry(VideoStore.Video video, double position, double duration, boolean completed) {
            this.video = video; this.position = position; this.duration = duration; this.completed = completed;
        }
        double resumeAt() { return completed ? 0 : Math.max(0, position - 2); }
    }
}
