package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;

import java.util.List;
import java.util.Optional;

/**
 * A correctness rule evaluated after a concurrent fire.
 */
public interface Invariant {

    String name();

    /**
     * @return violation description, or empty if the invariant holds
     */
    Optional<Violation> check(ProbeState baseline,
                              ProbeState finalState,
                              List<Outcome> outcomes);
}
