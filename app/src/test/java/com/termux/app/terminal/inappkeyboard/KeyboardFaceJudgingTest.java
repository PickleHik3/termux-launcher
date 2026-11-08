package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyboardFaceJudgingTest {
    @Test public void atRestTheFacesAreJudged() {
        assertTrue(KeyboardFaceJudging.due(false, false, false));
        // A finished crossing's mark never holds a rest apply back.
        assertTrue(KeyboardFaceJudging.due(false, false, true));
    }

    /**
     * Home2 to the Workspace on pong: the keyboard rose in Home's put-away palette (function chips
     * at alpha 0x60, judged on no faces) and took the Workspace's (0xe1) only at landing. The
     * crossing judges the rising keyboard once, then keeps those inks.
     */
    @Test public void aCrossingJudgesTheKeyboardOnce() {
        assertTrue(KeyboardFaceJudging.due(false, true, false));
        assertFalse(KeyboardFaceJudging.due(false, true, true));
    }

    @Test public void aHeldEditorThumbWaits() {
        assertFalse(KeyboardFaceJudging.due(true, false, false));
        assertFalse(KeyboardFaceJudging.due(true, true, false));
    }

    @Test public void aCrossingKeepsTheInksUntilTheBandIsMeasured() {
        assertTrue(KeyboardFaceJudging.clearsWithoutBand(false));
        assertFalse(KeyboardFaceJudging.clearsWithoutBand(true));
    }
}
