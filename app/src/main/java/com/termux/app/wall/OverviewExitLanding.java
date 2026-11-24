package com.termux.app.wall;

import androidx.annotation.Nullable;

/**
 * Where the wall lands when the overview ends. A person closing it lands on the card they chose;
 * the Home exit does not page (ADR 0034): the press stays on the page the overview was opened
 * from, whichever card the strip had been scrolled to meanwhile.
 */
final class OverviewExitLanding {

    @Nullable private WallRow.Position mOrigin;
    private boolean mExiting;

    /** The overview opens: the page it was opened from. */
    void opened(@Nullable WallRow.Position origin) {
        mOrigin = origin;
        mExiting = false;
    }

    /**
     * The Home exit starts.
     *
     * @return the page to show under the fading overview, or null when none was recorded
     */
    @Nullable WallRow.Position exitStarted() {
        mExiting = true;
        return mOrigin;
    }

    /** The page the wall lands on now: the origin during the exit, else {@code focused}. */
    @Nullable WallRow.Position landing(@Nullable WallRow.Position focused) {
        return mExiting && mOrigin != null ? mOrigin : focused;
    }
}
