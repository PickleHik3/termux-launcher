package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Glass slider's curve, its read-back and migration, and the Edge colour's three steps. */
public class GlassControlsTest {

    @Test
    public void theCurveRunsFromClearToFrosted() {
        assertEquals(4, GlassControls.blurForGlass(0));
        assertEquals(2, GlassControls.grainForGlass(0));
        assertEquals(36, GlassControls.blurForGlass(100));
        assertEquals(8, GlassControls.grainForGlass(100));
        assertEquals("clamped", 36, GlassControls.blurForGlass(400));
        assertEquals("clamped", 2, GlassControls.grainForGlass(-5));
        int lastBlur = 0;
        int lastGrain = 0;
        for (int g = GlassControls.GLASS_MIN; g <= GlassControls.GLASS_MAX; g++) {
            int blur = GlassControls.blurForGlass(g);
            int grain = GlassControls.grainForGlass(g);
            assertTrue("blur never falls at " + g, blur >= lastBlur);
            assertTrue("grain never falls at " + g, grain >= lastGrain);
            assertTrue("never sand: " + grain, grain <= 8);
            lastBlur = blur;
            lastGrain = grain;
        }
    }

    /** Clear 20, Tint 40, Mist 75: blur R(4 + 32 t^1.5), grain R(2 + 6 t). */
    @Test
    public void theShippedLooksSitOnTheCurveAndReadBackAtTheirPositions() {
        assertEquals(7, GlassControls.blurForGlass(GlassControls.CLEAR));
        assertEquals(3, GlassControls.grainForGlass(GlassControls.CLEAR));
        assertEquals(12, GlassControls.blurForGlass(GlassControls.TINT));
        assertEquals(4, GlassControls.grainForGlass(GlassControls.TINT));
        assertEquals(25, GlassControls.blurForGlass(GlassControls.MIST));
        assertEquals(7, GlassControls.grainForGlass(GlassControls.MIST));
        assertEquals(20, GlassControls.glassFor(7, 3));
        assertEquals(40, GlassControls.glassFor(12, 4));
        assertEquals(75, GlassControls.glassFor(25, 7));
        assertTrue(GlassControls.onCurve(7, 3));
        assertTrue(!GlassControls.onCurve(4, 4));
        assertEquals("Solid's no-frost pair reads at the clear end", 0, GlassControls.glassFor(0, 0));
    }

    /** Every position's pair reads back as a position that writes the same pair. */
    @Test
    public void readingBackNeverMovesThePair() {
        for (int g = GlassControls.GLASS_MIN; g <= GlassControls.GLASS_MAX; g++) {
            int blur = GlassControls.blurForGlass(g);
            int grain = GlassControls.grainForGlass(g);
            int read = GlassControls.glassFor(blur, grain);
            assertEquals("blur at " + g, blur, GlassControls.blurForGlass(read));
            assertEquals("grain at " + g, grain, GlassControls.grainForGlass(read));
        }
    }

    /**
     * A stored pair moves to its nearest point, squared differences summed and the lowest position
     * on a tie: the old Looks' pairs, and a sandy hand-tuned one.
     */
    @Test
    public void aStoredPairSnapsToTheNearestPoint() {
        assertArrayEquals("old Clear", new int[] {5, 3}, GlassControls.snapped(4, 4));
        assertArrayEquals("old Mist", new int[] {25, 7}, GlassControls.snapped(25, 8));
        assertArrayEquals("old Tint", new int[] {13, 5}, GlassControls.snapped(10, 10));
        assertArrayEquals("on the curve already", new int[] {12, 4}, GlassControls.snapped(12, 4));
        assertArrayEquals("Solid keeps no frost", new int[] {0, 0}, GlassControls.snapped(0, 0));
        assertArrayEquals("no blur is no frost, whatever the grain", new int[] {0, 30},
            GlassControls.snapped(0, 30));
        assertArrayEquals("sand comes down to the curve's tooth", new int[] {25, 7},
            GlassControls.snapped(20, 60));
        // Brute force: no position is nearer than the one chosen.
        for (int blur = 1; blur <= 48; blur += 3) {
            for (int grain = 0; grain <= 100; grain += 7) {
                int[] pair = GlassControls.snapped(blur, grain);
                long best = sq(pair[0] - blur) + sq(pair[1] - grain);
                for (int g = 0; g <= 100; g++) {
                    long error = sq(GlassControls.blurForGlass(g) - blur)
                        + sq(GlassControls.grainForGlass(g) - grain);
                    assertTrue(blur + "/" + grain + " vs " + g, error >= best);
                }
            }
        }
    }

    private static long sq(int v) {
        return (long) v * v;
    }

    /** The slider reads back where it was left while the pair is still that position's. */
    @Test
    public void aReleaseAtFiveStaysAtFive() {
        int blur = GlassControls.blurForGlass(5);
        int grain = GlassControls.grainForGlass(5);
        assertEquals("0 to 7 write this pair", blur, GlassControls.blurForGlass(0));
        assertEquals(5, GlassControls.readBack(5, blur, grain));
        assertEquals("none stored: the nearest", GlassControls.glassFor(blur, grain),
            GlassControls.readBack(-1, blur, grain));
        assertEquals("a pair something else wrote: the nearest", 40,
            GlassControls.readBack(5, 12, 4));
        assertEquals("out of range: the nearest", GlassControls.glassFor(blur, grain),
            GlassControls.readBack(140, blur, grain));
    }

    /** A blur put back on its own lands on the curve beside the grain it will be worn with. */
    @Test
    public void aBlurBesideAGrainLandsOnTheCurve() {
        for (int grain = 2; grain <= 8; grain++) {
            for (int blur = 1; blur <= 48; blur++) {
                int snapped = GlassControls.blurWithGrain(blur, grain);
                assertTrue(blur + "/" + grain + " -> " + snapped,
                    GlassControls.onCurve(snapped, grain));
            }
        }
        assertEquals("no blur stays no blur", 0, GlassControls.blurWithGrain(0, 5));
    }
}
