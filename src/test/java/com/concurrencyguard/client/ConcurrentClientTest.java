package com.concurrencyguard.client;

import com.concurrencyguard.mock.BuggyMockWithdrawServer;
import com.concurrencyguard.model.FireMode;
import com.concurrencyguard.model.Outcome;
import com.concurrencyguard.model.RequestPlan;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentClientTest {

    @Test
    void barrierFireReturnsOneOutcomePerRequest() throws Exception {
        try (BuggyMockWithdrawServer server = new BuggyMockWithdrawServer(0, 1000);
             ConcurrentClient client = new ConcurrentClient()) {
            server.start();
            int n = 5;
            RequestPlan plan = RequestPlan.builder()
                    .method("POST")
                    .target("http://127.0.0.1:" + server.getPort() + "/withdraw")
                    .bodyTemplate("{\"amount\":10}")
                    .concurrency(n)
                    .mode(FireMode.BARRIER)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            List<Outcome> outcomes = client.fire(plan);
            assertEquals(n, outcomes.size());
            for (int i = 0; i < n; i++) {
                assertEquals(i, outcomes.get(i).index());
                assertTrue(outcomes.get(i).isTransportOk(), "outcome " + i + " should complete");
            }
        }
    }

    @Test
    void singlePacketModeRejectedInM2() {
        try (ConcurrentClient client = new ConcurrentClient()) {
            RequestPlan plan = RequestPlan.builder()
                    .target("http://127.0.0.1:9/x")
                    .concurrency(2)
                    .mode(FireMode.SINGLE_PACKET)
                    .build();
            assertThrows(UnsupportedOperationException.class, () -> client.fire(plan));
        }
    }

    @Test
    void bodyTemplateSubstitutesIndex() {
        assertEquals("{\"id\":3}", ConcurrentClient.renderBody("{\"id\":{{index}}}", 3));
        assertEquals("n=0", ConcurrentClient.renderBody("n={{n}}", 0));
    }
}
