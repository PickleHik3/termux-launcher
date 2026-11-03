package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class PaneAmbientTest {

    @Test public void playsOnlyWhenEveryGateHolds() {
        assertTrue(PaneAmbient.ambientAllowed(true, true, true, false, false, true, true, true, true));
        // Each gate on its own stops it.
        for (int i = 0; i < 9; i++) {
            boolean[] f = {true, true, true, false, false, true, true, true, true};
            f[i] = !f[i];
            assertFalse("gate " + i,
                PaneAmbient.ambientAllowed(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8]));
        }
    }

    @Test public void theSwitchOffAloneStopsIt() {
        assertFalse(PaneAmbient.ambientAllowed(true, false, true, false, false, true, true, true, true));
    }

    @Test public void tearsComeEveryTwoToFiveMinutes() {
        assertEquals(120_000L, PaneAmbient.TEAR_MIN_MS);
        assertEquals(300_000L, PaneAmbient.TEAR_MAX_MS);
        Random random = new Random(42L);
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < 10_000; i++) {
            long d = PaneAmbient.nextTearDelayMs(random);
            assertTrue(d >= PaneAmbient.TEAR_MIN_MS);
            assertTrue(d <= PaneAmbient.TEAR_MAX_MS);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        // Spread over the whole range, not stuck at one end.
        assertTrue(min < 130_000L);
        assertTrue(max > 290_000L);
    }

    @Test public void theAmbientTearIsShort() {
        assertTrue(PaneScreenEvent.Kind.AMBIENT_TEAR.durationMs < 250L);
        assertEquals(6, PaneScreenEvent.Kind.AMBIENT_TEAR.id);
    }

    @Test public void flickerStaysWithinAFewPercent() {
        assertEquals(0f, PaneAmbient.flickerAmplitude(false), 0f);
        assertEquals(0.03f, PaneAmbient.flickerAmplitude(true), 0f);
        float f = PaneAmbient.flickerAmplitude(true);
        for (int i = 0; i < 20_000; i++) {
            float shade = PaneAmbient.flickerShade(f, i * 0.05f);
            assertTrue(shade >= -1e-6f);
            assertTrue(shade <= f + 1e-6f);
        }
    }

    @Test public void flickerHasNoFastSwings() {
        // Never more than 3 dips a second (10-22 spec decision 5): count local minima over 60 s.
        float f = PaneAmbient.FLICKER_MAX;
        int minima = 0;
        float prev2 = PaneAmbient.flickerShade(f, 0f);
        float prev = PaneAmbient.flickerShade(f, 0.01f);
        for (int i = 2; i <= 6000; i++) {
            float cur = PaneAmbient.flickerShade(f, i * 0.01f);
            if (prev > prev2 && prev > cur) minima++; // most shade = darkest
            prev2 = prev;
            prev = cur;
        }
        assertTrue("dips " + minima, minima <= 3 * 60);
    }

    @Test public void theClockWrapsAtAThousandSeconds() {
        assertEquals(0f, PaneAmbient.clockSeconds(0L), 0f);
        assertEquals(0f, PaneAmbient.clockSeconds(-5L), 0f);
        assertEquals(1.5f, PaneAmbient.clockSeconds(1500L), 1e-6f);
        assertEquals(0f, PaneAmbient.clockSeconds(1_000_000L), 0f);
        assertEquals(2.25f, PaneAmbient.clockSeconds(1_002_250L), 1e-6f);
        assertTrue(PaneAmbient.clockSeconds(999_999L) < PaneAmbient.TIME_WRAP_S);
    }

    @Test public void theWrapIsSeamless() {
        float f = PaneAmbient.FLICKER_MAX;
        assertEquals(PaneAmbient.flickerShade(f, 0f),
            PaneAmbient.flickerShade(f, PaneAmbient.TIME_WRAP_S), 1e-4f);
    }
}
