package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeAppDropPolicyTest {

    private static WidgetCellRect cell(int column, int row) {
        return new WidgetCellRect(column, row, column + 1, row + 1);
    }

    // ---- the hand-over

    /** Home there and idle: a drawer pickup becomes a drag onto Home. */
    @Test
    public void anIdleUnlockedHomeTakesThePickup() {
        assertTrue(HomeAppDropPolicy.handsOverToHome(true, false, false));
    }

    /** No Home, a locked layout or a drag already in flight: the drawer keeps its own drag. */
    @Test
    public void theDrawerKeepsThePickupWhenHomeCannotTakeIt() {
        assertFalse(HomeAppDropPolicy.handsOverToHome(false, false, false));
        assertFalse(HomeAppDropPolicy.handsOverToHome(true, true, false));
        assertFalse(HomeAppDropPolicy.handsOverToHome(true, false, true));
    }

    // ---- the release

    @Test
    public void aReleaseOverAFreeCellPutsTheIconThere() {
        assertEquals(HomeAppDropPolicy.Outcome.PLACE,
            HomeAppDropPolicy.resolve(false, false, true, true));
    }

    @Test
    public void aReleaseOverAFullPageIsRefused() {
        assertEquals(HomeAppDropPolicy.Outcome.NO_ROOM,
            HomeAppDropPolicy.resolve(false, false, true, false));
    }

    /** Over the dock, off the grid, or cancelled: nothing is put down, whatever the page offered. */
    @Test
    public void aReleaseAnywhereElsePutsNothingDown() {
        assertEquals(HomeAppDropPolicy.Outcome.CANCEL,
            HomeAppDropPolicy.resolve(false, true, true, true));
        assertEquals(HomeAppDropPolicy.Outcome.CANCEL,
            HomeAppDropPolicy.resolve(false, false, false, true));
        assertEquals(HomeAppDropPolicy.Outcome.CANCEL,
            HomeAppDropPolicy.resolve(true, false, true, true));
    }

    // ---- the cell

    @Test
    public void onAnEmptyPageTheIconLandsInTheCellUnderTheFinger() {
        assertEquals(cell(2, 3), HomeAppDropPolicy.nearestFreeCell(4, 5,
            Collections.<WidgetCellRect>emptyList(), 2.4f, 3.9f));
    }

    /** A taken cell under the finger sends the icon to the nearest free one. */
    @Test
    public void aTakenCellSendsTheIconToTheNearestFreeOne() {
        List<WidgetCellRect> occupied = Arrays.asList(cell(1, 1));
        // Finger in the right half of the taken cell: the free cell to its right is nearest.
        assertEquals(cell(2, 1), HomeAppDropPolicy.nearestFreeCell(4, 5, occupied, 1.8f, 1.5f));
        // In its lower half: the cell below.
        assertEquals(cell(1, 2), HomeAppDropPolicy.nearestFreeCell(4, 5, occupied, 1.5f, 1.85f));
    }

    /** A widget spanning several cells blocks all of them. */
    @Test
    public void aWideWidgetBlocksEveryCellItSpans() {
        List<WidgetCellRect> occupied = Arrays.asList(new WidgetCellRect(0, 0, 4, 2));
        assertEquals(cell(1, 2), HomeAppDropPolicy.nearestFreeCell(4, 5, occupied, 1.5f, 0.5f));
    }

    /** Equal distances go to the upper row, then the leading column, so the target never flickers. */
    @Test
    public void tiesGoUpThenToTheLeadingSide() {
        List<WidgetCellRect> occupied = Arrays.asList(cell(1, 1));
        assertEquals(cell(1, 0), HomeAppDropPolicy.nearestFreeCell(3, 3, occupied, 1.5f, 1.5f));
    }

    @Test
    public void aFullPageOffersNoCell() {
        List<WidgetCellRect> occupied = new ArrayList<>();
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) occupied.add(cell(c, r));
        assertNull(HomeAppDropPolicy.nearestFreeCell(2, 2, occupied, 0.5f, 0.5f));
        assertNull(HomeAppDropPolicy.nearestFreeCell(0, 5,
            Collections.<WidgetCellRect>emptyList(), 0.5f, 0.5f));
    }

    /** A finger past the grid's edge still finds the nearest cell inside it. */
    @Test
    public void aFingerPastTheEdgeFindsTheNearestCellInside() {
        assertEquals(cell(3, 0), HomeAppDropPolicy.nearestFreeCell(4, 5,
            Collections.<WidgetCellRect>emptyList(), 6f, -1f));
        assertEquals(cell(3, 0), HomeAppDropPolicy.cellUnder(4, 5, 6f, -1f));
    }

    @Test
    public void pixelsBecomeCellUnitsAroundTheFirstCellsCentre() {
        // First cell centred at 50 px, 100 px from one centre to the next.
        assertEquals(0.5f, HomeAppDropPolicy.toCellUnits(50f, 50f, 100f), 1e-6f);
        assertEquals(2.5f, HomeAppDropPolicy.toCellUnits(250f, 50f, 100f), 1e-6f);
        assertEquals(0f, HomeAppDropPolicy.toCellUnits(0f, 50f, 100f), 1e-6f);
        assertEquals(0.5f, HomeAppDropPolicy.toCellUnits(999f, 50f, 0f), 1e-6f);
    }
}
