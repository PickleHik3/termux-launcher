package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.StyleFixtures;
import com.termux.terminal.TextStyle;

import org.junit.Test;

import java.util.HashMap;

public class TerminalRendererPolicyTest {

    @Test
    public void neverKeepsCaltEverywhere() {
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.NEVER, false));
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.NEVER, true));
    }

    @Test
    public void cursorDisablesCaltOnlyInCursorRun() {
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.CURSOR, false));
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.CURSOR, true));
    }

    @Test
    public void alwaysDisablesCaltEverywhere() {
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.ALWAYS, false));
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.ALWAYS, true));
    }

    @Test
    public void featureSettingsFollowTheRequestedFaceAndSymbolOverride() {
        TerminalRenderer.FontFeatures features = new TerminalRenderer.FontFeatures(
            "regular", "bold", "italic", "bold-italic", "symbols");
        assertTrue("regular".equals(features.forRun(false, false, false)));
        assertTrue("bold".equals(features.forRun(true, false, false)));
        assertTrue("italic".equals(features.forRun(false, true, false)));
        assertTrue("bold-italic".equals(features.forRun(true, true, false)));
        assertTrue("symbols".equals(features.forRun(true, true, true)));
    }

    @Test
    public void variationSettingsFollowTheRequestedFaceAndSymbolOverride() {
        TerminalRenderer.FontVariations variations = new TerminalRenderer.FontVariations(
            "regular", "bold", "italic", "bold-italic", "symbols");
        assertTrue("regular".equals(variations.forRun(false, false, false)));
        assertTrue("bold".equals(variations.forRun(true, false, false)));
        assertTrue("italic".equals(variations.forRun(false, true, false)));
        assertTrue("bold-italic".equals(variations.forRun(true, true, false)));
        assertTrue("symbols".equals(variations.forRun(false, false, true)));
    }

    @Test
    public void aSymbolMapsOwnSettingsWinOverTheSharedSymbolsTarget() {
        assertEquals("'ss01' 1", TerminalRenderer.symbolSetting("'ss01' 1", "'liga' 1"));
        assertEquals("'wght' 600", TerminalRenderer.symbolSetting("'wght' 600", null));
    }

    @Test
    public void aSymbolMapWithoutOwnSettingsFallsBackToTheSharedSymbolsTarget() {
        assertEquals("'liga' 1", TerminalRenderer.symbolSetting(null, "'liga' 1"));
        // An empty declaration carries nothing, so it inherits rather than blanking the target.
        assertEquals("'liga' 1", TerminalRenderer.symbolSetting("", "'liga' 1"));
        assertNull(TerminalRenderer.symbolSetting(null, null));
        assertNull(TerminalRenderer.symbolSetting("", null));
    }

    @Test
    public void adjacentMapsSharingOneFaceStillBreakTheRunWhenTheirSettingsDiffer() {
        String shared = "'liga' 1";
        String ownFeatures = TerminalRenderer.symbolSetting("'ss01' 1", shared);
        String inheritedFeatures = TerminalRenderer.symbolSetting(null, shared);

        assertFalse("a feature change must end the run even on one shared typeface",
            TerminalRenderer.sameSymbolSettings(ownFeatures, null, inheritedFeatures, null));
        assertFalse("an axis change must end the run even on one shared typeface",
            TerminalRenderer.sameSymbolSettings(shared, "'wght' 600", shared, "'wght' 300"));
        assertFalse(TerminalRenderer.sameSymbolSettings(shared, null, shared, "'wght' 600"));
        assertTrue("two maps resolving to the same settings stay in one run",
            TerminalRenderer.sameSymbolSettings(inheritedFeatures, "'wght' 600",
                TerminalRenderer.symbolSetting(null, shared), "'wght' 600"));
        assertTrue("text runs carry no symbol settings and must not be split",
            TerminalRenderer.sameSymbolSettings(null, null, null, null));
    }

    @Test
    public void variationCacheKeepsOneEntryPerFaceAndAxisSetAndReusesItOnRepeatedLookups() {
        HashMap<String, String> cache = new HashMap<>();
        final int base = 4711;
        cache.put(TerminalRenderer.variationKey(base, "'wght' 600"), "bold instance");
        cache.put(TerminalRenderer.variationKey(base, "'wght' 300"), "light instance");

        assertEquals("one base face with two axis sets must be two instances", 2, cache.size());
        assertEquals("bold instance",
            cache.get(TerminalRenderer.variationKey(base, "'wght' 600")));
        cache.put(TerminalRenderer.variationKey(base, "'wght' 600"), "bold instance");
        assertEquals("a repeated lookup must hit the instance already built", 2, cache.size());
        assertNotEquals("two base faces with one axis set must be two instances",
            TerminalRenderer.variationKey(base, "'wght' 600"),
            TerminalRenderer.variationKey(base + 1, "'wght' 600"));
    }

    @Test
    public void metricAdjustmentsUsePixelsAsDeltasAndPercentAsReplacementScale() {
        assertEquals(18f, TerminalRenderer.adjustMetric(16f,
            new TerminalRenderer.MetricAdjustment(2f, false)), 0f);
        assertEquals(24f, TerminalRenderer.adjustMetric(16f,
            new TerminalRenderer.MetricAdjustment(150f, true)), 0f);
        assertEquals(16f, TerminalRenderer.adjustMetric(16f, null), 0f);
    }

    private static int[] palette() {
        int[] palette = new int[TextStyle.NUM_INDEXED_COLORS];
        for (int i = 0; i < palette.length; i++) palette[i] = 0xff000000 | (i * 7);
        palette[TextStyle.COLOR_INDEX_FOREGROUND] = 0xffe0e0e0;
        palette[TextStyle.COLOR_INDEX_BACKGROUND] = 0xff101010;
        return palette;
    }

    private static boolean alike(long symbol, long blank) {
        return TerminalRenderer.blankCellPaintsAlike(symbol, blank, palette(), false, false);
    }

    @Test
    public void aColouredIconExpandsIntoAnUncolouredBlank() {
        // The case every Nerd Font icon in a prompt or fetch tool hits: the icon carries a
        // foreground colour, the separator after it does not. A space shows neither, so the two
        // cells paint the same and the icon may use both.
        long icon = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND, 0);
        long blank = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND, 0);
        assertTrue(alike(icon, blank));
        assertNotEquals("the styles themselves must still differ", icon, blank);
    }

    @Test
    public void boldAndItalicOnTheBlankDoNotBlockExpansion() {
        long icon = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND, 0);
        long blank = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_ITALIC);
        assertTrue(alike(icon, blank));
    }

    @Test
    public void aDifferentBackgroundBlocksExpansion() {
        long icon = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND, 0);
        long blank = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND, 5, 0);
        assertFalse(alike(icon, blank));
    }

    @Test
    public void decorationsDrawnAcrossABlankMustMatch() {
        long plain = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND, 0);
        long underlined = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND, TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE);
        long struck = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND, TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH);
        assertFalse("an underline would gain a cell", alike(plain, underlined));
        assertFalse("a strikethrough would gain a cell", alike(plain, struck));
        assertFalse("an underlined icon would lose its second cell's line",
            alike(underlined, plain));
        long curly = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE, TextStyle.UNDERLINE_STYLE_CURLY);
        long single = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND, TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE,
            TextStyle.UNDERLINE_STYLE_SINGLE);
        assertFalse("the underline style itself must match too", alike(curly, single));
    }

    @Test
    public void underReverseVideoTheForegroundBecomesTheBackgroundAndMustMatch() {
        long icon = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND, 0);
        long blank = StyleFixtures.style(TextStyle.COLOR_INDEX_FOREGROUND,
            TextStyle.COLOR_INDEX_BACKGROUND, 0);
        assertFalse("reversed, the differing foregrounds are what gets painted",
            TerminalRenderer.blankCellPaintsAlike(icon, blank, palette(), false, true));
        long reversedIcon = StyleFixtures.style(4, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_INVERSE);
        assertFalse("one cell inverted and the other not swaps only one of them",
            alike(reversedIcon, blank));
    }

    @Test
    public void aSymbolThatFitsItsCellStaysInOneCell() {
        // kitty only borrows when ceil(glyph_width / cell_width) > 1; a glyph already inside its
        // cell must not be re-centred over a neighbour it does not need.
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(10f, 19f, Integer.MAX_VALUE));
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(19f, 19f, Integer.MAX_VALUE));
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(19.1f, 19f, Integer.MAX_VALUE));
    }

    @Test
    public void aWideSymbolAsksForAsManyCellsAsItsAdvanceNeeds() {
        // Maple Mono's 19px cell against a Nerd Font em square of 31.7px: two cells.
        assertEquals(2, TerminalRenderer.symbolExpansionColumns(31.7f, 19f, Integer.MAX_VALUE));
        assertEquals(3, TerminalRenderer.symbolExpansionColumns(40f, 19f, Integer.MAX_VALUE));
        assertEquals(2, TerminalRenderer.symbolExpansionColumns(38f, 19f, Integer.MAX_VALUE));
    }

    @Test
    public void expansionNeverExceedsKittysCeilingOrAConfiguredCap() {
        assertEquals(TerminalRenderer.MAX_SYMBOL_EXPANSION_COLUMNS,
            TerminalRenderer.symbolExpansionColumns(1000f, 19f, Integer.MAX_VALUE));
        assertEquals(2, TerminalRenderer.symbolExpansionColumns(1000f, 19f, 2));
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(1000f, 19f, 1));
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(1000f, 19f, 0));
        assertEquals(1, TerminalRenderer.symbolExpansionColumns(31.7f, 0f, Integer.MAX_VALUE));
    }

    @Test
    public void onlySpacesAndEnSpacesAreBorrowed() {
        assertTrue(TerminalRenderer.isExpansionBlank(' '));
        assertTrue(TerminalRenderer.isExpansionBlank('\u2002'));
        assertFalse(TerminalRenderer.isExpansionBlank('\u00a0'));
        assertFalse(TerminalRenderer.isExpansionBlank('x'));
        assertFalse(TerminalRenderer.isExpansionBlank('\t'));
    }

    @Test
    public void narrowSymbolCapsAreRangedAndLastMatchWins() {
        TerminalRenderer.SymbolExpansion caps = new TerminalRenderer.SymbolExpansion(
            new int[] {0xE000, 0xE0A0},
            new int[] {0xF8FF, 0xE0A3},
            new int[] {3, 1});
        assertEquals(3, caps.maxColumnsFor(0xE100));
        assertEquals("the later rule must win the overlap", 1, caps.maxColumnsFor(0xE0A1));
        assertEquals(Integer.MAX_VALUE, caps.maxColumnsFor(0xF0032));
        assertEquals(Integer.MAX_VALUE,
            TerminalRenderer.SymbolExpansion.DEFAULT.maxColumnsFor(0xE0B0));
    }
}
