package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The settle a finger hands its motion to: it starts where the finger left it and at the finger's
 * own speed, comes to rest without passing it, and is stiffened rather than overshooting when it
 * is thrown at its rest.
 */
public class SettleSpringTest {

    private static final float EPS = 0.01f;
    private static final float OMEGA = 18f;
    private static final float MAX_OMEGA = 60f;

    @Test
    public void itStartsWhereTheFingerLeftItAndAtItsSpeed() {
        SettleSpring spring = SettleSpring.of(-400f, 1500f, OMEGA, MAX_OMEGA);
        assertEquals(-400f, spring.displacementAt(0f), EPS);
        assertEquals(1500f, spring.velocityAt(0f), EPS);
        // One millisecond on, it has moved at the finger's speed: no seam at the release.
        float moved = spring.displacementAt(0.001f) - spring.displacementAt(0f);
        assertEquals(1.5f, moved, 0.1f);
    }

    @Test
    public void fromStillAWholeWidthTakesAboutTheWindowPansTime() {
        SettleSpring spring = SettleSpring.of(1080f, 0f, OMEGA, MAX_OMEGA);
        long ms = spring.durationMs(0.5f);
        assertTrue("a whole width from still: " + ms, ms >= 480L && ms <= 640L);
        assertEquals(0f, spring.displacementAt(ms / 1000f), 0.5f);
        // Most of the way is covered early, the way a settle curve lands.
        assertTrue(Math.abs(spring.displacementAt(0.15f)) < 1080f * 0.3f);
    }

    @Test
    public void itNeverPassesItsRest() {
        float[][] releases = {
            {1080f, 0f}, {-700f, -3000f}, {500f, -2500f}, {60f, -8000f}, {-30f, 20000f},
            {300f, 900f}, {-900f, 400f},
        };
        for (float[] release : releases) {
            SettleSpring spring = SettleSpring.of(release[0], release[1], OMEGA, MAX_OMEGA);
            float sign = Math.signum(release[0]);
            for (int ms = 0; ms <= 3000; ms += 2) {
                float x = spring.displacementAt(ms / 1000f);
                assertTrue("released at " + release[0] + " moving " + release[1] + ", at " + ms
                    + " ms it stood at " + x, x * sign >= -EPS);
            }
        }
    }

    @Test
    public void aThrowAtItsRestStiffensTheSpringInsteadOfOvershooting() {
        // Thrown at the rest faster than the base spring would go there itself.
        SettleSpring thrown = SettleSpring.of(100f, -5000f, OMEGA, MAX_OMEGA);
        assertEquals(50f, thrown.omega(), EPS);
        // Thrown slower, or away from the rest, the base spring is left as it is.
        assertEquals(OMEGA, SettleSpring.of(100f, -500f, OMEGA, MAX_OMEGA).omega(), EPS);
        assertEquals(OMEGA, SettleSpring.of(100f, 5000f, OMEGA, MAX_OMEGA).omega(), EPS);
        // Never past the stiffest allowed.
        assertEquals(MAX_OMEGA, SettleSpring.of(10f, -5000f, OMEGA, MAX_OMEGA).omega(), EPS);
    }

    @Test
    public void thrownAwayItGoesOnAWhileAndComesBack() {
        SettleSpring spring = SettleSpring.of(200f, 1500f, OMEGA, MAX_OMEGA);
        float furthest = 0f;
        for (int ms = 0; ms <= 400; ms += 2) {
            furthest = Math.max(furthest, spring.displacementAt(ms / 1000f));
        }
        assertTrue("carried on by the throw: " + furthest, furthest > 200f);
        long ms = spring.durationMs(0.5f);
        assertEquals(0f, spring.displacementAt(ms / 1000f), 0.5f);
    }

    @Test
    public void aSpringAtRestIsDoneAtOnceAndNothingIsNotANumber() {
        SettleSpring still = SettleSpring.of(0f, 0f, OMEGA, MAX_OMEGA);
        assertEquals(0f, still.displacementAt(0.2f), EPS);
        assertEquals(1L, still.durationMs(0.5f));
        SettleSpring broken = SettleSpring.of(Float.NaN, Float.NaN, OMEGA, MAX_OMEGA);
        assertEquals(0f, broken.displacementAt(0.1f), EPS);
        assertEquals(0f, broken.velocityAt(0f), EPS);
    }

    @Test
    public void theDurationIsBounded() {
        long ms = SettleSpring.of(1e6f, 0f, 1f, 1f).durationMs(0.5f);
        assertTrue(ms <= SettleSpring.MAX_DURATION_MS);
    }
}
