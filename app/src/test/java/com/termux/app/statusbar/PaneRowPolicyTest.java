package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.terminal.PaneShape;

import org.junit.Test;

/**
 * Status bar place: the thin row inside the top pane. One pure policy answers what the terminal
 * gives up at its top, so the prediction (a keyboard reveal, a crossing's end) and the settled
 * layout cannot disagree about the rows.
 */
public class PaneRowPolicyTest {

    private static final float DENSITY = 2.75f;
    private static final int STRIP_PX =
        StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, true, DENSITY);

    @Test
    public void theRowIsAsThinAsItCanBeAtRest() {
        assertEquals(16f, PaneRowPolicy.REST_HEIGHT_DP, 0f);
        assertEquals(12f, PaneRowPolicy.MARK_DP, 0f);
        assertEquals(9f, PaneRowPolicy.READING_SP, 0f);
        assertEquals(44, PaneRowPolicy.rowHeightPx(DENSITY));
    }

    @Test
    public void theRowStandsOnEveryPageButHome() {
        assertTrue(PaneRowPolicy.rowStands(StatusPlace.PANE, PageKind.TERMINAL, true));
        assertTrue(PaneRowPolicy.rowStands(StatusPlace.PANE, PageKind.DISPLAY, true));
        assertFalse(PaneRowPolicy.rowStands(StatusPlace.PANE, PageKind.HOME, true));
    }

    @Test
    public void aStripOrAHiddenBarNeverStandsAsARow() {
        assertFalse(PaneRowPolicy.rowStands(StatusPlace.STRIP, PageKind.TERMINAL, true));
        assertFalse(PaneRowPolicy.rowStands(StatusPlace.PANE, PageKind.TERMINAL, false));
    }

    @Test
    public void theStripKeepsItsBandUnlessTheRowTookItsPlace() {
        assertEquals(STRIP_PX, PaneRowPolicy.stripBandPx(false, STRIP_PX));
        assertEquals(0, PaneRowPolicy.stripBandPx(true, STRIP_PX));
    }

    @Test
    public void onlyTheTopPaneHoldsItsContentBelowTheRow() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        assertEquals(row, PaneRowPolicy.paneTopInsetPx(true, true, row));
        assertEquals(0, PaneRowPolicy.paneTopInsetPx(true, false, row));
        assertEquals(0, PaneRowPolicy.paneTopInsetPx(false, true, row));
    }

    private static int totalPx(boolean rowStands, boolean topPane, int stripPx, int rowPx) {
        return PaneRowPolicy.stripBandPx(rowStands, stripPx)
            + PaneRowPolicy.paneTopInsetPx(rowStands, topPane, rowPx);
    }

    @Test
    public void theTerminalGetsTheStripsHeightBackAsRowsLessTheRow() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        int strip = totalPx(false, true, STRIP_PX, row);
        int pane = totalPx(true, true, STRIP_PX, row);
        assertEquals(STRIP_PX, strip);
        assertEquals(row, pane);
        assertEquals(STRIP_PX - row, strip - pane);
        // A pane under the top one pays nothing for the row, in either place.
        assertEquals(0, PaneRowPolicy.paneTopInsetPx(true, false, row));
    }

    @Test
    public void theRowsAreComputedFromOnePolicyOnBothPaths() {
        // The terminal's top gives up the strip band and the pane's inset, the two functions
        // production reads (PageTopInsets, the pane controller); rows follow at any height.
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        int cell = 41;
        for (boolean inPane : new boolean[] {false, true}) {
            int total = totalPx(inPane, true, STRIP_PX, row);
            for (int height = 1200; height <= 2400; height += 37) {
                int predicted = (height - total) / cell;
                int settled = (height - PaneRowPolicy.stripBandPx(inPane, STRIP_PX)
                    - PaneRowPolicy.paneTopInsetPx(inPane, true, row)) / cell;
                assertEquals(predicted, settled);
            }
        }
    }

    @Test
    public void theRowNeverOverlapsTheFirstRowOfText() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        int bottomInset = 30;
        int headroom = 12;
        int without = PaneShape.topInsetPx(bottomInset, headroom, 0);
        int with = PaneShape.topInsetPx(bottomInset, headroom, row);
        assertEquals(PaneShape.topInsetPx(bottomInset, headroom), without);
        // The margin grows by the whole row, so the first cell starts a row lower.
        assertEquals(without + row, with);
        // Headroom is only the clearance the pane already had: it never pays for the row.
        assertEquals(row, PaneShape.topInsetPx(bottomInset, 999, row));
    }

    @Test
    public void theRowKeepsTheMockUpsSideInsetsOnASquareOrGentlyRoundedPane() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        int start = Math.round(PaneRowPolicy.START_INSET_DP * DENSITY);
        int end = Math.round(PaneRowPolicy.END_INSET_DP * DENSITY);
        assertEquals(start, PaneRowPolicy.startInsetPx(0f, row, DENSITY));
        assertEquals(end, PaneRowPolicy.endInsetPx(0f, row, DENSITY));
        // A 24dp corner bends in about 6dp at the row's middle: the 10dp start already clears it.
        assertEquals(start, PaneRowPolicy.startInsetPx(24f * DENSITY, row, DENSITY));
    }

    @Test
    public void aRoundCornerPushesTheRowInBeyondItsArc() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        float radius = 40f * DENSITY;
        // At the row's middle (8dp down) a 40dp arc stands 40 - sqrt(40² - 32²) = 16dp in.
        int arc = Math.round(16f * DENSITY);
        assertEquals(arc, PaneRowPolicy.startInsetPx(radius, row, DENSITY));
        assertEquals(arc, PaneRowPolicy.endInsetPx(radius, row, DENSITY));
        assertEquals(arc, PaneRowPolicy.arcInsetPx(radius, row / 2f));
        assertEquals(0, PaneRowPolicy.arcInsetPx(radius, radius + 1f));
    }

    // ---- the enlarged row (#334) -----------------------------------------------------------

    @Test
    public void theEnlargedRowIs32dpWithFullSizeMarks() {
        assertEquals(32f, PaneRowPolicy.ENLARGED_HEIGHT_DP, 0f);
        assertEquals(18f, PaneRowPolicy.ENLARGED_MARK_DP, 0f);
        assertEquals(88, PaneRowPolicy.enlargedHeightPx(DENSITY));
    }

    @Test
    public void enlargingNeverChangesWhatTheTerminalGivesUp() {
        int rest = PaneRowPolicy.rowHeightPx(DENSITY);
        int before = totalPx(true, true, STRIP_PX, rest);
        assertEquals(100 + rest, PaneRowPolicy.rowBottomPx(100, DENSITY, false));
        assertEquals(100 + PaneRowPolicy.enlargedHeightPx(DENSITY),
            PaneRowPolicy.rowBottomPx(100, DENSITY, true));
        assertEquals(before, totalPx(true, true, STRIP_PX, rest));
        assertEquals(rest, PaneRowPolicy.paneTopInsetPx(true, true, rest));
    }

    @Test
    public void atRestTheRowReachesAboveItAndNeverIntoTheFirstTextRow() {
        int top = 100;
        int bottom = PaneRowPolicy.rowBottomPx(top, DENSITY, false);
        int above = Math.round(PaneRowPolicy.TOUCH_ABOVE_DP * DENSITY);
        assertTrue(PaneRowPolicy.hitsRow(50f, top - above, 0, top, 400, DENSITY, false));
        assertFalse(PaneRowPolicy.hitsRow(50f, top - above - 1, 0, top, 400, DENSITY, false));
        assertTrue(PaneRowPolicy.hitsRow(50f, bottom - 1, 0, top, 400, DENSITY, false));
        // The terminal's first row starts where the pane's inset ends: the row's bottom.
        assertFalse(PaneRowPolicy.hitsRow(50f, bottom, 0, top, 400, DENSITY, false));
        assertFalse(PaneRowPolicy.hitsRow(-1f, top, 0, top, 400, DENSITY, false));
        assertFalse(PaneRowPolicy.hitsRow(400f, top, 0, top, 400, DENSITY, false));
    }

    @Test
    public void enlargedTheRowAlsoReachesALittleBelowItself() {
        int top = 100;
        int bottom = PaneRowPolicy.rowBottomPx(top, DENSITY, true);
        int below = Math.round(PaneRowPolicy.TOUCH_BELOW_ENLARGED_DP * DENSITY);
        assertEquals(6f, PaneRowPolicy.TOUCH_BELOW_ENLARGED_DP, 0f);
        assertTrue(PaneRowPolicy.hitsRow(50f, bottom + below - 1, 0, top, 400, DENSITY, true));
        assertFalse(PaneRowPolicy.hitsRow(50f, bottom + below, 0, top, 400, DENSITY, true));
    }
}
