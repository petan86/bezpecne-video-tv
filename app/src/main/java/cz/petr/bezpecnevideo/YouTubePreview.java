package cz.petr.bezpecnevideo;

import org.json.JSONObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

final class YouTubePreview {
    static JSONObject lookup(String id) throws Exception {
        if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Video ID");
        // Always contact YouTube; never fetch a URL supplied by the phone.
        URL url = new URL("https://www.youtube.com/oembed?format=json&url="
                + URLEncoder.encode("https://www.youtube.com/watch?v=" + id, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Metadata unavailable");
            StringBuilder body = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8")) {
                char[] buffer = new char[2048]; int length;
                while ((length = reader.read(buffer)) >= 0) {
                    body.append(buffer, 0, length);
                    if (body.length() > 65536) throw new java.io.IOException("Metadata too large");
                }
            }
            JSONObject data = new JSONObject(body.toString());
            String title = data.getString("title"), author = data.optString("author_name");
            return new JSONObject().put("title", title.substring(0, Math.min(180, title.length())))
                    .put("author", author.substring(0, Math.min(180, author.length())));
        } finally { connection.disconnect(); }
    }
}
