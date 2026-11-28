package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FrameGlassPolicyTest {

    @Test
    public void standDownOnlyWhenAllThreeHold() {
        assertTrue(FrameGlassPolicy.sheetsStandDown(true, true, true));
    }

    @Test
    public void opaqueModeKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(false, true, true));
    }

    @Test
    public void glassOffKeepsEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, false, true));
    }

    @Test
    public void floatingOrEdgeCardsKeepEverySheet() {
        assertFalse(FrameGlassPolicy.sheetsStandDown(true, true, false));
    }

    /** Round 5: the wallpaper's dim (the frame's glass) went whenever the keyboard showed under Terl. */
    @Test
    public void underTerlTheFrameStandsWholeWhateverTheKeyboardDoes() {
        // The keyboard up splits v1's shape into edge cards: no joined frame card.
        assertTrue(FrameGlassPolicy.frameCard(true, true, false));
        assertTrue(FrameGlassPolicy.frameCard(true, true, true));
    }

    @Test
    public void underTerlFloatingHasNoFrameToKeep() {
        assertFalse(FrameGlassPolicy.frameCard(true, false, false));
    }

    @Test
    public void withoutTerlTheShapeModelDecides() {
        assertTrue(FrameGlassPolicy.frameCard(false, true, true));
        assertFalse(FrameGlassPolicy.frameCard(false, true, false));
    }
}
