package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The breath: its 4 Hz tick on step boundaries, and a level that never goes dark. */
public class WindowActivityRingBreathTest {

    @Test
    public void theBreathTicksAtFourHertzOnStepBoundaries() {
        long step = WindowActivityRing.BREATHE_STEP_MS;
        assertEquals(250L, step);
        assertEquals(step, WindowActivityRing.nextBreatheTickDelayMs(0L));
        assertEquals(1L, WindowActivityRing.nextBreatheTickDelayMs(step - 1));
        for (long ms = 0; ms < 5000; ms += 7) {
            long delay = WindowActivityRing.nextBreatheTickDelayMs(ms);
            assertTrue(delay > 0 && delay <= step);
            assertEquals(0, (ms + delay) % step);
        }
    }

    @Test
    public void theBreathStaysBetweenItsFloorAndFull() {
        for (long ms = 0; ms < 7000; ms += 7) {
            float level = WindowActivityRing.breatheLevel(ms);
            assertTrue(level >= WindowActivityRing.BREATHE_FLOOR - 1e-6f && level <= 1f + 1e-6f);
        }
        assertEquals(WindowActivityRing.BREATHE_FLOOR, WindowActivityRing.breatheLevel(0), 1e-6f);
        assertEquals(1f, WindowActivityRing.breatheLevel(WindowActivityRing.BREATHE_PERIOD_MS / 2),
            1e-6f);
    }
}
