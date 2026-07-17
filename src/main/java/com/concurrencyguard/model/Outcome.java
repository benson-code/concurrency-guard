package com.concurrencyguard.model;

/**
 * Result of a single concurrent request attempt.
 *
 * @param index       zero-based request index
 * @param statusCode  HTTP status, or -1 on connection failure
 * @param body        response body (may be truncated by the client)
 * @param sentAtNanos {@link System#nanoTime()} when the request was sent
 * @param recvAtNanos {@link System#nanoTime()} when the response completed
 * @param error       exception message if the call failed; null on HTTP response
 */
public record Outcome(
        int index,
        int statusCode,
        String body,
        long sentAtNanos,
        long recvAtNanos,
        String error) {

    public static final int BODY_TRUNCATE = 2048;

    public static Outcome ofHttp(int index, int statusCode, String body,
                                 long sentAtNanos, long recvAtNanos) {
        return new Outcome(index, statusCode, truncate(body), sentAtNanos, recvAtNanos, null);
    }

    public static Outcome ofError(int index, String error, long sentAtNanos, long recvAtNanos) {
        return new Outcome(index, -1, null, sentAtNanos, recvAtNanos, error);
    }

    public boolean isTransportOk() {
        return error == null && statusCode > 0;
    }

    /** Default success: 2xx with a completed response. */
    public boolean isSuccess() {
        return isTransportOk() && statusCode >= 200 && statusCode < 300;
    }

    public long latencyMs() {
        if (recvAtNanos <= sentAtNanos) {
            return 0L;
        }
        return (recvAtNanos - sentAtNanos) / 1_000_000L;
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= BODY_TRUNCATE) {
            return body;
        }
        return body.substring(0, BODY_TRUNCATE) + "...(truncated)";
    }
}
