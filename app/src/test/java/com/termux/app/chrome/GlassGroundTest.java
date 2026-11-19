package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Prototype #283: what a glass over the ground strip draws, read at an x across the seam, as a
 * crossing carries the page edge under it.
 */
public class GlassGroundTest {

    private static final float STRIDE = 1080f;
    private static final float FEATHER = 63f;
    /** Home's dim: none. */
    private static final int HOME = 0x00000000;
    /** The Terminal's dim, 70%. */
    private static final int TERMINAL = 0xB3000000;

    /** Home at stop 0, the Terminal at stop 1; {@code share} of the way from Home. */
    private static GroundBands crossing(float share) {
        GroundBands b = new GroundBands();
        b.set(new int[] {HOME, TERMINAL}, 2, 0, STRIDE, -share * STRIDE, FEATHER);
        return b;
    }

    /** The dim a glass from {@code left} to {@code right} draws at each px, as alpha. */
    private static int[] drawn(GroundBands b, float left, float right) {
        int[] out = new int[(int) (right - left)];
        for (int i = 0; i < out.length; i++) {
            float x = left + i + 0.5f;
            int color = 0;
            for (int band = 0; band < b.count(); band++) {
                float x0 = GlassGround.spanLeft(b, band, left, right - left) + left;
                float x1 = GlassGround.spanRight(b, band, left, right - left) + left;
                if (x >= x0 && x < x1) color = b.color(band);
            }
            out[i] = color >>> 24;
        }
        return out;
    }

    @Test
    public void atRestTheGlassDrawsThePagesDimAcrossItsWidth() {
        for (int a : drawn(crossing(0f), 0f, STRIDE)) assertEquals(0, a);
        for (int a : drawn(crossing(1f), 0f, STRIDE)) assertEquals(TERMINAL >>> 24, a);
    }

    @Test
    public void aDimOfZeroDrawsNothing() {
        GroundBands b = new GroundBands();
        b.set(new int[] {HOME, HOME}, 2, 0, STRIDE, -0.4f * STRIDE, FEATHER);
        for (int a : drawn(b, 0f, STRIDE)) assertEquals(0, a);
    }

    @Test
    public void midCrossingEachSideOfTheSeamShowsItsOwnPagesDim() {
        GroundBands b = crossing(0.5f);
        float edge = STRIDE / 2f;
        assertEquals(HOME, GlassGround.groundAt(b, edge - FEATHER));
        assertEquals(TERMINAL, GlassGround.groundAt(b, edge + FEATHER));
        int[] alpha = drawn(b, 0f, STRIDE);
        assertEquals(0, alpha[0]);
        assertEquals(TERMINAL >>> 24, alpha[alpha.length - 1]);
    }

    @Test
    public void theSeamPassesUnderTheGlassWithoutAStep() {
        // One seam step is the most any px may differ from its neighbour, at every frame.
        int step = (TERMINAL >>> 24) / GroundBands.SEAM_STEPS + 1;
        for (int frame = 1; frame < 60; frame++) {
            int[] alpha = drawn(crossing(frame / 60f), 0f, STRIDE);
            for (int i = 1; i < alpha.length; i++) {
                assertTrue("frame " + frame + " x " + i,
                    Math.abs(alpha[i] - alpha[i - 1]) <= step);
            }
        }
    }

    @Test
    public void aNarrowGlassDrawsTheBandsWhereItStands() {
        // The dock, 600 px wide from 240: the seam at 540 sits in it.
        GroundBands b = crossing(0.5f);
        int[] alpha = drawn(b, 240f, 840f);
        assertEquals(0, alpha[0]);
        assertEquals(TERMINAL >>> 24, alpha[599]);
        // A glass left of the seam sees Home only; right of it the Terminal only.
        for (int a : drawn(b, 0f, 400f)) assertEquals(0, a);
        for (int a : drawn(b, 700f, 1000f)) assertEquals(TERMINAL >>> 24, a);
    }

    @Test
    public void theBandsRunOutToAGlassWiderThanTheWall() {
        GroundBands b = crossing(1f);
        int[] alpha = drawn(b, -30f, STRIDE + 30f);
        assertEquals(TERMINAL >>> 24, alpha[0]);
        assertEquals(TERMINAL >>> 24, alpha[alpha.length - 1]);
    }

    @Test
    public void bandsThatMovedAreNotTheSame() {
        GroundBands a = crossing(0.3f);
        GroundBands b = new GroundBands();
        b.copyFrom(a);
        assertTrue(b.sameAs(a));
        assertFalse(crossing(0.31f).sameAs(a));
    }

    @Test
    public void onlyTheBarsAndTheDockDrawTheGround() {
        assertEquals(GlassGround.ON, GlassGround.drawsGround(GlassBackdropCache.Band.DOCK));
        assertEquals(GlassGround.ON, GlassGround.drawsGround(GlassBackdropCache.Band.STATUS_BAR));
        assertEquals(GlassGround.ON, GlassGround.drawsGround(GlassBackdropCache.Band.WINDOW_BAR));
        assertFalse(GlassGround.drawsGround(GlassBackdropCache.Band.KEYBOARD));
    }
}
