package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

/**
 * General › Animation speed (F3): one multiplier on the launcher's own motion, read through this
 * one accessor. Slower stretches every duration by half again, Faster takes a third off, Off
 * makes the motion instant. The phone's reduce-motion setting ({@link ReducedMotion}) still wins
 * over all of these.
 *
 * <p>Readers, one place each: Settings' screen transitions and row ring
 * ({@code SettingsMotion}), the wall's page and window slide ({@code PaneWallLayout.startSlide}),
 * the command palette's springs ({@code TerminalCommandPaletteController.kick}), the floating
 * windows' depth ramp ({@code Motion.floatDepthMs}), and window and session switches with the
 * window bar's highlight ({@code TermuxActivity.windowSwitchMs}, {@code TerminalWindowBar}). Each reads it when a motion starts, never per
 * frame.
 */
public final class AnimationSpeed {

    private AnimationSpeed() {}

    /** The preference, in the app's default store, holding one of the values below. */
    public static final String KEY = "animation_speed";
    public static final String SLOWER = "slower";
    public static final String NORMAL = "normal";
    public static final String FASTER = "faster";
    public static final String OFF = "off";

    public static final float SLOWER_MULTIPLIER = 1.5f;
    public static final float FASTER_MULTIPLIER = 0.67f;
    /**
     * The fastest a spring is ever asked to run, as a share of its own time: Off on a spring-driven
     * motion lands it within a frame or two instead of dividing by zero.
     */
    static final float MIN_SPRING_TIME = 0.05f;

    /** The duration multiplier a stored value means: 1.5, 1, 0.67 or 0. Unknown means Normal. */
    public static float multiplierFor(@Nullable String value) {
        if (SLOWER.equals(value)) return SLOWER_MULTIPLIER;
        if (FASTER.equals(value)) return FASTER_MULTIPLIER;
        if (OFF.equals(value)) return 0f;
        return 1f;
    }

    @NonNull
    public static String get(@NonNull Context context) {
        try {
            String value = preferences(context).getString(KEY, NORMAL);
            return value == null ? NORMAL : value;
        } catch (RuntimeException e) {
            return NORMAL;
        }
    }

    public static void set(@NonNull Context context, @Nullable String value) {
        String stored = SLOWER.equals(value) || FASTER.equals(value) || OFF.equals(value) ? value : NORMAL;
        preferences(context).edit().putString(KEY, stored).apply();
    }

    /** The multiplier in effect: 1.5, 1, 0.67, or 0 for Off. */
    public static float multiplier(@NonNull Context context) {
        return multiplierFor(get(context));
    }

    public static boolean isOff(@NonNull Context context) {
        return multiplier(context) == 0f;
    }

    /** {@code durationMs} at the chosen speed; 0 for Off. */
    public static long scale(@NonNull Context context, long durationMs) {
        return scaleDuration(multiplier(context), durationMs);
    }

    public static long scaleDuration(float multiplier, long durationMs) {
        return Math.round(durationMs * (double) multiplier);
    }

    /**
     * The share of its own time a spring-driven motion takes at the chosen speed: divide a spring's
     * frequency by it, or multiply its clock's step by its inverse. Never 0, so Off is very quick
     * rather than undefined.
     */
    public static float springTime(@NonNull Context context) {
        return Math.max(MIN_SPRING_TIME, multiplier(context));
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE);
    }
}
