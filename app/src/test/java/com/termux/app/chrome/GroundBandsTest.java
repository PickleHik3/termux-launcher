package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GroundBandsTest {

    private static final float STRIDE = 1000f;
    private static final float FEATHER = 60f;
    private static final int CLEAR = 0x00000000;
    private static final int DIM = 0x4D000000;
    private static final int TINT = 0xB3202020;

    private static GroundBands bands(int[] grounds, int current, float offset) {
        GroundBands b = new GroundBands();
        b.set(grounds, grounds.length, current, STRIDE, offset, FEATHER);
        return b;
    }

    private static void assertCovers(GroundBands b) {
        assertEquals(0f, b.start(0), 0f);
        assertEquals(STRIDE, b.end(b.count() - 1), 0.01f);
        for (int i = 1; i < b.count(); i++) assertEquals(b.end(i - 1), b.start(i), 0.01f);
    }

    @Test
    public void atRestThereIsOneBandOfTheCurrentGround() {
        int[] g = {CLEAR, DIM, TINT};
        for (int current = 0; current < 3; current++) {
            GroundBands b = bands(g, current, 0f);
            assertEquals(1, b.count());
            assertEquals(g[current], b.color(0));
            assertCovers(b);
        }
    }

    @Test
    public void midSlideAFeatheredSeamSitsOnThePageEdge() {
        GroundBands b = bands(new int[]{DIM, TINT}, 0, -500f);
        assertCovers(b);
        assertEquals(GroundBands.SEAM_STEPS + 2, b.count());
        assertEquals(DIM, b.color(0));
        assertEquals(TINT, b.color(b.count() - 1));
        assertEquals(500f - FEATHER / 2f, b.end(0), 0.01f);
        assertEquals(500f + FEATHER / 2f, b.start(b.count() - 1), 0.01f);
        int mid = b.color(GroundBands.SEAM_STEPS / 2);
        assertTrue((mid >>> 24) > (DIM >>> 24) && (mid >>> 24) < (TINT >>> 24));
    }

    @Test
    public void theSeamTravelsWithTheEdge() {
        GroundBands b = bands(new int[]{DIM, TINT}, 0, -250f);   // edge at 750
        assertEquals(750f - FEATHER / 2f, b.end(0), 0.01f);
        GroundBands c = bands(new int[]{DIM, TINT}, 1, 750f);    // same wall position from stop 1
        assertEquals(b.end(0), c.end(0), 0.01f);
    }

    @Test
    public void theSeamNarrowsAsTheEdgeLeavesTheViewport() {
        GroundBands b = bands(new int[]{DIM, TINT}, 0, -990f);   // edge at 10 px
        assertCovers(b);
                assertEquals(20f, b.start(b.count() - 1), 0.01f);
        GroundBands nearlyRest = bands(new int[]{DIM, TINT}, 0, -1f);
        assertCovers(nearlyRest);
        // A 1 px seam at the edge: the last step is within a hair of the arriving ground.
        assertEquals(TINT >>> 24, nearlyRest.color(nearlyRest.count() - 1) >>> 24, 8);
    }

    @Test
    public void equalNeighboursHaveNoSeam() {
        GroundBands b = bands(new int[]{DIM, DIM}, 0, -500f);
        assertEquals(1, b.count());
        assertEquals(DIM, b.color(0));
        assertCovers(b);
    }

    @Test
    public void farOffsetsClampToTheEndStops() {
        GroundBands left = bands(new int[]{DIM, TINT}, 0, 700f);
        assertEquals(1, left.count());
        assertEquals(DIM, left.color(0));
        GroundBands right = bands(new int[]{DIM, TINT}, 1, -700f);
        assertEquals(1, right.count());
        assertEquals(TINT, right.color(0));
        GroundBands gone = bands(new int[]{DIM, TINT}, 0, -1500f);
        assertEquals(1, gone.count());
        assertEquals(TINT, gone.color(0));
    }

    @Test
    public void theFeatherWidthIsHonoured() {
        GroundBands b = new GroundBands();
        b.set(new int[]{DIM, TINT}, 2, 0, STRIDE, -500f, 24f);
        assertEquals(500f - 12f, b.end(0), 0.01f);
        assertEquals(500f + 12f, b.start(b.count() - 1), 0.01f);
        b.set(new int[]{DIM, TINT}, 2, 0, STRIDE, -500f, 0f);
        assertEquals(2, b.count());
        assertEquals(500f, b.end(0), 0.01f);
    }

    @Test
    public void threeStopsShowOnlyTheSeamInView() {
        int[] g = {CLEAR, DIM, TINT};
        GroundBands b = bands(g, 1, 400f);   // edge 0|1 at 400
        assertCovers(b);
        assertEquals(CLEAR, b.color(0));
        assertEquals(DIM, b.color(b.count() - 1));
        GroundBands c = bands(g, 1, -400f);  // edge 1|2 at 600
        assertEquals(DIM, c.color(0));
        assertEquals(TINT, c.color(c.count() - 1));
    }

    @Test
    public void oneStopIsOneBand() {
        GroundBands b = new GroundBands();
        b.set(new int[]{TINT}, 1, 0, STRIDE, -300f, FEATHER);
        assertEquals(1, b.count());
        assertEquals(TINT, b.color(0));
    }

    @Test
    public void groundUnderIsTheColourAtTheRectsCentre() {
        GroundBands b = bands(new int[]{DIM, TINT}, 0, -500f);
        assertEquals(DIM, b.groundUnder(0f, 400f));
        assertEquals(TINT, b.groundUnder(700f, 1000f));
        assertEquals(DIM, b.groundUnder(100f, 800f));   // centre 450
        assertEquals(TINT, b.groundUnder(2000f, 3000f));
        assertEquals(DIM, b.groundUnder(-500f, -400f));
    }

    @Test
    public void theSeamInterpolatesThroughPremultipliedColour() {
        int mid = GroundBands.lerp(0x00000000, 0xFF000000, 0.5f);
        assertEquals(0x80, mid >>> 24);
        assertEquals(0, mid & 0x00FFFFFF);
        // Clear carries no colour, so the other side's colour is not diluted by it.
        assertEquals(0xFF, GroundBands.lerp(0x00000000, 0xFFFFFFFF, 0.5f) & 0xFF);
    }
}
