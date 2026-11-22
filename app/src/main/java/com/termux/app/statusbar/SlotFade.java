package com.termux.app.statusbar;

/** How the window chips feather through the right edge of their slot while they slide. */
public final class SlotFade {

    /**
     * The fade's width: wide enough to read as a feather on a chip about 40dp across, narrow
     * enough that a chip at rest never meets it.
     */
    public static final float FADE_WIDTH_DP = 16f;

    private SlotFade() {}

    /** 1 left of the fade, 0 at and right of the slot's edge, linear between; width <= 0 is a hard edge. */
    static float alphaAt(float xPx, float slotRightPx, float fadeWidthPx) {
        if (fadeWidthPx <= 0f) return xPx < slotRightPx ? 1f : 0f;
        float t = (slotRightPx - xPx) / fadeWidthPx;
        return Math.max(0f, Math.min(1f, t));
    }
}
