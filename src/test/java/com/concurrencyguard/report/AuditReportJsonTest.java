package com.concurrencyguard.report;

import com.concurrencyguard.invariant.Violation;
import com.concurrencyguard.model.FireMode;
import com.concurrencyguard.model.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReportJsonTest {

    @Test
    void toJsonEscapesAndIncludesOutcomes() {
        AuditReport report = new AuditReport(
                "POST",
                "http://localhost/withdraw",
                FireMode.BARRIER,
                2,
                100,
                30,
                "balance",
                -10L,
                3,
                4,
                List.of(new Violation("max-successes", "4 > 3")),
                List.of(
                        Outcome.ofHttp(0, 200, "ok", 0, 1_000_000),
                        Outcome.ofHttp(1, 200, "ok", 0, 2_000_000)));

        String json = report.toJson();
        assertTrue(json.contains("\"verdict\":\"VIOLATION\""));
        assertTrue(json.contains("\"invariant\":\"max-successes\""));
        assertTrue(json.contains("\"index\":0"));
        assertTrue(report.isViolation());
        assertTrue(report.formatText().contains("[FAIL]"));
        assertFalse(json.contains("\n"));
    }
}
