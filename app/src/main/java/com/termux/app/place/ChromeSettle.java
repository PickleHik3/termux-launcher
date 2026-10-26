package com.termux.app.place;

/**
 * The chrome's clock while the wall settles with no finger on it, a tap's slide or the rest of a
 * fling, under a page transition that turns the pages. The dock, the keyboard and the extra keys
 * then land on the wall's last frame, as the page's turn does, instead of ahead of it.
 *
 * <p>Under a finger the chrome is a function of the wall's position ({@link PlaceChromeTravel}):
 * it rests over the last {@link PlaceChromeTravel#LANDING} of the way, and a keyboard being put
 * away is gone by {@link PlaceChromeTravel#KEYBOARD_HIDE_END}. On the settle's spring those
 * stretches are most of its time: the slide covers half its way in about a sixth of its duration,
 * so the keyboard was gone while a turned page still crept in or out at the edge (pong,
 * 2026-10-22). Here the chrome reads its way from where it stood as the slide began to where it
 * rests, in step with the share of the slide still to go. It starts on the slide's first frame,
 * keeps the slide's spring as its curve, and arrives with it.
 *
 * <p>Each answer is an offset the chrome reads in place of the wall's, in px from the current
 * page's rest: {@link #chromeOffsetPx} for the dock, the extra keys, the status content and a
 * rising keyboard, {@link #keyboardOffsetPx} for a keyboard being put away, which rests on its
 * own stretch. Where neither moves, under a finger, at rest, under the slide transition or across
 * more than one page, both are the wall's own offset.
 *
 * <p>Pure: the activity feeds it the wall's frames and reads the offsets back.
 */
public final class ChromeSettle {

    /** How far past the slide's start a frame may read before it is not this slide's. */
    private static final float SHARE_SLACK = 1e-3f;

    /** The start of the slide the chrome is keeping time with; NaN while none. */
    private float mSlideFromPx = Float.NaN;
    /** Where the chrome's and the put-away keyboard's ways stood as that slide began. */
    private float mChromeFromPx;
    private float mKeyboardFromPx;
    /** How far each stood from the wall on the last frame: a new slide carries on from there. */
    private float mChromeLeadPx;
    private float mKeyboardLeadPx;
    private float mChromePx;
    private float mKeyboardPx;

    /**
     * One frame of the wall.
     *
     * @param wallPx          the wall's offset from the current page's rest
     * @param slideFromPx     where the slide under way started, NaN while none runs (at rest,
     *                        under a finger)
     * @param widthPx         the wall's width
     * @param turns           whether the page transition turns the pages, rather than the slide
     * @param towardCommitted whether the slide lands on the place the chrome is committed to (a
     *                        fling that springs back), where a keyboard being put away comes back
     */
    public void frame(float wallPx, float slideFromPx, int widthPx, boolean turns,
                      boolean towardCommitted) {
        if (!turns || widthPx <= 0 || Float.isNaN(wallPx) || Float.isNaN(slideFromPx)
                || slideFromPx == 0f || Math.abs(slideFromPx) > widthPx * (1f + SHARE_SLACK)) {
            follow(wallPx);
            return;
        }
        float sign = Math.signum(slideFromPx);
        // A keyboard being put away rests, toward the place it leaves, from the start of the way
        // to LANDING and from KEYBOARD_HIDE_END on; toward the place that keeps it, the other
        // way round, in the same remaining shares of the slide.
        float keyboardStart = towardCommitted ? PlaceChromeTravel.KEYBOARD_HIDE_END
            : 1f - PlaceChromeTravel.LANDING;
        float keyboardLand = towardCommitted ? PlaceChromeTravel.LANDING
            : 1f - PlaceChromeTravel.KEYBOARD_HIDE_END;
        if (slideFromPx != mSlideFromPx) {
            mSlideFromPx = slideFromPx;
            mChromeFromPx = slideFromPx + mChromeLeadPx;
            // Nothing else reads the keyboard's way, so it starts at the edge of its rest and
            // moves on the slide's first frame; the chrome keeps its own first stretch, which the
            // beads melt in.
            mKeyboardFromPx = startAt(slideFromPx + mKeyboardLeadPx, sign * keyboardStart * widthPx,
                widthPx);
        }
        float share = wallPx / slideFromPx;
        if (!(share >= 0f) || share > 1f + SHARE_SLACK) {
            // Not this slide's frame: the wall changed pages under it before its next slide began.
            mChromePx = wallPx + mChromeLeadPx;
            mKeyboardPx = wallPx + mKeyboardLeadPx;
            return;
        }
        mChromePx = toward(share, mChromeFromPx, sign * PlaceChromeTravel.LANDING * widthPx);
        mKeyboardPx = toward(share, mKeyboardFromPx, sign * keyboardLand * widthPx);
        mChromeLeadPx = mChromePx - wallPx;
        mKeyboardLeadPx = mKeyboardPx - wallPx;
    }

    /** The offset the dock, the extra keys, the status content and a rising keyboard read. */
    public float chromeOffsetPx() {
        return mChromePx;
    }

    /** The offset a keyboard being put away reads. */
    public float keyboardOffsetPx() {
        return mKeyboardPx;
    }

    private void follow(float wallPx) {
        mSlideFromPx = Float.NaN;
        mChromeLeadPx = 0f;
        mKeyboardLeadPx = 0f;
        mChromePx = wallPx;
        mKeyboardPx = wallPx;
    }

    /**
     * A way that stood {@code fromPx} as the slide began, {@code share} of the slide still to go:
     * from there to {@code landPx}, where it rests, in step with the share. A way already inside
     * its rest goes the rest of the way with the wall.
     */
    static float toward(float share, float fromPx, float landPx) {
        if (fromPx * landPx > 0f && Math.abs(fromPx) > Math.abs(landPx)) {
            return landPx + (fromPx - landPx) * share;
        }
        return fromPx * share;
    }

    /**
     * {@code fromPx}, or {@code startPx} where it stands further out within one page: the same
     * state, at the edge of the stretch it rests over, so it moves on the slide's first frame.
     */
    static float startAt(float fromPx, float startPx, int widthPx) {
        if (fromPx * startPx > 0f && Math.abs(fromPx) > Math.abs(startPx)
                && Math.abs(fromPx) <= widthPx * (1f + SHARE_SLACK)) {
            return startPx;
        }
        return fromPx;
    }
}
