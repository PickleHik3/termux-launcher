package com.termux.app.x11;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;

/**
 * How a Linux app is named inside the launcher's app catalogue: an {@link AppRef} whose package
 * is the reserved {@link #PACKAGE} and whose activity is the desktop file's id. Everything keyed
 * by {@code stableId} — usage ranking, pins, folders — works unchanged; the few places that ask
 * Android about a package check {@link #isLinuxApp} first.
 */
public final class X11Apps {

    /** Never a real Android package name: no dot, and colons are not allowed in one. */
    public static final String PACKAGE = "x11:linux";

    private X11Apps() {}

    @NonNull
    public static AppRef ref(@NonNull String desktopId) {
        return new AppRef(PACKAGE, desktopId);
    }

    public static boolean isLinuxApp(@NonNull AppRef ref) {
        return PACKAGE.equals(ref.packageName);
    }

    /** The desktop id behind a Linux app's ref. */
    @NonNull
    public static String desktopId(@NonNull AppRef ref) {
        return ref.activityName;
    }
}
