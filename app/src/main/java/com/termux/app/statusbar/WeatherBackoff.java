package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How long automatic weather refreshes wait after a failed fetch: 1, 2, 4, 8, 16, then 30 minutes,
 * doubling per consecutive failure. Tracks one place at a time; a different place starts clean, and
 * a success clears it. Pure: the clock is passed in. Explicit refreshes (a tap, a permission grant)
 * do not ask this at all.
 */
final class WeatherBackoff {

    private static final long MINUTE_MS = 60_000L;
    private static final long[] DELAYS_MS = {
        MINUTE_MS, 2 * MINUTE_MS, 4 * MINUTE_MS, 8 * MINUTE_MS, 16 * MINUTE_MS, 30 * MINUTE_MS};

    @Nullable private String mPlace;
    private int mFailures;
    private long mNextAttemptMs;

    /** The wait after the {@code failures}-th consecutive failure (1-based). */
    static long delayMs(int failures) {
        if (failures <= 0) return 0L;
        return DELAYS_MS[Math.min(failures, DELAYS_MS.length) - 1];
    }

    /** True when an automatic refresh for {@code place} may start at {@code nowMs}. */
    boolean allows(@NonNull String place, long nowMs) {
        if (!place.equals(mPlace) || mFailures == 0) return true;
        // A clock set back must not lock refreshes out for longer than the wait itself.
        return nowMs >= mNextAttemptMs || mNextAttemptMs - nowMs > delayMs(mFailures);
    }

    void onFailure(@NonNull String place, long nowMs) {
        if (!place.equals(mPlace)) {
            mPlace = place;
            mFailures = 0;
        }
        mFailures++;
        mNextAttemptMs = nowMs + delayMs(mFailures);
    }

    void onSuccess() {
        mPlace = null;
        mFailures = 0;
        mNextAttemptMs = 0L;
    }

    int failures() {
        return mFailures;
    }
}
