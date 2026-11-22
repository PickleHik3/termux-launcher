package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * One touch on the page bar. Down presses a slot; moving past the touch slop sideways starts a
 * slide whose focus follows the finger over the rest slots; up taps (never slid) or commits the
 * focus. Sliding back to where the touch began, leaving the pill vertically by more than
 * {@link PageBarLayout#CANCEL_DP} (the key entry only above), or {@link #cancel()} ends it with
 * nothing chosen. After a result the touch is over until the next down. A TAP or COMMIT on the
 * current page carries a CLOSE target, so the host just closes.
 */
public final class PageBarSlide {

    public enum State { IDLE, PRESSED, SLIDING }

    public enum Result {
        /** Nothing to do. */
        NONE,
        /** A tap: go to {@link #entryIndex()}. */
        TAP,
        /** A slide ended on {@link #entryIndex()}. */
        COMMIT,
        /** Nothing chosen: close. */
        CANCEL
    }

    @NonNull private final PageBarLayout mLayout;
    @NonNull private final List<PageBarFold.Slot> mSlots;
    @NonNull private final List<PageBarModel.Entry> mEntries;
    private final float mSlopPx;
    private final boolean mKeyEntry;

    @NonNull private State mState = State.IDLE;
    private float mDownX;
    private float mX;
    private int mStartSlot;
    private int mStartMember;
    private int mFocusSlot;
    private int mFocusMember;
    private boolean mFocusChanged;
    private int mEntryIndex = -1;

    /** @param keyEntry the bar popped over the extra keys: only leaving above cancels. */
    public PageBarSlide(@NonNull PageBarLayout layout, @NonNull PageBarFold.Result fold,
                        @NonNull List<PageBarModel.Entry> entries, float touchSlopPx,
                        boolean keyEntry) {
        mLayout = layout;
        mSlots = fold.slots;
        mEntries = entries;
        mSlopPx = touchSlopPx;
        mKeyEntry = keyEntry;
    }

    @NonNull public State state() {
        return mState;
    }

    public int focusSlot() {
        return mFocusSlot;
    }

    /** The finger's x, for {@link PageBarLayout#focusAt}. */
    public float x() {
        return mX;
    }

    /** Whether the last event moved the focus to another slot or member: the haptic tick. */
    public boolean focusChanged() {
        return mFocusChanged;
    }

    /** The entry a TAP or COMMIT chose. */
    public int entryIndex() {
        return mEntryIndex;
    }

    public void down(float x, float y) {
        mState = State.PRESSED;
        mDownX = x;
        mX = x;
        mStartSlot = mLayout.slotAt(x);
        mStartMember = memberAt(x, mStartSlot);
        mFocusSlot = mStartSlot;
        mFocusMember = mStartMember;
        mFocusChanged = false;
        mEntryIndex = -1;
    }

    @NonNull
    public Result move(float x, float y) {
        mFocusChanged = false;
        if (mState == State.IDLE) return Result.NONE;
        if (outside(y)) return end(Result.CANCEL);
        mX = x;
        if (mState == State.PRESSED && Math.abs(x - mDownX) > mSlopPx) mState = State.SLIDING;
        if (mState == State.SLIDING) {
            int slot = mLayout.slotAt(x);
            int member = memberAt(x, slot);
            mFocusChanged = slot != mFocusSlot || member != mFocusMember;
            mFocusSlot = slot;
            mFocusMember = member;
        }
        return Result.NONE;
    }

    @NonNull
    public Result up(float x, float y) {
        mFocusChanged = false;
        if (mState == State.IDLE) return Result.NONE;
        if (outside(y)) return end(Result.CANCEL);
        if (mState == State.PRESSED) {
            mEntryIndex = tapEntry(mStartSlot);
            return end(Result.TAP);
        }
        move(x, y);
        if (mFocusSlot == mStartSlot && mFocusMember == mStartMember) return end(Result.CANCEL);
        mEntryIndex = mSlots.get(mFocusSlot).first + mFocusMember;
        return end(Result.COMMIT);
    }

    @NonNull
    public Result cancel() {
        mFocusChanged = false;
        if (mState == State.IDLE) return Result.NONE;
        return end(Result.CANCEL);
    }

    private Result end(Result result) {
        mState = State.IDLE;
        return result;
    }

    private boolean outside(float y) {
        float cancel = mLayout.cancelPx();
        if (y < mLayout.pillTopPx() - cancel) return true;
        return !mKeyEntry && y > mLayout.pillBottomPx() + cancel;
    }

    private int memberAt(float x, int slot) {
        PageBarFold.Slot s = mSlots.get(slot);
        return s.folded() ? mLayout.subIndexAt(x, slot, s.count) : 0;
    }

    /** A folded slot's tap: the current member, else the window you would return to, else the first. */
    private int tapEntry(int slotIndex) {
        PageBarFold.Slot s = mSlots.get(slotIndex);
        if (!s.folded()) return s.first;
        if (s.currentMember >= 0) return s.first + s.currentMember;
        for (int m = 0; m < s.count; m++) {
            if (mEntries.get(s.first + m).returnTo) return s.first + m;
        }
        return s.first;
    }
}
