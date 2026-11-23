package com.termux.app.launcher.widget.builtin;

/**
 * How the 1×1 art fits its card: the Battery's ring and the analog Clock's dial fill the card to
 * the inset, and the Battery's number is sized to the ring's inner disc, never to the card or to
 * the font scale. Sizes are in pixels; text metrics are per pixel of text size, so the arithmetic
 * holds at any density and any font scale. Android-free.
 */
final class OneByOneFit {
    private OneByOneFit() { }

    /** The content inset at 1×1, in dp: the one-row and 1×1 inset of the widget shell. */
    static final int INSET_DP = 12;

    /** The clear band kept between the number and the inner disc's rim, as a share of its diameter. */
    static final float RING_CLEAR = 0.06f;

    /** The side of the square art that fills a {@code width}×{@code height} card to the inset. */
    static int artSide(int widthPx, int heightPx, int insetPx) {
        return Math.max(0, Math.min(widthPx, heightPx) - 2 * insetPx);
    }

    /**
     * The largest scale at which a {@code width}×{@code height} box, centred, fits inside a circle
     * of {@code diameter}: its corners then sit on the rim.
     */
    static float scaleIntoCircle(float width, float height, float diameter) {
        if (diameter <= 0f || (width <= 0f && height <= 0f)) return 0f;
        return diameter / (float) Math.hypot(width, height);
    }

    /** Whether a centred {@code width}×{@code height} box lies inside a circle of {@code diameter}. */
    static boolean fitsCircle(float width, float height, float diameter) {
        return width * width + height * height <= diameter * diameter * 1.0001f;
    }

    /**
     * The text size, in pixels, at which the ring's stack (a mark above the number, with a gap
     * between) fits a disc of {@code innerDiameter} less the {@link #RING_CLEAR} band. Every
     * metric is the ink at a number text size of one pixel; a stack without a mark passes zeros.
     */
    static float ringNumberSize(float innerDiameter, float numberWidth, float numberHeight,
                                float markWidth, float markHeight, float gap) {
        float width = Math.max(numberWidth, markWidth);
        float height = numberHeight + (markHeight > 0f ? gap + markHeight : 0f);
        return scaleIntoCircle(width, height, innerDiameter * (1f - 2f * RING_CLEAR));
    }
}
