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
    public void theRimBreathesBetweenItsFloorAndFullStrength() {
        assertEquals(1f, ShellActivityPulse.rimWeight(0f), .0001f);
        float floor = ShellActivityPulse.rimWeight(0.5f);
        assertTrue("floor " + floor, floor > 0f && floor < 0.5f);
        for (float phase = 0f; phase <= 1f; phase += 0.005f) {
            float weight = ShellActivityPulse.rimWeight(phase);
            assertTrue("weight " + weight, weight >= floor - .0001f && weight <= 1.0001f);
        }
    }

    @Test
    public void theRimBreathIsSmoothAndContinuousAcrossTheWrap() {
        // A corner at either end of the breath reads as a dropped frame rather than as breathing.
        assertEquals(ShellActivityPulse.rimWeight(0f), ShellActivityPulse.rimWeight(0.999f), .01f);
        float previous = ShellActivityPulse.rimWeight(0f);
        for (float phase = 0.005f; phase <= 1f; phase += 0.005f) {
            float current = ShellActivityPulse.rimWeight(phase);
            assertTrue("jump at " + phase, Math.abs(current - previous) < 0.05f);
            previous = current;
        }
    }

    @Test
    public void theBreathFallsOnceAndRisesOncePerCycle() {
        // One swell per cycle. Two would read as a double blink rather than as breathing: out from
        // the peak at the start of the cycle, and back to it by the end, with no turn in between.
        float previous = ShellActivityPulse.rimWeight(0f);
        for (float phase = 0.002f; phase < 0.5f; phase += 0.002f) {
            float current = ShellActivityPulse.rimWeight(phase);
            assertTrue("rose at " + phase, current <= previous);
            previous = current;
        }
        previous = ShellActivityPulse.rimWeight(0.5f);
        for (float phase = 0.502f; phase <= 1f; phase += 0.002f) {
            float current = ShellActivityPulse.rimWeight(phase);
            assertTrue("fell at " + phase, current >= previous);
            previous = current;
        }
    }
}
