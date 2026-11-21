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
        assertEquals(128, PageBarBand.bandHeightPx(true, true, 0, 2f));
        assertEquals(104, PageBarBand.bandHeightPx(true, false, 0, 2f));
        assertEquals(PageBarLayout.bandHeightDp(), PageBarBand.bandHeightDp(true, true), EPS);
    }

    @Test public void indicatorOffGivesTheWholeBandBack() {
        assertEquals(0, PageBarBand.bandHeightPx(false, true, 20, 3f));
        assertEquals(0, PageBarBand.bandHeightPx(false, false, 20, 3f));
    }

    @Test public void withNoDockRowsThePillStandsTheDocksEdgeGapAboveThePanesBottom() {
        // The pill's bottom is the air under it above the band's bottom, which is the pane's
        // bottom with no reserve: the line the dock's bottom would stand on.
        int edgeGap = 16;
        int top = Math.round((PageBarLayout.TOP_AIR_DP + PageBarLayout.PILL_HEIGHT_DP) * 2f);
        assertEquals(top + edgeGap, PageBarBand.bandHeightPx(true, false, edgeGap, 2f));
        assertEquals("a flush dock keeps no gap, so neither does the pill", top,
            PageBarBand.bandHeightPx(true, false, 0, 2f));
        assertEquals(top, PageBarBand.bandHeightPx(true, false, -4, 2f));
    }

    @Test public void overDockRowsTheEdgeGapIsTheDocksAndTheAirUnderThePillIsItsOwn() {
        assertEquals(PageBarBand.bandHeightPx(true, true, 0, 2f),
            PageBarBand.bandHeightPx(true, true, 16, 2f));
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

    @Test public void pillCrossingTowardHomeShowsAsHomesRestDoes() {
        // Parity: the pill a crossing toward Home slides in with (Home not in front yet, the
        // Terminal's keyboard up or down) is the one Home's rest shows after the landing, with
        // Home's keyboard on demand (lands down) and always on (lands up).
        for (boolean terminalKeyboardUp : new boolean[] {false, true}) {
            for (boolean homeAlwaysOn : new boolean[] {false, true}) {
                boolean homeRestWants = homeAlwaysOn;
                boolean crossing = PageBarBand.shown(true,
                    PageBarBand.homeKeyboardUp(false, terminalKeyboardUp, homeRestWants));
                boolean rest = PageBarBand.shown(true,
                    PageBarBand.homeKeyboardUp(true, homeAlwaysOn, homeRestWants));
                assertEquals("terminal " + terminalKeyboardUp + " alwaysOn " + homeAlwaysOn,
                    rest, crossing);
                assertEquals(!homeAlwaysOn, crossing);
            }
        }
    }

    @Test public void pillAtHomeHearsEitherKeyboardUpNow() {
        assertFalse(PageBarBand.shown(true, PageBarBand.homeKeyboardUp(true, true, false)));
        assertTrue(PageBarBand.shown(true, PageBarBand.homeKeyboardUp(true, false, false)));
        // A keyboard raised on demand on Home and put away shows the pill again at once.
        assertTrue(PageBarBand.shown(true, PageBarBand.homeKeyboardUp(true, false, true)));
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
