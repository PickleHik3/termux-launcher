package com.termux.app.launcher.drawer;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import com.termux.app.launcher.model.LauncherAppEntry;

/** The single icon-and-label implementation shared by both drawer presentations. */
public class AppDrawerAppCellView extends LinearLayout {

    /** Stream-level guard used because a nested close deliberately does not cancel its child. */
    public interface ClickGate {
        boolean suppressCellClick();
    }

    public static final ClickGate ALLOW_CLICKS = () -> false;

    @NonNull public final ImageView icon;
    @NonNull public final TextView label;

    public AppDrawerAppCellView(@NonNull android.content.Context context) {
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
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate);
    }

    void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
              int iconPx, int rowHeightPx, @NonNull ClickGate clickGate,
              @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate, pickupDelegate);
    }

    private void bindInternal(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                              float iconSize, float rowHeight,
                              @NonNull ClickGate clickGate) {
        bindInternal(dock, entry, iconSize, rowHeight, clickGate, null);
    }

    private void bindInternal(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
                              float iconSize, float rowHeight, @NonNull ClickGate clickGate,
                              @Nullable AppDrawerPickupDelegate pickupDelegate) {
        int iconPx = iconSize > 0f ? Math.max(1, Math.round(iconSize)) : 0;
        applyGeometry(rowHeight, iconPx);
        Drawable artwork = dock != null && iconPx > 0 ? dock.getRenderedIcon(entry, iconPx) : null;
        icon.setImageDrawable(artwork != null ? artwork : entry.icon);
        if (dock != null) dock.applyIconColorFilter(icon);
        icon.setContentDescription(entry.label);

        label.setText(entry.label);
        label.setTextColor(dock != null ? dock.getLauncherTextColor() : Color.WHITE);
        setContentDescription(entry.label);
        setOnClickListener(view -> {
            if (!clickGate.suppressCellClick() && dock != null)
                dock.launchEntryFromDrawer(icon, entry);
        });
        if (dock != null) dock.bindDrawerAppContextLongPress(this, entry, pickupDelegate);
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
        label.setPadding(0, gap, 0, 0);
    }

    public void setScrubAppearance(char letter, char activeLetter, float strength) {
        setAlpha(AppDrawerScrubHighlight.alphaFor(letter, activeLetter, strength));
        float scale = AppDrawerScrubHighlight.scaleFor(letter, activeLetter, strength);
        setScaleX(scale);
        setScaleY(scale);
    }

    /** Releases rendered artwork, app-specific listeners and transient scrub state. */
    public void unbind() {
        cancelLongPress();
        setOnClickListener(null);
        setOnLongClickListener(null);
        setOnTouchListener(null);
        setLongClickable(false);
        icon.setImageDrawable(null);
        icon.setContentDescription(null);
        label.setText(null);
        setContentDescription(null);
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
    }
}
