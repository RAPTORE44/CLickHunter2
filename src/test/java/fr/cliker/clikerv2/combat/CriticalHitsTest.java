package fr.cliker.clikerv2.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CriticalHitsTest {
    @Test
    void criticalHitsAlwaysExceedNormalHitsWithoutOverflow() {
        assertEquals(8, CriticalHits.multiply(5));
        assertEquals(15, CriticalHits.multiply(10));
        assertEquals(2, CriticalHits.multiply(1));
        assertEquals(Long.MAX_VALUE, CriticalHits.multiply(Long.MAX_VALUE));
        assertEquals(0, CriticalHits.multiply(0));
    }
}
