package com.concurrencyguard.client;

import com.concurrencyguard.model.FireMode;
import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.RequestPlan;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fires N concurrent HTTP requests aligned by a barrier (M2).
 *
 * <p>Uses JDK {@link HttpClient} and virtual threads. Does not implement
 * HTTP/2 single-packet attack (M3).
 */
public final class ConcurrentClient implements AutoCloseable {

    private final HttpClient http;

    public ConcurrentClient() {
        this(Duration.ofSeconds(30));
    }

    public ConcurrentClient(Duration connectTimeout) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Package-visible for tests that inject a custom client. */
    ConcurrentClient(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /**
     * Execute the plan and return one {@link Outcome} per index (0..N-1), ordered by index.
     */
    public List<Outcome> fire(RequestPlan plan) throws InterruptedException {
        Objects.requireNonNull(plan, "plan");
        if (plan.mode() == FireMode.SINGLE_PACKET) {
            throw new UnsupportedOperationException(
                    "single-packet fire mode is planned for M3; use barrier for now");
        }

        int n = plan.concurrency();
        Outcome[] slots = new Outcome[n];
        AtomicReference<Throwable> barrierFailure = new AtomicReference<>();
        CyclicBarrier barrier = new CyclicBarrier(n, () -> {
            // all threads released together after this barrier action
        });

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        barrierFailure.compareAndSet(null, e);
                        slots[index] = Outcome.ofError(index, "barrier: " + e.getMessage(),
                                System.nanoTime(), System.nanoTime());
                        return;
                    }
                    slots[index] = sendOne(plan, index);
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    // individual outcomes already recorded; keep going
                }
            }
        }

        if (barrierFailure.get() != null
                && barrierFailure.get() instanceof InterruptedException ie) {
            throw ie;
        }

        List<Outcome> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Outcome o = slots[i];
            out.add(o != null ? o : Outcome.ofError(i, "missing outcome", 0L, 0L));
        }
        return out;
    }

    private Outcome sendOne(RequestPlan plan, int index) {
        long sent = System.nanoTime();
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(plan.target())
                    .timeout(plan.timeout());

            String body = renderBody(plan.bodyTemplate(), index);
            String method = plan.method();
            if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.method(method, HttpRequest.BodyPublishers.ofString(
                        body, StandardCharsets.UTF_8));
            }

            boolean hasContentType = false;
            for (Map.Entry<String, String> h : plan.headers().entrySet()) {
                b.header(h.getKey(), h.getValue());
                if ("content-type".equalsIgnoreCase(h.getKey())) {
                    hasContentType = true;
                }
            }
            if (!hasContentType && body != null && !body.isEmpty()
                    && !("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method))) {
                b.header("Content-Type", "application/json");
            }

            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            long recv = System.nanoTime();
            return Outcome.ofHttp(index, resp.statusCode(), resp.body(), sent, recv);
        } catch (IOException e) {
            return Outcome.ofError(index, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    sent, System.nanoTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.ofError(index, "interrupted", sent, System.nanoTime());
        }
    }

    /**
     * Body template substitutions for M2:
     * <ul>
     *   <li>{@code {{index}}} — zero-based request index</li>
     *   <li>{@code {{n}}} — same as index</li>
     * </ul>
     */
    static String renderBody(String template, int index) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String s = template.replace("{{index}}", Integer.toString(index));
        s = s.replace("{{n}}", Integer.toString(index));
        return s;
    }

    @Override
    public void close() {
        // HttpClient has no close in older APIs; rely on GC. JDK 21 HttpClient is fine.
    }

    /** Await pool termination helper for tests (no-op with try-with-resources design). */
    public void shutdownQuietly(long timeout, TimeUnit unit) {
        // reserved
    }
}
