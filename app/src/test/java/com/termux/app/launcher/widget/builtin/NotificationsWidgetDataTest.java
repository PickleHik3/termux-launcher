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

    private static LauncherCtlNotificationEvent categorised(String key, String pkg, String app,
                                                            String sender, String text, String category,
                                                            long time) {
        return new LauncherCtlNotificationEvent(-1, key, pkg, app, null, sender, sender, text, null,
            category, null, time, time, time, 0, false);
    }

    @Test public void systemAndSilentNotificationsAreLeftOut() {
        List<LauncherCtlNotificationEvent> rows = Arrays.asList(
            categorised("a", "com.whatsapp", "WhatsApp", "Mum", "hi", "msg", NOW - MINUTE),
            categorised("b", "android", "Android System", null, "USB charging", null, NOW - MINUTE),
            categorised("c", "com.android.systemui", "System UI", null, "Battery", null, NOW - MINUTE),
            categorised("d", "com.some.app", "Some app", null, "downloading", "progress", NOW - MINUTE),
            categorised("e", "com.news", "News", null, "quiet digest", null, NOW - MINUTE),
            categorised("f", "com.news", "News", null, "breaking", null, NOW - 2 * MINUTE));
        NotificationsWidgetData.Snapshot snapshot = NotificationsWidgetData.select(rows, NOW, 50,
            java.util.Collections.singleton("e"));
        assertEquals(2, snapshot.count());
        assertEquals("hi", snapshot.items.get(0).text);
        assertEquals("breaking", snapshot.items.get(1).text);
    }

    @Test public void groupsPutPeopleFirstAndCountMessagesPerPersonAndPerApp() {
        List<LauncherCtlNotificationEvent> rows = Arrays.asList(
            categorised("n1", "com.news", "News", null, "newest of all", null, NOW - 1 * MINUTE),
            categorised("n2", "com.news", "News", null, "second", null, NOW - 2 * MINUTE),
            // One notification carrying three messages shares a key.
            categorised("w", "com.whatsapp", "WhatsApp", "Mum", "three", "msg", NOW - 3 * MINUTE),
            categorised("w", "com.whatsapp", "WhatsApp", "Mum", "two", "msg", NOW - 4 * MINUTE),
            categorised("w", "com.whatsapp", "WhatsApp", "Mum", "one", "msg", NOW - 5 * MINUTE),
            categorised("s", "com.sms", "Messages", "Alex", "plans", "msg", NOW - 6 * MINUTE));
        NotificationsWidgetData.Snapshot snapshot = NotificationsWidgetData.select(rows, NOW, 50);
        assertEquals(3, snapshot.groups.size());
        assertEquals("Mum", snapshot.groups.get(0).newest.who);
        assertEquals(3, snapshot.groups.get(0).count);
        assertEquals("Alex", snapshot.groups.get(1).newest.who);
        assertEquals(1, snapshot.groups.get(1).count);
        assertFalse(snapshot.groups.get(2).person());
        assertEquals("News", snapshot.groups.get(2).newest.app);
        assertEquals(2, snapshot.groups.get(2).count);
        assertEquals(6, snapshot.groupedCount());
        // The items keep one entry per notification.
        assertEquals(4, snapshot.count());
    }
}
