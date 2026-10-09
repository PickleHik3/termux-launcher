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
}
