package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
