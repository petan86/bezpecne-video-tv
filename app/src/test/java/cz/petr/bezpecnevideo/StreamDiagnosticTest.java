package cz.petr.bezpecnevideo;

import org.junit.Test;
import org.junit.Assume;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.StreamInfo;

/** Opt-in real network diagnostic; excluded from ordinary offline verification. */
public class StreamDiagnosticTest {
    @Test public void extractReportedVideo() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("VIDEO_NETWORK_DIAGNOSTIC")));
        NewPipe.init(new org.schabi.newpipe.extractor.downloader.Downloader() {
            @Override public org.schabi.newpipe.extractor.downloader.Response execute(
                    org.schabi.newpipe.extractor.downloader.Request request) throws java.io.IOException {
                var response = new ExtractorDownloader().execute(request);
                var uri = java.net.URI.create(request.url());
                String body = response.responseBody();
                var title = java.util.regex.Pattern.compile("<title>(.*?)</title>").matcher(body);
                System.out.println(uri.getHost() + uri.getPath() + " status=" + response.responseCode()
                        + " length=" + body.length() + " title=" + (title.find() ? title.group(1) : "none")
                        + " consent=" + body.contains("consent.youtube.com")
                        + " initialData=" + body.contains("ytInitialData"));
                return response;
            }
        }, new Localization("cs", "CZ"));
        StreamInfo info = StreamInfo.getInfo("https://www.youtube.com/watch?v=2DGmX8F3cPo");
        System.out.println("Video streams: " + info.getVideoStreams().size()
                + ", video-only: " + info.getVideoOnlyStreams().size()
                + ", audio: " + info.getAudioStreams().size());
        for (Throwable error : info.getErrors()) error.printStackTrace();
        org.junit.Assert.assertFalse(info.getVideoOnlyStreams().isEmpty());
        org.junit.Assert.assertFalse(info.getAudioStreams().isEmpty());
        var video = info.getVideoOnlyStreams().stream()
                .filter(s -> s.isUrl() && s.getHeight() <= 1080)
                .max(java.util.Comparator.comparingInt(org.schabi.newpipe.extractor.stream.VideoStream::getHeight))
                .orElseThrow();
        var audio = info.getAudioStreams().stream()
                .max(java.util.Comparator.comparingInt(org.schabi.newpipe.extractor.stream.AudioStream::getAverageBitrate))
                .orElseThrow();
        probe("video " + video.getResolution() + " " + video.getCodec(), video.getContent());
        probe("audio " + audio.getCodec(), audio.getContent());
    }

    private static void probe(String label, String url) throws Exception {
        var connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", ExtractorDownloader.USER_AGENT);
        connection.setRequestProperty("Referer", "https://www.youtube.com/");
        connection.setRequestProperty("Origin", "https://www.youtube.com");
        connection.setRequestProperty("Range", "bytes=0-1023");
        try {
            int status = connection.getResponseCode();
            System.out.println(label + " HTTP " + status);
            org.junit.Assert.assertTrue(label, status == 200 || status == 206);
            try (var input = connection.getInputStream()) {
                org.junit.Assert.assertTrue(label + " data", input.readNBytes(1024).length > 0);
            }
        } finally {
            connection.disconnect();
        }
    }
}
