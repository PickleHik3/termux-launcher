package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Compact trailing status-bar widget: a native vector icon plus a short value, e.g. a CPU/RAM
 * percentage or a temperature. Sized to sit on the same row as the window pills. Tapping it
 * is expected to open an anchored detail card; the widget itself is the anchor view.
 */
public final class StatusBarWidgetView extends LinearLayout {

    public enum ColorRole { PRIMARY, SECONDARY, TERTIARY }

    private final ImageView mIcon;
    private final TextView mValue;
    private boolean mAccent;
    @NonNull private ColorRole mColorRole = ColorRole.PRIMARY;

    public StatusBarWidgetView(Context context) {
        this(context, null);
    }

    public StatusBarWidgetView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        setClipToPadding(false);
        setClipChildren(false);
        setPadding(dp(5), dp(1), dp(5), dp(1));
        setMinimumWidth(dp(34));

        mIcon = new ImageView(context);
        mIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mIcon, new LayoutParams(dp(15), dp(15)));

        mValue = new TextView(context);
        mValue.setGravity(Gravity.CENTER_VERTICAL);
        mValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mValue.setIncludeFontPadding(false);
        mValue.setSingleLine(true);
        LayoutParams valueParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp(2));
        addView(mValue, valueParams);

        applyColors();
    }

    /** Material vector resource shown before the value. */
    public void setIconResource(@DrawableRes int drawableRes) {
        mIcon.setImageResource(drawableRes);
    }

    public void setValue(@NonNull CharSequence value) {
        mValue.setText(value);
    }

    /** Gives CPU, memory and weather distinct wallpaper-derived Material roles. */
    public void setColorRole(@NonNull ColorRole colorRole) {
        if (mColorRole == colorRole) return;
        mColorRole = colorRole;
        applyColors();
    }

    /** Accent styling used while the widget's detail card is open. */
    public void setAccent(boolean accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        applyColors();
    }

    private void applyColors() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        int roleColor = mColorRole == ColorRole.SECONDARY ? secondary
            : mColorRole == ColorRole.TERTIARY ? tertiary : primary;

        // The trailing stats read as one status cluster. Color and dot separators provide the
        // grouping, so individual pill backgrounds only add visual noise.
        setBackground(null);

        ImageViewCompat.setImageTintList(mIcon, ColorStateList.valueOf(roleColor));
        mValue.setTextColor(ColorUtils.setAlphaComponent(roleColor, mAccent ? 255 : 238));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
