package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;

import com.termux.app.terminal.inappkeyboard.TypingClaim.Owner;

import org.junit.Test;

/** Who the launcher keyboard types into, whichever surface just opened or closed. */
public class TypingClaimTest {

    @Test public void theOrderIsTheZOrderOfTheSurfaces() {
        assertEquals(Owner.COMMAND_PALETTE, TypingClaim.owner(true, true, true, true, true));
        assertEquals(Owner.TERMINAL_SHEET, TypingClaim.owner(false, true, true, true, true));
        assertEquals(Owner.RENAME, TypingClaim.owner(false, false, true, true, true));
        assertEquals(Owner.APP_DRAWER, TypingClaim.owner(false, false, false, true, true));
        assertEquals(Owner.DISPLAY, TypingClaim.owner(false, false, false, false, true));
    }

    /** The close that used to leave the keyboard typing into the terminal behind the display. */
    @Test public void aLayerClosingOverTheDisplayWindowHandsTheKeysBackToX() {
        assertEquals(Owner.DISPLAY, TypingClaim.owner(false, false, false, false, true));
        assertEquals("a sheet closing under an open drawer, over the display",
            Owner.APP_DRAWER, TypingClaim.owner(false, false, false, true, true));
    }

    @Test public void withNothingUpAndNoDisplayWindowTheTerminalTypes() {
        assertEquals(Owner.NONE, TypingClaim.owner(false, false, false, false, false));
    }
}
