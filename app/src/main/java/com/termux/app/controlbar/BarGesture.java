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

    /** What a slide up raises (#437): decided by where the press started. */
    public enum Target {
        /** The Workspace list (#429): a slide up from anywhere on the bar. */
        WORKSPACE_LIST,
        /** The A–Z rail: a slide up that started on the Search tool. */
        AZ_RAIL
    }

    /**
     * What a slide up raises, given whether the press started on the Search tool: the A–Z rail
     * from search (its door), the Workspace list from everywhere else. Pure.
     */
    @NonNull
    public static Target targetFor(boolean startedOnSearch) {
        return startedOnSearch ? Target.AZ_RAIL : Target.WORKSPACE_LIST;
    }

    /**
     * A slide up that let go within this long of raising the Workspace list was a quick swipe, not
     * a slide to a row, in ms.
     */
    public static final long QUICK_SWIPE_MS = 250L;

    /**
     * Whether letting go of a slide up goes to the row under the finger. A quick swipe up that
     * began on the place badge (the Workspace chip) only opens the list, which stays up for a tap
     * (round 3: it switched Workspace); a slide that took its time chooses the row it ends on.
     */
    public static boolean releaseChooses(boolean startedOnBadge, long raisedForMs) {
        return !(startedOnBadge && raisedForMs < QUICK_SWIPE_MS);
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
