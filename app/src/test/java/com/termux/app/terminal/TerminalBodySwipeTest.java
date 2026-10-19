package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The one rule for a sideways swipe on a terminal's text, a Workspace window's or a Home
 * float's: who keeps the drag, and how sideways a start has to be to page.
 */
public class TerminalBodySwipeTest {

    @Test
    public void aTerminalAtRestGivesTheSwipeAway() {
        assertTrue(TerminalBodySwipe.yieldsSideways(false, false, false, false, false));
    }

    @Test
    public void theTerminalKeepsTheSwipeWhenItHasAUseForIt() {
        assertFalse("a float's titlebar or resize corner",
            TerminalBodySwipe.yieldsSideways(true, false, false, false, false));
        assertFalse("a selection under way is extended",
            TerminalBodySwipe.yieldsSideways(false, true, false, false, false));
        assertFalse("a program tracking the mouse gets the drag",
            TerminalBodySwipe.yieldsSideways(false, false, true, false, false));
        assertFalse("a program asking for touch reports gets the drag",
            TerminalBodySwipe.yieldsSideways(false, false, false, true, false));
        assertFalse("mouse mode drives the pointer",
            TerminalBodySwipe.yieldsSideways(false, false, false, false, true));
    }

    @Test
    public void onlyAClearlySidewaysStartPages() {
        assertTrue("straight across", TerminalBodySwipe.isSideways(24f, 0f));
        assertTrue("leftward too", TerminalBodySwipe.isSideways(-24f, 4f));
        assertTrue("exactly at the ratio", TerminalBodySwipe.isSideways(15f, -10f));
        assertFalse("just steeper than the ratio", TerminalBodySwipe.isSideways(14.9f, 10f));
        assertFalse("a diagonal", TerminalBodySwipe.isSideways(20f, 20f));
        assertFalse("a vertical scroll that drifted", TerminalBodySwipe.isSideways(6f, 24f));
        assertFalse("straight down", TerminalBodySwipe.isSideways(0f, 24f));
        assertFalse("no travel", TerminalBodySwipe.isSideways(0f, 0f));
    }
}
