package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.window.Hold;

import java.util.function.LongSupplier;

/**
 * The system keyboard's two answers (ADR 0030 section 5), with no view in them.
 *
 * <p>Ownership: the system keyboard's insets are this activity's to act on only after a flow here
 * asked for it ({@link #request}) and the keyboard showed ({@link #onInsets}), or once an IME
 * animation ran in this window ({@link #onAnimationStart()}, which Android dispatches only to the
 * window the keyboard serves). A request whose keyboard never comes backs off by its hold's
 * backstop, so the gate cannot stay open.</p>
 *
 * <p>The ride: while the keyboard animates, the stack is drawn at the animated keyboard's height
 * instead of the end state the layout answered for, so the dock and rows rise and sink with it
 * frame by frame ({@link #offsetPx}).</p>
 */
public final class SystemImeRide {

    /** How long a request waits for its keyboard: a slow keyboard's first show on an old phone. */
    public static final long BACKSTOP_MS = 1500L;

    public static final int NONE = 0;
    public static final int WAITING = 1;
    public static final int OWNED = 2;

    private int mState = NONE;
    @Nullable private Hold mHold;
    @Nullable private Runnable mOnEnded;

    private boolean mRunning;
    private int mLaidForPx;
    private int mAnimatedPx;

    /** Runs {@code run} when a backstop or {@link #release} ends a waiting request. */
    public void onWaitEnded(@Nullable Runnable run) {
        mOnEnded = run;
    }

    /**
     * An in-activity flow asks for the system keyboard. Returns the hold the activity arms its
     * backstop on, or null when the ride already owns the keyboard.
     */
    @Nullable
    public Hold request(@NonNull LongSupplier clock) {
        if (mState == OWNED) return null;
        Hold old = mHold;
        mHold = null;
        if (old != null) old.release(Hold.LANDING, "replaced");
        Hold hold = Hold.take("system-ime", BACKSTOP_MS, Hold.NO_FRAME_LIMIT, clock);
        mHold = hold;
        mState = WAITING;
        hold.onRelease(() -> {
            if (mHold != hold) return;
            mHold = null;
            if (mState == WAITING) {
                mState = NONE;
                Runnable then = mOnEnded;
                if (then != null) then.run();
            }
        });
        return hold;
    }

    /** The keyboard's visibility in the insets the layout is answering. */
    public void onInsets(boolean imeVisible) {
        if (mState == WAITING && imeVisible) own();
    }

    /** A keyboard animation began in this window: the window is the one the keyboard serves. */
    public void onAnimationStart() {
        if (mState != OWNED) own();
    }

    private void own() {
        mState = OWNED;
        Hold hold = mHold;
        if (hold != null) hold.signal();
    }

    /** Drops ownership: what arrives next is inherited, not ours. */
    public void release() {
        mState = NONE;
        Hold hold = mHold;
        if (hold != null) hold.release("released", null);
        mHold = null;
    }

    public int state() {
        return mState;
    }

    public boolean owns() {
        return mState == OWNED;
    }

    // ---- The ride ---------------------------------------------------------------------------

    /** An animation began; {@code laidForImePx} is the keyboard height the layout last answered for. */
    public void onAnimationStart(int laidForImePx) {
        mRunning = true;
        mLaidForPx = laidForImePx;
        mAnimatedPx = laidForImePx;
    }

    public void onAnimationProgress(int animatedImePx) {
        mAnimatedPx = animatedImePx;
    }

    public void onAnimationEnd() {
        mRunning = false;
    }

    /**
     * The stack's lift this frame: negative while the keyboard shows (drawn lower than the laid-out
     * end state), positive while it hides, 0 when no animation runs.
     */
    public int offsetPx() {
        return rideOffsetPx(mRunning, mAnimatedPx, mLaidForPx);
    }

    public static int rideOffsetPx(boolean running, int animatedPx, int laidForPx) {
        return running ? animatedPx - laidForPx : 0;
    }
}
