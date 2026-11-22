package cz.petr.bezpecnevideo;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelIdentity {
    private static final Pattern DIRECT_ID = Pattern.compile("^/channel/(UC[A-Za-z0-9_-]{22})(?:/|$)");
    private static final String DESKTOP_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern NAMED = Pattern.compile("^/(@[^/?#]{1,100}|c/[^/?#]{1,100}|user/[^/?#]{1,100})(?:/|$)");

    /** Recommendations can contain channelId before the page owner's actual metadata. */
    static VideoStore.Channel fromPage(String html) throws Exception {
        Matcher marker = Pattern.compile("\"channelMetadataRenderer\"\\s*:\\s*").matcher(html);
        if (!marker.find()) throw new IllegalArgumentException("Channel metadata missing");
        Object parsed = new JSONTokener(html.substring(marker.end())).nextValue();
        if (!(parsed instanceof JSONObject)) throw new IllegalArgumentException("Channel metadata invalid");
        JSONObject metadata = (JSONObject) parsed;
        String id = metadata.getString("externalId");
        if (!id.matches("UC[A-Za-z0-9_-]{22}")) throw new IllegalArgumentException("Channel ID invalid");
        return new VideoStore.Channel(id, metadata.getString("title"));
    }

    /**
     * Normalises a YouTube channel link (/channel/UC…, /@handle, /c/…, /user/…, also with /videos etc.)
     * to its https://www.youtube.com page, or returns null when the text is not a channel link.
     */
    static String channelUrl(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.isEmpty()) return null;
        if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("youtube.com".equals(host) || "www.youtube.com".equals(host) || "m.youtube.com".equals(host))) return null;
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            Matcher direct = DIRECT_ID.matcher(path);
            if (direct.find()) return "https://www.youtube.com/channel/" + direct.group(1);
            Matcher named = NAMED.matcher(path);
            if (named.find()) return "https://www.youtube.com/" + named.group(1);
        } catch (Exception ignored) { }
        return null;
    }

    /** The UC… id written directly in a channel link, or null. */
    static String directId(String channelUrl) {
        if (channelUrl == null) return null;
        Matcher matcher = Pattern.compile("/channel/(UC[A-Za-z0-9_-]{22})").matcher(channelUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Finds the channel id and name for a channel link. A link with the id in it still works when the
     * page cannot be loaded; the name is then empty.
     */
    static VideoStore.Channel resolve(String link) throws Exception {
        String url = channelUrl(link);
        if (url == null) throw new IllegalArgumentException("Not a channel link");
        String direct = directId(url);
        try {
            return fromPage(download(url));
        } catch (Exception pageFailed) {
            if (direct != null) return new VideoStore.Channel(direct, "");
            throw pageFailed;
        }
    }

    private static String download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        // In the EU YouTube redirects to a cookie-consent page; SOCS=CAI declines the consent so the
        // channel page itself is served. A desktop agent gets the full page with channel metadata.
        connection.setRequestProperty("User-Agent", DESKTOP_AGENT);
        connection.setRequestProperty("Accept-Language", "cs-CZ,cs;q=0.9");
        connection.setRequestProperty("Cookie", "SOCS=CAI");
        StringBuilder html = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) html.append(line);
        } finally {
            connection.disconnect();
        }
        return html.toString();
    }
}
