package com.termux.terminal;

import junit.framework.TestCase;

/** The rule that picks Standard or Ghostty for a new session. */
public class TerminalEngineTest extends TestCase {

    public void testStandardWhenNothingAsksForGhostty() {
        assertFalse(TerminalEngine.useGhostty(false, false, true, false));
    }

    public void testEitherTheSettingOrThePropertyIsEnough() {
        assertTrue(TerminalEngine.useGhostty(true, false, true, false));
        assertTrue(TerminalEngine.useGhostty(false, true, true, false));
        assertTrue(TerminalEngine.useGhostty(true, true, true, false));
    }

    public void testStandardWhenTheLibraryIsMissing() {
        assertFalse(TerminalEngine.useGhostty(true, false, false, false));
        assertFalse(TerminalEngine.useGhostty(false, true, false, false));
    }

    public void testStandardWhenTheCrashGuardBlocksIt() {
        assertFalse(TerminalEngine.useGhostty(true, true, true, true));
    }

    public void testOnlyTheStoredGhosttyValueAsksForGhostty() {
        assertTrue(TerminalEngine.isGhosttyPreference("ghostty"));
        assertFalse(TerminalEngine.isGhosttyPreference("standard"));
        assertFalse(TerminalEngine.isGhosttyPreference(null));
        assertFalse(TerminalEngine.isGhosttyPreference("Ghostty "));
    }
}
