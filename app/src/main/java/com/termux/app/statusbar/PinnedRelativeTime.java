package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * The pinned card's age label: "now", "4m", "2h", "1d". Whole units, rounded down, the largest
 * that is at least one; a post time in the future (a clock skew) reads as "now".
 */
public final class PinnedRelativeTime {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;

    private PinnedRelativeTime() {}

    @NonNull
    public static String format(long nowMs, long postTimeMs) {
        long age = nowMs - postTimeMs;
        if (postTimeMs <= 0L || age < MINUTE_MS) return "now";
        if (age < HOUR_MS) return (age / MINUTE_MS) + "m";
        if (age < DAY_MS) return (age / HOUR_MS) + "h";
        return (age / DAY_MS) + "d";
    }

    /**
     * How long until the label next changes, so the view refreshes at most once a minute and no
     * sooner than the label actually moves.
     */
    public static long msUntilChange(long nowMs, long postTimeMs) {
        long age = Math.max(0L, nowMs - postTimeMs);
        long unit = age < HOUR_MS ? MINUTE_MS : age < DAY_MS ? HOUR_MS : DAY_MS;
        long next = unit - (age % unit);
        return Math.max(MINUTE_MS, next);
    }
}
