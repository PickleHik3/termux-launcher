package com.termux.app;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A short Info-level trail of the activity and service lifecycle. A release build keeps Info, so
 * Settings > Diagnostics > Export logs from a user whose Home "refreshes nonstop" shows whether the
 * activity is being recreated, finished and relaunched, or crashing, and what asked for it. Log
 * only: nothing here changes what the app does.
 */
final class LifecycleTrail {

    static final String LOG_TAG = "Lifecycle";

    private static final AtomicInteger sSeq = new AtomicInteger();
    private static final Rate sCreateRate = new Rate(5, 60_000L);

    private LifecycleTrail() {}

    static void activity(@NonNull String event, @Nullable String detail) {
        line("activity", event, detail);
        if ("create".equals(event) && sCreateRate.onEvent(SystemClock.elapsedRealtime())) {
            Logger.logWarn(LOG_TAG, "activity created 5 times in 60 s: relaunch loop?");
        }
    }

    static void service(@NonNull String event, @Nullable String detail) {
        line("service", event, detail);
    }

    /** The method name {@code framesUp} frames above the caller of this method; "?" when out of range. */
    @NonNull
    static String caller(int framesUp) {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            // 0 getStackTrace, 1 caller (this method), 2 its caller, ...
            int i = 1 + framesUp;
            return i >= 0 && i < st.length ? st[i].getMethodName() : "?";
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static void line(String kind, String event, @Nullable String detail) {
        long up = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime();
        Logger.logInfo(LOG_TAG, kind + " " + event + " #" + sSeq.incrementAndGet()
            + " pid=" + android.os.Process.myPid() + " up=" + up + "ms"
            + (detail == null ? "" : " " + detail));
    }

    /**
     * Fires on the event that reaches {@code limit} within {@code windowMs}, then not again until
     * the window has moved on, so a loop logs one warning a window, not one per event. Pure: the
     * caller hands in the clock.
     */
    static final class Rate {
        private final int limit;
        private final long windowMs;
        private final long[] times;
        private int count;
        private long lastFire = Long.MIN_VALUE;

        Rate(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
            this.times = new long[limit];
        }

        synchronized boolean onEvent(long nowMs) {
            // The last `limit` events, oldest first.
            if (count == limit) {
                System.arraycopy(times, 1, times, 0, limit - 1);
                count--;
            }
            times[count++] = nowMs;
            if (count < limit || nowMs - times[0] >= windowMs) return false;
            if (lastFire != Long.MIN_VALUE && nowMs - lastFire < windowMs) return false;
            lastFire = nowMs;
            return true;
        }
    }
}
