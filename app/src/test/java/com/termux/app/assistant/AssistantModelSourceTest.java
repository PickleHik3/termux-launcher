package com.termux.app.assistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AssistantModelSourceTest {

    @Test
    public void aCacheNeverReadIsStale() {
        // elapsedRealtime is small after boot; MIN_VALUE once overflowed to "fresh" for good.
        assertTrue(AssistantModelSource.isStale(1_000L, Long.MIN_VALUE, 5_000L));
        assertTrue(AssistantModelSource.isStale(Long.MAX_VALUE / 2, Long.MIN_VALUE, 5_000L));
    }

    @Test
    public void aCacheIsFreshWithinItsTimeAndStaleAfter() {
        assertFalse(AssistantModelSource.isStale(10_000L, 6_000L, 5_000L));
        assertTrue(AssistantModelSource.isStale(12_000L, 6_000L, 5_000L));
    }
}
