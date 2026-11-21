package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageBarBandTest {

    private static final float EPS = 0.01f;

    @Test public void bandIsTopAirAndPillWithBottomAirOnlyOverDockRows() {
        assertEquals(64f, PageBarBand.bandHeightDp(true, true), EPS);
        assertEquals(52f, PageBarBand.bandHeightDp(true, false), EPS);
        assertEquals(128, PageBarBand.bandHeightPx(true, true, 2f));
        assertEquals(104, PageBarBand.bandHeightPx(true, false, 2f));
        assertEquals(PageBarLayout.bandHeightDp(), PageBarBand.bandHeightDp(true, true), EPS);
    }

    @Test public void indicatorOffGivesTheWholeBandBack() {
        assertEquals(0, PageBarBand.bandHeightPx(false, true, 3f));
        assertEquals(0, PageBarBand.bandHeightPx(false, false, 3f));
    }

    @Test public void dockRowsAreBelowWhileThereIsAReserve() {
        assertTrue(PageBarBand.dockRowsBelow(1));
        assertFalse(PageBarBand.dockRowsBelow(0));
        assertFalse(PageBarBand.dockRowsBelow(-5));
        assertEquals(90, PageBarBand.bottomMarginPx(90));
        assertEquals(0, PageBarBand.bottomMarginPx(-5));
    }

    @Test public void pillTopIsTheTopAir() {
        assertEquals(8f, PageBarBand.pillTopPx(2f), EPS);
        assertEquals(new PageBarLayout(2f, 800f, 4).pillTopPx(), PageBarBand.pillTopPx(2f), EPS);
    }

    @Test public void pillShowsOnlyWithTheIndicatorOnAndNoKeyboard() {
        assertTrue(PageBarBand.shown(true, false));
        assertFalse(PageBarBand.shown(true, true));
        assertFalse(PageBarBand.shown(false, false));
        assertFalse(PageBarBand.shown(false, true));
    }

    @Test public void pillIsPaneCentredWhenThePaneIsCentredInTheWindow() {
        assertEquals(130f, PageBarBand.pillLeftPx(0f, 1080f, 1080f, 820f, 1f), EPS);
        assertEquals(130f, PageBarBand.pillLeftPx(100f, 880f, 1080f, 620f, 1f), EPS);
    }

    @Test public void pillCentresOnTheWindowWhenThePaneIsOffset() {
        // A rail 80 wide on the left: the pane is 80..1080 of a 1080 window; the window's centre
        // is 540, so 460 in the pane.
        assertEquals(460f - 100f, PageBarBand.pillLeftPx(80f, 1000f, 1080f, 200f, 1f), EPS);
    }

    @Test public void pillIsClampedInsideThePaneWithTheSideInset() {
        // The pane is the right 300 of a 1080 window; the window's centre is outside it.
        assertEquals(16f, PageBarBand.pillLeftPx(780f, 300f, 1080f, 200f, 1f), EPS);
        // The pane is the left 300: the centre is past its right end.
        assertEquals(300f - 16f - 200f, PageBarBand.pillLeftPx(0f, 300f, 1080f, 200f, 1f), EPS);
        assertEquals(2f * 16f, PageBarBand.pillLeftPx(780f, 300f, 1080f, 200f, 2f), EPS);
    }

    @Test public void pillFallsBackToThePaneCentreWithoutAWindowOrRoom() {
        assertEquals(100f, PageBarBand.pillLeftPx(80f, 400f, 0f, 200f, 1f), EPS);
        assertEquals(10f, PageBarBand.pillLeftPx(80f, 220f, 1080f, 200f, 1f), EPS);
    }

    @Test public void layoutTakesThePolicysCentre() {
        PageBarLayout offset = new PageBarLayout(1f, 1000f, 3, 80f, 1080f);
        assertEquals(PageBarBand.pillLeftPx(80f, 1000f, 1080f, offset.pillWidthPx(), 1f),
            offset.pillLeftPx(), EPS);
        assertEquals(540f - 80f, offset.pillLeftPx() + offset.pillWidthPx() / 2f, EPS);
        // The pane-centred constructors keep the old centring, the key's bar included.
        assertEquals((1000f - 159f) / 2f, new PageBarLayout(1f, 1000f, 3).pillLeftPx(), EPS);
        assertEquals((1000f - 159f) / 2f,
            new PageBarLayout(1f, 1000f, 3, 100f, 1200f).pillLeftPx(), EPS);
        assertEquals(16f, new PageBarLayout(1f, 360f, 3, true).pillLeftPx(), EPS);
    }
}
