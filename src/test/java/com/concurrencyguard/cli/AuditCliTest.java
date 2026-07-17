package com.concurrencyguard.cli;

import com.concurrencyguard.mock.BuggyMockWithdrawServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditCliTest {

    @TempDir
    Path temp;

    @Test
    void helpExitsZero() {
        assertEquals(AuditCli.EXIT_OK, AuditCli.run(new String[]{"help"}));
    }

    @Test
    void missingTargetIsUsageError() {
        assertEquals(AuditCli.EXIT_USAGE, AuditCli.run(new String[]{"audit", "--concurrency", "2"}));
    }

    @Test
    void nonLocalhostRequiresAuthorizationFlag() {
        int code = AuditCli.run(new String[]{
                "audit",
                "--target", "http://example.com/withdraw",
                "--concurrency", "2",
                "--initial", "100",
                "--amount", "30"
        });
        assertEquals(AuditCli.EXIT_USAGE, code);
    }

    @Test
    void isLocalhostRecognizesLoopback() {
        assertTrue(AuditCli.isLocalhost(URI.create("http://localhost:18080/x")));
        assertTrue(AuditCli.isLocalhost(URI.create("http://127.0.0.1:9/x")));
        assertFalse(AuditCli.isLocalhost(URI.create("http://example.com/x")));
    }

    @Test
    void auditAgainstMockProducesViolationExitCode() throws Exception {
        Integer lastCode = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try (BuggyMockWithdrawServer server = new BuggyMockWithdrawServer(0, 100)) {
                server.start();
                int port = server.getPort();
                Path report = temp.resolve("report-" + attempt + ".txt");
                lastCode = AuditCli.run(new String[]{
                        "audit",
                        "--target", "http://127.0.0.1:" + port + "/withdraw",
                        "--method", "POST",
                        "--body", "{\"amount\":30}",
                        "--concurrency", "10",
                        "--baseline", "http://127.0.0.1:" + port + "/balance",
                        "--state-field", "balance",
                        "--amount", "30",
                        "--invariant", "max-successes,non-negative,conservation",
                        "--report", "text",
                        "--out", report.toString()
                });
                if (lastCode == AuditCli.EXIT_VIOLATION) {
                    String text = Files.readString(report);
                    assertTrue(text.contains("VIOLATION") || text.contains("OVERSELL")
                                    || text.contains("NEGATIVE"),
                            text);
                    return;
                }
            }
        }
        assertEquals(AuditCli.EXIT_VIOLATION, lastCode,
                "expected CLI to report violation against buggy mock");
    }

    @Test
    void jsonReportIsValidShape() throws Exception {
        try (BuggyMockWithdrawServer server = new BuggyMockWithdrawServer(0, 100)) {
            server.start();
            int port = server.getPort();
            Path report = temp.resolve("out.json");
            int code = AuditCli.run(new String[]{
                    "audit",
                    "--target", "http://127.0.0.1:" + port + "/withdraw",
                    "--body", "{\"amount\":30}",
                    "--concurrency", "8",
                    "--baseline", "http://127.0.0.1:" + port + "/balance",
                    "--amount", "30",
                    "--report", "json",
                    "--out", report.toString()
            });
            assertTrue(code == AuditCli.EXIT_VIOLATION || code == AuditCli.EXIT_OK);
            String json = Files.readString(report);
            assertTrue(json.contains("\"verdict\""));
            assertTrue(json.contains("\"outcomes\""));
            assertTrue(json.contains("\"violations\""));
        }
    }
}
