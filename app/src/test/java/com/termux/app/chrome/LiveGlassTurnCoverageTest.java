package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A turned live slab's frost covers its whole outline: the program's turn term shifts every
 * sample by up to {@code uPoseLight.w}, and on the live route the content is the slab's node,
 * which holds only the picture's margin past the outline. Before, the node was cut at the slab's
 * own bounds and the shift was the optics' own (up to 24 dp), so the near edge read a band of
 * nothing as wide as the shift, and the unfrosted wallpaper showed through it (pong, Carousel).
 */
public class LiveGlassTurnCoverageTest {

    private static final float DENSITY = 2.625f;
    /** The widget card on pong and its live picture's margin: shown=538x682 for 500x644. */
    private static final int WIDTH = 500;
    private static final int HEIGHT = 644;
    private static final int MARGIN = 19;

    private static final int[] TURNS = {0, 30, -30, 60, -60};

    /**
     * The program's turn term for the pixel {@code (x, y)} of the node: {@code sampleCoord -
     * normalize(uPose.xy) * min(faceT * uPose.w, uPoseLight.w)}, with the deepest {@code faceT}
     * the program allows ({@code hs * kSlabHold}), so the shift is the most it can be.
     */
    private static float[] turned(float x, float y, float[] u) {
        float len = (float) Math.hypot(u[0], u[1]);
        if (!(len > 0f)) return new float[] {x, y};
        float hs = Math.min(WIDTH, HEIGHT) / 2f;
        float faceT = hs * GlassSlab.DEFAULT.holdShare();
        float shift = Math.min(faceT * u[3], u[7]);
        return new float[] {x - u[0] / len * shift, y - u[1] / len * shift};
    }

    /** The pose uniforms for a turn about Y of {@code degrees}, held to {@code reachPx}. */
    private static float[] uniforms(int degrees, float reachPx) {
        GlassPose pose = new GlassPose();
        pose.tiltX = (float) Math.sin(Math.toRadians(degrees));
        float[] u = new float[GlassPoseOptics.UNIFORMS];
        GlassPoseOptics.DEFAULT.uniforms(pose, GlassSlab.DEFAULT, DENSITY, u, new float[4]);
        u[7] = GlassRefraction.poseShiftCap(u[7], reachPx);
        return u;
    }

    @Test
    public void theTurnedOutlineReadsInsideTheDrawnNodeAtEveryTurn() {
        float reach = LiveGlassEffect.heldReachPx(MARGIN);
        float nodeW = WIDTH + 2f * MARGIN;
        float nodeH = HEIGHT + 2f * MARGIN;
        float keep = GlassRefraction.FRAME_MARGIN_PX;
        for (int deg : TURNS) {
            float[] u = uniforms(deg, reach);
            // The outline in the node's px, every 10 px round it, corners included.
            for (int i = 0; i <= WIDTH; i += 10) {
                assertInside(deg, turned(MARGIN + i, MARGIN, u), nodeW, nodeH, keep);
                assertInside(deg, turned(MARGIN + i, MARGIN + HEIGHT, u), nodeW, nodeH, keep);
            }
            for (int j = 0; j <= HEIGHT; j += 10) {
                assertInside(deg, turned(MARGIN, MARGIN + j, u), nodeW, nodeH, keep);
                assertInside(deg, turned(MARGIN + WIDTH, MARGIN + j, u), nodeW, nodeH, keep);
            }
        }
    }

    /** The defect's numbers: the unheld shift at the turns the strip shows, past a 0 px margin. */
    @Test
    public void theUnheldTurnReadPastTheOldNodeByTheShift() {
        float unbounded = Float.POSITIVE_INFINITY;
        float hs = Math.min(WIDTH, HEIGHT) / 2f;
        float faceT = hs * GlassSlab.DEFAULT.holdShare();
        // The old node was the slab: the outline's near edge read the whole shift past it.
        float[] at30 = uniforms(30, unbounded);
        float[] at60 = uniforms(60, unbounded);
        float band30 = Math.min(faceT * at30[3], at30[7]);
        float band60 = Math.min(faceT * at60[3], at60[7]);
        assertTrue("a band at 30 degrees: " + band30, band30 > MARGIN);
        assertTrue("wider at 60 degrees: " + band60, band60 > band30);
        // A 20 dp slab at 45 degrees: 52.5 px x (tan 45 - tan 24.6) = 28.5 px, the strip's band.
        float slab20dp = 20f * DENSITY;
        float at45 = slab20dp * GlassPoseOptics.DEFAULT.shiftTan(
            (float) Math.sin(Math.toRadians(45)), GlassSlab.DEFAULT.ior);
        assertEquals(28.5f, at45, 0.5f);
    }

    @Test
    public void flatTheReachChangesNothing() {
        float[] u = uniforms(0, LiveGlassEffect.heldReachPx(MARGIN));
        float[] at = turned(MARGIN, MARGIN, u);
        assertEquals(MARGIN, at[0], 0f);
        assertEquals(MARGIN, at[1], 0f);
        assertEquals(0f, u[0], 0f);
        assertEquals(0f, u[1], 0f);
    }

    @Test
    public void theReachIsTheMarginLessTheFrameKeep() {
        assertEquals(MARGIN - GlassRefraction.FRAME_MARGIN_PX, LiveGlassEffect.heldReachPx(MARGIN), 0f);
        assertEquals(0f, LiveGlassEffect.heldReachPx(1), 0f);
        assertEquals(0f, LiveGlassEffect.heldReachPx(0), 0f);
        assertEquals(7f, GlassRefraction.poseShiftCap(63f, 7f), 0f);
        assertEquals(5f, GlassRefraction.poseShiftCap(5f, 7f), 0f);
    }

    private static void assertInside(int deg, float[] p, float nodeW, float nodeH, float keep) {
        String at = deg + " degrees at " + p[0] + "," + p[1];
        assertTrue(at, p[0] >= keep && p[0] <= nodeW - keep);
        assertTrue(at, p[1] >= keep && p[1] <= nodeH - keep);
    }
}
