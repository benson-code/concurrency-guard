package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;

import java.util.List;
import java.util.Optional;

/**
 * {@code initial - final == actualSuccesses * amount} (balance conservation).
 *
 * <p>On a racy check-then-act withdraw, conservation often fails when more
 * successes drain past zero unevenly, or when remaining is corrupted. With the
 * mock server both oversell and negative balance typically appear together.
 */
public final class ConservationInvariant implements Invariant {

    private final String stateField;
    private final long amount;

    public ConservationInvariant(String stateField, long amount) {
        if (stateField == null || stateField.isBlank()) {
            throw new IllegalArgumentException("stateField required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        this.stateField = stateField;
        this.amount = amount;
    }

    @Override
    public String name() {
        return "conservation";
    }

    @Override
    public Optional<Violation> check(ProbeState baseline,
                                     ProbeState finalState,
                                     List<Outcome> outcomes) {
        if (baseline == null || baseline.get(stateField).isEmpty()) {
            return Optional.of(new Violation(name(),
                    "baseline missing field '" + stateField + "'"));
        }
        if (finalState == null || finalState.get(stateField).isEmpty()) {
            return Optional.of(new Violation(name(),
                    "final state missing field '" + stateField + "'"));
        }
        long initial = baseline.require(stateField);
        long fin = finalState.require(stateField);
        int successes = MaxSuccessesInvariant.countSuccesses(outcomes);
        long delta = initial - fin;
        long expectedDelta = (long) successes * amount;
        if (delta != expectedDelta) {
            return Optional.of(new Violation(name(),
                    initial + " - " + fin + " = " + delta
                            + " != " + successes + "*" + amount + " = " + expectedDelta));
        }
        return Optional.empty();
    }
}
