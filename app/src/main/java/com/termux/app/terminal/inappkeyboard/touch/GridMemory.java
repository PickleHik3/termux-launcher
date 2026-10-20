package com.termux.app.terminal.inappkeyboard.touch;

import java.util.Arrays;

/**
 * Everything learned about one rendered key grid: the {@link TapModel} that moves borders, the
 * {@link KeyLog} and {@link PassBarCounts} that only record, and the per-key rate limit on what
 * the model may learn.
 * The limit lives in memory only; it guards against a burst, not against history.
 */
public final class GridMemory {

    /**
     * Observations a key may learn from in one minute; the rest of that minute is ignored. Fast
     * typing hits the space bar 40 to 60 times a minute, so the cap sits well above that.
     */
    public static final int MAX_OBSERVATIONS_PER_KEY_PER_MINUTE = 90;
    private static final long MINUTE_MS = 60_000L;

    private final TapModel mModel;
    private final KeyLog mLog;
    private final PassBarCounts mCounts;
    private final long[] mMinute;
    private final int[] mUsed;

    public GridMemory(int keyCount) {
        this(new TapModel(keyCount), new KeyLog(keyCount), new PassBarCounts(keyCount));
    }

    public GridMemory(TapModel model, KeyLog log, PassBarCounts counts) {
        if (model.keyCount() != log.keyCount())
            throw new IllegalArgumentException("mismatched grid");
        mModel = model;
        mLog = log;
        mCounts = counts == null ? new PassBarCounts(model.keyCount()) : counts;
        mMinute = new long[model.keyCount()];
        Arrays.fill(mMinute, Long.MIN_VALUE);
        mUsed = new int[model.keyCount()];
    }

    public TapModel model() {
        return mModel;
    }

    public KeyLog log() {
        return mLog;
    }

    public PassBarCounts counts() {
        return mCounts;
    }

    public int keyCount() {
        return mModel.keyCount();
    }

    public boolean isEmpty() {
        return mModel.isEmpty() && mLog.isEmpty();
    }

    /** Whether key {@code key} may learn one more observation at {@code nowMs}; counts it. */
    boolean admit(int key, long nowMs) {
        if (key < 0 || key >= mUsed.length)
            return false;
        long minute = Math.floorDiv(nowMs, MINUTE_MS);
        if (mMinute[key] != minute) {
            mMinute[key] = minute;
            mUsed[key] = 0;
        }
        if (mUsed[key] >= MAX_OBSERVATIONS_PER_KEY_PER_MINUTE)
            return false;
        mUsed[key]++;
        return true;
    }
}
