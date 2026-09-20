package com.termux.app.statusbar;

/**
 * Derived state of the 68dp widget slot. Priority order is pinned notifications, then media, then
 * the clock at full size. The clock form follows from how much of the slot is already claimed.
 *
 * <p>Media only shares the slot with a single pinned card: the contention layout needs 40dp for the
 * card plus a 6dp gap plus the 20dp media strip, so a second card leaves no room for the strip.
 *
 * <p>Two cards are as many as the slot ever shows at once. Further matches are not squeezed in:
 * the cards keep their size and the slot scrolls through them, so the clock never has to shrink
 * past its compact face however many notifications match.
 */
public enum TopPaneSlotMode {
    CLOCK_ONLY,
    MEDIA,
    NOTIFICATIONS,
    NOTIFICATIONS_AND_MEDIA;

    /** How many full cards the slot shows at once; the rest are a swipe away. */
    public static final int VISIBLE_PINNED = 2;

    /**
     * How many matches are kept at all. It is no longer a drawing limit — the cards scroll — only
     * a ceiling on what one pane holds, so an app posting a run of matches cannot fill it forever.
     */
    public static final int MAX_PINNED = 8;

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

    /**
     * Anything sharing the slot leaves the clock its compact face. Nothing drops it to the mono
     * chip any more: that was the three-row stack, which the scrolling cards replaced.
     */
    public TopPaneClockForm clockForm(int pinnedCount) {
        switch (this) {
            case NOTIFICATIONS:
            case NOTIFICATIONS_AND_MEDIA:
            case MEDIA:
                return TopPaneClockForm.COMPACT;
            default:
                return TopPaneClockForm.FULL;
        }
    }
}
