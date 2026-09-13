package com.termux.view;

/**
 * The hold that aims a click. On a terminal that is tracking the mouse a finger held still stops
 * being a tap and becomes an aim: the cell under it is shown magnified instead of being clicked,
 * dragging moves the aim without typing anything at the program, and the lift clicks wherever the
 * aim ended.
 *
 * <p>It is a window between two behaviours that already existed, and gives way to both of them: a
 * flick before {@link #AIM_DELAY_MS} is a scroll, and a still hold that reaches the long-press
 * timeout is text selection.
 *
 * <p>Pure by design — the view feeds it a touch stream and reads back where to draw and what the
 * lift meant.
 */
final class AimState {

    /** How long a finger must stay still before the aim opens, well short of a long press. */
    static final long AIM_DELAY_MS = 150L;

    enum Phase {
        /** No finger, or one whose gesture is over. */
        IDLE,
        /** A finger is down and the aim will open if it holds still long enough. */
        PENDING,
        /** The aim is open: the view is drawing it and the lift will click it. */
        AIMING,
        /** Something else claimed this gesture — a scroll, a long press, a second finger. */
        CANCELLED
    }

    private Phase mPhase = Phase.IDLE;

    private float mDownX, mDownY;

    private float mAimX, mAimY;

    private float mSlop;

    /** A finger landed. The aim is pending until the delay elapses or the finger travels. */
    void down(float x, float y, float slopPixels) {
        mPhase = Phase.PENDING;
        mDownX = mAimX = x;
        mDownY = mAimY = y;
        mSlop = slopPixels;
    }

    /**
     * The aim delay elapsed. A finger that already travelled has cancelled itself through
     * {@link #move(float, float)} by now, so a still pending hold is a still one.
     *
     * @return true when the aim just opened.
     */
    boolean delayElapsed() {
        if (mPhase != Phase.PENDING)
            return false;
        mPhase = Phase.AIMING;
        return true;
    }

    /**
     * The finger moved. Before the aim opens that is the start of a scroll and gives the gesture
     * up; once it is open the move only carries the aim along.
     *
     * @return true when the aim moved, and the view owes a repaint.
     */
    boolean move(float x, float y) {
        if (mPhase == Phase.PENDING) {
            float dx = x - mDownX;
            float dy = y - mDownY;
            if (dx * dx + dy * dy > mSlop * mSlop)
                mPhase = Phase.CANCELLED;
            return false;
        }
        if (mPhase != Phase.AIMING || (x == mAimX && y == mAimY))
            return false;
        mAimX = x;
        mAimY = y;
        return true;
    }

    /** Something else claimed the gesture — the long press, a second finger, a lost emulator. */
    void cancel() {
        if (mPhase == Phase.PENDING || mPhase == Phase.AIMING)
            mPhase = Phase.CANCELLED;
    }

    /**
     * The finger lifted, ending the gesture either way.
     *
     * @return true when the lift should click the aimed cell.
     */
    boolean lift() {
        boolean click = mPhase == Phase.AIMING;
        mPhase = Phase.IDLE;
        return click;
    }

    /** Forget the last gesture entirely, which is what the next finger down does. */
    void reset() {
        mPhase = Phase.IDLE;
    }

    boolean isAiming() {
        return mPhase == Phase.AIMING;
    }

    boolean isPending() {
        return mPhase == Phase.PENDING;
    }

    Phase phase() {
        return mPhase;
    }

    float aimX() {
        return mAimX;
    }

    float aimY() {
        return mAimY;
    }
}
