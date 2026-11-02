package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CursorTrailHaloTest {

    // A quad from a tail at x=0 to a head cell at x=100, 20 tall.
    private static final float[] QUAD = {0f, 0f, 100f, 0f, 100f, 20f, 0f, 20f};

    @Test
    public void theHaloIsFaintWithAWideFeatheredSkirt() {
        // Halved from 0.55 on the developer's 11-02 look at pong, where it read as a pale box.
        assertEquals(0.28f, CursorTrailHalo.PEAK_ALPHA, 0f);
        assertEquals(2f, CursorTrailHalo.SKIRT, 0f);
        // Core over skirt, drawn out to the skirt so the clip never shows as an edge.
        org.junit.Assert.assertTrue(CursorTrailHalo.AGSL.contains(
            "float fall = 0.65 * exp(-4.0 * q * q) + 0.35 * exp(-q * q);"));
        double atClip = 0.65 * Math.exp(-4.0 * 4.0) + 0.35 * Math.exp(-4.0);
        org.junit.Assert.assertTrue("under 1 % of the peak at the clip", atClip < 0.01);
    }

    @Test
    public void inflatedBoundsAddTheWidthOnEverySide() {
        float[] out = new float[4];
        CursorTrailHalo.inflatedBounds(QUAD, 12f, out);
        assertEquals(-12f, out[0], 0f);
        assertEquals(-12f, out[1], 0f);
        assertEquals(112f, out[2], 0f);
        assertEquals(32f, out[3], 0f);
    }

    @Test
    public void tailCentroidIsTheTwoCornersFarthestFromTheHead() {
        float[] out = new float[2];
        CursorTrailHalo.tailCentroid(QUAD, 95f, 10f, out);
        assertEquals(0f, out[0], 0f);
        assertEquals(10f, out[1], 0f);
    }

    @Test
    public void alongIsOneAtTheHeadAndTheTailFactorAtTheTail() {
        assertEquals(1f, CursorTrailHalo.strength(
            CursorTrailHalo.along(95f, 10f, 0f, 10f, 95f, 10f)), 1e-6f);
        assertEquals(0.35f, CursorTrailHalo.strength(
            CursorTrailHalo.along(0f, 10f, 0f, 10f, 95f, 10f)), 1e-6f);
        assertEquals(0.35f, CursorTrailHalo.strength(
            CursorTrailHalo.along(-50f, 10f, 0f, 10f, 95f, 10f)), 1e-6f);
    }

    @Test
    public void headEqualToTailCountsAsTheHead() {
        assertEquals(1f, CursorTrailHalo.along(5f, 5f, 3f, 3f, 3f, 3f), 0f);
    }
}
