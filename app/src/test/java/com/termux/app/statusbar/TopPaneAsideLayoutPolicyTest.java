package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The clock keeps the slot; the place switch hugs its content at the other end, or stands down. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TopPaneAsideLayoutPolicyTest {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 68;
    private static final int GUTTER = 12;
    private static final int GAP = 12;

    @Test public void noSwitchGivesTheClockTheWholeUsableWidth() {
        TopPaneAsideLayoutPolicy.Result r = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", 0, 36, 100, 80, false);
        assertEquals(new Rect(GUTTER, 0, WIDTH - GUTTER, HEIGHT), r.clock);
        assertTrue(r.place.isEmpty());
        assertFalse(r.clockCompact);
    }

    @Test public void aLeftOrCentredClockPutsTheSwitchAtTheTrailingEnd() {
        for (String alignment : new String[]{"left", "center"}) {
            TopPaneAsideLayoutPolicy.Result r = TopPaneAsideLayoutPolicy.calculate(
                WIDTH, HEIGHT, GUTTER, GAP, alignment, 150, 36, 100, 80, false);
            assertEquals(alignment, new Rect(WIDTH - GUTTER - 150, 16, WIDTH - GUTTER, 52), r.place);
            assertEquals(alignment, new Rect(GUTTER, 0, WIDTH - GUTTER - 150 - GAP, HEIGHT), r.clock);
            assertFalse(alignment, r.clockCompact);
        }
    }

    @Test public void aRightAlignedClockPutsTheSwitchAtTheLeadingEnd() {
        TopPaneAsideLayoutPolicy.Result r = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "right", 150, 36, 100, 80, false);
        assertEquals(new Rect(GUTTER, 16, GUTTER + 150, 52), r.place);
        assertEquals(new Rect(GUTTER + 150 + GAP, 0, WIDTH - GUTTER, HEIGHT), r.clock);
    }

    @Test public void theClockGoesCompactWhenItsFullFaceNoLongerFits() {
        TopPaneAsideLayoutPolicy.Result r = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", 150, 36, 200, 80, false);
        assertTrue(r.clockCompact);
    }

    @Test public void aSwitchThatLeavesLessThanTheCompactClockStandsDown() {
        TopPaneAsideLayoutPolicy.Result r = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", 270, 36, 100, 80, false);
        assertTrue(r.place.isEmpty());
        assertEquals(new Rect(GUTTER, 0, WIDTH - GUTTER, HEIGHT), r.clock);
    }

    @Test public void rtlMirrorsBothRects() {
        TopPaneAsideLayoutPolicy.Result ltr = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", 150, 36, 100, 80, false);
        TopPaneAsideLayoutPolicy.Result rtl = TopPaneAsideLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", 150, 36, 100, 80, true);
        assertEquals(WIDTH - ltr.place.right, rtl.place.left);
        assertEquals(WIDTH - ltr.clock.right, rtl.clock.left);
    }
}
