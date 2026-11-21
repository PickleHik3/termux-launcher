package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;


import org.junit.Test;
import com.termux.app.page.PageKind;

/**
 * The terminal long-press menu's Appearance: the editor at rest on the Terminal half, never a
 * Settings page and never a card.
 */
public class AppearanceEditorEntryTest {

    @Test
    public void overATerminalTheEditorRestsWhereItIs() {
        assertEquals(PageKind.TERMINAL,
            AppearanceEditorEntry.terminalMenuPlace(PageKind.TERMINAL));
    }

    @Test
    public void overTheDisplayWindowItStaysOnTheTerminalPage() {
        assertEquals(PageKind.DISPLAY,
            AppearanceEditorEntry.terminalMenuPlace(PageKind.DISPLAY));
    }

    @Test
    public void fromHomeOrNowhereItLandsOnTheTerminal() {
        assertEquals("a Home float's terminal is still the Terminal's thing",
            PageKind.TERMINAL, AppearanceEditorEntry.terminalMenuPlace(PageKind.HOME));
        assertEquals(PageKind.TERMINAL, AppearanceEditorEntry.terminalMenuPlace(null));
    }
}
