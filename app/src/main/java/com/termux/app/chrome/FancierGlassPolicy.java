package com.termux.app.chrome;

import android.os.Build;

/**
 * When the Fancier Glass switch does anything.
 *
 * <p>Three facts decide it, and the same rule answers the settings page (whether to offer the
 * switch, and whether to offer it live) and the chrome (whether a glass surface refracts). The
 * switch itself stays wherever the user left it; only its effect comes and goes with the phone and
 * the wallpaper, so a wallpaper picked from inside the launcher later turns it on without another
 * trip to Settings.</p>
 *
 * <p>Pure: three booleans in, one out, so every case is testable without a window.</p>
 */
public final class FancierGlassPolicy {

    /** The first Android that runs an AGSL {@code RuntimeShader}; below it there is nothing to switch on. */
    public static final int MIN_SDK = Build.VERSION_CODES.TIRAMISU;

    private FancierGlassPolicy() {}

    /** Whether the settings page shows the switch at all. Below {@link #MIN_SDK} it is hidden, not disabled. */
    public static boolean offered(int sdkInt) {
        return sdkInt >= MIN_SDK;
    }

    /**
     * Whether the switch can be flipped: it needs a wallpaper set from inside the launcher, whose
     * exact pixels the glass can read. With any other wallpaper it is shown disabled, with the hint.
     */
    public static boolean flippable(int sdkInt, boolean managedWallpaperOnScreen) {
        return offered(sdkInt) && managedWallpaperOnScreen;
    }

    /**
     * Whether every glass surface refracts right now.
     *
     * @param toggleOn                 the stored switch
     * @param managedWallpaperOnScreen the wallpaper on screen is the one the in-app picker set,
     *                                 and its exact copy is still on disk
     */
    public static boolean active(int sdkInt, boolean toggleOn, boolean managedWallpaperOnScreen) {
        return flippable(sdkInt, managedWallpaperOnScreen) && toggleOn;
    }
}
