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

    /** The least the wheel is ever wide: room for two digits at the default text size. */
    public static final float MIN_WIDTH_DP = 52f;

    /** The air between two numbers, as a share of the digits' size, once the text is large. */
    private static final float SPACING_EM = 0.6f;

    /** The air either side of the widest number, as a share of the digits' size. */
    private static final float SIDE_PADDING_EM = 0.6f;

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
     * The distance from one number to the next, in pixels: the digits' own height and some air,
     * so large text never stacks the numbers on top of each other, but never less than the
     * fixed {@link #STEP_DP} the wheel has always had.
     */
    public static float pitchPx(float density, float glyphHeightPx, float textSizePx) {
        float minimum = STEP_DP * (density > 0f ? density : 1f);
        return Math.max(minimum, glyphHeightPx + SPACING_EM * textSizePx);
    }

    /** The wheel's width in pixels: its widest number and some padding, never under the minimum. */
    public static float widthPx(float density, float widestNumberPx, float textSizePx) {
        float minimum = MIN_WIDTH_DP * (density > 0f ? density : 1f);
        return Math.max(minimum, widestNumberPx + 2f * SIDE_PADDING_EM * textSizePx);
    }

    /** How many digits the longest number the wheel can show has. */
    public int maximumDigits() {
        return String.valueOf(Math.max(Math.abs(mMinimum), Math.abs(mMaximum))).length();
    }

    /**
     * How many whole numbers a drag is worth. Dragging up counts up, and a drag has to cover the
     * whole step before it counts at all — half a step is not half a number.
     */
    public static int stepsFor(float dragPx, float density) {
        return stepsForPitch(dragPx, STEP_DP * (density > 0f ? density : 1f));
    }

    /** {@link #stepsFor(float, float)} for a wheel whose numbers are {@code pitchPx} apart. */
    public static int stepsForPitch(float dragPx, float pitchPx) {
        return (int) (-dragPx / pitchPx);
    }

    /**
     * What is left of a drag once its whole numbers are taken out, in pixels: how far the digits
     * have slid towards the next one. Negative while the finger is moving up.
     */
    public static float leftoverPx(float dragPx, float density) {
        return leftoverForPitch(dragPx, STEP_DP * (density > 0f ? density : 1f));
    }

    /** {@link #leftoverPx(float, float)} for a wheel whose numbers are {@code pitchPx} apart. */
    public static float leftoverForPitch(float dragPx, float pitchPx) {
        return dragPx + stepsForPitch(dragPx, pitchPx) * pitchPx;
    }

    /** The value a drag of {@code dragPx} from {@code start} lands on. */
    public int valueFor(int start, float dragPx, float density) {
        return clamp(clamp(start) + stepsFor(dragPx, density));
    }

    /** {@link #valueFor(int, float, float)} for a wheel whose numbers are {@code pitchPx} apart. */
    public int valueForPitch(int start, float dragPx, float pitchPx) {
        return clamp(clamp(start) + stepsForPitch(dragPx, pitchPx));
    }
}
