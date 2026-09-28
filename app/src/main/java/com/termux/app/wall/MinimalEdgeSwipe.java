package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * A sideways swipe along a minimal place's pane edge, which pages the wall.
 *
 * <p>In minimal mode the pane is the whole screen and the status bar is a strip too thin to find
 * without looking, so the pane lends the wall a band along its top and its bottom edge: a swipe
 * that <em>starts</em> in the band and travels sideways drags the wall exactly as a swipe along
 * the bar does. Everything else stays the content's — a swipe that starts lower is a TUI's mouse
 * drag or a text selection, and one that turns downward before it turns sideways is a scroll —
 * so the band claims nothing until the finger has moved past the slop with more sideways travel
 * than vertical, and gives the gesture up for good the moment vertical travel wins.</p>
 *
 * <p>The corner squares are left out of the band. A finger down in one is the corner tab's until
 * its hold fires or abandons it, and the pane's overlay forbids interception for the whole of that
 * gesture on the DOWN, so a swipe from a corner could never reach the wall anyway; excluding them
 * makes the band's shape the truth rather than an accident of dispatch.</p>
 *
 * <p>Pure: the layout feeds it the DOWN and every MOVE in its own coordinates and reads the claim
 * back. One instance is reused across gestures, so a swipe allocates nothing.</p>
 */
public final class MinimalEdgeSwipe {

    /** How far in from the pane's top and bottom edge the band reaches, in dp: a thumb's width. */
    public static final float BAND_DP = 32f;

    public enum Claim {
        /** No finger, or one that landed outside the band. */
        NONE,
        /** Down in the band; waiting for the slop to say which way the finger is going. */
        PENDING,
        /** Sideways past the slop: the wall's drag, from here to the lift. */
        PAGING,
        /** Vertical past the slop, or a second finger: the content keeps this gesture. */
        ABANDONED
    }

    @NonNull private Claim mClaim = Claim.NONE;
    private float mDownX;
    private float mDownY;
    private float mSlop;

    /**
     * Whether {@code (x, y)} lies in the band along the top or the bottom edge of the frame
     * {@code left, top, right, bottom}, outside the corner squares of side {@code cornerPx}.
     */
    public static boolean inBand(float x, float y, float left, float top, float right, float bottom,
                                 float bandPx, float cornerPx) {
        if (bandPx <= 0f || right <= left || bottom <= top) return false;
        float band = Math.min(bandPx, (bottom - top) / 2f);
        float corner = Math.max(0f, cornerPx);
        if (x < left + corner || x > right - corner) return false;
        if (y < top || y > bottom) return false;
        return y < top + band || y > bottom - band;
    }

    /**
     * The finger landed at {@code (x, y)}. Arms the swipe when the point is in the band (see
     * {@link #inBand}); otherwise the gesture is not this one and every later call is a no-op.
     *
     * @return whether the swipe is armed
     */
    public boolean down(float x, float y, float left, float top, float right, float bottom,
                        float bandPx, float cornerPx, float slopPx) {
        mDownX = x;
        mDownY = y;
        mSlop = Math.max(0f, slopPx);
        mClaim = inBand(x, y, left, top, right, bottom, bandPx, cornerPx)
            ? Claim.PENDING : Claim.NONE;
        return mClaim == Claim.PENDING;
    }

    /**
     * One move of the finger. A pending swipe decides on the first move past the slop: sideways
     * travel that beats the vertical is the wall's, anything else is the content's. A decided
     * claim never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = Math.abs(x - mDownX);
        float dy = Math.abs(y - mDownY);
        if (dx <= mSlop && dy <= mSlop) return mClaim;
        mClaim = dx > dy ? Claim.PAGING : Claim.ABANDONED;
        return mClaim;
    }

    /**
     * A second finger landed. Before the claim it settles the question — two fingers are the
     * content's pinch or scroll, never a page — and after it changes nothing: the wall is already
     * moving under the first finger and its lift still ends the drag.
     */
    @NonNull
    public Claim secondPointer() {
        if (mClaim == Claim.PENDING) mClaim = Claim.ABANDONED;
        return mClaim;
    }

    /**
     * Something else took the wall from under this finger (the wall was moved by a tile, a key,
     * {@code wall.go}): the rest of the gesture is swallowed and moves nothing.
     */
    @NonNull
    public Claim abandon() {
        if (mClaim == Claim.PENDING || mClaim == Claim.PAGING) mClaim = Claim.ABANDONED;
        return mClaim;
    }

    /** The finger lifted or the stream was cancelled: back to nothing. */
    public void reset() {
        mClaim = Claim.NONE;
    }

    @NonNull
    public Claim claim() {
        return mClaim;
    }

    /** True from the DOWN in the band until the lift, whichever way the claim went. */
    public boolean isArmed() {
        return mClaim != Claim.NONE;
    }

    /** Sideways travel since the DOWN, positive to the right: what the wall is dragged by. */
    public float travel(float x) {
        return x - mDownX;
    }
}
