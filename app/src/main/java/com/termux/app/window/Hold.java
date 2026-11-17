package com.termux.app.window;

import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.LongSupplier;

/**
 * A wait on something Android or another process delivers late, as named state (ADR 0028 section
 * 4). The owner keeps the waiting element at its before-pose while {@link #isOpen}; the hold ends
 * once, by the signal it waits for ({@link #signal}), by the work landing ({@link #landing}) or by
 * its backstop ({@link #BACKSTOP}), and the first release wins. Every release is counted under
 * {@code hold:<name>:<reason>} and logged with how long and how many frames it was held.
 *
 * <p>A hold never cancels a draw by itself. The one that may is the window's first frame after a
 * relaunch ({@link FramePipeline#holdFirstFrame}), which reads {@link #onFrame} once per frame
 * and so keeps its frame-count and time backstops.</p>
 */
public final class Hold {

    public static final String SIGNAL = "signal";
    public static final String LANDING = "landing";
    public static final String BACKSTOP = "backstop";

    /** No frame-count backstop: only the time one. */
    public static final int NO_FRAME_LIMIT = 0;

    @NonNull private final String mName;
    private final long mBackstopMs;
    private final int mMaxFrames;
    @NonNull private final LongSupplier mClock;
    private final long mTakenAt;
    private int mFrames;
    @Nullable private String mReason;
    @Nullable private Runnable mOnRelease;
    @Nullable private Handler mTimer;
    private final Runnable mBackstopRun = this::releaseByBackstop;
    /** A hold that cycles too often for the ring: counted always, logged only on a backstop. */
    private final boolean mQuiet;

    private Hold(@NonNull String name, long backstopMs, int maxFrames, @NonNull LongSupplier clock,
                 boolean quiet) {
        mQuiet = quiet;
        mName = name;
        mBackstopMs = backstopMs;
        mMaxFrames = maxFrames;
        mClock = clock;
        mTakenAt = clock.getAsLong();
    }

    /** Takes a hold that backs off after {@code backstopMs}; the owner checks or arms the backstop. */
    @NonNull
    public static Hold take(@NonNull String name, long backstopMs) {
        return take(name, backstopMs, NO_FRAME_LIMIT, SystemClock::uptimeMillis);
    }

    /** As {@link #take(String, long)}, also backing off after {@code maxFrames} frames asked. */
    @NonNull
    public static Hold take(@NonNull String name, long backstopMs, int maxFrames,
                            @NonNull LongSupplier clock) {
        return take(name, backstopMs, maxFrames, clock, false);
    }

    /**
     * A hold that cycles every few seconds (a poll): counted like any other, but it leaves a ring
     * line only when its backstop ends it, so it cannot fill the ring.
     */
    @NonNull
    public static Hold takeQuiet(@NonNull String name, long backstopMs) {
        return take(name, backstopMs, NO_FRAME_LIMIT, SystemClock::uptimeMillis, true);
    }

    @NonNull
    private static Hold take(@NonNull String name, long backstopMs, int maxFrames,
                             @NonNull LongSupplier clock, boolean quiet) {
        Hold hold = new Hold(name, backstopMs, maxFrames, clock, quiet);
        if (Counters.enabled) {
            Counters.count("hold:" + name, "backstopMs=" + backstopMs);
            if (!quiet) Counters.ring("hold-take", "hold=" + name + " backstopMs=" + backstopMs);
        }
        return hold;
    }

    @NonNull
    public String name() {
        return mName;
    }

    public long backstopMs() {
        return mBackstopMs;
    }

    public boolean isOpen() {
        return mReason == null;
    }

    /** How the hold ended ({@link #SIGNAL}, {@link #LANDING}, {@link #BACKSTOP}); null while open. */
    @Nullable
    public String releaseReason() {
        return mReason;
    }

    public long heldMs() {
        return mClock.getAsLong() - mTakenAt;
    }

    public int heldFrames() {
        return mFrames;
    }

    /** Runs {@code run} once, when the hold ends, whoever ends it; at once if it already has. */
    public void onRelease(@NonNull Runnable run) {
        if (mReason != null) {
            run.run();
            return;
        }
        mOnRelease = run;
    }

    /** Ends the hold by the signal it waits for; false when it had already ended. */
    public boolean signal() {
        return release(SIGNAL, null);
    }

    /** Ends the hold because the work landed; false when it had already ended. */
    public boolean landing() {
        return release(LANDING, null);
    }

    /**
     * Ends the hold with {@code reason}; the first release wins and later ones do nothing (a hold
     * that a newer one superseded is a no-op when it fires).
     */
    public boolean release(@NonNull String reason, @Nullable String detail) {
        if (mReason != null) return false;
        mReason = reason;
        Handler timer = mTimer;
        mTimer = null;
        if (timer != null) timer.removeCallbacks(mBackstopRun);
        if (Counters.enabled) {
            Counters.count("hold:" + mName + ":" + reason, detail);
            if (!mQuiet || BACKSTOP.equals(reason)) {
                Counters.ring("hold-released", "hold=" + mName + " reason=" + reason
                    + " heldMs=" + heldMs() + " frames=" + mFrames + (detail == null ? "" : " " + detail));
            }
        }
        Runnable then = mOnRelease;
        mOnRelease = null;
        if (then != null) then.run();
        return true;
    }

    /**
     * One frame is about to draw with the hold open: true while it still holds, false once a
     * backstop ended it (the frame count or the time) or it had ended. Frames are counted only
     * while held.
     */
    public boolean onFrame() {
        if (mReason != null) return false;
        if ((mMaxFrames > 0 && mFrames >= mMaxFrames) || heldMs() >= mBackstopMs) {
            releaseByBackstop();
            return false;
        }
        mFrames++;
        return true;
    }

    /** Ends the hold by its time backstop when that has passed; true while it is still open. */
    public boolean pollBackstop() {
        if (mReason != null) return false;
        if (heldMs() >= mBackstopMs) {
            releaseByBackstop();
            return false;
        }
        return true;
    }

    /** Has {@code handler} end the hold by its backstop even when no frame comes to ask. */
    public void armBackstop(@NonNull Handler handler) {
        if (mReason != null) return;
        mTimer = handler;
        handler.postDelayed(mBackstopRun, mBackstopMs);
    }

    private void releaseByBackstop() {
        release(BACKSTOP, "ms=" + mBackstopMs);
    }
}
