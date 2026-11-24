package com.termux.app.surfaces;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * What the editor's frame chips share (the bar chips, the Hidden column's chips, the place
 * buttons and the keyboard grip's pill): one text size, one way of setting a label, and a pill
 * background from a surface colour.
 */
final class BarChipStyle {

    private BarChipStyle() {}

    /** The chips' text size. */
    static final float TEXT_SP = 12f;

    /** {@code label} as a chip's one line of text in {@code color}. */
    static void styleLabel(@NonNull TextView label, int color) {
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        label.setTextColor(color);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_VERTICAL);
    }

    /** A pill of {@code color} at {@code alpha} (0 to 255), corners {@code radiusPx}. */
    @NonNull
    static GradientDrawable pill(int color, int alpha, float radiusPx) {
        GradientDrawable back = new GradientDrawable();
        back.setColor((alpha << 24) | (color & 0x00FFFFFF));
        back.setCornerRadius(radiusPx);
        return back;
    }
}
