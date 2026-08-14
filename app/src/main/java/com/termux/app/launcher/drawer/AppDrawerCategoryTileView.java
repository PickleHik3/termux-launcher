package com.termux.app.launcher.drawer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

/**
 * Seven-icon category preview drawn as one rounded rect with the heading INSIDE at the top —
 * the tile reads as a folder card (label band above a square icon area, slightly taller than
 * wide), not as a picture with an external caption.
 *
 * <p>Redesign: the whole card is one open-category target. The three large icons and the 2x2
 * mini-cluster are display only — launching individual apps is the expanded grid's job — so a tap
 * anywhere on the card expands it, with a light press dip (0.98 scale, lifted wash) as feedback.
 */
public final class AppDrawerCategoryTileView extends ViewGroup {
    public static final float HEADING_TEXT_SP = 13f;
    /** Card washes from the mock: white over the dark glass, wash-only like the search pill. */
    private static final int FILL_COLOR = 0x0EFFFFFF;
    private static final int FILL_PRESSED_COLOR = 0x1CFFFFFF;
    private static final int STROKE_COLOR = 0x21FFFFFF;
    private static final float PRESSED_SCALE = 0.98f;
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
    /** Label band inside the tile's top: the icon square starts below it. */
    private float headingBand;

    public AppDrawerCategoryTileView(@NonNull android.content.Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(FILL_COLOR);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        strokePaint.setColor(STROKE_COLOR);
        for (int i = 0; i < icons.length; i++) {
            ImageView icon = new ImageView(context);
            // The cached drawable is rendered at this view's exact pixel size. CENTER therefore
            // performs no second scaling pass (CENTER_INSIDE made the already-small bitmap look
            // like a large preview squeezed into a mini view on high-density devices).
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icons[i] = icon;
            addView(icon);
        }
        // The whole-card open target: a transparent sibling laid over the entire drawn tile, above
        // every display-only icon, that reports its pressed state back for the card's press dip.
        expandTarget = new PressTargetView(context, this::applyPressedAppearance);
        expandTarget.setBackgroundColor(Color.TRANSPARENT);
        expandTarget.setClickable(true);
        addView(expandTarget);
        heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADING_TEXT_SP);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setLetterSpacing(-0.005f);
        heading.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        heading.setIncludeFontPadding(false);
        heading.setClickable(true);
        addView(heading);
    }

    /** The mock's press feedback: the card dips to 0.98 and its wash lifts while held. */
    private void applyPressedAppearance(boolean pressed) {
        setScaleX(pressed ? PRESSED_SCALE : 1f);
        setScaleY(pressed ? PRESSED_SCALE : 1f);
        fillPaint.setColor(pressed ? FILL_PRESSED_COLOR : FILL_COLOR);
        invalidate();
    }

    /** A plain transparent view that reports pressed-state flips to the card. */
    private static final class PressTargetView extends View {
        interface PressedListener { void onPressedChanged(boolean pressed); }
        @NonNull private final PressedListener listener;
        PressTargetView(@NonNull android.content.Context context,
                        @NonNull PressedListener listener) {
            super(context);
            this.listener = listener;
        }
        @Override protected void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
            listener.onPressedChanged(pressed);
        }
        @Override public void setPressed(boolean pressed) {
            boolean changed = pressed != isPressed();
            super.setPressed(pressed);
            if (changed) listener.onPressedChanged(pressed);
        }
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
        // 90% of the launcher text colour, per the mock's card titles.
        int headingColor = dock == null ? Color.WHITE : dock.getLauncherTextColor();
        heading.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
            headingColor, 0xE6));
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
            // Display-only, all seven: the whole card is one open-category target and individual
            // launches live in the expanded grid. The full-card target above them owns the tap.
            icon.setClickable(false);
            icon.setLongClickable(false);
            icon.setContentDescription(null);
        }
        requestLayout();
    }

    /** Releases every drawable and app-specific listener while retaining the cheap holder tree. */
    public void releaseDrawables() {
        for (ImageView icon : icons) clearIcon(icon);
    }

    public void unbind() {
        releaseDrawables();
        applyPressedAppearance(false);
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
        // The old external caption's vertical budget moves inside the tile as its label band, so
        // the grid item height is unchanged while the tile itself grows taller than wide.
        headingBand = resolved == null ? 0f : resolved.headingGapPx + resolved.headingHeightPx;
        int headingHeight = resolved == null ? 0 : Math.max(0, Math.round(resolved.headingHeightPx));
        float inner = resolved == null ? 0f : resolved.innerPaddingPx;
        int headingWidth = Math.max(0, Math.round(tileSide - 2f * inner));
        heading.measure(MeasureSpec.makeMeasureSpec(headingWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(headingHeight, MeasureSpec.EXACTLY));
        int large = resolved == null ? 1 : resolved.largeIconPx;
        int small = resolved == null ? 1 : resolved.smallIconPx;
        for (int i = 0; i < icons.length; i++) {
            int size = i < 3 ? large : small;
            icons[i].measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
        }
        // The open target covers the whole drawn card, not just the fourth slot.
        expandTarget.measure(
            MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(tileSide)), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(headingBand + tileSide)),
                MeasureSpec.EXACTLY));
        int desired = Math.round(headingBand + tileSide
            + (resolved == null ? 0f : resolved.itemBottomGapPx));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desired, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        AppDrawerCategoryGridMetrics resolved = metrics;
        if (resolved == null) return;
        float inner = resolved.innerPaddingPx;
        float slot = Math.max(0f, (tileSide - 2f * inner - resolved.slotGapPx) / 2f);
        float left = tileLeft + inner;
        float right = left + slot + resolved.slotGapPx;
        // Label band first, icon square below it — both inside the one drawn rect.
        int headingLeft = Math.round(tileLeft + inner);
        int headingTop = Math.round(inner * 0.75f);
        heading.layout(headingLeft, headingTop, headingLeft + heading.getMeasuredWidth(),
            headingTop + heading.getMeasuredHeight());
        float top = headingBand + inner;
        float bottom = top + slot + resolved.slotGapPx;
        layoutCentered(icons[0], left, top, slot);
        layoutCentered(icons[1], right, top, slot);
        layoutCentered(icons[2], left, bottom, slot);
        RectF parentSlot = new RectF(right, bottom, right + slot, bottom + slot);
        // One large icon plus the block's own hairline gap, so each of the four cells comes out at
        // exactly smallIconPx and no icon has to overflow the cell it is centred in.
        RectF[] smallCells = smallCellRects(
            smallBlockBounds(parentSlot, resolved.largeIconPx + resolved.smallBlockGapPx),
            resolved.smallBlockGapPx);
        for (int i = 0; i < smallCells.length; i++) {
            RectF cell = smallCells[i];
            layoutCentered(icons[3 + i], cell.left, cell.top, cell.width());
        }
        // Whole-card target: exactly the drawn rounded rect.
        expandTarget.layout(Math.round(tileLeft), 0,
            Math.round(tileLeft) + expandTarget.getMeasuredWidth(),
            expandTarget.getMeasuredHeight());
    }

    private static void layoutCentered(@NonNull View view, float left, float top, float size) {
        int x = Math.round(left + (size - view.getMeasuredWidth()) / 2f);
        int y = Math.round(top + (size - view.getMeasuredHeight()) / 2f);
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    /**
     * The square the 2x2 block occupies inside its slot: one large icon's worth, centred.
     *
     * <p>Spreading the four small icons over the whole slot pushed the outer two against the tile's
     * inner edges, so the block read as sitting closer to the border than the three large icons
     * beside it. Clumping it to a large icon's footprint gives the tile even inner padding on every
     * side and makes the block weigh the same as its neighbours.
     */
    @NonNull
    static RectF smallBlockBounds(@NonNull RectF parentSlot, float blockSizePx) {
        float size = Math.max(0f, Math.min(blockSizePx,
            Math.min(parentSlot.width(), parentSlot.height())));
        float insetX = (parentSlot.width() - size) / 2f;
        float insetY = (parentSlot.height() - size) / 2f;
        return new RectF(parentSlot.left + insetX, parentSlot.top + insetY,
            parentSlot.right - insetX, parentSlot.bottom - insetY);
    }

    /** Exact centred 2x2 geometry inside the one large-slot rectangle. */
    @NonNull
    static RectF[] smallCellRects(@NonNull RectF parentSlot, float requestedGapPx) {
        float gap = Math.max(0f, Math.min(requestedGapPx,
            Math.min(parentSlot.width(), parentSlot.height())));
        float cellWidth = Math.max(0f, (parentSlot.width() - gap) / 2f);
        float cellHeight = Math.max(0f, (parentSlot.height() - gap) / 2f);
        float secondLeft = parentSlot.left + cellWidth + gap;
        float secondTop = parentSlot.top + cellHeight + gap;
        return new RectF[] {
            new RectF(parentSlot.left, parentSlot.top,
                parentSlot.left + cellWidth, parentSlot.top + cellHeight),
            new RectF(secondLeft, parentSlot.top, parentSlot.right,
                parentSlot.top + cellHeight),
            new RectF(parentSlot.left, secondTop, parentSlot.left + cellWidth,
                parentSlot.bottom),
            new RectF(secondLeft, secondTop, parentSlot.right, parentSlot.bottom)
        };
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        AppDrawerCategoryGridMetrics resolved = metrics;
        float radius = resolved == null ? 0f : Math.min(resolved.radiusPx, tileSide / 2f);
        float bottom = tileHeight();
        canvas.drawRoundRect(tileLeft, 0f, tileLeft + tileSide, bottom, radius, radius, fillPaint);
        canvas.drawRoundRect(tileLeft, 0f, tileLeft + tileSide, bottom, radius, radius, strokePaint);
    }

    public float tileLeft() { return tileLeft; }
    public float tileTop() { return 0f; }
    public float tileSide() { return tileSide; }
    /** Drawn tile height: the label band plus the icon square. */
    public float tileHeight() { return headingBand + tileSide; }
    @Nullable public AppDrawerCategoryBucket bucket() { return bucket; }
}
