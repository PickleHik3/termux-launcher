package com.termux.app.chrome;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * What a preset says about the glass's colour, beyond blur, opacity and grain: which ink it is
 * mixed from (the scheme's own, or Obsidian's), how much accent colour is mixed into that ink (the
 * Tint control's amount, on one axis every Look shares) and how its rim is drawn. {@code scheme} +
 * {@code hairline} at amount 0 is the shipped look and is drawn by exactly the code that drew it
 * before this existed. Amount 35 on the scheme ink is the Material blend the Tint Look wears;
 * amount 100 moves the ink all the way to the accent's container. Behind see-through glass the
 * amount also tints the shared frame ({@link #frameTint}).
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

    /** The accents the amount can mix in: the primary, secondary or tertiary pair. */
    public enum Hue {
        PRIMARY, SECONDARY, TERTIARY;

        /** The stored name's hue; anything unknown is the primary. */
        @NonNull
        public static Hue of(@Nullable String stored) {
            if (TERMUX_APP.GLASS_TINT_HUE_SECONDARY.equals(stored)) return SECONDARY;
            if (TERMUX_APP.GLASS_TINT_HUE_TERTIARY.equals(stored)) return TERTIARY;
            return PRIMARY;
        }
    }

    public final boolean obsidianTint;
    public final boolean gradientRim;
    /** Which accent pair the amount mixes in. */
    @NonNull public final Hue hue;
    /** The accent's colours are resolved; until they are, any amount draws the ink untouched. */
    public final boolean accentResolved;
    @ColorInt private final int mContainer;
    @ColorInt private final int mAccent;
    /**
     * How much accent the glass mixes in, 0..1: 0 is the ink untouched, 1 the ink moved all the
     * way to the accent's container ({@link #FULL_BLEND}) under the Tint Look's wash. Not the
     * tint's opacity ({@code GlassStack.Spec#tintAlpha}).
     */
    private final float mAmount;

    /** M3 tonal elevation level 5: the surface tint's share of the wash, the Tint Look's. */
    public static final float MATERIAL_WASH = 0.14f;
    /** How far the Tint Look moves the ink toward the accent's container. */
    public static final float MATERIAL_BLEND = 0.35f;
    /**
     * How far the full amount moves the ink toward the container: all the way, to the accent's
     * own tonal surface, so Tint 100 is a strong accent wherever the face shows.
     */
    public static final float FULL_BLEND = 1f;
    /**
     * The amount, percent, that draws the Material blend the Tint Look wears (0.35 toward the
     * container, a 0.14 wash): {@link #MATERIAL_BLEND} over {@link #FULL_BLEND}. Below it the
     * blend and the wash grow together, as the old Material strength did, so an old amount s is
     * this new one at s times 35 over 100 ({@link #amountForLegacyPercent}). Above it only the
     * blend grows: the wash pulls toward the accent role, which on dark glass is pale, and more
     * of it would lift the glass toward milk.
     */
    public static final int TINT_LOOK_PERCENT = 35;

    /**
     * The amount, percent, that draws what {@code legacyPercent} drew before the Tint scale
     * reached the container (glass controls format 2): the same blend and the same wash.
     */
    public static int amountForLegacyPercent(int legacyPercent) {
        int clamped = Math.max(0, Math.min(100, legacyPercent));
        return Math.round(clamped * TINT_LOOK_PERCENT / 100f);
    }

    public GlassLook(boolean obsidianTint, boolean gradientRim) {
        this(obsidianTint, gradientRim, Hue.PRIMARY, false, 0, 0, 0f);
    }

    private GlassLook(boolean obsidianTint, boolean gradientRim, @NonNull Hue hue,
                      boolean accentResolved, @ColorInt int container, @ColorInt int accent,
                      float amount) {
        this.obsidianTint = obsidianTint;
        this.gradientRim = gradientRim;
        this.hue = hue;
        this.accentResolved = accentResolved;
        mContainer = container;
        mAccent = accent;
        mAmount = amount;
    }

    /** This look mixing in {@code percent} of the accent (0..100, clamped): the Tint control. */
    @NonNull
    public GlassLook withAmountPercent(int percent) {
        return withAmount(Math.max(0, Math.min(100, percent)) / 100f);
    }

    /** {@link #withAmountPercent} as a fraction, 0..1. */
    @NonNull
    public GlassLook withAmount(float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        if (t == mAmount) return this;
        return new GlassLook(obsidianTint, gradientRim, hue, accentResolved, mContainer, mAccent, t);
    }

    /** How much accent this look mixes in, 0..1. */
    public float amount() {
        return mAmount;
    }

    /** This look mixing in {@code hue}'s accent; its colours have to be resolved again. */
    @NonNull
    public GlassLook withHue(@NonNull Hue hue) {
        if (hue == this.hue) return this;
        return new GlassLook(obsidianTint, gradientRim, hue, false, 0, 0, mAmount);
    }

    /**
     * This look with its accent's colours resolved: {@code container} is the hue's
     * {@code color*Container} and {@code accent} its role colour (M3's surface tint is the primary
     * role), both resolved by the caller from a view's context.
     */
    @NonNull
    public GlassLook withAccentColors(@ColorInt int container, @ColorInt int accent) {
        return new GlassLook(obsidianTint, gradientRim, hue, true, container, accent, mAmount);
    }

    /** True while this look mixes in some accent whose colours are not resolved yet. */
    public boolean needsAccentColors() {
        return mAmount > 0f && !accentResolved;
    }

    /**
     * The look two preset strings name; anything unknown is the shipped half of that pair. The
     * legacy {@code material} tint reads as {@code scheme}: its blend is the amount now.
     */
    @NonNull
    public static GlassLook of(@Nullable String tint, @Nullable String rim) {
        return of(tint, rim, null);
    }

    /** {@link #of(String, String)} mixing in the stored {@code hue}'s accent. */
    @NonNull
    public static GlassLook of(@Nullable String tint, @Nullable String rim, @Nullable String hue) {
        boolean obsidian = TERMUX_APP.GLASS_TINT_OBSIDIAN.equals(tint);
        boolean gradient = TERMUX_APP.GLASS_RIM_GRADIENT.equals(rim);
        Hue named = Hue.of(hue);
        if (!obsidian && !gradient && named == Hue.PRIMARY) return SCHEME;
        return new GlassLook(obsidian, gradient, named, false, 0, 0, 0f);
    }

    @NonNull
    public static GlassLook of(@Nullable TermuxAppSharedPreferences preferences) {
        return preferences == null ? SCHEME
            : of(preferences.getSurfaceGlassTint(), preferences.getSurfaceGlassRim(),
                preferences.getSurfaceGlassTintHue());
    }

    /**
     * The colour the tint layer is mixed from: the scheme's own for {@code scheme}, else the
     * Obsidian ink where the scheme is dark and white where it is light; then the accent mixed in
     * by the amount. The alpha of {@code schemeBase} is kept, so the caller's opacity handling is
     * unchanged. At amount 0 this is exactly the ink.
     */
    @ColorInt
    public int tintBase(@ColorInt int schemeBase) {
        int ink = schemeBase;
        if (obsidianTint) {
            int obsidian = isDark(schemeBase) ? OBSIDIAN_DARK : OBSIDIAN_LIGHT;
            ink = (schemeBase & 0xFF000000) | (obsidian & 0x00FFFFFF);
        }
        return accented(ink);
    }

    /**
     * {@code ink} with the accent mixed in by the amount; the ink itself at 0 or unresolved. The
     * ink moves {@link #FULL_BLEND} times the amount toward the container, then takes the wash,
     * which grows with the amount up to the Tint Look's and holds there.
     */
    @ColorInt
    private int accented(@ColorInt int ink) {
        if (mAmount <= 0f || !accentResolved) return ink;
        float blend = FULL_BLEND * mAmount;
        float wash = MATERIAL_WASH * Math.min(1f, mAmount / (TINT_LOOK_PERCENT / 100f));
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (ink >> shift) & 0xFF;
            float p = (mContainer >> shift) & 0xFF;
            float t = (mAccent >> shift) & 0xFF;
            float blended = c + (p - c) * blend;
            float washed = blended + (t - blended) * wash;
            rgb |= Math.round(Math.max(0f, Math.min(255f, washed))) << shift;
        }
        return (ink & 0xFF000000) | rgb;
    }

    /**
     * The tint this look's amount bakes into the shared frame behind glass at
     * {@code opacityPercent} ({@link GlassFrameTint}): {@link GlassFrameTint#NONE} at amount 0,
     * while the accent's colours are unresolved, and where the face carries the accent alone.
     */
    public int frameTint(int opacityPercent) {
        if (mAmount <= 0f || !accentResolved) return GlassFrameTint.NONE;
        return GlassFrameTint.of(Math.round(mAmount * 100f), opacityPercent, mContainer, mAccent);
    }

    /** White 0.05: the wash that replaces the light model's sheen in Obsidian glass. */
    @ColorInt
    public static int wash() {
        return (WASH_ALPHA << 24) | 0x00FFFFFF;
    }

    /**
     * One colour standing for the tint and its wash, for the surfaces that draw a single tint
     * paint (the panes): {@code schemeTint} recoloured and accented, then, for Obsidian, the wash
     * composited over it. A fully transparent tint stays transparent, and {@code scheme} at amount
     * 0 returns the colour untouched.
     */
    @ColorInt
    public int flatTint(@ColorInt int schemeTint) {
        if ((schemeTint >>> 24) == 0) return schemeTint;
        int base = tintBase(schemeTint);
        if (!obsidianTint) return base;
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

    /**
     * Whether glass mixed from {@code schemeBase} is light: the scheme base is pale, so Obsidian
     * glass wears its white ink. What a Look whose values differ by glass is chosen by.
     */
    public static boolean isLightGlass(@ColorInt int schemeBase) {
        return !isDark(schemeBase);
    }

    static boolean isDark(@ColorInt int color) {
        double brightness = 0.299 * ((color >> 16) & 0xFF) + 0.587 * ((color >> 8) & 0xFF)
            + 0.114 * (color & 0xFF);
        return brightness < 128d;
    }

    @Override public boolean equals(@Nullable Object other) {
        if (!(other instanceof GlassLook)) return false;
        GlassLook that = (GlassLook) other;
        return that.obsidianTint == obsidianTint && that.gradientRim == gradientRim
            && that.hue == hue && that.accentResolved == accentResolved
            && that.mContainer == mContainer && that.mAccent == mAccent
            && that.mAmount == mAmount;
    }

    @Override public int hashCode() {
        return (obsidianTint ? 2 : 0) + (gradientRim ? 1 : 0) + (accentResolved ? 4 : 0)
            + 8 * hue.ordinal() + 31 * mContainer + 17 * mAccent
            + 13 * Float.floatToIntBits(mAmount);
    }
}
