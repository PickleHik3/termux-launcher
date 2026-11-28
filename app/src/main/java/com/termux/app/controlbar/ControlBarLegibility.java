package com.termux.app.controlbar;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.GlassInk;
import com.termux.app.glass.LegibilityBand;
import com.termux.app.glass.LegibilityCorrection;
import com.termux.app.glass.LegibilityStore;

/**
 * The control bar's legibility correction (ADR 0029 section 9): the bar is the dock's glass entry
 * under Terl, so it keeps the dock band's correction, one per page, measured at the page's rest
 * and never in a crossing; its ink is chosen on that face as v1's dock band chooses the extra
 * keys' ({@link GlassInk#faceInk}), not the theme's colour as it is.
 */
public final class ControlBarLegibility {

    /** Measures the face the bar's content stands on under its glass now; transparent when unread. */
    public interface Measure {
        @ColorInt
        int faceUnderGlass();
    }

    private ControlBarLegibility() {}

    /**
     * The face kept for the bar on {@code page} at this rest rectangle and look: the one the store
     * holds, else measured now and kept. In a crossing the store measures nothing: the face last
     * kept for the page, whatever rect it was measured at. Transparent when there is none.
     */
    @ColorInt
    public static int face(@NonNull LegibilityStore store, long page, int left, int top, int right,
                           int bottom, int look, @NonNull Measure measure) {
        if (store.crossing()) {
            LegibilityCorrection kept = store.peek(page, LegibilityBand.DOCK);
            return kept == null ? 0 : kept.face(0);
        }
        LegibilityCorrection kept = store.get(page, LegibilityBand.DOCK, left, top, right, bottom, look);
        if (kept != null) return kept.face(0);
        int face = measure.faceUnderGlass();
        if ((face >>> 24) == 0) return 0;
        store.recordFace(page, LegibilityBand.DOCK, left, top, right, bottom, look, face);
        return face;
    }

    /** The bar's ink on {@code face}: the theme's held to {@code target}, or the theme's with no face. */
    @ColorInt
    public static int ink(@ColorInt int face, @ColorInt int themeInk, double target) {
        if ((face >>> 24) == 0) return themeInk;
        return GlassInk.faceInk(face, themeInk, target);
    }

    /** What the bar's face depends on beyond the wallpaper and its rect: its Opacity, Blur and the tint. */
    public static int look(int opacityPercent, int blurDp, int tintPercent) {
        return (opacityPercent * 131 + blurDp) * 31 + tintPercent;
    }
}
