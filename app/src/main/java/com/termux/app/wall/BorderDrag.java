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
 * <p>The bottom border carries one more gesture, the keyboard's: a clear vertical swipe that
 * starts on it — past the slop, more up or down than sideways, before the hold — is claimed at
 * once ({@link Claim#KEYBOARD}), and its release opens the keyboard on the way up and closes it
 * on the way down ({@link #keyboardRelease}). It is the one way to the keyboard that every place
 * and every mode shares. Its band reaches the whole {@link #BAND_DP} out past the line, where
 * there is only the page's air, but only {@link #KEYBOARD_REACH_DP} in: it claims a moving finger
 * rather than a still one, so the content keeps everything above that. A sideways start is still
 * the content's, and a hold is still the wall's.</p>
 *
 * <p>The hold is timed by whoever owns the finger; this class only says whether a hold that has
 * elapsed may claim ({@link #holdElapsed}). Pure, so the band, the hold and the drag can be
 * tested without a view; one instance is reused across gestures, so a swipe allocates nothing.</p>
 */
public final class BorderDrag {

    /** How far to either side of a border a press still finds it, in dp: a thumb's half width. */
    public static final float BAND_DP = 24f;

    /**
     * How far inside the bottom line a press may start the keyboard swipe, in dp; outside the
     * line it reaches the whole {@link #BAND_DP}. About one terminal row — the pane's inner gap
     * and the lower part of its last row — so a scroll that starts any higher is never taken.
     */
    public static final float KEYBOARD_REACH_DP = 16f;

    /** How far a keyboard swipe has to travel to open or close the keyboard, in dp. */
    public static final float KEYBOARD_COMMIT_DP = 32f;

    /** How fast a shorter keyboard swipe has to be flicked to count, in dp per second. */
    public static final float KEYBOARD_FLING_DP_PER_SEC = 500f;

    /** Which border a finger is on. */
    public enum Border { NONE, TOP, BOTTOM, LEFT, RIGHT }

    public enum Claim {
        /** No finger, or one that landed off the border. */
        NONE,
        /** Down on a border; waiting to see whether it holds still through the hold. */
        PENDING,
        /** The hold elapsed on a still finger: the wall's, from here to the lift. */
        PAGING,
        /**
         * A vertical swipe that set off from the bottom border before the hold: the keyboard's,
         * from here to the lift, where {@link #keyboardRelease} says which way it went.
         */
        KEYBOARD,
        /** Moved, joined by a second finger, or taken from under: the content keeps this gesture. */
        ABANDONED
    }

    /** What a keyboard swipe's release asks for. */
    public enum KeyboardSwipe { NONE, OPEN, CLOSE }

    @NonNull private Claim mClaim = Claim.NONE;
    @NonNull private Border mBorder = Border.NONE;
    private float mDownX;
    private float mDownY;
    private float mSlop;
    /** Whether the hold may page: a wall of one place has nowhere to go. */
    private boolean mCanPage;
    /** Whether this press may become the keyboard swipe: it landed in the bottom band. */
    private boolean mKeyboardEligible;

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
     * The hold may page, and there is no keyboard swipe.
     *
     * @return whether the drag is armed
     */
    public boolean down(float x, float y, float left, float top, float right, float bottom,
                        float bandPx, float cornerPx, float slopPx) {
        return down(x, y, left, top, right, bottom, bandPx, cornerPx, slopPx, true, 0f);
    }

    /**
     * The same, saying what the press may become.
     *
     * @param canPage whether the hold may page the wall; without it only the keyboard swipe can
     *                claim, and a press off the bottom band arms nothing
     * @param keyboardReachPx how far inside the bottom line a press may start the keyboard swipe
     *                        (outside the line the band reaches {@code bandPx}); 0 for none
     * @return whether the press is armed
     */
    public boolean down(float x, float y, float left, float top, float right, float bottom,
                        float bandPx, float cornerPx, float slopPx, boolean canPage,
                        float keyboardReachPx) {
        mDownX = x;
        mDownY = y;
        mSlop = Math.max(0f, slopPx);
        mCanPage = canPage;
        mBorder = borderAt(x, y, left, top, right, bottom, bandPx, cornerPx);
        mKeyboardEligible = mBorder == Border.BOTTOM && keyboardReachPx > 0f
            && y >= bottom - keyboardReachPx;
        boolean armed = mBorder != Border.NONE && (canPage || mKeyboardEligible);
        if (!armed) mBorder = Border.NONE;
        mClaim = armed ? Claim.PENDING : Claim.NONE;
        return armed;
    }

    /**
     * One move of the finger. Before the hold, travel past the slop gives the gesture away for
     * good: a finger that sets off is not holding. Sideways, or from anywhere but the bottom band,
     * it goes to the content; up or down from the bottom band it is the keyboard's. After a claim
     * the move is the claimant's, and the claim never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy <= mSlop * mSlop) return mClaim;
        mClaim = mKeyboardEligible && Math.abs(dy) > Math.abs(dx)
            ? Claim.KEYBOARD : Claim.ABANDONED;
        return mClaim;
    }

    /**
     * The hold time passed. A finger that travelled or was joined has already given the gesture
     * up through {@link #move} or {@link #secondPointer}, so a still pending press is a still one.
     * Where the hold may not page, a still finger is the content's — a long press, a selection —
     * and a later vertical move can no longer turn into the keyboard swipe.
     *
     * @return true when the wall just claimed the finger, and the content is owed a cancel
     */
    public boolean holdElapsed() {
        if (mClaim != Claim.PENDING) return false;
        if (!mCanPage) {
            mClaim = Claim.ABANDONED;
            return false;
        }
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
        if (mClaim == Claim.PENDING || mClaim == Claim.PAGING || mClaim == Claim.KEYBOARD) {
            mClaim = Claim.ABANDONED;
        }
        return mClaim;
    }

    /** The finger lifted or the stream was cancelled: back to nothing. */
    public void reset() {
        mClaim = Claim.NONE;
        mBorder = Border.NONE;
        mKeyboardEligible = false;
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

    /** True while the keyboard swipe owns the finger. */
    public boolean isKeyboardSwipe() {
        return mClaim == Claim.KEYBOARD;
    }

    /**
     * True from a DOWN in the keyboard swipe's band — the bottom border, within
     * {@link #KEYBOARD_REACH_DP} inside the line — until the lift, whichever way the claim went.
     */
    public boolean isKeyboardEligible() {
        return mKeyboardEligible;
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

    /**
     * What a keyboard swipe released at {@code y}, moving at {@code velocityYPxPerSec} (positive
     * downward), asks for: {@link KeyboardSwipe#OPEN} for one that went up, {@link
     * KeyboardSwipe#CLOSE} for one that went down, each once it has travelled {@code commitPx} or
     * is flicked on the same way at {@code flingPxPerSec}. A release short of both, or flicked
     * back against its own travel, asks for nothing; so does any gesture that is not a keyboard
     * swipe.
     */
    @NonNull
    public KeyboardSwipe keyboardRelease(float y, float velocityYPxPerSec, float commitPx,
                                         float flingPxPerSec) {
        if (mClaim != Claim.KEYBOARD) return KeyboardSwipe.NONE;
        float dy = y - mDownY;
        if (dy == 0f) return KeyboardSwipe.NONE;
        boolean up = dy < 0f;
        // The release speed along the swipe's own travel; negative is a flick back.
        float along = up ? -velocityYPxPerSec : velocityYPxPerSec;
        float fling = Math.max(0f, flingPxPerSec);
        if (along <= -fling && fling > 0f) return KeyboardSwipe.NONE;
        boolean far = Math.abs(dy) >= commitPx;
        boolean flung = fling > 0f && along >= fling;
        if (!far && !flung) return KeyboardSwipe.NONE;
        return up ? KeyboardSwipe.OPEN : KeyboardSwipe.CLOSE;
    }
}
