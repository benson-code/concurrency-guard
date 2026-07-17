package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;

import java.util.List;
import java.util.Optional;

/**
 * At most {@code floor(initial / amount)} requests may succeed (oversell detector).
 */
public final class MaxSuccessesInvariant implements Invariant {

    private final long initialBalance;
    private final long amount;

    public MaxSuccessesInvariant(long initialBalance, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        this.initialBalance = initialBalance;
        this.amount = amount;
    }

    @Override
    public String name() {
        return "max-successes";
    }

    public int expectedSuccesses() {
        if (initialBalance < 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, initialBalance / amount);
    }

    @Override
    public Optional<Violation> check(ProbeState baseline,
                                     ProbeState finalState,
                                     List<Outcome> outcomes) {
        int actual = countSuccesses(outcomes);
        int expected = expectedSuccesses();
        if (actual > expected) {
            return Optional.of(new Violation(name(),
                    actual + " > " + expected + " (oversell +" + (actual - expected) + ")"));
        }
        return Optional.empty();
    }

    public static int countSuccesses(List<Outcome> outcomes) {
        int n = 0;
        for (Outcome o : outcomes) {
            if (o.isSuccess()) {
                n++;
            }
        }
        return n;
    }
}
