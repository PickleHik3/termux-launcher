package com.termux.terminal;

import junit.framework.TestCase;

/** The TERM a session exports, as XTGETTCAP TN reports it. */
public class TerminalSessionTermTest extends TestCase {

    public void testTermIsReadFromTheEnvironment() {
        assertEquals("xterm-kitty", TerminalSession.termValue(new String[] {"HOME=/h", "TERM=xterm-kitty", "TERMINFO=/x"}));
    }

    public void testNoTermEntryGivesNull() {
        assertNull(TerminalSession.termValue(new String[] {"HOME=/h", "TERMINFO=/x"}));
        assertNull(TerminalSession.termValue(null));
    }
}
