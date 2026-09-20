package com.termux.app;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * Whether the phone has been told to stop animating.
 *
 * <p>One reading of the system's animator scale, shared by everything that has to answer the
 * question: the launcher's own chrome, and the help overlay's gesture demonstration, which draws a
 * static cue instead of a moving finger when it is on.
 */
public final class ReducedMotion {

    private ReducedMotion() {}

    /** True when the phone is set to play no animations at all. */
    public static boolean isEnabled(@NonNull Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
