package com.termux.app.surfaces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The preset tiles' geometry, rewritten for the corner crop that replaced the scaled-down phone.
 *
 * <p>The old mock multiplied every value a preset differs by into invisibility: at
 * {@code presetScale() = 0.1167} a 24 dp corner drew as 2.8 dp and a 4 dp margin as half a pixel,
 * and four different looks came out as four near-identical dark outlines. Everything here is
 * therefore a difference test — the four shipped presets have to produce visibly different
 * numbers — plus the one rule that must not be unified: Docked and Floating stay two formulas.
 */
public class SurfaceEditorPresetPreviewTest {

    /** The two reference densities: 180 dpi (1.125x) and 260 dpi (1.625x). */
    private static final float LOW = 1.125f;
    private static final float HIGH = 1.625f;

    private static int width(float density) {
        return Math.round(SurfaceEditorPresetPreview.CARD_WIDTH_DP * density);
    }

    private static int height(float density) {
        return Math.round(SurfaceEditorPresetPreview.CARD_HEIGHT_DP * density);
    }

    // ------------------------------------------------------------------ nothing is scaled down

    @Test
    public void aPresetsMarginIsDrawnAtTheDpItReallyIs() {
        for (float density : new float[] {LOW, HIGH}) {
            int[] insets = SurfaceEditorPresetPreview.surfaceInsets(
                width(density), height(density), density, 10, true);
            assertEquals("10 dp of margin is 10 dp of margin",
                10f, insets[0] / density, 0.6f);
            assertEquals(insets[0], insets[3]);
        }
    }

    @Test
    public void aPresetsCornerIsDrawnAtTheDpItReallyIs() {
        for (float density : new float[] {LOW, HIGH}) {
            float[] radii = SurfaceEditorPresetPreview.surfaceCornerRadiiPx(density, 24, true);
            assertEquals(24f, radii[0] / density, 0.01f);
            // A third of the tile's width, which is the whole point of drawing it at true size.
            assertTrue(radii[0] > width(density) / 4f);
        }
    }

    @Test
    public void theFourShippedLooksProduceFourDifferentTiles() {
        // Classic, Mist, Slate and Bare differ in radius, margin and dock style; every one of
        // those has to come out as a different number on the tile.
        float density = HIGH;
        int w = width(density);
        int h = height(density);

        int[] roomy = SurfaceEditorPresetPreview.surfaceInsets(w, h, density, 16, true);
        int[] tight = SurfaceEditorPresetPreview.surfaceInsets(w, h, density, 4, true);
        int[] flush = SurfaceEditorPresetPreview.surfaceInsets(w, h, density, 16, false);
        assertTrue("a wide margin reads wider than a narrow one", roomy[0] > tight[0]);
        assertTrue("and a narrow one still reads at all", tight[0] > 0);
        assertEquals("a docked surface is flush whatever its margin says", 0, flush[0]);
        assertEquals(0, flush[3]);

        assertNotEquals(SurfaceEditorPresetPreview.surfaceCornerRadiiPx(density, 0, false)[0],
            SurfaceEditorPresetPreview.surfaceCornerRadiiPx(density, 24, true)[0], 0.5f);
        assertNotEquals(SurfaceEditorPresetPreview.tileCornerPx(density, 0),
            SurfaceEditorPresetPreview.tileCornerPx(density, 24), 0.5f);
    }

    @Test
    public void everyTileKeepsABandOfCropForOpacityAndBlurToReadAgainst() {
        for (float density : new float[] {LOW, HIGH}) {
            for (boolean floating : new boolean[] {true, false}) {
                int[] insets = SurfaceEditorPresetPreview.surfaceInsets(
                    width(density), height(density), density, 0, floating);
                assertTrue("a surface with no margin still leaves the band", insets[1] > 0);
                assertEquals(SurfaceEditorPresetPreview.WALLPAPER_BAND_DP,
                    insets[1] / density, 0.6f);
            }
        }
    }

    // -------------------------------------------------------- Docked and Floating stay two rules

    @Test
    public void dockedRoundsOnlyTheEdgeItDoesNotTouch() {
        float[] docked = SurfaceEditorPresetPreview.surfaceCornerRadiiPx(HIGH, 24, false);
        assertTrue("the top leading corner is the one off the screen's edge", docked[0] > 0f);
        assertEquals("flush at the bottom is square at the bottom", 0f, docked[3], 0.001f);
    }

    @Test
    public void floatingRoundsEveryCornerItShows() {
        float[] floating = SurfaceEditorPresetPreview.surfaceCornerRadiiPx(HIGH, 24, true);
        assertTrue(floating[0] > 0f);
        assertEquals("a card is a card at both ends", floating[0], floating[3], 0.001f);
        // The trailing corners are always square: the tile is a crop and the surface runs off it.
        assertEquals(0f, floating[1], 0.001f);
        assertEquals(0f, floating[2], 0.001f);
    }

    // --------------------------------------------------------------------------------- the blur

    @Test
    public void moreBlurSamplesTheCropSmaller() {
        int side = width(HIGH);
        int none = SurfaceEditorPresetPreview.backdropSamplePx(side, HIGH, 0);
        int some = SurfaceEditorPresetPreview.backdropSamplePx(side, HIGH, 12);
        int most = SurfaceEditorPresetPreview.backdropSamplePx(side, HIGH, 30);
        assertEquals("no blur is no resample at all", side, none);
        assertTrue(none + " !> " + some, none > some);
        assertTrue(some + " !> " + most, some > most);
        assertTrue("and the crop is never sampled away entirely", most >= 1);
    }

    // ---------------------------------------------------------------------------- the boundaries

    @Test
    public void aMarginTooBigForTheTileCannotSwallowIt() {
        for (float density : new float[] {LOW, HIGH}) {
            int w = width(density);
            int h = height(density);
            int[] insets = SurfaceEditorPresetPreview.surfaceInsets(w, h, density, 480, true);
            assertTrue("the surface still has width", insets[0] + insets[2] < w);
            assertTrue("and still has height", insets[1] + insets[3] < h);
        }
    }

    @Test
    public void theTileIsNeverRounderThanARectangleCanBe() {
        for (float density : new float[] {LOW, HIGH}) {
            float corner = SurfaceEditorPresetPreview.tileCornerPx(density, 240);
            assertTrue("a stadium stops reading as a radius",
                corner <= height(density) / 2f + 0.5f);
        }
    }

    @Test
    public void theStripIsFiveTilesAndFourGaps() {
        float density = HIGH;
        int gap = Math.round(8 * density);
        int strip = SurfaceEditorPresetPreview.stripWidthPx(5, gap, density);
        assertEquals((5 * width(density)) + (4 * gap), strip);
        assertEquals(392f, strip / density, 2f);
    }
}
