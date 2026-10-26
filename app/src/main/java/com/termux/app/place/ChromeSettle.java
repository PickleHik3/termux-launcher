package com.termux.app.place;

/**
 * The chrome's clock while the wall settles with no finger on it, a tap's slide or the rest of a
 * fling, under a page transition that turns the pages. The dock, the extra keys and the status
 * content then land on the wall's last frame, as the page's turn does, instead of ahead of it.
 *
 * <p>Under a finger the chrome is a function of the wall's position ({@link PlaceChromeTravel}):
 * it rests over the last {@link PlaceChromeTravel#LANDING} of the way. On the settle's spring that
 * stretch is most of its time: the slide covers half its way in about a sixth of its duration, so
 * the chrome had landed while a turned page still crept in or out (pong, 2026-10-22). Here the
 * chrome reads its way from where it stood as the slide began to where it rests, in step with the
 * share of the slide still to go. It starts on the slide's first frame, keeps the slide's spring
 * as its curve, and arrives with it.
 *
 * <p>The keyboard is not the chrome's here: it travels with the page on the wall's own offset
 * ({@link PlaceChromeTravel.Frame#keyboardFraction}), which lands with the wall already.
 *
 * <p>The answer is an offset the chrome reads in place of the wall's, in px from the current
 * page's rest. Under a finger, at rest, under the slide transition or across more than one page,
 * it is the wall's own offset.
 *
 * <p>Pure: the activity feeds it the wall's frames and reads the offset back.
 */
public final class ChromeSettle {

    /** How far past the slide's start a frame may read before it is not this slide's. */
    private static final float SHARE_SLACK = 1e-3f;

    /** The start of the slide the chrome is keeping time with; NaN while none. */
    private float mSlideFromPx = Float.NaN;
    /** Where the chrome's way stood as that slide began. */
    private float mChromeFromPx;
    /** How far it stood from the wall on the last frame: a new slide carries on from there. */
    private float mChromeLeadPx;
    private float mChromePx;

    /**
     * One frame of the wall.
     *
     * @param wallPx      the wall's offset from the current page's rest
     * @param slideFromPx where the slide under way started, NaN while none runs (at rest, under a
     *                    finger)
     * @param widthPx     the wall's width
     * @param turns       whether the page transition turns the pages, rather than the slide
     */
    public void frame(float wallPx, float slideFromPx, int widthPx, boolean turns) {
        if (!turns || widthPx <= 0 || Float.isNaN(wallPx) || Float.isNaN(slideFromPx)
                || slideFromPx == 0f || Math.abs(slideFromPx) > widthPx * (1f + SHARE_SLACK)) {
            follow(wallPx);
            return;
        }
        float sign = Math.signum(slideFromPx);
        if (slideFromPx != mSlideFromPx) {
            mSlideFromPx = slideFromPx;
            mChromeFromPx = slideFromPx + mChromeLeadPx;
        }
        float share = wallPx / slideFromPx;
        if (!(share >= 0f) || share > 1f + SHARE_SLACK) {
            // Not this slide's frame: the wall changed pages under it before its next slide began.
            mChromePx = wallPx + mChromeLeadPx;
            return;
        }
        mChromePx = toward(share, mChromeFromPx, sign * PlaceChromeTravel.LANDING * widthPx);
        mChromeLeadPx = mChromePx - wallPx;
    }

    /** The offset the dock, the extra keys and the status content read. */
    public float chromeOffsetPx() {
        return mChromePx;
    }

    private void follow(float wallPx) {
        mSlideFromPx = Float.NaN;
        mChromeLeadPx = 0f;
        mChromePx = wallPx;
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
}
