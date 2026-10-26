package com.termux.app.launcher.notifications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NotificationBadgeStyleTest {

    @Test
    public void theDotIsTheDefault() {
        assertEquals(NotificationBadgeStyle.DOT, NotificationBadgeStyle.fromValue(null));
        assertEquals(NotificationBadgeStyle.DOT, NotificationBadgeStyle.fromValue("number"));
        assertEquals(NotificationBadgeStyle.DOT, NotificationBadgeStyle.fromValue("dot"));
        assertEquals(NotificationBadgeStyle.COUNT, NotificationBadgeStyle.fromValue("count"));
    }

    @Test
    public void everyValueRoundTrips() {
        for (NotificationBadgeStyle style : NotificationBadgeStyle.values()) {
            assertEquals(style, NotificationBadgeStyle.fromValue(style.value));
        }
    }

    @Test
    public void countsShowAsThemselvesUpToNinetyNine() {
        assertEquals("1", NotificationBadgeStyle.countLabel(1));
        assertEquals("99", NotificationBadgeStyle.countLabel(99));
    }

    @Test
    public void moreThanNinetyNineReadsNinetyNinePlus() {
        assertEquals("99+", NotificationBadgeStyle.countLabel(100));
        assertEquals("99+", NotificationBadgeStyle.countLabel(4000));
    }

    @Test
    public void nothingToSayIsEmpty() {
        assertEquals("", NotificationBadgeStyle.countLabel(0));
        assertEquals("", NotificationBadgeStyle.countLabel(-2));
    }
}
