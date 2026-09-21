package com.termux.app.terminal.inappkeyboard;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/**
 * The two colours the pressed-key glyph is drawn in, resolved from the active Material theme.
 *
 * <p>The popup has no material of its own — no card, no border, no veil over the surface. It is one
 * filled glyph in the theme's accent, with a soft shadow in the theme's lowest surface behind it so
 * it still reads over terminal text. Both are Material roles, so they invert with the theme on
 * their own: the shadow is near-black on a dark theme and near-white on a light one.
 */
public final class KeyPopupPalette {

    /** The glyph itself. */
    @ColorInt public final int primary;
    /** The soft shadow behind it, already carrying its own opacity. */
    @ColorInt public final int shadow;

    KeyPopupPalette(@ColorInt int primary, @ColorInt int shadow) {
        this.primary = primary;
        this.shadow = shadow;
    }

    /** How strongly the shadow carries, so the glyph separates without glowing. */
    private static final float SHADOW_ALPHA = 0.60f;

    @NonNull
    public static KeyPopupPalette resolve(@NonNull Context context) {
        int surface = role(context, com.google.android.material.R.attr.colorSurface, 0xFF101010);
        int primary = role(context, com.google.android.material.R.attr.colorPrimary, 0xFFE9B308);
        int lowest = role(context,
            com.google.android.material.R.attr.colorSurfaceContainerLowest, surface);
        return new KeyPopupPalette(opaque(primary), withAlpha(lowest, SHADOW_ALPHA));
    }

    /** A signature that moves whenever either role above does. */
    public static int signature(@NonNull Context context) {
        KeyPopupPalette p = resolve(context);
        return 31 * p.primary + p.shadow;
    }

    @ColorInt
    static int withAlpha(@ColorInt int color, float alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
    }

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return color | 0xFF000000;
    }

    @ColorInt
    private static int role(@NonNull Context context, int attr, @ColorInt int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }
}
