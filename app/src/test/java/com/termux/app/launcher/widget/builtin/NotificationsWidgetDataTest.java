package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.launcherctl.LauncherCtlNotificationEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NotificationsWidgetDataTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long MINUTE = 60_000L;

    private static LauncherCtlNotificationEvent row(String key, String pkg, String app, String sender,
                                                    String title, String text, long time, long removed) {
        return new LauncherCtlNotificationEvent(-1, key, pkg, app, null, title, sender, text, null,
            null, null, time, time, time, removed, false);
    }

    @Test public void keepsPostedRowsOfTheLastDayNewestFirstOnePerKey() {
        List<LauncherCtlNotificationEvent> rows = Arrays.asList(
            row("a", "com.whatsapp", "WhatsApp", "Ahmed", "Ahmed", "older line", NOW - 10 * MINUTE, 0),
            row("a", "com.whatsapp", "WhatsApp", "Ahmed", "Ahmed", "Sent the PO", NOW - 8 * MINUTE, 0),
            row("b", "com.google.android.gm", "Gmail", null, "Elsevier", "Quote ready", NOW - 30 * MINUTE, 0),
            row("c", "com.github.android", "GitHub", null, "PR", "approved", NOW - 5 * MINUTE, NOW - MINUTE),
            row("d", "org.telegram", "Telegram", null, "Old", "yesterday", NOW - 25 * 60 * MINUTE, 0));
        NotificationsWidgetData.Snapshot snapshot = NotificationsWidgetData.select(rows, NOW, 50);
        assertEquals(2, snapshot.count());
        assertEquals(2, snapshot.apps);
        assertEquals("Ahmed", snapshot.items.get(0).who);
        assertEquals("Sent the PO", snapshot.items.get(0).text);
        assertEquals("Elsevier", snapshot.items.get(1).who);
    }

    @Test public void whoFallsBackFromSenderToTitleToApp() {
        NotificationsWidgetData.Snapshot snapshot = NotificationsWidgetData.select(Arrays.asList(
            row("x", "p.one", "One", null, null, "line\nbreak", NOW - MINUTE, 0)), NOW, 50);
        assertEquals("One", snapshot.items.get(0).who);
        assertEquals("line break", snapshot.items.get(0).text);
    }

    @Test public void limitAndOtherApps() {
        List<LauncherCtlNotificationEvent> rows = Arrays.asList(
            row("1", "p.a", "A", "s", "t", "x", NOW - 1 * MINUTE, 0),
            row("2", "p.a", "A", "s", "t", "x", NOW - 2 * MINUTE, 0),
            row("3", "p.b", "B", "s", "t", "x", NOW - 3 * MINUTE, 0),
            row("4", "p.c", "C", "s", "t", "x", NOW - 4 * MINUTE, 0));
        NotificationsWidgetData.Snapshot all = NotificationsWidgetData.select(rows, NOW, 50);
        assertEquals(Arrays.asList("p.a", "p.b", "p.c"), all.iconPackages(6));
        assertEquals("p.b", all.otherApps(2).get(0).packageName);
        assertEquals("p.c", all.otherApps(2).get(1).packageName);
        assertEquals(2, NotificationsWidgetData.select(rows, NOW, 2).count());
    }

    @Test public void sameAsComparesContent() {
        List<LauncherCtlNotificationEvent> rows = Arrays.asList(
            row("1", "p.a", "A", "s", "t", "x", NOW - MINUTE, 0));
        NotificationsWidgetData.Snapshot one = NotificationsWidgetData.select(rows, NOW, 50);
        assertTrue(one.sameAs(NotificationsWidgetData.select(rows, NOW, 50)));
        assertFalse(one.sameAs(NotificationsWidgetData.Snapshot.EMPTY));
        assertFalse(one.sameAs(null));
    }
}
