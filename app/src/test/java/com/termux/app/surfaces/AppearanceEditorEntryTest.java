package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * The terminal long-press menu's Appearance: the editor at rest on the Workspace half, never a
 * Settings page and never a card.
 */
public class AppearanceEditorEntryTest {

    @Test
    public void overATerminalTheEditorRestsWhereItIs() {
        assertEquals(PaneWallPage.TERMINAL,
            AppearanceEditorEntry.terminalMenuPlace(PaneWallPage.TERMINAL));
    }

    @Test
    public void overTheDisplayWindowItStaysOnTheWorkspacePage() {
        assertEquals(PaneWallPage.DISPLAY,
            AppearanceEditorEntry.terminalMenuPlace(PaneWallPage.DISPLAY));
    }

    @Test
    public void fromHomeOrNowhereItLandsOnTheTerminal() {
        assertEquals("a Home float's terminal is still the Workspace's thing",
            PaneWallPage.TERMINAL, AppearanceEditorEntry.terminalMenuPlace(PaneWallPage.WIDGETS));
        assertEquals(PaneWallPage.TERMINAL, AppearanceEditorEntry.terminalMenuPlace(null));
    }
}
