package com.termux.app.wayland;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayScaleSteps;
import com.termux.app.x11.X11AppSettings;

/**
 * The scales of Linux windows under Wayland Display.
 *
 * <p>A window's default follows the screen's density ({@link #densityScale}), stepping down
 * so the window is wide enough for a desktop app ({@link #defaultScale}). An app's own Scale (the per-app value in
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

    /**
     * A desktop app wants at least this many points of width ({@code widthPx / scale}) to lay out
     * without running off the edge; the default scale steps down the rail until a window has it.
     */
    static final float MIN_COMFORTABLE_WIDTH = 520f;
    /** The default never draws smaller than this fraction of the screen's density, so text stays readable. */
    static final float MIN_TEXT_FRACTION = 0.7f;

    private LinuxDisplayScale() {}

    /**
     * The scale a window draws at: its app's own Scale ({@code appScale}, one of
     * {@link X11AppSettings#SCALES}) when it has one, else the density scale of a screen at
     * {@code density} ({@code DisplayMetrics.density}). Pure.
     */
    public static float windowScale(@Nullable String appScale, float density, int widthPx) {
        int step = DisplayScaleSteps.stepOf(appScale);
        return step > 0 ? stepScale(step) : defaultScale(density, widthPx);
    }

    /**
     * The default scale of a window {@code widthPx} wide on a screen at {@code density}: the
     * largest rail stop that is not above the density scale and leaves the window at least
     * {@link #MIN_COMFORTABLE_WIDTH} points, but not below {@link #MIN_TEXT_FRACTION} of the
     * density (snapped up to a stop) and never below 1; the floor wins a conflict. An unknown
     * width ({@code widthPx <= 0}) uses the density scale alone. Pure.
     */
    static float defaultScale(float density, int widthPx) {
        float dens = densityScale(density);
        int[] steps = DisplayScaleSteps.STEPS;
        int cap = steps[0];
        for (int step : steps) {
            if (stepScale(step) <= dens) cap = step;
        }
        if (widthPx <= 0) return dens;
        int chosen = steps[0];
        for (int step : steps) {
            if (step <= cap && widthPx / stepScale(step) >= MIN_COMFORTABLE_WIDTH) chosen = step;
        }
        float floor = Math.max(1f, MIN_TEXT_FRACTION * dens);
        int floorStep = steps[steps.length - 1];
        for (int i = steps.length - 1; i >= 0; i--) {
            if (stepScale(steps[i]) >= floor - 1e-4f) floorStep = steps[i];
        }
        return stepScale(Math.max(chosen, floorStep));
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
     * The screen's default scale: the host's output scale, the scale X apps under Xwayland are
     * told, and the desktop's. The width is the screen's shorter side, so it does not change with
     * rotation (the server-wide scale cannot follow a window). A Wayland window computes its own
     * default from its own width; this is the first value before it has one.
     */
    public static float current(@NonNull Context context) {
        android.util.DisplayMetrics m = context.getResources().getDisplayMetrics();
        return defaultScale(m.density, Math.min(m.widthPixels, m.heightPixels));
    }
}
