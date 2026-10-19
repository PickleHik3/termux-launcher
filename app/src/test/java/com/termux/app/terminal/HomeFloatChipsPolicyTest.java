package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The chips minimised Home floats come back from, above the dock: their row and their content. */
public class HomeFloatChipsPolicyTest {

    private static final float D = 2f;
    private static final float GLYPH = 30f;

    @Test
    public void theRowStandsJustAboveTheDockOrAboveTheKeyboardWhileOneIsUp() {
        int band = Math.round((HomeFloatChipsPolicy.CHIP_HEIGHT_DP
            + HomeFloatChipsPolicy.ROW_AIR_DP) * D);
        assertEquals(1800 - band, HomeFloatChipsPolicy.rowTopPx(D, 1800, -1));
        assertEquals("it moves with the dock riding the keyboard", 1100 - band,
            HomeFloatChipsPolicy.rowTopPx(D, 1800, 1100));
    }

    @Test
    public void theFloatsRoomGivesUpTheBandOnlyWhileTheRowShows() {
        assertEquals(Math.round((HomeFloatChipsPolicy.CHIP_HEIGHT_DP
            + HomeFloatChipsPolicy.ROW_AIR_DP) * D), HomeFloatChipsPolicy.bandPx(D, true));
        assertEquals(0, HomeFloatChipsPolicy.bandPx(D, false));
    }

    @Test
    public void aChipIsTheWindowChipsGlyphAndShortName() {
        HomeFloatChipsPolicy.Chip nvim = HomeFloatChipsPolicy.Chip.of(" neovim", "neovim");
        assertEquals("", nvim.glyph);
        assertEquals("neovim", nvim.name);
        HomeFloatChipsPolicy.Chip bare = HomeFloatChipsPolicy.Chip.of("home", "home");
        assertEquals("no glyph: the generic terminal's", ProcessGlyphs.DEFAULT, bare.glyph);
        assertEquals("home", bare.name);
    }

    @Test
    public void chipsWithNamesSitCentredInOneRowWhenTheyFit() {
        HomeFloatChipsPolicy.Layout layout = HomeFloatChipsPolicy.layout(D, 800,
            new float[]{GLYPH, GLYPH}, new float[]{60f, 40f});
        assertTrue(layout.names);
        assertEquals(2, layout.shown);
        float padding = HomeFloatChipsPolicy.CHIP_PADDING_DP * D;
        float nameGap = HomeFloatChipsPolicy.NAME_GAP_DP * D;
        assertEquals(2 * padding + GLYPH + nameGap + 60f, layout.right[0] - layout.left[0], 0.01f);
        assertEquals(HomeFloatChipsPolicy.CHIP_GAP_DP * D, layout.left[1] - layout.right[0],
            0.01f);
        assertEquals(layout.right[1], layout.rowWidth, 0.01f);
    }

    @Test
    public void namesGiveWayFirstThenTheChipsShareTheWidth() {
        float[] glyphs = {GLYPH, GLYPH, GLYPH, GLYPH};
        float[] names = {140f, 140f, 140f, 140f};
        // 600 wide less 2 x 16 of side air: 568 for the row; names make it about 1000.
        HomeFloatChipsPolicy.Layout glyphsOnly = HomeFloatChipsPolicy.layout(D, 600, glyphs, names);
        assertFalse(glyphsOnly.names);
        assertEquals(4, glyphsOnly.shown);
        assertEquals(2 * HomeFloatChipsPolicy.CHIP_PADDING_DP * D + GLYPH,
            glyphsOnly.right[0] - glyphsOnly.left[0], 0.01f);

        // Twelve glyph-only chips need 12 x 70 + 11 x 12 = 972: they share 568 evenly.
        float[] many = new float[12];
        java.util.Arrays.fill(many, GLYPH);
        HomeFloatChipsPolicy.Layout shared = HomeFloatChipsPolicy.layout(D, 600, many,
            new float[12]);
        assertEquals(12, shared.shown);
        assertTrue("never wider than the row's room", shared.rowWidth <= 600 - 32 + 0.5f);
        assertEquals((568f - 11 * 12f) / 12f, shared.right[0] - shared.left[0], 0.01f);

        // Twenty would be narrower than their glyphs: those that do not fit are left off the end.
        float[] crowd = new float[20];
        java.util.Arrays.fill(crowd, GLYPH);
        HomeFloatChipsPolicy.Layout full = HomeFloatChipsPolicy.layout(D, 600, crowd,
            new float[20]);
        assertTrue(full.shown < 20 && full.shown >= 1);
        assertTrue("never wider than the row's room", full.rowWidth <= 600 - 32 + 0.5f);
        for (int i = 0; i < full.shown; i++)
            assertTrue("never narrower than the glyph",
                full.right[i] - full.left[i] >= GLYPH - 0.01f);
    }

    @Test
    public void aTapLandsOnAChipAndNotOnTheGapsBetween() {
        HomeFloatChipsPolicy.Layout layout = HomeFloatChipsPolicy.layout(D, 800,
            new float[]{GLYPH, GLYPH, GLYPH}, new float[]{40f, 40f, 40f});
        assertEquals(0, HomeFloatChipsPolicy.chipAt(layout, layout.left[0] + 1f));
        assertEquals(2, HomeFloatChipsPolicy.chipAt(layout, layout.right[2] - 1f));
        assertEquals(-1, HomeFloatChipsPolicy.chipAt(layout, layout.right[0] + 1f));
        assertEquals(-1, HomeFloatChipsPolicy.chipAt(layout, layout.rowWidth + 1f));
    }

    @Test
    public void noChipsNoRow() {
        assertEquals(0, HomeFloatChipsPolicy.layout(D, 800, new float[0], new float[0]).shown);
        assertEquals(0f, HomeFloatChipsPolicy.layout(D, 800, new float[0], new float[0]).rowWidth,
            0f);
        assertEquals("an unmeasured layer", 0,
            HomeFloatChipsPolicy.layout(D, 0, new float[]{GLYPH}, new float[]{10f}).shown);
    }
}
