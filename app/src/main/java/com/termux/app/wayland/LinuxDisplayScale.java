package com.termux.app.wayland;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayScaleSteps;

/**
 * The Wayland output scale the Linux windows screen asks for.
 *
 * <p>When the user has chosen a Display scale stop (the X11 Display setting,
 * {@link DisplayScaleSteps}), its percent is the scale, as on X11 Display: a stop of N% makes the
 * X screen 100/N of the real pixels, magnified N/100, and a Wayland output at scale N/100 is the
 * same. When they have not, the scale follows the screen's density ({@link #densityScale}), so an
 * app draws its text and controls at about the size Android draws its own.
 *
 * <p>"Not chosen" is read from the display's store: no {@code displayScale} stored, and no
 * resolution mode stored or the fresh-install one ({@code scaled}, written by {@code X11Defaults}
 * and by the settings page's mode row when it first shows). The rail writes {@code native} for
 * 100% and stores {@code displayScale} for every other stop, and the settings slider stores
 * {@code displayScale}, so any of the user's own choices, 100% included, is told apart.
 */
public final class LinuxDisplayScale {

    /** The density scale's bounds: never smaller than the screen's pixels, never past Android's largest bucket. */
    static final float MIN_DENSITY_SCALE = 1f;
    static final float MAX_DENSITY_SCALE = 4f;
    /** Density scales are snapped to quarters, which the fractional-scale protocol (120ths) carries exactly. */
    private static final float STEPS_PER_UNIT = 4f;

    private static final String KEY_SCALE = "displayScale";
    private static final String KEY_MODE = "displayResolutionMode";

    private LinuxDisplayScale() {}

    /** The output scale for a stored resolution mode and scale percent, on a stop of the rail. Pure. */
    public static float outputScale(@NonNull String resolutionMode, int scalePercent) {
        return DisplayScaleSteps.fromPreferences(resolutionMode, scalePercent) / 100f;
    }

    /**
     * The output scale for the store's values and the screen's {@code density}
     * ({@code DisplayMetrics.density}). Pure.
     *
     * @param storedMode    the stored resolution mode, or null when none is stored.
     * @param storedPercent the stored scale percent, or null when none is stored.
     */
    public static float outputScale(@Nullable String storedMode, @Nullable Integer storedPercent, float density) {
        if (!isChosen(storedMode, storedPercent)) return densityScale(density);
        // As X11 Display reads them: no stored mode is the store's default, the screen's pixels.
        return outputScale(storedMode == null ? DisplayScaleSteps.modeFor(100) : storedMode,
            storedPercent == null ? 100 : storedPercent);
    }

    /** Whether the stored values are the user's choice rather than a fresh install's. Pure. */
    static boolean isChosen(@Nullable String storedMode, @Nullable Integer storedPercent) {
        return storedPercent != null || (storedMode != null && !"scaled".equals(storedMode));
    }

    /**
     * The scale that draws a Linux app at Android's own size: {@code density} (1 at 160 dpi)
     * to the nearest quarter, a tie going to the smaller (2.625 at 420 dpi is 2.5, the rail's
     * top stop, leaving an app a little more room on a phone), between
     * {@link #MIN_DENSITY_SCALE} and {@link #MAX_DENSITY_SCALE}; 1 for an unusable density. Pure.
     */
    static float densityScale(float density) {
        if (!(density > 0f) || Float.isInfinite(density)) return MIN_DENSITY_SCALE;
        float snapped = (float) Math.ceil(density * STEPS_PER_UNIT - 0.5f) / STEPS_PER_UNIT;
        return Math.max(MIN_DENSITY_SCALE, Math.min(MAX_DENSITY_SCALE, snapped));
    }

    /** The scale to use now; the density scale when the store cannot be read. */
    public static float current(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        try {
            com.termux.x11.Prefs prefs = new com.termux.x11.Prefs(context.getApplicationContext());
            SharedPreferences store = prefs.get();
            String mode = store.contains(KEY_MODE) ? prefs.displayResolutionMode.get() : null;
            Integer percent = store.contains(KEY_SCALE) ? prefs.displayScale.get() : null;
            return outputScale(mode, percent, density);
        } catch (RuntimeException e) {
            return densityScale(density);
        }
    }
}
