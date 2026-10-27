package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The display window's chip names the app in front, and says Display only with none open. */
public class DisplayWindowNameTest {

    @Test public void theFocusedAppIsInFront() {
        assertEquals(0, DisplayWindowName.frontIndex(3, 0));
        assertEquals(2, DisplayWindowName.frontIndex(3, 2));
    }

    @Test public void withNothingFocusedTheNewestAppIsInFront() {
        assertEquals(2, DisplayWindowName.frontIndex(3, -1));
        assertEquals("a stale focus past the list", 1, DisplayWindowName.frontIndex(2, 5));
    }

    @Test public void noAppNoFront() {
        assertEquals(-1, DisplayWindowName.frontIndex(0, -1));
        assertEquals(-1, DisplayWindowName.frontIndex(0, 0));
        assertEquals(-1, DisplayWindowName.frontIndex(-1, 0));
    }

    @Test public void theDesktopEntrysNameWins() {
        assertEquals("Firefox ESR", DisplayWindowName.of("Firefox ESR", "Firefox-esr", "Display"));
        assertEquals("trimmed", "GIMP", DisplayWindowName.of("  GIMP ", "Gimp", "Display"));
    }

    @Test public void withoutAnEntryTheWindowsOwnLabel() {
        assertEquals("Xeyes", DisplayWindowName.of(null, "Xeyes", "Display"));
        assertEquals("a blank entry name is none", "Xeyes",
            DisplayWindowName.of("   ", "Xeyes", "Display"));
    }

    @Test public void displayOnlyWhenNothingNamesAnApp() {
        assertEquals("Display", DisplayWindowName.of(null, null, "Display"));
        assertEquals("Display", DisplayWindowName.of("", " ", "Display"));
    }
}
