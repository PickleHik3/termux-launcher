package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * One folder on a Home page: a plate the size of an app icon holding up to four of its apps'
 * icons in a 2×2 miniature, and the folder's name under it, laid out exactly as an app icon is
 * ({@link HomeAppIconMetrics}) so a folder and an icon side by side line up. The miniature is the
 * drawer folder's ({@code AppDrawerFolderCellView}): two 42% minis and a 6% gap, centred.
 */
final class HomeFolderView extends ViewGroup {
    /** The drawer folder's mini size, as a fraction of the icon. */
    static final float MINI_FRACTION = 0.42f;
    /** The gap between minis, as a fraction of the icon. */
    static final float GAP_FRACTION = 0.06f;
    /** How many apps the miniature shows. */
    static final int SHOWN = 4;

    private final View plate;
    private final GradientDrawable plateShape = new GradientDrawable();
    private final ImageView[] minis = new ImageView[SHOWN];
    private final TextView label;
    private final float density;
    @NonNull private HomeAppIconMetrics metrics = HomeAppIconMetrics.of(0, 0, 0, 1f);
    @NonNull private HomeIconStyle style = HomeIconStyle.DEFAULT;

    HomeFolderView(@NonNull Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        plate = new View(context);
        // A light frosted plate over the wallpaper or the glass, as a home screen's folder reads.
        plateShape.setColor(0x40FFFFFF);
        plateShape.setStroke(Math.max(1, Math.round(density)), 0x33FFFFFF);
        plate.setBackground(plateShape);
        plate.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(plate);
        for (int i = 0; i < SHOWN; i++) {
            ImageView mini = new ImageView(context);
            mini.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mini.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            minis[i] = mini;
            addView(mini);
        }
        label = new TextView(context);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(Color.WHITE);
        HomeIconLabels.dress(label, style, density);
        label.setIncludeFontPadding(false);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(label);
        setClickable(true);
        setFocusable(true);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
            ripple, true) && ripple.resourceId != 0) {
            setBackgroundResource(ripple.resourceId);
        }
    }

    /**
     * The folder's name and the artwork of its first apps, in order; a null drawable is an app
     * that is gone, drawn faint with the system's default icon in its place.
     */
    void bind(@NonNull CharSequence name, @NonNull List<Drawable> artwork,
              @NonNull List<Boolean> unavailable) {
        for (int i = 0; i < SHOWN; i++) {
            ImageView mini = minis[i];
            if (i < artwork.size()) {
                mini.setImageDrawable(artwork.get(i));
                mini.setAlpha(i < unavailable.size() && unavailable.get(i)
                    ? HomeAppIconView.UNAVAILABLE_ALPHA : 1f);
                mini.setVisibility(VISIBLE);
            } else {
                mini.setImageDrawable(null);
                mini.setVisibility(GONE);
            }
        }
        if (!TextUtils.equals(label.getText(), name)) label.setText(name);
        setContentDescription(name);
    }

    @NonNull CharSequence labelText() { return label.getText(); }

    /**
     * How Home draws its icons and labels (Settings › Home › Icons and labels). Set when the
     * style changes, never from a measure.
     */
    void setStyle(@NonNull HomeIconStyle value) {
        if (style.equals(value)) return;
        style = value;
        HomeIconLabels.dress(label, value, density);
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        metrics = HomeIconLabels.measure(label, width, height, style, density);
        label.setVisibility(metrics.showLabel ? VISIBLE : GONE);
        int side = MeasureSpec.makeMeasureSpec(metrics.iconPx, MeasureSpec.EXACTLY);
        plate.measure(side, side);
        plateShape.setCornerRadius(metrics.iconPx * 0.28f);
        int mini = MeasureSpec.makeMeasureSpec(miniPx(metrics.iconPx), MeasureSpec.EXACTLY);
        for (ImageView view : minis) view.measure(mini, mini);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t;
        int gap = Math.round(HomeAppIconMetrics.LABEL_GAP_DP * density);
        int labelHeight = metrics.showLabel ? label.getMeasuredHeight() : 0;
        int icon = metrics.iconPx;
        int total = icon + (metrics.showLabel ? gap + labelHeight : 0);
        int top = Math.max(0, (height - total) / 2);
        int left = (width - icon) / 2;
        plate.layout(left, top, left + icon, top + icon);
        int mini = miniPx(icon);
        int miniGap = Math.max(1, Math.round(icon * GAP_FRACTION));
        int pad = Math.max(0, (icon - 2 * mini - miniGap) / 2);
        for (int i = 0; i < SHOWN; i++) {
            int x = left + pad + (i % 2) * (mini + miniGap);
            int y = top + pad + (i / 2) * (mini + miniGap);
            minis[i].layout(x, y, x + mini, y + mini);
        }
        if (metrics.showLabel) {
            int labelWidth = label.getMeasuredWidth();
            int labelLeft = (width - labelWidth) / 2;
            int labelTop = top + icon + gap;
            label.layout(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight);
        }
    }

    private static int miniPx(int iconPx) {
        return Math.max(1, Math.round(iconPx * MINI_FRACTION));
    }

    @Override public CharSequence getAccessibilityClassName() {
        return android.widget.Button.class.getName();
    }

    @Override public boolean shouldDelayChildPressedState() { return false; }
}
