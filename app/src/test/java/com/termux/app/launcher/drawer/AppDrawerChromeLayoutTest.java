package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerChromeLayoutTest {

    private static AppDrawerChromeLayout.Result resolve(float density, int width, boolean shown,
                                                        int switchHeight) {
        return resolve(density, width, shown, switchHeight, false, 0);
    }

    private static AppDrawerChromeLayout.Result resolve(float density, int width, boolean shown,
                                                        int switchHeight, boolean bottom,
                                                        int inset) {
        return AppDrawerChromeLayout.resolve(new AppDrawerChromeLayout.Input(density, width, shown,
            switchHeight, bottom, inset));
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

    @Test public void narrowPhoneBarIsFullWidthMinusMargins() {
        AppDrawerChromeLayout.Result r = resolve(3f, 360 * 3, false, 0);
        assertEquals((360 - 32) * 3, r.searchWidthPx);
    }

    @Test public void wideScreenBarStopsAtMaxWidth() {
        AppDrawerChromeLayout.Result r = resolve(2f, 600 * 2, false, 0);
        assertEquals(480 * 2, r.searchWidthPx);
        // Centred: the same room on both sides.
        assertEquals(0, (1200 - r.searchWidthPx) % 2);
        assertEquals(1200 - 2 * 32, r.switchMaxWidthPx);
    }

    @Test public void topPlacementIgnoresTheInsetForTheBarAndKeepsToday() {
        for (float d : new float[] {1f, 2.625f, 3.5f}) {
            for (boolean shown : new boolean[] {false, true}) {
                AppDrawerChromeLayout.Result base = resolve(d, 1080, shown, 100);
                AppDrawerChromeLayout.Result up = resolve(d, 1080, shown, 100, false, 300);
                assertEquals(base.searchTopPx, up.searchTopPx);
                assertEquals(base.contentTopPx, up.contentTopPx);
                assertEquals(0, up.contentBottomPx);
                assertEquals(0, up.searchBottomMarginPx);
                assertEquals(300, up.innerBottomInsetPx);
            }
        }
    }

    @Test public void bottomBarSitsAboveTheInsetAndTheContentEndsAboveIt() {
        float d = 2.625f;
        int gap = Math.round(10f * d);
        int height = Math.round(46f * d);
        for (int inset : new int[] {0, 48, 400}) {
            AppDrawerChromeLayout.Result r = resolve(d, 1080, false, 0, true, inset);
            assertEquals(inset, r.searchBottomMarginPx);
            assertEquals(0, r.searchTopPx);
            assertEquals(inset + height + gap, r.contentBottomPx);
            assertEquals(0, r.innerBottomInsetPx);
            assertEquals(Math.round(12f * d), r.contentTopPx);
        }
    }

    @Test public void bottomWithTheSwitchKeepsTheSwitchOnTop() {
        float d = 2f;
        int switchHeight = Math.round(40f * d);
        AppDrawerChromeLayout.Result r = resolve(d, 1080, true, switchHeight, true, 100);
        assertEquals(Math.round(12f * d), r.switchTopPx);
        assertEquals(r.switchTopPx + switchHeight + Math.round(8f * d), r.contentTopPx);
        assertEquals(100, r.searchBottomMarginPx);
        AppDrawerChromeLayout.Result topBar = resolve(d, 1080, true, switchHeight);
        assertEquals(topBar.switchTopPx, r.switchTopPx);
    }

    @Test public void bottomBarWidthRuleIsTheSameAsOnTop() {
        assertEquals(resolve(2f, 1200, false, 0).searchWidthPx,
            resolve(2f, 1200, false, 0, true, 50).searchWidthPx);
    }
}
