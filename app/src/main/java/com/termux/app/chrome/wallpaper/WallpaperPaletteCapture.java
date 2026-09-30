package com.termux.app.chrome.wallpaper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The four colours a generated background is drawn with. "Material" reads the roles the launcher's
 * colour scheme resolves on the given (themed) context, the same way the terminal colour export
 * does; "own" is the background's shipped palette.
 */
public final class WallpaperPaletteCapture {

    public static final String MODE_MATERIAL = "material";
    public static final String MODE_OWN = "own";

    private WallpaperPaletteCapture() {}

    /**
     * Four opaque ARGB colours from colorPrimaryContainer, colorTertiaryContainer, colorSurfaceDim
     * (colorSurface when the theme has no dim role) and colorSecondary. Pass a context that carries
     * the launcher's scheme theme (the activity), not the application context.
     */
    @NonNull
    public static int[] material(@NonNull Context themedContext) {
        int primary = ContextCompat.getColor(themedContext, R.color.termux_primary);
        int surface = MaterialColors.getColor(themedContext,
            com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(themedContext, R.color.termux_surface_base));
        return new int[] {
            opaque(MaterialColors.getColor(themedContext,
                com.google.android.material.R.attr.colorPrimaryContainer, primary)),
            opaque(MaterialColors.getColor(themedContext,
                com.google.android.material.R.attr.colorTertiaryContainer, primary)),
            opaque(MaterialColors.getColor(themedContext,
                com.google.android.material.R.attr.colorSurfaceDim, surface)),
            opaque(MaterialColors.getColor(themedContext,
                com.google.android.material.R.attr.colorSecondary,
                ContextCompat.getColor(themedContext, R.color.termux_secondary))),
        };
    }

    /** The palette for {@code mode}: {@code "own"} gives the background's own, anything else Material. */
    @NonNull
    public static int[] resolve(@Nullable Context context, @NonNull AnimatedWallpaper w, @Nullable String mode) {
        if (MODE_OWN.equals(mode)) return w.ownPalette().clone();
        return material(context);
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }
}
