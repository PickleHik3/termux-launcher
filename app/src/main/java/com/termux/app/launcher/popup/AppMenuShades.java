package com.termux.app.launcher.popup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.material.M3;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.app.theme.LauncherThemeTokens;

import java.util.Map;

/**
 * The App menu's three group shades, Launcher3's popup_shade_first, second and third.
 *
 * <p>They are the palette's three highest surface containers — {@code colorSurfaceContainer},
 * {@code …High} and {@code …Highest} — which follow Material You when the system palette drives the
 * theme, and the launcher's own scheme surfaces when the scheme drives the chrome. Whatever the
 * theme, the shades run a step lighter top to bottom: in a dark theme the highest container is the
 * lightest, in a light theme the lowest is, so they are ordered by lightness rather than by name.
 */
public final class AppMenuShades {

    private AppMenuShades() {
    }

    /** The three shades for {@code context}'s theme, darkest first. */
    @NonNull
    public static int[] resolve(@NonNull Context context) {
        int[] shades = null;
        if (LauncherSchemeTheme.isSchemeChromeActive(context)) {
            shades = fromScheme(LauncherSchemeTheme.tokens());
        }
        if (shades == null) {
            shades = new int[]{
                M3.surfaceContainer(context),
                M3.surfaceContainerHigh(context),
                M3.color(context, com.google.android.material.R.attr.colorSurfaceContainerHighest,
                    com.google.android.material.R.color.m3_sys_color_dark_surface_container_highest)
            };
        }
        return orderDarkToLight(shades);
    }

    @Nullable
    private static int[] fromScheme(@Nullable Map<String, Integer> tokens) {
        if (tokens == null) return null;
        Integer container = tokens.get(LauncherThemeTokens.SURFACE_CONTAINER);
        Integer high = tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_HIGH);
        Integer highest = tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_HIGHEST);
        if (container == null || high == null || highest == null) return null;
        return new int[]{container, high, highest};
    }

    /** {@code shades} sorted darkest first, opaque; a new array, the input untouched. */
    @NonNull
    static int[] orderDarkToLight(@NonNull int[] shades) {
        int[] out = new int[shades.length];
        for (int i = 0; i < shades.length; i++) out[i] = shades[i] | 0xFF000000;
        // Three entries: an insertion sort is the whole algorithm.
        for (int i = 1; i < out.length; i++) {
            int value = out[i];
            int j = i - 1;
            while (j >= 0 && lightness(out[j]) > lightness(value)) {
                out[j + 1] = out[j];
                j--;
            }
            out[j + 1] = value;
        }
        return out;
    }

    /** Relative luminance of an sRGB colour, 0..1. */
    static double lightness(int color) {
        return 0.2126 * linear((color >> 16) & 0xFF)
            + 0.7152 * linear((color >> 8) & 0xFF)
            + 0.0722 * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
