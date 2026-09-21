package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/**
 * The colours the pressed-key popup is drawn in, resolved from the active Material theme.
 *
 * <p>The popup has no material of its own — no card, no border, no scrim behind the glyph. Its
 * legibility rests on one accent halo and on dimming the whole surface beneath it, so every colour
 * here is a Material role with the design's alpha applied on top, exactly the way a state layer
 * works. On a light theme the outline is already the dark one (it is {@code onSurface}) and the
 * halo is toned down instead, which is the inversion the design asks for.
 */
public final class KeyPopupPalette {

    /** Halo, targeted alternate, the outline once a direction is targeted, the latch mark. */
    @ColorInt public final int primary;
    /** The centre glyph's outline at rest. */
    @ColorInt public final int glyphStroke;
    /** An alternate that is not the target. */
    @ColorInt public final int ringIdle;
    /** The HELD / LATCH mark under a modifier's glyph. */
    @ColorInt public final int subLabel;
    /** The surface-wide veil, already carrying its own opacity. */
    @ColorInt public final int dim;
    /** Multiplier on every halo and glow alpha. */
    public final float glow;

    KeyPopupPalette(@ColorInt int primary, @ColorInt int glyphStroke, @ColorInt int ringIdle,
                    @ColorInt int subLabel, @ColorInt int dim, float glow) {
        this.primary = primary;
        this.glyphStroke = glyphStroke;
        this.ringIdle = ringIdle;
        this.subLabel = subLabel;
        this.dim = dim;
        this.glow = glow;
    }

    /** Alphas from the design, so the roles keep their intended relationship. */
    private static final float GLYPH_STROKE_ALPHA = 0.95f;
    private static final float RING_IDLE_ALPHA = 0.60f;
    private static final float SUB_LABEL_ALPHA = 0.45f;
    private static final float DIM_ALPHA = 0.72f;
    /** A light theme carries the same accent at a lower glow, so the halo does not wash out. */
    private static final float LIGHT_GLOW = 0.7f;

    @NonNull
    public static KeyPopupPalette resolve(@NonNull Context context) {
        int surface = role(context, com.google.android.material.R.attr.colorSurface, 0xFF101010);
        int onSurface = role(context, com.google.android.material.R.attr.colorOnSurface, 0xFFF2EFE8);
        int onSurfaceVariant = role(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ColorUtils.blendARGB(onSurface, surface, 0.28f));
        int primary = role(context, com.google.android.material.R.attr.colorPrimary, 0xFFE9B308);
        int lowest = role(context,
            com.google.android.material.R.attr.colorSurfaceContainerLowest, surface);
        boolean night = (context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new KeyPopupPalette(
            opaque(primary),
            withAlpha(onSurface, GLYPH_STROKE_ALPHA),
            withAlpha(onSurfaceVariant, RING_IDLE_ALPHA),
            withAlpha(onSurfaceVariant, SUB_LABEL_ALPHA),
            withAlpha(lowest, DIM_ALPHA),
            night ? 1f : LIGHT_GLOW);
    }

    /** A signature that moves whenever any of the roles above does. */
    public static int signature(@NonNull Context context) {
        KeyPopupPalette p = resolve(context);
        int hash = p.primary;
        hash = 31 * hash + p.glyphStroke;
        hash = 31 * hash + p.ringIdle;
        hash = 31 * hash + p.subLabel;
        hash = 31 * hash + p.dim;
        return 31 * hash + Float.floatToIntBits(p.glow);
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
