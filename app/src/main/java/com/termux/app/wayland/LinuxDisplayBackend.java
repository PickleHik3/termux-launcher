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

    /** What one Linux app asks for; Default follows the main setting. */
    public enum Choice {
        DEFAULT(null), X11(VALUE_X11), WAYLAND(VALUE_WAYLAND);

        /** The stored value; null for Default, which is stored as nothing. */
        @Nullable public final String value;

        Choice(@Nullable String value) {
            this.value = value;
        }

        /** The choice {@code stored} names; anything unknown is Default. */
        @NonNull
        public static Choice parse(@Nullable String stored) {
            if (stored == null) return DEFAULT;
            String trimmed = stored.trim();
            for (Choice choice : values()) {
                if (choice.value != null && choice.value.equals(trimmed)) return choice;
            }
            return DEFAULT;
        }
    }

    /**
     * The one answer to which display a launch uses. A build without the Wayland host is X11. A
     * Wayland desktop ({@code waylandDesktop}) is Wayland, whatever else is set. A whole desktop in
     * a container ({@code session} and {@code inContainer}) stays on X11. Otherwise the app's own
     * {@code choice} wins and Default follows {@code main}. A whole X11 desktop on Wayland runs in a
     * rootful X server of its own ({@link WaylandDesktopServer}); an app inside a proot-distro
     * container opens as a window, its login carrying the socket ({@link LinuxWindowsProot}).
     */
    @NonNull
    public static LinuxDisplayBackend forApp(boolean waylandBuilt, @NonNull LinuxDisplayBackend main,
                                             @NonNull Choice choice, boolean session,
                                             boolean waylandDesktop, boolean inContainer) {
        if (!waylandBuilt) return X11;
        if (session && waylandDesktop) return WAYLAND;
        if (session && inContainer) return X11;
        switch (choice) {
            case X11: return X11;
            case WAYLAND: return WAYLAND;
            default: return main;
        }
    }

    /** Whether the app's page offers a Display row: where the choice can change a launch. */
    public static boolean choosable(boolean waylandBuilt, boolean session, boolean waylandDesktop,
                                    boolean inContainer) {
        return waylandBuilt && !(session && waylandDesktop) && !(session && inContainer);
    }
}
