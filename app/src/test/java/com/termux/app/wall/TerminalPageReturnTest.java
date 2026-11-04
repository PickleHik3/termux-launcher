package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

/** A wall without its Terminal page takes it back, after Home, once a terminal window opens. */
public class TerminalPageReturnTest {

    private static WallPages ok(WallPages.Result result) {
        assertTrue(String.valueOf(result.error), result.ok());
        return result.pages;
    }

    /** [Home 1][Home 2][Display] with no Terminal page. */
    private static WallPages withoutTerminal() {
        WallPages p = ok(WallPages.EMPTY.add(PageKind.HOME, WallPages.END, null));
        p = ok(p.add(PageKind.HOME, WallPages.END, null));
        return ok(p.add(PageKind.DISPLAY, WallPages.END, null));
    }

    @Test
    public void neededOnlyWhenAWindowOpensOnAWallWithoutTheTerminalPage() {
        WallPages none = withoutTerminal();
        assertTrue(TerminalPageReturn.needed(none, true));
        assertFalse("no window: the wall stays as it is", TerminalPageReturn.needed(none, false));
        assertFalse("pages not known yet", TerminalPageReturn.needed(null, true));
        WallPages back = ok(TerminalPageReturn.restore(none));
        assertFalse("already there", TerminalPageReturn.needed(back, true));
    }

    @Test
    public void itComesBackAfterTheLastHomePage() {
        WallPages back = ok(TerminalPageReturn.restore(withoutTerminal()));
        assertNotNull(back.terminal());
        assertEquals(PageKind.HOME, back.pages().get(1).kind);
        assertEquals(PageKind.TERMINAL, back.pages().get(2).kind);
        assertEquals(PageKind.DISPLAY, back.pages().get(3).kind);
    }

    @Test
    public void withNoHomePageItGoesFirst() {
        WallPages displayOnly = ok(WallPages.EMPTY.add(PageKind.DISPLAY, WallPages.END, null));
        assertEquals(TerminalPageReturn.FIRST, TerminalPageReturn.addAfter(displayOnly));
        WallPages back = ok(TerminalPageReturn.restore(displayOnly));
        assertEquals(PageKind.TERMINAL, back.pages().get(0).kind);
        assertEquals(PageKind.DISPLAY, back.pages().get(1).kind);
    }
}
