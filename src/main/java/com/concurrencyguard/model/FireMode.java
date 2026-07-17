package com.concurrencyguard.model;

/**
 * How concurrent requests are synchronized before leaving the client.
 *
 * <p>{@link #BARRIER} is M2 (virtual threads + barrier). {@link #SINGLE_PACKET}
 * is reserved for M3 HTTP/2 single-packet attack.
 */
public enum FireMode {
    BARRIER,
    SINGLE_PACKET;

    public static FireMode fromCli(String raw) {
        if (raw == null || raw.isBlank()) {
            return BARRIER;
        }
        return switch (raw.trim().toLowerCase().replace('_', '-')) {
            case "barrier" -> BARRIER;
            case "single-packet", "singlepacket" -> SINGLE_PACKET;
            default -> throw new IllegalArgumentException(
                    "Unknown fire mode: " + raw + " (use barrier|single-packet)");
        };
    }
}
