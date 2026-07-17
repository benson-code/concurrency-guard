package com.concurrencyguard.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateProbeTest {

    @Test
    void extractsBalance() {
        assertEquals(100L, StateProbe.extractLongField("{\"balance\":100}", "balance"));
        assertEquals(-50L, StateProbe.extractLongField("{\"balance\": -50 }", "balance"));
    }

    @Test
    void missingFieldThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> StateProbe.extractLongField("{\"x\":1}", "balance"));
    }
}
