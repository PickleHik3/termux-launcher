package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScrubWaveTest {

    @Test public void envelopeAtSlots() {
        assertEquals(1f, ScrubWave.envelope(0f), 0f);
        assertEquals((float) Math.exp(-0.85), ScrubWave.envelope(1f), 1e-6f);
        assertEquals((float) Math.exp(-3.4), ScrubWave.envelope(2f), 1e-6f);
        assertEquals(ScrubWave.envelope(1.5f), ScrubWave.envelope(-1.5f), 0f);
    }

    @Test public void liftIsMonotoneAndCapped() {
        float prev = -1f;
        for (int i = 0; i <= 20; i++) {
            float l = ScrubWave.lift(i / 20f);
            assertTrue(l >= prev);
            prev = l;
        }
        assertEquals(0f, ScrubWave.lift(0f), 0f);
        assertEquals(1f, ScrubWave.lift(1f), 1e-6f);
        assertEquals(ScrubWave.lift(1f), ScrubWave.lift(1.7f), 0f);
    }
}
