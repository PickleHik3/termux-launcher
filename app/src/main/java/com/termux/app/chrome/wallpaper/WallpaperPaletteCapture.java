package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * The four colours a generated background is drawn with: always its own shipped palette. Colours
 * flow from the wallpaper to the system theme, never the other way.
 */
public final class WallpaperPaletteCapture {

    public static final String MODE_OWN = "own";

    private WallpaperPaletteCapture() {}

    /** The background's own palette, as a copy so a caller cannot edit the shipped one. */
    @NonNull
    public static int[] own(@NonNull AnimatedWallpaper w) {
        return w.ownPalette().clone();
    }

}
