package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

public class NotificationsSnapshotCacheTest {
    @After public void tearDown() { NotificationsSnapshotCache.clear(); }

    @Test public void emptyUntilFirstRead() {
        assertNull(NotificationsSnapshotCache.get());
    }

    @Test public void keepsOnlyTheLastSnapshot() {
        NotificationsWidgetData.Snapshot first = new NotificationsWidgetData.Snapshot(
            java.util.Collections.emptyList(), 0);
        NotificationsWidgetData.Snapshot second = new NotificationsWidgetData.Snapshot(
            java.util.Collections.emptyList(), 0);
        NotificationsSnapshotCache.put(first);
        NotificationsSnapshotCache.put(second);
        assertSame(second, NotificationsSnapshotCache.get());
    }

    @Test public void clearForgetsIt() {
        NotificationsSnapshotCache.put(NotificationsWidgetData.Snapshot.EMPTY);
        NotificationsSnapshotCache.clear();
        assertNull(NotificationsSnapshotCache.get());
    }
}
