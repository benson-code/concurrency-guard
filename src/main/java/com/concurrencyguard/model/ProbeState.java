package com.concurrencyguard.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Snapshot of numeric state fields read from a probe endpoint (e.g. balance).
 */
public record ProbeState(Map<String, Long> fields) {

    public ProbeState {
        Objects.requireNonNull(fields, "fields");
        fields = Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static ProbeState empty() {
        return new ProbeState(Map.of());
    }

    public static ProbeState of(String field, long value) {
        return new ProbeState(Map.of(field, value));
    }

    public OptionalLong get(String field) {
        Long v = fields.get(field);
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }

    public long require(String field) {
        return get(field).orElseThrow(() ->
                new IllegalStateException("Probe field missing: " + field));
    }
}
