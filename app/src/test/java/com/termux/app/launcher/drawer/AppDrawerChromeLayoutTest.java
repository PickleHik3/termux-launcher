package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerChromeLayoutTest {

    private static AppDrawerChromeLayout.Result resolve(float density, int width, boolean shown,
                                                        int switchHeight) {
        return AppDrawerChromeLayout.resolve(
            new AppDrawerChromeLayout.Input(density, width, shown, switchHeight));
    }

    /** Without the switch: the numbers the drawer had before it (12dp, 46dp, 10dp, 16dp). */
    @Test public void withoutTheSwitchEveryNumberIsToday() {
        for (float d : new float[] {1f, 1.5f, 2.625f, 2.75f, 3.5f}) {
            AppDrawerChromeLayout.Result r = resolve(d, 1080, false, 120);
            int top = Math.round(12f * d);
            int height = Math.round(46f * d);
            assertEquals(top, r.searchTopPx);
            assertEquals(height, r.searchHeightPx);
            assertEquals(top + height + Math.round(10f * d), r.contentTopPx);
            assertEquals(Math.round(16f * d), r.sideMarginPx);
            assertEquals(0, r.switchTopPx);
            assertEquals(0, r.switchHeightPx);
        }
    }

    @Test public void theSwitchTakesTheTopMarginAndPushesSearchAndContentDown() {
        float d = 2.625f;
        int switchHeight = Math.round(40f * d);
        AppDrawerChromeLayout.Result off = resolve(d, 1080, false, switchHeight);
        AppDrawerChromeLayout.Result on = resolve(d, 1080, true, switchHeight);
        assertEquals(off.searchTopPx, on.switchTopPx);
        assertEquals(on.switchTopPx + switchHeight + Math.round(8f * d), on.searchTopPx);
        // The content keeps its place relative to the search bar.
        assertEquals(off.contentTopPx - off.searchTopPx, on.contentTopPx - on.searchTopPx);
        assertEquals(on.searchTopPx - off.searchTopPx, on.contentTopPx - off.contentTopPx);
        assertTrue(on.contentTopPx > off.contentTopPx);
    }

    @Test public void maxWidthIsBetweenTheSideMargins() {
        AppDrawerChromeLayout.Result r = resolve(2f, 1000, true, 80);
        assertEquals(1000 - 2 * 32, r.switchMaxWidthPx);
        assertEquals(0, resolve(2f, 0, true, 80).switchMaxWidthPx);
    }
}
