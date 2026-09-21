package com.termux.terminal;

/**
 * Synchronized output, private mode 2026: while it is set the client is not told about screen
 * changes, so a program that repaints in several writes shows one finished frame. The hold is
 * bounded, because a program that dies between "begin" and "end" must not leave the pane frozen.
 */
public class SynchronizedUpdateTest extends TerminalTestCase {

    public void testModeHoldsAndReleasesTheScreen() {
        withTerminalSized(5, 3);
        assertFalse("Nothing is held before the mode is set", mTerminal.isScreenUpdateHeld());

        enterString("\033[?2026h");
        assertTrue(mTerminal.isScreenUpdateHeld());
        enterString("hello");
        assertTrue("Output during the hold does not end it", mTerminal.isScreenUpdateHeld());

        enterString("\033[?2026l");
        assertFalse(mTerminal.isScreenUpdateHeld());
    }

    /** Ending the hold hands the client the one update the whole frame is worth. */
    public void testResettingTheModeAsksForOneRedraw() {
        withTerminalSized(5, 3);
        enterString("\033[?2026h");
        int before = mOutput.screenChanges;
        enterString("abc");
        assertEquals("A held update is not delivered", before, mOutput.screenChanges);

        enterString("\033[?2026l");
        assertEquals("Exactly one update on release", before + 1, mOutput.screenChanges);
    }

    /** A program that never resets the mode gets its screen back when the timeout passes. */
    public void testHoldExpiresAfterTheTimeout() {
        withTerminalSized(5, 3);
        mTerminal.setSynchronizedUpdateTimeoutMillisForTests(0);
        enterString("\033[?2026h");
        assertFalse("A hold past its deadline does not hold", mTerminal.isScreenUpdateHeld());
        assertEquals(0, mTerminal.screenUpdateHoldRemainingMillis());
        // And the expired hold is gone for good, not re-armed by more output.
        enterString("abc");
        assertFalse(mTerminal.isScreenUpdateHeld());
    }

    /** The default hold is the named safety timeout, and the client can wake itself for it. */
    public void testHoldRemainingIsBoundedByTheTimeout() {
        withTerminalSized(5, 3);
        enterString("\033[?2026h");
        long remaining = mTerminal.screenUpdateHoldRemainingMillis();
        assertTrue("Remaining=" + remaining, remaining > 0
            && remaining <= TerminalEmulator.SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS);
    }

    /** DECRQM: 1 while held, 2 once it is not. */
    public void testDecrqmReportsTheMode() {
        withTerminalSized(5, 3);
        assertEnteringStringGivesResponse("\033[?2026$p", "\033[?2026;2$y");
        enterString("\033[?2026h");
        assertEnteringStringGivesResponse("\033[?2026$p", "\033[?2026;1$y");
        enterString("\033[?2026l");
        assertEnteringStringGivesResponse("\033[?2026$p", "\033[?2026;2$y");
    }

    /** An expired hold reports as reset, so a program is never told the terminal is still holding. */
    public void testDecrqmReportsResetAfterTheTimeout() {
        withTerminalSized(5, 3);
        mTerminal.setSynchronizedUpdateTimeoutMillisForTests(0);
        enterString("\033[?2026h");
        assertEnteringStringGivesResponse("\033[?2026$p", "\033[?2026;2$y");
    }

    /** Bell and title are client callbacks of their own and are not part of the held frame. */
    public void testBellAndTitleAreNotHeld() {
        withTerminalSized(5, 3);
        enterString("\033[?2026h");
        enterString("\007");
        enterString("\033]0;held\007");
        assertEquals(1, mOutput.bellsRung);
        assertEquals("held", mTerminal.getTitle());
    }

    /** A terminal reset drops the hold along with the rest of the mode state. */
    public void testResetClearsTheHold() {
        withTerminalSized(5, 3);
        enterString("\033[?2026h");
        assertTrue(mTerminal.isScreenUpdateHeld());
        mTerminal.reset();
        assertFalse(mTerminal.isScreenUpdateHeld());
    }
}
