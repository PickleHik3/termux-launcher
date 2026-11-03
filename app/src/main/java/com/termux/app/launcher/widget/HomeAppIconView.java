package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One app icon on a Home page: the icon, and the app's label under it in one ellipsised line.
 * The label is white with a soft shadow, the way a home screen writes over a wallpaper or glass,
 * at the dock's small size. Sizes come from {@link HomeAppIconMetrics}; the view only applies them.
 * The cell around it keeps the gutter, the clip and the long press, so a tap here is a launch and
 * a hold is the page's edit mode, as for any widget.
 */
final class HomeAppIconView extends ViewGroup implements com.termux.app.theme.TextScale.Follower {
    /** The label's size: the drawer's 11 sp, a step above the dock's tighter 10 sp. */
    static final float LABEL_SP = 11f;
    /** How faint an app that is gone is drawn. */
    static final float UNAVAILABLE_ALPHA = 0.4f;

    private final ImageView icon;
    private final TextView label;
    private final float density;
    @NonNull private HomeAppIconMetrics metrics = HomeAppIconMetrics.of(0, 0, 0, 1f);
    @NonNull private HomeIconStyle style = HomeIconStyle.DEFAULT;
    /** The label's ink from the wallpaper under it (editor-redesign §8). */
    private final HomeLabelInk labelInk = new HomeLabelInk();

    HomeAppIconView(@NonNull Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon);
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

    /** The app's artwork and label; {@code unavailable} draws an app that is gone, faint. */
    void bind(@Nullable Drawable artwork, @NonNull CharSequence text, boolean unavailable) {
        icon.setImageDrawable(artwork);
        icon.setAlpha(unavailable ? UNAVAILABLE_ALPHA : 1f);
        label.setAlpha(unavailable ? UNAVAILABLE_ALPHA : 1f);
        if (!TextUtils.equals(label.getText(), text)) label.setText(text);
        setContentDescription(text);
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
        // The dress put Home's fixed shadow back; the wallpaper's ink and shadow, once known, win.
        labelInk.apply(label, value.shadow, density);
        HomeIconLabels.fitLines(this, label, getWidth(), getHeight(), value, density, false);
        requestLayout();
    }

    /**
     * The label takes light or dark ink from the wallpaper under it, read through the page's
     * Look; a label on no Home page (a folder popup's cell, on its own glass) is left as it is.
     * Cheap when nothing moved: the sample is kept until the label moves or the wallpaper does.
     */
    void refreshLabelInk() {
        if (label.getVisibility() != VISIBLE) return;
        com.termux.app.chrome.WidgetInkSource source = HomeLabelInk.sourceFor(this);
        if (source == null) return;
        labelInk.wallpaperUnder(label, source);
        labelInk.apply(label, style.shadow, density);
    }

    @Override public void onTextScaleChanged() {
        HomeIconLabels.followTextScale(this, label, style, density);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        HomeIconLabels.fitLines(this, label, w, h, style, density, true);
    }

    boolean labelShown() { return label.getVisibility() == VISIBLE; }

    int iconSizePx() { return metrics.iconPx; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        metrics = HomeIconLabels.measure(label, width, height, style, density);
        label.setVisibility(metrics.showLabel ? VISIBLE : GONE);
        int side = MeasureSpec.makeMeasureSpec(metrics.iconPx, MeasureSpec.EXACTLY);
        icon.measure(side, side);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t;
        int gap = Math.round(HomeAppIconMetrics.LABEL_GAP_DP * density);
        int labelHeight = metrics.showLabel ? label.getMeasuredHeight() : 0;
        int total = metrics.iconPx + (metrics.showLabel ? gap + labelHeight : 0);
        int top = Math.max(0, (height - total) / 2);
        int iconLeft = (width - metrics.iconPx) / 2;
        icon.layout(iconLeft, top, iconLeft + metrics.iconPx, top + metrics.iconPx);
        if (metrics.showLabel) {
            int labelWidth = label.getMeasuredWidth();
            int labelLeft = (width - labelWidth) / 2;
            int labelTop = top + metrics.iconPx + gap;
            label.layout(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight);
            refreshLabelInk();
        }
    }

    @Override public CharSequence getAccessibilityClassName() {
        return android.widget.Button.class.getName();
    }

    /** Not a scrolling container: the press shows at once. */
    @Override public boolean shouldDelayChildPressedState() { return false; }
}
