package cz.petr.bezpecnevideo;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Public uploads only; callers supply a parent-approved channel ID. */
final class ChannelCatalog {
    private final Map<String, String> playlists = new ConcurrentHashMap<>();
    private final Map<String, Page> firstPages = new ConcurrentHashMap<>();

    void clear() { playlists.clear(); firstPages.clear(); }

    Page load(String key, String channel, String title, String token) throws Exception {
        String cacheKey = key + ":" + channel;
        Page cached = firstPages.get(cacheKey);
        if (token.isEmpty() && cached != null && System.currentTimeMillis() - cached.loaded < 300000) return cached;
        String playlist = playlists.get(cacheKey);
        if (playlist == null) {
            JSONObject response = get("channels?part=contentDetails&id=" + Uri.encode(channel), key);
            JSONArray items = response.getJSONArray("items");
            if (items.length() == 0) throw new Exception("Kanál nebyl nalezen.");
            playlist = items.getJSONObject(0).getJSONObject("contentDetails")
                    .getJSONObject("relatedPlaylists").getString("uploads");
            playlists.put(cacheKey, playlist);
        }
        JSONObject response = get("playlistItems?part=snippet,status&maxResults=50&playlistId="
                + Uri.encode(playlist) + "&pageToken=" + Uri.encode(token)
                + "&fields=" + Uri.encode("nextPageToken,items(snippet(title,resourceId/videoId,videoOwnerChannelId),status/privacyStatus)"), key);
        List<VideoStore.Video> videos = new ArrayList<>();
        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject snippet = item.getJSONObject("snippet");
            JSONObject status = item.optJSONObject("status");
            if (status == null || !"public".equals(status.optString("privacyStatus"))) continue;
            // Do not accept unrelated items if a response contains a different owner.
            if (!channel.equals(snippet.optString("videoOwnerChannelId"))) continue;
            String id = snippet.getJSONObject("resourceId").optString("videoId");
            if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
            videos.add(new VideoStore.Video(id, snippet.getString("title") + "  •  " + title));
        }
        Page page = new Page(videos, response.optString("nextPageToken"));
        if (token.isEmpty()) firstPages.put(cacheKey, page);
        return page;
    }

    private JSONObject get(String path, String key) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://www.googleapis.com/youtube/v3/" + path + "&key=" + Uri.encode(key)).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        try {
            int status = connection.getResponseCode();
            if (status == 400 || status == 401 || status == 403)
                throw new Exception("Zkontrolujte API klíč, zapnutí YouTube Data API a denní limit v Google Cloud.");
            if (status != 200) throw new Exception("YouTube je dočasně nedostupný (" + status + "). Zkuste to znovu.");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally { connection.disconnect(); }
    }

    static final class Page {
        final List<VideoStore.Video> videos;
        final String next;
        final long loaded = System.currentTimeMillis();
        Page(List<VideoStore.Video> videos, String next) { this.videos = videos; this.next = next; }
    }
}
