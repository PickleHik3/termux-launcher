package com.termux.app.statusbar;

/**
 * Derived state of the 68dp widget slot. Priority order is pinned notifications, then media, then
 * the clock at full size. The clock form follows from how much of the slot is already claimed.
 *
 * <p>Media only shares the slot with a single pinned card: the contention layout needs 40dp for the
 * card plus a 6dp gap plus the 20dp media strip, so a second card leaves no room for the strip.
 */
public enum TopPaneSlotMode {
    CLOCK_ONLY,
    MEDIA,
    NOTIFICATIONS,
    NOTIFICATIONS_AND_MEDIA;

    /** A fourth match evicts the oldest pin rather than growing the stack. */
    public static final int MAX_PINNED = 3;

    public static TopPaneSlotMode derive(int pinnedCount, boolean mediaActive) {
        int pinned = Math.max(0, Math.min(MAX_PINNED, pinnedCount));
        if (pinned > 0) {
            return mediaActive && pinned == 1 ? NOTIFICATIONS_AND_MEDIA : NOTIFICATIONS;
        }
        return mediaActive ? MEDIA : CLOCK_ONLY;
    }

    public boolean showsNotifications() {
        return this == NOTIFICATIONS || this == NOTIFICATIONS_AND_MEDIA;
    }

    /**
     * Whether the wall's navigation tiles fit. Pinned notifications and media outrank the clock
     * and the tiles both: the 68dp band cannot hold three cells and a notification card at phone
     * widths, and the status-bar swipe remains the way across while they hold the slot.
     */
    public boolean showsTiles(boolean tilesRequested) {
        return tilesRequested && this == CLOCK_ONLY;
    }

    public boolean showsMedia() {
        return this == MEDIA || this == NOTIFICATIONS_AND_MEDIA;
    }

    /** The full stack of three drops the clock to its mono chip; anything else shares one row. */
    public TopPaneClockForm clockForm(int pinnedCount) {
        switch (this) {
            case NOTIFICATIONS:
                return pinnedCount >= MAX_PINNED ? TopPaneClockForm.MONO_CHIP : TopPaneClockForm.COMPACT;
            case NOTIFICATIONS_AND_MEDIA:
            case MEDIA:
                return TopPaneClockForm.COMPACT;
            default:
                return TopPaneClockForm.FULL;
        }
    }
}
