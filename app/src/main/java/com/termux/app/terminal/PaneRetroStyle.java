package com.termux.app.terminal;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;

/** The retro monitor a pane card is drawn through. Pure data; the shader lives in {@link PaneRetroEffect}. */
public enum PaneRetroStyle {
    NONE("none", 0f, 0f, 0f, 0f),
    CRT("crt", 0f, 0f, 0f, 0f),
    CRT_GREEN("crt_green", 0.35f, 1.0f, 0.55f, 0.85f),
    CRT_AMBER("crt_amber", 1.0f, 0.68f, 0.25f, 0.85f),
    /**
     * The CRT in the phone's own colours (editor-redesign §2, Terminal): its phosphor is the
     * theme's {@code colorPrimary}, the Material You primary where the phone has one, at a
     * phosphor's brightness ({@link #materialPhosphor}). The tint is set by the activity from its
     * theme ({@link #setMaterialPrimary}); until then it is Material's baseline primary.
     */
    CRT_MATERIAL("crt_material", 0f, 0f, 0f, 0f),
    TFT("tft", 0f, 0f, 0f, 0f);

    /** The phosphor's HCT tone: as bright as Green's and Amber's glow. */
    static final double MATERIAL_PHOSPHOR_TONE = 80d;
    /** The least chroma the phosphor keeps, so a near-grey theme still reads as a colour. */
    static final double MATERIAL_PHOSPHOR_MIN_CHROMA = 48d;
    /** How strongly the phosphor tints the screen: Green's and Amber's strength. */
    static final float MATERIAL_PHOSPHOR_STRENGTH = 0.85f;
    /** Material 3's baseline primary, the phosphor before any theme was read. */
    @ColorInt static final int BASELINE_PRIMARY = 0xFF6750A4;

    @NonNull private static volatile float[] sMaterialTint = materialPhosphor(BASELINE_PRIMARY);

    private final String mId;
    private final float[] mTint;

    PaneRetroStyle(String id, float r, float g, float b, float strength) {
        mId = id;
        mTint = new float[]{r, g, b, strength};
    }

    public String id() {
        return mId;
    }

    /** Unknown or null ids fall back to {@link #NONE}. */
    public static PaneRetroStyle fromId(@Nullable String id) {
        for (PaneRetroStyle style : values())
            if (style.mId.equals(id)) return style;
        return NONE;
    }

    /** A copy of {r, g, b, strength}. */
    public float[] tint() {
        return this == CRT_MATERIAL ? sMaterialTint.clone() : mTint.clone();
    }

    /** True for the scanline family (CRT and its phosphor tints). */
    public boolean usesCrt() {
        return this == CRT || this == CRT_GREEN || this == CRT_AMBER || this == CRT_MATERIAL;
    }

    /**
     * The theme's primary the Material CRT glows in; the activity sets it from
     * {@code ?attr/colorPrimary} before it applies the effect. A screen built after this call
     * takes the new phosphor.
     */
    public static void setMaterialPrimary(@ColorInt int primary) {
        sMaterialTint = materialPhosphor(primary);
    }

    /**
     * {r, g, b, strength} of the Material CRT's phosphor for {@code primary}: the primary's own hue
     * at HCT tone {@link #MATERIAL_PHOSPHOR_TONE}, with at least
     * {@link #MATERIAL_PHOSPHOR_MIN_CHROMA} of chroma (its own where it has more), at Green's
     * and Amber's strength. A deep primary (tone 40 on a light theme) and its pale twin (tone 80
     * on a dark one) glow alike, so the effect does not change with the theme's polarity.
     */
    @NonNull
    public static float[] materialPhosphor(@ColorInt int primary) {
        Hct hct = Hct.fromInt(primary);
        int argb = Hct.from(hct.getHue(), Math.max(MATERIAL_PHOSPHOR_MIN_CHROMA, hct.getChroma()),
            MATERIAL_PHOSPHOR_TONE).toInt();
        return new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
            (argb & 0xFF) / 255f, MATERIAL_PHOSPHOR_STRENGTH};
    }
}
