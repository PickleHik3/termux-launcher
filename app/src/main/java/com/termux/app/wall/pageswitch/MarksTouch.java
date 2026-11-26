package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One touch on the marks row, along its axis: {@link PageBarSlide}'s state machine over the
 * {@link MarksTouchLayout} made at touch-down. Down presses the anchor slot, and the focus exists
 * from then on; moving more than the touch slop along the line starts a slide whose focus follows
 * the finger (held at the ends); up taps (never slid) or commits the focus, and sliding back to the
 * slot (and member) the touch began on, or {@link #cancel()}, ends it with nothing chosen.
 * Positions are along the line, from its start; the view maps x or y (and the right-to-left mirror)
 * to that. The cross axis is the view's: it never ends a slide.
 */
public final class MarksTouch {

    /** The entry a TAP or COMMIT chose when it is the Overview cell. */
    public static final int OVERVIEW_ENTRY = MarksRowLayout.OVERVIEW_ENTRY;

    @NonNull private final MarksRowLayout mRest;
    private final float mDensity;
    private final float mSlopPx;

    @NonNull private PageBarSlide.State mState = PageBarSlide.State.IDLE;
    @Nullable private MarksTouchLayout mLayout;
    private float mDown;
    private float mPos;
    private int mStartSlot;
    private int mStartMember;
    private int mFocusSlot;
    private int mFocusMember;
    private boolean mFocusChanged;
    private int mEntryIndex = -1;

    public MarksTouch(@NonNull MarksRowLayout rest, float density, float touchSlopPx) {
        mRest = rest;
        mDensity = density;
        mSlopPx = touchSlopPx;
    }

    @NonNull public PageBarSlide.State state() {
        return mState;
    }

    /** The layout made at the last down, held to the lift; null before the first down. */
    @Nullable public MarksTouchLayout layout() {
        return mLayout;
    }

    public int focusSlot() {
        return mFocusSlot;
    }

    public int focusMember() {
        return mFocusMember;
    }

    /** The finger's position along the line, for {@link MarksTouchLayout#scaleAt}. */
    public float pos() {
        return mPos;
    }

    /** Whether the last event moved the focus to another slot or member: the haptic tick. */
    public boolean focusChanged() {
        return mFocusChanged;
    }

    /** The entry a TAP or COMMIT chose, {@link #OVERVIEW_ENTRY} for Overview; -1 otherwise. */
    public int entryIndex() {
        return mEntryIndex;
    }

    public void down(float pos) {
        mLayout = new MarksTouchLayout(mRest, mDensity, pos, mRest.clampedSlotAt(pos));
        mState = PageBarSlide.State.PRESSED;
        mDown = pos;
        mPos = pos;
        mStartSlot = mLayout.anchorSlot();
        mStartMember = mLayout.anchorMember();
        mFocusSlot = mStartSlot;
        mFocusMember = mStartMember;
        mFocusChanged = false;
        mEntryIndex = -1;
    }

    @NonNull
    public PageBarSlide.Result move(float pos) {
        mFocusChanged = false;
        if (mState == PageBarSlide.State.IDLE || mLayout == null) return PageBarSlide.Result.NONE;
        mPos = pos;
        if (mState == PageBarSlide.State.PRESSED && Math.abs(pos - mDown) > mSlopPx) {
            mState = PageBarSlide.State.SLIDING;
        }
        if (mState == PageBarSlide.State.SLIDING) {
            int slot = mLayout.clampedSlotAt(pos);
            int member = mLayout.memberAt(pos, slot);
            mFocusChanged = slot != mFocusSlot || member != mFocusMember;
            mFocusSlot = slot;
            mFocusMember = member;
        }
        return PageBarSlide.Result.NONE;
    }

    @NonNull
    public PageBarSlide.Result up(float pos) {
        mFocusChanged = false;
        if (mState == PageBarSlide.State.IDLE || mLayout == null) return PageBarSlide.Result.NONE;
        if (mState == PageBarSlide.State.PRESSED) {
            mEntryIndex = mLayout.entryIndex(mStartSlot, mStartMember);
            return end(PageBarSlide.Result.TAP);
        }
        move(pos);
        if (mFocusSlot == mStartSlot && mFocusMember == mStartMember) {
            return end(PageBarSlide.Result.CANCEL);
        }
        mEntryIndex = mLayout.entryIndex(mFocusSlot, mFocusMember);
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
