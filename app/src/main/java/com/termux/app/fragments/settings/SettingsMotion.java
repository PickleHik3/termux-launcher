package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.ReducedMotion;

/** How Settings moves: whether to move at all, and how long a move takes. */
public final class SettingsMotion {

    private SettingsMotion() {}

    /** True when Settings should cut instead of animating: the phone's reduce motion. */
    public static boolean isInstant(@NonNull Context context) {
        return ReducedMotion.isEnabled(context);
    }

    /** A duration for Settings' own motion. */
    public static long scaled(@NonNull Context context, long durationMs) {
        return durationMs;
    }
}
