package com.termux.app.launcher.icon;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A pack is measured once, over a sample of its icons, and drawn at one scale about the centre of
 * the box, so it reads as big as stock icons while its glyphs keep their sizes relative to one
 * another. The measure must ignore a pack's transparent margin and its soft shadow.
 */
public class ArtworkFootprintTest {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    @Test
    public void theVisibleBoxLeavesTheTransparentMarginOut() {
        int[] pixels = new int[12 * 10];
        for (int y = 3; y < 8; y++)
            for (int x = 2; x < 9; x++)
                pixels[y * 12 + x] = OPAQUE_WHITE;
        assertArrayEquals(new int[] {2, 3, 9, 8}, ArtworkFootprint.visibleBounds(pixels, 12, 10));
    }

    @Test
    public void aSoftShadowIsNotPartOfTheVisibleBox() {
        int[] pixels = new int[10 * 10];
        for (int y = 3; y < 7; y++)
            for (int x = 4; x < 8; x++)
                pixels[y * 10 + x] = OPAQUE_WHITE;
        pixels[1 * 10 + 1] = 0x20000000;
        assertArrayEquals(new int[] {4, 3, 8, 7}, ArtworkFootprint.visibleBounds(pixels, 10, 10));
    }

    @Test
    public void nothingVisibleHasNoBox() {
        assertNull(ArtworkFootprint.visibleBounds(new int[16], 4, 4));
        assertNull(ArtworkFootprint.visibleBounds(new int[3], 4, 4));
    }

    @Test
    public void theExtentIsTheLongerSidesShare() {
        assertEquals(0.8f, ArtworkFootprint.extent(new int[] {10, 20, 90, 70}, 100, 100), 1e-6f);
        assertEquals(1f, ArtworkFootprint.extent(new int[] {0, 0, 100, 100}, 100, 100), 0f);
    }

    @Test
    public void theSampleIsSpreadAcrossThePackAndBounded() {
        assertArrayEquals(new int[] {0, 6, 12, 18}, ArtworkFootprint.sampleIndices(24, 4));
        assertArrayEquals(new int[] {0, 1, 2}, ArtworkFootprint.sampleIndices(3, 16));
        assertEquals(16, ArtworkFootprint.sampleIndices(5000, 16).length);
        assertEquals(0, ArtworkFootprint.sampleIndices(0, 16).length);
        int[] spread = ArtworkFootprint.sampleIndices(5000, 16);
        assertTrue("reaches the end of the pack, not just its head", spread[15] > 4000);
    }

    @Test
    public void theMedianIgnoresAFewOddGlyphs() {
        // Line packs: mostly 0.8 of the box, a tall thin glyph and a tiny dot among them.
        float[] extents = {0.8f, 0.82f, 0.3f, 0.79f, 1f, 0.81f, 0f, 0f};
        assertEquals(0.805f, ArtworkFootprint.median(extents, 6), 1e-6f);
        assertEquals(0.8f, ArtworkFootprint.median(new float[] {0.9f, 0.8f, 0.3f}, 3), 0f);
        assertEquals(0f, ArtworkFootprint.median(new float[0], 0), 0f);
    }

    /** The reported case: a pack whose icons span 80% of their box, beside stock icons. */
    @Test
    public void aPackWithAMarginIsEnlargedToTheStockFootprint() {
        assertEquals(1.25f, ArtworkFootprint.packScale(1f, 0.8f), 1e-6f);
        assertEquals(1.15f, ArtworkFootprint.packScale(0.92f, 0.8f), 1e-6f);
    }

    @Test
    public void aPackIsNeverShrunkAndNeverBlownUp() {
        assertEquals("already as big as stock", 1f, ArtworkFootprint.packScale(0.9f, 1f), 0f);
        assertEquals("under one measured pixel", 1f, ArtworkFootprint.packScale(1f, 0.995f), 0f);
        assertEquals("one odd pack", ArtworkFootprint.MAX_PACK_SCALE,
            ArtworkFootprint.packScale(1f, 0.2f), 0f);
        assertEquals("unmeasured", 1f, ArtworkFootprint.packScale(1f, 0f), 0f);
    }

    /**
     * One scale about the centre: a glyph that sits off centre, or is narrower than its siblings,
     * keeps its place and its size relative to them; only the margin falls outside the box.
     */
    @Test
    public void thePackScalesAboutTheCentreOfTheBox() {
        float[] place = ArtworkFootprint.placementAboutCentre(1.25f);
        assertArrayEquals(new float[] {-0.125f, -0.125f, 1.125f, 1.125f}, place, 1e-6f);
        // A narrow glyph at [0.4, 0.6] and a wide one at [0.1, 0.9]: both grow by the same 1.25,
        // and the narrow one is still centred where it was.
        float scale = place[2] - place[0];
        float narrowLeft = place[0] + 0.4f * scale;
        float narrowRight = place[0] + 0.6f * scale;
        float wideLeft = place[0] + 0.1f * scale;
        float wideRight = place[0] + 0.9f * scale;
        assertEquals(0.5f, (narrowLeft + narrowRight) / 2f, 1e-6f);
        assertEquals((wideRight - wideLeft) / (narrowRight - narrowLeft), 0.8f / 0.2f, 1e-5f);
        assertTrue("an 80% glyph fills the box exactly", wideLeft >= -1e-6f && wideRight <= 1f + 1e-6f);
    }
}
