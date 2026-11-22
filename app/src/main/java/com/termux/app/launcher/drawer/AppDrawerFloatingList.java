package com.termux.app.launcher.drawer;

/**
 * How a drawer list runs under the floating search capsule, pure. The list spans the whole card;
 * the capsule's band is its content padding (so the first row rests below the capsule), and the
 * list does not clip to that padding, so scrolled rows pass under and beside the capsule. The card's
 * own top and bottom edges fade rows out over {@link #FADE_DP} instead of cutting them.
 *
 * <p>Views apply these numbers; nothing here runs per frame.
 */
public final class AppDrawerFloatingList {

    /** The soft edge at the card's top and bottom, in dp: the one fade length. */
    public static final float FADE_DP = 24f;

    private AppDrawerFloatingList() {}

    /** The fade length in pixels at {@code density}. */
    public static int fadeLengthPx(float density) {
        return Math.max(1, Math.round(FADE_DP * density));
    }

    /**
     * The list's top padding: the chrome's band (switch and capsule), the categories search's
     * results band and the lowered start, all of which the rows rest below at the top.
     */
    public static int topPaddingPx(int contentTopPx, int resultsBandPx, int lowerStartPx) {
        return Math.max(0, contentTopPx) + Math.max(0, resultsBandPx) + Math.max(0, lowerStartPx);
    }

    /**
     * The offset that moves a fading edge from the padding box's top out to the view's own top, so
     * the fade sits at the card's edge and not at the band's: the view adds it to its fade top.
     */
    public static int topFadeOffsetPx(int paddingTopPx) {
        return -Math.max(0, paddingTopPx);
    }

    /** As {@link #topFadeOffsetPx}, for the bottom edge. */
    public static int bottomFadeOffsetPx(int paddingBottomPx) {
        return Math.max(0, paddingBottomPx);
    }

    /**
     * Whether a touch at ({@code x}, {@code y}) is inside the capsule drawn between
     * {@code left} and {@code right} (a full-round capsule of {@code height}): the capsule takes
     * touches only inside its own shape, so the list under its corners and its folded-away part
     * still scrolls.
     */
    public static boolean capsuleContains(float x, float y, float left, float right,
                                          float height) {
        if (right <= left || height <= 0f || y < 0f || y >= height) return false;
        if (x < left || x >= right) return false;
        float radius = Math.min(height * 0.5f, (right - left) * 0.5f);
        float cy = height * 0.5f;
        float cx;
        if (x < left + radius) cx = left + radius;
        else if (x > right - radius) cx = right - radius;
        else return true;
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}
