package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.launcher.icon.AsyncIconBinder;
import com.termux.app.launcher.model.LauncherAppEntry;

/**
 * The single icon-and-label implementation shared by both drawer presentations.
 *
 * <p>The icon binds through {@link SuggestionBarView#bindRenderedIcon}: a held icon is set at once,
 * and a missing one shows a quiet tile until the worker has rendered it, then fades in. Binding
 * never renders on the main thread, so a cold scroll costs a tile per cell, not three bitmaps and
 * a blur.</p>
 */
public class AppDrawerAppCellView extends LinearLayout {

    /** Stream-level guard used because a nested close deliberately does not cancel its child. */
    public interface ClickGate {
        boolean suppressCellClick();
    }

    public static final ClickGate ALLOW_CLICKS = () -> false;

    /** Highlight ring and wash, seeds resolved against the chrome's polarity when drawn. */
    private static final int HIGHLIGHT_RADIUS_DP = 16;
    private static final int HIGHLIGHT_STROKE_DP = 2;
    private static final int HIGHLIGHT_RING_SEED = 0xE6FFFFFF;
    private static final int HIGHLIGHT_WASH_SEED = 0x26FFFFFF;
    private static final int HIGHLIGHT_WASH_REST = 0x08FFFFFF;

    @NonNull public final ImageView icon;
    @NonNull public final TextView label;
    private boolean mKeyboardHighlighted;

    public AppDrawerAppCellView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAdjustViewBounds(true);
        icon.setPadding(0, 0, 0, 0);
        icon.setDuplicateParentStateEnabled(true);
        addView(icon, new LinearLayout.LayoutParams(0, 0));

        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppDrawerAppsAdapter.LABEL_TEXT_SP);
        label.setSingleLine(true);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setIncludeFontPadding(false);
        addView(label, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                     @Nullable AppDrawerGridMetrics metrics, @NonNull ClickGate clickGate) {
        bind(dock, entry, metrics, clickGate, null);
    }

    public void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                     @Nullable AppDrawerGridMetrics metrics, @NonNull ClickGate clickGate,
                     @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindInternal(dock, entry, metrics == null ? 0f : metrics.iconPx,
            metrics == null ? 0f : metrics.rowHeightPx, clickGate, pickupDelegate);
    }

    public void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                     @Nullable AppDrawerHorizontalGridMetrics metrics,
                     @NonNull ClickGate clickGate) {
        bindInternal(dock, entry, metrics == null ? 0f : metrics.iconPx,
            metrics == null ? 0f : metrics.rowHeightPx, clickGate);
    }

    void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
              int iconPx, int rowHeightPx, @NonNull ClickGate clickGate) {
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate, null, null);
    }

    void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
              int iconPx, int rowHeightPx, @NonNull ClickGate clickGate,
              @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate, pickupDelegate, null);
    }

    /** Used by the category detail grid: swaps the popup's Pin row for a Category row instead. */
    void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
              int iconPx, int rowHeightPx, @NonNull ClickGate clickGate,
              @Nullable AppDrawerCategoryChoiceListener categoryChoiceListener) {
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate, null, categoryChoiceListener);
    }

    private void bindInternal(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                              float iconSize, float rowHeight,
                              @NonNull ClickGate clickGate) {
        bindInternal(dock, entry, iconSize, rowHeight, clickGate, null, null);
    }

    private void bindInternal(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                              float iconSize, float rowHeight, @NonNull ClickGate clickGate,
                              @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindInternal(dock, entry, iconSize, rowHeight, clickGate, pickupDelegate, null);
    }

    private void bindInternal(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                              float iconSize, float rowHeight, @NonNull ClickGate clickGate,
                              @Nullable AppDrawerPickupDelegate pickupDelegate,
                              @Nullable AppDrawerCategoryChoiceListener categoryChoiceListener) {
        int iconPx = iconSize > 0f ? Math.max(1, Math.round(iconSize)) : 0;
        applyGeometry(rowHeight, iconPx);
        if (dock != null && iconPx > 0) {
            dock.bindRenderedIcon(icon, entry, iconPx);
        } else {
            AsyncIconBinder.cancel(icon);
            icon.setImageDrawable(entry.icon);
        }
        if (dock != null) dock.applyIconColorFilter(icon);
        icon.setContentDescription(entry.label);

        label.setText(entry.label);
        label.setTextColor(dock != null ? dock.getLauncherTextColor() : Color.WHITE);
        setContentDescription(entry.label);
        setOnClickListener(view -> {
            if (!clickGate.suppressCellClick() && dock != null)
                dock.launchEntryFromDrawer(icon, entry);
        });
        if (dock != null) {
            Runnable categoryAction = categoryChoiceListener == null ? null
                : () -> categoryChoiceListener.onCategoryChoiceRequested(entry, this);
            dock.bindDrawerAppContextLongPress(this, entry, pickupDelegate, categoryAction);
        }
    }

    protected void applyGeometry(float rowHeightPx, int iconPx) {
        if (rowHeightPx <= 0f) return;
        ViewGroup.LayoutParams cellParams = getLayoutParams();
        int rowHeight = Math.max(1, Math.round(rowHeightPx));
        if (cellParams != null && cellParams.height != rowHeight) {
            cellParams.height = rowHeight;
            setLayoutParams(cellParams);
        }
        ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
        if (iconParams != null && (iconParams.width != iconPx || iconParams.height != iconPx)) {
            iconParams.width = iconPx;
            iconParams.height = iconPx;
            icon.setLayoutParams(iconParams);
        }
        int gap = Math.round(AppDrawerGridMetrics.LABEL_GAP_DP
            * getResources().getDisplayMetrics().density);
        if (label.getPaddingTop() != gap) label.setPadding(0, gap, 0, 0);
    }

    /**
     * The icon sits above the label; nothing in this cell draws over anything else. Saying so lets
     * the scrub dim ({@code setAlpha(0.28f)} on 24-36 attached cells per frame) propagate alpha to
     * the children directly instead of allocating an offscreen layer per dimmed cell per frame.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setScrubAppearance(char letter, char activeLetter, float strength) {
        setAlpha(AppDrawerScrubHighlight.alphaFor(letter, activeLetter, strength));
        float scale = AppDrawerScrubHighlight.scaleFor(letter, activeLetter, strength);
        setScaleX(scale);
        setScaleY(scale);
    }

    /**
     * The result a physical keyboard's arrow keys are on: a ring and wash, reported to screen
     * readers as selected. Drawn by the background, set when the highlight moves — never per frame.
     */
    public final void setKeyboardHighlight(boolean highlighted) {
        if (mKeyboardHighlighted == highlighted && !highlighted) return;
        mKeyboardHighlighted = highlighted;
        setSelected(highlighted);
        refreshBackground();
    }

    public final boolean isKeyboardHighlighted() {
        return mKeyboardHighlighted;
    }

    /** The grid cell has no background at rest; a presentation with one overrides this. */
    protected void refreshBackground() {
        setBackground(mKeyboardHighlighted ? highlightDrawable(getResources()
            .getDisplayMetrics().density) : null);
    }

    /** Ring over a faint wash, both resolved against the chrome so light, dark and black all read. */
    @NonNull
    static Drawable highlightDrawable(float density) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(HIGHLIGHT_RADIUS_DP * density);
        ring.setColor(ChromeShade.stateFill(HIGHLIGHT_WASH_SEED, HIGHLIGHT_WASH_REST));
        ring.setStroke(Math.round(HIGHLIGHT_STROKE_DP * density),
            ChromeShade.rim(HIGHLIGHT_RING_SEED));
        return ring;
    }

    /** Releases rendered artwork, app-specific listeners and transient scrub state. */
    public void unbind() {
        if (mKeyboardHighlighted) setKeyboardHighlight(false);
        cancelLongPress();
        setOnClickListener(null);
        setOnLongClickListener(null);
        setOnTouchListener(null);
        setLongClickable(false);
        // A render still on its way for the app this cell showed must not land on its next one.
        AsyncIconBinder.cancel(icon);
        icon.setImageDrawable(null);
        icon.setContentDescription(null);
        label.setText(null);
        setContentDescription(null);
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
    }
}
