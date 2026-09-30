package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure choices behind the wallpaper picker sheet, kept apart from views so they can be tested. */
public final class WallpaperPickerLogic {

    private WallpaperPickerLogic() {}

    /**
     * The palette mode a tile starts on: the stored mode for the stored background, Material for
     * every other tile (and for a stored mode that is not recognised).
     */
    @NonNull
    public static String initialMode(@NonNull String tileId, @Nullable String storedId, @Nullable String storedMode) {
        if (tileId.equals(storedId) && WallpaperPaletteCapture.MODE_OWN.equals(storedMode)) {
            return WallpaperPaletteCapture.MODE_OWN;
        }
        return WallpaperPaletteCapture.MODE_MATERIAL;
    }

    /** Whether this tile is the stored background. */
    public static boolean isStored(@NonNull String tileId, @Nullable String storedId) {
        return tileId.equals(storedId);
    }

    /** Thumbnail width for a given height at the screen's portrait aspect, at least 1. */
    public static int thumbWidth(int thumbHeightPx, int screenW, int screenH) {
        int shortSide = Math.max(1, Math.min(screenW, screenH));
        int longSide = Math.max(1, Math.max(screenW, screenH));
        return Math.max(1, Math.round(thumbHeightPx * (shortSide / (float) longSide)));
    }
}
