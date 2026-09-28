package com.termux.view.textselection;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/** The selection toolbar's content rect stays inside the view it is measured in. */
public class TextSelectionToolbarRectTest {

    @Test public void aSelectionInsideTheViewIsLeftAlone() {
        assertArrayEquals(new int[] {40, 120}, TextSelectionCursorController.clampToView(40, 120, 600));
    }

    @Test public void aSelectionBelowAShortenedPaneIsPulledBackIn() {
        // The keyboard rose and the pane is 300 tall; the selected rows still sit at 420-500.
        assertArrayEquals(new int[] {300, 300}, TextSelectionCursorController.clampToView(420, 500, 300));
        assertArrayEquals(new int[] {250, 300}, TextSelectionCursorController.clampToView(250, 340, 300));
    }

    @Test public void aSelectionScrolledAboveTheViewStartsAtItsTop() {
        assertArrayEquals(new int[] {0, 30}, TextSelectionCursorController.clampToView(-60, 30, 300));
    }
}
