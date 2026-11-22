package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.app.theme.LauncherThemeTokens;

import java.util.Map;

/**
 * The Material roles the command palette draws with. While the launcher's scheme owns the chrome
 * ({@link LauncherSchemeTheme#isSchemeChromeActive}) every role is the scheme's token, as the rest
 * of the chrome reads it; otherwise the theme's own attribute.
 */
final class CommandPaletteRoles {

    @ColorInt final int primary;
    @ColorInt final int onSurface;
    @ColorInt final int onSurfaceVariant;
    @ColorInt final int surfaceContainerHigh;
    @ColorInt final int secondaryContainer;
    @ColorInt final int onSecondaryContainer;

    private CommandPaletteRoles(int primary, int onSurface, int onSurfaceVariant,
                                int surfaceContainerHigh, int secondaryContainer,
                                int onSecondaryContainer) {
        this.primary = primary;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
    }

    @NonNull
    static CommandPaletteRoles resolve(@NonNull Context context, @ColorInt int glassBase) {
        Map<String, Integer> tokens = LauncherSchemeTheme.isSchemeChromeActive(context)
            ? LauncherSchemeTheme.tokens() : null;
        int primary = pick(tokens, LauncherThemeTokens.PRIMARY, attr(context,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary)));
        int onSurface = pick(tokens, LauncherThemeTokens.ON_SURFACE, attr(context,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface)));
        int onSurfaceVariant = pick(tokens, LauncherThemeTokens.ON_SURFACE_VARIANT, attr(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ColorUtils.blendARGB(onSurface, glassBase, 0.28f)));
        int surfaceContainerHigh = pick(tokens, LauncherThemeTokens.SURFACE_CONTAINER_HIGH,
            attr(context, com.google.android.material.R.attr.colorSurfaceContainerHigh,
                ColorUtils.blendARGB(glassBase, onSurface, 0.10f)));
        int secondaryContainer = pick(tokens, LauncherThemeTokens.SECONDARY_CONTAINER,
            attr(context, com.google.android.material.R.attr.colorSecondaryContainer,
                ColorUtils.blendARGB(surfaceContainerHigh, primary, 0.24f)));
        int onSecondaryContainer = pick(tokens, LauncherThemeTokens.ON_SECONDARY_CONTAINER,
            attr(context, com.google.android.material.R.attr.colorOnSecondaryContainer,
                onSurface));
        return new CommandPaletteRoles(primary, onSurface, onSurfaceVariant, surfaceContainerHigh,
            secondaryContainer, onSecondaryContainer);
    }

    /** The scheme's {@code key} when it has one, else {@code themeValue}. Pure. */
    @ColorInt
    static int pick(@Nullable Map<String, Integer> tokens, @NonNull String key,
                    @ColorInt int themeValue) {
        Integer value = tokens == null ? null : tokens.get(key);
        return value != null ? value : themeValue;
    }

    @ColorInt
    private static int attr(@NonNull Context context, @AttrRes int attr, @ColorInt int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }
}
