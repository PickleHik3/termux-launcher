package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where the palette's room ends off Home, now that the terminal's column fills the window. */
public class CommandPaletteTerminalRoomTest {

    @Test
    public void theRoomEndsAtTheBottomStacksTop_soThePaletteStandsAboveTheKeyboard() {
        // Column 136..2337 (the window), keyboard and dock rows standing from 1550.
        assertEquals(1550f, TerminalCommandPaletteController.terminalRoomBottomPx(136f, 2337f, 1550f), 0f);
    }

    @Test
    public void withNoStackOrOneBelowTheScreen_theRoomIsTheWholeColumn() {
        assertEquals(2337f, TerminalCommandPaletteController.terminalRoomBottomPx(136f, 2337f, Float.NaN), 0f);
        assertEquals(2337f, TerminalCommandPaletteController.terminalRoomBottomPx(136f, 2337f, 2500f), 0f);
    }
}
