package com.termux.app.wall;

import androidx.annotation.NonNull;

import com.termux.app.terminal.TerminalBodySwipe;

/**
 * A press on a pane's border that pages the wall: slid sideways off any of its four borders, or
 * held on any border and then dragged. The one gesture every place has for moving to the places
 * beside it, in every mode.
 *
 * <p>The border lives outside the page. Its zone ({@link Zone}) is the strip between each edge of
 * the page — the terminal, the Widgets page, the display window — and the wall's own edge on that
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
 *   <li>On a border outside the page's text, with another place to go ({@link #slidePages}):
 *       the top or bottom border out in the air beyond the page, or the left or right border in
 *       the air beside it and its {@link #SIDE_REACH_DP} of clearance. The wall is claimed at
 *       once, the same drag a hold would have begun, measured from where the claim was made.
 *       Nothing there is the content's. The phone's back swipe still takes the side edges outside
 *       the stretch the wall asks it to leave (its gesture exclusion), before this sees them.</li>
 *   <li>On the page's text, where the terminal there gives a sideways swipe away (the owner
 *       asks {@link TerminalBodySwipe#yieldsSideways} as the finger lands): only a start that is
 *       clearly sideways ({@link TerminalBodySwipe#isSideways}) claims the wall, the same drag
 *       again; a vertical or diagonal one stays the terminal's scroll, and the hold never pages
 *       from the text, so a still finger is the terminal's long press.</li>
 *   <li>Anywhere else — the minimum band where it reaches past the clearance into the page (a
 *       page almost flush with the wall), the boxes by the pills, and the phone's own strip at the
 *       top of the screen, wherever the text does not give the swipe away: the gesture is given
 *       up, so a scroll, a selection or a TUI's drag inside the page stays the content's. Only the
 *       hold pages from the band.</li>
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
 * <p>The bottom border is where paging between windows starts most, and its air is only the
 * Margin tall, so the seam there is a band a fingertip tall ({@link SeamSwipe}, set by {@link
 * Zone#seam}): the air, the window's last row above it and the dock's top below it. From the last
 * row a clearly sideways start pages where the terminal there gives the swipe away (asked as the
 * finger lands, whatever the body swipe's setting), and from the dock's top too ({@link
 * #downOnDock}: the dock has the finger, and its owner hands the wall the stream). In both a still
 * finger and a steeper start stay the content's.
 *
 * <p>A page whose content owns everything inside its edges — the display window, whose picture
 * is a Linux app's to its rim — keeps every zone out of it ({@link Zone#ownContent}): no side
 * reach, no boxes by the pills, no seam band in its last row, no body swipe. Its borders are the
 * air around it, and only where that air is thinner than {@link #MIN_BAND_DP} does a border
 * reach in, by the frame line alone ({@link BorderReach#FRAME_STRIP_DP}). The dock's top keeps
 * its part of the seam band, which lies outside the page.
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

    /**
     * How tall the box a Home page's grabber is found in is, in dp: a finger's height, standing on
     * the dock's top edge ({@link #homeGrabber}).
     */
    public static final float HOME_GRABBER_HIT_DP = 48f;

    /** How far above the dock's top edge a Home page's grabber's lower edge sits, in dp. */
    public static final float HOME_GRABBER_LIFT_DP = 4f;

    /** A Home page's grabber's thickness, in dp: a little bolder than a window's. */
    public static final float HOME_GRABBER_THICKNESS_DP = 4f;

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
        /**
         * Where the bottom pill's box stands when it is not by the page's bottom edge (a Home
         * page's grabber, over the dock): set by {@link #bottomPill}, and only while
         * {@link #mBottomPillSet}.
         */
        private final float[] mBottomPill = new float[4];
        private boolean mBottomPillSet;
        /** Scratch for {@link #inPill}, so a press allocates nothing. */
        private final float[] mPillBox = new float[4];
        /**
         * The seam band's reach above the bottom air into the page ({@link #seam}); 0, the
         * default, leaves the bottom border its air alone.
         */
        float mSeamReach;
        /** The seam band's reach below the bottom air into the dock's top. */
        float mSeamDockReach;
        /** Where the bottom air ends: the dock's top, or NaN for the wall's edge. */
        float mSeamAirBottom = Float.NaN;
        /** Whether the dock stands below the air, so the band reaches into its top. */
        boolean mSeamDock;
        /** Whether a seam band is set at all ({@link #seam}); its last row's reach may be 0. */
        boolean mSeamSet;
        /**
         * Whether the page's content owns everything inside its edges ({@link #ownContent}): no
         * zone reaches past {@link #mFrameStrip} into it.
         */
        boolean mOwnsContent;
        /** How far a border may reach into a page that owns its content: its frame line. */
        float mFrameStrip;

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
         * The bottom pill's box where it is not by the page's bottom edge: a Home page's grabber
         * stands over the dock ({@link BorderDrag#homeGrabber}), and its swipe starts by it. An
         * empty box is no override.
         */
        @NonNull
        public Zone bottomPill(float left, float top, float right, float bottom) {
            mBottomPill[0] = left;
            mBottomPill[1] = top;
            mBottomPill[2] = right;
            mBottomPill[3] = bottom;
            mBottomPillSet = right > left && bottom > top;
            return this;
        }

        /**
         * The seam band along the bottom border ({@link SeamSwipe}): reaching {@code paneReachPx}
         * into the page's last row and, where {@code dockTop} is a number, {@code dockReachPx}
         * into the dock whose top it is. The air between ends at the dock's top, or at the wall's
         * edge with no dock ({@code dockTop} NaN).
         */
        @NonNull
        public Zone seam(float dockTop, float paneReachPx, float dockReachPx) {
            mSeamReach = Math.max(0f, paneReachPx);
            mSeamDockReach = Math.max(0f, dockReachPx);
            mSeamAirBottom = dockTop;
            mSeamDock = !Float.isNaN(dockTop);
            mSeamSet = true;
            return this;
        }

        /** No seam band: the bottom border is its air alone. */
        @NonNull
        public Zone clearSeam() {
            mSeamReach = 0f;
            mSeamDockReach = 0f;
            mSeamAirBottom = Float.NaN;
            mSeamDock = false;
            mSeamSet = false;
            return this;
        }

        /**
         * The page's content owns everything inside its edges (the display window): every border
         * stops at the page's edge, and only where a side's air is thinner than the minimum band
         * does it reach in, by {@code frameStripPx} at most — the frame line, never the content.
         * The boxes by the pills and the seam band's last row are gone; the seam band's part on
         * the dock's top, outside the page, stays.
         */
        @NonNull
        public Zone ownContent(float frameStripPx) {
            mOwnsContent = true;
            mFrameStrip = Math.max(0f, frameStripPx);
            return this;
        }

        /** The page shares its edges with the borders again, as a terminal does (the default). */
        @NonNull
        public Zone shareContent() {
            mOwnsContent = false;
            mFrameStrip = 0f;
            return this;
        }

        /**
         * How far the seam band reaches above the bottom air into the page: none on a page that
         * owns its content, and never past the middle of the page.
         */
        float seamPaneReach() {
            if (mOwnsContent) return 0f;
            return Math.max(0f, Math.min(mSeamReach, (mBottom - mTop) / 2f));
        }

        /**
         * The seam band's part inside the page, its last row, as {@code left, top, right, bottom}
         * in {@code out}, between the corner squares. False, with {@code out} untouched, where
         * there is none.
         */
        public boolean seamPaneBounds(@NonNull float[] out) {
            if (!mSeamSet || mRight <= mLeft || mBottom <= mTop) return false;
            float corner = cornerSize();
            float l = mLeft + corner;
            float r = mRight - corner;
            float t = mBottom - seamPaneReach();
            if (r <= l || !(mBottom > t) || seamAirBottom() < mBottom) return false;
            out[0] = l;
            out[1] = t;
            out[2] = r;
            out[3] = mBottom;
            return true;
        }

        /** Where the bottom air ends: the dock's top where one is set, else the wall's edge. */
        public float seamAirBottom() {
            float wallEdge = mBottom + mGapBottom;
            return Float.isNaN(mSeamAirBottom) ? wallEdge : Math.min(wallEdge, mSeamAirBottom);
        }

        /** Where {@code (x, y)} lies on the seam band ({@link SeamSwipe#startAt}). */
        @NonNull
        public SeamSwipe.Start seamAt(float x, float y) {
            if (!mSeamSet || mRight <= mLeft || mBottom <= mTop) {
                return SeamSwipe.Start.NONE;
            }
            return SeamSwipe.startAt(x, y, mLeft, mRight, cornerSize(), mBottom, seamAirBottom(),
                seamPaneReach(), mSeamDockReach, mSeamDock);
        }

        /** The bottom pill's box by the page's bottom edge again, as {@link #pills} puts it. */
        @NonNull
        public Zone clearBottomPill() {
            mBottomPillSet = false;
            return this;
        }

        /**
         * How far inside the page's edge on {@code side} its zone reaches: the shortfall of that
         * side's air against the minimum band, or on the left and right the side reach where that
         * is further; 0 on the top and bottom wherever the air is at least the band, and never
         * past the middle of the page. On a page that owns its content ({@link #ownContent}), 0
         * wherever the air is at least the band, and the frame line at most where it is not.
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
            float in = Math.max(0f, Math.min(Math.max(mMinBand - gap, reach), half));
            if (!mOwnsContent) return in;
            return gap >= mMinBand ? 0f : Math.min(in, mFrameStrip);
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
            if (side == Border.BOTTOM && mBottomPillSet) {
                System.arraycopy(mBottomPill, 0, out, 0, 4);
                return true;
            }
            // A page that owns its content keeps the pills' swipes on its border's air.
            if (mOwnsContent) return false;
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

        /**
         * Whether {@code (x, y)} lies on the page itself: from its left and top edges to just
         * short of its right and bottom ones.
         */
        boolean holds(float x, float y) {
            return x >= mLeft && x < mRight && y >= mTop && y < mBottom;
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
    /**
     * Whether the finger has gone past the plain slop: it is not holding, though a diagonal
     * start may still be short of the damped slop a page asks for ({@link PagePolicy#pastSlop}).
     */
    private boolean mSetOff;
    /** Whether the hold may page: the wall has another place to go. */
    private boolean mHoldPages;
    /**
     * Whether a sideways start pages at once, without the hold: a border outside the page's text
     * ({@link #slidesFrom}) and below the phone's own strip, on a wall that can page.
     */
    private boolean mSlidePages;
    /** Where the wall's drag is measured from: the DOWN, or where a sideways start claimed it. */
    private float mDragOriginX;
    /** Whether this press may become the keyboard swipe: it landed in the bottom zone. */
    private boolean mKeyboardEligible;
    /** Whether this press may become the status bar's swipe: it landed in the top zone. */
    private boolean mStatusEligible;
    /** Whether the status bar's swipe needs a second finger (set before {@link #down}). */
    private boolean mTwoFingerStatus;
    /**
     * Whether a sideways start pages from inside the page, on its text: the terminal there gives
     * the swipe away ({@link TerminalBodySwipe}), and the finger has to set off clearly sideways
     * ({@link TerminalBodySwipe#isSideways}).
     */
    private boolean mBodySlides;
    /**
     * Whether a clearly sideways start pages from the seam band beyond the border's air
     * ({@link SeamSwipe}): the window's last row, where the terminal gives the swipe away, or the
     * dock's top ({@link #downOnDock}).
     */
    private boolean mSeamSlides;
    /** Where on the seam band the finger landed; NONE off it. */
    @NonNull private SeamSwipe.Start mSeamStart = SeamSwipe.Start.NONE;
    /**
     * How far the finger had already gone sideways when a start claimed the wall, positive to the
     * right; 0 for a hold. The wall is dragged from the claim, so this is travel the drag never
     * saw, and a short flick counts it ({@link #claimLead}).
     */
    private float mClaimLeadX;

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
     * Whether a sideways start from {@code (x, y)} on {@code side} pages at once: the point lies
     * outside the page's text. On the top and bottom that is the air beyond the page's edge; on
     * the left and right, the air beside it and the clearance the side reach takes over the edge
     * ({@link #SIDE_REACH_DP}), which never holds a character. Where the minimum band reaches
     * further in than that (a page almost flush with the wall), the rest of it may be text, and
     * only the hold pages from there.
     */
    static boolean slidesFrom(@NonNull Border side, float x, float y, @NonNull Zone zone) {
        switch (side) {
            case TOP: return y < zone.mTop;
            case BOTTOM: return y >= zone.mBottom;
            case LEFT:
                return x < zone.mLeft + Math.min(zone.mSideReach, zone.inside(Border.LEFT));
            case RIGHT:
                return x >= zone.mRight - Math.min(zone.mSideReach, zone.inside(Border.RIGHT));
            default: return false;
        }
    }

    /**
     * Where a Home page's grabber stands, and the box its swipe (the palette gesture) starts in.
     * A Home page has no border, and the dock floats over its foot, so the grabber is not on the
     * page's edge as a window's is: it stands centred on the screen's width — the wall's width
     * and the insets beside it ({@code leftInset}, {@code rightInset}: a side stack, say) — with
     * its lower edge {@link #HOME_GRABBER_LIFT_DP} above the dock's top, wherever the dock is (up
     * with the keyboard too). Its box is {@link #PILL_SWIPE_WIDTH_DP} wide on the same centre
     * and {@link #HOME_GRABBER_HIT_DP} tall, standing on the dock's top edge, never over the dock.
     * With no dock ({@code dockTop} NaN) the page's bottom edge stands in for it. All in the
     * wall's coordinates, at rest.
     *
     * @param pillOut the grabber at rest, {@code left, top, right, bottom}: {@link
     *                BorderGrabber#WIDTH_DP} by {@link #HOME_GRABBER_THICKNESS_DP}
     * @param hitOut  the box its swipe starts in, inside the wall's width
     */
    public static void homeGrabber(float wallWidth, float leftInset, float rightInset,
                                   float pageBottom, float dockTop, float density,
                                   @NonNull float[] pillOut, @NonNull float[] hitOut) {
        float left = Math.max(0f, leftInset);
        float right = Math.max(0f, rightInset);
        float centreX = (left + Math.max(0f, wallWidth) + right) / 2f - left;
        float anchor = Float.isNaN(dockTop) ? pageBottom : dockTop;
        float halfWidth = BorderGrabber.WIDTH_DP * density / 2f;
        float pillBottom = anchor - HOME_GRABBER_LIFT_DP * density;
        pillOut[0] = centreX - halfWidth;
        pillOut[1] = pillBottom - HOME_GRABBER_THICKNESS_DP * density;
        pillOut[2] = centreX + halfWidth;
        pillOut[3] = pillBottom;
        float halfBox = PILL_SWIPE_WIDTH_DP * density / 2f;
        hitOut[0] = Math.max(0f, centreX - halfBox);
        hitOut[1] = anchor - HOME_GRABBER_HIT_DP * density;
        hitOut[2] = Math.min(Math.max(0f, wallWidth), centreX + halfBox);
        hitOut[3] = anchor;
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
     * never pages, so a still finger there is given up to the content at the hold. A press on a
     * border outside the page's text may also page by setting off sideways ({@link #slidePages}).
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
        return down(x, y, zone, slopPx, canPage, keyboardSwipe, statusSwipe, statusTopLimit,
            false);
    }

    /**
     * {@link #down}, for a press that may also land on the page's text with a terminal there that
     * gives a sideways swipe away ({@code bodySwipe}). Inside the page, below the phone's strip,
     * on a wall with another place to go, a finger that sets off clearly sideways
     * ({@link TerminalBodySwipe#isSideways}) before the hold pages at once, as from a border
     * outside the text. Its hold never pages — a still finger is the terminal's long press — and
     * a start that is not clearly sideways stays the terminal's scroll, or is the keyboard's or the
     * status bar's swipe where it landed in a pill's box.
     */
    public boolean down(float x, float y, @NonNull Zone zone, float slopPx, boolean canPage,
                        boolean keyboardSwipe, boolean statusSwipe, float statusTopLimit,
                        boolean bodySwipe) {
        return down(x, y, zone, slopPx, canPage, keyboardSwipe, statusSwipe, statusTopLimit,
            bodySwipe, false);
    }

    /**
     * {@link #down}, for a press that may also land in the window's last row of the seam band
     * ({@link Zone#seam}) with a terminal there that gives a sideways swipe away ({@code
     * seamSwipe}, asked whatever the body swipe's setting). From there a clearly sideways start
     * pages as from the border's air; a still finger or a steeper start stay the terminal's, or
     * the keyboard's swipe where the press is in the pill's box.
     */
    public boolean down(float x, float y, @NonNull Zone zone, float slopPx, boolean canPage,
                        boolean keyboardSwipe, boolean statusSwipe, float statusTopLimit,
                        boolean bodySwipe, boolean seamSwipe) {
        mDownX = x;
        mDownY = y;
        mDragOriginX = x;
        mSlop = Math.max(0f, slopPx);
        mBorder = borderAt(x, y, zone);
        mHoldPages = canPage && mBorder != Border.NONE;
        mSlidePages = mHoldPages && slidesFrom(mBorder, x, y, zone) && !(y < statusTopLimit);
        // Off the border, the pills' boxes inside the page carry the vertical swipes alone.
        Border swipeSide = mBorder != Border.NONE ? mBorder : pillAt(x, y, zone);
        mKeyboardEligible = keyboardSwipe && swipeSide == Border.BOTTOM;
        mStatusEligible = statusSwipe && swipeSide == Border.TOP && !(y < statusTopLimit);
        mBodySlides = bodySwipe && canPage && !mSlidePages && !zone.mOwnsContent
            && zone.holds(x, y) && !(y < statusTopLimit);
        mSeamStart = zone.seamAt(x, y);
        // The dock's part of the band is never this view's press: its owner calls downOnDock.
        if (mSeamStart == SeamSwipe.Start.DOCK) mSeamStart = SeamSwipe.Start.NONE;
        mSeamSlides = mSeamStart == SeamSwipe.Start.PANE && seamSwipe && canPage && !mSlidePages
            && !(y < statusTopLimit);
        mClaimLeadX = 0f;
        mSetOff = false;
        boolean armed = mHoldPages || mKeyboardEligible || mStatusEligible || mBodySlides
            || mSeamSlides;
        mBorder = !armed ? Border.NONE : mHoldPages ? mBorder
            : swipeSide == Border.NONE && mSeamSlides ? Border.BOTTOM : swipeSide;
        mClaim = armed ? Claim.PENDING : Claim.NONE;
        return armed;
    }

    /**
     * Whether the status bar's swipe needs two fingers: with it on, one finger that sets off
     * vertically from the top zone never claims {@link Claim#STATUS}, and a second finger landing
     * on the still pending press does. Set before {@link #down}; off is the default.
     */
    public void setTwoFingerStatus(boolean twoFingers) {
        mTwoFingerStatus = twoFingers;
    }

    /**
     * The finger landed at {@code (x, y)} on the dock, which has it: arms the drag only where the
     * point lies on the dock's top in the seam band ({@link Zone#seamAt}) and the wall has another
     * place to go. Only a clearly sideways start then pages ({@link SeamSwipe#decide}); a tap, a
     * still finger, a steeper start or a second finger are the dock's, and the hold never pages.
     *
     * @return whether the press is armed
     */
    public boolean downOnDock(float x, float y, @NonNull Zone zone, float slopPx,
                              boolean canPage) {
        reset();
        mDownX = x;
        mDownY = y;
        mDragOriginX = x;
        mSlop = Math.max(0f, slopPx);
        SeamSwipe.Start start = zone.seamAt(x, y);
        if (!canPage || start != SeamSwipe.Start.DOCK) return false;
        mSeamStart = start;
        mSeamSlides = true;
        mBorder = Border.BOTTOM;
        mClaim = Claim.PENDING;
        return true;
    }

    /**
     * One move of the finger. Before the hold, travel past the slop decides: a finger that sets
     * off is not holding. Up or down from the bottom zone it is the keyboard's, and from the top
     * zone the status bar's; sideways from where a slide pages ({@link #slidePages}) it is the
     * wall's at once, its drag measured from here so the wall does not jump by the slop. A
     * sideways start more than 30 degrees off the axis waits for the page's damped slop
     * ({@link PagePolicy#pastSlop}), so a diagonal thumb is not a turn by accident. Any other
     * start is given up. After a claim the move is the claimant's, and the claim never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy <= mSlop * mSlop) return mClaim;
        mSetOff = true;
        boolean vertical = Math.abs(dy) > Math.abs(dx);
        if (vertical) {
            mClaim = mKeyboardEligible ? Claim.KEYBOARD
                : mStatusEligible && !mTwoFingerStatus ? Claim.STATUS : Claim.ABANDONED;
        } else if (mSlidePages || (mBodySlides && TerminalBodySwipe.isSideways(dx, dy))
            || (mSeamSlides && SeamSwipe.decide(mSeamStart, dx, dy, mSlop, true, false)
                == SeamSwipe.Decision.BORDER_DRAG)) {
            if (!PagePolicy.pastSlop(dx, dy, mSlop)) return mClaim;
            mClaim = Claim.PAGING;
            mDragOriginX = x;
            mClaimLeadX = dx;
        } else {
            mClaim = Claim.ABANDONED;
        }
        return mClaim;
    }

    /**
     * The hold time passed. A finger that travelled or was joined has already given the gesture
     * up through {@link #move} or {@link #secondPointer}, or is a diagonal start still short of
     * the page's damped slop, which its travel goes on deciding; any other pending press is a
     * still one. Where the hold may not page — a wall of one place, or a press in a pill's box — a
     * still finger is given up, and inside the page it is the content's long press. A later
     * vertical move can then no longer turn into the keyboard's or the status bar's swipe.
     *
     * @return true when the wall just claimed the finger, and the content is owed a cancel
     */
    public boolean holdElapsed() {
        if (mClaim != Claim.PENDING || mSetOff) return false;
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
        if (mClaim == Claim.PENDING) {
            mClaim = mTwoFingerStatus && mStatusEligible && !mSetOff
                ? Claim.STATUS : Claim.ABANDONED;
        }
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
        mBodySlides = false;
        mSeamSlides = false;
        mSeamStart = SeamSwipe.Start.NONE;
        mClaimLeadX = 0f;
        mSetOff = false;
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
     * True from a DOWN whose sideways start pages at once — a border outside the page's text
     * ({@link #slidesFrom}), below the phone's strip, with another place to go — until the lift,
     * whichever way the claim went. Every such press also {@link #holdPages}.
     */
    public boolean slidePages() {
        return mSlidePages;
    }

    /**
     * True from a DOWN on the page's text whose clearly sideways start pages ({@link #down} with
     * {@code bodySwipe}) until the lift, whichever way the claim went.
     */
    public boolean bodySlides() {
        return mBodySlides;
    }

    /**
     * True from a DOWN on the seam band beyond the border's air — the window's last row, or the
     * dock's top ({@link #downOnDock}) — whose clearly sideways start pages, until the lift.
     */
    public boolean seamSlides() {
        return mSeamSlides;
    }

    /** Where on the seam band the finger landed, from the DOWN to the lift; NONE off it. */
    @NonNull
    public SeamSwipe.Start seamStart() {
        return mSeamStart;
    }

    /**
     * How far the finger had gone sideways before a sideways start claimed the wall, positive to
     * the right; 0 for a claim by the hold, or no claim. The drag is measured from the claim so
     * the wall does not jump by the slop, and this is the travel it left out: a release adds it
     * back for the flick's least distance ({@link PaneWallPolicy#settle}), so a short fast flick
     * pages rather than springing back for the slop it spent.
     */
    public float claimLead() {
        return mClaim == Claim.PAGING ? mClaimLeadX : 0f;
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
