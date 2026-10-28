package cz.petr.bezpecnevideo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class VideoStore {
    static final String MATYSEK = "matysek";
    static final String FILIPEK = "filipek";
    private static final String DEFAULT_PIN = "2468";
    private final SharedPreferences prefs;

    VideoStore(Context context) {
        prefs = context.getSharedPreferences("rodicovska_sprava", Context.MODE_PRIVATE);
        if (!prefs.contains("pin_hash")) setPin(DEFAULT_PIN);
    }

    List<Video> videos(String profile) {
        List<Video> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs.getString("videos_" + profile, "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                result.add(new Video(item.getString("id"), item.optString("title", "Povolené video")));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    List<Channel> channels(String profile) {
        List<Channel> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs.getString("channels_" + profile, "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                result.add(new Channel(item.getString("id"), item.optString("title", "Povolený kanál")));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    boolean addVideo(String profile, String url, String title) {
        String id = extractVideoId(url);
        if (id == null) return false;
        List<Video> current = videos(profile);
        for (Video video : current) if (video.id.equals(id)) return true;
        current.add(new Video(id, title.trim().isEmpty() ? "Povolené video" : title.trim()));
        save(profile, current);
        return true;
    }

    void removeVideo(String profile, String id) {
        List<Video> current = videos(profile);
        current.removeIf(video -> video.id.equals(id));
        save(profile, current);
    }

    void addChannel(String profile, String channelId, String title) {
        if (channelId == null || !channelId.matches("UC[A-Za-z0-9_-]{22}")) return;
        List<Channel> current = channels(profile);
        for (Channel channel : current) if (channel.id.equals(channelId)) return;
        current.add(new Channel(channelId, title == null || title.trim().isEmpty() ? "Povolený kanál" : title.trim()));
        saveChannels(profile, current);
    }

    void removeChannel(String profile, String channelId) {
        List<Channel> current = channels(profile);
        current.removeIf(channel -> channel.id.equals(channelId));
        saveChannels(profile, current);
    }

    boolean checkPin(String pin) {
        String salt = prefs.getString("pin_salt", "");
        return prefs.getString("pin_hash", "").equals(hash(pin, salt));
    }

    void setPin(String pin) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(pin, salt)).apply();
    }

    private void save(String profile, List<Video> videos) {
        JSONArray values = new JSONArray();
        for (Video video : videos) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", video.id);
                item.put("title", video.title);
                values.put(item);
            } catch (JSONException ignored) { }
        }
        prefs.edit().putString("videos_" + profile, values.toString()).apply();
    }

    private void saveChannels(String profile, List<Channel> channels) {
        JSONArray values = new JSONArray();
        for (Channel channel : channels) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", channel.id);
                item.put("title", channel.title);
                values.put(item);
            } catch (JSONException ignored) { }
        }
        prefs.edit().putString("channels_" + profile, values.toString()).apply();
    }

    static String extractVideoId(String value) {
        if (value == null) return null;
        String text = value.trim();
        String id = null;
        if (text.matches("[A-Za-z0-9_-]{11}")) id = text;
        else {
            String[] markers = {"youtu.be/", "youtube.com/watch?v=", "youtube.com/shorts/", "youtube.com/embed/"};
            for (String marker : markers) {
                int start = text.indexOf(marker);
                if (start >= 0) {
                    start += marker.length();
                    int end = start;
                    while (end < text.length() && "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".indexOf(text.charAt(end)) >= 0) end++;
                    id = text.substring(start, end);
                    break;
                }
            }
        }
        return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
    }

    private static String hash(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + pin).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static final class Video {
        final String id;
        final String title;
        Video(String id, String title) { this.id = id; this.title = title; }
    }

    static final class Channel {
        final String id;
        final String title;
        Channel(String id, String title) { this.id = id; this.title = title; }
        String uploadsPlaylist() { return "UU" + id.substring(2); }
    }
}
