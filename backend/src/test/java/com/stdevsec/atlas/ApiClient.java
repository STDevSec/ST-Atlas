package com.stdevsec.atlas;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal browser-like client: keeps cookies and sends the CSRF header, like the SPA does. */
public final class ApiClient {

    public record Response(int status, JsonNode body, String raw, byte[] bytes, HttpResponse<byte[]> http) {}

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public ApiClient(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Response post(String path, Object body) {
        return send(json(path).POST(HttpRequest.BodyPublishers.ofString(write(body))));
    }

    public Response put(String path, Object body) {
        return send(json(path).PUT(HttpRequest.BodyPublishers.ofString(write(body))));
    }

    public Response delete(String path) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).DELETE()));
    }

    public Response upload(String path, String filename, String contentType, byte[] content) {
        String boundary = "----atlas" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(content, 0, all, head.length, content.length);
        System.arraycopy(tail, 0, all, head.length + content.length, tail.length);
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(all))));
    }

    /** Logs in through the real endpoints (CSRF bootstrap + login). */
    public Response login(String username, String password) {
        get("/api/v1/auth/csrf");
        return post("/api/v1/auth/login", java.util.Map.of("username", username, "password", password));
    }

    private HttpRequest.Builder json(String path) {
        return withCsrf(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json"));
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder b) {
        if (csrfToken() == null) {
            get("/api/v1/auth/csrf");
        }
        String token = csrfToken();
        return token == null ? b : b.header("X-XSRF-TOKEN", token);
    }

    private String csrfToken() {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue).findFirst().orElse(null);
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<byte[]> r = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            String raw = new String(r.body(), StandardCharsets.UTF_8);
            JsonNode node = null;
            if (r.headers().firstValue("Content-Type").orElse("").contains("json") && !raw.isBlank()) {
                node = JSON.readTree(raw);
            }
            return new Response(r.statusCode(), node, raw, r.body(), r);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String write(Object body) {
        return JSON.writeValueAsString(body);
    }
}
