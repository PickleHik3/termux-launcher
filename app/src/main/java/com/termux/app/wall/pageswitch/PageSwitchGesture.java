package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * The page switch panel's touch rules, as a state machine. The view feeds it panel-coordinate
 * events; it answers which cell is highlighted and what a release does. The page changes only on
 * a COMMIT result, never while sliding.
 *
 * <pre>
 * IDLE --down--> PRESSED --move past slop--> DRAGGING <--> CANCELLED (above the panel top)
 * PRESSED --up--> OPEN (STAY_OPEN)      DRAGGING --up--> COMMIT      CANCELLED --up--> CLOSE
 * OPEN --tapInPanel--> COMMIT           OPEN --tapOutside--> CLOSE   any --cancel--> CLOSE
 * </pre>
 */
public final class PageSwitchGesture {

    public enum State { IDLE, PRESSED, DRAGGING, CANCELLED, OPEN }

    public enum Result { NONE, COMMIT, CLOSE, STAY_OPEN }

    private final float mSlopPx;
    @NonNull private PageSwitchLayout mLayout;
    private float mPanelTop;
    private float mPanelBottom = Float.MAX_VALUE;

    @NonNull private State mState = State.IDLE;
    private float mDownX;
    private float mFingerX;
    private int mHighlight = -1;
    private int mCommitted = -1;
    private boolean mHighlightChanged;

    public PageSwitchGesture(@NonNull PageSwitchLayout layout, float slopPx) {
        mLayout = layout;
        mSlopPx = slopPx;
    }

    public void setLayout(@NonNull PageSwitchLayout layout) {
        mLayout = layout;
    }

    public void setPanelBounds(float top, float bottom) {
        mPanelTop = top;
        mPanelBottom = bottom;
    }

    @NonNull public State state() {
        return mState;
    }

    /** Finger down on an entry point: the panel opens with the current page highlighted. */
    public void down(float x, float y, int currentIndex) {
        mState = State.PRESSED;
        mDownX = x;
        mFingerX = x;
        mCommitted = -1;
        mHighlightChanged = false;
        mHighlight = currentIndex;
    }

    public void move(float x, float y) {
        if (mState == State.PRESSED) {
            if (Math.abs(x - mDownX) > mSlopPx) {
                mState = State.DRAGGING;
            } else {
                return;
            }
        }
        if (mState != State.DRAGGING && mState != State.CANCELLED) return;
        mFingerX = x;
        if (y < mPanelTop) {
            mState = State.CANCELLED;
            setHighlight(-1);
        } else {
            mState = State.DRAGGING;
            setHighlight(mLayout.indexAtX(x));
        }
    }

    public Result up(float x, float y) {
        switch (mState) {
            case PRESSED:
                mState = State.OPEN;
                return Result.STAY_OPEN;
            case DRAGGING:
            case CANCELLED:
                move(x, y);
                if (mState == State.CANCELLED) return finish(Result.CLOSE);
                mCommitted = mLayout.indexAtX(x);
                return finish(mCommitted < 0 ? Result.CLOSE : Result.COMMIT);
            default:
                return Result.NONE;
        }
    }

    /** ACTION_CANCEL or Back: close without switching. */
    public void cancel() {
        finish(Result.CLOSE);
    }

    /** In OPEN, a tap on the panel: commit the cell under it. */
    public Result tapInPanel(float x, float y) {
        if (mState != State.OPEN) return Result.NONE;
        mFingerX = x;
        mCommitted = mLayout.indexAtX(x);
        setHighlight(mCommitted);
        return finish(mCommitted < 0 ? Result.CLOSE : Result.COMMIT);
    }

    /** In OPEN, a tap outside the panel closes it. */
    public Result tapOutside() {
        if (mState != State.OPEN) return Result.NONE;
        return finish(Result.CLOSE);
    }

    private Result finish(Result r) {
        mState = State.IDLE;
        if (r != Result.COMMIT) mCommitted = -1;
        return r;
    }

    private void setHighlight(int index) {
        if (index != mHighlight) {
            mHighlight = index;
            mHighlightChanged = true;
        }
    }

    /** The cell committed by the last COMMIT result; -1 otherwise. */
    public int committedIndex() {
        return mCommitted;
    }

    /** -1 when nothing is highlighted, and once the gesture has ended. */
    public int highlightIndex() {
        return mState == State.IDLE ? -1 : mHighlight;
    }

    public float fingerX() {
        return mFingerX;
    }

    public boolean isTouching() {
        return mState == State.PRESSED || mState == State.DRAGGING || mState == State.CANCELLED;
    }

    /** True once after each change of highlight; the view plays the tick on it. */
    public boolean highlightChanged() {
        boolean c = mHighlightChanged;
        mHighlightChanged = false;
        return c;
    }
}
