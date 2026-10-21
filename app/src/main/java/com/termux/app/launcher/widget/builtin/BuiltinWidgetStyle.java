package com.termux.app.launcher.widget.builtin;

import android.content.Context;
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
 * The resolved look of every built-in widget: Material containers (sans captions, pill bars, a
 * filled clock face) in the launcher's scheme colours, on either the solid card or the
 * see-through one. One {@link Numerals} choice, Sans or Mono, sets the face of every numeral. The
 * card is cut at the Look's Corners (Base's, {@link com.termux.app.chrome.LookGlassSpec}), which
 * every surface shares; a detached surface's never reaches a widget. Every colour is a scheme role read through
 * {@link M3}, so a widget follows the terminal scheme, Material You and the scheme-chrome override
 * exactly as the rest of the chrome does.</p>
 */
public final class BuiltinWidgetStyle {
    /** The face of every built-in widget's numerals. */
    public enum Numerals {
        SANS("sans"), MONO("mono");
        @NonNull public final String key;
        Numerals(@NonNull String key) { this.key = key; }
        /**
         * The choice a stored value means. The retired directions migrate: Tonal's numerals become
         * Sans and Pane's Mono; anything else (unset, unknown) is Sans.
         */
        @NonNull public static Numerals fromKey(@Nullable String key) {
            return MONO.key.equals(com.termux.shared.termux.settings.preferences
                .TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals(key)) ? MONO : SANS;
        }
    }

    @NonNull public final Numerals numeralsChoice;
    /**
     * True while the widget wears the Look's glass: the slab is its card and the card's own fill
     * is only the body's lift and veil over it ({@code BuiltinWidgetView}). Inner fills stay
     * opaque either way: no glass on glass.
     */
    public final boolean glass;
    /**
     * True for the dark card: the theme's night, or the page's pale ink polarity by day
     * ({@link #onInk}). Every role below is on this card's side.
     */
    public final boolean dark;
    /** True when the theme itself is dark (night mode), which forces the dark card. */
    public final boolean night;
    public final float density;

    public final float cornerRadiusPx;
    public final float barRadiusPx;

    /** The card. */
    @ColorInt public final int card;
    /** Container inside the card, and the one above it. */
    @ColorInt public final int container;
    @ColorInt public final int containerHigh;
    @ColorInt public final int onSurface;
    @ColorInt public final int onSurfaceVariant;
    @ColorInt public final int outlineVariant;
    /** The outline role: the rim a card draws where it would vanish into its wallpaper. */
    @ColorInt public final int outline;
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
    /** The clock face fill. */
    @ColorInt public final int face;
    /** The inside of the battery ring. */
    @ColorInt public final int ringInner;

    @NonNull public final Typeface sans;
    @NonNull public final Typeface sansMedium;
    @NonNull public final Typeface sansBold;
    @NonNull public final Typeface mono;
    @NonNull public final Typeface monoMedium;
    /** Big numerals: light sans, or medium mono, by the {@link Numerals} choice. */
    @NonNull public final Typeface numerals;
    @Nullable public final Typeface nerd;

    /** This look on the Look's glass, made once ({@link #glassVariant}). */
    @Nullable private BuiltinWidgetStyle glassCache;
    /** This look on the dark card, made once ({@link #onInk}). */
    @Nullable private BuiltinWidgetStyle darkCache;

    private BuiltinWidgetStyle(@NonNull Context context, @NonNull Numerals numerals,
                               boolean glass, @Nullable Typeface monoFace, float cornerRadiusPx) {
        this.numeralsChoice = numerals;
        this.glass = glass;
        density = context.getResources().getDisplayMetrics().density;
        this.cornerRadiusPx = cornerRadiusPx;
        barRadiusPx = 99f * density;

        onSurface = M3.onSurface(context);
        onSurfaceVariant = M3.onSurfaceVariant(context);
        outlineVariant = M3.outlineVariant(context);
        outline = com.google.android.material.color.MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutline, atTone(onSurfaceVariant, 50d));
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
        night = isDark(onSurface);
        dark = night;
        // One card, glass or not: on glass the slab under it is the card's material and the
        // card's colour is the body's lift and veil over it, so every fill inside stays opaque.
        card = ColorUtils.setAlphaComponent(surfaceContainer, 255);
        ringInner = card;
        face = container;

        sans = Typeface.SANS_SERIF;
        sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface baseMono = monoFace != null ? monoFace : Typeface.MONOSPACE;
        mono = baseMono;
        monoMedium = weighted(baseMono, 500);
        this.numerals = numerals == Numerals.MONO ? monoMedium : weighted(Typeface.SANS_SERIF, 300);
        nerd = com.termux.shared.termux.font.NerdFontSpans.typeface(context);
    }

    /**
     * {@code src} on the Look's glass ({@code glass}) and/or moved to the dark card
     * ({@code toDark}): every role re-toned to the Material dark scheme's tone for it, hue and
     * chroma kept, so a day scheme's card goes dark on a dark page without leaving its scheme.
     */
    private BuiltinWidgetStyle(@NonNull BuiltinWidgetStyle src, boolean glass, boolean toDark) {
        numeralsChoice = src.numeralsChoice;
        this.glass = glass;
        night = src.night;
        dark = src.dark || toDark;
        density = src.density;
        cornerRadiusPx = src.cornerRadiusPx;
        barRadiusPx = src.barRadiusPx;
        boolean shift = toDark && !src.dark;
        card = shift ? atTone(src.card, DarkTones.CARD) : src.card;
        container = shift ? atTone(src.container, DarkTones.CONTAINER) : src.container;
        containerHigh = shift ? atTone(src.containerHigh, DarkTones.CONTAINER_HIGH)
            : src.containerHigh;
        onSurface = shift ? atTone(src.onSurface, DarkTones.ON_SURFACE) : src.onSurface;
        onSurfaceVariant = shift ? atTone(src.onSurfaceVariant, DarkTones.ON_SURFACE_VARIANT)
            : src.onSurfaceVariant;
        outlineVariant = shift ? atTone(src.outlineVariant, DarkTones.OUTLINE_VARIANT)
            : src.outlineVariant;
        outline = shift ? atTone(src.outline, DarkTones.OUTLINE) : src.outline;
        primary = shift ? atTone(src.primary, DarkTones.PRIMARY) : src.primary;
        onPrimary = shift ? atTone(src.onPrimary, DarkTones.ON_PRIMARY) : src.onPrimary;
        primaryContainer = shift ? atTone(src.primaryContainer, DarkTones.CONTAINER_ACCENT)
            : src.primaryContainer;
        onPrimaryContainer = shift ? atTone(src.onPrimaryContainer, DarkTones.ON_CONTAINER_ACCENT)
            : src.onPrimaryContainer;
        tertiaryContainer = shift ? atTone(src.tertiaryContainer, DarkTones.CONTAINER_ACCENT)
            : src.tertiaryContainer;
        onTertiaryContainer = shift
            ? atTone(src.onTertiaryContainer, DarkTones.ON_CONTAINER_ACCENT)
            : src.onTertiaryContainer;
        error = shift ? atTone(src.error, DarkTones.ERROR) : src.error;
        done = src.done;
        warm = src.warm;
        ringInner = card;
        face = container;
        sans = src.sans;
        sansMedium = src.sansMedium;
        sansBold = src.sansBold;
        mono = src.mono;
        monoMedium = src.monoMedium;
        numerals = src.numerals;
        nerd = src.nerd;
    }

    /** The Material dark scheme's tone for each role a dark card re-tones. */
    static final class DarkTones {
        private DarkTones() { }
        static final double CARD = 12d;
        static final double CONTAINER = 17d;
        static final double CONTAINER_HIGH = 22d;
        static final double ON_SURFACE = 90d;
        static final double ON_SURFACE_VARIANT = 80d;
        static final double OUTLINE_VARIANT = 30d;
        static final double OUTLINE = 60d;
        static final double PRIMARY = 80d;
        static final double ON_PRIMARY = 20d;
        static final double CONTAINER_ACCENT = 30d;
        static final double ON_CONTAINER_ACCENT = 90d;
        static final double ERROR = 80d;
    }

    /** {@code color} at HCT {@code tone}, its hue and chroma kept, opaque. */
    @ColorInt static int atTone(@ColorInt int color, double tone) {
        int opaque = ColorUtils.setAlphaComponent(color, 255);
        return com.termux.app.theme.SchemeTone.toneShift(opaque,
            tone - com.termux.app.theme.SchemeTone.tone(opaque));
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
     * Resolves the style for the {@code numerals} choice, on the see-through card or not, with the
     * terminal's own monospace face when one is known, cut at the Look's Corners as stored now.
     */
    @NonNull public static BuiltinWidgetStyle resolve(@NonNull Context context,
                                                      @NonNull Numerals numerals, boolean glass,
                                                      @Nullable Typeface monoFace) {
        return new BuiltinWidgetStyle(context, numerals, glass, monoFace,
            lookCornerRadiusPx(context));
    }

    /** The Look's Corners in px, which every built-in widget's card is cut at. */
    public static float lookCornerRadiusPx(@NonNull Context context) {
        return com.termux.app.chrome.LookGlassSpec.cornerRadiusDp(
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(
                context, false)) * context.getResources().getDisplayMetrics().density;
    }

    /**
     * This look on the Look's glass ({@link BuiltinWidgetGlass}): the same numerals, scheme,
     * card side and faces. Itself when it already is glass; made once, so a re-dress with the
     * same answer keeps the same style and builds nothing again.
     */
    @NonNull public BuiltinWidgetStyle glassVariant(@NonNull Context context) {
        if (glass) return this;
        if (glassCache == null) glassCache = new BuiltinWidgetStyle(this, true, false);
        return glassCache;
    }

    /**
     * This look on the card the page's ink asks for: the dark card for the pale ink
     * ({@code paleInk}), the theme's own for the dark ink. Night forces the dark card whatever
     * the ink, so a night style is always itself. Made once per style.
     */
    @NonNull public BuiltinWidgetStyle onInk(boolean paleInk) {
        if (dark || !paleInk) return this;
        if (darkCache == null) darkCache = new BuiltinWidgetStyle(this, glass, true);
        return darkCache;
    }

    public int dp(float value) { return Math.round(value * density); }

    /** True when the numerals are the terminal's mono, so a plan view drops tabular figures. */
    public boolean monoNumerals() { return numeralsChoice == Numerals.MONO; }
}
