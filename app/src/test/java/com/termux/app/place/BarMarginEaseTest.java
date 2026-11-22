package com.termux.app.place;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A still bar easing its Margin across a crossing: its glass stretches to the eased width, and its
 * content keeps its size while each end's cluster moves with its edge.
 */
public class BarMarginEaseTest {

    private static final float EPSILON = 1e-4f;

    @Test
    public void growIsTheLaidInsetLessTheEasedOne() {
        assertEquals(12f, BarMarginEase.growPx(16f, 4f), EPSILON);
        assertEquals(-12f, BarMarginEase.growPx(4f, 16f), EPSILON);
        assertEquals(0f, BarMarginEase.growPx(8f, 8f), EPSILON);
    }

    /** A glass layer's sides stand exactly {@code grow} further out, about the bar's centre. */
    @Test
    public void aGlassLayerStretchesItsSidesByTheGrow() {
        float half = 500f;
        for (float grow : new float[] {-24f, -6f, 0f, 6f, 24f}) {
            float scale = BarMarginEase.layerScale(half, grow);
            assertEquals("side at " + grow, half + grow, half * scale, EPSILON);
        }
        assertEquals("no width, no scale", 1f, BarMarginEase.layerScale(0f, 12f), EPSILON);
        assertEquals("never mirrored", 0.01f, BarMarginEase.layerScale(10f, -50f), EPSILON);
    }

    /**
     * The start cluster moves with the start edge, the end cluster with the end edge, a centred
     * piece stays: so a bar shrinking by 12 a side keeps its chips and readings 12 inside its
     * glass, at their own size.
     */
    @Test
    public void eachClusterMovesWithItsOwnEdge() {
        int row = 1000;
        assertEquals(-12f, BarMarginEase.contentShiftPx(0, 600, row, 12f), EPSILON);
        assertEquals(12f, BarMarginEase.contentShiftPx(800, 1000, row, 12f), EPSILON);
        assertEquals(0f, BarMarginEase.contentShiftPx(400, 600, row, 12f), EPSILON);
        // Shrinking: both move inward.
        assertEquals(6f, BarMarginEase.contentShiftPx(0, 300, row, -6f), EPSILON);
        assertEquals(-6f, BarMarginEase.contentShiftPx(900, 1000, row, -6f), EPSILON);
    }
}
