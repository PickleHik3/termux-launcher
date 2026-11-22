package com.termux.app.terminal.extrakeys;

/**
 * Where the Home pill may rest and where it does: pure geometry in pixels, no views. The legal
 * area is the window minus the system insets and a gutter, below Home's status bar and above the
 * accessory stack; the A-Z tab's rect is the one place inside it the pill steps aside for.
 *
 * <p>The spot is stored as the pill centre's fractions of the window, so it survives rotation; a
 * spot a rotation or a taller dock makes illegal is clamped when shown and the stored fractions
 * stay, so the pill returns when room comes back. The hot calls ({@link #place}, {@link #settle},
 * {@link #chipTopLeft}) write into a caller's array and allocate nothing.
 */
public final class HomePillSpot {

    /** How far in from the legal area's right edge the pill first rests. */
    public static final float DEFAULT_INSET_DP = 12f;
    /** The gutter kept clear inside the system insets. */
    public static final float GUTTER_DP = 16f;
    /** The air between the pill and its chip. */
    public static final float CHIP_GAP_DP = 10f;
    public static final float CHIP_HEIGHT_DP = 34f;
    public static final float PILL_HEIGHT_DP = 46f;
    public static final float PILL_RADIUS_DP = 23f;
    /** The pill's corner radius once it is docked for the keyboard. */
    public static final float DOCKED_RADIUS_DP = 16f;

    private HomePillSpot() {}

    /** An axis-aligned rect in pixels. */
    public static final class Rect {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        public float centerX() {
            return (left + right) * 0.5f;
        }

        public float centerY() {
            return (top + bottom) * 0.5f;
        }
    }

    /** Everything {@link #place} needs to know about the window. Built once per layout, not per frame. */
    public static final class Area {
        public final float windowWidth;
        public final float windowHeight;
        /** The legal rect for the pill. It may be smaller than the pill. */
        public final Rect legal;
        /** The A-Z tab, or null when it is not drawn. */
        public final Rect az;
        /** How far in from {@code legal.right} the default spot sits. */
        public final float defaultInset;

        public Area(float windowWidth, float windowHeight, Rect legal, Rect az, float defaultInset) {
            this.windowWidth = windowWidth;
            this.windowHeight = windowHeight;
            this.legal = legal;
            this.az = az;
            this.defaultInset = defaultInset;
        }
    }

    /**
     * The area for a window: inside the insets and the gutter, below the status bar, above the
     * accessory stack's top. {@code az} may be null.
     */
    public static Area area(float windowWidth, float windowHeight,
                            float insetLeft, float insetTop, float insetRight, float insetBottom,
                            float gutter, float statusBarBottom, float stackTop,
                            Rect az, float defaultInset) {
        float left = insetLeft + gutter;
        float right = windowWidth - insetRight - gutter;
        float top = Math.max(insetTop + gutter, statusBarBottom);
        float bottom = Math.min(windowHeight - insetBottom - gutter, stackTop);
        return new Area(windowWidth, windowHeight, new Rect(left, top, right, bottom), az, defaultInset);
    }

    /**
     * The pill's top-left for the stored centre fractions ({@code fractions} {x, y}, or null for the
     * default first spot), written to {@code out[0]} (left) and {@code out[1]} (top).
     */
    public static void place(float[] fractions, float pillW, float pillH, Area area, float[] out) {
        Rect legal = area.legal;
        float centerX;
        float centerY;
        if (fractions == null || fractions.length < 2
            || Float.isNaN(fractions[0]) || Float.isNaN(fractions[1])) {
            centerX = legal.right - area.defaultInset - pillW * 0.5f;
            centerY = legal.centerY();
        } else {
            centerX = fractions[0] * area.windowWidth;
            centerY = fractions[1] * area.windowHeight;
        }
        settle(centerX, centerY, pillW, pillH, area, out);
    }

    /**
     * The same legal answer for a centre in pixels, which is what a drag clamps live: into the
     * legal area, off the A-Z tab, centred in the window on an axis where the area is too small.
     */
    public static void settle(float centerX, float centerY, float pillW, float pillH,
                              Area area, float[] out) {
        Rect legal = area.legal;
        float left = centerX - pillW * 0.5f;
        float top = centerY - pillH * 0.5f;
        boolean fitsX = legal.width() >= pillW;
        boolean fitsY = legal.height() >= pillH;
        left = fitsX ? clamp(left, legal.left, legal.right - pillW) : (area.windowWidth - pillW) * 0.5f;
        top = fitsY ? clamp(top, legal.top, legal.bottom - pillH) : (area.windowHeight - pillH) * 0.5f;
        Rect az = area.az;
        if (az != null && left < az.right && left + pillW > az.left
            && top < az.bottom && top + pillH > az.top) {
            float toLeft = az.left - pillW;
            float toRight = az.right;
            boolean leftFits = toLeft >= legal.left;
            boolean rightFits = toRight + pillW <= legal.right;
            boolean preferLeft = Math.abs(left - toLeft) <= Math.abs(left - toRight);
            if (leftFits && (preferLeft || !rightFits)) left = toLeft;
            else if (rightFits) left = toRight;
        }
        out[0] = left;
        out[1] = top;
    }

    /** The centre fractions of a pill at {@code pillLeft/pillTop}, in [0, 1], to {@code out[0..1]}. */
    public static void fractionsOf(float pillLeft, float pillTop, float pillW, float pillH,
                                   float windowW, float windowH, float[] out) {
        out[0] = windowW <= 0f ? 0.5f : clamp((pillLeft + pillW * 0.5f) / windowW, 0f, 1f);
        out[1] = windowH <= 0f ? 0.5f : clamp((pillTop + pillH * 0.5f) / windowH, 0f, 1f);
    }

    /**
     * The chip's top-left: centred above the pill, below it when above would cross the legal
     * area's top, then clamped sideways into the legal area. Written to {@code out[0]} (left) and
     * {@code out[1]} (top).
     */
    public static void chipTopLeft(float pillLeft, float pillTop, float pillW, float pillH,
                                   float chipW, float chipH, float gap, Area area, float[] out) {
        Rect legal = area.legal;
        float top = pillTop - gap - chipH;
        if (top < legal.top) top = pillTop + pillH + gap;
        float left = pillLeft + (pillW - chipW) * 0.5f;
        float maxLeft = legal.right - chipW;
        left = Math.max(legal.left, Math.min(left, maxLeft));
        out[0] = left;
        out[1] = top;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
