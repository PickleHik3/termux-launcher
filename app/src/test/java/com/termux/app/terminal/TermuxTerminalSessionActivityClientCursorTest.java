package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A hidden activity ignores cursor show/hide from the emulator, so a background TUI cannot spam the log or the blinker. */
public class TermuxTerminalSessionActivityClientCursorTest {

    @Test
    public void cursorChangesApplyOnlyWhileVisible() {
        assertTrue(TermuxTerminalSessionActivityClient.shouldApplyCursorStateChange(true));
        assertFalse(TermuxTerminalSessionActivityClient.shouldApplyCursorStateChange(false));
    }
}
