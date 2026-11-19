package com.termux.app.terminal;


import org.junit.Test;
import com.termux.app.page.PageKind;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A tap on the selected chip closes its window only where that window is on screen. */
public class SelectedChipOnScreenTest {
    @Test
    public void onHomeTheSelectedChipIsAWindowToGoTo() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(PageKind.HOME));
    }

    @Test
    public void onTheAssistantTheSelectedChipIsAWindowToGoTo() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(PageKind.ASSISTANT));
    }

    @Test
    public void onTheTerminalAndDisplayTheSelectedChipOffersItsClose() {
        assertTrue(TerminalWindowBar.selectedChipOnScreen(PageKind.TERMINAL));
        assertTrue(TerminalWindowBar.selectedChipOnScreen(PageKind.DISPLAY));
    }

    @Test
    public void noPageKnownOffersNoClose() {
        assertFalse(TerminalWindowBar.selectedChipOnScreen(null));
    }
}
