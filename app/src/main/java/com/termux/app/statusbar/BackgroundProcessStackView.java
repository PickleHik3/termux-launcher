package com.termux.app.statusbar;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.List;

/** Non-interactive top-trailing stack of persistent background command titles. */
public final class BackgroundProcessStackView extends LinearLayout {

    private final int mOnSurface;

    public BackgroundProcessStackView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setClickable(false);
        setFocusable(false);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(LayoutTransition.DISAPPEARING, 160L);
        transition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 180L);
        transition.setDuration(LayoutTransition.APPEARING, 140L);
        setLayoutTransition(transition);
    }

    public void bind(@NonNull List<BackgroundProcessModel.Entry> entries) {
        removeAllViews();
        int shown = Math.min(3, entries.size());
        for (int i = 0; i < shown; i++) {
            boolean overflowRow = i == 2 && entries.size() > 3;
            View row = buildRow(entries.get(i).displayText(), overflowRow,
                Math.max(0, entries.size() - 3));
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(28));
            if (i > 0) params.topMargin = dp(3);
            addView(row, params);
        }
        setVisibility(entries.isEmpty() ? GONE : VISIBLE);
    }

    private View buildRow(@NonNull String text, boolean overflow, int hiddenCount) {
        FrameLayout frame = new FrameLayout(getContext());
        frame.setClickable(false);
        TextView title = new TextView(getContext());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(dp(220));
        title.setText(text);
        title.setTextColor(mOnSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        title.setIncludeFontPadding(false);
        title.setPadding(dp(10), 0, dp(10), 0);
        title.setBackground(chipBackground());
        if (overflow) title.setAlpha(0.28f);
        frame.addView(title, new FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END));
        if (overflow) {
            TextView dots = new TextView(getContext());
            dots.setText("•••");
            dots.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 220));
            dots.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            dots.setGravity(Gravity.CENTER);
            dots.setContentDescription(getResources().getQuantityString(
                R.plurals.background_process_hidden_count, hiddenCount, hiddenCount));
            frame.addView(dots, new FrameLayout.LayoutParams(dp(42),
                LayoutParams.MATCH_PARENT, Gravity.END));
        }
        return frame;
    }

    private GradientDrawable chipBackground() {
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 150));
        chip.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(mOnSurface, 38));
        chip.setCornerRadius(dp(14));
        return chip;
    }

    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        float density = context.getResources().getDisplayMetrics().density;
        params.topMargin = Math.round(42f * density);
        params.setMarginEnd(Math.round(10f * density));
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
