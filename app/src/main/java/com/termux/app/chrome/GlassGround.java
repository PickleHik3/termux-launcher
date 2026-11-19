package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * Prototype (#283, ADR 0031 section 5): the chrome's glass draws the ground of the page beneath
 * it. A bar's glass covers the ground strip with its own frame (the stored pre-blurred wallpaper),
 * so over a dimmed page it stays as bright as on Home; with this on, the bar draws the strip's
 * bands over its frame and under its tint, at its own place on screen, so the seam passes under
 * the glass while a page edge does. Pure: the arithmetic {@link GroundStripView#drawUnder} draws
 * by, and which bands' ink then stands on the page's dim although they draw a frame.
 */
public final class GlassGround {

    /**
     * The prototype's gate. Off, every glass draws as before and the ink measures a framed band
     * over no dim: the measured baseline is the same build with this false.
     */
    public static final boolean ON = true;

    private GlassGround() {}

    /**
     * Whether {@code band}'s glass draws the page's ground over its frame (the status bar, the
     * window bar and the dock): its ink and even-out are then measured over the page's dim even
     * when its frame is up. The keyboard's stack does not draw it.
     */
    public static boolean drawsGround(@NonNull GlassBackdropCache.Band band) {
        if (!ON) return false;
        switch (band) {
            case STATUS_BAR:
            case WINDOW_BAR:
            case SESSION_CHIP:
            case DOCK:
                return true;
            default:
                return false;
        }
    }

    /**
     * The ground at {@code wallX} (the wall's px, the bands' own space): the band holding it, the
     * nearest band outside the viewport, transparent with no bands. What a glass draws there.
     */
    public static int groundAt(@NonNull GroundBands bands, float wallX) {
        int count = bands.count();
        if (count == 0) return 0;
        for (int i = 0; i < count; i++) {
            if (wallX < bands.end(i)) return bands.color(i);
        }
        return bands.color(count - 1);
    }

    /**
     * Band {@code band}'s left edge in a glass {@code width} px wide whose left edge stands at
     * {@code glassLeftInWall} in the wall's px: the first band runs out to the glass's left, as the
     * strip runs it out to the window's, and the span is clamped to the glass.
     */
    public static float spanLeft(@NonNull GroundBands bands, int band, float glassLeftInWall,
                                 float width) {
        float x = band == 0 ? 0f : bands.start(band) - glassLeftInWall;
        return Math.max(0f, Math.min(width, x));
    }

    /** {@link #spanLeft}'s right edge: the last band runs out to the glass's right. */
    public static float spanRight(@NonNull GroundBands bands, int band, float glassLeftInWall,
                                  float width) {
        float x = band == bands.count() - 1 ? width : bands.end(band) - glassLeftInWall;
        return Math.max(0f, Math.min(width, x));
    }
}
