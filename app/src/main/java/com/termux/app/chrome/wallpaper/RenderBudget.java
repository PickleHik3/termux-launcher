package com.termux.app.chrome.wallpaper;

import java.util.Arrays;

/**
 * The renderer's self-check (animated-wallpaper SPEC §9.4), pure: render times go in, and once
 * {@link #WINDOW} of them have arrived the p90 is tested against {@link #P90_LIMIT_MS}. The
 * window then starts over.
 */
final class RenderBudget {

    static final int WINDOW = 120;
    static final float P90_LIMIT_MS = 4f;

    private final float[] mSamples = new float[WINDOW];
    private final float[] mSorted = new float[WINDOW];
    private int mCount;
    private float mLastP90;

    /** Adds one render time; false when the window just completed with a p90 over the limit. */
    boolean add(float millis) {
        mSamples[mCount++] = millis;
        if (mCount < WINDOW) return true;
        mCount = 0;
        System.arraycopy(mSamples, 0, mSorted, 0, WINDOW);
        Arrays.sort(mSorted);
        mLastP90 = mSorted[(int) Math.ceil(WINDOW * 0.9) - 1];
        return mLastP90 <= P90_LIMIT_MS;
    }

    /** The p90 of the last completed window, 0 before the first. */
    float lastP90Ms() {
        return mLastP90;
    }
}
