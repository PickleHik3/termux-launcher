package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * A press held on a pane's border, then dragged sideways, which pages the wall. The one gesture
 * every place has for moving to the places beside it, in every mode.
 *
 * <p>The border is the line the page's frame draws — the terminal's frame, the Widgets page's
 * rim, the Display page's — and the band a thumb can find it in: {@link #BAND_DP} to either side
 * of each of the four edges, less the corner squares, which stay the corner tab's. A finger that
 * lands in the band is the content's until it has rested there for the hold: a tap reaches the
 * program under it, a drag that sets off before the hold is a scroll or a text selection or a
 * TUI's mouse drag, and a second finger is a pinch. Only a finger that holds still through the
 * hold claims the wall — from there to the lift its sideways travel drags the pages, whichever
 * edge it started on, and its release speed decides where the wall lands.</p>
 *
 * <p>The hold is timed by whoever owns the finger; this class only says whether a hold that has
 * elapsed may claim ({@link #holdElapsed}). Pure, so the band, the hold and the drag can be
 * tested without a view; one instance is reused across gestures, so a swipe allocates nothing.</p>
 */
public final class BorderDrag {

    /** How far to either side of a border a press still finds it, in dp: a thumb's half width. */
    public static final float BAND_DP = 24f;

    /** Which border a finger is on. */
    public enum Border { NONE, TOP, BOTTOM, LEFT, RIGHT }

    public enum Claim {
        /** No finger, or one that landed off the border. */
        NONE,
        /** Down on a border; waiting to see whether it holds still through the hold. */
        PENDING,
        /** The hold elapsed on a still finger: the wall's, from here to the lift. */
        PAGING,
        /** Moved, joined by a second finger, or taken from under: the content keeps this gesture. */
        ABANDONED
    }

    @NonNull private Claim mClaim = Claim.NONE;
    @NonNull private Border mBorder = Border.NONE;
    private float mDownX;
    private float mDownY;
    private float mSlop;

    /**
     * Which border of the frame {@code left, top, right, bottom} the point {@code (x, y)} presses,
     * reaching {@code bandPx} to either side of each edge, or {@link Border#NONE} for the
     * interior, the outside past the band, and the corner squares of side {@code cornerPx}
     * (measured in from each corner, and reaching out past it as far as the band does).
     */
    @NonNull
    public static Border borderAt(float x, float y, float left, float top, float right,
                                  float bottom, float bandPx, float cornerPx) {
        if (bandPx <= 0f || right <= left || bottom <= top) return Border.NONE;
        float band = Math.min(bandPx, Math.min(right - left, bottom - top) / 2f);
        if (x < left - band || x > right + band || y < top - band || y > bottom + band) {
            return Border.NONE;
        }
        float corner = Math.max(0f, cornerPx);
        boolean cornerColumn = x < left + corner || x > right - corner;
        boolean cornerRow = y < top + corner || y > bottom - corner;
        if (cornerColumn && cornerRow) return Border.NONE;
        if (y < top + band) return Border.TOP;
        if (y > bottom - band) return Border.BOTTOM;
        if (x < left + band) return Border.LEFT;
        if (x > right - band) return Border.RIGHT;
        return Border.NONE;
    }

    /**
     * The finger landed at {@code (x, y)}. Arms the drag when the point presses a border (see
     * {@link #borderAt}); otherwise the gesture is not this one and every later call is a no-op.
     *
     * @return whether the drag is armed
     */
    public boolean down(float x, float y, float left, float top, float right, float bottom,
                        float bandPx, float cornerPx, float slopPx) {
        mDownX = x;
        mDownY = y;
        mSlop = Math.max(0f, slopPx);
        mBorder = borderAt(x, y, left, top, right, bottom, bandPx, cornerPx);
        mClaim = mBorder == Border.NONE ? Claim.NONE : Claim.PENDING;
        return mClaim == Claim.PENDING;
    }

    /**
     * One move of the finger. Before the hold, travel past the slop in any direction gives the
     * gesture to the content for good: a finger that sets off is not holding. After the claim
     * the move is the drag's, and the claim never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy > mSlop * mSlop) mClaim = Claim.ABANDONED;
        return mClaim;
    }

    /**
     * The hold time passed. A finger that travelled or was joined has already given the gesture
     * up through {@link #move} or {@link #secondPointer}, so a still pending press is a still one.
     *
     * @return true when the wall just claimed the finger, and the content is owed a cancel
     */
    public boolean holdElapsed() {
        if (mClaim != Claim.PENDING) return false;
        mClaim = Claim.PAGING;
        return true;
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
     * {@code wall.go}, or the gestures were switched off): the rest of the gesture is swallowed
     * and moves nothing.
     */
    @NonNull
    public Claim abandon() {
        if (mClaim == Claim.PENDING || mClaim == Claim.PAGING) mClaim = Claim.ABANDONED;
        return mClaim;
    }

    /** The finger lifted or the stream was cancelled: back to nothing. */
    public void reset() {
        mClaim = Claim.NONE;
        mBorder = Border.NONE;
    }

    @NonNull
    public Claim claim() {
        return mClaim;
    }

    /** The border the finger landed on, from the DOWN to the lift; NONE off a border. */
    @NonNull
    public Border border() {
        return mBorder;
    }

    /** True from the DOWN on a border until the lift, whichever way the claim went. */
    public boolean isArmed() {
        return mClaim != Claim.NONE;
    }

    /** True while the wall owns the finger. */
    public boolean isPaging() {
        return mClaim == Claim.PAGING;
    }

    /**
     * What the wall is dragged by for a finger now at {@code x}: its sideways travel since the
     * DOWN, positive to the right, on every border alike. The wall only ever moves sideways, so a
     * drag along a side border still pages by how far it went across, and a finger that pulls
     * the right border leftward brings the place on the right in exactly as the top border does.
     */
    public float travel(float x) {
        return x - mDownX;
    }
}
