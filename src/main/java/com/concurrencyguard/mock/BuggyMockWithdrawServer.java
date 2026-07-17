package com.concurrencyguard.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Intentionally racy mock withdraw server (ConcurrencyGuard M1).
 *
 * <p>TOCTOU check-then-act on {@code balance}: concurrent threads can all pass
 * {@code balance >= amount} before any subtracts → oversell / negative balance.
 *
 * <pre>
 * POST /withdraw  {"amount":30}  → 200 ok | 409 insufficient
 * GET  /balance                  → 200 {"balance":...}
 * </pre>
 */
public final class BuggyMockWithdrawServer implements AutoCloseable {

    private final HttpServer server;
    /** Unsynchronized on purpose — the bug under test. */
    private long balance;

    public BuggyMockWithdrawServer(int port, long initialBalance) throws IOException {
        this.balance = initialBalance;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/withdraw", this::handleWithdraw);
        this.server.createContext("/balance", this::handleBalance);
        // Multi-thread executor is required; single-thread would hide the race.
        this.server.setExecutor(Executors.newFixedThreadPool(32));
    }

    public void start() { server.start(); }

    public int getPort() { return server.getAddress().getPort(); }

    public long getBalance() { return balance; }

    @Override
    public void close() { server.stop(0); }

    private void handleWithdraw(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        long amount = parseAmount(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (amount <= 0) {
            respond(ex, 400, "{\"error\":\"invalid amount\"}");
            return;
        }

        // ===== RACE WINDOW (check-then-act, no lock) =====
        if (balance >= amount) {
            // Sleep widens the window so oversell reproduces reliably under load.
            try { Thread.sleep(5); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            balance -= amount;
            respond(ex, 200, "{\"status\":\"ok\",\"remaining\":" + balance + "}");
        } else {
            respond(ex, 409, "{\"status\":\"insufficient\",\"remaining\":" + balance + "}");
        }
    }

    private void handleBalance(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        respond(ex, 200, "{\"balance\":" + balance + "}");
    }

    /** Minimal "amount":N extractor — no external JSON lib. */
    static long parseAmount(String body) {
        int i = body.indexOf("\"amount\"");
        if (i < 0 || (i = body.indexOf(':', i)) < 0) return -1;
        String n = body.substring(i + 1).replaceAll("[^0-9\\-].*", "").trim();
        try { return n.isEmpty() ? -1 : Long.parseLong(n); }
        catch (NumberFormatException e) { return -1; }
    }

    private static void respond(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18080;
        long initial = args.length > 1 ? Long.parseLong(args[1]) : 100;
        BuggyMockWithdrawServer s = new BuggyMockWithdrawServer(port, initial);
        s.start();
        System.out.println("BuggyMockWithdrawServer http://localhost:" + s.getPort()
                + " balance=" + initial + " (race-prone)");
        Thread.currentThread().join();
    }
}
