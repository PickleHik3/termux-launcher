package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The display window's place in the Workspace's row (ADR 0008, amended 2026-10-19): a window of
 * the row like the terminal windows, kept beside their list.
 */
public class DisplaySlotTest {

    private static List<String> windows(String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }

    /** The row as the bar shows it, the display window as "D". */
    private static String row(List<String> windows, DisplaySlot<String> display) {
        List<String> row = new ArrayList<>(windows);
        int slot = display.slot(windows);
        if (slot >= 0) row.add(slot, "D");
        return String.join(" ", row);
    }

    @Test
    public void theDisplayJoinsTheEndOfTheRow() {
        List<String> windows = windows("a", "b");
        DisplaySlot<String> display = new DisplaySlot<>();
        assertEquals(DisplaySlot.NONE, display.slot(windows));
        display.joinAtEnd(windows);
        assertEquals("a b D", row(windows, display));
        assertEquals(3, DisplaySlot.rowCount(windows.size(), display.slot(windows)));
    }

    @Test
    public void aRowOfTheDisplayAloneHoldsOnlyIt() {
        List<String> windows = windows();
        DisplaySlot<String> display = new DisplaySlot<>();
        display.joinAtEnd(windows);
        assertEquals(0, display.slot(windows));
        assertEquals(1, DisplaySlot.rowCount(0, 0));
        assertEquals("in front whatever was asked", 0, DisplaySlot.rowCurrent(false, 0, 0, 0));
    }

    @Test
    public void theDisplayKeepsItsNeighbourWhateverHappensElsewhere() {
        List<String> windows = windows("a", "b", "c");
        DisplaySlot<String> display = new DisplaySlot<>();
        display.joinAtEnd(windows);
        display.place(windows, 1);
        assertEquals("a D b c", row(windows, display));
        windows.add(0, "z");
        assertEquals("a window opened before it moves it along", "z a D b c", row(windows, display));
        windows.remove("c");
        assertEquals("one closed after it leaves it be", "z a D b", row(windows, display));
        windows.remove("z");
        assertEquals("one closed before it moves it back", "a D b", row(windows, display));
    }

    @Test
    public void theDisplayFallsBackOnePlaceWhenItsNeighbourLeaves() {
        List<String> windows = windows("a", "b", "c");
        DisplaySlot<String> display = new DisplaySlot<>();
        display.joinAtEnd(windows);
        display.place(windows, 2);
        assertEquals("a b D c", row(windows, display));
        windows.remove("b");
        assertEquals("the place b had", "a D c", row(windows, display));
        windows.remove("a");
        assertEquals("first", "D c", row(windows, display));
        windows.remove("c");
        assertEquals("alone", "D", row(windows, display));
    }

    @Test
    public void rowPlacesAndWindowIndicesConvert() {
        // a D b c
        assertTrue(DisplaySlot.isDisplay(1, 1));
        assertEquals(0, DisplaySlot.windowAt(0, 1));
        assertEquals(DisplaySlot.NONE, DisplaySlot.windowAt(1, 1));
        assertEquals(1, DisplaySlot.windowAt(2, 1));
        assertEquals(2, DisplaySlot.windowAt(3, 1));
        assertEquals(0, DisplaySlot.rowOf(0, 1));
        assertEquals(2, DisplaySlot.rowOf(1, 1));
        assertEquals("no display: the same numbers", 2, DisplaySlot.rowOf(2, DisplaySlot.NONE));
        assertEquals(2, DisplaySlot.windowAt(2, DisplaySlot.NONE));
        assertFalse(DisplaySlot.isDisplay(0, DisplaySlot.NONE));
    }

    @Test
    public void theRowsWindowInFront() {
        assertEquals("the display's place", 1, DisplaySlot.rowCurrent(true, 2, 3, 1));
        assertEquals("a window after the display", 3, DisplaySlot.rowCurrent(false, 2, 3, 1));
        assertEquals("a window before it", 0, DisplaySlot.rowCurrent(false, 0, 3, 1));
        assertEquals("no display", 2, DisplaySlot.rowCurrent(true, 2, 3, DisplaySlot.NONE));
    }

    @Test
    public void aNewWindowGoesRightAfterTheWindowInFrontTheDisplayIncluded() {
        // a D b, a in front: the new window goes between a and the display.
        int row = DisplaySlot.insertionRow(0, 3);
        assertEquals(1, row);
        assertEquals(1, DisplaySlot.windowIndexForInsert(row, 1));
        assertEquals(2, DisplaySlot.slotAfterInsert(row, 1));
        // a D b, the display in front: the new window goes right after it.
        row = DisplaySlot.insertionRow(1, 3);
        assertEquals(2, row);
        assertEquals(1, DisplaySlot.windowIndexForInsert(row, 1));
        assertEquals(1, DisplaySlot.slotAfterInsert(row, 1));
    }

    @Test
    public void draggingTheDisplayMovesOnlyIt() {
        DisplaySlot.Move move = DisplaySlot.move(3, 3, 3, 0);
        assertTrue(move.accepted);
        assertEquals(DisplaySlot.NONE, move.windowFrom);
        assertEquals(0, move.slot);
    }

    @Test
    public void draggingAWindowPastTheDisplay() {
        // a D b: a to the end -> D b a.
        DisplaySlot.Move move = DisplaySlot.move(2, 1, 0, 2);
        assertEquals(0, move.windowFrom);
        assertEquals(1, move.windowTo);
        assertEquals(0, move.slot);
        assertEquals("D b a", apply(windows("a", "b"), 1, move));
        // a b D: b to the front -> b a D.
        assertEquals("b a D", apply(windows("a", "b"), 2, DisplaySlot.move(2, 2, 1, 0)));
        // D a b: b before a -> D b a.
        assertEquals("D b a", apply(windows("a", "b"), 0, DisplaySlot.move(2, 0, 2, 1)));
        // a b D c: c to the front -> c a b D.
        assertEquals("c a b D", apply(windows("a", "b", "c"), 2, DisplaySlot.move(3, 2, 3, 0)));
    }

    @Test
    public void aMoveOutsideTheRowIsRefused() {
        assertFalse(DisplaySlot.move(2, 1, 3, 0).accepted);
        assertFalse(DisplaySlot.move(2, 1, 0, -1).accepted);
        assertTrue("staying put is taken", DisplaySlot.move(2, 1, 1, 1).accepted);
    }

    @Test
    public void theDisplayLeavingHandsTheRowToItsLeftNeighbour() {
        assertEquals(0, DisplaySlot.windowAfterDisplayLeaves(1, 2));
        assertEquals("first: the window after it", 0, DisplaySlot.windowAfterDisplayLeaves(0, 2));
        assertEquals(1, DisplaySlot.windowAfterDisplayLeaves(2, 2));
        assertEquals("nothing left", DisplaySlot.NONE, DisplaySlot.windowAfterDisplayLeaves(0, 0));
    }

    @Test
    public void aWindowClosingInFrontHandsTheFrontToItsRightNeighbourTheDisplayIncluded() {
        // a b D: b closes (row 1); a D remain and the display moved up into its place.
        assertTrue(DisplaySlot.isDisplay(DisplaySlot.rowAfterRemoval(1, 2), 1));
        // a D b: b, the tail, closes: its left neighbour, the display.
        assertTrue(DisplaySlot.isDisplay(DisplaySlot.rowAfterRemoval(2, 2), 1));
        // D a b: a closes: b moves up.
        assertFalse(DisplaySlot.isDisplay(DisplaySlot.rowAfterRemoval(1, 2), 0));
        assertEquals("nothing left", DisplaySlot.NONE, DisplaySlot.rowAfterRemoval(0, 0));
    }

    @Test
    public void leavingForgetsThePlaceAndTheFront() {
        List<String> windows = windows("a");
        DisplaySlot<String> display = new DisplaySlot<>();
        display.joinAtEnd(windows);
        display.setFront(true);
        assertTrue(display.isFront());
        display.leave();
        assertFalse(display.isOnRow());
        assertFalse(display.isFront());
        display.setFront(true);
        assertFalse("off the row nothing is in front", display.isFront());
    }

    /** The row after {@code move} is applied to {@code windows} with the display at {@code slot}. */
    private static String apply(List<String> windows, int slot, DisplaySlot.Move move) {
        if (move.windowFrom >= 0) windows.add(move.windowTo, windows.remove(move.windowFrom));
        DisplaySlot<String> display = new DisplaySlot<>();
        display.joinAtEnd(windows);
        display.place(windows, move.slot);
        return row(windows, display);
    }
}
