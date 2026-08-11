package com.termux.app.launcher.drawer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.List;

/** Seven-icon category preview with one exact rounded-square draw rect and an external heading. */
public final class AppDrawerCategoryTileView extends ViewGroup {
    public static final float HEADING_TEXT_SP = 13f;
    public interface ExpansionListener {
        void onExpandRequested(@NonNull AppDrawerCategoryBucket bucket,
                               @NonNull AppDrawerCategoryTileView source);
    }

    @NonNull public final ImageView[] icons = new ImageView[AppDrawerCategoryBucket.PREVIEW_COUNT];
    @NonNull public final View expandTarget;
    @NonNull public final TextView heading;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private AppDrawerCategoryGridMetrics metrics;
    @Nullable private AppDrawerCategoryBucket bucket;
    @Nullable private SuggestionBarView dock;
    @Nullable private ExpansionListener expansionListener;
    @NonNull private AppDrawerAppCellView.ClickGate clickGate = AppDrawerAppCellView.ALLOW_CLICKS;
    private float tileLeft;
    private float tileSide;

    public AppDrawerCategoryTileView(@NonNull android.content.Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0x1FFFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        strokePaint.setColor(0x38FFFFFF);
        for (int i = 0; i < icons.length; i++) {
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setAdjustViewBounds(true);
            icons[i] = icon;
            addView(icon);
        }
        expandTarget = new View(context);
        expandTarget.setBackgroundColor(Color.TRANSPARENT);
        expandTarget.setClickable(true);
        addView(expandTarget);
        heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADING_TEXT_SP);
        heading.setSingleLine(true);
        heading.setMaxLines(1);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setGravity(android.view.Gravity.CENTER);
        heading.setIncludeFontPadding(false);
        heading.setClickable(true);
        addView(heading);
    }

    public void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
        this.metrics = metrics;
        requestLayout();
    }

    public void bind(@Nullable SuggestionBarView dock, @NonNull AppDrawerCategoryBucket bucket,
        @NonNull AppDrawerCategoryGridMetrics metrics, @NonNull ExpansionListener listener,
        @NonNull AppDrawerAppCellView.ClickGate clickGate) {
        unbind();
        this.dock = dock;
        this.bucket = bucket;
        this.metrics = metrics;
        this.expansionListener = listener;
        this.clickGate = clickGate;
        String label = getResources().getString(bucket.category.labelRes);
        heading.setText(label);
        heading.setClickable(true);
        heading.setTextColor(dock == null ? Color.WHITE : dock.getLauncherTextColor());
        String open = getResources().getString(R.string.app_drawer_category_open, label);
        heading.setContentDescription(open);
        expandTarget.setContentDescription(open);
        expandTarget.setClickable(true);
        View.OnClickListener expand = view -> {
            AppDrawerCategoryBucket current = this.bucket;
            ExpansionListener currentListener = expansionListener;
            if (!this.clickGate.suppressCellClick() && current != null && currentListener != null)
                currentListener.onExpandRequested(current, this);
        };
        heading.setOnClickListener(expand);
        expandTarget.setOnClickListener(expand);
        List<LauncherAppEntry> previews = bucket.previews();
        for (int i = 0; i < icons.length; i++) {
            ImageView icon = icons[i];
            if (i >= previews.size()) {
                clearIcon(icon);
                continue;
            }
            LauncherAppEntry entry = previews.get(i);
            int iconPx = i < 3 ? metrics.largeIconPx : metrics.smallIconPx;
            Drawable artwork = dock == null ? entry.icon : dock.getRenderedIcon(entry, iconPx);
            icon.setImageDrawable(artwork != null ? artwork : entry.icon);
            if (dock != null) dock.applyIconColorFilter(icon);
            icon.setVisibility(VISIBLE);
            if (i < 3) {
                icon.setClickable(true);
                icon.setContentDescription(entry.label);
                icon.setOnClickListener(view -> {
                    if (!this.clickGate.suppressCellClick() && this.dock != null)
                        this.dock.launchEntryFromDrawer(view, entry);
                });
                if (dock != null) dock.bindDrawerAppContextLongPress(icon, entry);
            } else {
                // Display-only: the one accessible target layered above all four owns the block.
                icon.setClickable(false);
                icon.setLongClickable(false);
                icon.setContentDescription(null);
            }
        }
        requestLayout();
    }

    /** Releases every drawable and app-specific listener while retaining the cheap holder tree. */
    public void releaseDrawables() {
        for (ImageView icon : icons) clearIcon(icon);
    }

    public void unbind() {
        releaseDrawables();
        heading.setText(null);
        heading.setContentDescription(null);
        heading.setOnClickListener(null);
        heading.setClickable(false);
        expandTarget.setContentDescription(null);
        expandTarget.setOnClickListener(null);
        expandTarget.setClickable(false);
        bucket = null;
        dock = null;
        expansionListener = null;
        clickGate = AppDrawerAppCellView.ALLOW_CLICKS;
    }

    private static void clearIcon(@NonNull ImageView icon) {
        icon.cancelLongPress();
        icon.setImageDrawable(null);
        icon.setOnClickListener(null);
        icon.setOnLongClickListener(null);
        icon.setOnTouchListener(null);
        icon.setClickable(false);
        icon.setLongClickable(false);
        icon.setContentDescription(null);
        icon.setVisibility(INVISIBLE);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        AppDrawerCategoryGridMetrics resolved = metrics;
        int width = Math.max(0, MeasureSpec.getSize(widthMeasureSpec));
        float inset = resolved == null ? 0f : resolved.tileHorizontalInsetPx;
        tileLeft = inset;
        tileSide = Math.max(0f, width - 2f * inset);
        int headingHeight = resolved == null ? 0 : Math.max(0, Math.round(resolved.headingHeightPx));
        heading.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(headingHeight, MeasureSpec.EXACTLY));
        int large = resolved == null ? 1 : resolved.largeIconPx;
        int small = resolved == null ? 1 : resolved.smallIconPx;
        for (int i = 0; i < icons.length; i++) {
            int size = i < 3 ? large : small;
            icons[i].measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
        }
        int block = resolved == null ? 1 : Math.max(1, Math.round(resolved.largeSlotPx));
        expandTarget.measure(MeasureSpec.makeMeasureSpec(block, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(block, MeasureSpec.EXACTLY));
        int desired = Math.round(tileSide + (resolved == null ? 0f
            : resolved.headingGapPx + resolved.headingHeightPx + resolved.itemBottomGapPx));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desired, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        AppDrawerCategoryGridMetrics resolved = metrics;
        if (resolved == null) return;
        float inner = resolved.innerPaddingPx;
        float slot = Math.max(0f, (tileSide - 2f * inner - resolved.slotGapPx) / 2f);
        float left = tileLeft + inner;
        float right = left + slot + resolved.slotGapPx;
        float top = inner;
        float bottom = top + slot + resolved.slotGapPx;
        layoutCentered(icons[0], left, top, slot);
        layoutCentered(icons[1], right, top, slot);
        layoutCentered(icons[2], left, bottom, slot);
        float cell = slot / 2f;
        for (int i = 0; i < 4; i++) {
            float cellLeft = right + (i % 2) * cell;
            float cellTop = bottom + (i / 2) * cell;
            layoutCentered(icons[3 + i], cellLeft, cellTop, cell);
        }
        int blockLeft = Math.round(right);
        int blockTop = Math.round(bottom);
        expandTarget.layout(blockLeft, blockTop, blockLeft + Math.round(slot),
            blockTop + Math.round(slot));
        int headingTop = Math.round(tileSide + resolved.headingGapPx);
        heading.layout(0, headingTop, getMeasuredWidth(), headingTop + heading.getMeasuredHeight());
    }

    private static void layoutCentered(@NonNull View view, float left, float top, float size) {
        int x = Math.round(left + (size - view.getMeasuredWidth()) / 2f);
        int y = Math.round(top + (size - view.getMeasuredHeight()) / 2f);
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        AppDrawerCategoryGridMetrics resolved = metrics;
        float radius = resolved == null ? 0f : Math.min(resolved.radiusPx, tileSide / 2f);
        canvas.drawRoundRect(tileLeft, 0f, tileLeft + tileSide, tileSide, radius, radius, fillPaint);
        canvas.drawRoundRect(tileLeft, 0f, tileLeft + tileSide, tileSide, radius, radius, strokePaint);
    }

    public float tileLeft() { return tileLeft; }
    public float tileTop() { return 0f; }
    public float tileSide() { return tileSide; }
    @Nullable public AppDrawerCategoryBucket bucket() { return bucket; }
}
