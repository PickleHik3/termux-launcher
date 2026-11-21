package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
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
public class AppDrawerAppCellView extends LinearLayout
    implements com.termux.app.launcher.animation.LaunchIconSource {

    /** Stream-level guard used because a nested close deliberately does not cancel its child. */
    public interface ClickGate {
        boolean suppressCellClick();
    }

    public static final ClickGate ALLOW_CLICKS = () -> false;

    @NonNull public final ImageView icon;
    @NonNull public final TextView label;
    private boolean mKeyboardHighlighted;
    /** The glass bead behind the highlighted result; one per cell, made on first highlight. */
    @Nullable private AppDrawerGlassBeadDrawable mBead;
    private final int[] mContentBox = new int[4];
    private final int[] mBeadBox = new int[4];

    /** The package this cell shows, for the home handoff; null while unbound. */
    @Nullable private String launchPackage;

    @Override public boolean launchesPackage(@NonNull String packageName) {
        return packageName.equals(launchPackage);
    }
    @Override public boolean isDrawerCell() { return true; }
    @Override @Nullable public View launchIconView() { return icon; }

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

    /** Category detail grid: the popup's category row plus the drawer's pickup, as in grid and list. */
    void bind(@Nullable SuggestionBarView dock, @NonNull LauncherAppEntry entry,
              int iconPx, int rowHeightPx, @NonNull ClickGate clickGate,
              @Nullable AppDrawerPickupDelegate pickupDelegate,
              @Nullable AppDrawerCategoryChoiceListener categoryChoiceListener) {
        bindInternal(dock, entry, iconPx, rowHeightPx, clickGate, pickupDelegate,
            categoryChoiceListener);
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
        launchPackage = entry.appRef.packageName;
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
        float density = getResources().getDisplayMetrics().density;
        int top = Math.round(AppDrawerGridMetrics.ROW_TOP_DP * density);
        if (getPaddingTop() != top) {
            setPadding(getPaddingLeft(), top, getPaddingRight(), getPaddingBottom());
        }
        int gap = Math.round(AppDrawerGridMetrics.LABEL_GAP_DP * density);
        // Air inside the cell on both sides: neighbouring labels never touch, and the outer
        // columns' labels stand off the card's edge.
        int side = Math.round(AppDrawerGridMetrics.labelSideInsetPx(density));
        if (label.getPaddingTop() != gap || label.getPaddingLeft() != side
            || label.getPaddingRight() != side) {
            label.setPadding(side, gap, side, 0);
        }
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
     * The result a physical keyboard's arrow keys are on: a glass bead hugging the icon and name,
     * reported to screen readers as selected. Set when the highlight moves, placed in
     * {@link #onLayout} — never per frame.
     */
    public final void setKeyboardHighlight(boolean highlighted) {
        if (mKeyboardHighlighted == highlighted && !highlighted) return;
        mKeyboardHighlighted = highlighted;
        setSelected(highlighted);
        refreshBackground();
        if (highlighted) placeBead();
    }

    public final boolean isKeyboardHighlighted() {
        return mKeyboardHighlighted;
    }

    /** The grid cell has no background at rest; a presentation with one overrides this. */
    protected void refreshBackground() {
        setBackground(mKeyboardHighlighted ? beadDrawable() : null);
    }

    /** The bead, made on first use with its colours read from the chrome as it stands. */
    @NonNull
    protected final AppDrawerGlassBeadDrawable beadDrawable() {
        if (mBead == null) {
            mBead = new AppDrawerGlassBeadDrawable(getResources().getDisplayMetrics().density);
        } else {
            mBead.resolveColors();
        }
        return mBead;
    }

    /** A stacked cell centres its bead on the icon; a row presentation hugs its content instead. */
    protected boolean centersHighlightOnIcon() {
        return true;
    }

    /** Grows {@code box} to take in whatever else this presentation shows beside icon and label. */
    protected void unionExtraContent(@NonNull int[] box) {}

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mKeyboardHighlighted) placeBead();
    }

    private void placeBead() {
        AppDrawerGlassBeadDrawable bead = mBead;
        int width = getWidth();
        int height = getHeight();
        if (bead == null || width <= 0 || height <= 0 || icon.getWidth() <= 0) return;
        int iconLeft = offsetIn(icon, true);
        int iconTop = offsetIn(icon, false);
        int[] box = mContentBox;
        box[0] = iconLeft;
        box[1] = iconTop;
        box[2] = iconLeft + icon.getWidth();
        box[3] = iconTop + icon.getHeight();
        unionTextBounds(label, box);
        unionExtraContent(box);
        float density = getResources().getDisplayMetrics().density;
        int padding = Math.round(AppDrawerHighlightShape.PADDING_DP * density);
        int spare = Math.max(1, Math.round(AppDrawerHighlightShape.SPARE_DP * density));
        AppDrawerHighlightShape.resolve(width, height, box, iconLeft + icon.getWidth() / 2,
            centersHighlightOnIcon(),
            padding, spare, mBeadBox);
        int[] out = mBeadBox;
        float radius = AppDrawerHighlightShape.radiusPx(icon.getWidth(), padding,
            out[2] - out[0], out[3] - out[1]);
        bead.setInner(out[0], out[1], out[2], out[3], radius);
    }

    /** Grows {@code box} over {@code view}'s text: the measured line, not the view's full width. */
    protected final void unionTextBounds(@NonNull TextView view, @NonNull int[] box) {
        if (view.getVisibility() == GONE || view.getWidth() <= 0
            || TextUtils.isEmpty(view.getText())) return;
        int left = offsetIn(view, true);
        int top = offsetIn(view, false);
        int areaLeft = left + view.getCompoundPaddingLeft();
        int areaRight = left + view.getWidth() - view.getCompoundPaddingRight();
        int textLeft = areaLeft;
        int textRight = areaRight;
        android.text.Layout layout = view.getLayout();
        if (layout != null && layout.getLineCount() > 0) {
            // Width of the line as shown (ellipsis included), placed by the view's own gravity.
            int lineWidth = Math.min(areaRight - areaLeft, (int) Math.ceil(layout.getLineWidth(0)));
            int gravity = Gravity.getAbsoluteGravity(view.getGravity(), view.getLayoutDirection())
                & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (gravity == Gravity.RIGHT) {
                textLeft = areaRight - lineWidth;
            } else if (gravity == Gravity.LEFT) {
                textRight = areaLeft + lineWidth;
            } else {
                textLeft = areaLeft + (areaRight - areaLeft - lineWidth) / 2;
                textRight = textLeft + lineWidth;
            }
        }
        int textTop = top + view.getCompoundPaddingTop();
        int textBottom = top + view.getHeight() - view.getCompoundPaddingBottom();
        if (textRight <= textLeft || textBottom <= textTop) return;
        box[0] = Math.min(box[0], textLeft);
        box[1] = Math.min(box[1], textTop);
        box[2] = Math.max(box[2], textRight);
        box[3] = Math.max(box[3], textBottom);
    }

    /** {@code view}'s left or top in this cell's pixels, through any wrapper between. */
    private int offsetIn(@NonNull View view, boolean horizontal) {
        int offset = 0;
        View v = view;
        while (v != this && v != null) {
            offset += horizontal ? v.getLeft() : v.getTop();
            android.view.ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return offset;
    }

    /** Releases rendered artwork, app-specific listeners and transient scrub state. */
    public void unbind() {
        launchPackage = null;
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
