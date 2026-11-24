package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * One touch on the Marks line, along its axis: {@link PageBarSlide}'s state machine over a
 * {@link MarksLine}'s cells. Down presses a slot; moving more than the touch slop along the line
 * starts a slide whose focus follows the finger (held at the ends); up taps (never slid) or commits
 * the focus, and sliding back to the slot (and member) the touch began on, or {@link #cancel()},
 * ends it with nothing chosen. Positions are along the line, from its start; the view maps x or y
 * (and the row's right-to-left mirror) to that. The cross axis is the view's: it never ends a
 * slide, because the bubble sits across it.
 */
public final class MarksSlide {

    @NonNull private final MarksLine mLine;
    private final float mSlopPx;

    @NonNull private PageBarSlide.State mState = PageBarSlide.State.IDLE;
    private float mDown;
    private float mPos;
    private int mStartSlot;
    private int mStartMember;
    private int mFocusSlot;
    private int mFocusMember;
    private boolean mFocusChanged;
    private int mEntryIndex = -1;

    public MarksSlide(@NonNull MarksLine line, float touchSlopPx) {
        mLine = line;
        mSlopPx = touchSlopPx;
    }

    @NonNull public PageBarSlide.State state() {
        return mState;
    }

    public int focusSlot() {
        return mFocusSlot;
    }

    public int focusMember() {
        return mFocusMember;
    }

    /** The finger's position along the line, for {@link MarksLine#focusAt}. */
    public float pos() {
        return mPos;
    }

    /** Whether the last event moved the focus to another slot or member: the haptic tick. */
    public boolean focusChanged() {
        return mFocusChanged;
    }

    /** The entry a TAP or COMMIT chose; -1 otherwise. */
    public int entryIndex() {
        return mEntryIndex;
    }

    public void down(float pos) {
        mState = PageBarSlide.State.PRESSED;
        mDown = pos;
        mPos = pos;
        mStartSlot = mLine.clampedSlotAt(pos);
        mStartMember = mLine.memberAt(pos, mStartSlot);
        mFocusSlot = mStartSlot;
        mFocusMember = mStartMember;
        mFocusChanged = false;
        mEntryIndex = -1;
    }

    @NonNull
    public PageBarSlide.Result move(float pos) {
        mFocusChanged = false;
        if (mState == PageBarSlide.State.IDLE) return PageBarSlide.Result.NONE;
        mPos = pos;
        if (mState == PageBarSlide.State.PRESSED && Math.abs(pos - mDown) > mSlopPx) {
            mState = PageBarSlide.State.SLIDING;
        }
        if (mState == PageBarSlide.State.SLIDING) {
            int slot = mLine.clampedSlotAt(pos);
            int member = mLine.memberAt(pos, slot);
            mFocusChanged = slot != mFocusSlot || member != mFocusMember;
            mFocusSlot = slot;
            mFocusMember = member;
        }
        return PageBarSlide.Result.NONE;
    }

    @NonNull
    public PageBarSlide.Result up(float pos) {
        mFocusChanged = false;
        if (mState == PageBarSlide.State.IDLE) return PageBarSlide.Result.NONE;
        if (mState == PageBarSlide.State.PRESSED) {
            mEntryIndex = mLine.tapEntryIndex(mStartSlot);
            return end(PageBarSlide.Result.TAP);
        }
        move(pos);
        if (mFocusSlot == mStartSlot && mFocusMember == mStartMember) {
            return end(PageBarSlide.Result.CANCEL);
        }
        mEntryIndex = mLine.entryIndex(mFocusSlot, mFocusMember);
        return end(PageBarSlide.Result.COMMIT);
    }

    @NonNull
    public PageBarSlide.Result cancel() {
        mFocusChanged = false;
        if (mState == PageBarSlide.State.IDLE) return PageBarSlide.Result.NONE;
        return end(PageBarSlide.Result.CANCEL);
    }

    private PageBarSlide.Result end(PageBarSlide.Result result) {
        mState = PageBarSlide.State.IDLE;
        return result;
    }
}
