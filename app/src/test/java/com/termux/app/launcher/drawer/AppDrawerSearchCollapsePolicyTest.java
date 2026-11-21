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
}
