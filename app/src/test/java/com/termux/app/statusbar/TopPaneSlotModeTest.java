package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopPaneSlotModeTest {

    @Test
    public void idleSlot_keepsTheClockAtFullSize() {
        TopPaneSlotMode mode = TopPaneSlotMode.derive(0, false);
        assertEquals(TopPaneSlotMode.CLOCK_ONLY, mode);
        assertEquals(TopPaneClockForm.FULL, mode.clockForm(0));
        assertFalse(mode.showsMedia());
        assertFalse(mode.showsNotifications());
    }

    @Test
    public void mediaAlone_compactsTheClock() {
        TopPaneSlotMode mode = TopPaneSlotMode.derive(0, true);
        assertEquals(TopPaneSlotMode.MEDIA, mode);
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(0));
        assertTrue(mode.showsMedia());
    }

    @Test
    public void pinnedNotifications_outrankMedia() {
        // One card leaves room for the media strip; a second does not.
        assertEquals(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA, TopPaneSlotMode.derive(1, true));
        assertEquals(TopPaneSlotMode.NOTIFICATIONS, TopPaneSlotMode.derive(2, true));
        assertEquals(TopPaneSlotMode.NOTIFICATIONS, TopPaneSlotMode.derive(3, true));
        assertTrue(TopPaneSlotMode.derive(1, true).showsMedia());
        assertFalse(TopPaneSlotMode.derive(2, true).showsMedia());
    }

    @Test
    public void nothingDropsTheClockToItsMonoChip() {
        // The three-row stack went away with the scrolling cards: two cards and a compact clock
        // is the busiest the slot ever gets, however many notifications matched.
        TopPaneSlotMode mode = TopPaneSlotMode.derive(3, false);
        assertEquals(TopPaneSlotMode.NOTIFICATIONS, mode);
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(1));
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(2));
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(3));
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(9));
    }

    @Test
    public void twoCardsAreWhatTheSlotShowsAtOnce() {
        assertEquals(2, TopPaneSlotMode.VISIBLE_PINNED);
        assertTrue("the pane keeps more than it shows, so there is something to scroll",
            TopPaneSlotMode.MAX_PINNED > TopPaneSlotMode.VISIBLE_PINNED);
        TopPaneSlotMode mode = TopPaneSlotMode.derive(9, false);
        assertEquals(TopPaneSlotMode.NOTIFICATIONS, mode);
        assertEquals(TopPaneClockForm.COMPACT, mode.clockForm(9));
    }

    @Test
    public void negativeCountsAreTreatedAsIdle() {
        assertEquals(TopPaneSlotMode.CLOCK_ONLY, TopPaneSlotMode.derive(-1, false));
    }

    @Test public void tilesGiveWayToPinnedNotificationsAndMedia() {
        // The 68dp band cannot hold three equal cells and a notification card at phone widths,
        // so while anything outranks the clock the status-bar swipe is the way across.
        assertTrue(TopPaneSlotMode.CLOCK_ONLY.showsTiles(true));
        assertFalse(TopPaneSlotMode.CLOCK_ONLY.showsTiles(false));
        assertFalse(TopPaneSlotMode.MEDIA.showsTiles(true));
        assertFalse(TopPaneSlotMode.NOTIFICATIONS.showsTiles(true));
        assertFalse(TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA.showsTiles(true));
    }
}
