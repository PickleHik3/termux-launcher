package com.termux.app.chrome.wallpaper;

import java.util.Arrays;

/**
 * The renderer's self-check (animated-wallpaper SPEC §9.4), pure: render times go in, and once
 * {@link #WINDOW} of them have arrived the p90 is tested against {@link #P90_LIMIT_MS}. The
 * window then starts over.
 *
 * <p>Warm-up: after {@link #restartWarmup} (a clock start, a renderer build, a screen-on or an
 * unlock) renders are ignored until both {@link #WARMUP_MS} have passed and
 * {@link #WARMUP_RENDERS} renders have arrived, whichever is later. Those first renders queue
 * behind the unlock's UI work, run at low GPU clocks and carry the first shader compile, so they
 * say nothing about whether the device keeps up.</p>
 */
final class RenderBudget {

    static final int WINDOW = 120;
    static final float P90_LIMIT_MS = 4f;
    /** Renders ignored for at least this long after a warm-up restart... */
    static final long WARMUP_MS = 2_000L;
    /** ...and for at least this many renders; the later of the two ends the warm-up. */
    static final int WARMUP_RENDERS = 60;

    private final float[] mSamples = new float[WINDOW];
    private final float[] mSorted = new float[WINDOW];
    private int mCount;
    private float mLastP90;
    private float mLastP50;
    private boolean mClosed;

    private boolean mWarming;
    private long mWarmUntilMs;
    private int mWarmSkipped;
    private int mWindowWarmSkipped;

    /** Starts (or restarts) the warm-up and drops the partial window. */
    void restartWarmup(long nowMs) {
        mWarming = true;
        mWarmUntilMs = nowMs + WARMUP_MS;
        mWarmSkipped = 0;
        mCount = 0;
    }

    /** True while renders are still being ignored. */
    boolean warmingUp(long nowMs) {
        return mWarming && (mWarmSkipped < WARMUP_RENDERS || nowMs < mWarmUntilMs);
    }

    /** Adds one render time with no warm-up in force; see {@link #add(float, long)}. */
    boolean add(float millis) {
        return add(millis, Long.MAX_VALUE);
    }

    /**
     * Adds one render time; false when the window just completed with a p90 over the limit. A
     * render inside the warm-up is not counted and returns true.
     *
     * @param nowMs a monotonic clock in milliseconds, the same one {@link #restartWarmup} got
     */
    boolean add(float millis, long nowMs) {
        mClosed = false;
        if (mWarming) {
            if (mWarmSkipped < WARMUP_RENDERS || nowMs < mWarmUntilMs) {
                mWarmSkipped++;
                return true;
            }
            mWarming = false;
            mWindowWarmSkipped = mWarmSkipped;
            mWarmSkipped = 0;
        }
        mSamples[mCount++] = millis;
        if (mCount < WINDOW) return true;
        mCount = 0;
        mClosed = true;
        System.arraycopy(mSamples, 0, mSorted, 0, WINDOW);
        Arrays.sort(mSorted);
        mLastP90 = mSorted[(int) Math.ceil(WINDOW * 0.9) - 1];
        mLastP50 = mSorted[(int) Math.ceil(WINDOW * 0.5) - 1];
        return mLastP90 <= P90_LIMIT_MS;
    }

    /** True when the last {@link #add} completed a window. */
    boolean windowClosed() {
        return mClosed;
    }

    /** The p90 of the last completed window, 0 before the first. */
    float lastP90Ms() {
        return mLastP90;
    }

    /** The p50 of the last completed window, 0 before the first. */
    float lastP50Ms() {
        return mLastP50;
    }

    /** How many renders the warm-up ignored before the first window of this run, 0 if none. */
    int warmupSkipped() {
        return mWindowWarmSkipped;
    }
}
