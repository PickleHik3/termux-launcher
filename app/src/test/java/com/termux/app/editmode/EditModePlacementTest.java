package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EditModePlacementTest {

    @Test
    public void theCardStandsAboveTheKeyboard() {
        // A 2000px overlay, the keyboard's top at 1400: the card clears 600px plus the gap.
        assertEquals(616, EditModePlacement.cardBottomMarginPx(2000, 1400, 120, 16));
    }

    @Test
    public void withTheSurfaceOffScreenTheCardStandsAboveTheStrip() {
        assertEquals(136, EditModePlacement.cardBottomMarginPx(2000,
            EditModePlacement.OFF_SCREEN, 120, 16));
    }

    @Test
    public void aSurfaceLowerThanTheStripTopStillLeavesTheStripClear() {
        assertEquals(136, EditModePlacement.cardBottomMarginPx(2000, 1950, 120, 16));
    }

    @Test
    public void aSurfaceBelowTheOverlayCountsAsOffScreen() {
        assertEquals(136, EditModePlacement.cardBottomMarginPx(2000, 2100, 120, 16));
    }
}
