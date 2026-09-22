package com.termux.app.terminal.io;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;

import java.util.ArrayList;
import java.util.List;

/**
 * The strip of colours a key can be given: one swatch per Material role, plus the row's own
 * styling at the front. Each swatch is drawn in the colour the current theme actually resolves the
 * role to, so what the user picks from is what the key will wear.
 *
 * <p>One widget, two homes — the key editor's sheet and the Appearance editor's popup — so the
 * choice looks and behaves the same in both.
 */
public final class ExtraKeyColorSwatches {

    /** A swatch was chosen. A null role is "no colour": the key goes back to the row's styling. */
    public interface OnPicked {
        void onPicked(@Nullable ExtraKeyColorRole role);
    }

    private ExtraKeyColorSwatches() {}

    private static final int SWATCH_DP = 40;
    private static final int SWATCH_GAP_DP = 8;

    /**
     * Builds the strip. It marks {@code current} with a check and re-marks itself as the user
     * picks, so the caller only has to store the choice.
     */
    @NonNull
    public static View build(@NonNull Context context, @Nullable ExtraKeyColorRole current,
                             @NonNull OnPicked onPicked) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(SWATCH_DP * density);
        int gap = Math.round(SWATCH_GAP_DP * density);
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutline, 0x66FFFFFF);
        int onSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);

        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);

        String[] roleNames = context.getResources()
            .getStringArray(R.array.extra_keys_color_role_names);
        final List<TextView> swatches = new ArrayList<>();
        final List<ExtraKeyColorRole> roles = new ArrayList<>();

        roles.add(null);
        for (ExtraKeyColorRole role : ExtraKeyColorRole.values()) roles.add(role);

        final ExtraKeyColorRole[] chosen = { current };
        final Runnable remark = () -> {
            for (int i = 0; i < swatches.size(); i++)
                swatches.get(i).setText(roles.get(i) == chosen[0] ? "✓" : "");
        };

        for (int i = 0; i < roles.size(); i++) {
            final ExtraKeyColorRole role = roles.get(i);
            int fill = role == null ? Color.TRANSPARENT : role.background(context);
            int label = role == null ? onSurface : role.label(context);

            TextView swatch = new TextView(context);
            swatch.setGravity(Gravity.CENTER);
            swatch.setTextColor(label);
            swatch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            swatch.setContentDescription(role == null
                ? context.getString(R.string.settings_extra_keys_color_default)
                : nameOf(roleNames, role));
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(fill);
            // Every swatch is ringed, so the "no colour" one still reads as a choice rather than a
            // hole in the strip.
            shape.setStroke(Math.max(1, Math.round(density)), outline);
            swatch.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(gap);
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(view -> {
                chosen[0] = role;
                remark.run();
                onPicked.onPicked(role);
            });
            swatches.add(swatch);
            strip.addView(swatch);
        }
        remark.run();

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.addView(strip, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    /** The role's name for a screen reader, falling back to its stored token. */
    @NonNull
    private static String nameOf(@NonNull String[] names, @NonNull ExtraKeyColorRole role) {
        return role.ordinal() < names.length ? names[role.ordinal()] : role.token;
    }
}
