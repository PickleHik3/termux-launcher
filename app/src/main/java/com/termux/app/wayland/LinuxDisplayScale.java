package com.termux.app.wayland;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayScaleSteps;
import com.termux.app.x11.X11AppSettings;

/**
 * The scales of Linux windows under Wayland Display.
 *
 * <p>Every window follows the screen's density ({@link #densityScale}), so an app draws its text
 * and controls at about the size Android draws its own. An app's own Scale (the per-app value in
 * {@link X11AppSettings}, set from its settings page or from the scale rail on the Linux windows
 * screen) replaces that for its windows. The X11 Display's own scale setting is not read here.
 *
 * <p>A scale of N/100 is the rail's stop of N%, as on X11 Display: the window is told it is
 * 100/N of its pixels wide, drawn at N/100.
 */
public final class LinuxDisplayScale {

    /** The density scale's bounds: never smaller than the screen's pixels, never past Android's largest bucket. */
    static final float MIN_DENSITY_SCALE = 1f;
    static final float MAX_DENSITY_SCALE = 4f;
    /** Density scales are snapped to quarters, which the fractional-scale protocol (120ths) carries exactly. */
    private static final float STEPS_PER_UNIT = 4f;

    private LinuxDisplayScale() {}

    /**
     * The scale a window draws at: its app's own Scale ({@code appScale}, one of
     * {@link X11AppSettings#SCALES}) when it has one, else the density scale of a screen at
     * {@code density} ({@code DisplayMetrics.density}). Pure.
     */
    public static float windowScale(@Nullable String appScale, float density) {
        int step = DisplayScaleSteps.stepOf(appScale);
        return step > 0 ? stepScale(step) : densityScale(density);
    }

    /** A rail stop's scale: 150% is 1.5. Pure. */
    public static float stepScale(int step) {
        return step / 100f;
    }

    /** The rail stop that shows {@code scale}: the nearest one (a density scale may sit between stops). Pure. */
    public static int railStep(float scale) {
        if (!(scale > 0f) || Float.isInfinite(scale)) return DisplayScaleSteps.STEPS[0];
        return DisplayScaleSteps.nearest(Math.round(scale * 100f));
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

    /**
     * The screen's density scale: the host's output scale, the scale X apps under Xwayland are
     * told, and every window's scale until its app has its own.
     */
    public static float current(@NonNull Context context) {
        return densityScale(context.getResources().getDisplayMetrics().density);
    }
}
