package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowActivityRingTest {

    private static final long SPIN = WindowActivityRing.SPIN_MS;

    @Test
    public void phaseWrapsWithinTheUnitInterval() {
        assertEquals(0f, WindowActivityRing.phase(0L), .0001f);
        assertEquals(0.5f, WindowActivityRing.phase(SPIN / 2), .0001f);
        assertEquals(0f, WindowActivityRing.phase(SPIN), .0001f);
        for (long ms = 0; ms < SPIN * 5; ms += 37) {
            float phase = WindowActivityRing.phase(ms);
            assertTrue("phase " + phase, phase >= 0f && phase < 1f);
        }
    }

    @Test
    public void theIndeterminateArcTurnsOnceAClockwiseTurnPerCycle() {
        assertEquals(WindowActivityRing.START_DEG, WindowActivityRing.indeterminateStartDeg(0f), .001f);
        assertEquals(WindowActivityRing.START_DEG + 180f,
            WindowActivityRing.indeterminateStartDeg(0.5f), .001f);
        // Continuous across the wrap: the end of one turn is the start of the next, modulo 360.
        float endOfTurn = WindowActivityRing.indeterminateStartDeg(0.999f) % 360f;
        float startOfNext = (WindowActivityRing.indeterminateStartDeg(0f) + 360f) % 360f;
        assertEquals(startOfNext, endOfTurn, 0.5f);
    }

    @Test
    public void lazyModeStepsThePhaseInsteadOfHoldingIt() {
        assertEquals(0f, WindowActivityRing.steppedPhase(0.05f, 8), .0001f);
        assertEquals(0.125f, WindowActivityRing.steppedPhase(0.13f, 8), .0001f);
        assertEquals(0.875f, WindowActivityRing.steppedPhase(0.999f, 8), .0001f);
        // Never wraps to a full turn: a phase in [0,1) stays in [0,1).
        assertTrue(WindowActivityRing.steppedPhase(1f, 8) < 1f);
        // Eight ticks make one turn: the tick is what a lazy ring redraws on.
        assertEquals(WindowActivityRing.SPIN_MS,
            WindowActivityRing.LAZY_TICK_MS * WindowActivityRing.LAZY_STEPS);
    }

    @Test
    public void aReportedPercentageFillsTheRingAndIsClamped() {
        assertEquals(0f, WindowActivityRing.determinateSweepDeg(0), .001f);
        assertEquals(90f, WindowActivityRing.determinateSweepDeg(25), .001f);
        assertEquals(360f, WindowActivityRing.determinateSweepDeg(100), .001f);
        assertEquals(360f, WindowActivityRing.determinateSweepDeg(140), .001f);
        assertEquals(0f, WindowActivityRing.determinateSweepDeg(-5), .001f);
    }
}
