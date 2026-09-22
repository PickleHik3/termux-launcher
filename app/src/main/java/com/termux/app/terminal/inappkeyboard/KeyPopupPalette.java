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

    /** The glow that wraps the glyph: the theme's accent. */
    @ColorInt public final int primary;
    /** The glyph itself: the theme's text-on-surface colour, the same family as the caps' labels. */
    @ColorInt public final int ink;

    KeyPopupPalette(@ColorInt int primary, @ColorInt int ink) {
        this.primary = primary;
        this.ink = ink;
    }


    @NonNull
    public static KeyPopupPalette resolve(@NonNull Context context) {
        int primary = role(context, com.google.android.material.R.attr.colorPrimary, 0xFFE9B308);
        int onSurface = role(context, com.google.android.material.R.attr.colorOnSurface, 0xFFF2EFE8);
        return new KeyPopupPalette(opaque(primary), opaque(onSurface));
    }

    /** A signature that moves whenever either role above does. */
    public static int signature(@NonNull Context context) {
        KeyPopupPalette p = resolve(context);
        return 31 * p.primary + p.ink;
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
