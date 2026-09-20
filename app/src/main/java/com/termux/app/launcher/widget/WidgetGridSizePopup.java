package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The widget grid's size, as two columns of numbers with a {@code ×} between them, hanging off
 * the tab that opened it. Dragging either column changes the grid straight away, so the widgets
 * reflow under the finger; a tap outside puts the panel away.
 */
public final class WidgetGridSizePopup {

    /** Told the size the wheels now read. */
    public interface Listener {
        void onGridSizeChanged(int columns, int rows);
    }

    @NonNull private final PopupWindow mPopup;

    private WidgetGridSizePopup(@NonNull PopupWindow popup) {
        mPopup = popup;
    }

    /**
     * Show the panel under {@code tab}, which is where the tab sits in {@code page}'s own
     * coordinates.
     */
    @NonNull
    public static WidgetGridSizePopup show(@NonNull View page, @NonNull RectF tab,
                                           int columns, int rows, @NonNull Listener listener) {
        Context context = page.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int onSurfaceVariant = MaterialColors.getColor(page,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFCCCCCC);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(14f * density);
        shell.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(16f * density);
        background.setColor(MaterialColors.getColor(page,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh, 0xFF202124));
        background.setStroke(Math.max(1, Math.round(1f * density)),
            ColorUtils.setAlphaComponent(onSurfaceVariant, 0x3D));
        shell.setBackground(background);

        GridSizeWheelView columnsWheel = new GridSizeWheelView(context,
            GridSizeWheelPolicy.columns(), columns);
        GridSizeWheelView rowsWheel = new GridSizeWheelView(context,
            GridSizeWheelPolicy.rows(), rows);
        GridSizeWheelView.Listener relay = value ->
            listener.onGridSizeChanged(columnsWheel.value(), rowsWheel.value());
        columnsWheel.setListener(relay);
        rowsWheel.setListener(relay);

        shell.addView(wheelColumn(context, columnsWheel,
            context.getString(R.string.widget_grid_size_columns), onSurfaceVariant, density));
        TextView times = new TextView(context);
        times.setText(R.string.widget_grid_size_separator);
        times.setTextColor(onSurfaceVariant);
        times.setTextSize(16f);
        LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timesParams.leftMargin = Math.round(6f * density);
        timesParams.rightMargin = Math.round(6f * density);
        // The × belongs beside the numbers, not beside the labels under them.
        timesParams.bottomMargin = Math.round(18f * density);
        timesParams.gravity = Gravity.CENTER_VERTICAL;
        shell.addView(times, timesParams);
        shell.addView(wheelColumn(context, rowsWheel,
            context.getString(R.string.widget_grid_size_rows), onSurfaceVariant, density));

        shell.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = shell.getMeasuredWidth();
        int height = shell.getMeasuredHeight();

        PopupWindow popup = new PopupWindow(shell, width, height, false);
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(10f * density);

        int[] pageOnScreen = new int[2];
        page.getLocationOnScreen(pageOnScreen);
        // Hanging from the tab's own edge: right-aligned under it, and never off the page.
        int x = clamp(Math.round(pageOnScreen[0] + tab.right) - width, pageOnScreen[0],
            Math.max(pageOnScreen[0], pageOnScreen[0] + page.getWidth() - width));
        int y = clamp(Math.round(pageOnScreen[1] + tab.bottom + 4f * density), pageOnScreen[1],
            Math.max(pageOnScreen[1], pageOnScreen[1] + page.getHeight() - height));
        popup.showAtLocation(page, Gravity.NO_GRAVITY, x, y);

        shell.setAlpha(0f);
        shell.setTranslationY(-8f * density);
        shell.animate().alpha(1f).translationY(0f).setDuration(150)
            .setInterpolator(new DecelerateInterpolator()).start();
        return new WidgetGridSizePopup(popup);
    }

    /** One wheel with its label under it. */
    @NonNull
    private static View wheelColumn(@NonNull Context context, @NonNull GridSizeWheelView wheel,
                                    @NonNull String label, int labelColor, float density) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        wheel.setContentDescription(label + " " + wheel.value());
        column.addView(wheel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView caption = new TextView(context);
        caption.setText(label);
        caption.setTextColor(labelColor);
        caption.setTextSize(11f);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = Math.round(4f * density);
        column.addView(caption, captionParams);
        return column;
    }

    public boolean isShowing() {
        return mPopup.isShowing();
    }

    /** The panel itself: its two wheels and their labels. */
    @NonNull
    View content() {
        return mPopup.getContentView();
    }

    public void dismiss() {
        if (mPopup.isShowing()) mPopup.dismiss();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
