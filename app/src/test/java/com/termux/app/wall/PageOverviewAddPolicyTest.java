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
        assertEquals(3, PageOverviewAddPolicy.bringBackCount(false, 3));
        assertEquals("nothing set aside: no pill", 0, PageOverviewAddPolicy.bringBackCount(false, 0));
        assertEquals("Home pages on the wall: no pill", 0,
            PageOverviewAddPolicy.bringBackCount(true, 2));
    }

    @Test
    public void addTerminalShowsOnlyWhileThereIsNoTerminalPage() {
        assertTrue(PageOverviewAddPolicy.showsAddTerminal(false));
        assertFalse(PageOverviewAddPolicy.showsAddTerminal(true));
    }
}
