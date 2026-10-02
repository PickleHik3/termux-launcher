package com.termux.app.terminal;

import androidx.annotation.Nullable;

/** The retro monitor a pane card is drawn through. Pure data; the shader lives in {@link PaneRetroEffect}. */
public enum PaneRetroStyle {
    NONE("none", 0f, 0f, 0f, 0f),
    CRT("crt", 0f, 0f, 0f, 0f),
    CRT_GREEN("crt_green", 0.35f, 1.0f, 0.55f, 0.85f),
    CRT_AMBER("crt_amber", 1.0f, 0.68f, 0.25f, 0.85f),
    TFT("tft", 0f, 0f, 0f, 0f);

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
        return mTint.clone();
    }

    /** True for the scanline family (CRT and its phosphor tints). */
    public boolean usesCrt() {
        return this == CRT || this == CRT_GREEN || this == CRT_AMBER;
    }
}
