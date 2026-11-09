package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * The Video page's import progress as plain state: idle, copying, converting a GIF (a fraction),
 * then done or failed. Pure so the transitions and the label text are unit-tested.
 */
public final class VideoProgressLogic {

    public enum Phase { IDLE, COPYING, CONVERTING, DONE, ERROR }

    /** The least time between two bar updates. */
    public static final long MIN_UPDATE_MS = 100;

    private Phase mPhase = Phase.IDLE;
    private float mFraction;
    private long mLastUpdateMs;

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    /** Controls wait and the bar shows while copying or converting. */
    public boolean busy() {
        return mPhase == Phase.COPYING || mPhase == Phase.CONVERTING;
    }

    public boolean indeterminate() {
        return mPhase == Phase.COPYING;
    }

    /** The import began. */
    public void start() {
        mPhase = Phase.COPYING;
        mFraction = 0f;
        mLastUpdateMs = 0;
    }

    /**
     * A progress callback. Returns true when the page should redraw: the first value above 0
     * always does, later ones at most every {@link #MIN_UPDATE_MS} (reaching 100% always does).
     * Ignored outside an import.
     */
    public boolean onProgress(float fraction, long nowMs) {
        if (!busy() || !(fraction > 0f)) return false;
        float f = Math.min(1f, fraction);
        boolean first = mPhase == Phase.COPYING;
        mPhase = Phase.CONVERTING;
        if (!first && f < 1f && nowMs - mLastUpdateMs < MIN_UPDATE_MS) return false;
        if (!first && f == mFraction) return false;
        mFraction = f;
        mLastUpdateMs = nowMs;
        return true;
    }

    public void finish(boolean ok) {
        mPhase = ok ? Phase.DONE : Phase.ERROR;
    }

    /** The line under the bar; empty when nothing is running. */
    @NonNull
    public String label(@NonNull String copying, @NonNull String convertingFormat) {
        switch (mPhase) {
            case COPYING:
                return copying;
            case CONVERTING:
                return String.format(convertingFormat, percent());
            default:
                return "";
        }
    }

    /** The whole-number percent, 0 to 100. */
    public int percent() {
        return Math.round(Math.max(0f, Math.min(1f, mFraction)) * 100f);
    }
}
