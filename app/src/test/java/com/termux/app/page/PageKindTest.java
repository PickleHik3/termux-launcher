package com.termux.app.page;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageKindTest {

    @Test
    public void homeIsHomesSideAndEveryWindowIsTheTerminals() {
        assertEquals(Side.HOME, PageKind.HOME.side());
        assertEquals(Side.TERMINAL, PageKind.TERMINAL.side());
        assertEquals(Side.TERMINAL, PageKind.DISPLAY.side());
        assertEquals(Side.TERMINAL, PageKind.ASSISTANT.side());
    }

    @Test
    public void everyKindButDisplayStandsItsOwnRoom() {
        assertTrue(PageKind.HOME.hasOwnRoom());
        assertTrue(PageKind.TERMINAL.hasOwnRoom());
        assertFalse(PageKind.DISPLAY.hasOwnRoom());
        assertTrue(PageKind.ASSISTANT.hasOwnRoom());
    }

    @Test
    public void aDisplayWindowStandsInTheTerminalsRoom() {
        assertEquals(PageKind.HOME, PageKind.HOME.roomKind());
        assertEquals(PageKind.TERMINAL, PageKind.TERMINAL.roomKind());
        assertEquals(PageKind.TERMINAL, PageKind.DISPLAY.roomKind());
        assertEquals(PageKind.ASSISTANT, PageKind.ASSISTANT.roomKind());
    }

    @Test
    public void roomKindsAreThoseWithAnOwnRoomInEnumOrder() {
        assertArrayEquals(
            new PageKind[] {PageKind.HOME, PageKind.TERMINAL, PageKind.ASSISTANT},
            PageKind.roomKinds());
    }
}
