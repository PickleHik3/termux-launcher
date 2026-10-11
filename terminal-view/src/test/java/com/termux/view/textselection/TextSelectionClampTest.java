package com.termux.view.textselection;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * A long press in the pane's clearance under the last row, or beside the last column, maps to a
 * cell just off the screen; the selection must start on the nearest real cell, not throw.
 */
public class TextSelectionClampTest {

    @Test
    public void aPressBelowTheLastRowStartsOnTheLastRow() {
        int[] cell = {5, 41};
        TextSelectionCursorController.clampToScreen(cell, 80, 40, 0);
        assertArrayEquals(new int[] {5, 39}, cell);
    }

    @Test
    public void aPressPastTheLastColumnStartsOnTheLastColumn() {
        int[] cell = {80, 10};
        TextSelectionCursorController.clampToScreen(cell, 80, 40, 0);
        assertArrayEquals(new int[] {79, 10}, cell);
    }

    @Test
    public void aPressAboveTheTranscriptStartsOnItsFirstRow() {
        int[] cell = {-1, -120};
        TextSelectionCursorController.clampToScreen(cell, 80, 40, 100);
        assertArrayEquals(new int[] {0, -100}, cell);
    }

    @Test
    public void aCellOnTheScreenIsLeftAlone() {
        int[] cell = {12, -3};
        TextSelectionCursorController.clampToScreen(cell, 80, 40, 100);
        assertArrayEquals(new int[] {12, -3}, cell);
    }
}
