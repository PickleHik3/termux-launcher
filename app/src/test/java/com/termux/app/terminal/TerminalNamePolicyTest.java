package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TerminalNamePolicyTest {

    @Test
    public void normalize_trimsAndCapsNamesAtTheCodePointLimit() {
        assertNull(TerminalNamePolicy.normalizeSession(null));
        assertNull(TerminalNamePolicy.normalizeSession("   "));
        assertEquals("work", TerminalNamePolicy.normalizeSession(" work "));
        assertEquals("abcdefgh", TerminalNamePolicy.normalizeSession("abcdefghi"));
        assertEquals("abcdefgh", TerminalNamePolicy.normalizeSession("abcdefgh"));
    }

    @Test
    public void normalize_neverSplitsASurrogatePairAtTheBoundary() {
        // Seven ASCII code points then an emoji: the eighth code point is a surrogate pair, so a
        // char-based cap would truncate mid-pair and leave a lone high surrogate.
        assertEquals("1234567🚀",
            TerminalNamePolicy.normalizeSession("1234567🚀abc"));
        // The pair sitting one past the cap is dropped whole.
        assertEquals("12345678", TerminalNamePolicy.normalizeSession("12345678🚀"));
    }

    @Test
    public void scratchpadConstant_fitsTheCapUnchanged() {
        // The scratchpad shell's name is shown as a session label, so lowering the cap or
        // lengthening the constant would silently start truncating it.
        assertTrue(TerminalPaneController.SCRATCHPAD_SESSION_NAME.codePointCount(
            0, TerminalPaneController.SCRATCHPAD_SESSION_NAME.length())
            <= TerminalNamePolicy.SESSION_MAX_CODE_POINTS);
        assertEquals(TerminalPaneController.SCRATCHPAD_SESSION_NAME,
            TerminalNamePolicy.normalizeSession(TerminalPaneController.SCRATCHPAD_SESSION_NAME));
    }
}
