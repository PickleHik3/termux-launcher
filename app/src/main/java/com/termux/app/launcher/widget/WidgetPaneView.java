package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

/** Fixed middle-body UI; it has no authority over FULL or terminal geometry. */
public final class WidgetPaneView extends FrameLayout {
    public interface Listener { void onAddRequested(); }
    private final FrameLayout actionStrip;
    private final ImageButton compactAdd;
    private final WidgetGridView grid;
    private final LinearLayout empty;
    private final MaterialButton largeAdd;
    private final TextView emptyMessage;
    private final TextView notice;
    private final WidgetPickerSheetView picker;
    private final Runnable hideNotice;
    private Listener listener;

    public WidgetPaneView(@NonNull Context context, AttributeSet attrs) { this(context); }

    public WidgetPaneView(@NonNull Context context) {
        super(context); setId(R.id.widget_pane); setClipChildren(true); setClipToPadding(true);
        setVisibility(INVISIBLE);
        actionStrip = new FrameLayout(context);
        compactAdd = new ImageButton(context); compactAdd.setId(R.id.widget_add_compact);
        compactAdd.setImageResource(R.drawable.ic_status_bar_add_window);
        compactAdd.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        compactAdd.setColorFilter(0xBFFFFFFF);
        compactAdd.setContentDescription(context.getString(R.string.widget_add));
        compactAdd.setMinimumWidth(dp(48)); compactAdd.setMinimumHeight(dp(48)); compactAdd.setFocusable(false);
        GradientDrawable compactBackground = new GradientDrawable();
        compactBackground.setShape(GradientDrawable.OVAL);
        compactBackground.setColor(0x385F6368);
        compactAdd.setBackground(compactBackground);
        FrameLayout.LayoutParams compactAddParams = new FrameLayout.LayoutParams(dp(48), dp(48),
            Gravity.START | Gravity.TOP);
        actionStrip.addView(compactAdd, compactAddParams);
        addView(actionStrip, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP));

        grid = new WidgetGridView(context); grid.setId(R.id.widget_grid);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        gridParams.topMargin = dp(48); addView(grid, gridParams);

        empty = new LinearLayout(context); empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        largeAdd = new MaterialButton(context); largeAdd.setId(R.id.widget_add_large);
        largeAdd.setText("＋  " + context.getString(R.string.widget_add));
        largeAdd.setContentDescription(context.getString(R.string.widget_add));
        largeAdd.setMinimumHeight(dp(48)); largeAdd.setFocusable(false);
        empty.addView(largeAdd, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
        emptyMessage = new TextView(context); emptyMessage.setId(R.id.widget_empty_message);
        emptyMessage.setText(R.string.widget_empty_message); emptyMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); messageParams.topMargin = dp(8);
        empty.addView(emptyMessage, messageParams);
        LayoutParams emptyParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        emptyParams.topMargin = dp(48); addView(empty, emptyParams);

        notice = new TextView(context); notice.setId(R.id.widget_pane_notice); notice.setGravity(Gravity.CENTER);
        hideNotice = () -> notice.setVisibility(GONE);
        notice.setVisibility(GONE); notice.setPadding(dp(12), dp(6), dp(12), dp(6));
        LayoutParams noticeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(48),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL); addView(notice, noticeParams);

        picker = new WidgetPickerSheetView(context, item -> {
            if (providerListener != null) providerListener.onProviderSelected(item);
        });
        picker.setId(R.id.widget_picker_sheet);
        addView(picker, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        largeAdd.setOnClickListener(view -> { if (listener != null) listener.onAddRequested(); });
        compactAdd.setOnClickListener(view -> { if (listener != null) listener.onAddRequested(); });
    }

    private WidgetPickerAdapter.Listener providerListener;
    public void setListener(@NonNull Listener value,
                            @NonNull WidgetPickerAdapter.Listener providers) {
        listener = value; providerListener = providers;
    }
    @NonNull public WidgetGridView grid() { return grid; }
    @NonNull public WidgetPickerSheetView picker() { return picker; }
    public boolean onBackPressed() {
        if (!picker.isOpen()) return false;
        picker.close();
        return true;
    }

    /** Lazily created edit chrome; always the last child so it draws over every pane surface. */
    @NonNull public WidgetEditOverlayView widgetEditOverlay() {
        if (editOverlay == null) {
            editOverlay = new WidgetEditOverlayView(getContext());
            editOverlay.setId(R.id.widget_edit_overlay);
            addView(editOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        } else if (indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return editOverlay;
    }

    public void hideWidgetEditOverlay() {
        if (editOverlay != null) editOverlay.hide();
    }

    public boolean widgetEditActive() { return editOverlay != null && editOverlay.isShowing(); }

    private WidgetEditOverlayView editOverlay;

    public void render(@NonNull LauncherWidgetRepository repository,
                       @NonNull LauncherWidgetHostController.Capability capability) {
        hideWidgetEditOverlay();
        boolean populated = !repository.records().isEmpty();
        grid.refresh(repository.gridDefinition(), repository.records());
        empty.setVisibility(populated ? GONE : VISIBLE);
        // Keep the empty grid measured: picker span/fit is derived from these real pixels.
        grid.setVisibility(populated ? VISIBLE : INVISIBLE);
        compactAdd.setVisibility(populated ? VISIBLE : INVISIBLE);
        boolean supported = capability == LauncherWidgetHostController.Capability.AVAILABLE;
        largeAdd.setEnabled(supported); compactAdd.setEnabled(supported);
        emptyMessage.setText(supported ? R.string.widget_empty_message : R.string.widget_unsupported);
    }
    public void showNotice(@NonNull String message) {
        notice.setText(message); notice.setContentDescription(message); notice.setVisibility(VISIBLE);
        notice.removeCallbacks(hideNotice); notice.postDelayed(hideNotice, 3500);
    }
    public void setFullProgress(float progress) {
        setVisibility(progress >= 0.999f ? VISIBLE : INVISIBLE);
    }
    public void setFullState(float progress, boolean settled) {
        setVisibility(settled && progress >= 0.999f ? VISIBLE : INVISIBLE);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
