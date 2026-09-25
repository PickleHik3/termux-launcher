package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The saturating dB-above-floor curve: a pure function, no VAD or view involved. */
public class VoiceLevelCurveTest {

    @Test
    public void atOrBelowTheFloorIsZero() {
        assertEquals(0f, VoiceLevelCurve.level(0.02f, 0.02f), 0f);
        assertEquals(0f, VoiceLevelCurve.level(0.01f, 0.02f), 0f);
        assertEquals(0f, VoiceLevelCurve.level(0f, 0.02f), 0f);
    }

    @Test
    public void aSilentOrUnknownFloorIsZero() {
        assertEquals(0f, VoiceLevelCurve.level(0.1f, 0f), 0f);
        assertEquals(0f, VoiceLevelCurve.level(0.1f, -1f), 0f);
        assertEquals(0f, VoiceLevelCurve.level(-1f, 0.02f), 0f);
    }

    @Test
    public void roomNoiseJustOverTheFloorLeavesTheBarEmpty() {
        float floor = 0.02f;
        float rms = (float) (floor * Math.pow(10, 5.0 / 20.0));
        assertEquals(0f, VoiceLevelCurve.level(rms, floor), 0f);
    }

    @Test
    public void about26dBOverTheFloorReachesAboutNinetyPercent() {
        float floor = 0.02f;
        float rms = (float) (floor * Math.pow(10, 26.0 / 20.0));
        assertEquals(0.9f, VoiceLevelCurve.level(rms, floor), 0.01f);
    }

    @Test
    public void theCurveIsMonotonicAndSaturates() {
        float floor = 0.02f;
        float previous = 0f;
        for (int db = 0; db <= 60; db += 5) {
            float rms = (float) (floor * Math.pow(10, db / 20.0));
            float level = VoiceLevelCurve.level(rms, floor);
            assertTrue("level should not fall as dB rises: " + db, level >= previous);
            assertTrue("level should stay within [0, 1]", level >= 0f && level <= 1f);
            previous = level;
        }
        // Far above the floor, the curve is close to its ceiling.
        assertTrue(previous > 0.95f);
    }
}
