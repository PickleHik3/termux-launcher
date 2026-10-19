package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.AnimationSpeed;
import com.termux.app.ReducedMotion;

/**
 * How Settings moves: whether to move at all, and how long a move takes. The phone's reduce
 * motion and General › Animation speed at Off both make every move an instant cut; otherwise
 * durations follow the animation speed.
 */
public final class SettingsMotion {

    private SettingsMotion() {}

    /** True when Settings should cut instead of animating. */
    public static boolean isInstant(@NonNull Context context) {
        return ReducedMotion.isEnabled(context) || AnimationSpeed.isOff(context);
    }

    /** A duration for Settings' own motion, at the chosen animation speed. */
    public static long scaled(@NonNull Context context, long durationMs) {
        return AnimationSpeed.scale(context, durationMs);
    }
}
