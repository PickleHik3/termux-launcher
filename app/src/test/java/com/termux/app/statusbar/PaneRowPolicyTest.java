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

    @Test
    public void theTerminalGetsTheStripsHeightBackAsRowsLessTheRow() {
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        PaneRowPolicy.Room strip = PaneRowPolicy.room(false, true, STRIP_PX, row);
        PaneRowPolicy.Room pane = PaneRowPolicy.room(true, true, STRIP_PX, row);
        assertEquals(STRIP_PX, strip.totalPx());
        assertEquals(row, pane.totalPx());
        assertEquals(STRIP_PX - row, strip.totalPx() - pane.totalPx());
        // A pane under the top one pays nothing for the row, in either place.
        assertEquals(0, PaneRowPolicy.room(true, false, STRIP_PX, row).paneTopInsetPx);
    }

    @Test
    public void theRowsAreComputedFromOnePolicyOnBothPaths() {
        // The prediction reads Room.totalPx; the settled layout reads the strip band and the
        // pane's inset through the same two functions. Both give the same rows at any height.
        int row = PaneRowPolicy.rowHeightPx(DENSITY);
        int cell = 41;
        for (boolean inPane : new boolean[] {false, true}) {
            PaneRowPolicy.Room room = PaneRowPolicy.room(inPane, true, STRIP_PX, row);
            for (int height = 1200; height <= 2400; height += 37) {
                int predicted = (height - room.totalPx()) / cell;
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
}
