package com.termux.app.chrome;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * What a preset says about the glass's colour, beyond blur, opacity and grain: which tint it is
 * mixed from and how its rim is drawn. {@code scheme} + {@code hairline} is the shipped look and
 * is drawn by exactly the code that drew it before this existed.
 *
 * <p>The Obsidian values (Apache-2.0, Obsidian-Music 2.5.1; see
 * project-docs/reference/launcher/mist-preset-obsidian-values.md): dark
 * glass is #161822 at the user's opacity with a
 * white 0.05 wash over it; light glass is white with the same wash; the rim is a 1dp diagonal
 * gradient, white 0.25 at the top-left to white 0.03. Pure ints, no {@code Context}.</p>
 */
public final class GlassLook {

    public static final GlassLook SCHEME = new GlassLook(false, false);

    @ColorInt public static final int OBSIDIAN_DARK = GlassTokens.OBSIDIAN_DARK;
    @ColorInt public static final int OBSIDIAN_LIGHT = GlassTokens.OBSIDIAN_LIGHT;
    /** White 0.05, out of 255. */
    public static final int WASH_ALPHA = GlassTokens.WASH_ALPHA;
    @ColorInt public static final int RIM_START = GlassTokens.RIM_START;
    @ColorInt public static final int RIM_END = GlassTokens.RIM_END;

    public final boolean obsidianTint;
    public final boolean gradientRim;

    public GlassLook(boolean obsidianTint, boolean gradientRim) {
        this.obsidianTint = obsidianTint;
        this.gradientRim = gradientRim;
    }

    /** The look two preset strings name; anything unknown is the shipped half of that pair. */
    @NonNull
    public static GlassLook of(@Nullable String tint, @Nullable String rim) {
        boolean obsidian = TERMUX_APP.GLASS_TINT_OBSIDIAN.equals(tint);
        boolean gradient = TERMUX_APP.GLASS_RIM_GRADIENT.equals(rim);
        return !obsidian && !gradient ? SCHEME : new GlassLook(obsidian, gradient);
    }

    @NonNull
    public static GlassLook of(@Nullable TermuxAppSharedPreferences preferences) {
        return preferences == null ? SCHEME
            : of(preferences.getSurfaceGlassTint(), preferences.getSurfaceGlassRim());
    }

    /**
     * The colour the tint layer is mixed from: the scheme's own for {@code scheme}, else the
     * Obsidian ink where the scheme is dark and white where it is light. The alpha of
     * {@code schemeBase} is kept, so the caller's opacity handling is unchanged.
     */
    @ColorInt
    public int tintBase(@ColorInt int schemeBase) {
        if (!obsidianTint) return schemeBase;
        int ink = isDark(schemeBase) ? OBSIDIAN_DARK : OBSIDIAN_LIGHT;
        return (schemeBase & 0xFF000000) | (ink & 0x00FFFFFF);
    }

    /** White 0.05: the wash that replaces the light model's sheen in Obsidian glass. */
    @ColorInt
    public static int wash() {
        return (WASH_ALPHA << 24) | 0x00FFFFFF;
    }

    /**
     * One colour standing for the tint and its wash, for the surfaces that draw a single tint
     * paint (the panes): {@code schemeTint} recoloured, then the wash composited over it. A fully
     * transparent tint stays transparent, and {@code scheme} returns the colour untouched.
     */
    @ColorInt
    public int flatTint(@ColorInt int schemeTint) {
        if (!obsidianTint || (schemeTint >>> 24) == 0) return schemeTint;
        int base = tintBase(schemeTint);
        float a = (base >>> 24) / 255f;
        float w = WASH_ALPHA / 255f;
        float outA = w + a * (1f - w);
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (base >> shift) & 0xFF;
            float mixed = (255f * w + c * a * (1f - w)) / outA;
            rgb |= Math.round(Math.max(0f, Math.min(255f, mixed))) << shift;
        }
        return (Math.round(outA * 255f) << 24) | rgb;
    }

    private static boolean isDark(@ColorInt int color) {
        double brightness = 0.299 * ((color >> 16) & 0xFF) + 0.587 * ((color >> 8) & 0xFF)
            + 0.114 * (color & 0xFF);
        return brightness < 128d;
    }

    @Override public boolean equals(@Nullable Object other) {
        return other instanceof GlassLook && ((GlassLook) other).obsidianTint == obsidianTint
            && ((GlassLook) other).gradientRim == gradientRim;
    }

    @Override public int hashCode() {
        return (obsidianTint ? 2 : 0) + (gradientRim ? 1 : 0);
    }
}
