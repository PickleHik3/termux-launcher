package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Device check 3, finding 3: always on's notice, turned on from the Layout editor, waits for the
 * editor to close rather than standing over its mode switcher and Undo.
 */
public class HomeAlwaysOnNoticeTest {

    @Test
    public void withNoEditorUpTheNoticeShowsAtOnce() {
        HomeAlwaysOnNotice notice = new HomeAlwaysOnNotice();
        assertEquals("moved", notice.offer("moved", false));
        assertFalse(notice.isPending());
        assertNull(notice.onEditorClosed());
    }

    @Test
    public void turnedOnInTheEditorTheNoticeWaitsForTheCloseAndShowsOnce() {
        HomeAlwaysOnNotice notice = new HomeAlwaysOnNotice();
        assertNull(notice.offer("moved", true));
        assertTrue(notice.isPending());
        assertEquals("moved", notice.onEditorClosed());
        assertNull(notice.onEditorClosed());
    }

    @Test
    public void turnedOffAgainBeforeTheCloseTheNoticeIsWithdrawn() {
        HomeAlwaysOnNotice notice = new HomeAlwaysOnNotice();
        notice.offer("moved", true);
        notice.withdraw();
        assertNull(notice.onEditorClosed());
    }

    @Test
    public void theLatestOfferInOneSessionIsTheOneShown() {
        HomeAlwaysOnNotice notice = new HomeAlwaysOnNotice();
        notice.offer("first", true);
        notice.offer("second", true);
        assertEquals("second", notice.onEditorClosed());
    }
}
