package com.concurrencyguard.audit;

import com.concurrencyguard.client.ConcurrentClient;
import com.concurrencyguard.invariant.ConservationInvariant;
import com.concurrencyguard.invariant.InvariantEngine;
import com.concurrencyguard.invariant.MaxSuccessesInvariant;
import com.concurrencyguard.invariant.NonNegativeStateInvariant;
import com.concurrencyguard.invariant.Violation;
import com.concurrencyguard.mock.BuggyMockWithdrawServer;
import com.concurrencyguard.model.FireMode;
import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;
import com.concurrencyguard.model.RequestPlan;
import com.concurrencyguard.probe.StateProbe;
import com.concurrencyguard.report.AuditReport;
import com.concurrencyguard.report.ViolationReport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end: concurrent fire against the intentional TOCTOU mock must detect oversell.
 *
 * <p>The mock sleeps 5ms inside the race window, so barrier-aligned requests
 * should reproduce oversell reliably. We retry a few times to absorb rare scheduling misses.
 */
class OversellE2ETest {

    private static final long INITIAL = 100;
    private static final long AMOUNT = 30;
    private static final int CONCURRENCY = 10;
    private static final int MAX_ATTEMPTS = 5;

    @Test
    void concurrentWithdrawsTriggerViolationOnBuggyServer() throws Exception {
        boolean detected = false;
        AuditReport last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (BuggyMockWithdrawServer server = new BuggyMockWithdrawServer(0, INITIAL);
                 ConcurrentClient client = new ConcurrentClient()) {
                server.start();
                int port = server.getPort();
                String base = "http://127.0.0.1:" + port;

                RequestPlan plan = RequestPlan.builder()
                        .method("POST")
                        .target(base + "/withdraw")
                        .bodyTemplate("{\"amount\":" + AMOUNT + "}")
                        .concurrency(CONCURRENCY)
                        .mode(FireMode.BARRIER)
                        .timeout(Duration.ofSeconds(10))
                        .build();

                StateProbe probe = new StateProbe();
                ProbeState baseline = probe.read(URI.create(base + "/balance"), "balance");
                List<Outcome> outcomes = client.fire(plan);
                ProbeState finalState = probe.read(URI.create(base + "/balance"), "balance");

                InvariantEngine engine = new InvariantEngine(List.of(
                        new MaxSuccessesInvariant(INITIAL, AMOUNT),
                        new NonNegativeStateInvariant("balance"),
                        new ConservationInvariant("balance", AMOUNT)));

                List<Violation> violations = engine.evaluate(baseline, finalState, outcomes);
                last = AuditReport.from(plan, INITIAL, AMOUNT, "balance",
                        finalState, violations, outcomes);

                ViolationReport legacy = last.toViolationReport();
                if (last.isViolation() && legacy.isViolation()) {
                    detected = true;
                    assertTrue(legacy.isOversell() || legacy.isNegativeBalance(),
                            "expected oversell or negative balance; report=\n" + last.formatText());
                    break;
                }
            }
        }

        if (!detected) {
            fail("Failed to reproduce oversell in " + MAX_ATTEMPTS + " attempts. Last report:\n"
                    + (last == null ? "<none>" : last.formatText()));
        }
    }
}
