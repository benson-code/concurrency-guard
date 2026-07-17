package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;

import java.util.List;
import java.util.Optional;

/**
 * Final numeric state field must not go below zero.
 */
public final class NonNegativeStateInvariant implements Invariant {

    private final String stateField;

    public NonNegativeStateInvariant(String stateField) {
        if (stateField == null || stateField.isBlank()) {
            throw new IllegalArgumentException("stateField required");
        }
        this.stateField = stateField;
    }

    @Override
    public String name() {
        return "non-negative";
    }

    @Override
    public Optional<Violation> check(ProbeState baseline,
                                     ProbeState finalState,
                                     List<Outcome> outcomes) {
        if (finalState == null || finalState.get(stateField).isEmpty()) {
            return Optional.of(new Violation(name(),
                    "final state missing field '" + stateField + "'"));
        }
        long value = finalState.require(stateField);
        if (value < 0) {
            return Optional.of(new Violation(name(),
                    "final " + stateField + " " + value + " < 0"));
        }
        return Optional.empty();
    }
}
