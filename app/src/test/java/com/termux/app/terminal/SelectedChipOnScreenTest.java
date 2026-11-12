package com.termux.app.terminal;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A tap on the selected chip closes its window only where that window is on screen. */
public class SelectedChipOnScreenTest {
    @Test
    public void onHomeTheSelectedChipIsAWindowToGoTo() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(PaneWallPage.WIDGETS));
    }

    @Test
    public void onTheAssistantTheSelectedChipIsAWindowToGoTo() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(PaneWallPage.ASSISTANT));
    }

    @Test
    public void onTheWorkspaceAndDisplayTheSelectedChipOffersItsClose() {
        assertTrue(TerminalWindowBar.selectedChipOnScreen(PaneWallPage.TERMINAL));
        assertTrue(TerminalWindowBar.selectedChipOnScreen(PaneWallPage.DISPLAY));
    }

    @Test
    public void noPageKnownOffersNoClose() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(null));
    }
}
