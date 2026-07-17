package com.concurrencyguard.report;

import com.concurrencyguard.invariant.MaxSuccessesInvariant;
import com.concurrencyguard.invariant.Violation;
import com.concurrencyguard.model.FireMode;
import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.ProbeState;
import com.concurrencyguard.model.RequestPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Full audit result: request plan summary, probe snapshots, outcomes, violations.
 *
 * <p>Keeps {@link ViolationReport} as a focused oversell view via {@link #toViolationReport()}.
 */
public final class AuditReport {

    public enum Verdict {
        OK, VIOLATION
    }

    private final String method;
    private final String target;
    private final FireMode fireMode;
    private final int concurrency;
    private final long initialBalance;
    private final long withdrawAmount;
    private final String stateField;
    private final Long finalBalance;
    private final int expectedSuccesses;
    private final int actualSuccesses;
    private final List<Violation> violations;
    private final List<Outcome> outcomes;

    public AuditReport(
            String method,
            String target,
            FireMode fireMode,
            int concurrency,
            long initialBalance,
            long withdrawAmount,
            String stateField,
            Long finalBalance,
            int expectedSuccesses,
            int actualSuccesses,
            List<Violation> violations,
            List<Outcome> outcomes) {
        this.method = method;
        this.target = target;
        this.fireMode = fireMode;
        this.concurrency = concurrency;
        this.initialBalance = initialBalance;
        this.withdrawAmount = withdrawAmount;
        this.stateField = stateField;
        this.finalBalance = finalBalance;
        this.expectedSuccesses = expectedSuccesses;
        this.actualSuccesses = actualSuccesses;
        this.violations = List.copyOf(violations == null ? List.of() : violations);
        this.outcomes = List.copyOf(outcomes == null ? List.of() : outcomes);
    }

    public static AuditReport from(
            RequestPlan plan,
            long initialBalance,
            long amount,
            String stateField,
            ProbeState finalState,
            List<Violation> violations,
            List<Outcome> outcomes) {
        Objects.requireNonNull(plan, "plan");
        int actual = MaxSuccessesInvariant.countSuccesses(outcomes);
        int expected = amount > 0
                ? (int) Math.min(Integer.MAX_VALUE, Math.max(0, initialBalance) / amount)
                : 0;
        Long fin = null;
        if (finalState != null && stateField != null && finalState.get(stateField).isPresent()) {
            fin = finalState.require(stateField);
        }
        return new AuditReport(
                plan.method(),
                plan.target().toString(),
                plan.mode(),
                plan.concurrency(),
                initialBalance,
                amount,
                stateField,
                fin,
                expected,
                actual,
                violations,
                outcomes);
    }

    public boolean isViolation() {
        return !violations.isEmpty();
    }

    public Verdict verdict() {
        return isViolation() ? Verdict.VIOLATION : Verdict.OK;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public List<Outcome> getOutcomes() {
        return outcomes;
    }

    public int getActualSuccesses() {
        return actualSuccesses;
    }

    public int getExpectedSuccesses() {
        return expectedSuccesses;
    }

    public Long getFinalBalance() {
        return finalBalance;
    }

    /** Compatibility view for M1-style oversell assertions. */
    public ViolationReport toViolationReport() {
        long fin = finalBalance == null ? 0L : finalBalance;
        return new ViolationReport(
                initialBalance,
                withdrawAmount,
                concurrency,
                expectedSuccesses,
                actualSuccesses,
                fin);
    }

    public String formatText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ConcurrencyGuard Violation Report ===").append('\n');
        sb.append("Target          : ").append(method).append(' ').append(target).append('\n');
        sb.append("Initial balance : ").append(initialBalance).append('\n');
        sb.append("Withdraw amount : ").append(withdrawAmount).append('\n');
        sb.append("Concurrent reqs : ").append(concurrency).append('\n');
        sb.append("Fire mode       : ").append(fireMode.name().toLowerCase().replace('_', '-'))
                .append('\n');
        sb.append("----------------------------------------").append('\n');
        sb.append("Expected successes : ").append(expectedSuccesses).append('\n');
        sb.append("Actual   successes : ").append(actualSuccesses);
        if (actualSuccesses > expectedSuccesses) {
            sb.append("  << OVERSELL (+")
                    .append(actualSuccesses - expectedSuccesses)
                    .append(')');
        }
        sb.append('\n');
        sb.append("Final balance      : ")
                .append(finalBalance == null ? "n/a" : finalBalance);
        if (finalBalance != null && finalBalance < 0) {
            sb.append("  << NEGATIVE");
        }
        sb.append('\n');
        sb.append("----------------------------------------").append('\n');
        sb.append("Invariants:").append('\n');
        if (violations.isEmpty()) {
            sb.append("  (all passed)").append('\n');
        } else {
            for (Violation v : violations) {
                sb.append("  [FAIL] ").append(pad(v.invariant(), 16))
                        .append(": ").append(v.detail()).append('\n');
            }
        }
        sb.append("----------------------------------------").append('\n');
        sb.append("Verdict : ").append(verdict()).append('\n');
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        field(sb, "target", target, true);
        field(sb, "method", method, true);
        field(sb, "fireMode", fireMode.name().toLowerCase().replace('_', '-'), true);
        num(sb, "initialBalance", initialBalance);
        num(sb, "withdrawAmount", withdrawAmount);
        num(sb, "concurrency", concurrency);
        num(sb, "expectedSuccesses", expectedSuccesses);
        num(sb, "actualSuccesses", actualSuccesses);
        if (finalBalance != null) {
            num(sb, "finalBalance", finalBalance);
        } else {
            sb.append("\"finalBalance\":null,");
        }
        if (stateField != null) {
            field(sb, "stateField", stateField, true);
        }
        field(sb, "verdict", verdict().name(), true);

        sb.append("\"violations\":[");
        for (int i = 0; i < violations.size(); i++) {
            Violation v = violations.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            field(sb, "invariant", v.invariant(), true);
            field(sb, "detail", v.detail(), false);
            sb.append('}');
        }
        sb.append("],");

        sb.append("\"outcomes\":[");
        for (int i = 0; i < outcomes.size(); i++) {
            Outcome o = outcomes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            num(sb, "index", o.index());
            num(sb, "status", o.statusCode());
            num(sb, "latencyMs", o.latencyMs());
            sb.append("\"success\":").append(o.isSuccess());
            if (o.error() != null) {
                sb.append(',');
                field(sb, "error", o.error(), false);
            }
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }

    private static void field(StringBuilder sb, String name, String value, boolean commaAfter) {
        sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
        if (commaAfter) {
            sb.append(',');
        }
    }

    private static void num(StringBuilder sb, String name, long value) {
        sb.append('"').append(name).append("\":").append(value).append(',');
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Collect invariant names that were requested (for tests / debugging). */
    public List<String> violationNames() {
        List<String> names = new ArrayList<>(violations.size());
        for (Violation v : violations) {
            names.add(v.invariant());
        }
        return names;
    }
}
