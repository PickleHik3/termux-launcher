package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Where the run's two marks are allowed to land.
 *
 * <p>A portrait phone's numbers at 3x throughout — a 1080 x 2400 overlay, a 120 px (40 dp) corner
 * square, an 18 px (6 dp) slop — so a failure reads as the drawing a user would have seen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class TourGlowGeometryTest {

    private static final int W = 1080;
    private static final int H = 2400;
    private static final float SIZE = 120f;
    private static final float SLOP = 18f;
    /** Half a 1.5 dp stroke plus a 6 dp halo at 3x, which is what the ring paints past its rect. */
    private static final float REACH = 20.25f;
    private static final float MARGIN = 6f;

    // ---- the corner square -------------------------------------------------------------------

    @Test
    public void theCornerSquareStraddlesTheFramesBorder() {
        Rect out = new Rect();
        assertTrue(TourGlowGeometry.cornerZone(new Rect(100, 200, 900, 1500), SIZE, SLOP, out));
        // Outward by the slop, inward by the square's own side: the border runs through it.
        assertEquals(new Rect(82, 182, 220, 320), out);
        assertTrue(out.contains(100, 200));
    }

    @Test
    public void theCornerSquaresCentreFallsOnTheBorderSideOfTheFramesCorner() {
        Rect out = new Rect();
        TourGlowGeometry.cornerZone(new Rect(100, 200, 900, 1500), SIZE, SLOP, out);
        // Half the slop nearer the corner than a square that started at the frame's own edge.
        assertEquals(151, out.centerX());
        assertEquals(251, out.centerY());
    }

    @Test
    public void aSlopOfNothingLeavesTheSquareOnTheFramesOwnCorner() {
        Rect out = new Rect();
        assertTrue(TourGlowGeometry.cornerZone(new Rect(100, 200, 900, 1500), SIZE, 0f, out));
        assertEquals(new Rect(100, 200, 220, 320), out);
    }

    @Test
    public void aFrameWithNoSizeHasNoCorner() {
        Rect out = new Rect();
        assertFalse(TourGlowGeometry.cornerZone(new Rect(100, 200, 100, 200), SIZE, SLOP, out));
        assertFalse(TourGlowGeometry.cornerZone(new Rect(100, 200, 900, 1500), 0f, SLOP, out));
    }

    // ---- the glow ----------------------------------------------------------------------------

    @Test
    public void aControlWithRoomAroundItKeepsItsWholePadding() {
        RectF out = new RectF();
        TourGlowGeometry.glowRect(new Rect(300, 900, 700, 1000), 6f, REACH, W, H, MARGIN, out);
        assertEquals(new RectF(294f, 894f, 706f, 1006f), out);
    }

    @Test
    public void aControlFlushWithTheLeftEdgeGivesUpPaddingOnThatSideOnly() {
        RectF out = new RectF();
        TourGlowGeometry.glowRect(new Rect(0, 900, 400, 1000), 6f, REACH, W, H, MARGIN, out);
        // The stroke and its halo are the reach; the ring's outermost pixel lands on the margin.
        assertEquals(MARGIN + REACH, out.left, 0.001f);
        // Nothing has slid: the far side is still the control plus its padding.
        assertEquals(406f, out.right, 0.001f);
        assertEquals(894f, out.top, 0.001f);
        assertEquals(1006f, out.bottom, 0.001f);
    }

    @Test
    public void aControlFlushWithTheRightEdgeGivesUpPaddingOnThatSideOnly() {
        RectF out = new RectF();
        TourGlowGeometry.glowRect(new Rect(700, 900, W, 1000), 6f, REACH, W, H, MARGIN, out);
        assertEquals(694f, out.left, 0.001f);
        assertEquals(W - MARGIN - REACH, out.right, 0.001f);
    }

    @Test
    public void aControlAboveTheTopOfTheOverlayIsBroughtBackOntoIt() {
        RectF out = new RectF();
        // The pane's corner square reaches outside the pane, and a pane flush with the screen puts
        // that square off it.
        TourGlowGeometry.glowRect(new Rect(-18, -18, 120, 120), 6f, REACH, W, H, MARGIN, out);
        assertEquals(MARGIN + REACH, out.left, 0.001f);
        assertEquals(MARGIN + REACH, out.top, 0.001f);
        assertEquals(126f, out.right, 0.001f);
        assertEquals(126f, out.bottom, 0.001f);
    }

    @Test
    public void anOverlayTooSmallToHoldTheMarkDoesNotTurnItInsideOut() {
        RectF out = new RectF();
        TourGlowGeometry.glowRect(new Rect(0, 0, 10, 10), 6f, REACH, 20, 20, MARGIN, out);
        assertTrue(out.left <= out.right);
        assertTrue(out.top <= out.bottom);
    }

    // ---- the finger cue ----------------------------------------------------------------------

    @Test
    public void theCuesCentreIsHeldFarEnoughInForItsWidestRing() {
        RectF bounds = new RectF();
        TourGlowGeometry.cueBounds(W, H, 51f, MARGIN, bounds);
        assertEquals(new RectF(57f, 57f, W - 57f, H - 57f), bounds);
    }

    @Test
    public void aCueOverTheEdgeOfTheScreenComesBackOntoIt() {
        RectF bounds = new RectF();
        TourGlowGeometry.cueBounds(W, H, 51f, MARGIN, bounds);
        float[] point = {-20f, 1200f};
        TourGlowGeometry.clampPoint(point, bounds);
        assertEquals(57f, point[0], 0.001f);
        assertEquals(1200f, point[1], 0.001f);
    }

    @Test
    public void aCueWithRoomIsLeftWhereTheTracePutIt() {
        RectF bounds = new RectF();
        TourGlowGeometry.cueBounds(W, H, 51f, MARGIN, bounds);
        float[] point = {540f, 1200f};
        TourGlowGeometry.clampPoint(point, bounds);
        assertEquals(540f, point[0], 0.001f);
        assertEquals(1200f, point[1], 0.001f);
    }

    @Test
    public void noBoundsAtAllLeavesTheCueAlone() {
        float[] point = {-400f, -400f};
        TourGlowGeometry.clampPoint(point, null);
        assertEquals(-400f, point[0], 0.001f);
        assertEquals(-400f, point[1], 0.001f);
    }
}
