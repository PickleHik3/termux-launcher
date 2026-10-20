package com.termux.app.launcher.icon;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    /** The measuring raster every synthetic icon below is drawn on. */
    private static final int SIZE = ArtworkFootprint.MEASURE_PX;

    @Test
    public void theDrawnBoxKeepsTheFaintEdgeTheVisibleBoxLeavesOut() {
        int[] pixels = new int[10 * 10];
        for (int y = 3; y < 7; y++)
            for (int x = 4; x < 8; x++)
                pixels[y * 10 + x] = OPAQUE_WHITE;
        pixels[1 * 10 + 1] = 0x20000000;
        pixels[9 * 10 + 9] = 0x05000000;
        assertArrayEquals(new int[] {1, 1, 8, 7}, ArtworkFootprint.drawnBounds(pixels, 10, 10));
        assertNull(ArtworkFootprint.drawnBounds(new int[16], 4, 4));
    }

    @Test
    public void theCentredExtentCountsTheSideThatReachesFurthest() {
        assertEquals(0.8f, ArtworkFootprint.centredExtent(new int[] {10, 10, 90, 90}, 100, 100), 1e-6f);
        // A glyph half the box wide sitting right of centre reaches 0.4 from it: scaled about the
        // centre, that side meets the edge first.
        assertEquals(0.8f, ArtworkFootprint.centredExtent(new int[] {40, 45, 90, 55}, 100, 100), 1e-6f);
    }

    /**
     * The reported case (Arcticons, 2026-10-14): a line pack whose sampled icons are square art
     * at 75% of their box, and a round icon (Clock, Chrome) whose circle spans 87.5%. At the
     * pack's scale the circle reached past its box and lost its top, bottom and sides; held to
     * its own box it is a whole circle as big as a stock icon, and the square art keeps its size.
     */
    @Test
    public void aRoundIconStaysWholeWhereTheSquareArtSetTheScale() {
        int[] square = squareOutline(12, 84, 3);
        int[] round = ring(42f, 3f);
        float squareExtent = ArtworkFootprint.extent(
            ArtworkFootprint.visibleBounds(square, SIZE, SIZE), SIZE, SIZE);
        float packScale = ArtworkFootprint.packScale(1f,
            ArtworkFootprint.median(new float[] {squareExtent, squareExtent, squareExtent}, 3));
        assertEquals(1f / 0.75f, packScale, 1e-6f);
        assertTrue("the circle at the pack's scale is cut, which is the bug",
            spillsOutsideTheBox(round, packScale));

        float roundScale = ArtworkFootprint.iconScale(packScale, 1f, drawnCentredExtent(round));
        assertTrue("the circle still grows", roundScale > 1f);
        assertFalse("and is whole", spillsOutsideTheBox(round, roundScale));
        assertEquals("as big as a stock icon", 1f, drawnCentredExtent(round) * roundScale, 1e-6f);

        float squareScale = ArtworkFootprint.iconScale(packScale, 1f, drawnCentredExtent(square));
        assertEquals("the square art keeps the size it had", packScale, squareScale, 0f);
        assertFalse(spillsOutsideTheBox(square, squareScale));
    }

    @Test
    public void anIconIsHeldToTheStockFootprintNotJustItsBox() {
        // A mask with a margin of its own (an OEM's 90%): a pack icon is never drawn past it.
        assertEquals(0.9f / 0.8f, ArtworkFootprint.iconScale(1.4f, 0.9f, 0.8f), 1e-6f);
        assertEquals(1.2f, ArtworkFootprint.iconScale(1.2f, 0.9f, 0.6f), 0f);
    }

    @Test
    public void anIconIsNeverShrunkAndAnUnmeasuredOneIsLeftAsItShips() {
        assertEquals("reaches past the stock footprint already", 1f,
            ArtworkFootprint.iconScale(1.3f, 1f, 1f), 0f);
        assertEquals("under one measured pixel", 1f, ArtworkFootprint.iconScale(1.3f, 1f, 0.996f), 0f);
        assertEquals("unmeasured", 1f, ArtworkFootprint.iconScale(1.3f, 1f, 0f), 0f);
        assertEquals("a pack left as it ships", 1f, ArtworkFootprint.iconScale(1f, 1f, 0.5f), 0f);
    }

    private static float drawnCentredExtent(int[] pixels) {
        return ArtworkFootprint.centredExtent(
            ArtworkFootprint.drawnBounds(pixels, SIZE, SIZE), SIZE, SIZE);
    }

    /** Whether any drawn pixel, enlarged {@code scale} times about the centre, leaves the box. */
    private static boolean spillsOutsideTheBox(int[] pixels, float scale) {
        float[] place = ArtworkFootprint.placementAboutCentre(scale);
        float tolerance = 1e-5f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if ((pixels[y * SIZE + x] >>> 24) < ArtworkFootprint.MIN_VISIBLE_ALPHA) continue;
                float left = place[0] + x / (float) SIZE * scale;
                float right = place[0] + (x + 1) / (float) SIZE * scale;
                float top = place[1] + y / (float) SIZE * scale;
                float bottom = place[1] + (y + 1) / (float) SIZE * scale;
                if (left < -tolerance || top < -tolerance
                    || right > 1f + tolerance || bottom > 1f + tolerance) return true;
            }
        }
        return false;
    }

    /** A line-art circle about the centre, its stroke's outer edge at {@code outerRadius} px. */
    private static int[] ring(float outerRadius, float stroke) {
        int[] pixels = new int[SIZE * SIZE];
        float centre = SIZE / 2f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double distance = Math.hypot(x + 0.5 - centre, y + 0.5 - centre);
                if (distance <= outerRadius && distance >= outerRadius - stroke)
                    pixels[y * SIZE + x] = OPAQUE_WHITE;
            }
        }
        return pixels;
    }

    /** A line-art square from {@code start} to {@code end} px on both axes. */
    private static int[] squareOutline(int start, int end, int stroke) {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = start; y < end; y++) {
            for (int x = start; x < end; x++) {
                boolean edge = x < start + stroke || x >= end - stroke
                    || y < start + stroke || y >= end - stroke;
                if (edge) pixels[y * SIZE + x] = OPAQUE_WHITE;
            }
        }
        return pixels;
    }
}
