package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShellActivityPulseTest {

    private static final long CYCLE = ShellActivityPulse.CYCLE_MS;

    @Test
    public void phaseWrapsWithinTheUnitInterval() {
        assertEquals(0f, ShellActivityPulse.phase(0L), .0001f);
        assertEquals(0.5f, ShellActivityPulse.phase(CYCLE / 2), .0001f);
        assertEquals(0f, ShellActivityPulse.phase(CYCLE), .0001f);
        assertEquals(0.25f, ShellActivityPulse.phase(CYCLE * 9 + CYCLE / 4), .0001f);
        for (long ms = 0; ms < CYCLE * 5; ms += 37) {
            float phase = ShellActivityPulse.phase(ms);
            assertTrue("phase " + phase, phase >= 0f && phase < 1f);
        }
    }

    @Test
    public void dotWeightsStayInRangeForEveryDotAndPhase() {
        for (int i = 0; i < ShellActivityPulse.DOT_COUNT; i++) {
            for (float phase = 0f; phase < 1f; phase += 0.01f) {
                float weight = ShellActivityPulse.dotWeight(i, phase);
                assertTrue("dot " + i + " at " + phase, weight >= 0f && weight <= 1f);
            }
        }
    }

    @Test
    public void eachDotPeaksAtItsOwnPhase() {
        // Otherwise the three dots blink together, which reads as flashing rather than as motion.
        float[] peaks = new float[ShellActivityPulse.DOT_COUNT];
        for (int i = 0; i < ShellActivityPulse.DOT_COUNT; i++) {
            float bestPhase = 0f;
            float best = -1f;
            for (float phase = 0f; phase < 1f; phase += 0.001f) {
                float weight = ShellActivityPulse.dotWeight(i, phase);
                if (weight > best) {
                    best = weight;
                    bestPhase = phase;
                }
            }
            peaks[i] = bestPhase;
            assertEquals(1f, best, .01f);
        }
        for (int i = 0; i < peaks.length; i++) {
            for (int j = i + 1; j < peaks.length; j++) {
                assertTrue("dots " + i + " and " + j + " peak together",
                    Math.abs(peaks[i] - peaks[j]) > 0.1f);
            }
        }
    }

    @Test
    public void dotWeightsAreContinuousAcrossTheWrap() {
        for (int i = 0; i < ShellActivityPulse.DOT_COUNT; i++) {
            assertEquals(ShellActivityPulse.dotWeight(i, 0f),
                ShellActivityPulse.dotWeight(i, 0.999f), .02f);
        }
    }

    @Test
    public void sweepIsContinuousAcrossTheWrap() {
        // A saw would snap from the far end back to the near end once a cycle, and that snap reads
        // as a dropped frame.
        assertEquals(ShellActivityPulse.sweepStartFraction(0f),
            ShellActivityPulse.sweepStartFraction(0.999f), .01f);
        float previous = ShellActivityPulse.sweepStartFraction(0f);
        for (float phase = 0.005f; phase <= 1f; phase += 0.005f) {
            float current = ShellActivityPulse.sweepStartFraction(phase);
            assertTrue("jump at " + phase, Math.abs(current - previous) < 0.05f);
            previous = current;
        }
    }

    @Test
    public void theSweepWindowNeverLeavesThePill() {
        for (float phase = 0f; phase <= 1f; phase += 0.005f) {
            float start = ShellActivityPulse.sweepStartFraction(phase);
            assertTrue("start " + start, start >= 0f);
            assertTrue("end " + (start + ShellActivityPulse.SWEEP_WIDTH_FRACTION),
                start + ShellActivityPulse.SWEEP_WIDTH_FRACTION <= 1.0001f);
        }
        // And it actually travels the full available span rather than sitting still.
        assertEquals(0f, ShellActivityPulse.sweepStartFraction(0f), .0001f);
        assertEquals(1f - ShellActivityPulse.SWEEP_WIDTH_FRACTION,
            ShellActivityPulse.sweepStartFraction(0.5f), .0001f);
    }
}
