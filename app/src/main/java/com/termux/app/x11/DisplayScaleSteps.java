package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * The stops of the display window's scale rail, and how they map onto the display's resolution
 * preferences.
 *
 * <p>Scale is the one knob a running display applies at once — the X screen is re-sized as soon
 * as the preference changes, where text size (DPI) is read only when a server starts. The rail
 * offers a few stops rather than every percent: 100 is the screen's own pixels ("Same as the
 * screen"), the rest are the scaled mode at a size that still leaves a desktop app its width.
 */
public final class DisplayScaleSteps {

    /** The stops, in percent, from the top of the rail to its bottom. */
    public static final int[] STEPS = {100, 125, 150, 175, 200, 250};

    static final String MODE_NATIVE = "native";
    static final String MODE_SCALED = "scaled";

    private DisplayScaleSteps() { }

    /** The stop the current preferences amount to: 100 unless the scaled mode is on. */
    public static int fromPreferences(@NonNull String resolutionMode, int scalePercent) {
        if (!MODE_SCALED.equals(resolutionMode)) return STEPS[0];
        return nearest(scalePercent);
    }

    /** The resolution mode {@code step} asks for. */
    @NonNull
    public static String modeFor(int step) {
        return step <= STEPS[0] ? MODE_NATIVE : MODE_SCALED;
    }

    /** The stop closest to {@code percent}; ties go to the smaller stop. */
    public static int nearest(int percent) {
        int best = STEPS[0];
        for (int step : STEPS) {
            if (Math.abs(step - percent) < Math.abs(best - percent)) best = step;
        }
        return best;
    }

    /** Where {@code step} sits along the rail, 0 at the first stop and 1 at the last. */
    public static float fraction(int step) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == step) return i / (float) (STEPS.length - 1);
        }
        return fraction(nearest(step));
    }

    /** The stop a finger at {@code fraction} along the rail lands on. */
    public static int stepAt(float fraction) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        int index = Math.round(clamped * (STEPS.length - 1));
        return STEPS[index];
    }

    /**
     * A stop as a per-app scale value ({@link X11AppSettings#SCALES}): its percent as a factor,
     * "1", "1.25" … "2.5". The rail and the per-app Scale row offer the same stops.
     */
    @NonNull
    public static String scaleValue(int step) {
        return BigDecimal.valueOf(step).movePointLeft(2).stripTrailingZeros().toPlainString();
    }

    /** The stop a per-app scale value names, or 0 when it names none. */
    public static int stepOf(@Nullable String scaleValue) {
        if (scaleValue == null) return 0;
        for (int step : STEPS) {
            if (scaleValue(step).equals(scaleValue)) return step;
        }
        return 0;
    }

    /** The rail's read-out for a stop. */
    @NonNull
    public static String label(int step) {
        return step + "%";
    }
}
