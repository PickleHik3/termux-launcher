package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeamFadeTest {

    private static final float EPS = 1e-6f;

    @Test
    public void nothingOfTheOtherDressShowsBeforeTheMiddleOfTheWay() {
        assertEquals(0f, SeamFade.share(0f), EPS);
        assertEquals(0f, SeamFade.share(0.1f), EPS);
        assertEquals(0f, SeamFade.share(SeamFade.START), EPS);
        assertEquals(0f, SeamFade.share(-0.3f), EPS);
        assertEquals(0f, SeamFade.share(Float.NaN), EPS);
    }

    @Test
    public void allOfItShowsPastTheMiddleOfTheWay() {
        assertEquals(1f, SeamFade.share(SeamFade.END), EPS);
        assertEquals(1f, SeamFade.share(0.9f), EPS);
        assertEquals(1f, SeamFade.share(1f), EPS);
        assertEquals(1f, SeamFade.share(1.4f), EPS);
    }

    @Test
    public void theMiddleIsASmoothstepHalfwayAtTheHalf() {
        assertEquals(0.5f, SeamFade.share(0.5f), EPS);
        // smoothstep(0.25) = 0.15625
        assertEquals(0.15625f, SeamFade.share(0.35f), 1e-5f);
        assertEquals(1f - 0.15625f, SeamFade.share(0.65f), 1e-5f);
    }

    @Test
    public void theCurveRisesWithTheFingerSoACancelReadsItBackDown() {
        float last = -1f;
        for (int i = 0; i <= 100; i++) {
            float s = SeamFade.share(i / 100f);
            assertTrue("never falls going forward", s >= last);
            last = s;
        }
        // A cancelled drag is the same curve read from the other end: back at 0 it is gone.
        last = 2f;
        for (int i = 100; i >= 0; i--) {
            float s = SeamFade.share(i / 100f);
            assertTrue("never rises coming back", s <= last);
            last = s;
        }
        assertEquals(0f, last, 0f);
    }

    @Test
    public void theCurveIsFlatAtBothEndsOfTheFade() {
        float d = 1e-3f;
        assertEquals(0f, (SeamFade.share(SeamFade.START + d) - SeamFade.share(SeamFade.START)) / d,
            0.01f);
        assertEquals(0f, (SeamFade.share(SeamFade.END) - SeamFade.share(SeamFade.END - d)) / d,
            0.01f);
    }

    @Test
    public void aSurfaceOnOneEndOnlyFadesInOrOut() {
        assertEquals(0f, SeamFade.presence(false, true, 0f), EPS);
        assertEquals(0.3f, SeamFade.presence(false, true, 0.3f), EPS);
        assertEquals(1f, SeamFade.presence(false, true, 1f), EPS);
        assertEquals(1f, SeamFade.presence(true, false, 0f), EPS);
        assertEquals(0.7f, SeamFade.presence(true, false, 0.3f), EPS);
        assertEquals(0f, SeamFade.presence(true, false, 1f), EPS);
    }

    @Test
    public void aSurfaceOnBothEndsStaysWholeAndOnNeitherStaysGone() {
        for (float s = 0f; s <= 1f; s += 0.125f) {
            assertEquals(1f, SeamFade.presence(true, true, s), EPS);
            assertEquals(0f, SeamFade.presence(false, false, s), EPS);
        }
        assertEquals(1f, SeamFade.presence(false, true, 3f), EPS);
    }
}
