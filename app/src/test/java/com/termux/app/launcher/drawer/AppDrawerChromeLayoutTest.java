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

    @Test public void aPhoneBarIsACentredCapsuleNotEdgeToEdge() {
        // 360dp phone: 328dp between the margins, 78% of it.
        AppDrawerChromeLayout.Result r = resolve(3f, 360 * 3, false, 0);
        assertEquals(Math.round(328 * 3 * 0.78f), r.searchWidthPx);
        assertTrue(r.searchWidthPx < (360 - 32) * 3);
        assertEquals(0, (360 * 3 - r.searchWidthPx) % 2);
    }

    @Test public void aNarrowPaneKeepsTheMinimumWhileTheMarginsAllow() {
        // 250dp between the margins: 78% would be 195dp, the floor is 220dp.
        assertEquals(220 * 2, resolve(2f, (250 + 32) * 2, false, 0).searchWidthPx);
        // Narrower than the floor: the bar takes what the margins leave.
        assertEquals(200 * 2, resolve(2f, (200 + 32) * 2, false, 0).searchWidthPx);
    }

    @Test public void theBottomBarHasTheSameWidthAsTheTopOne() {
        for (int w : new int[] {280, 360, 411, 600, 900}) {
            assertEquals(resolve(2.75f, Math.round(w * 2.75f), true, 110).searchWidthPx,
                resolve(2.75f, Math.round(w * 2.75f), true, 110, true, 300).searchWidthPx);
        }
    }

    @Test public void wideScreenBarStopsAtMaxWidth() {
        AppDrawerChromeLayout.Result r = resolve(2f, 600 * 2, false, 0);
        assertEquals(360 * 2, r.searchWidthPx);
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

    private static AppDrawerChromeLayout.Result fold(boolean bottom, int inset, boolean atLeft,
                                                     boolean collapsed) {
        return AppDrawerChromeLayout.resolve(new AppDrawerChromeLayout.Input(2f, 1080, false, 0,
            bottom, inset, 2000, atLeft, collapsed));
    }

    @Test public void theButtonKeepsTheBarsMarginsAndItsHeightAsDiameter() {
        AppDrawerChromeLayout.Result left = fold(false, 40, true, false);
        AppDrawerChromeLayout.Result right = fold(false, 40, false, false);
        int size = Math.round(46f * 2f);
        int side = Math.round(16f * 2f);
        assertEquals(size, left.buttonSizePx);
        assertEquals(side, left.buttonLeftPx);
        assertEquals(1080 - side - size, right.buttonLeftPx);
        // Bar on top: the larger of the inset and the side margin above the card's bottom.
        assertEquals(2000 - Math.max(40, side) - size, left.buttonTopPx);
        assertEquals(2000 - side - size, fold(false, 0, true, false).buttonTopPx);
        // Bar at the bottom: the bar's own margin.
        assertEquals(2000 - 40 - size, fold(true, 40, true, false).buttonTopPx);
    }

    @Test public void theBarsRestRectIsWhereItIsLaidOut() {
        AppDrawerChromeLayout.Result top = fold(false, 40, true, false);
        assertEquals((1080 - top.searchWidthPx) / 2, top.barLeftPx);
        assertEquals(top.searchTopPx, top.barTopPx);
        AppDrawerChromeLayout.Result bottom = fold(true, 40, true, false);
        assertEquals(2000 - 40 - bottom.searchHeightPx, bottom.barTopPx);
    }

    @Test public void morphEndsAreTheFullBarAndTheCircleInBothCorners() {
        for (boolean atLeft : new boolean[] {true, false}) {
            for (boolean bottom : new boolean[] {true, false}) {
                AppDrawerChromeLayout.Result r = fold(bottom, 40, atLeft, false);
                AppDrawerChromeLayout.Morph m = new AppDrawerChromeLayout.Morph();
                AppDrawerChromeLayout.morph(r, 0f, m);
                assertEquals(0f, m.translationX, 0f);
                assertEquals(0f, m.translationY, 0f);
                assertEquals(0f, m.drawnLeft, 0f);
                assertEquals(r.searchWidthPx, m.drawnRight, 0f);
                AppDrawerChromeLayout.morph(r, 1f, m);
                float left = r.barLeftPx + m.translationX + m.drawnLeft;
                float right = r.barLeftPx + m.translationX + m.drawnRight;
                float top = r.barTopPx + m.translationY;
                assertEquals(r.buttonLeftPx, left, 0.001f);
                assertEquals(r.buttonLeftPx + r.buttonSizePx, right, 0.001f);
                assertEquals(r.buttonTopPx, top, 0.001f);
                assertEquals(r.buttonSizePx, right - left, 0.001f);
            }
        }
    }

    @Test public void morphIsMonotoneAndClamped() {
        AppDrawerChromeLayout.Result r = fold(false, 40, true, false);
        AppDrawerChromeLayout.Morph a = new AppDrawerChromeLayout.Morph();
        AppDrawerChromeLayout.Morph b = new AppDrawerChromeLayout.Morph();
        AppDrawerChromeLayout.morph(r, 0.3f, a);
        AppDrawerChromeLayout.morph(r, 0.6f, b);
        assertTrue(b.drawnRight < a.drawnRight);
        assertTrue(b.translationY > a.translationY);
        AppDrawerChromeLayout.morph(r, 2f, a);
        AppDrawerChromeLayout.morph(r, 1f, b);
        assertEquals(b.drawnRight, a.drawnRight, 0f);
    }

    @Test public void aFoldedTopBarLeavesTheListRoomForTheButton() {
        AppDrawerChromeLayout.Result open = fold(false, 40, true, false);
        AppDrawerChromeLayout.Result folded = fold(false, 40, true, true);
        assertEquals(40, open.innerBottomInsetPx);
        assertTrue(folded.innerBottomInsetPx
            >= Math.round(16f * 2f) + folded.buttonSizePx + Math.round(10f * 2f));
        // The top band and the content's top never move with the fold.
        assertEquals(open.contentTopPx, folded.contentTopPx);
        // A bar at the bottom has its band already; nothing changes.
        assertEquals(fold(true, 40, true, false).innerBottomInsetPx,
            fold(true, 40, true, true).innerBottomInsetPx);
        assertEquals(fold(true, 40, true, false).contentBottomPx,
            fold(true, 40, true, true).contentBottomPx);
    }
}
