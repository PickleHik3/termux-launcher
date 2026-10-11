package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * A press on a pane's border that pages the wall: slid sideways off the top or bottom border, or
 * held on any border and then dragged. The one gesture every place has for moving to the places
 * beside it, in every mode.
 *
 * <p>The border lives outside the page. Its zone ({@link Zone}) is the strip between each edge of
 * the page — the terminal, the Widgets page, the Display page — and the wall's own edge on that
 * side: the frame line drawn there and the air beyond it, up to whatever stands next to the wall
 * (the status bar, the dock or the keyboard, the screen's edge). Which border a press is on is
 * which page edge it lies beyond. The page's text is never the zone, so a tap, a long press or a
 * mouse click on the terminal's first or last row or column is always the terminal's. The left and
 * right zones reach {@link #SIDE_REACH_DP} over the page's edge, into the clearance a pane keeps
 * before its text, so the narrow side borders are not missed. And where the air on a side is
 * thinner than {@link #MIN_BAND_DP} (a page almost flush with the wall), that side's zone reaches
 * into the page by the shortfall, so the gestures never vanish at Margin 0; a side reaches in by
 * the larger of the two, never their sum. The corner squares are left out, from the page's
 * corners out through the zone: they are the corner tabs'.
 *
 * <p>A finger that lands in the zone claims nothing at once. One that holds still through the hold
 * claims the wall — from there to the lift its sideways travel drags the pages, whichever edge it
 * started on, and its release speed decides where the wall lands. One joined by a second finger
 * gives the gesture up, and where the zone reaches into the page that leaves it with the content,
 * as a pinch. What a finger that sets off sideways before the hold does depends on where it
 * landed:
 * <ul>
 *   <li>On the top or bottom border, out in the air beyond the page, with another place to go
 *       ({@link #slidePages}): the wall is claimed at once, the same drag a hold would have begun,
 *       measured from where the claim was made. Nothing there is the content's, and the phone's
 *       back swipe is on the side edges, not these.</li>
 *   <li>Anywhere else — the left and right borders, which share their edge with the phone's back
 *       swipe and reach over the page's edge, the minimum band where it reaches into the page, the
 *       boxes by the pills, and the phone's own strip at the top of the screen: the gesture is
 *       given up, so a scroll, a selection or a TUI's drag inside the page stays the content's.
 *       Only the hold pages from there.</li>
 * </ul>
 *
 * <p>The bottom border carries one more gesture, the keyboard's: a clear vertical swipe that
 * starts in its zone — past the slop, more up or down than sideways, before the hold — is claimed
 * at once ({@link Claim#KEYBOARD}), and its release opens the keyboard on the way up and closes it
 * on the way down ({@link #keyboardRelease}). It is the one way to the keyboard that every place
 * and every mode shares.
 *
 * <p>The top border carries its mirror, the status bar's: the same clear vertical swipe from the
 * top zone, before the hold, is claimed as {@link Claim#STATUS}, and drives the bar's fold — down
 * unfolds it, up folds it ({@link #statusRelease}). Never above {@code statusTopLimit}: the
 * phone's own pull for its notification shade starts in the strip at the top of the screen, and
 * a press there is never taken for the bar, nor for a sideways slide.
 *
 * <p>The air alone is a thin target, so both swipes also start near their grabber pills
 * ({@link BorderGrabber}), about a row into the page: a box {@link #PILL_SWIPE_WIDTH_DP} wide,
 * centred on the pill, reaching {@link #PILL_SWIPE_REACH_DP} in from the page's bottom edge for
 * the keyboard and from its top edge for the status bar ({@link #pillAt}). Only the vertical swipe
 * is taken there. The hold never pages from it, so a still finger is the content's long press, and
 * a tap, a mouse click or a sideways drag in it reach the content as anywhere else on the page.
 *
 * <p>The hold is timed by whoever owns the finger; this class only says whether a hold that has
 * elapsed may claim ({@link #holdElapsed}). Pure, so the zone, the hold and the drag can be
 * tested without a view; one instance is reused across gestures, so a swipe allocates nothing.</p>
 */
public final class BorderDrag {

    /**
     * The thinnest a border's zone ever gets, in dp. Where the air between a page edge and the
     * wall's edge is thinner than this (Margin set near 0), the zone reaches into the page by the
     * shortfall, so the border can still be found; at the default Margin nothing reaches in.
     */
    public static final float MIN_BAND_DP = 6f;

    /**
     * How far the left and right zones reach over the page's edge, in dp, at any Margin: inside
     * the clearance a pane keeps between its edge and its text (the rim's stroke and 2dp more),
     * so the side borders are found without ever taking a character. Where the minimum band
     * reaches further in, it is the reach; the two never add.
     */
    public static final float SIDE_REACH_DP = 2f;

    /**
     * How wide the box by each grabber pill is, in dp, where the keyboard's or the status bar's
     * swipe also starts inside the page ({@link Zone#pills}). Never wider than the page between
     * its corner squares.
     */
    public static final float PILL_SWIPE_WIDTH_DP = 120f;

    /**
     * How far inside the page's edge the box by each grabber pill reaches, in dp: about a row of
     * the terminal at its usual size.
     */
    public static final float PILL_SWIPE_REACH_DP = 16f;

    /** How far a keyboard swipe has to travel to open or close the keyboard, in dp. */
    public static final float KEYBOARD_COMMIT_DP = 32f;

    /** How fast a shorter keyboard swipe has to be flicked to count, in dp per second. */
    public static final float KEYBOARD_FLING_DP_PER_SEC = 500f;

    /** Which border a finger is on. */
    public enum Border { NONE, TOP, BOTTOM, LEFT, RIGHT }

    public enum Claim {
        /** No finger, or one that landed off the border. */
        NONE,
        /** Down on a border; waiting to see whether it holds still, or which way it sets off. */
        PENDING,
        /**
         * The hold elapsed on a still finger, or a finger that may slide ({@link #slidePages}) set
         * off sideways: the wall's, from here to the lift.
         */
        PAGING,
        /**
         * A vertical swipe that set off from the bottom border before the hold: the keyboard's,
         * from here to the lift, where {@link #keyboardRelease} says which way it went.
         */
        KEYBOARD,
        /**
         * A vertical swipe that set off from the top border before the hold: the status bar's,
         * from here to the lift, where {@link #statusRelease} says which way it went.
         */
        STATUS,
        /** Moved, joined by a second finger, or taken from under: the content keeps this gesture. */
        ABANDONED
    }

    /** What a keyboard swipe's release asks for. */
    public enum KeyboardSwipe { NONE, OPEN, CLOSE }

    /** What a status bar swipe's release asks for: the bar unfolded, or folded. */
    public enum StatusSwipe { NONE, EXPAND, COLLAPSE }

    /**
     * Where the borders are: the page's edges, the air outside each of them, the minimum band, the
     * corner squares and the boxes by the grabber pills. Mutable so the owner can keep one and
     * refill it on every press.
     */
    public static final class Zone {
        float mLeft;
        float mTop;
        float mRight;
        float mBottom;
        float mGapLeft;
        float mGapTop;
        float mGapRight;
        float mGapBottom;
        float mMinBand;
        float mSideReach;
        float mCorner;
        float mPillCentreX;
        float mPillWidth;
        float mPillReach;
        /** Scratch for {@link #inPill}, so a press allocates nothing. */
        private final float[] mPillBox = new float[4];

        /** The page's edges, as it stands now (its slide applied). */
        @NonNull
        public Zone page(float left, float top, float right, float bottom) {
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            return this;
        }

        /**
         * The air outside each edge, from the page's edge out to the wall's edge on that side: how
         * far out each border's zone runs, and, below {@link #minBand}, how much it reaches in.
         */
        @NonNull
        public Zone gaps(float left, float top, float right, float bottom) {
            mGapLeft = Math.max(0f, left);
            mGapTop = Math.max(0f, top);
            mGapRight = Math.max(0f, right);
            mGapBottom = Math.max(0f, bottom);
            return this;
        }

        /** The thinnest a zone gets, in px ({@link #MIN_BAND_DP}). */
        @NonNull
        public Zone minBand(float px) {
            mMinBand = Math.max(0f, px);
            return this;
        }

        /** How far the left and right zones reach over the page's edge, in px ({@link #SIDE_REACH_DP}). */
        @NonNull
        public Zone sideReach(float px) {
            mSideReach = Math.max(0f, px);
            return this;
        }

        /** The corner squares' side, in px, measured in from the page's corners. */
        @NonNull
        public Zone corner(float px) {
            mCorner = Math.max(0f, px);
            return this;
        }

        /**
         * The boxes by the grabber pills, where a vertical swipe also starts inside the page:
         * centred sideways on {@code centreX}, the pills' centre as drawn (both pills sit on the
         * same page, so they share it), {@code width} px wide and reaching {@code reach} px in from
         * the page's bottom and top edges. A width or reach of 0, the default, leaves no box.
         */
        @NonNull
        public Zone pills(float centreX, float width, float reach) {
            mPillCentreX = centreX;
            mPillWidth = Math.max(0f, width);
            mPillReach = Math.max(0f, reach);
            return this;
        }

        /**
         * How far inside the page's edge on {@code side} its zone reaches: the shortfall of that
         * side's air against the minimum band, or on the left and right the side reach where that
         * is further; 0 on the top and bottom wherever the air is at least the band, and never
         * past the middle of the page.
         */
        public float inside(@NonNull Border side) {
            float gap;
            float half;
            float reach = 0f;
            switch (side) {
                case LEFT: gap = mGapLeft; half = (mRight - mLeft) / 2f; reach = mSideReach; break;
                case RIGHT: gap = mGapRight; half = (mRight - mLeft) / 2f; reach = mSideReach; break;
                case TOP: gap = mGapTop; half = (mBottom - mTop) / 2f; break;
                case BOTTOM: gap = mGapBottom; half = (mBottom - mTop) / 2f; break;
                default: return 0f;
            }
            return Math.max(0f, Math.min(Math.max(mMinBand - gap, reach), half));
        }

        /**
         * The zone along {@code side} as {@code left, top, right, bottom} in {@code out}, less the
         * corner squares: from the wall's edge to the page's, and in by {@link #inside}. False,
         * with {@code out} untouched, where that side has no zone.
         */
        public boolean bounds(@NonNull Border side, @NonNull float[] out) {
            if (mRight <= mLeft || mBottom <= mTop) return false;
            float corner = cornerSize();
            float in = inside(side);
            float l;
            float t;
            float r;
            float b;
            switch (side) {
                case LEFT:
                    l = mLeft - mGapLeft; r = mLeft + in; t = mTop + corner; b = mBottom - corner;
                    break;
                case RIGHT:
                    l = mRight - in; r = mRight + mGapRight; t = mTop + corner; b = mBottom - corner;
                    break;
                case TOP:
                    l = mLeft + corner; r = mRight - corner; t = mTop - mGapTop; b = mTop + in;
                    break;
                case BOTTOM:
                    l = mLeft + corner; r = mRight - corner; t = mBottom - in;
                    b = mBottom + mGapBottom;
                    break;
                default:
                    return false;
            }
            if (r <= l || b <= t) return false;
            out[0] = l;
            out[1] = t;
            out[2] = r;
            out[3] = b;
            return true;
        }

        /**
         * The box by the pill on {@code side} ({@link Border#BOTTOM} or {@link Border#TOP}) as
         * {@code left, top, right, bottom} in {@code out}: inside the page, from its edge in by the
         * reach (never past the middle of the page), and between the corner squares. False, with
         * {@code out} untouched, where there is no box.
         */
        public boolean pillBounds(@NonNull Border side, @NonNull float[] out) {
            if (mRight <= mLeft || mBottom <= mTop) return false;
            if (!(mPillWidth > 0f) || !(mPillReach > 0f)) return false;
            float corner = cornerSize();
            float l = Math.max(mLeft + corner, mPillCentreX - mPillWidth / 2f);
            float r = Math.min(mRight - corner, mPillCentreX + mPillWidth / 2f);
            float reach = Math.min(mPillReach, (mBottom - mTop) / 2f);
            float t;
            float b;
            switch (side) {
                case TOP: t = mTop; b = mTop + reach; break;
                case BOTTOM: t = mBottom - reach; b = mBottom; break;
                default: return false;
            }
            if (r <= l || b <= t) return false;
            out[0] = l;
            out[1] = t;
            out[2] = r;
            out[3] = b;
            return true;
        }

        /** Whether {@code (x, y)} lies in the box by the pill on {@code side}. */
        boolean inPill(@NonNull Border side, float x, float y) {
            float[] box = mPillBox;
            return pillBounds(side, box)
                && x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
        }

        /** The corner squares' side, never more than half of the page either way. */
        private float cornerSize() {
            return Math.min(mCorner, Math.min(mRight - mLeft, mBottom - mTop) / 2f);
        }
    }

    @NonNull private Claim mClaim = Claim.NONE;
    @NonNull private Border mBorder = Border.NONE;
    private float mDownX;
    private float mDownY;
    private float mSlop;
    /** Whether the hold may page: the wall has another place to go. */
    private boolean mHoldPages;
    /**
     * Whether a sideways start pages at once, without the hold: the top or bottom border, out in
     * the air beyond the page and below the phone's own strip, on a wall that can page.
     */
    private boolean mSlidePages;
    /** Where the wall's drag is measured from: the DOWN, or where a sideways start claimed it. */
    private float mDragOriginX;
    /** Whether this press may become the keyboard swipe: it landed in the bottom zone. */
    private boolean mKeyboardEligible;
    /** Whether this press may become the status bar's swipe: it landed in the top zone. */
    private boolean mStatusEligible;

    /**
     * Which border the point {@code (x, y)} presses in {@code zone}: the page edge it lies beyond,
     * within that side's air, or within that side's reach inside the edge ({@link Zone#inside}). {@link Border#NONE} for the page itself, anything past the air,
     * and the corner squares, which run from the page's corners out through the zone.
     */
    @NonNull
    public static Border borderAt(float x, float y, @NonNull Zone zone) {
        float left = zone.mLeft;
        float top = zone.mTop;
        float right = zone.mRight;
        float bottom = zone.mBottom;
        if (right <= left || bottom <= top) return Border.NONE;
        if (x < left - zone.mGapLeft || x > right + zone.mGapRight
            || y < top - zone.mGapTop || y > bottom + zone.mGapBottom) {
            return Border.NONE;
        }
        float corner = zone.cornerSize();
        boolean cornerColumn = x < left + corner || x >= right - corner;
        boolean cornerRow = y < top + corner || y >= bottom - corner;
        if (cornerColumn && cornerRow) return Border.NONE;
        // A page's first pixel is at its left and top edges and its last just short of its right
        // and bottom ones, so a press is beyond the right or bottom edge from the edge itself on.
        if (y < top + zone.inside(Border.TOP)) return Border.TOP;
        if (y >= bottom - zone.inside(Border.BOTTOM)) return Border.BOTTOM;
        if (x < left + zone.inside(Border.LEFT)) return Border.LEFT;
        if (x >= right - zone.inside(Border.RIGHT)) return Border.RIGHT;
        return Border.NONE;
    }

    /**
     * Which pill's box ({@link Zone#pills}) the point {@code (x, y)} lies in, inside the page:
     * {@link Border#BOTTOM} for the keyboard's, {@link Border#TOP} for the status bar's, and
     * {@link Border#NONE} anywhere else. Like the page itself, a box holds its left and top edges
     * and stops just short of its right and bottom ones.
     */
    @NonNull
    public static Border pillAt(float x, float y, @NonNull Zone zone) {
        if (zone.inPill(Border.BOTTOM, x, y)) return Border.BOTTOM;
        if (zone.inPill(Border.TOP, x, y)) return Border.TOP;
        return Border.NONE;
    }

    /**
     * The finger landed at {@code (x, y)}. Arms the drag when the point presses a border of
     * {@code zone} (see {@link #borderAt}) that the press can do something on, or lies in a pill's
     * box ({@link #pillAt}) whose swipe is on; otherwise the gesture is not this one and every
     * later call is a no-op. A press in a pill's box may only become that pill's swipe: its hold
     * never pages, so a still finger there is given up to the content at the hold. A press on the
     * top or bottom border outside the page may also page by setting off sideways
     * ({@link #slidePages}).
     *
     * @param canPage whether the hold may page the wall: it has another place to go
     * @param keyboardSwipe whether a vertical swipe from the bottom border is the keyboard's
     * @param statusSwipe whether a vertical swipe from the top border folds the status bar
     * @param statusTopLimit the foot of the phone's own gesture strip at the top of the screen, in
     *                       the same coordinates ({@link Float#NEGATIVE_INFINITY} for none): no
     *                       swipe, the status bar's or a sideways one, starts above it
     * @return whether the press is armed
     */
    public boolean down(float x, float y, @NonNull Zone zone, float slopPx, boolean canPage,
                        boolean keyboardSwipe, boolean statusSwipe, float statusTopLimit) {
        mDownX = x;
        mDownY = y;
        mDragOriginX = x;
        mSlop = Math.max(0f, slopPx);
        mBorder = borderAt(x, y, zone);
        mHoldPages = canPage && mBorder != Border.NONE;
        // Out in the air only: where the band reaches into the page, the content keeps its drags.
        boolean beyondPage = mBorder == Border.TOP ? y < zone.mTop
            : mBorder == Border.BOTTOM && y >= zone.mBottom;
        mSlidePages = mHoldPages && beyondPage && !(y < statusTopLimit);
        // Off the border, the pills' boxes inside the page carry the vertical swipes alone.
        Border swipeSide = mBorder != Border.NONE ? mBorder : pillAt(x, y, zone);
        mKeyboardEligible = keyboardSwipe && swipeSide == Border.BOTTOM;
        mStatusEligible = statusSwipe && swipeSide == Border.TOP && !(y < statusTopLimit);
        boolean armed = mHoldPages || mKeyboardEligible || mStatusEligible;
        mBorder = !armed ? Border.NONE : mHoldPages ? mBorder : swipeSide;
        mClaim = armed ? Claim.PENDING : Claim.NONE;
        return armed;
    }

    /**
     * One move of the finger. Before the hold, travel past the slop decides: a finger that sets
     * off is not holding. Up or down from the bottom zone it is the keyboard's, and from the top
     * zone the status bar's; sideways from where a slide pages ({@link #slidePages}) it is the
     * wall's at once, its drag measured from here so the wall does not jump by the slop. Any other
     * start is given up. After a claim the move is the claimant's, and the claim never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy <= mSlop * mSlop) return mClaim;
        boolean vertical = Math.abs(dy) > Math.abs(dx);
        if (vertical) {
            mClaim = mKeyboardEligible ? Claim.KEYBOARD
                : mStatusEligible ? Claim.STATUS : Claim.ABANDONED;
        } else if (mSlidePages) {
            mClaim = Claim.PAGING;
            mDragOriginX = x;
        } else {
            mClaim = Claim.ABANDONED;
        }
        return mClaim;
    }

    /**
     * The hold time passed. A finger that travelled or was joined has already given the gesture
     * up through {@link #move} or {@link #secondPointer}, so a still pending press is a still one.
     * Where the hold may not page — a wall of one place, or a press in a pill's box — a still
     * finger is given up, and inside the page it is the content's long press. A later vertical
     * move can then no longer turn into the keyboard's or the status bar's swipe.
     *
     * @return true when the wall just claimed the finger, and the content is owed a cancel
     */
    public boolean holdElapsed() {
        if (mClaim != Claim.PENDING) return false;
        if (!mHoldPages) {
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
        if (mClaim == Claim.PENDING || mClaim == Claim.PAGING || mClaim == Claim.KEYBOARD
            || mClaim == Claim.STATUS) {
            mClaim = Claim.ABANDONED;
        }
        return mClaim;
    }

    /** The finger lifted or the stream was cancelled: back to nothing. */
    public void reset() {
        mClaim = Claim.NONE;
        mBorder = Border.NONE;
        mHoldPages = false;
        mSlidePages = false;
        mKeyboardEligible = false;
        mStatusEligible = false;
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

    /**
     * True from a DOWN whose hold may page the wall — on a border, with another place to go —
     * until the lift, whichever way the claim went.
     */
    public boolean holdPages() {
        return mHoldPages;
    }

    /**
     * True from a DOWN whose sideways start pages at once — the top or bottom border, beyond the
     * page, below the phone's strip, with another place to go — until the lift, whichever way the
     * claim went. Every such press also {@link #holdPages}.
     */
    public boolean slidePages() {
        return mSlidePages;
    }

    /** True while the wall owns the finger. */
    public boolean isPaging() {
        return mClaim == Claim.PAGING;
    }

    /** True while the keyboard swipe owns the finger. */
    public boolean isKeyboardSwipe() {
        return mClaim == Claim.KEYBOARD;
    }

    /** True while the status bar's swipe owns the finger. */
    public boolean isStatusSwipe() {
        return mClaim == Claim.STATUS;
    }

    /**
     * True from a DOWN in the status bar swipe's zone — the top border or the box by its pill,
     * below the phone's own strip — until the lift, whichever way the claim went.
     */
    public boolean isStatusEligible() {
        return mStatusEligible;
    }

    /** The finger's vertical travel since the DOWN, positive downward. */
    public float verticalTravel(float y) {
        return y - mDownY;
    }

    /**
     * True from a DOWN in the keyboard swipe's zone — the bottom border or the box by its pill —
     * until the lift, whichever way the claim went.
     */
    public boolean isKeyboardEligible() {
        return mKeyboardEligible;
    }

    /**
     * What the wall is dragged by for a finger now at {@code x}: its sideways travel since the
     * DOWN — or, for a sideways start that claimed the wall, since that claim — positive to the
     * right, on every border alike. The wall only ever moves sideways, so a drag along a side
     * border still pages by how far it went across, and a finger that pulls the right border
     * leftward brings the place on the right in exactly as the top border does.
     */
    public float travel(float x) {
        return x - mDragOriginX;
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
        int way = verticalRelease(y, velocityYPxPerSec, commitPx, flingPxPerSec);
        return way < 0 ? KeyboardSwipe.OPEN : way > 0 ? KeyboardSwipe.CLOSE : KeyboardSwipe.NONE;
    }

    /**
     * What a status bar swipe released at {@code y} asks for, by the keyboard's own rule
     * ({@link #keyboardRelease}) mirrored: {@link StatusSwipe#EXPAND} for one that went down, away
     * from the bar, and {@link StatusSwipe#COLLAPSE} for one that went up, into it.
     */
    @NonNull
    public StatusSwipe statusRelease(float y, float velocityYPxPerSec, float commitPx,
                                     float flingPxPerSec) {
        if (mClaim != Claim.STATUS) return StatusSwipe.NONE;
        int way = verticalRelease(y, velocityYPxPerSec, commitPx, flingPxPerSec);
        return way > 0 ? StatusSwipe.EXPAND : way < 0 ? StatusSwipe.COLLAPSE : StatusSwipe.NONE;
    }

    /**
     * The way a vertical swipe released at {@code y} went: -1 up, +1 down, each once it has
     * travelled {@code commitPx} or is flicked on the same way at {@code flingPxPerSec}; 0 for a
     * release short of both or flicked back against its own travel.
     */
    private int verticalRelease(float y, float velocityYPxPerSec, float commitPx,
                                float flingPxPerSec) {
        float dy = y - mDownY;
        if (dy == 0f) return 0;
        boolean up = dy < 0f;
        // The release speed along the swipe's own travel; negative is a flick back.
        float along = up ? -velocityYPxPerSec : velocityYPxPerSec;
        float fling = Math.max(0f, flingPxPerSec);
        if (along <= -fling && fling > 0f) return 0;
        boolean far = Math.abs(dy) >= commitPx;
        boolean flung = fling > 0f && along >= fling;
        if (!far && !flung) return 0;
        return up ? -1 : 1;
    }
}
