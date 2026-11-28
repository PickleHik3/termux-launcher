package com.termux.app.tiling.navigation;

import androidx.annotation.NonNull;

/**
 * Navigation mode's one finger (#433; spec "Moving around"): what a touch on the stack is while
 * the mode is on. A finger that moves past the slop pans the stack and settles when it lifts; one
 * that lifts where it landed is a tap; one held still on a window for the long-press time picks
 * that window up, and its lift drops it.
 *
 * <p>Pure: the view feeds it the finger and a timer, and applies what it answers.
 */
public final class NavigationGesture {

    /** What the finger is doing. */
    public enum Phase {
        /** No finger. */
        IDLE,
        /** Down, still within the slop: a tap or a long press yet. */
        PENDING,
        /** Sliding the stack. */
        PANNING,
        /** Carrying a picked-up window. */
        CARRYING
    }

    /** What a lift or a cancel asks for. */
    public enum Outcome {
        /** Nothing. */
        NONE,
        /** A tap where the finger landed. */
        TAP,
        /** A pan ended: the view settles on the nearest window. */
        SETTLE,
        /** A carried window is let go where the finger is. */
        DROP,
        /** A carried window goes back where it was. */
        CANCEL
    }

    private final float mSlopSquared;
    private final long mLongPressMs;
    @NonNull private Phase mPhase = Phase.IDLE;
    private float mDownX;
    private float mDownY;
    private long mDownMs;
    private float mLastX;
    private float mLastY;
    private float mStepX;
    private float mStepY;

    /** A gesture that pans past {@code slopPx} and picks up after {@code longPressMs}. */
    public NavigationGesture(float slopPx, long longPressMs) {
        mSlopSquared = slopPx * slopPx;
        mLongPressMs = longPressMs;
    }

    @NonNull
    public Phase phase() {
        return mPhase;
    }

    /** A finger lands at ({@code x}, {@code y}). */
    public void down(float x, float y, long nowMs) {
        mPhase = Phase.PENDING;
        mDownX = x;
        mDownY = y;
        mDownMs = nowMs;
        mLastX = x;
        mLastY = y;
        mStepX = 0f;
        mStepY = 0f;
    }

    /** When a long press is due, in the clock {@link #down} was given. */
    public long longPressAt() {
        return mDownMs + mLongPressMs;
    }

    /**
     * The finger moved: past the slop a pending finger starts panning. The pan's step since the
     * last move is then {@link #stepX}, {@link #stepY}; the first step counts from where it landed.
     */
    @NonNull
    public Phase move(float x, float y, long nowMs) {
        mStepX = 0f;
        mStepY = 0f;
        if (mPhase == Phase.PENDING) {
            float dx = x - mDownX;
            float dy = y - mDownY;
            if (dx * dx + dy * dy > mSlopSquared) mPhase = Phase.PANNING;
        }
        if (mPhase == Phase.PANNING) {
            mStepX = x - mLastX;
            mStepY = y - mLastY;
            mLastX = x;
            mLastY = y;
        }
        return mPhase;
    }

    /** How far the last {@link #move} panned across, in px; 0 unless panning. */
    public float stepX() {
        return mStepX;
    }

    /** How far the last {@link #move} panned down, in px; 0 unless panning. */
    public float stepY() {
        return mStepY;
    }

    /**
     * The long-press timer ran at {@code nowMs}: a pending finger over a window
     * ({@code onWindow}) picks it up. True when it did.
     */
    public boolean longPress(long nowMs, boolean onWindow) {
        if (mPhase != Phase.PENDING || !onWindow || nowMs < longPressAt()) return false;
        mPhase = Phase.CARRYING;
        return true;
    }

    /** The finger lifts at ({@code x}, {@code y}). */
    @NonNull
    public Outcome up(float x, float y, long nowMs) {
        Phase was = mPhase;
        mPhase = Phase.IDLE;
        switch (was) {
            case PENDING: return Outcome.TAP;
            case PANNING: return Outcome.SETTLE;
            case CARRYING: return Outcome.DROP;
            default: return Outcome.NONE;
        }
    }

    /** The touch was taken away: a pan still settles, a carried window goes back. */
    @NonNull
    public Outcome cancel() {
        Phase was = mPhase;
        mPhase = Phase.IDLE;
        switch (was) {
            case PANNING: return Outcome.SETTLE;
            case CARRYING: return Outcome.CANCEL;
            default: return Outcome.NONE;
        }
    }
}
