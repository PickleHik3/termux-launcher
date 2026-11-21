package com.termux.app.chrome;

import androidx.annotation.ColorInt;

/**
 * The inks of a row of page marks (the page bar's glyphs, the dock's capsules): the current
 * page's mark in the accent, the others in the ground's neutral ink, both solved on the ground
 * they are drawn on, on that ground's side ({@code paleSide}, from the ground's own ink).
 *
 * <p>The rule: the current mark is the accent made legible at {@link #CURRENT} (body text, 4.5:1);
 * every other mark is the neutral ink at the smallest alpha that reads at the current mark's
 * contrast over {@link #STEP}. So the current mark is always the strongest, and since it clears
 * 4.5:1 the others clear {@link #OTHER_FLOOR} (the contract's graphics tier, 3:1). Pure; asked on
 * a dress pass, never per frame.</p>
 */
public final class PageMarkInk {

    /**
     * What a mark's target carries over its tier. The ground a mark is solved on is the shared
     * measurement of the whole wallpaper (and a glass body before its frost), while the patch
     * really behind the mark can be a little paler or darker: on screen the marks landed 2 to 5%
     * under their tiers (4.29 for 4.5, 2.90 for 3). Solving this much over keeps them on it.
     */
    public static final double HEADROOM = 1.1;
    /** The graphics tier with {@link #HEADROOM}, for a mark with no current/other pair. */
    public static final double GRAPHIC = OnGlass.TARGET_LARGE_TEXT * HEADROOM;
    /** The tier the current page's mark is held to. */
    public static final double CURRENT = OnGlass.TARGET_BODY_TEXT * HEADROOM;
    /** The floor the other marks keep whenever the current one clears {@link #CURRENT}. */
    public static final double OTHER_FLOOR = GRAPHIC;
    /** How much weaker the other marks read than the current one: the gap between the tiers. */
    public static final double STEP = CURRENT / OTHER_FLOOR;

    private PageMarkInk() {
    }

    /** The current mark: {@code accent} toned on {@code paleSide} until it reads at {@link #CURRENT}. */
    @ColorInt
    public static int current(@ColorInt int ground, @ColorInt int accent, boolean paleSide) {
        return GlassInk.legibleOn(ground, accent, paleSide, CURRENT, 0xFF);
    }

    /**
     * An other mark: {@code ink} at the smallest alpha whose drawn colour reads at
     * {@code current}'s contrast over {@link #STEP}; opaque where even that is short.
     */
    @ColorInt
    public static int other(@ColorInt int ground, @ColorInt int ink, @ColorInt int current) {
        int base = OnGlass.opaque(ground);
        double target = OnGlass.ratio(GlassInk.effective(current, base), base) / STEP;
        int opaqueInk = OnGlass.opaque(ink);
        if (OnGlass.ratio(opaqueInk, base) < target) return opaqueInk;
        int low = 0;
        int high = 255;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (OnGlass.ratio(GlassInk.effective(opaqueInk, mid, base), base) >= target) high = mid;
            else low = mid + 1;
        }
        return OnGlass.withAlpha(opaqueInk, low);
    }
}
