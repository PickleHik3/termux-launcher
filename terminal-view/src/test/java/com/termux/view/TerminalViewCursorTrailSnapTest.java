package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The cursor trail snaps over a cursor the pane's geometry moved (a held grid riding a divider
 * drag, the hold starting or ending) and trails a cursor the program moved (ADR 0019 follow-up).
 */
public class TerminalViewCursorTrailSnapTest {

    @Test
    public void aCursorTheProgramMovedAtRestTrails() {
        assertFalse(TerminalView.cursorTrailSnaps(false, false, true));
    }

    @Test
    public void aRedrawAtRestWithNothingMovedDoesNotSnap() {
        assertFalse(TerminalView.cursorTrailSnaps(false, false, false));
    }

    @Test
    public void aHeldPaneMovedByTheDragSnaps() {
        // The divider moved the pane (and the top-anchored grid with it); the cursor cell did not.
        assertTrue(TerminalView.cursorTrailSnaps(true, false, false));
    }

    @Test
    public void aCursorTheProgramMovedWhileHeldStillTrails() {
        assertFalse(TerminalView.cursorTrailSnaps(true, false, true));
    }

    @Test
    public void theHoldStartingOrEndingSnapsWhateverTheProgramDid() {
        // Held → at rest: the grid goes back to centred (a pause-resize or the release).
        assertTrue(TerminalView.cursorTrailSnaps(false, true, false));
        assertTrue(TerminalView.cursorTrailSnaps(false, true, true));
        // At rest → held, or re-anchored after a mid-drag pause.
        assertTrue(TerminalView.cursorTrailSnaps(true, true, true));
    }
}
