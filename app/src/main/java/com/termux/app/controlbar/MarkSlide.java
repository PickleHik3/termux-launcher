package com.termux.app.controlbar;

import androidx.annotation.NonNull;

/**
 * A finger on the control bar's marks (#424), pure: a tap goes to the mark it landed on; once it
 * has moved past the slop it slides, and every mark it reaches on the way is gone to once. Holding
 * a mark (#425) and sliding up (#429) are other tickets' and not read here.
 */
public final class MarkSlide {

    private final float mSlop;
    private MarkStrip mStrip;
    private boolean mDown;
    private boolean mSliding;
    private float mDownX;
    private int mDownIndex = MarkStrip.NONE;
    private int mLast = MarkStrip.NONE;

    public MarkSlide(float slopPx) {
        mSlop = Math.max(0f, slopPx);
    }

    /** A finger landed at {@code x} along {@code strip}; true when it is on a mark, and is taken. */
    public boolean down(@NonNull MarkStrip strip, float x) {
        int index = strip.indexAt(x);
        mDown = index != MarkStrip.NONE;
        mStrip = mDown ? strip : null;
        mSliding = false;
        mDownX = x;
        mDownIndex = index;
        mLast = index;
        return mDown;
    }

    /** The finger moved to {@code x}: the mark to go to now, or {@link MarkStrip#NONE}. */
    public int move(float x) {
        if (!mDown) return MarkStrip.NONE;
        if (!mSliding && Math.abs(x - mDownX) <= mSlop) return MarkStrip.NONE;
        mSliding = true;
        int index = mStrip.nearestIndex(x);
        if (index == mLast) return MarkStrip.NONE;
        mLast = index;
        return index;
    }

    /** The finger lifted: the mark a tap goes to, or {@link MarkStrip#NONE} after a slide. */
    public int up() {
        int tap = mDown && !mSliding ? mDownIndex : MarkStrip.NONE;
        cancel();
        return tap;
    }

    public void cancel() {
        mDown = false;
        mSliding = false;
        mStrip = null;
        mDownIndex = MarkStrip.NONE;
        mLast = MarkStrip.NONE;
    }

    /** Whether the finger is sliding along the marks. */
    public boolean sliding() {
        return mSliding;
    }
}
