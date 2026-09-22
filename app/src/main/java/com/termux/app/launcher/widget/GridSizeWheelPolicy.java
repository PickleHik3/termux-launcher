package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * One digit wheel's arithmetic: how far a finger has to travel for the next number, where the
 * drag has taken the value, and the two numbers the wheel may never leave.
 *
 * <p>Its bounds are the settings sliders' own, so the Layout page and the wheel can never disagree
 * about what a grid is allowed to be — and the whole of it is arithmetic, so the clamps are tested
 * rather than watched.
 */
public final class GridSizeWheelPolicy {

    /** How far a finger travels for one number. */
    public static final float STEP_DP = 28f;

    private final int mMinimum;
    private final int mMaximum;

    public GridSizeWheelPolicy(int minimum, int maximum) {
        mMinimum = Math.min(minimum, maximum);
        mMaximum = Math.max(minimum, maximum);
    }

    /** The columns a widget grid may have. */
    @NonNull
    public static GridSizeWheelPolicy columns() {
        return new GridSizeWheelPolicy(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
    }

    /** The rows a widget grid may have. */
    @NonNull
    public static GridSizeWheelPolicy rows() {
        return new GridSizeWheelPolicy(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
    }

    public int minimum() {
        return mMinimum;
    }

    public int maximum() {
        return mMaximum;
    }

    public int clamp(int value) {
        return Math.max(mMinimum, Math.min(mMaximum, value));
    }

    /**
     * How many whole numbers a drag is worth. Dragging up counts up, and a drag has to cover the
     * whole step before it counts at all — half a step is not half a number.
     */
    public static int stepsFor(float dragPx, float density) {
        float step = STEP_DP * (density > 0f ? density : 1f);
        return (int) (-dragPx / step);
    }

    /**
     * What is left of a drag once its whole numbers are taken out, in pixels: how far the digits
     * have slid towards the next one. Negative while the finger is moving up.
     */
    public static float leftoverPx(float dragPx, float density) {
        float step = STEP_DP * (density > 0f ? density : 1f);
        return dragPx + stepsFor(dragPx, density) * step;
    }

    /** The value a drag of {@code dragPx} from {@code start} lands on. */
    public int valueFor(int start, float dragPx, float density) {
        return clamp(clamp(start) + stepsFor(dragPx, density));
    }
}
