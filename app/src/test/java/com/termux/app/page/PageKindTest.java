package com.termux.app.page;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PageKindTest {

    @Test
    public void homeIsHomesSideAndEveryWindowIsTheWorkspaces() {
        assertEquals(Side.HOME, PageKind.HOME.side());
        assertEquals(Side.WORKSPACE, PageKind.TERMINAL.side());
        assertEquals(Side.WORKSPACE, PageKind.DISPLAY.side());
        assertEquals(Side.WORKSPACE, PageKind.ASSISTANT.side());
    }
}
