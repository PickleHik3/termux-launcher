package com.termux.app.launcher;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

public class TerminalAppSearchKeyDecisionTest {

    @Test
    public void normalTerminalInputAlwaysPassesThrough() {
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(false, false, 4, KeyEvent.KEYCODE_ENTER));
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(false, false, 4, KeyEvent.KEYCODE_DPAD_UP));
    }

    @Test
    public void alternateBufferAlwaysPassesThrough() {
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(true, true, 4, KeyEvent.KEYCODE_ENTER));
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(true, true, 4, KeyEvent.KEYCODE_DPAD_LEFT));
    }

    @Test
    public void searchWithResultsBorrowsNavigationAndEnter() {
        assertEquals(TerminalAppSearchKeyDecision.Action.PREVIOUS,
            TerminalAppSearchKeyDecision.decide(true, false, 2, KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(TerminalAppSearchKeyDecision.Action.NEXT,
            TerminalAppSearchKeyDecision.decide(true, false, 2, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(TerminalAppSearchKeyDecision.Action.LAUNCH,
            TerminalAppSearchKeyDecision.decide(true, false, 2, KeyEvent.KEYCODE_ENTER));
    }

    @Test
    public void noMatchesDoesNotStealEnterOrArrows() {
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(true, false, 0, KeyEvent.KEYCODE_ENTER));
        assertEquals(TerminalAppSearchKeyDecision.Action.PASS,
            TerminalAppSearchKeyDecision.decide(true, false, 0, KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    @Test
    public void escapeExitsLiteralSearchEvenWithoutMatches() {
        assertEquals(TerminalAppSearchKeyDecision.Action.EXIT,
            TerminalAppSearchKeyDecision.decide(true, false, 0, KeyEvent.KEYCODE_ESCAPE));
    }
}
