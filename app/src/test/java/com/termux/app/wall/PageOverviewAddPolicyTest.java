package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Edit pages' bottom pills: Add page, Bring back N Home pages, Add terminal. */
public class PageOverviewAddPolicyTest {

    @Test
    public void addPageShowsOnlyWhileHomePagesAreOnTheWall() {
        assertTrue(PageOverviewAddPolicy.showsAddPage(true));
        assertFalse(PageOverviewAddPolicy.showsAddPage(false));
    }

    @Test
    public void bringBackCountsTheDetachedHomePagesUnderTerminalOnly() {
        assertEquals("the welcome sheet's Terminal only", 3,
            PageOverviewAddPolicy.bringBackCount(false, 3, 0));
        assertEquals("nothing set aside: no pill", 0,
            PageOverviewAddPolicy.bringBackCount(false, 0, 0));
        assertEquals("Home pages on the wall: no pill", 0,
            PageOverviewAddPolicy.bringBackCount(true, 2, 0));
    }

    @Test
    public void settingsUseAsHidesHomePagesThatStayInTheList() {
        // Settings > Use as writes the preference alone: the Home pages stay in the page list,
        // nothing is detached, and the wall hides them. They are the ones to bring back.
        WallPages pages = WallPages.EMPTY
            .add(WallPages.PageKind.HOME, WallPages.END, null).pages
            .add(WallPages.PageKind.HOME, WallPages.END, null).pages
            .add(WallPages.PageKind.TERMINAL, WallPages.END, null).pages;
        assertEquals(2, PageOverviewAddPolicy.bringBackCount(false, pages.detached().size(),
            pages.homePages().size()));
        assertEquals(0, PageOverviewAddPolicy.bringBackCount(true, pages.detached().size(),
            pages.homePages().size()));
        WallPages detached = pages.detachHomes().pages;
        assertEquals("the welcome sheet's path detaches them instead", 2,
            PageOverviewAddPolicy.bringBackCount(false, detached.detached().size(),
                detached.homePages().size()));
    }

    @Test
    public void addTerminalShowsOnlyWhileThereIsNoTerminalPage() {
        assertTrue(PageOverviewAddPolicy.showsAddTerminal(false));
        assertFalse(PageOverviewAddPolicy.showsAddTerminal(true));
    }
}
