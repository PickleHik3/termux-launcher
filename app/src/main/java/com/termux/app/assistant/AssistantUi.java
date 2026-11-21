package com.termux.app.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Sizes, type and colour roles of the Assistant page's views (sheet 01's tokens), in one place so
 * the page, the thread, the composer and the sheets agree. Colours are the theme's Material roles,
 * read from a view in the page ({@link Palette#read}) so they follow the launcher's scheme.
 */
final class AssistantUi {

    /** The roles the page draws with; read again on every dress so a scheme change reaches it. */
    static final class Palette {
        int surface;
        int surfaceHigh;
        int surfaceHighest;
        int onSurface;
        int onSurfaceVariant;
        int primary;
        int onPrimary;
        int primaryContainer;
        int onPrimaryContainer;
        int secondaryContainer;
        int onSecondaryContainer;
        int outline;
        int outlineVariant;
        int error;
        int errorContainer;
        int onErrorContainer;
        int success;

        @NonNull
        static Palette read(@NonNull View view) {
            Palette p = new Palette();
            p.surface = role(view, com.google.android.material.R.attr.colorSurface, 0xFF1B1B1F);
            p.surfaceHigh = role(view, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF2A2A2F);
            p.surfaceHighest = role(view, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF343439);
            p.onSurface = role(view, com.google.android.material.R.attr.colorOnSurface, 0xFFE3E2E6);
            p.onSurfaceVariant = role(view, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFC4C6D0);
            p.primary = role(view, androidx.appcompat.R.attr.colorPrimary, 0xFFAFC6FF);
            p.onPrimary = role(view, com.google.android.material.R.attr.colorOnPrimary, 0xFF002D6C);
            p.primaryContainer = role(view, com.google.android.material.R.attr.colorPrimaryContainer, 0xFF264585);
            p.onPrimaryContainer = role(view, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFFD9E2FF);
            p.secondaryContainer = role(view, com.google.android.material.R.attr.colorSecondaryContainer, 0xFF3F4759);
            p.onSecondaryContainer = role(view, com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFFDBE2F9);
            p.outline = role(view, com.google.android.material.R.attr.colorOutline, 0xFF8E9099);
            p.outlineVariant = role(view, com.google.android.material.R.attr.colorOutlineVariant, 0xFF44474F);
            p.error = role(view, androidx.appcompat.R.attr.colorError, 0xFFFFB4AB);
            p.errorContainer = role(view, com.google.android.material.R.attr.colorErrorContainer, 0xFF93000A);
            p.onErrorContainer = role(view, com.google.android.material.R.attr.colorOnErrorContainer, 0xFFFFDAD6);
            p.success = ContextCompat.getColor(view.getContext(), R.color.termux_chip_done);
            return p;
        }

        private static int role(View view, int attr, int fallback) {
            return MaterialColors.getColor(view, attr, fallback);
        }
    }

    private AssistantUi() {
    }

    static int dp(@NonNull View view, float dp) {
        return dp(view.getContext(), dp);
    }

    static int dp(@NonNull Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    static GradientDrawable fill(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    @NonNull
    static GradientDrawable disc(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Type tokens: size in sp, line height in sp, medium or regular. */
    static void type(@NonNull TextView view, float sizeSp, float lineSp, boolean medium, int color) {
        view.setTextSize(sizeSp);
        view.setLineSpacing(0f, lineSp / sizeSp);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
    }

    static void body(@NonNull TextView v, int color) {
        type(v, 16f, 24f, false, color);
    }

    static void bodySmall(@NonNull TextView v, int color) {
        type(v, 14f, 20f, false, color);
    }

    static void labelSmall(@NonNull TextView v, int color) {
        type(v, 14f, 20f, true, color);
    }

    static void label(@NonNull TextView v, int color) {
        type(v, 16f, 24f, true, color);
    }

    static void meta(@NonNull TextView v, int color) {
        type(v, 12f, 16f, false, color);
    }

    @NonNull
    static TextView text(@NonNull Context context) {
        TextView v = new TextView(context);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** A 48 dp touch target holding a 24 dp icon (or {@code iconDp}). */
    @NonNull
    static ImageButton iconButton(@NonNull Context context, @DrawableRes int icon, @StringRes int description,
                                  int tint, float iconDp) {
        ImageButton b = new ImageButton(context);
        b.setImageResource(icon);
        b.setContentDescription(context.getString(description));
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(context, (48f - iconDp) / 2f);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(rippleBackground(context));
        ImageViewCompat.setImageTintList(b, ColorStateList.valueOf(tint));
        b.setMinimumWidth(dp(context, 48));
        b.setMinimumHeight(dp(context, 48));
        return b;
    }

    @NonNull
    static android.graphics.drawable.Drawable rippleBackground(@NonNull Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(context, value.resourceId);
            if (d != null) return d;
        }
        return new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
    }

    static void tint(@NonNull ImageButton b, int color) {
        ImageViewCompat.setImageTintList(b, ColorStateList.valueOf(color));
    }

    /** A disabled control's ink: onSurface at 38%. */
    static int disabledInk(@NonNull Palette p) {
        return ColorUtils.setAlphaComponent(p.onSurface, 97);
    }

    /** A disabled container: onSurface at 12%. */
    static int disabledFill(@NonNull Palette p) {
        return ColorUtils.setAlphaComponent(p.onSurface, 31);
    }

    @NonNull
    static LinearLayout.LayoutParams linear(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    @NonNull
    static LinearLayout.LayoutParams weighted(int height) {
        return new LinearLayout.LayoutParams(0, height, 1f);
    }

    static void gravityCenterVertical(@NonNull LinearLayout row) {
        row.setGravity(Gravity.CENTER_VERTICAL);
    }

    static void setVisible(@NonNull View view, boolean visible) {
        int target = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != target) view.setVisibility(target);
    }

    static void setText(@NonNull TextView view, CharSequence text) {
        if (!view.getText().toString().contentEquals(text)) view.setText(text);
    }
}
