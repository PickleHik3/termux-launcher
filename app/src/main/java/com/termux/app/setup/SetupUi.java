package com.termux.app.setup;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/** The wizard's colours and small view builders, so the sections look alike. */
final class SetupUi {

    private SetupUi() {}

    static int dp(@NonNull Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            context.getResources().getDisplayMetrics()));
    }

    static int surface(@NonNull View view) {
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface,
            Color.BLACK);
    }

    static int onSurface(@NonNull View view) {
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface,
            Color.WHITE);
    }

    static int onSurfaceVariant(@NonNull View view) {
        return MaterialColors.getColor(view,
            com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface(view));
    }

    static int accent(@NonNull View view) {
        return MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary,
            onSurface(view));
    }

    @NonNull
    static TextView text(@NonNull Context context, float sp, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setTextColor(color);
        return view;
    }

    @NonNull
    static LinearLayout.LayoutParams matchWrap(int topMarginPx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        return params;
    }

    /** A choice card's outline and wash: the chosen one carries the accent. */
    static void dressChoice(@NonNull View card, boolean chosen) {
        Context context = card.getContext();
        int accent = accent(card);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 16));
        shape.setColor(chosen ? ColorUtils.setAlphaComponent(accent, 36) : Color.TRANSPARENT);
        shape.setStroke(dp(context, chosen ? 2 : 1), chosen ? accent
            : ColorUtils.setAlphaComponent(onSurface(card), 60));
        card.setBackground(shape);
        card.setSelected(chosen);
    }
}
