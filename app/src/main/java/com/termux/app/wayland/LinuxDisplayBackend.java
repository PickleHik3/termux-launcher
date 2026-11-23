package com.termux.app.wayland;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Which display a Linux app tapped in the drawer opens under: the Settings choice
 * (Terminal › Linux apps › Display), read against what this build carries. The drawer's runner
 * asks {@link #forApp} once per tap; nothing else branches on the stored value.
 */
public enum LinuxDisplayBackend {
    /** X11 Display: the one X screen on the Display page. The default. */
    X11,
    /** Wayland Display: each app in its own window on the Linux windows screen. */
    WAYLAND;

    /** The stored values, as the preference's entry values. */
    public static final String VALUE_X11 = "x11";
    public static final String VALUE_WAYLAND = "wayland";

    /**
     * The backend {@code stored} names, in a build that has the Wayland host or not. Anything but
     * {@link #VALUE_WAYLAND} in a build with the host is X11 Display, and a build without it is
     * always X11 Display.
     */
    @NonNull
    public static LinuxDisplayBackend resolve(@Nullable String stored, boolean waylandBuilt) {
        if (!waylandBuilt || stored == null) return X11;
        return VALUE_WAYLAND.equals(stored.trim()) ? WAYLAND : X11;
    }

    /** The backend the user chose, in this build. */
    @NonNull
    public static LinuxDisplayBackend current(@NonNull Context context) {
        if (!BuildConfig.WAYLAND_DISPLAY) return X11;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return resolve(preferences == null ? null : preferences.getLinuxDisplayBackend(), true);
    }

    /**
     * The backend one launch uses. Two kinds of app stay on X11 Display even when Wayland Display
     * is chosen, for now: a whole desktop session ({@code session}), which needs a rootful X
     * screen of its own inside a window and is a later slice; and an app inside a proot-distro
     * container ({@code inDistro}), whose login forwards neither the Wayland socket nor
     * {@code WAYLAND_DISPLAY} yet.
     */
    @NonNull
    public static LinuxDisplayBackend forApp(@NonNull LinuxDisplayBackend chosen, boolean session,
                                             boolean inDistro) {
        if (chosen != WAYLAND || session || inDistro) return X11;
        return WAYLAND;
    }
}
