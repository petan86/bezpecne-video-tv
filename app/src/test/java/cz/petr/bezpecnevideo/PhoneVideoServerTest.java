package cz.petr.bezpecnevideo;

import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class PhoneVideoServerTest {
    private PhoneVideoServer server(List<String> saved) throws Exception {
        return new PhoneVideoServer(InetAddress.getByName("127.0.0.1"), "<html>phone</html>", "// phone UI",
                id -> new JSONObject().put("title", "Test video"),
                (profile, id, title) -> { saved.add(profile + ":" + id + ":" + title); return true; });
    }
    private String request(PhoneVideoServer server, String method, String path, String body,
                           String authorization, String origin, String host) throws Exception {
        URI uri = URI.create(server.origin());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nContent-Type: application/json\r\n"
                + "Authorization: " + authorization + "\r\nOrigin: " + origin + "\r\nContent-Length: " + bytes.length + "\r\n\r\n";
        try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int length;
            while ((length = socket.getInputStream().read(buffer)) >= 0) result.write(buffer, 0, length);
            return result.toString("UTF-8");
        }
    }
    private String post(PhoneVideoServer server, String path, String body) throws Exception {
        return request(server, "POST", path, body, "Bearer " + URI.create(server.link()).getFragment(),
                server.origin(), URI.create(server.origin()).getAuthority());
    }
    @Test public void htmlDoesNotDiscloseTokenAndWrongHostIsRejected() throws Exception {
        try (PhoneVideoServer server = server(new ArrayList<>())) {
            String page = request(server, "GET", "/", "", "", "", URI.create(server.origin()).getAuthority());
            assertTrue(page.startsWith("HTTP/1.1 200"));
            assertFalse(page.contains(URI.create(server.link()).getFragment()));
            assertTrue(page.contains("Cache-Control: no-store"));
            assertTrue(request(server, "GET", "/", "", "", "", "attacker.invalid").startsWith("HTTP/1.1 403"));
        }
    }
    @Test public void previewDoesNotSaveAndSelectedProfileDoes() throws Exception {
        List<String> saved = Collections.synchronizedList(new ArrayList<>());
        try (PhoneVideoServer server = server(saved)) {
            assertTrue(post(server, "/api/preview", "{\"url\":\"https://youtu.be/16LuvR2CARA?si=test\"}").contains("Test video"));
            assertTrue(saved.isEmpty());
            assertTrue(post(server, "/api/add", "{\"id\":\"16LuvR2CARA\",\"profile\":\"filipek\",\"title\":\"Český název\"}").startsWith("HTTP/1.1 200"));
            assertEquals(Collections.singletonList("filipek:16LuvR2CARA:Český název"), saved);
        }
    }
    @Test public void bothProfilesRequireExplicitSelection() throws Exception {
        List<String> saved = Collections.synchronizedList(new ArrayList<>());
        try (PhoneVideoServer server = server(saved)) {
            assertTrue(post(server, "/api/add", "{\"id\":\"16LuvR2CARA\",\"profile\":\"both\",\"title\":\"Test\"}").startsWith("HTTP/1.1 200"));
            assertEquals(2, saved.size());
            assertTrue(saved.get(0).startsWith("matysek:")); assertTrue(saved.get(1).startsWith("filipek:"));
        }
    }
    @Test public void badTokenAndCrossOriginCannotWrite() throws Exception {
        List<String> saved = Collections.synchronizedList(new ArrayList<>());
        try (PhoneVideoServer server = server(saved)) {
            String body = "{\"id\":\"16LuvR2CARA\",\"profile\":\"matysek\"}";
            String host = URI.create(server.origin()).getAuthority();
            assertTrue(request(server, "POST", "/api/add", body, "Bearer invalid", server.origin(), host).startsWith("HTTP/1.1 403"));
            assertTrue(request(server, "POST", "/api/add", body, "Bearer " + URI.create(server.link()).getFragment(),
                    "https://attacker.invalid", host).startsWith("HTTP/1.1 403"));
            assertTrue(saved.isEmpty());
        }
    }
    @Test public void malformedVideoAndProfileCannotWrite() throws Exception {
        List<String> saved = Collections.synchronizedList(new ArrayList<>());
        try (PhoneVideoServer server = server(saved)) {
            assertTrue(post(server, "/api/add", "{\"id\":\"16LuvR2CARA\",\"profile\":\"unknown\"}").startsWith("HTTP/1.1 400"));
            assertTrue(post(server, "/api/add", "{\"id\":\"<script>\",\"profile\":\"matysek\"}").startsWith("HTTP/1.1 400"));
            assertTrue(post(server, "/api/preview", "{\"url\":\"http://127.0.0.1/secret\"}").startsWith("HTTP/1.1 400"));
            assertTrue(saved.isEmpty());
        }
    }
    @Test public void closingSessionRevokesAccess() throws Exception {
        PhoneVideoServer server = server(new ArrayList<>());
        server.close();
        assertFalse(server.isOpen());
        assertThrows(IOException.class, () -> post(server, "/api/add", "{}"));
    }
    @Test public void expiredSessionCannotSave() throws Exception {
        List<String> saved = Collections.synchronizedList(new ArrayList<>());
        try (PhoneVideoServer server = new PhoneVideoServer(InetAddress.getByName("127.0.0.1"), "", "",
                id -> new JSONObject(), (profile, id, title) -> { saved.add(id); return true; }, 0)) {
            assertFalse(server.isOpen());
            try { assertFalse(post(server, "/api/add", "{}").startsWith("HTTP/1.1 200")); }
            catch (IOException expected) { }
            assertTrue(saved.isEmpty());
        }
    }
    @Test public void metadataFailureOffersManualTitle() throws Exception {
        try (PhoneVideoServer server = new PhoneVideoServer(InetAddress.getByName("127.0.0.1"), "", "",
                id -> { throw new IOException(); }, (profile, id, title) -> true)) {
            String response = post(server, "/api/preview", "{\"url\":\"16LuvR2CARA\"}");
            assertTrue(response.startsWith("HTTP/1.1 200"));
            assertTrue(response.contains("warning")); assertTrue(response.contains("16LuvR2CARA"));
        }
    }
    @Test public void parserRejectsOversizedTruncatedAndAmbiguousRequests() {
        for (String headers : new String[]{"Content-Length: 9000\r\n", "Content-Length: 4\r\n",
                "Content-Length: 0\r\nContent-Length: 1\r\n", "Transfer-Encoding: chunked\r\n"}) {
            byte[] bytes = ("POST / HTTP/1.1\r\n" + headers + "\r\n").getBytes(StandardCharsets.US_ASCII);
            assertThrows(IOException.class, () -> PhoneVideoServer.Request.read(new ByteArrayInputStream(bytes)));
        }
    }
}
