package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The concave lens the page bar's glass wears ({@link GlassRefraction#concavePullPx}): pure
 * arithmetic, checked on the bar's own slab (30 dp tall, a capsule) at the densities phones ship.
 */
public class GlassConcaveLensTest {

    private static final float[] DENSITIES = {2.0f, 2.75f, 3.0f};
    private static final float SLAB_DP = 30f;
    private static final float WIDTH_DP = 200f;
    private static final float STEP = 0.05f;

    @Test
    public void nothingOnTheCentreLineNorAtTheOutline() {
        for (float density : DENSITIES) {
            float hs = SLAB_DP * density / 2f;
            float band = GlassRefraction.concaveBandPx(density, 2f * hs);
            assertEquals(0f, GlassRefraction.concavePullPx(hs, band), 0f);
            assertEquals(0f, GlassRefraction.concavePullPx(0f, band), 0f);
            // Continuous at the rim: a hair in, a hair of pull.
            assertTrue(GlassRefraction.concavePullPx(0.1f, band) < 0.05f);
            float[] off = new float[2];
            GlassRefraction.concaveOffset(off, 0f, 0f, WIDTH_DP * density / 2f, hs, hs, density);
            assertEquals(0f, off[0], 0f);
            assertEquals(0f, off[1], 0f);
        }
    }

    @Test
    public void thePullGrowsTowardTheRimDownToTheLip() {
        for (float density : DENSITIES) {
            float band = GlassRefraction.concaveBandPx(density, SLAB_DP * density);
            float lip = GlassRefraction.concaveLipPx(band);
            float last = 0f;
            for (float y = band; y >= lip; y -= STEP) {
                float pull = GlassRefraction.concavePullPx(y, band);
                assertTrue("density " + density + " at " + y, pull >= last - 1e-5f);
                last = pull;
            }
            assertEquals(GlassRefraction.concavePeakPx(band), last, 0.01f);
            assertTrue("the lip is inside the band", lip < band);
        }
    }

    @Test
    public void theBandIsCappedAndTheTwoBandsNeverMeet() {
        for (float density : DENSITIES) {
            float height = SLAB_DP * density;
            float band = GlassRefraction.concaveBandPx(density, height);
            assertTrue(band <= GlassRefraction.CONCAVE_BAND_MAX_SHARE * height + 1e-4f);
            assertTrue(band <= GlassRefraction.CONCAVE_BAND_DP * density + 1e-4f);
            assertTrue("top and bottom bands leave the middle untouched", 2f * band < height);
        }
        assertTrue(GlassRefraction.CONCAVE_BAND_MAX_SHARE < 0.5f);
    }

    @Test
    public void theSampleNeverFoldsBackSoNothingSmearsIntoStreaks() {
        for (float density : DENSITIES) {
            float band = GlassRefraction.concaveBandPx(density, SLAB_DP * density);
            float last = 0f;
            float minSlope = Float.MAX_VALUE;
            for (float y = STEP; y <= band + 1f; y += STEP) {
                float sample = y - GlassRefraction.concavePullPx(y, band);
                minSlope = Math.min(minSlope, (sample - last) / STEP);
                last = sample;
            }
            // Never under 1 - the lip's slope: at most twice stretched, never folded.
            assertTrue("density " + density + ": " + minSlope,
                minSlope >= 1f - GlassRefraction.CONCAVE_LIP_MAX_SLOPE - 0.01f);
        }
    }

    @Test
    public void everySampleStaysInsideTheOutline() {
        float[] off = new float[2];
        for (float density : DENSITIES) {
            float bx = WIDTH_DP * density / 2f;
            float by = SLAB_DP * density / 2f;
            float cr = by;
            for (float px = -bx; px <= bx; px += 0.5f) {
                for (float py = -by; py <= by; py += 0.25f) {
                    float inside = GlassRefraction.insidePx(px, py, bx, by, cr);
                    if (inside < 0f) continue;
                    GlassRefraction.concaveOffset(off, px, py, bx, by, cr, density);
                    float sampled = GlassRefraction.insidePx(px + off[0], py + off[1], bx, by, cr);
                    assertTrue("density " + density + " at " + px + "," + py + ": " + sampled,
                        sampled >= -1e-3f);
                    assertTrue("drawn from further out", sampled <= inside + 1e-3f);
                }
            }
        }
    }

    @Test
    public void itPullsTheOtherWayFromTheConvexRim() {
        float[] concave = new float[2];
        float[] convex = new float[2];
        float density = 2.75f;
        float bx = WIDTH_DP * density / 2f;
        float by = SLAB_DP * density / 2f;
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        // A pixel in the upper band, half way along: the outward normal is straight up.
        float py = -by + GlassRefraction.concaveLipPx(GlassRefraction.concaveBandPx(density, 2f * by));
        GlassRefraction.concaveOffset(concave, 0f, py, bx, by, by, density);
        GlassRefraction.restBendOffset(convex, 0f, py, bx, by, by, look.bandPx(density),
            look.strengthPx(density), 0f, 0f, GlassRefraction.Look.rimWidthPx(density));
        assertTrue("concave samples further out (up)", concave[1] < 0f);
        assertTrue("convex samples further in (down)", convex[1] > 0f);
        assertEquals(1, GlassRefraction.CONCAVE_SIGN);
        assertEquals(-1, GlassRefraction.CONVEX_SIGN);
    }

    /** What streaked before: the convex bend on this slab falls faster than a px per px and folds. */
    @Test
    public void theConvexBendOnThisSlabFoldsWhichIsWhatStreaked() {
        float density = 2.75f;
        float hs = SLAB_DP * density / 2f;
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        float rim = GlassRefraction.Look.rimWidthPx(density);
        float last = 0f;
        boolean folds = false;
        for (float y = STEP; y < hs; y += STEP) {
            float sample = y + GlassRefraction.pullPx(y, look.bandPx(density), look.strengthPx(density), hs, rim);
            if (sample < last) folds = true;
            last = sample;
        }
        assertTrue("the old bend folds the picture back on this slab", folds);
    }

    @Test
    public void theProgramPullsOutwardThroughTheSameNumbers() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("uniform float2 uConcave;"));
        assertTrue(agsl.contains("fragCoord + cn * concavePull(inside, cB, cP, cL)"));
        assertTrue(agsl.contains("const float kConcaveBandShare = " + GlassRefraction.CONCAVE_BAND_MAX_SHARE + ";"));
        assertTrue(agsl.contains("const float kConcavePullShare = " + GlassRefraction.CONCAVE_PULL_SHARE + ";"));
        assertTrue(agsl.contains("const float kConcaveLipSlope = " + GlassRefraction.CONCAVE_LIP_MAX_SLOPE + ";"));
        assertTrue(agsl.contains("float cL = 1.5 * cP / kConcaveLipSlope;"));
    }

    @Test
    public void concaveIsPartOfTheLookAndSurvivesCover() {
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertFalse(look.concave);
        GlassRefraction.Look sunk = look.withConcave(true);
        assertTrue(sunk.concave);
        assertNotEquals(look, sunk);
        assertTrue(sunk.withCover(0.4f).concave);
        assertTrue(sunk.withFaceTerms(10, 10, 10).concave);
        assertEquals(look, sunk.withConcave(false));
        assertEquals(sunk, look.withConcave(true));
        assertEquals(sunk.hashCode(), look.withConcave(true).hashCode());
    }
}
