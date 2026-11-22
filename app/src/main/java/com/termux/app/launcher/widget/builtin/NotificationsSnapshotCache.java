package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The last notifications read, kept so a Notifications card bound to a page that is swiping in
 * draws its rows on its first frame, before its own read lands. The shade is the same for every
 * card, so one snapshot serves them all: text only, no icons, at most the reader's row limit.
 * Main thread only.
 */
final class NotificationsSnapshotCache {
    @Nullable private static NotificationsWidgetData.Snapshot last;

    private NotificationsSnapshotCache() { }

    /** The last snapshot a card drew, or null before the first read or after access was lost. */
    @Nullable static NotificationsWidgetData.Snapshot get() { return last; }

    static void put(@NonNull NotificationsWidgetData.Snapshot snapshot) { last = snapshot; }

    static void clear() { last = null; }
}
