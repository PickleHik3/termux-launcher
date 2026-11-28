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
    public void homeArrivesWithoutTheKeyboardUnlessItKeepsItOn() {
        assertTrue("Home starts to come in", TerlKeyboardRest.downOnHomeArrival(1f, 0.9f, true, false));
        assertTrue("a jump straight to Home", TerlKeyboardRest.downOnHomeArrival(1f, 0f, true, false));
        assertFalse("Home keeps it on", TerlKeyboardRest.downOnHomeArrival(1f, 0f, true, true));
        assertFalse("already down", TerlKeyboardRest.downOnHomeArrival(1f, 0f, false, false));
        assertFalse("mid-slide steps", TerlKeyboardRest.downOnHomeArrival(0.6f, 0.5f, true, false));
        assertFalse("Home leaving", TerlKeyboardRest.downOnHomeArrival(0f, 0.3f, true, false));
    }
}
