package com.termux.app.controlbar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the launcher's keyboard goes down under Terl (pong, 11-28). */
public class TerlKeyboardRestTest {

    @Test
    public void theDrawersScrollPutsTheKeyboardDownOnlyUnderTheBar() {
        assertTrue(TerlKeyboardRest.downOnDrawerScroll(true, true));
        assertFalse("v1 keeps it as it was", TerlKeyboardRest.downOnDrawerScroll(false, true));
        assertFalse("already down", TerlKeyboardRest.downOnDrawerScroll(true, false));
    }

    @Test
    public void homeCommittedPutsTheKeyboardDownUnlessItAsksForIt() {
        assertTrue("Home committed from a Workspace", TerlKeyboardRest.downOnHomeCommitted(false, true, true, false));
        assertFalse("Home asks for it (always on, or the page left with it up)",
            TerlKeyboardRest.downOnHomeCommitted(false, true, true, true));
        assertFalse("already down", TerlKeyboardRest.downOnHomeCommitted(false, true, false, false));
        assertFalse("Home again: nothing arrived", TerlKeyboardRest.downOnHomeCommitted(true, true, true, false));
        assertFalse("a Workspace committed keeps it", TerlKeyboardRest.downOnHomeCommitted(true, false, true, false));
    }
}
