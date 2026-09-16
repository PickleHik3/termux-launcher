package com.termux.shared.termux.extrakeys;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * The colour one extra key can be given.
 *
 * <p>A role, not a raw colour: the cap is painted from the current theme's Material attributes, so
 * a key the user coloured follows dark mode, the wallpaper palette and the launcher's own scheme
 * without anything being stored per theme. The label takes the matching {@code on} role, which is
 * what keeps a coloured cap legible in every one of them.
 *
 * <p>{@link #BLACK} and {@link #WHITE} are the two fixed ones, for a cap that has to read the same
 * whatever the theme does; they are their own opposites' labels.
 *
 * <p>Resolving a role touches the theme, so callers resolve once when a key is bound or the theme
 * changes — never from {@code onDraw}.
 */
public enum ExtraKeyColorRole {

    PRIMARY("primary",
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        0xFF4F6BED, 0xFFFFFFFF),
    SECONDARY("secondary",
        com.google.android.material.R.attr.colorSecondary,
        com.google.android.material.R.attr.colorOnSecondary,
        0xFF5A5F72, 0xFFFFFFFF),
    TERTIARY("tertiary",
        com.google.android.material.R.attr.colorTertiary,
        com.google.android.material.R.attr.colorOnTertiary,
        0xFF7A5375, 0xFFFFFFFF),
    ERROR("error",
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        0xFFBA1A1A, 0xFFFFFFFF),
    PRIMARY_CONTAINER("primary_container",
        com.google.android.material.R.attr.colorPrimaryContainer,
        com.google.android.material.R.attr.colorOnPrimaryContainer,
        0xFFDDE1FF, 0xFF001155),
    SECONDARY_CONTAINER("secondary_container",
        com.google.android.material.R.attr.colorSecondaryContainer,
        com.google.android.material.R.attr.colorOnSecondaryContainer,
        0xFFDFE1F9, 0xFF171B2C),
    TERTIARY_CONTAINER("tertiary_container",
        com.google.android.material.R.attr.colorTertiaryContainer,
        com.google.android.material.R.attr.colorOnTertiaryContainer,
        0xFFFFD7F5, 0xFF30112F),
    ERROR_CONTAINER("error_container",
        com.google.android.material.R.attr.colorErrorContainer,
        com.google.android.material.R.attr.colorOnErrorContainer,
        0xFFFFDAD6, 0xFF410002),
    SURFACE_VARIANT("surface_variant",
        com.google.android.material.R.attr.colorSurfaceVariant,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        0xFF45464F, 0xFFC6C5D0),
    /** A fixed black cap with a white label. */
    BLACK("black", 0, 0, 0xFF000000, 0xFFFFFFFF),
    /** A fixed white cap with a black label. */
    WHITE("white", 0, 0, 0xFFFFFFFF, 0xFF000000);

    /** The token written into the key row's JSON. Stable: it is what a user's file holds. */
    @NonNull public final String token;

    @AttrRes private final int backgroundAttr;
    @AttrRes private final int labelAttr;
    @ColorInt private final int backgroundFallback;
    @ColorInt private final int labelFallback;

    ExtraKeyColorRole(@NonNull String token, @AttrRes int backgroundAttr, @AttrRes int labelAttr,
                      @ColorInt int backgroundFallback, @ColorInt int labelFallback) {
        this.token = token;
        this.backgroundAttr = backgroundAttr;
        this.labelAttr = labelAttr;
        this.backgroundFallback = backgroundFallback;
        this.labelFallback = labelFallback;
    }

    /** The attribute this role's cap is painted from, or 0 for the two fixed roles. */
    @AttrRes
    public int backgroundAttr() {
        return backgroundAttr;
    }

    /** The attribute this role's label is painted from, or 0 for the two fixed roles. */
    @AttrRes
    public int labelAttr() {
        return labelAttr;
    }

    /** The cap colour in the theme {@code context} is carrying. */
    @ColorInt
    public int background(@NonNull Context context) {
        return backgroundAttr == 0
            ? backgroundFallback
            : MaterialColors.getColor(context, backgroundAttr, backgroundFallback);
    }

    /** The label colour that goes with {@link #background(Context)}. */
    @ColorInt
    public int label(@NonNull Context context) {
        return labelAttr == 0
            ? labelFallback
            : MaterialColors.getColor(context, labelAttr, labelFallback);
    }

    /** The role a stored token names, or null for an absent, empty or unknown one. */
    @Nullable
    public static ExtraKeyColorRole fromToken(@Nullable String token) {
        if (token == null)
            return null;
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty())
            return null;
        for (ExtraKeyColorRole role : values()) {
            if (role.token.equals(trimmed))
                return role;
        }
        return null;
    }

    /** The token to store for a role, or null for "no colour, use the row's own styling". */
    @Nullable
    public static String tokenOf(@Nullable ExtraKeyColorRole role) {
        return role == null ? null : role.token;
    }

    /** A swatch for a role that is not set: the row's own transparent cap. */
    @ColorInt
    public static final int NO_COLOR = Color.TRANSPARENT;
}
