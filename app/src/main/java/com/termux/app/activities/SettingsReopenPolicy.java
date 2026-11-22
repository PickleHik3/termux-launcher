package com.termux.app.activities;

/**
 * Whether reopening Settings starts again at the root list. Settings keeps the page it was left
 * on for {@link #RETAIN_WINDOW_MS} after it closes, so a quick return lands where the user was;
 * later than that it opens at the top. Times are {@code SystemClock.elapsedRealtime()} values.
 */
final class SettingsReopenPolicy {

    /** How long after a close the last page is still remembered. */
    static final long RETAIN_WINDOW_MS = 3 * 60 * 1000L;

    /** No close has been recorded in this process. */
    static final long NO_CLOSE = -1L;

    private SettingsReopenPolicy() {}

    /**
     * @param closedAtMs when Settings last closed, or {@link #NO_CLOSE}
     * @param nowMs the clock now
     * @param deepLink the launch names a specific page, which always wins
     * @return true when the pages should pop back to the root before the page shows
     */
    static boolean shouldReset(long closedAtMs, long nowMs, boolean deepLink) {
        if (deepLink) return false;
        if (closedAtMs == NO_CLOSE) return false;
        long elapsed = nowMs - closedAtMs;
        return elapsed < 0 || elapsed >= RETAIN_WINDOW_MS;
    }
}
