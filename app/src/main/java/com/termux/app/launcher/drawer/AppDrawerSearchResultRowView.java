package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The categories drawer's search-result row: icon at the left, the app name over its category name
 * at the right, on a softly washed 16dp-radius row — the redesign mock's list presentation.
 *
 * <p>It is still an {@link AppDrawerAppCellView}, deliberately: the icon comes from the same
 * rendered byte-budgeted cache, the label colour from the dock, and the tap from the dock's launch
 * ladder, all through the parent's one {@code bind}. Only the geometry and the extra category line
 * are this class's own.
 */
public final class AppDrawerSearchResultRowView extends AppDrawerAppCellView {

    private static final float ICON_DP = 44f;
    private static final float PAD_H_DP = 8f;
    private static final float PAD_V_DP = 9f;
    private static final float ICON_TEXT_GAP_DP = 14f;
    private static final float NAME_SP = 15f;
    private static final float CATEGORY_SP = 11f;
    private static final float RADIUS_DP = 16f;
    /** Row washes from the mock: rest 3%, pressed 9%, white over the dark glass. */
    private static final int REST_FILL = 0x08FFFFFF;
    private static final int PRESSED_FILL = 0x17FFFFFF;
    /** Category line at 45% of the name's colour. */
    private static final int CATEGORY_ALPHA = 0x73;

    @NonNull public final TextView category;

    public AppDrawerSearchResultRowView(@NonNull Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int padH = Math.round(PAD_H_DP * density);
        int padV = Math.round(PAD_V_DP * density);
        setPadding(padH, padV, padH, padV);
        setBackground(rowBackground(density));

        // Re-home the shared label into a two-line text column beside the icon.
        removeView(label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_SP);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setGravity(Gravity.START);
        label.setPadding(0, 0, 0, 0);

        category = new TextView(context);
        category.setTextSize(TypedValue.COMPLEX_UNIT_SP, CATEGORY_SP);
        category.setSingleLine(true);
        category.setMaxLines(1);
        category.setEllipsize(TextUtils.TruncateAt.END);
        category.setGravity(Gravity.START);
        category.setIncludeFontPadding(false);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        column.addView(label, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(category, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = Math.round(ICON_TEXT_GAP_DP * density);
        addView(column, columnParams);
    }

    @NonNull
    private static StateListDrawable rowBackground(float density) {
        float radius = RADIUS_DP * density;
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(radius);
        pressed.setColor(PRESSED_FILL);
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(radius);
        rest.setColor(REST_FILL);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_pressed}, pressed);
        background.addState(new int[0], rest);
        return background;
    }

    /**
     * Row geometry is this view's own: a fixed 44dp icon slot (the rendered drawable keeps the
     * shared cache's pixel size and centre-fits into it) and wrap-content height from the paddings.
     */
    @Override
    protected void applyGeometry(float rowHeightPx, int iconPx) {
        int slot = Math.round(ICON_DP * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
        if (iconParams != null && (iconParams.width != slot || iconParams.height != slot)) {
            iconParams.width = slot;
            iconParams.height = slot;
            icon.setLayoutParams(iconParams);
        }
    }

    /** The category line under the name, or nothing when the app has no classified bucket. */
    public void setCategoryLabel(@Nullable CharSequence text) {
        category.setText(text);
        category.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
        category.setTextColor(ColorUtils.setAlphaComponent(
            label.getCurrentTextColor(), CATEGORY_ALPHA));
    }

    @Override
    public void unbind() {
        super.unbind();
        category.setText(null);
        category.setVisibility(GONE);
    }
}
