package cz.petr.bezpecnevideo;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Ephemeral LAN-only parent session. No account, PIN or API key is exposed to the browser. */
final class PhoneVideoServer implements AutoCloseable {
    interface Lookup { JSONObject find(String id) throws Exception; }
    interface Save { boolean add(String profile, String id, String title); }
    private final ServerSocket listener;
    private final String html, script, token, host;
    private final Lookup lookup;
    private final Save save;
    private final long expiresAt;
    private final Object lifecycle = new Object();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> { Thread thread = new Thread(runnable, "phone-request"); thread.setDaemon(true); return thread; });

    PhoneVideoServer(InetAddress address, String html, String script, Lookup lookup, Save save) throws IOException {
        this(address, html, script, lookup, save, TimeUnit.MINUTES.toMillis(10));
    }

    PhoneVideoServer(InetAddress address, String html, String script, Lookup lookup, Save save, long durationMs) throws IOException {
        this.html = html; this.script = script; this.lookup = lookup; this.save = save;
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        listener = new ServerSocket(0, 8, address);
        listener.setSoTimeout(1000);
        host = address.getHostAddress() + ":" + listener.getLocalPort();
        expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        Thread thread = new Thread(this::accept, "phone-session");
        thread.setDaemon(true);
        thread.start();
    }

    String origin() { return "http://" + host; }
    String link() { return origin() + "/#" + token; }
    boolean isOpen() { return !closed && System.nanoTime() < expiresAt; }

    private void accept() {
        try {
            while (isOpen()) {
                try {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(2000);
                    clients.add(socket);
                    try { workers.execute(() -> handle(socket)); }
                    catch (java.util.concurrent.RejectedExecutionException ignored) { clients.remove(socket); socket.close(); }
                } catch (SocketTimeoutException ignored) { }
            }
        } catch (IOException ignored) { }
        finally { close(); }
    }

    private void handle(Socket socket) {
        try (Socket client = socket) {
            try {
                Request request = Request.read(client.getInputStream());
                if (!isOpen()) { respond(client, 410, "application/json", error("Přístup skončil. Otevřete nový QR kód na TV.")); return; }
                if (!host.equals(request.headers.get("host"))) { respond(client, 403, "text/plain", "Forbidden"); return; }
                if ("GET".equals(request.method) && "/".equals(request.path)) {
                    respond(client, 200, "text/html", html); return;
                }
                if ("GET".equals(request.method) && "/phone.js".equals(request.path)) {
                    respond(client, 200, "text/javascript", script); return;
                }
                if (!"POST".equals(request.method)) { respond(client, 405, "text/plain", "Method not allowed"); return; }
                String authorization = request.headers.getOrDefault("authorization", "");
                if (!origin().equals(request.headers.get("origin")) || !MessageDigest.isEqual(
                        ("Bearer " + token).getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
                    respond(client, 403, "application/json", error("Přístup není platný. Naskenujte aktuální QR kód na TV.")); return;
                }
                if (!"application/json".equals(request.headers.getOrDefault("content-type", "").split(";", 2)[0].trim())) {
                    respond(client, 415, "application/json", error("Neplatný formát požadavku.")); return;
                }
                JSONObject data = new JSONObject(request.body);
                if ("/api/preview".equals(request.path)) {
                    String id = VideoStore.extractVideoId(data.optString("url"));
                    if (id == null) { respond(client, 400, "application/json", error("Vložte odkaz na konkrétní YouTube video.")); return; }
                    JSONObject result;
                    try { result = lookup.find(id); }
                    catch (Exception ignored) { result = new JSONObject().put("title", "").put("warning", "Název se nepodařilo načíst. Můžete ho doplnit ručně."); }
                    result.put("id", id);
                    respond(client, 200, "application/json", result.toString());
                } else if ("/api/add".equals(request.path)) {
                    String id = data.optString("id"), title = data.optString("title").trim(), profile = data.optString("profile");
                    if (!id.matches("[A-Za-z0-9_-]{11}") || title.length() > 180
                            || !("matysek".equals(profile) || "filipek".equals(profile) || "both".equals(profile))) {
                        respond(client, 400, "application/json", error("Zkontrolujte video, název a vybraný profil.")); return;
                    }
                    if (title.isEmpty()) title = "YouTube video " + id;
                    boolean success;
                    synchronized (lifecycle) {
                        if (!isOpen()) { respond(client, 410, "application/json", error("Přístup skončil. Otevřete nový QR kód na TV.")); return; }
                        if ("both".equals(profile)) {
                            boolean first = save.add("matysek", id, title);
                            boolean second = save.add("filipek", id, title);
                            success = first && second;
                        } else success = save.add(profile, id, title);
                    }
                    respond(client, success ? 200 : 500, "application/json", success
                            ? new JSONObject().put("ok", true).toString() : error("Uložení se nepodařilo. Zkuste to znovu."));
                } else respond(client, 404, "application/json", error("Neznámá akce."));
            } catch (Exception ignored) {
                try { respond(client, 400, "application/json", error("Neplatný požadavek.")); } catch (IOException ignoredAgain) { }
            }
        } catch (IOException ignored) { }
        finally { clients.remove(socket); }
    }

    private static String error(String message) {
        try { return new JSONObject().put("error", message).toString(); }
        catch (Exception impossible) { return "{}"; }
    }

    private void respond(Socket socket, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " Response\r\nContent-Type: " + type + "; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; "
                + "img-src https://i.ytimg.com data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n\r\n";
        OutputStream output = socket.getOutputStream();
        output.write(headers.getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.flush();
    }

    @Override public void close() {
        synchronized (lifecycle) { if (closed) return; closed = true; }
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket client : clients) try { client.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }

    static final class Request {
        String method, path, body;
        final Map<String, String> headers = new HashMap<>();
        static Request read(InputStream input) throws IOException {
            Request request = new Request();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int[] remaining = {8192};
            String[] first = line(input, remaining, deadline).split(" ");
            if (first.length != 3 || !"HTTP/1.1".equals(first[2])) throw new IOException("Protocol");
            request.method = first[0]; request.path = first[1];
            String line;
            while (!(line = line(input, remaining, deadline)).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon <= 0) throw new IOException("Header");
                String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
                if (request.headers.put(name, line.substring(colon + 1).trim()) != null) throw new IOException("Duplicate header");
            }
            if (request.headers.containsKey("transfer-encoding")) throw new IOException("Unsupported encoding");
            int length;
            try { length = Integer.parseInt(request.headers.getOrDefault("content-length", "0")); }
            catch (NumberFormatException invalid) { throw new IOException("Length"); }
            if (length < 0 || length > 8192) throw new IOException("Size");
            byte[] bytes = new byte[length];
            for (int offset = 0; offset < length;) {
                if (System.nanoTime() > deadline) throw new IOException("Timeout");
                int count = input.read(bytes, offset, length - offset);
                if (count < 0) throw new IOException("Incomplete body");
                offset += count;
            }
            request.body = new String(bytes, StandardCharsets.UTF_8);
            return request;
        }
        private static String line(InputStream input, int[] remaining, long deadline) throws IOException {
            ByteArrayOutputStream value = new ByteArrayOutputStream();
            int previous = -1;
            while (--remaining[0] >= 0 && System.nanoTime() <= deadline) {
                int next = input.read();
                if (next < 0) throw new IOException("Incomplete headers");
                if (next == '\n') {
                    if (previous != '\r') throw new IOException("Line ending");
                    byte[] bytes = value.toByteArray();
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
                }
                value.write(next); previous = next;
            }
            throw new IOException("Headers too large or slow");
        }
    }
}
