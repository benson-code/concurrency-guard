package com.concurrencyguard.invariant;

/**
 * One failed invariant check.
 */
public record Violation(String invariant, String detail) {

    public Violation {
        if (invariant == null || invariant.isBlank()) {
            throw new IllegalArgumentException("invariant name required");
        }
        if (detail == null) {
            detail = "";
        }
    }
}
