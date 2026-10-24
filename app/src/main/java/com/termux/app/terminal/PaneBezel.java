package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * The bezel a pane wears while a terminal effect is on: a band of the pane's own glass round the
 * "screen" the effect is drawn on, so the CRT's bend and the scanlines stop at a frame instead of
 * running into the pane's edge, where the bend used to pull text past the slab.
 *
 * <p>The bezel is {@link #SIDE_DP} at the sides and {@link #TOP_BOTTOM_DP} top and bottom; the
 * screen inside it is rounded at {@link #SCREEN_RADIUS_DP} (capped like any pane,
 * {@link PaneShape#radiusForBounds}). The effect is cut to the screen, and the terminal is laid
 * out inside it: past the bezel, past the screen's own arc, and past what the bend pushes off the
 * screen's edges, so no cell is lost to the bend either.
 *
 * <p>Pure arithmetic in px, so it is tested without a view.
 */
public final class PaneBezel {

    private PaneBezel() {}

    /** The bezel's width at the left and right. */
    public static final float SIDE_DP = 12f;
    /** The bezel's height at the top and bottom. */
    public static final float TOP_BOTTOM_DP = 16f;
    /** The radius the screen inside the bezel is rounded at. */
    public static final float SCREEN_RADIUS_DP = 10f;

    /** Whether a pane drawn through {@code style} wears the bezel: whenever any effect is on. */
    public static boolean worn(@NonNull PaneRetroStyle style) {
        return style != PaneRetroStyle.NONE;
    }

    /** The bezel's side width in px, 0 while it is not worn. */
    public static int sidePx(boolean worn, float density) {
        return worn ? Math.round(SIDE_DP * Math.max(0f, density)) : 0;
    }

    /** The bezel's top and bottom height in px, 0 while it is not worn. */
    public static int topBottomPx(boolean worn, float density) {
        return worn ? Math.round(TOP_BOTTOM_DP * Math.max(0f, density)) : 0;
    }

    /**
     * The screen: the rect the effect is cut to inside a pane {@code width} by {@code height} px,
     * the whole pane while no bezel is worn. Never inverted on a pane smaller than its bezel.
     *
     * @param out {@code left, top, right, bottom}
     * @return the screen's corner radius; with no bezel, {@code paneRadiusPx} (the pane's own)
     */
    public static float screen(boolean worn, float width, float height, float density,
                               float paneRadiusPx, @NonNull float[] out) {
        float w = Math.max(0f, width);
        float h = Math.max(0f, height);
        if (!worn) {
            out[0] = 0f;
            out[1] = 0f;
            out[2] = w;
            out[3] = h;
            return Math.max(0f, paneRadiusPx);
        }
        float side = Math.min(sidePx(true, density), w / 2f);
        float topBottom = Math.min(topBottomPx(true, density), h / 2f);
        out[0] = side;
        out[1] = topBottom;
        out[2] = w - side;
        out[3] = h - topBottom;
        return PaneShape.radiusForBounds(SCREEN_RADIUS_DP * Math.max(0f, density),
            Math.round(out[2] - out[0]), Math.round(out[3] - out[1]));
    }

    /**
     * How much of a screen's half-extent the CRT's barrel bend pushes past its edge, at the
     * middle of that edge, in px. The shader samples the picture at {@code c(1 + b|c|²)/(1 + 2b)}
     * of the screen's half size, so the edge's middle shows the picture from {@code (1 + b)/(1 +
     * 2b)} of the way out, and everything beyond that falls off the screen: {@code b/(1 + 2b)} of
     * the half-extent. Rounded up; 0 for a flat effect.
     */
    public static int bendClearancePx(float halfExtentPx, float bend) {
        if (!(bend > 0f) || !(halfExtentPx > 0f)) return 0;
        return (int) Math.ceil(halfExtentPx * bend / (1f + 2f * bend));
    }

    /**
     * The terminal's margins inside a pane {@code width} by {@code height} px while the bezel is
     * worn: the bezel, the screen's arc clearance and the bend's clearance on each axis. The top
     * is before the terminal's own headroom ({@link PaneShape#topInsetPx} takes that off).
     *
     * @param out {@code side, topBottom}
     */
    public static void contentInsets(float width, float height, float density, float bend,
                                     @NonNull int[] out) {
        float w = Math.max(0f, width);
        float h = Math.max(0f, height);
        float side = Math.min(sidePx(true, density), w / 2f);
        float topBottom = Math.min(topBottomPx(true, density), h / 2f);
        float screenW = w - 2f * side;
        float screenH = h - 2f * topBottom;
        int arc = PaneShape.contentInsetPx(PaneShape.radiusForBounds(
            SCREEN_RADIUS_DP * Math.max(0f, density), Math.round(screenW), Math.round(screenH)));
        out[0] = Math.round(side) + arc + bendClearancePx(screenW / 2f, bend);
        out[1] = Math.round(topBottom) + arc + bendClearancePx(screenH / 2f, bend);
    }

    /**
     * What the bezel is filled with under the terminal: nothing while the terminal's glass has
     * opacity to show (the bezel is that glass), the theme's surface colour, opaque, while it has
     * none, so the bezel still reads as a frame. A pane with no slab stands on its own ground,
     * which shows through the bezel as it does through the screen.
     */
    public static int fill(boolean glassActive, int glassTint, int surfaceColour) {
        if (!glassActive || (glassTint >>> 24) > 0) return 0;
        return surfaceColour | 0xFF000000;
    }
}
