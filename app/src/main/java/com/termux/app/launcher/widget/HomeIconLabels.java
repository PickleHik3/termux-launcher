package com.termux.app.launcher.widget;

import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * The label under an app icon or a folder on Home, for both views: dressed by the
 * {@link HomeIconStyle} when the style changes, and measured against the cell in their
 * {@code onMeasure}. Nothing here changes the label from inside a measure: its line count is
 * set when the style or the cell's size changes ({@link #fitLines}).
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

    /**
     * How many lines the label should take in a cell content of {@code width}×{@code height}: one,
     * or two where the style asks for them and the cell keeps a labelled icon under two lines.
     */
    static int linesFor(@NonNull TextView label, int width, int height,
                        @NonNull HomeIconStyle style, float density) {
        if (!style.twoLines) return 1;
        // Not laid out yet: room for two, which the first size change takes back if need be.
        if (width <= 0 || height <= 0) return 2;
        return HomeAppIconMetrics.labelLines(width, height, label.getLineHeight(), density,
            style.iconScale(), true);
    }

    /**
     * Sets the label's line count for its cell: called when the style is set and when the view's
     * size changes, never from a measure. A change made from a size change is posted, so the
     * layout pass that reported the size is never asked for another from inside it.
     */
    static void fitLines(@NonNull View owner, @NonNull TextView label, int width, int height,
                         @NonNull HomeIconStyle style, float density, boolean fromLayout) {
        if (!style.twoLines) return;
        int lines = linesFor(label, width, height, style, density);
        if (label.getMaxLines() == lines) return;
        if (!fromLayout) {
            label.setMaxLines(lines);
            return;
        }
        owner.post(() -> {
            int now = linesFor(label, owner.getWidth(), owner.getHeight(), style, density);
            if (label.getMaxLines() != now) label.setMaxLines(now);
        });
    }

    /** Measures {@code label} for a cell content of {@code width}×{@code height} and answers the icon's layout. */
    @NonNull
    static HomeAppIconMetrics measure(@NonNull TextView label, int width, int height,
                                      @NonNull HomeIconStyle style, float density) {
        int inset = Math.round(HomeAppIconMetrics.INSET_DP * density);
        label.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width - 2 * inset),
            View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return HomeAppIconMetrics.of(width, height, label.getMeasuredHeight(), density, style.iconScale());
    }
}
