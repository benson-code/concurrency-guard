package com.concurrencyguard.probe;

import com.concurrencyguard.model.ProbeState;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a JSON-ish numeric field from a GET endpoint (e.g. {@code GET /balance}).
 *
 * <p>Minimal parser — no JSON library. Looks for {@code "field": number}.
 */
public final class StateProbe {

    private final HttpClient http;
    private final Duration timeout;

    public StateProbe() {
        this(HttpClient.newHttpClient(), Duration.ofSeconds(10));
    }

    public StateProbe(HttpClient http, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public ProbeState read(URI endpoint, String field) throws IOException, InterruptedException {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(field, "field");

        HttpRequest req = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Probe HTTP " + resp.statusCode() + " from " + endpoint
                    + ": " + resp.body());
        }
        long value = extractLongField(resp.body(), field);
        Map<String, Long> fields = new LinkedHashMap<>();
        fields.put(field, value);
        return new ProbeState(fields);
    }

    /**
     * Extract {@code "field": number} from a minimal JSON body.
     */
    public static long extractLongField(String body, String field) {
        if (body == null) {
            throw new IllegalArgumentException("empty probe body");
        }
        String key = "\"" + field + "\"";
        int i = body.indexOf(key);
        if (i < 0) {
            throw new IllegalArgumentException("field not found in probe body: " + field);
        }
        int colon = body.indexOf(':', i + key.length());
        if (colon < 0) {
            throw new IllegalArgumentException("malformed probe body for field: " + field);
        }
        int p = colon + 1;
        while (p < body.length() && Character.isWhitespace(body.charAt(p))) {
            p++;
        }
        int start = p;
        if (p < body.length() && body.charAt(p) == '-') {
            p++;
        }
        while (p < body.length() && Character.isDigit(body.charAt(p))) {
            p++;
        }
        if (p == start || (p == start + 1 && body.charAt(start) == '-')) {
            throw new IllegalArgumentException("no number for field: " + field);
        }
        String n = body.substring(start, p);
        try {
            return Long.parseLong(n);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid number for field: " + field + " -> " + n, e);
        }
    }
}
