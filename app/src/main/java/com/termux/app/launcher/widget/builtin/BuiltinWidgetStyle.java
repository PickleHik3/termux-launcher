package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;


import com.termux.R;
import com.termux.app.material.M3;

/**
 * The resolved look of every built-in widget: the design's two directions, in the launcher's
 * scheme colours, on either a solid page or a glass one.
 *
 * <p><b>Tonal</b> is Material containers — 24dp corners, sans captions, light mono numerals,
 * pill bars, a filled clock face. <b>Pane</b> dresses a widget like a terminal pane — 12dp
 * corners, a hairline rim, mono path captions led by a Nerd glyph, medium numerals, square bars,
 * an outlined face. Every colour is a scheme role read through {@link M3}, so a widget follows the
 * terminal scheme, Material You and the scheme-chrome override exactly as the rest of the chrome
 * does.</p>
 */
public final class BuiltinWidgetStyle {
    public enum Direction {
        TONAL("tonal"), PANE("pane");
        @NonNull public final String key;
        Direction(@NonNull String key) { this.key = key; }
        @NonNull public static Direction fromKey(@Nullable String key) {
            return PANE.key.equals(key) ? PANE : TONAL;
        }
    }

    @NonNull public final Direction direction;
    /** True when the widgets page is glass: the card is translucent with a light rim. */
    public final boolean glass;
    public final float density;

    public final float cornerRadiusPx;
    public final float barRadiusPx;
    public final float rimWidthPx;
    @ColorInt public final int rim;

    /** The card. */
    @ColorInt public final int card;
    /** Container inside the card, and the one above it. */
    @ColorInt public final int container;
    @ColorInt public final int containerHigh;
    @ColorInt public final int onSurface;
    @ColorInt public final int onSurfaceVariant;
    @ColorInt public final int outlineVariant;
    @ColorInt public final int primary;
    @ColorInt public final int onPrimary;
    @ColorInt public final int primaryContainer;
    @ColorInt public final int onPrimaryContainer;
    @ColorInt public final int tertiaryContainer;
    @ColorInt public final int onTertiaryContainer;
    @ColorInt public final int error;
    /** The launcher's "done" green; Material has no success role. */
    @ColorInt public final int done;
    /** The warm accent: the sun, the year bar, the seconds hand. */
    @ColorInt public final int warm;
    /** The clock face fill (transparent for Pane, which outlines it instead). */
    @ColorInt public final int face;
    /** The inside of the battery ring. */
    @ColorInt public final int ringInner;

    @NonNull public final Typeface sans;
    @NonNull public final Typeface sansMedium;
    @NonNull public final Typeface sansBold;
    @NonNull public final Typeface mono;
    @NonNull public final Typeface monoMedium;
    /** Big numerals: light for Tonal, medium for Pane. */
    @NonNull public final Typeface numerals;
    @Nullable public final Typeface nerd;

    private BuiltinWidgetStyle(@NonNull Context context, @NonNull Direction direction,
                               boolean glass, @Nullable Typeface monoFace) {
        this.direction = direction;
        this.glass = glass;
        density = context.getResources().getDisplayMetrics().density;
        boolean pane = direction == Direction.PANE;
        cornerRadiusPx = (pane ? 12f : 24f) * density;
        barRadiusPx = (pane ? 1f : 99f) * density;

        onSurface = M3.onSurface(context);
        onSurfaceVariant = M3.onSurfaceVariant(context);
        outlineVariant = M3.outlineVariant(context);
        primary = M3.primary(context);
        onPrimary = M3.color(context, com.google.android.material.R.attr.colorOnPrimary, R.color.termux_on_primary);
        primaryContainer = M3.color(context, com.google.android.material.R.attr.colorPrimaryContainer,
            R.color.termux_primary_container);
        onPrimaryContainer = M3.color(context, com.google.android.material.R.attr.colorOnPrimaryContainer,
            R.color.termux_on_primary_container);
        tertiaryContainer = M3.color(context, com.google.android.material.R.attr.colorTertiaryContainer,
            R.color.termux_tertiary_container);
        onTertiaryContainer = M3.color(context, com.google.android.material.R.attr.colorOnTertiaryContainer,
            R.color.termux_on_tertiary_container);
        error = M3.error(context);
        done = ContextCompat.getColor(context, R.color.termux_chip_done);
        warm = ContextCompat.getColor(context, R.color.termux_place_display);
        int surfaceContainer = M3.surfaceContainer(context);
        container = M3.surfaceContainerHigh(context);
        containerHigh = M3.color(context, com.google.android.material.R.attr.colorSurfaceContainerHighest,
            R.color.termux_surface_panel_highest);
        boolean dark = isDark(onSurface);
        if (glass) {
            card = ColorUtils.setAlphaComponent(surfaceContainer, Math.round(255 * (dark ? 0.55f : 0.62f)));
            rim = ColorUtils.setAlphaComponent(Color.WHITE, Math.round(255 * (dark ? 0.12f : 0.55f)));
            ringInner = ColorUtils.compositeColors(card, dark ? 0xFF18202A : 0xFFE9EEF5);
        } else {
            card = surfaceContainer;
            rim = outlineVariant;
            ringInner = surfaceContainer;
        }
        rimWidthPx = (pane || glass) ? Math.max(1f, density) : 0f;
        face = pane ? Color.TRANSPARENT : container;

        sans = Typeface.SANS_SERIF;
        sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface baseMono = monoFace != null ? monoFace : Typeface.MONOSPACE;
        mono = baseMono;
        monoMedium = weighted(baseMono, 500);
        numerals = pane ? monoMedium : weighted(baseMono, 300);
        nerd = com.termux.shared.termux.font.NerdFontSpans.typeface(context);
    }

    /** {@code base} at {@code weight} where the platform can vary it; {@code base} itself before 28. */
    @NonNull private static Typeface weighted(@NonNull Typeface base, int weight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { return Typeface.create(base, weight, false); } catch (RuntimeException ignored) { }
        }
        return weight >= 600 ? Typeface.create(base, Typeface.BOLD) : base;
    }

    private static boolean isDark(@ColorInt int onSurface) {
        return ColorUtils.calculateLuminance(onSurface) > 0.5;
    }

    /**
     * Resolves the style for {@code direction} on a page that is {@code glass} or not, with the
     * terminal's own monospace face when one is known.
     */
    @NonNull public static BuiltinWidgetStyle resolve(@NonNull Context context,
                                                      @NonNull Direction direction, boolean glass,
                                                      @Nullable Typeface monoFace) {
        return new BuiltinWidgetStyle(context, direction, glass, monoFace);
    }

    public boolean isPane() { return direction == Direction.PANE; }

    public int dp(float value) { return Math.round(value * density); }

    /** Pane's hairline rim is a stroke; Tonal on glass has the light rim; Tonal solid has none. */
    public boolean hasRim() { return rimWidthPx > 0f; }
}
