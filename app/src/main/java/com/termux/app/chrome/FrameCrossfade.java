package com.termux.app.chrome;

import android.os.SystemClock;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.termux.app.terminal.Motion;

/**
 * A 220ms fade-in progress, on the settle curve, for a view that draws two bitmaps itself in
 * {@code onDraw} rather than handing the crossfade to a {@code Drawable}.
 *
 * <p>There is no animator object to leak or to cancel from the wrong thread: {@link #start()}
 * stamps a wall-clock time, and {@link #progress()} is a pure function of how long ago that was.
 * A view calls it once per draw, keeps invalidating itself while it answers less than 1, and stops
 * once it reaches 1 — the same pattern {@code postInvalidateOnAnimation} loops already use here for
 * geometry, just driven by elapsed time instead of a spring.</p>
 */
public final class FrameCrossfade {

    /** The wallpaper crossfade's one duration, named so every surface that fades agrees on it. */
    public static final long DURATION_MS = 220L;

    @NonNull private final Interpolator mInterpolator = Motion.settle();
    private long mStartUptimeMs = -1L;

    /** Begins the fade now; a fade already running restarts from 0 rather than jumping. */
    public void start() {
        mStartUptimeMs = SystemClock.uptimeMillis();
    }

    /** Cuts the fade short: the next {@link #progress()} answers 1, as if it had already finished. */
    public void cancel() {
        mStartUptimeMs = -1L;
    }

    /** True while the fade has landed on 1 and there is nothing left to draw a previous frame for. */
    public boolean isFinished() {
        return progress() >= 1f;
    }

    /** 0 at {@link #start()}, eased to 1 over {@link #DURATION_MS}; 1 before the first start. */
    public float progress() {
        if (mStartUptimeMs < 0L) return 1f;
        long elapsedMs = SystemClock.uptimeMillis() - mStartUptimeMs;
        if (elapsedMs >= DURATION_MS) return 1f;
        return mInterpolator.getInterpolation(elapsedMs / (float) DURATION_MS);
    }
}
