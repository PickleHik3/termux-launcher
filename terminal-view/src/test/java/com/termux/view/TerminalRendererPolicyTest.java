package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
