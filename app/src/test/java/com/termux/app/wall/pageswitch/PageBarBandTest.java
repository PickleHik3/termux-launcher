package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageBarBandTest {

    private static final float EPS = 0.01f;

    @Test public void bandIsTheTouchRowLessTheGapUnderTheSlabWithoutDockRows() {
        // Was 64 / 52 dp (4 air + 48 pill + 12 air): the drawn slab shrank to 24.5 dp and only
        // the 48 dp touch row stays.
        assertEquals(48f, PageBarBand.bandHeightDp(true, true), EPS);
        assertEquals(44f, PageBarBand.bandHeightDp(true, false), EPS);
        assertEquals(96, PageBarBand.bandHeightPx(true, true, 0, 2f));
        assertEquals(88, PageBarBand.bandHeightPx(true, false, 0, 2f));
        assertEquals(PageBarLayout.bandHeightDp(), PageBarBand.bandHeightDp(true, true), EPS);
    }

    @Test public void atPongsDensityTheBandGivesTheGridFortyTwoPixels() {
        assertEquals(126, PageBarBand.bandHeightPx(true, true, 0, 2.625f));
        assertEquals(168 - 126, Math.round(64f * 2.625f) - PageBarBand.bandHeightPx(true, true, 0, 2.625f));
    }

    @Test public void indicatorOffGivesTheWholeBandBack() {
        assertEquals(0, PageBarBand.bandHeightPx(false, true, 20, 3f));
        assertEquals(0, PageBarBand.bandHeightPx(false, false, 20, 3f));
    }

    @Test public void withNoDockRowsTheSlabStandsTheDocksEdgeGapAboveThePanesBottom() {
        // The slab's bottom is the edge gap above the band's bottom, which is the pane's bottom
        // with no reserve: the line the dock's bottom would stand on.
        int edgeGap = 16;
        int top = Math.round((PageBarLayout.PILL_HEIGHT_DP - PageBarLayout.GAP_DP) * 2f);
        assertEquals(top + edgeGap, PageBarBand.bandHeightPx(true, false, edgeGap, 2f));
        PageBarLayout l = new PageBarLayout(2f, 800f, 4);
        assertEquals(top, l.slabBottomPx(), EPS);
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

    @Test public void overDockRowsTheSlabStandsTheGapAboveTheBandsBottom() {
        PageBarLayout l = new PageBarLayout(2f, 800f, 4);
        assertEquals(PageBarBand.bandHeightPx(true, true, 0, 2f) - PageBarLayout.GAP_DP * 2f,
            l.slabBottomPx(), EPS);
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
        // The pane-centred constructors keep the old centring, the key's bar included; Home's
        // slots are MIN_SLOT_DP wide.
        float three = 3f * PageBarLayout.MIN_SLOT_DP;
        assertEquals((1000f - three) / 2f, new PageBarLayout(1f, 1000f, 3).pillLeftPx(), EPS);
        assertEquals((1000f - three) / 2f,
            new PageBarLayout(1f, 1000f, 3, 100f, 1200f).pillLeftPx(), EPS);
        assertEquals(16f, new PageBarLayout(1f, 360f, 3, true).pillLeftPx(), EPS);
    }

    @Test public void theBandFollowsTheIndicatorUnderBothLooks() {
        assertTrue(PageBarBand.bandOn(true));
        assertFalse(PageBarBand.bandOn(false));
    }

    @Test public void everyCombinationOfIndicatorDockRowsAndEdgeGapFeedsTheSameDecision() {
        for (boolean indicator : new boolean[] {false, true}) {
            for (int reserve : new int[] {0, 90, 180}) {      // none, Docked, Floating-ish
                for (int edgeGap : new int[] {0, 16, 40}) {   // flush, Floating gap
                    boolean on = PageBarBand.bandOn(indicator);
                    int band = PageBarBand.bandHeightPx(on, PageBarBand.dockRowsBelow(reserve),
                        edgeGap, 2f);
                    String what = indicator + " " + reserve + " " + edgeGap;
                    if (!indicator) {
                        assertEquals(what, 0, band);
                        assertFalse(what, PageBarBand.shown(on, false));
                    } else {
                        assertTrue(what, band > 0);
                        assertTrue(what, PageBarBand.shown(on, false));
                    }
                }
            }
        }
    }
}
