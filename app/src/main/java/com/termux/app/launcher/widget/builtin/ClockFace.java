package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * The Clock widget's eight clock faces (glossary, "Clock face"). Analog and Numerals are the
 * widget's own; the other six are the status bar's, drawn by the same
 * {@link com.termux.app.terminal.ClockFaceRenderer}. The {@link #key} is what a widget's settings
 * store; a bar face's key is the bar's own style value, so copying the bar's face is a lookup.
 */
public enum ClockFace {
    ANALOG("analog", R.string.bw_clock_face_analog, null),
    NUMERALS("numerals", R.string.bw_clock_face_numerals, null),
    SLAB(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB,
        R.string.termux_top_pane_clock_style_slab,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB),
    FLIP(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        R.string.termux_top_pane_clock_style_flip,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP),
    LCD(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        R.string.termux_top_pane_clock_style_lcd,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD),
    LED(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        R.string.termux_top_pane_clock_style_led,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED),
    TAPE(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        R.string.termux_top_pane_clock_style_tape,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE),
    MINIMAL(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        R.string.termux_top_pane_clock_style_minimal,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL);

    @NonNull public final String key;
    @StringRes public final int label;
    /** The bar style the renderer draws this face as; null for the widget's own two. */
    @Nullable private final String barStyle;

    ClockFace(@NonNull String key, @StringRes int label, @Nullable String barStyle) {
        this.key = key; this.label = label; this.barStyle = barStyle;
    }

    /** Whether the shared renderer draws this face (one of the bar's six). */
    public boolean isBarFace() { return barStyle != null; }

    /** The renderer style for this face, Minimal in its medium weight where {@code medium}. */
    @Nullable public String rendererStyle(boolean medium) {
        if (this == MINIMAL && medium) {
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM;
        }
        return barStyle;
    }

    /** The face stored under {@code key}, or null for none this build knows. */
    @Nullable public static ClockFace fromKey(@Nullable String key) {
        if (key == null) return null;
        for (ClockFace face : values()) if (face.key.equals(key)) return face;
        return null;
    }

    /**
     * The face the bar's {@code style} is: one of its six, Minimal's medium weight as Minimal,
     * anything unknown as the bar draws it (Flip, the renderer's fallback).
     */
    @NonNull public static ClockFace fromBarStyle(@Nullable String style) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(style)) {
            return MINIMAL;
        }
        ClockFace face = fromKey(style);
        return face == null || !face.isBarFace() ? FLIP : face;
    }
}
