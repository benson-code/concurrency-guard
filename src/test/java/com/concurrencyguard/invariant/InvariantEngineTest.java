package com.concurrencyguard.invariant;

import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantEngineTest {

    @Test
    void maxSuccessesDetectsOversell() {
        MaxSuccessesInvariant inv = new MaxSuccessesInvariant(100, 30);
        assertEquals(3, inv.expectedSuccesses());

        List<Outcome> outcomes = List.of(
                ok(0), ok(1), ok(2), ok(3), ok(4));
        var v = inv.check(ProbeState.of("balance", 100), ProbeState.of("balance", -50), outcomes);
        assertTrue(v.isPresent());
        assertEquals("max-successes", v.get().invariant());
    }

    @Test
    void maxSuccessesPassesWhenWithinBudget() {
        MaxSuccessesInvariant inv = new MaxSuccessesInvariant(100, 30);
        List<Outcome> outcomes = List.of(ok(0), ok(1), ok(2), fail(3), fail(4));
        assertTrue(inv.check(ProbeState.empty(), ProbeState.empty(), outcomes).isEmpty());
    }

    @Test
    void nonNegativeDetectsNegativeFinal() {
        NonNegativeStateInvariant inv = new NonNegativeStateInvariant("balance");
        var v = inv.check(
                ProbeState.of("balance", 100),
                ProbeState.of("balance", -10),
                List.of());
        assertTrue(v.isPresent());
    }

    @Test
    void conservationHoldsWhenDeltaMatches() {
        ConservationInvariant inv = new ConservationInvariant("balance", 30);
        List<Outcome> outcomes = List.of(ok(0), ok(1), ok(2), fail(3));
        // 100 - 10 = 90 == 3*30
        var v = inv.check(
                ProbeState.of("balance", 100),
                ProbeState.of("balance", 10),
                outcomes);
        assertTrue(v.isEmpty());
    }

    @Test
    void conservationFailsOnMismatch() {
        ConservationInvariant inv = new ConservationInvariant("balance", 30);
        List<Outcome> outcomes = List.of(ok(0), ok(1), ok(2), ok(3));
        // 100 - (-20) = 120 != 4*30=120 ... wait that's equal
        // use 100 - (-50) = 150 != 4*30 = 120
        var v = inv.check(
                ProbeState.of("balance", 100),
                ProbeState.of("balance", -50),
                outcomes);
        assertTrue(v.isPresent());
        assertTrue(v.get().detail().contains("!="));
    }

    @Test
    void engineCollectsMultipleViolations() {
        InvariantEngine engine = new InvariantEngine(List.of(
                new MaxSuccessesInvariant(100, 30),
                new NonNegativeStateInvariant("balance"),
                new ConservationInvariant("balance", 30)));

        List<Outcome> outcomes = List.of(ok(0), ok(1), ok(2), ok(3), ok(4), ok(5), ok(6));
        // 7 successes * 30 = 210; 100 - (-110) = 210 → conservation OK; oversell + negative fail
        List<Violation> vs = engine.evaluate(
                ProbeState.of("balance", 100),
                ProbeState.of("balance", -110),
                outcomes);
        assertEquals(2, vs.size());
        assertTrue(vs.stream().anyMatch(v -> v.invariant().equals("max-successes")));
        assertTrue(vs.stream().anyMatch(v -> v.invariant().equals("non-negative")));
        assertFalse(vs.stream().anyMatch(v -> v.invariant().equals("conservation")));
    }

    private static Outcome ok(int i) {
        return Outcome.ofHttp(i, 200, "{\"status\":\"ok\"}", 0L, 1_000_000L);
    }

    private static Outcome fail(int i) {
        return Outcome.ofHttp(i, 409, "{\"status\":\"insufficient\"}", 0L, 1_000_000L);
    }
}
