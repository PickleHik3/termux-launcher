package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.chrome.GlassMotion;

/**
 * How long the editor's reveals take: the card, the floating pill and the outline overlay. Under
 * {@link GlassMotion#CLASSIC} they are the fixed times the editor always ran; a springy profile
 * (Mist) takes its own arrival and departure times instead. The gesture-tied timings (the peek
 * while a surface is dragged, the park travel, the selection rings) are not reveals and stay in
 * the controller.
 */
final class SurfaceEditorMotion {

    private SurfaceEditorMotion() {}

    /** The card's and the pill's rise, fade and settle. */
    static final long CLASSIC_REVEAL_MS = 180L;
    /** The gesture overlay's fade. */
    static final long CLASSIC_OVERLAY_FADE_MS = 200L;

    /** True when the card arrives and leaves through {@code GlassMotionPlayer} rather than a rise. */
    static boolean cardPlaysMotion(@NonNull GlassMotion motion) {
        return motion.springy();
    }

    static long revealMs(@NonNull GlassMotion motion, boolean showing) {
        if (!motion.springy()) return CLASSIC_REVEAL_MS;
        return showing ? motion.enterAlphaMs : motion.exitAlphaMs;
    }

    static long overlayFadeMs(@NonNull GlassMotion motion, boolean showing) {
        if (!motion.springy()) return CLASSIC_OVERLAY_FADE_MS;
        return showing ? motion.enterAlphaMs : motion.exitAlphaMs;
    }
}
