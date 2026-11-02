package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CursorTrailHaloTest {

    // A quad from a tail at x=0 to a head cell at x=100, 20 tall.
    private static final float[] QUAD = {0f, 0f, 100f, 0f, 100f, 20f, 0f, 20f};

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
