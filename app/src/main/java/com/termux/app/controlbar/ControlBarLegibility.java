package com.termux.app.controlbar;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassInk;
import com.termux.app.glass.LegibilityBand;
import com.termux.app.glass.LegibilityBlend;
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

    /**
     * Measures {@code page} at this rest if it has nothing fresh kept, so a crossing toward it
     * finds both ends kept (ADR 0029 section 9: a page never visited is measured once, at rest,
     * before its first crossing). Nothing in a crossing. Whether it was measured.
     */
    public static boolean prepare(@NonNull LegibilityStore store, long page, int left, int top,
                                  int right, int bottom, int look, @NonNull Measure measure) {
        if (!store.needsMeasure(page, LegibilityBand.DOCK, left, top, right, bottom, look)) return false;
        int face = measure.faceUnderGlass();
        if ((face >>> 24) == 0) return false;
        return store.recordFace(page, LegibilityBand.DOCK, left, top, right, bottom, look, face);
    }

    /** Measures the face under the bar's glass at a rect of the wallpaper's frame. */
    public interface RectMeasure {
        @ColorInt
        int faceUnderGlass(int left, int top, int right, int bottom);
    }

    /**
     * {@link #face} for a page that stands {@code panPx} into a panning wallpaper's frame (a Home
     * page's own place on the row, {@code WallParallax#offsetPx}): measured, and kept, at the bar's
     * rect shifted there, so each Home page reads its own wallpaper and not the one under the page
     * in front (review P2, 11-28: Home 2 kept Home 1's face). 0 for a wallpaper that does not pan.
     */
    @ColorInt
    public static int face(@NonNull LegibilityStore store, long page, int left, int top, int right,
                           int bottom, int look, float panPx, @NonNull RectMeasure measure) {
        int pan = Math.round(panPx);
        return face(store, page, left + pan, top, right + pan, bottom, look,
            () -> measure.faceUnderGlass(left + pan, top, right + pan, bottom));
    }

    /** {@link #prepare} at {@code page}'s own place on a panning wallpaper ({@link #face(LegibilityStore, long, int, int, int, int, int, float, RectMeasure)}). */
    public static boolean prepare(@NonNull LegibilityStore store, long page, int left, int top,
                                  int right, int bottom, int look, float panPx,
                                  @NonNull RectMeasure measure) {
        int pan = Math.round(panPx);
        return prepare(store, page, left + pan, top, right + pan, bottom, look,
            () -> measure.faceUnderGlass(left + pan, top, right + pan, bottom));
    }

    /**
     * The bar's ink at each end of a crossing from {@code here} to {@code there}, chosen on the
     * faces each page kept at its rest ({here, there}); null when either end has none kept, and
     * the ink then stands as it is until the landing. Nothing is measured.
     */
    @Nullable
    public static int[] crossingInks(@NonNull LegibilityStore store, long here, long there,
                                     @ColorInt int themeInk, double target) {
        LegibilityCorrection from = store.peek(here, LegibilityBand.DOCK);
        LegibilityCorrection to = store.peek(there, LegibilityBand.DOCK);
        if (from == null || to == null) return null;
        return new int[] {ink(from.face(0), themeInk, target), ink(to.face(0), themeInk, target)};
    }

    /** The bar's ink {@code share} of the way across (ADR 0029 section 8: ink blends with the glass). */
    @ColorInt
    public static int blend(@ColorInt int here, @ColorInt int there, float share) {
        return LegibilityBlend.color(here, there, share);
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
