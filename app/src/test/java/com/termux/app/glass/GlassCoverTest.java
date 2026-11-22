package com.termux.app.glass;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.window.WindowLayer;

import org.junit.Test;

/** Which surfaces a sheet at rest covers: beneath its layer and wholly inside it, or not shown. */
public class GlassCoverTest {

    private static final int PAGES = WindowLayer.PAGES.number();
    private static final int CHROME = WindowLayer.CHROME.number();
    private static final int SHEETS = WindowLayer.SHEETS.number();

    /** A 1080 x 2400 window; the card stands at 0, 120 .. 1080, 2200. */
    private static boolean covered(int layer, boolean shown, int l, int t, int r, int b) {
        return GlassCover.covered(layer, shown, l, t, r, b, SHEETS, 0, 120, 1080, 2200);
    }

    @Test
    public void aWidgetWhollyUnderTheCardIsCovered() {
        assertTrue(covered(PAGES, true, 40, 400, 1040, 900));
        // Its edge on the card's edge is still inside.
        assertTrue(covered(PAGES, true, 0, 120, 1080, 2200));
    }

    @Test
    public void aSurfaceReachingPastTheCardIsSeen() {
        // The dock with the keyboard below the card's bottom.
        assertFalse(covered(CHROME, true, 0, 2100, 1080, 2400));
        assertFalse(covered(PAGES, true, 0, 60, 1080, 300));
    }

    @Test
    public void theSheetsOwnLayerAndAboveAreNeverCovered() {
        assertFalse(covered(SHEETS, true, 100, 200, 300, 300));
        assertFalse(covered(WindowLayer.NOTICES.number(), true, 100, 200, 300, 300));
    }

    @Test
    public void aHiddenSurfaceBeneathIsCoveredWherever() {
        assertTrue(covered(PAGES, false, 0, 0, 0, 0));
        assertFalse(covered(SHEETS, false, 0, 0, 0, 0));
    }

    @Test
    public void anEmptySheetCoversNothing() {
        assertFalse(GlassCover.covered(PAGES, false, 0, 0, 10, 10, SHEETS, 0, 0, 0, 0));
    }
}
