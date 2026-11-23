package com.termux.app.wayland;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.x11.DisplayScaleSteps;

/**
 * The Wayland output scale the Linux windows screen asks for, from the same Display scale setting
 * X11 Display has ({@link DisplayScaleSteps}). On X11 Display a stop of N% makes the X screen
 * 100/N of the real pixels, magnified N/100 to fill the screen; a Wayland output at scale N/100
 * is the same: a window's logical size is its pixels divided by the scale. So the stop's percent
 * is the scale, and 100 % (the fresh-install setting) is 1.0.
 */
public final class LinuxDisplayScale {

    private LinuxDisplayScale() {}

    /** The output scale for a stored resolution mode and scale percent, on a stop of the rail. Pure. */
    public static float outputScale(@NonNull String resolutionMode, int scalePercent) {
        return DisplayScaleSteps.fromPreferences(resolutionMode, scalePercent) / 100f;
    }

    /** The scale the Display setting asks for now; 1.0 when the store cannot be read. */
    public static float current(@NonNull Context context) {
        try {
            com.termux.x11.Prefs prefs = new com.termux.x11.Prefs(context.getApplicationContext());
            return outputScale(prefs.displayResolutionMode.get(), prefs.displayScale.get());
        } catch (RuntimeException e) {
            return outputScale(DisplayScaleSteps.modeFor(100), 100);
        }
    }
}
