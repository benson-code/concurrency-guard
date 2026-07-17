package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs a list of {@link Invariant} rules and collects violations.
 */
public final class InvariantEngine {

    private final List<Invariant> invariants;

    public InvariantEngine(List<Invariant> invariants) {
        Objects.requireNonNull(invariants, "invariants");
        this.invariants = List.copyOf(invariants);
    }

    public List<Violation> evaluate(ProbeState baseline,
                                    ProbeState finalState,
                                    List<Outcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        List<Violation> violations = new ArrayList<>();
        for (Invariant inv : invariants) {
            Optional<Violation> v = inv.check(
                    baseline == null ? ProbeState.empty() : baseline,
                    finalState == null ? ProbeState.empty() : finalState,
                    outcomes);
            v.ifPresent(violations::add);
        }
        return List.copyOf(violations);
    }

    public List<Invariant> invariants() {
        return invariants;
    }
}
