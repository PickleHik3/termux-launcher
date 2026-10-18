package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.terminal.ProcessGlyphs;

import org.junit.Test;

public class HomePillsPolicyTest {

    private static final float D = 2f;
    private static final float GLYPH = 30f;

    @Test
    public void pillsStandOnHomeAlongTheTopOrBottomOnly() {
        assertTrue(HomePillsPolicy.shown(true, Edge.TOP));
        assertTrue(HomePillsPolicy.shown(true, Edge.BOTTOM));
        assertFalse(HomePillsPolicy.shown(true, Edge.LEFT));
        assertFalse(HomePillsPolicy.shown(true, Edge.RIGHT));
        assertFalse(HomePillsPolicy.shown(false, Edge.TOP));
    }

    @Test
    public void aSectionSplitsTheChipsGlyphFromItsName() {
        HomePillsPolicy.Section nvim = HomePillsPolicy.Section.of(" neovim", "neovim");
        assertEquals("", nvim.glyph);
        assertEquals("neovim", nvim.name);
        HomePillsPolicy.Section bare = HomePillsPolicy.Section.of("home", "home");
        assertEquals(ProcessGlyphs.DEFAULT, bare.glyph);
        assertEquals("home", bare.name);
        assertEquals("", HomePillsPolicy.Section.of("", "x").name);
    }

    @Test
    public void onlyTheFocusedSectionCarriesItsName() {
        HomePillsPolicy.Layout layout = layout(800, 3, 1, 100f, 120f);
        float iconOnly = 2 * HomePillsPolicy.SECTION_PADDING_DP * D + GLYPH;
        assertEquals(iconOnly, width(layout, 0), 0.01f);
        assertEquals(iconOnly + HomePillsPolicy.NAME_GAP_DP * D + 100f, width(layout, 1), 0.01f);
        assertEquals(iconOnly, width(layout, 2), 0.01f);
        assertEquals(100f, layout.nameRoom, 0.01f);
        assertEquals(1, layout.focused);
    }

    @Test
    public void theWindowsPillStartsAtTheInsetAndTheStatsEndAtTheOther() {
        HomePillsPolicy.Layout layout = layout(800, 2, 0, 50f, 120f);
        assertEquals(24f, layout.windowsLeft, 0.01f);
        assertTrue(layout.statsShown);
        assertEquals(800 - 24f, layout.statsRight, 0.01f);
        assertEquals(800 - 24f - 120f, layout.statsLeft, 0.01f);
        assertTrue(layout.windowsRight + HomePillsPolicy.PILL_GAP_DP * D <= layout.statsLeft);
    }

    @Test
    public void theNameGivesWayBeforeTheStatsAndGoesBelowItsMinimum() {
        // Room: 400 - 48 = 352. Icons 3 * 70 = 210, stats 100, gap 16: 26 left, less the name gap
        // (12) is 14, under the 48 px minimum: the name goes, the stats stay.
        HomePillsPolicy.Layout layout = layout(400, 3, 0, 200f, 100f);
        assertTrue(layout.statsShown);
        assertEquals(0f, layout.nameRoom, 0.01f);
        assertTrue(layout.windowsRight + HomePillsPolicy.PILL_GAP_DP * D <= layout.statsLeft + 0.01f);
    }

    @Test
    public void theStatsStepAsideWhenTheGlyphsAloneLeaveNoRoom() {
        HomePillsPolicy.Layout layout = layout(400, 4, 0, 0f, 100f);
        assertFalse(layout.statsShown);
        assertTrue(layout.windowsShown);
    }

    @Test
    public void tooManyFloatsClipAtTheBandsEnd() {
        HomePillsPolicy.Layout layout = layout(400, 10, -1, 0f, 0f);
        assertEquals(400 - 24f, layout.windowsRight, 0.01f);
        assertEquals(-1, HomePillsPolicy.sectionAt(layout, 390f, 20f));
    }

    @Test
    public void withoutFloatsOnlyTheStatsShow() {
        HomePillsPolicy.Layout layout = layout(400, 0, -1, 0f, 100f);
        assertFalse(layout.windowsShown);
        assertTrue(layout.statsShown);
        HomePillsPolicy.Layout none = layout(400, 0, -1, 0f, 0f);
        assertFalse(none.windowsShown);
        assertFalse(none.statsShown);
    }

    @Test
    public void thePillIsAsTallAsTheBandAllowsAndRound() {
        HomePillsPolicy.Layout tall = HomePillsPolicy.layout(D, 400, 100, 0, 0,
            new float[] {GLYPH}, 0, 0f, 0f);
        assertEquals(64, tall.pillBottom - tall.pillTop);
        assertEquals(32f, tall.radius, 0.01f);
        HomePillsPolicy.Layout thin = HomePillsPolicy.layout(D, 400, 60, 0, 0,
            new float[] {GLYPH}, 0, 0f, 0f);
        assertEquals(52, thin.pillBottom - thin.pillTop);
        assertEquals(4, thin.pillTop);
    }

    @Test
    public void tapsFindTheirSectionAndMissOffThePills() {
        HomePillsPolicy.Layout layout = layout(800, 3, 1, 100f, 120f);
        float y = (layout.pillTop + layout.pillBottom) / 2f;
        assertEquals(0, HomePillsPolicy.sectionAt(layout, layout.sectionLeft[0] + 1f, y));
        assertEquals(1, HomePillsPolicy.sectionAt(layout, layout.sectionRight[1] - 1f, y));
        assertEquals(2, HomePillsPolicy.sectionAt(layout, layout.sectionLeft[2] + 1f, y));
        assertEquals(-1, HomePillsPolicy.sectionAt(layout, layout.windowsRight + 1f, y));
        assertEquals(-1, HomePillsPolicy.sectionAt(layout, layout.sectionLeft[0] + 1f,
            layout.pillBottom + 1f));
        assertFalse(HomePillsPolicy.onPill(layout, layout.windowsRight + 4f, y));
        assertTrue(HomePillsPolicy.onPill(layout, layout.statsLeft + 1f, y));
    }

    @Test
    public void aFocusMoveStepsBetweenTheTwoLayoutsAndLandsOnTheNewOne() {
        HomePillsPolicy.Layout from = layout(800, 3, 0, 100f, 0f);
        HomePillsPolicy.Layout to = layout(800, 3, 2, 60f, 0f);
        HomePillsPolicy.Layout half = HomePillsPolicy.between(from, to, 0.5f);
        assertEquals((width(from, 0) + width(to, 0)) / 2f, width(half, 0), 0.01f);
        assertEquals((from.windowsRight + to.windowsRight) / 2f, half.windowsRight, 0.01f);
        assertSame(to, HomePillsPolicy.between(from, to, 1f));
        HomePillsPolicy.Layout other = layout(800, 4, 2, 60f, 0f);
        assertSame(other, HomePillsPolicy.between(from, other, 0.5f));
    }

    @Test
    public void theStatsPillIsItsReadingsWithTheirGapsAndPadding() {
        float width = HomePillsPolicy.statsWidth(D, new float[] {20f, 20f}, new float[] {40f, 30f});
        float expected = 2 * HomePillsPolicy.STATS_PADDING_DP * D + HomePillsPolicy.STATS_ITEM_GAP_DP * D
            + 2 * HomePillsPolicy.STATS_GLYPH_GAP_DP * D + 20f + 40f + 20f + 30f;
        assertEquals(expected, width, 0.01f);
        assertEquals(0f, HomePillsPolicy.statsWidth(D, new float[0], new float[0]), 0f);
    }

    private static HomePillsPolicy.Layout layout(int width, int sections, int focused, float name,
                                                 float stats) {
        float[] glyphs = new float[sections];
        java.util.Arrays.fill(glyphs, GLYPH);
        return HomePillsPolicy.layout(D, width, 64, 24, 24, glyphs, focused, name, stats);
    }

    private static float width(HomePillsPolicy.Layout layout, int section) {
        return layout.sectionRight[section] - layout.sectionLeft[section];
    }
}
