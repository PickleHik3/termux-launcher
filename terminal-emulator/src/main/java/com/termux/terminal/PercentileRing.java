package com.termux.terminal;

import java.util.Arrays;

/**
 * The last N samples in a fixed ring, with p50, p90 and max over what it holds. Nothing is
 * allocated after construction, so it can sit on the keystroke path.
 */
public final class PercentileRing {

    private final long[] mSamples;
    private final long[] mScratch;
    private int mNext;
    private int mSize;

    public PercentileRing(int capacity) {
        mSamples = new long[capacity];
        mScratch = new long[capacity];
    }

    public void add(long sample) {
        mSamples[mNext] = sample;
        mNext = (mNext + 1) % mSamples.length;
        if (mSize < mSamples.length) mSize++;
    }

    public int size() {
        return mSize;
    }

    public void clear() {
        mNext = 0;
        mSize = 0;
    }

    /** Nearest-rank percentile, {@code p} in (0, 100]; 0 while empty. */
    public long percentile(int p) {
        if (mSize == 0) return 0L;
        System.arraycopy(mSamples, 0, mScratch, 0, mSize);
        Arrays.sort(mScratch, 0, mSize);
        int rank = (int) Math.ceil(p / 100.0 * mSize);
        return mScratch[Math.max(1, Math.min(mSize, rank)) - 1];
    }

    public long max() {
        long max = 0L;
        for (int i = 0; i < mSize; i++) max = Math.max(max, mSamples[i]);
        return max;
    }
}
