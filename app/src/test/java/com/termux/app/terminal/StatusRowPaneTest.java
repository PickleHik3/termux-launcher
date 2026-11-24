package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The thin row belongs to the pane on screen at the top-left, whichever way the window reads. */
public class StatusRowPaneTest {

    @Test
    public void stackedPanesGiveTheRowToTheTopOneInEitherDirection() {
        assertTrue(StatusRowPane.takesFirst(false, false));
        assertTrue(StatusRowPane.takesFirst(false, true));
    }

    @Test
    public void sideBySideGivesTheRowToTheLeftPane() {
        assertTrue("left to right: the first child is on the left",
            StatusRowPane.takesFirst(true, false));
        assertFalse("right to left: the first child is on the right",
            StatusRowPane.takesFirst(true, true));
    }
}
