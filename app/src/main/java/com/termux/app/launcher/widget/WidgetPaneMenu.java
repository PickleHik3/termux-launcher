package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;

import java.util.List;

/**
 * Anchored long-press menu for the widgets pane, in the launcher's shared popup material:
 * rounded panel, hairline glass rim, non-focusable window that never touches the IME.
 */
final class WidgetPaneMenu {
    interface Listener { void onItemSelected(@NonNull WidgetPaneMenuPolicy.Item item); }

    private WidgetPaneMenu() {}

    /** Builds and shows the menu at a raw screen location over the pane. */
    @NonNull
    static PopupWindow show(@NonNull View pane, @NonNull List<WidgetPaneMenuPolicy.Item> items,
                            float rawX, float rawY, @NonNull Listener listener) {
        Context context = pane.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(3f * density);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(14f * density);
        background.setColor(0xE6202124);
        background.setStroke(Math.max(1, Math.round(1.25f * density)), 0x3DFFFFFF);
        shell.setBackground(background);
        shell.setClipToOutline(true);

        final PopupWindow[] popupHolder = new PopupWindow[1];
        for (WidgetPaneMenuPolicy.Item item : items) {
            TextView row = new TextView(context);
            row.setText(titleFor(context, item));
            row.setContentDescription(row.getText());
            row.setTextColor(Color.WHITE);
            row.setTextSize(14f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(Math.round(44f * density));
            row.setMinWidth(Math.round(180f * density));
            row.setPadding(Math.round(14f * density), 0, Math.round(14f * density), 0);
            TypedValue ripple = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            row.setOnClickListener(view -> {
                if (popupHolder[0] != null) popupHolder[0].dismiss();
                listener.onItemSelected(item);
            });
            shell.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        shell.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = shell.getMeasuredWidth();
        int height = shell.getMeasuredHeight();

        PopupWindow popup = new PopupWindow(shell, width, height, false);
        popupHolder[0] = popup;
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(8f * density);

        int[] paneOnScreen = new int[2];
        pane.getLocationOnScreen(paneOnScreen);
        int paneLeft = paneOnScreen[0];
        int paneTop = paneOnScreen[1];
        int x = clamp(Math.round(rawX) - width / 2, paneLeft,
            Math.max(paneLeft, paneLeft + pane.getWidth() - width));
        int y = clamp(Math.round(rawY) - height / 2, paneTop,
            Math.max(paneTop, paneTop + pane.getHeight() - height));
        popup.showAtLocation(pane, Gravity.NO_GRAVITY, x, y);

        shell.setAlpha(0f);
        shell.setTranslationY(8f * density);
        shell.animate().alpha(1f).translationY(0f).setDuration(150)
            .setInterpolator(new DecelerateInterpolator()).start();
        return popup;
    }

    @NonNull private static String titleFor(@NonNull Context context,
                                            @NonNull WidgetPaneMenuPolicy.Item item) {
        switch (item) {
            case ADD_WIDGET: return context.getString(R.string.widget_add);
            case EDIT_WIDGETS: return context.getString(R.string.widget_menu_edit_widgets);
            case ADD_PAGE: return context.getString(R.string.widget_menu_add_page);
            case REMOVE_PAGE: return context.getString(R.string.widget_menu_remove_page);
            default: return "";
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
