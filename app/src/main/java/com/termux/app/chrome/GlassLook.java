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
    /** Material tint with both colours resolved; false degrades to the scheme behaviour. */
    public final boolean materialTint;
    @ColorInt private final int mPrimaryContainer;
    @ColorInt private final int mSurfaceTint;
    /**
     * How much of the tint the glass wears, 0..1; 1 is the shipped tint and is drawn by exactly
     * the code that drew it before the Tint control existed. Not the tint's opacity
     * ({@code GlassStack.Spec#tintAlpha}).
     */
    private float mStrength = 1f;

    /** M3 tonal elevation level 5: the surface tint's share of the wash. */
    public static final float MATERIAL_WASH = 0.14f;
    /** How far the scheme tint moves toward primary container. */
    public static final float MATERIAL_BLEND = 0.35f;

    public GlassLook(boolean obsidianTint, boolean gradientRim) {
        this(obsidianTint, gradientRim, false, 0, 0);
    }

    private GlassLook(boolean obsidianTint, boolean gradientRim, boolean materialTint,
                      @ColorInt int primaryContainer, @ColorInt int surfaceTint) {
        this(obsidianTint, gradientRim, materialTint, primaryContainer, surfaceTint, 1f);
    }

    private GlassLook(boolean obsidianTint, boolean gradientRim, boolean materialTint,
                      @ColorInt int primaryContainer, @ColorInt int surfaceTint, float strength) {
        this.obsidianTint = obsidianTint;
        this.gradientRim = gradientRim;
        this.materialTint = materialTint;
        mPrimaryContainer = primaryContainer;
        mSurfaceTint = surfaceTint;
        mStrength = strength;
    }

    /**
     * This look wearing {@code percent} of its tint (0..100): the Material tint's blend and wash
     * scale with it, and the scheme and Obsidian tints move toward the grey of the same
     * luminance. 100 returns this look untouched.
     */
    @NonNull
    public GlassLook withStrengthPercent(int percent) {
        return withStrength(Math.max(0, Math.min(100, percent)) / 100f);
    }

    /** {@link #withStrengthPercent} as a fraction, 0..1. */
    @NonNull
    public GlassLook withStrength(float strength) {
        float t = Math.max(0f, Math.min(1f, strength));
        if (t == mStrength) return this;
        GlassLook look = new GlassLook(obsidianTint, gradientRim, materialTint, mPrimaryContainer,
            mSurfaceTint, t);
        look.mMaterialRequested = mMaterialRequested;
        return look;
    }

    /** How much of the tint this look wears, 0..1. */
    public float strength() {
        return mStrength;
    }

    /**
     * The material look: {@code primaryContainer} is {@code colorPrimaryContainer} and
     * {@code surfaceTint} is {@code colorSurfaceTint} (or {@code colorPrimary}), both resolved by
     * the caller from a view's context.
     */
    @NonNull
    public static GlassLook material(boolean gradientRim, @ColorInt int primaryContainer,
                                     @ColorInt int surfaceTint) {
        return new GlassLook(false, gradientRim, true, primaryContainer, surfaceTint);
    }

    /** This look with the material colours resolved; any other tint is returned as it is. */
    @NonNull
    public GlassLook withMaterialColors(@ColorInt int primaryContainer, @ColorInt int surfaceTint) {
        return isMaterialRequested() ? new GlassLook(false, gradientRim, true, primaryContainer,
            surfaceTint, mStrength) : this;
    }

    private boolean mMaterialRequested;

    /** True when the preset named the material tint, resolved or not. */
    public boolean isMaterialRequested() {
        return materialTint || mMaterialRequested;
    }

    /** The look for the tint the preferences name: material is requested, awaiting its colours. */
    @NonNull
    public static GlassLook ofRequested(@Nullable String tint, @Nullable String rim) {
        GlassLook base = of(tint, rim);
        if (!TERMUX_APP.GLASS_TINT_MATERIAL.equals(tint)) return base;
        GlassLook look = new GlassLook(false, base.gradientRim, false, 0, 0);
        look.mMaterialRequested = true;
        return look;
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
            : ofRequested(preferences.getSurfaceGlassTint(), preferences.getSurfaceGlassRim());
    }

    /**
     * The colour the tint layer is mixed from: the scheme's own for {@code scheme}, else the
     * Obsidian ink where the scheme is dark and white where it is light. The alpha of
     * {@code schemeBase} is kept, so the caller's opacity handling is unchanged.
     */
    @ColorInt
    public int tintBase(@ColorInt int schemeBase) {
        if (materialTint) return materialBase(schemeBase);
        if (!obsidianTint) return neutralised(schemeBase);
        int ink = isDark(schemeBase) ? OBSIDIAN_DARK : OBSIDIAN_LIGHT;
        return neutralised((schemeBase & 0xFF000000) | (ink & 0x00FFFFFF));
    }

    /**
     * {@code color} moved toward its luminance-matched grey by what the strength leaves out; the
     * colour itself at full strength, and for a Material look that is still waiting for its
     * colours (it has no tint to dim yet).
     */
    @ColorInt
    private int neutralised(@ColorInt int color) {
        if (mStrength >= 1f || isMaterialRequested()) return color;
        float grey = 0.299f * ((color >> 16) & 0xFF) + 0.587f * ((color >> 8) & 0xFF)
            + 0.114f * (color & 0xFF);
        float away = 1f - mStrength;
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (color >> shift) & 0xFF;
            float moved = c + (grey - c) * away;
            rgb |= Math.round(Math.max(0f, Math.min(255f, moved))) << shift;
        }
        return (color & 0xFF000000) | rgb;
    }

    private int materialBase(@ColorInt int schemeBase) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (schemeBase >> shift) & 0xFF;
            float p = (mPrimaryContainer >> shift) & 0xFF;
            float t = (mSurfaceTint >> shift) & 0xFF;
            float blended = c + (p - c) * (MATERIAL_BLEND * mStrength);
            float washed = blended + (t - blended) * (MATERIAL_WASH * mStrength);
            rgb |= Math.round(Math.max(0f, Math.min(255f, washed))) << shift;
        }
        return (schemeBase & 0xFF000000) | rgb;
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
        if (materialTint) return (schemeTint >>> 24) == 0 ? schemeTint : materialBase(schemeTint);
        if ((schemeTint >>> 24) == 0) return schemeTint;
        if (!obsidianTint) return neutralised(schemeTint);
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
            && ((GlassLook) other).gradientRim == gradientRim
            && ((GlassLook) other).materialTint == materialTint
            && ((GlassLook) other).mPrimaryContainer == mPrimaryContainer
            && ((GlassLook) other).mSurfaceTint == mSurfaceTint
            && ((GlassLook) other).mStrength == mStrength;
    }

    @Override public int hashCode() {
        return (obsidianTint ? 2 : 0) + (gradientRim ? 1 : 0) + (materialTint ? 4 : 0)
            + 31 * mPrimaryContainer + 17 * mSurfaceTint + 13 * Float.floatToIntBits(mStrength);
    }
}
