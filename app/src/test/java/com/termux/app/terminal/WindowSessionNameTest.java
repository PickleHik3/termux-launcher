package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WindowSessionNameTest {

    @Test
    public void normalize_trimsAndCapsNamesAtTheCodePointLimit() {
        assertNull(WindowSessionName.normalize(null));
        assertNull(WindowSessionName.normalize("   "));
        assertEquals("work", WindowSessionName.normalize(" work "));
        assertEquals("abcdefgh", WindowSessionName.normalize("abcdefghi"));
        assertEquals("abcdefgh", WindowSessionName.normalize("abcdefgh"));
    }

    @Test
    public void normalize_neverSplitsASurrogatePairAtTheBoundary() {
        // Seven ASCII code points then an emoji: the eighth code point is a surrogate pair, so a
        // char-based cap would truncate mid-pair and leave a lone high surrogate.
        assertEquals("1234567🚀",
            WindowSessionName.normalize("1234567🚀abc"));
        // The pair sitting one past the cap is dropped whole.
        assertEquals("12345678", WindowSessionName.normalize("12345678🚀"));
    }

    @Test
    public void scratchpadConstant_fitsTheCapUnchanged() {
        // The scratchpad shell's name is shown as a session label, so lowering the cap or
        // lengthening the constant would silently start truncating it.
        assertTrue(TerminalPaneController.SCRATCHPAD_SESSION_NAME.codePointCount(
            0, TerminalPaneController.SCRATCHPAD_SESSION_NAME.length())
            <= WindowSessionName.MAX_CODE_POINTS);
        assertEquals(TerminalPaneController.SCRATCHPAD_SESSION_NAME,
            WindowSessionName.normalize(TerminalPaneController.SCRATCHPAD_SESSION_NAME));
    }
}
