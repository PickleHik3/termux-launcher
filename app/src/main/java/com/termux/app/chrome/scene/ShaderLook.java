package com.termux.app.chrome.scene;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

/**
 * The look a shader sees: {@code tlDark} and {@code tlColor0..2} (accent, surface, on-surface) from
 * the Context's Material roles, which the scheme writes when it is on. One place for the live
 * layer, the picker thumbnails and the still, so their roles cannot drift apart.
 */
public final class ShaderLook {
    public final boolean dark;
    public final int accent;
    public final int surface;
    public final int onSurface;

    public ShaderLook(boolean dark, int accent, int surface, int onSurface) {
        this.dark = dark;
        this.accent = accent;
        this.surface = surface;
        this.onSurface = onSurface;
    }

    /** The look of {@code context}'s theme and night mode; pass an activity or a view's context. */
    @NonNull
    public static ShaderLook resolve(@NonNull Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xff6750a4);
        int surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface,
            dark ? 0xff141218 : 0xfffef7ff);
        int onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface,
            dark ? 0xffe6e0e9 : 0xff1d1b20);
        return new ShaderLook(dark, accent, surface, onSurface);
    }

    /** Writes the look into {@code in}. */
    public void applyTo(@NonNull ShaderInputs in) {
        in.dark = dark ? 1f : 0f;
        in.setColor(0, accent);
        in.setColor(1, surface);
        in.setColor(2, onSurface);
    }
}
