package com.concurrencyguard.cli;

import com.concurrencyguard.client.ConcurrentClient;
import com.concurrencyguard.invariant.ConservationInvariant;
import com.concurrencyguard.invariant.Invariant;
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CLI entry: {@code audit} against any HTTP endpoint, or {@code serve-target} mock.
 *
 * <p>Exit codes (SPEC §7.3): 0 OK, 1 violation, 2 usage, 3 runtime error.
 */
public final class AuditCli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_VIOLATION = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_RUNTIME = 3;

    public static final int DEFAULT_MAX_CONCURRENCY = 100;

    private AuditCli() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Testable entry that returns the exit code instead of exiting. */
    public static int run(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            return EXIT_USAGE;
        }
        String cmd = args[0].toLowerCase(Locale.ROOT);
        return switch (cmd) {
            case "audit" -> runAudit(slice(args, 1));
            case "serve-target", "serve" -> runServe(slice(args, 1));
            case "help", "-h", "--help" -> {
                printUsage();
                yield EXIT_OK;
            }
            default -> {
                // allow bare flags as audit for convenience
                if (args[0].startsWith("--")) {
                    yield runAudit(args);
                }
                System.err.println("Unknown command: " + args[0]);
                printUsage();
                yield EXIT_USAGE;
            }
        };
    }

    private static int runServe(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 18080;
            long initial = args.length > 1 ? Long.parseLong(args[1]) : 100L;
            BuggyMockWithdrawServer s = new BuggyMockWithdrawServer(port, initial);
            s.start();
            System.out.println("BuggyMockWithdrawServer http://localhost:" + s.getPort()
                    + " balance=" + initial + " (race-prone)");
            Thread.currentThread().join();
            return EXIT_OK;
        } catch (NumberFormatException e) {
            System.err.println("Usage: serve-target [port] [initialBalance]");
            return EXIT_USAGE;
        } catch (Exception e) {
            System.err.println("Failed to start mock server: " + e.getMessage());
            return EXIT_RUNTIME;
        }
    }

    private static int runAudit(String[] args) {
        AuditArgs parsed;
        try {
            parsed = AuditArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage error: " + e.getMessage());
            printUsage();
            return EXIT_USAGE;
        }

        if (!parsed.authorized && !isLocalhost(parsed.target)) {
            System.err.println("""
                    Refusing to attack a non-localhost target without --i-am-authorized.
                    Only test systems you own or have written permission to test.""");
            return EXIT_USAGE;
        }
        if (parsed.concurrency > DEFAULT_MAX_CONCURRENCY && !parsed.allowHighConcurrency) {
            System.err.println("Concurrency " + parsed.concurrency
                    + " exceeds default max " + DEFAULT_MAX_CONCURRENCY
                    + ". Pass --allow-high-concurrency to override.");
            return EXIT_USAGE;
        }

        try {
            AuditReport report = executeAudit(parsed);
            String text = report.formatText();
            String json = report.toJson();

            switch (parsed.reportMode) {
                case TEXT -> writeOut(parsed.out, text, false);
                case JSON -> writeOut(parsed.out, json + System.lineSeparator(), false);
                case BOTH -> {
                    writeOut(parsed.out, text, false);
                    if (parsed.out == null) {
                        System.out.println(json);
                    } else {
                        Path jsonPath = Path.of(parsed.out + ".json");
                        Files.writeString(jsonPath, json + System.lineSeparator(), StandardCharsets.UTF_8);
                        System.err.println("JSON written to " + jsonPath);
                    }
                }
            }

            return report.isViolation() ? EXIT_VIOLATION : EXIT_OK;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted: " + e.getMessage());
            return EXIT_RUNTIME;
        } catch (Exception e) {
            System.err.println("Runtime error: " + e.getMessage());
            return EXIT_RUNTIME;
        }
    }

    static AuditReport executeAudit(AuditArgs parsed) throws Exception {
        RequestPlan plan = RequestPlan.builder()
                .method(parsed.method)
                .target(parsed.target)
                .headers(parsed.headers)
                .bodyTemplate(parsed.body)
                .concurrency(parsed.concurrency)
                .mode(parsed.fireMode)
                .timeout(Duration.ofSeconds(parsed.timeoutSec))
                .build();

        StateProbe probe = new StateProbe();
        ProbeState baseline;
        long initialBalance;

        if (parsed.baseline != null) {
            baseline = probe.read(parsed.baseline, parsed.stateField);
            initialBalance = baseline.require(parsed.stateField);
        } else if (parsed.initial != null) {
            initialBalance = parsed.initial;
            baseline = ProbeState.of(parsed.stateField, initialBalance);
        } else {
            throw new IllegalArgumentException(
                    "Provide --baseline URL or --initial <balance> for invariant math");
        }

        long amount = parsed.amount != null ? parsed.amount : 0L;
        if (amount <= 0 && needsAmount(parsed.invariants)) {
            throw new IllegalArgumentException("--amount is required for the selected invariants");
        }

        List<Invariant> rules = buildInvariants(parsed, initialBalance, amount);
        InvariantEngine engine = new InvariantEngine(rules);

        List<Outcome> outcomes;
        try (ConcurrentClient client = new ConcurrentClient(Duration.ofSeconds(parsed.timeoutSec))) {
            outcomes = client.fire(plan);
        }

        ProbeState finalState;
        if (parsed.baseline != null) {
            finalState = probe.read(parsed.baseline, parsed.stateField);
        } else {
            // Without a probe URL we cannot read final state; leave empty so
            // non-negative / conservation may fail if selected.
            finalState = ProbeState.empty();
        }

        List<Violation> violations = engine.evaluate(baseline, finalState, outcomes);
        return AuditReport.from(plan, initialBalance, amount, parsed.stateField,
                finalState, violations, outcomes);
    }

    private static boolean needsAmount(List<String> names) {
        for (String n : names) {
            if ("max-successes".equals(n) || "conservation".equals(n)) {
                return true;
            }
        }
        return false;
    }

    private static List<Invariant> buildInvariants(AuditArgs parsed, long initial, long amount) {
        List<Invariant> list = new ArrayList<>();
        for (String name : parsed.invariants) {
            switch (name) {
                case "max-successes" -> list.add(new MaxSuccessesInvariant(initial, amount));
                case "non-negative" -> list.add(new NonNegativeStateInvariant(parsed.stateField));
                case "conservation" -> list.add(new ConservationInvariant(parsed.stateField, amount));
                default -> throw new IllegalArgumentException("Unknown invariant: " + name
                        + " (M2 supports: max-successes,non-negative,conservation)");
            }
        }
        return list;
    }

    private static void writeOut(String outPath, String content, boolean ignored) throws IOException {
        if (outPath == null || outPath.isBlank() || "-".equals(outPath)) {
            System.out.print(content);
            if (!content.endsWith("\n")) {
                System.out.println();
            }
        } else {
            Files.writeString(Path.of(outPath), content, StandardCharsets.UTF_8);
            System.err.println("Report written to " + outPath);
        }
    }

    static boolean isLocalhost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String h = uri.getHost().toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h);
    }

    private static String[] slice(String[] args, int from) {
        if (from >= args.length) {
            return new String[0];
        }
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    private static void printUsage() {
        System.err.println("""
                ConcurrencyGuard — HTTP API race-condition audit CLI (M2)

                Usage:
                  java -jar concurrency-guard.jar audit [options]
                  java -jar concurrency-guard.jar serve-target [port] [initialBalance]

                Required (audit):
                  --target <url>            Endpoint to fire concurrently
                  --concurrency <n>         Number of aligned requests

                Common options:
                  --method <POST|GET|...>   Default POST
                  --body <json>             Request body ({{index}} / {{n}} substituted)
                  --header <K: V>           Repeatable custom header (Cookie, Authorization, ...)
                  --baseline <url>          GET probe for state (e.g. /balance)
                  --state-field <name>      Numeric JSON field (default: balance)
                  --initial <n>             Known initial value (if no --baseline)
                  --amount <n>              Per-request debit amount
                  --invariant <list>        Comma list (default: max-successes,non-negative,conservation)
                  --report text|json|both   Default text
                  --out <file>              Write report (default stdout)
                  --fire-mode barrier       (single-packet reserved for M3)
                  --timeout <sec>           Per-request timeout (default 30)
                  --i-am-authorized         Required for non-localhost targets
                  --allow-high-concurrency  Allow N > 100

                Exit codes: 0=OK  1=violation  2=usage  3=runtime
                """);
    }

    /** Parsed CLI arguments for audit. */
    static final class AuditArgs {
        URI target;
        String method = "POST";
        String body = "";
        Map<String, String> headers = new LinkedHashMap<>();
        int concurrency = -1;
        URI baseline;
        String stateField = "balance";
        Long initial;
        Long amount;
        List<String> invariants = List.of("max-successes", "non-negative", "conservation");
        ReportMode reportMode = ReportMode.TEXT;
        String out;
        FireMode fireMode = FireMode.BARRIER;
        int timeoutSec = 30;
        boolean authorized;
        boolean allowHighConcurrency;

        enum ReportMode { TEXT, JSON, BOTH }

        static AuditArgs parse(String[] args) {
            AuditArgs a = new AuditArgs();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--target" -> a.target = URI.create(need(args, ++i, "--target"));
                    case "--method" -> a.method = need(args, ++i, "--method");
                    case "--body" -> a.body = need(args, ++i, "--body");
                    case "--header" -> {
                        String hv = need(args, ++i, "--header");
                        int colon = hv.indexOf(':');
                        if (colon <= 0) {
                            throw new IllegalArgumentException(
                                    "--header must be 'Name: value', got: " + hv);
                        }
                        String name = hv.substring(0, colon).trim();
                        String value = hv.substring(colon + 1).trim();
                        a.headers.put(name, value);
                    }
                    case "--concurrency" -> a.concurrency = Integer.parseInt(need(args, ++i, "--concurrency"));
                    case "--baseline" -> a.baseline = URI.create(need(args, ++i, "--baseline"));
                    case "--state-field" -> a.stateField = need(args, ++i, "--state-field");
                    case "--initial" -> a.initial = Long.parseLong(need(args, ++i, "--initial"));
                    case "--amount" -> a.amount = Long.parseLong(need(args, ++i, "--amount"));
                    case "--invariant" -> a.invariants = splitList(need(args, ++i, "--invariant"));
                    case "--report" -> a.reportMode = parseReport(need(args, ++i, "--report"));
                    case "--out" -> a.out = need(args, ++i, "--out");
                    case "--fire-mode" -> a.fireMode = FireMode.fromCli(need(args, ++i, "--fire-mode"));
                    case "--timeout" -> a.timeoutSec = Integer.parseInt(need(args, ++i, "--timeout"));
                    case "--i-am-authorized" -> a.authorized = true;
                    case "--allow-high-concurrency" -> a.allowHighConcurrency = true;
                    default -> throw new IllegalArgumentException("Unknown flag: " + arg);
                }
            }
            if (a.target == null) {
                throw new IllegalArgumentException("--target is required");
            }
            if (a.concurrency < 1) {
                throw new IllegalArgumentException("--concurrency >= 1 is required");
            }
            return a;
        }

        private static String need(String[] args, int i, String flag) {
            if (i >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[i];
        }

        private static List<String> splitList(String raw) {
            String[] parts = raw.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
            if (list.isEmpty()) {
                throw new IllegalArgumentException("--invariant list is empty");
            }
            return List.copyOf(list);
        }

        private static ReportMode parseReport(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "text" -> ReportMode.TEXT;
                case "json" -> ReportMode.JSON;
                case "both" -> ReportMode.BOTH;
                default -> throw new IllegalArgumentException(
                        "--report must be text|json|both, got: " + raw);
            };
        }
    }
}
