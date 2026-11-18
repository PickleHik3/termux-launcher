package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import com.termux.app.page.PageKind;

import org.junit.Test;

public class PageTopInsetTest {
    private static final PageKind HOME = PageKind.HOME;
    private static final PageKind WINDOW = PageKind.TERMINAL;
    private static final int BAR = 120;

    @Test
    public void aHomePageStartsAtItsOwnInsetAndAWindowAtTheWorkspaces() {
        assertEquals(0, PageTopInset.pageTopInsetPx(HOME, BAR, 0));
        assertEquals(BAR, PageTopInset.pageTopInsetPx(HOME, 0, BAR));
        assertEquals(BAR, PageTopInset.pageTopInsetPx(WINDOW, BAR, 0));
        assertEquals("the display window's frame is a Workspace page", BAR,
            PageTopInset.pageTopInsetPx(null, BAR, 0));
        assertEquals(0, PageTopInset.pageTopInsetPx(WINDOW, -5, 0));
        assertEquals(0, PageTopInset.pageTopInsetPx(HOME, 0, -5));
    }
}
