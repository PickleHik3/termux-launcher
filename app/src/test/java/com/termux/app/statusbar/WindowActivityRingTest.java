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

    /**
     * The smooth arc used to be redrawn once per vsync, which on a 120 Hz panel is 154 redraws of
     * every working pill per turn. It now moves on a clock of its own, and the angle it draws at a
     * given moment is unchanged: the tick decides how many positions the arc visits, never how fast
     * it goes round.
     */
    @Test
    public void theSmoothArcTicksAboutThirtyTimesASecondAtUnchangedSpeed() {
        assertTrue("a tick of " + WindowActivityRing.SMOOTH_TICK_MS + "ms would read as stepping",
            WindowActivityRing.SMOOTH_TICK_MS <= 40L);
        assertTrue("a tick of " + WindowActivityRing.SMOOTH_TICK_MS + "ms is back on the vsync clock",
            WindowActivityRing.SMOOTH_TICK_MS >= 25L);
        // Enough stops that a ring a few pixels wide reads as gliding, and far fewer than a turn's
        // worth of vsyncs.
        assertTrue("steps " + WindowActivityRing.SMOOTH_STEPS, WindowActivityRing.SMOOTH_STEPS >= 32);
        assertTrue("steps " + WindowActivityRing.SMOOTH_STEPS, WindowActivityRing.SMOOTH_STEPS < 120);

        // The angle at a given elapsed time is what it always was: one full clockwise turn per
        // SPIN_MS, whoever asks for the redraw.
        assertEquals(WindowActivityRing.START_DEG,
            WindowActivityRing.indeterminateStartDeg(WindowActivityRing.phase(0L)), .001f);
        assertEquals(WindowActivityRing.START_DEG + 180f,
            WindowActivityRing.indeterminateStartDeg(WindowActivityRing.phase(SPIN / 2)), .001f);
        for (long ms = 0; ms < SPIN * 3; ms += 11) {
            assertEquals("a turn later is the same angle", WindowActivityRing.phase(ms),
                WindowActivityRing.phase(ms + SPIN), .0005f);
        }
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
