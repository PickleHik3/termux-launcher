package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerSearchCollapsePolicy.Event;

import org.junit.Test;

public class AppDrawerSearchCollapsePolicyTest {

    @Test public void aScrollableListFoldsTheBar() {
        assertTrue(AppDrawerSearchCollapsePolicy.collapsedAfter(false, Event.SCROLL_STARTED,
            true, false, false));
    }

    @Test public void aListThatFitsNeverFoldsIt() {
        assertFalse(AppDrawerSearchCollapsePolicy.collapsedAfter(false, Event.SCROLL_STARTED,
            false, false, false));
    }

    @Test public void aFoldedBarStaysFoldedWhileScrolling() {
        assertTrue(AppDrawerSearchCollapsePolicy.collapsedAfter(true, Event.SCROLL_STARTED,
            false, false, false));
    }

    @Test public void aQueryOrEditModeKeepsTheFullBar() {
        assertFalse(AppDrawerSearchCollapsePolicy.collapsedAfter(false, Event.SCROLL_STARTED,
            true, true, false));
        assertFalse(AppDrawerSearchCollapsePolicy.collapsedAfter(false, Event.SCROLL_STARTED,
            true, false, true));
        assertFalse(AppDrawerSearchCollapsePolicy.collapsedAfter(true, Event.SCROLL_STARTED,
            true, true, false));
    }

    @Test public void everyOtherEventUnfoldsIt() {
        for (Event e : Event.values()) {
            if (e == Event.SCROLL_STARTED) continue;
            assertFalse(e.name(),
                AppDrawerSearchCollapsePolicy.collapsedAfter(true, e, true, false, false));
        }
    }

    @Test public void aDragFoldsTheBarOnlyOnceItHasScrolledAFullRow() {
        // A 96 px row of the grid on screen.
        assertFalse(AppDrawerSearchCollapsePolicy.browsedARow(0, 96));
        assertFalse(AppDrawerSearchCollapsePolicy.browsedARow(95, 96));
        assertTrue(AppDrawerSearchCollapsePolicy.browsedARow(96, 96));
        assertTrue(AppDrawerSearchCollapsePolicy.browsedARow(400, 96));
        // Up counts back: a drag down a row and back up has not browsed.
        assertFalse(AppDrawerSearchCollapsePolicy.browsedARow(96 - 96, 96));
        assertFalse(AppDrawerSearchCollapsePolicy.browsedARow(-200, 96));
    }

    @Test public void aListWithNoRowLaidOutFoldsOnAnyScrollDown() {
        assertFalse(AppDrawerSearchCollapsePolicy.browsedARow(0, 0));
        assertTrue(AppDrawerSearchCollapsePolicy.browsedARow(1, 0));
    }
}
