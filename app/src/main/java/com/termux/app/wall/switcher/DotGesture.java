package com.termux.app.wall.switcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a touch on the page switcher's dot means. A press opens the picker at touch-down; a slide
 * from the dot chooses the cell it is released on; a hold inside the slop lifts the dot to move
 * it; a touch that starts outside the dot while the picker is open chooses or closes. Pure: the
 * controller feeds it the touch and the cell under it and carries out the {@link Action}.
 */
public final class DotGesture {

    public enum Phase { IDLE, PRESS, SLIDE, MOVE, OUTSIDE }

    public enum Action { NONE, OPEN, TRACK, CHOOSE, CLOSE, STAY_OPEN, PICK_UP, DRAG, SNAP }

    /** How long a press inside the slop has to last before the dot lifts. */
    public static final long HOLD_MS = 400L;

    private final float mSlop;
    @NonNull private Phase mPhase = Phase.IDLE;
    private boolean mWasOpen;
    private float mStartX;
    private float mStartY;
    private float mLastX;
    private float mLastY;
    private float mOffsetX;
    private float mOffsetY;
    private float mDragX;
    private float mDragY;
    @Nullable private Object mDownCell;
    @Nullable private Object mChosen;

    public DotGesture(float slopPx) {
        mSlop = slopPx;
    }

    @NonNull public Phase phase() {
        return mPhase;
    }

    public boolean isLive() {
        return mPhase != Phase.IDLE;
    }

    /** The dot's centre while it is lifted: the finger plus the offset taken at pick-up. */
    public float dragX() {
        return mDragX;
    }

    public float dragY() {
        return mDragY;
    }

    /** The cell a {@link Action#CHOOSE} chose. */
    @Nullable public Object chosen() {
        return mChosen;
    }

    @NonNull
    public Action down(float x, float y, boolean onDot, boolean pickerOpen,
                       @Nullable Object cellUnder) {
        if (isLive()) return Action.NONE;
        mChosen = null;
        mStartX = x;
        mStartY = y;
        mLastX = x;
        mLastY = y;
        if (onDot) {
            mPhase = Phase.PRESS;
            mWasOpen = pickerOpen;
            return pickerOpen ? Action.NONE : Action.OPEN;
        }
        if (pickerOpen) {
            mPhase = Phase.OUTSIDE;
            mDownCell = cellUnder;
        }
        return Action.NONE;
    }

    @NonNull
    public Action move(float x, float y, @Nullable Object cellUnder) {
        mLastX = x;
        mLastY = y;
        switch (mPhase) {
            case PRESS: {
                float dx = x - mStartX;
                float dy = y - mStartY;
                if (dx * dx + dy * dy > mSlop * mSlop) {
                    mPhase = Phase.SLIDE;
                    return Action.TRACK;
                }
                return Action.NONE;
            }
            case SLIDE:
                return Action.TRACK;
            case MOVE:
                mDragX = x + mOffsetX;
                mDragY = y + mOffsetY;
                return Action.DRAG;
            default:
                return Action.NONE;
        }
    }

    @NonNull
    public Action holdElapsed(float dotCx, float dotCy) {
        if (mPhase != Phase.PRESS) return Action.NONE;
        mPhase = Phase.MOVE;
        mOffsetX = dotCx - mLastX;
        mOffsetY = dotCy - mLastY;
        mDragX = dotCx;
        mDragY = dotCy;
        return Action.PICK_UP;
    }

    @NonNull
    public Action up(float x, float y, @Nullable Object cellUnder) {
        Phase phase = mPhase;
        mPhase = Phase.IDLE;
        switch (phase) {
            case PRESS:
                return mWasOpen ? Action.CLOSE : Action.STAY_OPEN;
            case SLIDE:
                if (cellUnder != null) {
                    mChosen = cellUnder;
                    return Action.CHOOSE;
                }
                return Action.STAY_OPEN;
            case MOVE:
                return Action.SNAP;
            case OUTSIDE:
                if (cellUnder != null && cellUnder == mDownCell) {
                    mChosen = cellUnder;
                    return Action.CHOOSE;
                }
                if (cellUnder == null && mDownCell == null) return Action.CLOSE;
                return Action.NONE;
            default:
                return Action.NONE;
        }
    }

    @NonNull
    public Action cancel() {
        Phase phase = mPhase;
        mPhase = Phase.IDLE;
        switch (phase) {
            case MOVE:
                return Action.SNAP;
            case PRESS:
            case SLIDE:
                return mWasOpen ? Action.NONE : Action.CLOSE;
            default:
                return Action.NONE;
        }
    }
}
