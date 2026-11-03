package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

/** Which layout a page wears (ADR 0020 §1, spec §9 point 3). */
public class LayoutKindResolverTest {

    @Test
    public void eachKindWearsItsOwn() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, false));
        assertEquals(LayoutKind.TERMINAL, LayoutKind.of(PageKind.TERMINAL, false));
        assertEquals(LayoutKind.DISPLAY, LayoutKind.of(PageKind.DISPLAY, false));
    }

    @Test
    public void minimalDressesTheTerminalAndTheDisplayButNeverHome() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.TERMINAL, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.DISPLAY, true));
    }

    @Test
    public void theEditorNamesTheLayout() {
        assertEquals("Home layout", LayoutKind.HOME.title);
        assertEquals("Terminal layout", LayoutKind.TERMINAL.title);
        assertEquals("Display layout", LayoutKind.DISPLAY.title);
        assertEquals("Terminal minimal layout", LayoutKind.TERMINAL_MINIMAL.title);
    }

    @Test
    public void keysNameTheDocuments() {
        for (LayoutKind kind : LayoutKind.values())
            assertEquals(kind, LayoutKind.forKey(kind.key));
        assertNull(LayoutKind.forKey("page1"));
    }
}
