package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One hold time for the whole terminal place, riding on the system long press but staying under it. */
public class HoldTimingTest {

    @Test
    public void aDefaultPhoneHoldsForThreeHundredMilliseconds() {
        assertEquals(300L, HoldTiming.holdTimeoutMs(400L));
    }

    @Test
    public void theAccessibilityDelaysScaleWithTheSetting() {
        assertEquals(750L, HoldTiming.holdTimeoutMs(1000L));
        assertEquals(1125L, HoldTiming.holdTimeoutMs(1500L));
    }

    @Test
    public void theHoldNeverDropsBelowTheFloor() {
        assertEquals(HoldTiming.MIN_HOLD_MS, HoldTiming.holdTimeoutMs(200L));
        assertEquals(HoldTiming.MIN_HOLD_MS, HoldTiming.holdTimeoutMs(0L));
        assertEquals(HoldTiming.MIN_HOLD_MS, HoldTiming.holdTimeoutMs(-50L));
    }

    @Test
    public void theHoldAlwaysBeatsTheLongPressItRidesOn() {
        for (long system = 340L; system <= 2000L; system += 20L) {
            assertTrue("system " + system, HoldTiming.holdTimeoutMs(system) < system);
        }
    }

    @Test
    public void theSecondStageIsTwiceTheSystemLongPress() {
        assertEquals(800L, HoldTiming.selectTimeoutMs(400L));
        assertEquals(2000L, HoldTiming.selectTimeoutMs(1000L));
    }

    @Test
    public void theSecondStageAlwaysComesAfterTheFirst() {
        for (long system = -50L; system <= 2000L; system += 10L) {
            assertTrue("system " + system,
                HoldTiming.selectTimeoutMs(system) > HoldTiming.holdTimeoutMs(system));
        }
    }

    @Test
    public void theFloorStaysClearOfASlowTap() {
        assertTrue(HoldTiming.MIN_HOLD_MS >= 250L);
    }
}
