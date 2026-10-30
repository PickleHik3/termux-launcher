package com.termux.app.setup;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

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

    /** A section's heading and its one-line description. */
    static void header(@NonNull LinearLayout page, int titleRes, int descriptionRes) {
        Context context = page.getContext();
        TextView title = text(context, 22f, true, onSurface(page));
        title.setText(titleRes);
        page.addView(title, matchWrap(0));
        TextView description = text(context, 14f, false, onSurfaceVariant(page));
        description.setText(descriptionRes);
        page.addView(description, matchWrap(dp(context, 4)));
    }

    /** A title, an optional helper line and a switch on the right. */
    @NonNull
    static MaterialSwitch toggleRow(@NonNull LinearLayout page, int titleRes, int helperRes,
                                    boolean checked, int topMarginDp,
                                    @NonNull CompoundButton.OnCheckedChangeListener listener) {
        Context context = page.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, 16f, true, onSurface(page));
        title.setText(titleRes);
        words.addView(title, matchWrap(0));
        if (helperRes != 0) {
            TextView helper = text(context, 13f, false, onSurfaceVariant(page));
            helper.setText(helperRes);
            words.addView(helper, matchWrap(dp(context, 1)));
        }
        row.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(context.getString(titleRes));
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        // The whole row flips the switch.
        row.setOnClickListener(view -> toggle.toggle());
        page.addView(row, matchWrap(dp(context, topMarginDp)));
        return toggle;
    }

    /** A title with a quiet explanation under it, nothing to tap. */
    static void infoRow(@NonNull LinearLayout page, int titleRes, int detailRes, int topMarginDp) {
        Context context = page.getContext();
        TextView title = text(context, 15f, true, onSurface(page));
        title.setText(titleRes);
        page.addView(title, matchWrap(dp(context, topMarginDp)));
        TextView detail = text(context, 13.5f, false, onSurfaceVariant(page));
        detail.setText(detailRes);
        page.addView(detail, matchWrap(dp(context, 1)));
    }
}
