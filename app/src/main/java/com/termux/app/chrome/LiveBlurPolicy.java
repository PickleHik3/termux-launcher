package com.termux.app.chrome;

/**
 * When a glass surface shows the live picture of what is drawn beneath it ({@link LivePicture})
 * instead of the stored wallpaper frame ({@link StoredPicture}).
 *
 * <p>Live feeds the Glass refraction program, so it needs Glass refraction to be on and working
 * (Android 13, a wallpaper set from inside the launcher). It also needs the launcher to be the one
 * painting that wallpaper: a wallpaper Android composites behind the window is not in the
 * window's drawing, so a recording of what is beneath the glass would have no wallpaper in it.
 * Battery saver, the Low-memory preset and Reduce idle activity each put it out. When any term
 * fails the surface falls back to the stored frame, through the same draw.</p>
 *
 * <p>Pure: facts in, one answer out, so every term is testable without a window.</p>
 */
public final class LiveBlurPolicy {

    /** The first Android that runs the program Live feeds; the same as Glass refraction's. */
    public static final int MIN_SDK = FancierGlassPolicy.MIN_SDK;

    private LiveBlurPolicy() {}

    /** Whether the settings page shows the switch at all. */
    public static boolean offered(int sdkInt) {
        return sdkInt >= MIN_SDK;
    }

    /**
     * Whether glass surfaces take the live picture right now.
     *
     * @param sdkInt              the phone's Android
     * @param liveBlurOn          the stored Live blur switch
     * @param fancierGlassActive  Glass refraction is in effect
     *                            ({@link FancierGlassPolicy#active}: its switch, Android 13 and a
     *                            wallpaper the launcher set, on screen)
     * @param wallpaperSelfDrawn  the launcher paints the wallpaper itself
     *                            ({@link WallpaperBackdropPolicy.Mode#SELF_DRAWN})
     * @param powerSaveMode       battery saver is on
     * @param lowMemoryPreset     the Low-memory preset is on
     * @param lazy                Reduce idle activity is on
     */
    public static boolean live(int sdkInt, boolean liveBlurOn, boolean fancierGlassActive,
                               boolean wallpaperSelfDrawn, boolean powerSaveMode,
                               boolean lowMemoryPreset, boolean lazy) {
        return offered(sdkInt) && liveBlurOn && fancierGlassActive && wallpaperSelfDrawn
            && !powerSaveMode && !lowMemoryPreset && !lazy;
    }
}
