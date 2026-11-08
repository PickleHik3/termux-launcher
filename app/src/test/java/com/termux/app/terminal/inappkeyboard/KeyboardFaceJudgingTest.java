package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyboardFaceJudgingTest {
    @Test public void atRestTheFacesAreJudged() {
        assertTrue(KeyboardFaceJudging.due(false, false, false, false));
        // A finished crossing's mark never holds a rest apply back.
        assertTrue(KeyboardFaceJudging.due(false, false, true, false));
    }

    /**
     * Home2 to the Workspace on pong: the keyboard rose in Home's put-away palette (function chips
     * at alpha 0x60, judged on no faces) and took the Workspace's (0xe1) only at landing. The
     * crossing judges the rising keyboard once, then keeps those inks while the band stays put.
     */
    @Test public void aCrossingJudgesTheKeyboardOnce() {
        assertTrue(KeyboardFaceJudging.due(false, true, false, false));
        assertFalse(KeyboardFaceJudging.due(false, true, true, false));
    }

    /**
     * Home2 to the Workspace on pong (RISEINK-DBG): the crossing's first frame measured the band
     * on the layout of the page it left, below the screen ([21,2360][1059,3003], rows off a
     * stand-in, a veil bought), and the function chips rose at alpha 0xCA; the landing measured
     * [21,1690][1059,2333] and raised them to 0xE1, a darkening at landing. A layout that moves
     * the band mid-crossing is judged again, so the keyboard rises in its landed rect's inks.
     */
    @Test public void aCrossingJudgesAgainWhenALayoutMovesTheBand() {
        assertTrue(KeyboardFaceJudging.due(false, true, true, true));
    }

    @Test public void aHeldEditorThumbWaits() {
        assertFalse(KeyboardFaceJudging.due(true, false, false, false));
        assertFalse(KeyboardFaceJudging.due(true, true, false, false));
        assertFalse(KeyboardFaceJudging.due(true, true, true, true));
        assertFalse(KeyboardFaceJudging.rejudgeDue(true));
    }

    /** The band's own frame landing after a stand-in read re-judges it, mid-crossing too. */
    @Test public void aLandedFrameRejudgesAStandInReadingInACrossing() {
        assertTrue(KeyboardFaceJudging.rejudgeDue(false));
    }

    @Test public void aCrossingKeepsTheInksUntilTheBandIsMeasured() {
        assertTrue(KeyboardFaceJudging.clearsWithoutBand(false));
        assertFalse(KeyboardFaceJudging.clearsWithoutBand(true));
    }
}
