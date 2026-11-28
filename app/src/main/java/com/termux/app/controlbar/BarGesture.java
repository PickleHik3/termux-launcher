package com.termux.app.controlbar;

import androidx.annotation.NonNull;

/**
 * Which way a finger on the control bar is going (#429), pure. The first movement past the slop
 * decides, once: mostly upward is the slide that raises the Workspace list, anything else is the
 * bar's own touch (the marks' sideways slide, a tap, a hold). The same threshold the marks' slide
 * uses ({@link MarkSlide}), so a finger is never both.
 */
public final class BarGesture {

    /** What the finger has turned out to be doing. */
    public enum Kind {
        /** It has not moved past the slop yet. */
        UNDECIDED,
        /** It is sliding up: the Workspace list. */
        SLIDE_UP,
        /** It moved some other way: the bar's own touch. */
        OTHER
    }

    private final float mSlop;
    private float mDownX;
    private float mDownY;
    private Kind mKind = Kind.UNDECIDED;

    public BarGesture(float slopPx) {
        mSlop = Math.max(0f, slopPx);
    }

    /** A finger landed at ({@code x}, {@code y}). */
    public void down(float x, float y) {
        mDownX = x;
        mDownY = y;
        mKind = Kind.UNDECIDED;
    }

    /** The finger moved to ({@code x}, {@code y}); the kind it is now. */
    @NonNull
    public Kind move(float x, float y) {
        if (mKind != Kind.UNDECIDED) return mKind;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (Math.hypot(dx, dy) <= mSlop) return mKind;
        mKind = -dy > Math.abs(dx) ? Kind.SLIDE_UP : Kind.OTHER;
        return mKind;
    }

    @NonNull
    public Kind kind() {
        return mKind;
    }

    public void reset() {
        mKind = Kind.UNDECIDED;
    }
}
