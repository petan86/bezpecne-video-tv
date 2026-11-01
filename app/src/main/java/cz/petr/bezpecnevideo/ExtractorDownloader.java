package cz.petr.bezpecnevideo;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Small dependency-free HTTP bridge used by NewPipe Extractor. */
final class ExtractorDownloader extends Downloader {
    @Override public Response execute(Request request) throws java.io.IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod(request.httpMethod());
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/131 Safari/537.36");
        for (Map.Entry<String, List<String>> header : request.headers().entrySet())
            for (String value : header.getValue()) connection.addRequestProperty(header.getKey(), value);
        byte[] requestBody = request.dataToSend();
        if (requestBody != null && requestBody.length > 0) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(requestBody); }
        }
        int code = connection.getResponseCode();
        String body = "";
        if (!"HEAD".equals(request.httpMethod())) {
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input != null) try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = source.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                body = bytes.toString(StandardCharsets.UTF_8.name());
            }
        }
        return new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), body,
                connection.getURL().toString());
    }
}
