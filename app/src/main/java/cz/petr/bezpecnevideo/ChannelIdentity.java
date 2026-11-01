package cz.petr.bezpecnevideo;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelIdentity {
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
}
