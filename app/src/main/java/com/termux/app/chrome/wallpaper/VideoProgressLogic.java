package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * The Video page's import progress as plain state: idle, copying, converting a GIF (a fraction),
 * then done or failed. Pure so the transitions and the label text are unit-tested.
 */
public final class VideoProgressLogic {

    public enum Phase { IDLE, COPYING, CONVERTING, APPLYING, DONE, ERROR }

    /** The least time between two bar updates. */
    public static final long MIN_UPDATE_MS = 100;

    private Phase mPhase = Phase.IDLE;
    private float mFraction;
    private long mLastUpdateMs;
    /** The rate whose version is being made (0: a GIF conversion or nothing). */
    private int mRateHz;
    /** A version is being made on its own (not part of an import): the rate pills stay usable. */
    private boolean mPreparing;

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    /** Controls wait and the bar shows while copying or converting. */
    public boolean busy() {
        return mPhase == Phase.COPYING || mPhase == Phase.CONVERTING || mPhase == Phase.APPLYING;
    }

    public boolean indeterminate() {
        return mPhase == Phase.COPYING || mPhase == Phase.APPLYING;
    }

    /** The import began. */
    public void start() {
        mPhase = Phase.COPYING;
        mFraction = 0f;
        mLastUpdateMs = 0;
        mRateHz = 0;
        mPreparing = false;
    }

    /** What an import makes: the version for rateHz (a video), or 0 for a GIF's conversion. */
    public void setImportRate(int rateHz) {
        mRateHz = rateHz;
    }

    /** A version for rateHz is being made from the stored clip: a determinate bar from 0. */
    public void startPreparing(int rateHz) {
        mPhase = Phase.CONVERTING;
        mFraction = 0f;
        mLastUpdateMs = 0;
        mRateHz = rateHz;
        mPreparing = true;
    }

    /** True while a version is being made on its own; the rate pills stay enabled then. */
    public boolean preparing() {
        return mPreparing && busy();
    }

    /** The wallpaper is being set (after an import, or Back to photo): never goes back to copying. */
    public void startApplying() {
        mPhase = Phase.APPLYING;
        mFraction = 0f;
    }

    /**
     * A progress callback. Returns true when the page should redraw: the first value above 0
     * always does, later ones at most every {@link #MIN_UPDATE_MS} (reaching 100% always does).
     * Ignored outside an import.
     */
    public boolean onProgress(float fraction, long nowMs) {
        if ((mPhase != Phase.COPYING && mPhase != Phase.CONVERTING) || !(fraction > 0f)) return false;
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
        mPreparing = false;
        mPhase = ok ? Phase.DONE : Phase.ERROR;
    }

    /** The line under the bar; empty when nothing is running. */
    @NonNull
    public String label(@NonNull String copying, @NonNull String convertingFormat, @NonNull String applying) {
        return label(copying, convertingFormat, applying, convertingFormat);
    }

    /** As above; a version being made reads preparingFormat (rate %1$d, percent %2$d). */
    @NonNull
    public String label(@NonNull String copying, @NonNull String convertingFormat, @NonNull String applying,
                        @NonNull String preparingFormat) {
        if (mPhase == Phase.CONVERTING && mRateHz > 0 && !preparingFormat.equals(convertingFormat)) {
            return String.format(preparingFormat, mRateHz, percent());
        }
        switch (mPhase) {
            case COPYING:
                return copying;
            case CONVERTING:
                return String.format(convertingFormat, percent());
            case APPLYING:
                return applying;
            default:
                return "";
        }
    }

    /** The whole-number percent, 0 to 100. */
    public int percent() {
        return Math.round(Math.max(0f, Math.min(1f, mFraction)) * 100f);
    }
}
