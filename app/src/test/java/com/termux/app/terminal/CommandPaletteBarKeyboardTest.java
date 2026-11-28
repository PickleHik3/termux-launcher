package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import com.termux.app.terminal.CommandPaletteBarKeyboard.Raise;

import org.junit.Test;

/**
 * Review P2: the control bar's search raises a keyboard only when none is up, so it never puts
 * away one that was already there (the system keyboard with the launcher's off, too).
 */
public class CommandPaletteBarKeyboardTest {

    @Test
    public void aSystemKeyboardAlreadyUpIsNotRaisedSoItIsNotHidden() {
        assertEquals(Raise.NONE, CommandPaletteBarKeyboard.raise(false, false, false, false, true));
        assertEquals(Raise.SYSTEM, CommandPaletteBarKeyboard.raise(false, false, false, false, false));
    }

    @Test
    public void theLaunchersKeyboardIsRaisedOnlyWhenDown() {
        assertEquals(Raise.IN_APP, CommandPaletteBarKeyboard.raise(true, false, false, false, false));
        assertEquals(Raise.NONE, CommandPaletteBarKeyboard.raise(true, true, false, false, false));
        assertEquals("a hardware keyboard types", Raise.NONE,
            CommandPaletteBarKeyboard.raise(true, false, true, true, false));
    }

    @Test
    public void thePhonesOwnKeysNeedNoKeyboard() {
        assertEquals(Raise.NONE, CommandPaletteBarKeyboard.raise(false, false, false, true, false));
    }
}
