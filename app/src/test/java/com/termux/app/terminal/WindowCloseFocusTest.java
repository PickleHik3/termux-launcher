package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where a session's selection goes when one of its windows closes. */
public class WindowCloseFocusTest {

    @Test
    public void selectedWindowClosingHandsOverToTheRightNeighbour() {
        // [a, B, c] -> [a, c], c selected.
        assertEquals(1, WindowCloseFocus.afterRemoval(1, 1, 2));
    }

    @Test
    public void selectedLastWindowClosingHandsOverToTheLeftNeighbour() {
        // [a, b, C] -> [a, b], b selected.
        assertEquals(1, WindowCloseFocus.afterRemoval(2, 2, 2));
    }

    @Test
    public void aWindowBeforeTheSelectionClosingKeepsTheSameWindowSelected() {
        // [a, b, C] with a's command exiting behind the strip -> [b, C], still C.
        assertEquals(1, WindowCloseFocus.afterRemoval(2, 0, 2));
    }

    @Test
    public void aWindowAfterTheSelectionClosingChangesNothing() {
        // [A, b, c] with c closing -> [A, b].
        assertEquals(0, WindowCloseFocus.afterRemoval(0, 2, 2));
    }

    @Test
    public void theOnlyWindowClosingEndsTheSession() {
        assertEquals(-1, WindowCloseFocus.afterRemoval(0, 0, 0));
    }

    @Test
    public void aStaleSelectionIsClampedIntoTheStrip() {
        assertEquals(1, WindowCloseFocus.afterRemoval(5, 5, 2));
        assertEquals(0, WindowCloseFocus.afterRemoval(-1, 0, 1));
    }
}
