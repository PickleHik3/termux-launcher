package com.termux.app.launcher.widget;

import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * The label under an app icon or a folder on Home, for both views: dressed by the
 * {@link HomeIconStyle} when the style changes, and measured against the cell in their
 * {@code onMeasure}. Nothing here requests a layout from inside a measure except a change of the
 * label's line count, which is compared first and so happens once per change of the cell or the
 * style.
 */
final class HomeIconLabels {

    /** The label's shadow: soft, a hair below, so white writing stays legible on a pale wallpaper. */
    static final float SHADOW_RADIUS_DP = 3f;
    static final float SHADOW_DY_DP = 1f;
    static final int SHADOW_COLOR = 0x99000000;

    private HomeIconLabels() {}

    /** Dresses {@code label} for {@code style}: its size, its shadow, one line or room for two. */
    static void dress(@NonNull TextView label, @NonNull HomeIconStyle style, float density) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.labelSize.sp,
            label.getResources().getDisplayMetrics());
        if (label.getTextSize() != px) label.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.labelSize.sp);
        if (style.shadow) {
            label.setShadowLayer(SHADOW_RADIUS_DP * density, 0f, SHADOW_DY_DP * density, SHADOW_COLOR);
        } else {
            label.setShadowLayer(0f, 0f, 0f, 0);
        }
        // One line is the single-line label Home always drew; two lines wrap, and the measure
        // below takes the second back where the cell has no room for it.
        label.setSingleLine(!style.twoLines);
        if (style.twoLines) label.setMaxLines(2);
    }

    /** Measures {@code label} for a cell content of {@code width}×{@code height} and answers the icon's layout. */
    @NonNull
    static HomeAppIconMetrics measure(@NonNull TextView label, int width, int height,
                                      @NonNull HomeIconStyle style, float density) {
        int inset = Math.round(HomeAppIconMetrics.INSET_DP * density);
        if (style.twoLines) {
            int lines = HomeAppIconMetrics.labelLines(width, height, label.getLineHeight(), density,
                style.iconScale(), true);
            if (label.getMaxLines() != lines) label.setMaxLines(lines);
        }
        label.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width - 2 * inset),
            View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return HomeAppIconMetrics.of(width, height, label.getMeasuredHeight(), density, style.iconScale());
    }
}
