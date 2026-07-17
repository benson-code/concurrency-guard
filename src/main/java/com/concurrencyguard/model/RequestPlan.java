package com.concurrencyguard.model;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a concurrent HTTP fire plan against one endpoint.
 */
public record RequestPlan(
        String method,
        URI target,
        Map<String, String> headers,
        String bodyTemplate,
        int concurrency,
        FireMode mode,
        Duration timeout) {

    public RequestPlan {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(timeout, "timeout");
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        method = method.trim().toUpperCase();
        headers = headers == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(headers));
        if (bodyTemplate == null) {
            bodyTemplate = "";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String method = "POST";
        private URI target;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String bodyTemplate = "";
        private int concurrency = 1;
        private FireMode mode = FireMode.BARRIER;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder target(URI target) {
            this.target = target;
            return this;
        }

        public Builder target(String target) {
            this.target = URI.create(target);
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder bodyTemplate(String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder mode(FireMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public RequestPlan build() {
            return new RequestPlan(method, target, headers, bodyTemplate, concurrency, mode, timeout);
        }
    }
}
