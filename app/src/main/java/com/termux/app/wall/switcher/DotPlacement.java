package com.termux.app.wall.switcher;

import com.termux.app.chrome.CornerZones;

/**
 * Where the page switcher's dot may stand and where it rests, in the layer's pixels. The dot sits
 * against the left or right edge, at a height the user chose, and never inside a Display corner
 * zone or under the bottom chrome. Pure: the controller reads the answer and moves the view.
 */
public final class DotPlacement {

    public static final float DIAMETER_DP = 28f;
    public static final float REST_ALPHA = 0.35f;
    public static final float EDGE_GAP_FACTOR = 0.25f;
    /** The hit circle's radius, so the touch target is 48 dp. */
    public static final float TOUCH_RADIUS_DP = 24f;
    public static final float KEYBOARD_GAP_DP = 8f;
    public static final float BAND_MARGIN_DP = 8f;
    public static final float DEFAULT_HEIGHT_FRACTION = 0.6f;
    public static final Edge DEFAULT_EDGE = Edge.RIGHT;

    private DotPlacement() {
    }

    public static float centerX(Edge edge, float width, float diameter) {
        float inset = (0.5f + EDGE_GAP_FACTOR) * diameter;
        return edge == Edge.LEFT ? inset : width - inset;
    }

    /** The highest the dot's centre may stand: below the top corner zone and its margin. */
    public static float bandTop(float diameter, float density) {
        return (CornerZones.PANE_SIZE_DP + BAND_MARGIN_DP) * density + diameter / 2f;
    }

    /**
     * The lowest the dot's centre may stand: above the bottom corner zone, and above the bottom
     * chrome (keyboard, accessory stack) by a gap.
     */
    public static float bandBottom(float height, float bottomChromeTop, float diameter,
                                   float density) {
        float limit = Math.min(height - (CornerZones.PANE_SIZE_DP + BAND_MARGIN_DP) * density,
            bottomChromeTop - KEYBOARD_GAP_DP * density);
        return limit - diameter / 2f;
    }

    /**
     * The resting centre y for a height fraction, kept inside the band; when the band is
     * inverted its bottom wins, and the dot never stands above half its diameter.
     */
    public static float restY(float fraction, float height, float top, float bottom,
                              float diameter) {
        float y = bottom < top ? bottom : Math.min(bottom, Math.max(top, fraction * height));
        return Math.max(diameter / 2f, y);
    }

    /** Frames the band's bottom must hold still before a clamped height is adopted. */
    public static final int STABLE_FRAMES = 3;

    /** Whether the band's bottom held the resting centre above the height the user chose. */
    public static boolean clampedUp(float y, float fraction, float height, float top) {
        return y < Math.max(top, fraction * height) - 0.5f;
    }

    /**
     * Whether a clamped resting height becomes the dot's saved position: only on a settled frame
     * with nothing else in play, so one visit to the page with the tallest bottom stack puts the
     * dot at the same height on every page. A keyboard, a page slide or a sheet never qualifies
     * ({@code chromeAtRest}), and neither does a stack still changing height.
     */
    public static boolean adoptsClamp(boolean clampedUp, boolean fingerDown, boolean pickerUp,
                                      int stableFrames, boolean chromeAtRest) {
        return clampedUp && !fingerDown && !pickerUp && stableFrames >= STABLE_FRAMES
            && chromeAtRest;
    }

    public static Edge nearerEdge(float x, float width) {
        return x < width / 2f ? Edge.LEFT : Edge.RIGHT;
    }

    /** The height fraction of a centre y, 0..1; an unmeasured layer gives the default. */
    public static float fractionOf(float y, float height) {
        if (height <= 0f) return DEFAULT_HEIGHT_FRACTION;
        return Math.min(1f, Math.max(0f, y / height));
    }

    public static float dragX(float x, float width, float diameter) {
        float half = diameter / 2f;
        return Math.min(width - half, Math.max(half, x));
    }

    public static float dragY(float y, float top, float bottom) {
        if (bottom < top) return bottom;
        return Math.min(bottom, Math.max(top, y));
    }

    public static boolean onDot(float x, float y, float cx, float cy, float touchRadiusPx) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= touchRadiusPx * touchRadiusPx;
    }
}
