package com.termux.app.terminal;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * Where a drawer sits inside the terminal area, and what its corners may be.
 *
 * <p>Pure arithmetic, kept away from the view that applies it for the same reason
 * {@link PaneShape} is: the drawer has to land on the terminal's leading edge whichever way the
 * layout runs, whether the terminal is one pane or nine, and whether the keyboard is up — and none
 * of that is worth a laid-out split to assert.
 */
public final class TerminalDrawerMetrics {

    /**
     * The widest a drawer gets. Past this it stops reading as a panel over the terminal and starts
     * reading as a screen that replaced it.
     */
    public static final float MAX_WIDTH_DP = 340f;
    /**
     * And the most of a narrow terminal it may take: under half, so the terminal it sits over stays
     * the larger thing on the screen. On a phone in portrait this is the binding limit, not the dp
     * cap — at the earlier 78% the drawer read as a page that had replaced the terminal.
     */
    public static final float AREA_WIDTH_SHARE = 0.45f;

    /** The scrim over the rest of the terminal area, as an alpha. */
    public static final int SCRIM_ALPHA = 71;   // 28% of 255

    private TerminalDrawerMetrics() {}

    /** A drawer's rectangle, in the coordinates of the plane it is laid out on. */
    public static final class Bounds {
        public final int leftMargin;
        public final int topMargin;
        public final int width;
        public final int height;

        Bounds(int leftMargin, int topMargin, int width, int height) {
            this.leftMargin = leftMargin;
            this.topMargin = topMargin;
            this.width = width;
            this.height = height;
        }
    }

    /** How wide a drawer is over a terminal area this wide. */
    public static int widthPx(int areaWidthPx, float density) {
        if (areaWidthPx <= 0) return 0;
        float safeDensity = density > 0f ? density : 1f;
        return Math.max(1, Math.min(Math.round(MAX_WIDTH_DP * safeDensity),
            Math.round(areaWidthPx * AREA_WIDTH_SHARE)));
    }

    /**
     * The drawer over a terminal area at {@code areaLeft, areaTop}.
     *
     * <p>Full area height, because the drawer belongs to the terminal rather than to a pane: with
     * the window split four ways there is no one pane it could sit in, and a panel that stopped at
     * the first seam would read as one pane's menu.
     */
    @NonNull
    public static Bounds place(int areaLeft, int areaTop, int areaWidth, int areaHeight,
                               boolean rtl, float density) {
        int width = widthPx(areaWidth, density);
        int left = rtl ? areaLeft + areaWidth - width : areaLeft;
        return new Bounds(left, areaTop, width, Math.max(0, areaHeight));
    }

    /**
     * Where a drawer starts from, and sinks back to: just past the terminal's leading edge.
     *
     * @return the translation, negative in a left-to-right layout and positive in a right-to-left
     *     one, so adding it to the resting position puts the whole card outside the area.
     */
    public static float enterTranslationX(int widthPx, boolean rtl) {
        return rtl ? widthPx : -widthPx;
    }

    /**
     * The part of a sliding drawer that is inside the terminal area, in the card's own coordinates.
     *
     * <p>A translated card is clipped by its plane, whose edge is the screen's, not the terminal's:
     * with a side gap set, the drawer would otherwise appear in the gap first and slide the rest
     * of the way in. Clipping at the resting edge keeps it out of sight until it crosses the
     * terminal's border. The clip follows the card, so it is recomputed for every translation.
     *
     * @return false when nothing needs clipping, i.e. the card is at rest.
     */
    public static boolean slideClip(int widthPx, int heightPx, float translationX, boolean rtl,
                                    @NonNull Rect out) {
        int hidden = Math.max(0, Math.min(widthPx, Math.round(Math.abs(translationX))));
        if (hidden == 0) {
            out.set(0, 0, widthPx, heightPx);
            return false;
        }
        if (rtl) out.set(0, 0, widthPx - hidden, heightPx);
        else out.set(hidden, 0, widthPx, heightPx);
        return true;
    }

    /**
     * The radius the trailing corners actually draw with — the terminal's own, capped where a tall
     * narrow card could not wear it. The leading corners are the terminal's edge and take it whole:
     * they sit exactly on the arc the terminal is already drawing there.
     */
    public static float trailingRadiusPx(float terminalRadiusPx, int widthPx, int heightPx) {
        return PaneShape.radiusForBounds(terminalRadiusPx, widthPx, heightPx);
    }

    /**
     * The eight radii a {@code GradientDrawable} wants, with the leading pair on whichever side the
     * layout's leading edge is.
     */
    @NonNull
    public static float[] cornerRadii(float leadingRadiusPx, float trailingRadiusPx, boolean rtl) {
        float start = Math.max(0f, leadingRadiusPx);
        float end = Math.max(0f, trailingRadiusPx);
        float left = rtl ? end : start;
        float right = rtl ? start : end;
        return new float[] {left, left, right, right, right, right, left, left};
    }
}
