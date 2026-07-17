package com.concurrencyguard.report;

/**
 * Formats concurrency-invariant findings for human-readable output.
 *
 * <p>Holds expected vs actual success counts and the final balance so callers
 * (tests or a future auditor) can print a clear violation summary without
 * embedding formatting logic themselves.
 */
public final class ViolationReport {

    private final long initialBalance;
    private final long withdrawAmount;
    private final int requestCount;
    private final int expectedSuccesses;
    private final int actualSuccesses;
    private final long finalBalance;

    public ViolationReport(
            long initialBalance,
            long withdrawAmount,
            int requestCount,
            int expectedSuccesses,
            int actualSuccesses,
            long finalBalance) {
        this.initialBalance = initialBalance;
        this.withdrawAmount = withdrawAmount;
        this.requestCount = requestCount;
        this.expectedSuccesses = expectedSuccesses;
        this.actualSuccesses = actualSuccesses;
        this.finalBalance = finalBalance;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public long getWithdrawAmount() {
        return withdrawAmount;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getExpectedSuccesses() {
        return expectedSuccesses;
    }

    public int getActualSuccesses() {
        return actualSuccesses;
    }

    public long getFinalBalance() {
        return finalBalance;
    }

    /** True when more withdrawals succeeded than the balance could allow. */
    public boolean isOversell() {
        return actualSuccesses > expectedSuccesses;
    }

    /** True when final balance went negative (common oversell symptom). */
    public boolean isNegativeBalance() {
        return finalBalance < 0;
    }

    /** True when either oversell or negative balance is present. */
    public boolean isViolation() {
        return isOversell() || isNegativeBalance();
    }

    /**
     * Multi-line human-readable summary: setup, expected vs actual successes,
     * final balance, and a short verdict.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ConcurrencyGuard Violation Report ===").append('\n');
        sb.append("Initial balance : ").append(initialBalance).append('\n');
        sb.append("Withdraw amount : ").append(withdrawAmount).append('\n');
        sb.append("Concurrent reqs : ").append(requestCount).append('\n');
        sb.append("----------------------------------------").append('\n');
        sb.append("Expected successes : ").append(expectedSuccesses).append('\n');
        sb.append("Actual   successes : ").append(actualSuccesses);
        if (isOversell()) {
            sb.append("  << OVERSELL (+")
                    .append(actualSuccesses - expectedSuccesses)
                    .append(')');
        }
        sb.append('\n');
        sb.append("Final balance      : ").append(finalBalance);
        if (isNegativeBalance()) {
            sb.append("  << NEGATIVE");
        }
        sb.append('\n');
        sb.append("----------------------------------------").append('\n');
        sb.append("Verdict : ").append(isViolation() ? "VIOLATION" : "OK").append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
