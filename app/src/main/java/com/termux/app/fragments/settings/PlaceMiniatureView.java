package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.EnumMap;
import java.util.Map;

/**
 * A phone-shaped miniature of one place's resolved {@link PlaceLayout}: a faithful, small
 * rendering of the launcher's own chrome — status bar, apps row, alphabets row, extra keys and the
 * place-specific canvas underneath them — every band tinted with the app's own theme attrs so it
 * reads correctly in every theme. Every interactive band carries a small glyph+word pill so a user
 * never has to decode it from shape or colour alone; the pill drops its word and keeps the glyph
 * when the band is too short to hold both. Tapping a block reports which one, so the settings page
 * can scroll to its row.
 */
public final class PlaceMiniatureView extends View {

    /** One region of the miniature. */
    public enum Block { STATUS_BAR, APPS_ROW, ALPHABETS_ROW, EXTRA_KEYS, CANVAS }

    /** What the canvas band draws, driven by which place is selected. */
    public enum CanvasKind { TERMINAL, HOME_GRID, DISPLAY }

    /** Reports a tapped block; {@code null} when the tap landed outside every block (rare). */
    public interface OnBlockTappedListener {
        void onBlockTapped(@NonNull Block block);
    }

    private static final float STATUS_BAR_FRACTION = 0.12f;
    private static final float ROW_FRACTION = 0.17f;
    private static final float ALPHABETS_FRACTION = 0.09f;
    /** Portrait: narrow and tall; landscape: wide and short — a phone silhouette either way. */
    private static final float PORTRAIT_ASPECT = 9f / 19.5f;
    private static final float LANDSCAPE_ASPECT = 19.5f / 9f;
    private static final float DEFAULT_HEIGHT_DP = 172f;

    private static final String[] ALPHABETS_SAMPLE = {"A", "F", "M", "S", "Z"};
    /**
     * Sampled from the launcher's own default extra-keys row
     * ({@code TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS}: the keyboard toggle, new
     * session, and the three wall pages) — a preview illustrates the shape of the row, not
     * whatever the user has actually edited it to.
     */
    private static final String[] EXTRA_KEY_GLYPHS =
        {"󰥻", "󰝜", "", "", ""};
    private static final int APPS_ROW_ICON_COUNT = 5;
    private static final int APPS_ROW_ACTIVE_INDEX = APPS_ROW_ICON_COUNT - 1;
    private static final int DISPLAY_KEY_GRID_COLUMNS = 6;
    private static final int DISPLAY_KEY_GRID_ROWS = 2;

    private static final float PILL_LABEL_SP = 8f;
    private static final float PILL_MAX_FONT_SCALE = 1.3f;
    /** A pill never claims more of its band's length than this, so it never touches the ends. */
    private static final float PILL_MAX_BAND_FRACTION = 0.86f;

    @Nullable private PlaceLayout mLayout;
    @NonNull private PlaceOrientation mOrientation = PlaceOrientation.PORTRAIT;
    @NonNull private PaneWallPage mPlace = PaneWallPage.TERMINAL;
    @Nullable private OnBlockTappedListener mListener;

    private final Paint mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNerdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFrameRect = new RectF();
    private final Path mClipPath = new Path();
    /** Reused scratch rects for whatever a draw call is computing right now; never read across
     *  two different shapes, only within one draw-then-move-on sequence. */
    private final RectF mScratchRectA = new RectF();
    private final RectF mScratchRectB = new RectF();
    private final Map<Block, RectF> mBlockRects = new EnumMap<>(Block.class);
    private final Map<Block, Boolean> mShowPillLabel = new EnumMap<>(Block.class);
    private RectF mRemaining = new RectF();
    private boolean mGridCollapsed;

    @Nullable private final Typeface mNerdTypeface;
    @NonNull private final Drawable mIconStatus;
    @NonNull private final Drawable mIconApps;
    @NonNull private final Drawable mIconKeys;
    @NonNull private final Drawable mIconHomeGrid;
    @NonNull private final Drawable mIconDisplay;

    public PlaceMiniatureView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setStrokeWidth(dp(2));
        mFramePaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        mFillPaint.setStyle(Paint.Style.FILL);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setStrokeWidth(dp(1f));
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{dp(2f), dp(2f)}, 0f));
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(dp(1.3f));
        mTextPaint.setStyle(Paint.Style.FILL);
        mNerdTypeface = NerdFontSpans.typeface(context);
        mNerdPaint.setStyle(Paint.Style.FILL);
        mNerdPaint.setTextAlign(Paint.Align.CENTER);
        if (mNerdTypeface != null) mNerdPaint.setTypeface(mNerdTypeface);

        mIconStatus = loadIcon(R.drawable.ic_symbol_notifications);
        mIconApps = loadIcon(R.drawable.ic_symbol_apps);
        mIconKeys = loadIcon(R.drawable.ic_symbol_keyboard);
        mIconHomeGrid = loadIcon(R.drawable.ic_symbol_grid_view);
        mIconDisplay = loadIcon(R.drawable.ic_symbol_desktop_windows);
    }

    public PlaceMiniatureView(@NonNull Context context) {
        this(context, null);
    }

    @NonNull
    private Drawable loadIcon(@DrawableRes int resId) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), resId);
        if (drawable == null) drawable = new android.graphics.drawable.ColorDrawable(0);
        return DrawableCompat.wrap(drawable).mutate();
    }

    /** What to draw, for the Terminal place. Kept for callers that never distinguish the place. */
    public void setLayout(@NonNull PlaceLayout layout, @NonNull PlaceOrientation orientation) {
        setLayout(layout, orientation, PaneWallPage.TERMINAL);
    }

    /**
     * What to draw: the resolved layout, the orientation, and which place's canvas to render.
     * Redraws only when one of the three actually changed.
     */
    public void setLayout(@NonNull PlaceLayout layout, @NonNull PlaceOrientation orientation,
                           @NonNull PaneWallPage place) {
        if (layout.equals(mLayout) && orientation == mOrientation && place == mPlace) return;
        mLayout = layout;
        mOrientation = orientation;
        mPlace = place;
        // The blocks are laid out from the frame, and the frame from the view's size; a new
        // arrangement, place or orientation at the same size never reaches onSizeChanged, so the
        // blocks are recomputed here or the old picture would be drawn again.
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    /** The rectangle a block is drawn in, in view pixels, or null while it is not on the picture. */
    @Nullable
    @VisibleForTesting
    public RectF blockRect(@NonNull Block block) {
        RectF rect = mBlockRects.get(block);
        return rect == null ? null : new RectF(rect);
    }

    public void setOnBlockTappedListener(@Nullable OnBlockTappedListener listener) {
        mListener = listener;
    }

    /** What the canvas band is currently drawing, driven by the selected place. */
    @VisibleForTesting
    @NonNull
    public CanvasKind canvasKind() {
        return canvasKindFor(mPlace);
    }

    @NonNull
    private static CanvasKind canvasKindFor(@NonNull PaneWallPage place) {
        switch (place) {
            case WIDGETS: return CanvasKind.HOME_GRID;
            case DISPLAY: return CanvasKind.DISPLAY;
            case TERMINAL:
            default: return CanvasKind.TERMINAL;
        }
    }

    /** Whether the Home canvas collapsed its widget grid to a single tinted rect because a cell
     *  would otherwise draw under ~4dp. Meaningless (always false) off the Home place. */
    @VisibleForTesting
    public boolean isWidgetGridCollapsed() {
        return mGridCollapsed;
    }

    /** Whether the given block's pill is currently showing its word, rather than glyph-only. */
    @VisibleForTesting
    public boolean isPillLabelShown(@NonNull Block block) {
        Boolean shown = mShowPillLabel.get(block);
        return shown != null && shown;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            height = Math.round(dp(DEFAULT_HEIGHT_DP));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutFrame(w, h);
    }

    private void layoutFrame(int viewWidth, int viewHeight) {
        float pad = dp(8);
        float availableWidth = Math.max(0f, viewWidth - 2 * pad);
        float availableHeight = Math.max(0f, viewHeight - 2 * pad);
        float aspect = mOrientation == PlaceOrientation.LANDSCAPE
            ? LANDSCAPE_ASPECT : PORTRAIT_ASPECT;
        float frameWidth = availableHeight * aspect;
        float frameHeight = availableHeight;
        if (frameWidth > availableWidth) {
            frameWidth = availableWidth;
            frameHeight = availableWidth / aspect;
        }
        float left = (viewWidth - frameWidth) / 2f;
        float top = (viewHeight - frameHeight) / 2f;
        mFrameRect.set(left, top, left + frameWidth, top + frameHeight);
        computeBlocks();
    }

    private void computeBlocks() {
        mBlockRects.clear();
        mRemaining = new RectF(mFrameRect);
        if (mLayout == null) {
            mShowPillLabel.clear();
            mGridCollapsed = false;
            return;
        }
        takeEdgeStrip(Block.STATUS_BAR, mLayout.statusBarEdge, STATUS_BAR_FRACTION);
        if (mLayout.extraKeys != RowPlacement.HIDDEN) {
            takeEdgeStrip(Block.EXTRA_KEYS, edgeOf(mLayout.extraKeys), ROW_FRACTION);
        }
        if (mLayout.appsRow != RowPlacement.HIDDEN) {
            takeEdgeStrip(Block.APPS_ROW, edgeOf(mLayout.appsRow), ROW_FRACTION);
        }
        if (mLayout.appsRow == RowPlacement.BOTTOM && mLayout.azRowShown) {
            takeEdgeStrip(Block.ALPHABETS_ROW, Edge.BOTTOM, ALPHABETS_FRACTION);
        }
        mBlockRects.put(Block.CANVAS, new RectF(mRemaining));
        computeContent();
    }

    /** Decisions that do not need a {@link Canvas} to make: the grid collapse and every pill's
     *  glyph-only-or-with-word call. Recomputed whenever the blocks move, so {@code onDraw} only
     *  ever reads them. */
    private void computeContent() {
        mShowPillLabel.clear();
        mGridCollapsed = false;
        if (mLayout == null) return;

        if (canvasKind() == CanvasKind.HOME_GRID) {
            RectF canvasRect = mBlockRects.get(Block.CANVAS);
            if (canvasRect != null) {
                int columns = Math.max(1, mLayout.widgetColumns);
                int rows = Math.max(1, mLayout.widgetRows);
                float cellWidth = canvasRect.width() / columns;
                float cellHeight = canvasRect.height() / rows;
                mGridCollapsed = Math.min(cellWidth, cellHeight) < dp(4);
            }
        }

        mShowPillLabel.put(Block.STATUS_BAR, fitsPill(Block.STATUS_BAR,
            isVerticalEdge(mLayout.statusBarEdge), pillLabel(Block.STATUS_BAR), true));
        mShowPillLabel.put(Block.APPS_ROW, fitsPill(Block.APPS_ROW,
            mLayout.appsRow.isOnSide(), pillLabel(Block.APPS_ROW), true));
        mShowPillLabel.put(Block.ALPHABETS_ROW, fitsPill(Block.ALPHABETS_ROW,
            false, pillLabel(Block.ALPHABETS_ROW), false));
        mShowPillLabel.put(Block.EXTRA_KEYS, fitsPill(Block.EXTRA_KEYS,
            mLayout.extraKeys.isOnSide(), pillLabel(Block.EXTRA_KEYS), true));
        String canvasLabel = canvasPillLabel();
        if (canvasLabel != null) {
            mShowPillLabel.put(Block.CANVAS, fitsPill(Block.CANVAS, false, canvasLabel, true));
        }
    }

    private boolean fitsPill(@NonNull Block block, boolean vertical, @Nullable String label,
                              boolean hasGlyph) {
        if (label == null) return false;
        RectF rect = mBlockRects.get(block);
        if (rect == null || rect.isEmpty()) return false;
        float bandLength = vertical ? rect.height() : rect.width();
        float bandThickness = vertical ? rect.width() : rect.height();
        mTextPaint.setTextSize(pillTextSizePx());
        float textWidth = mTextPaint.measureText(label);
        float glyphSpace = hasGlyph ? pillGlyphSizePx(bandThickness) + pillGapPx() : 0f;
        float needed = pillPaddingPx() * 2f + glyphSpace + textWidth;
        return needed <= bandLength * PILL_MAX_BAND_FRACTION;
    }

    @Nullable
    private String pillLabel(@NonNull Block block) {
        switch (block) {
            case STATUS_BAR: return getContext().getString(R.string.settings_layout_miniature_status);
            case APPS_ROW: return getContext().getString(R.string.settings_layout_miniature_apps);
            case ALPHABETS_ROW:
                return getContext().getString(R.string.settings_layout_miniature_alphabets);
            case EXTRA_KEYS: return getContext().getString(R.string.settings_layout_miniature_keys);
            default: return null;
        }
    }

    /** The Home/Display canvas's own chip label; {@code null} on Terminal, which needs none. */
    @Nullable
    private String canvasPillLabel() {
        if (mLayout == null) return null;
        switch (canvasKind()) {
            case HOME_GRID:
                return getContext().getString(R.string.settings_layout_miniature_grid_format,
                    mLayout.widgetColumns, mLayout.widgetRows);
            case DISPLAY:
                return getContext().getString(mLayout.keyboardMode == KeyboardMode.OVERLAY
                    ? R.string.settings_layout_keyboard_mode_overlay
                    : R.string.settings_layout_keyboard_mode_resize);
            case TERMINAL:
            default:
                return null;
        }
    }

    private float pillTextSizePx() {
        float fontScale = getResources().getConfiguration().fontScale;
        if (fontScale <= 0f) fontScale = 1f;
        float clamped = Math.min(fontScale, PILL_MAX_FONT_SCALE);
        return PILL_LABEL_SP * getResources().getDisplayMetrics().density * clamped;
    }

    private float pillGlyphSizePx(float bandThickness) {
        return Math.max(dp(6), Math.min(dp(11), bandThickness - dp(4)));
    }

    private float pillGapPx() {
        return dp(3);
    }

    private float pillPaddingPx() {
        return dp(5);
    }

    private static boolean isVerticalEdge(@NonNull Edge edge) {
        return edge == Edge.LEFT || edge == Edge.RIGHT;
    }

    @NonNull
    private static Edge edgeOf(@NonNull RowPlacement placement) {
        if (placement == RowPlacement.LEFT) return Edge.LEFT;
        if (placement == RowPlacement.RIGHT) return Edge.RIGHT;
        return Edge.BOTTOM;
    }

    /** Claims a strip off the current remaining rect for one edge, shrinking it in place. */
    private void takeEdgeStrip(@NonNull Block block, @NonNull Edge edge, float fraction) {
        RectF rect = new RectF(mRemaining);
        switch (edge) {
            case TOP: {
                float h = mRemaining.height() * fraction;
                rect.bottom = mRemaining.top + h;
                mRemaining.top += h;
                break;
            }
            case BOTTOM: {
                float h = mRemaining.height() * fraction;
                rect.top = mRemaining.bottom - h;
                mRemaining.bottom -= h;
                break;
            }
            case LEFT: {
                float w = mRemaining.width() * fraction;
                rect.right = mRemaining.left + w;
                mRemaining.left += w;
                break;
            }
            case RIGHT: {
                float w = mRemaining.width() * fraction;
                rect.left = mRemaining.right - w;
                mRemaining.right -= w;
                break;
            }
        }
        mBlockRects.put(block, rect);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null || mFrameRect.isEmpty()) return;
        float radius = dp(10);
        mFillPaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorSurfacePanel,
            R.color.termux_surface_panel));
        canvas.drawRoundRect(mFrameRect, radius, radius, mFillPaint);

        // The blocks are clipped to the frame's rounded outline, so a strip that reaches a corner
        // follows the curve instead of poking a square corner past it.
        mClipPath.reset();
        mClipPath.addRoundRect(mFrameRect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(mClipPath);
        drawCanvasBlock(canvas);
        drawExtraKeysBlock(canvas);
        drawAppsRowBlock(canvas);
        drawAlphabetsRowBlock(canvas);
        drawStatusBarBlock(canvas);
        canvas.restoreToCount(saved);

        mFramePaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        canvas.drawRoundRect(mFrameRect, radius, radius, mFramePaint);
    }

    // ---- Status bar --------------------------------------------------------------------------

    private void drawStatusBarBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.STATUS_BAR);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        int fill = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        int onFill = themeColor(com.termux.shared.R.attr.termuxColorOnPrimary, R.color.termux_on_primary);
        mFillPaint.setColor(fill);
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = isVerticalEdge(mLayout.statusBarEdge);
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        drawClockAndDots(canvas, mScratchRectA, onFill);
        endBandOrientation(canvas, saved);

        drawPill(canvas, Block.STATUS_BAR, rect, vertical, mIconStatus, onFill, fill);
    }

    private void drawClockAndDots(@NonNull Canvas canvas, @NonNull RectF local, int color) {
        mTextPaint.setColor(color);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        float textSize = Math.min(dp(8), Math.max(dp(5), local.height() * 0.55f));
        mTextPaint.setTextSize(textSize);
        canvas.drawText("12:40", local.left + dp(5), local.centerY() + textSize * 0.32f, mTextPaint);

        mFillPaint.setColor(color);
        float dotR = Math.min(dp(2f), local.height() * 0.18f);
        float spacing = dotR * 2.6f;
        float x = local.right - dp(5) - dotR;
        for (int i = 0; i < 3; i++) {
            canvas.drawCircle(x, local.centerY(), dotR, mFillPaint);
            x -= spacing;
        }
    }

    // ---- Apps row -----------------------------------------------------------------------------

    private void drawAppsRowBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.APPS_ROW);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        int fill = themeColor(com.termux.shared.R.attr.termuxColorSecondary, R.color.termux_secondary);
        int onFill = themeColor(com.termux.shared.R.attr.termuxColorOnSecondary, R.color.termux_on_secondary);
        int active = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        mFillPaint.setColor(fill);
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = mLayout.appsRow.isOnSide();
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        drawAppIcons(canvas, mScratchRectA, onFill, active);
        endBandOrientation(canvas, saved);

        drawPill(canvas, Block.APPS_ROW, rect, vertical, mIconApps, onFill, fill);
    }

    private void drawAppIcons(@NonNull Canvas canvas, @NonNull RectF local, int color, int activeColor) {
        float inset = dp(6);
        float slot = (local.width() - inset * 2f) / APPS_ROW_ICON_COUNT;
        float size = Math.min(slot * 0.62f, local.height() * 0.55f);
        float radius = size * 0.28f;
        float cy = local.centerY();
        for (int i = 0; i < APPS_ROW_ICON_COUNT; i++) {
            float cx = local.left + inset + slot * (i + 0.5f);
            mScratchRectB.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            mFillPaint.setColor(i == APPS_ROW_ACTIVE_INDEX ? activeColor : color);
            mFillPaint.setAlpha(i == APPS_ROW_ACTIVE_INDEX ? 255 : 190);
            canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
        }
        mFillPaint.setAlpha(255);
    }

    // ---- Alphabets row -------------------------------------------------------------------------

    private void drawAlphabetsRowBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.ALPHABETS_ROW);
        if (rect == null || rect.isEmpty()) return;
        int fill = themeColor(com.termux.shared.R.attr.termuxColorAccentContainer,
            R.color.termux_accent_container);
        int onFill = themeColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
            R.color.termux_on_accent_container);
        mFillPaint.setColor(fill);
        canvas.drawRect(rect, mFillPaint);

        mTextPaint.setColor(onFill);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(Math.min(dp(7), rect.height() * 0.62f));
        int n = ALPHABETS_SAMPLE.length;
        float inset = dp(8);
        float slot = (rect.width() - inset * 2f) / n;
        float baseline = rect.centerY() + mTextPaint.getTextSize() * 0.32f;
        for (int i = 0; i < n; i++) {
            float x = rect.left + inset + slot * (i + 0.5f);
            canvas.drawText(ALPHABETS_SAMPLE[i], x, baseline, mTextPaint);
        }

        drawPill(canvas, Block.ALPHABETS_ROW, rect, false, null, onFill, fill);
    }

    // ---- Extra keys ---------------------------------------------------------------------------

    private void drawExtraKeysBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.EXTRA_KEYS);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        int fill = themeColor(com.termux.shared.R.attr.termuxColorTertiaryContainer,
            R.color.termux_tertiary_container);
        int onFill = themeColor(com.termux.shared.R.attr.termuxColorOnTertiaryContainer,
            R.color.termux_on_tertiary_container);
        mFillPaint.setColor(fill);
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = mLayout.extraKeys.isOnSide();
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        drawExtraKeyGlyphs(canvas, mScratchRectA, onFill);
        endBandOrientation(canvas, saved);

        drawPill(canvas, Block.EXTRA_KEYS, rect, vertical, mIconKeys, onFill, fill);
    }

    private void drawExtraKeyGlyphs(@NonNull Canvas canvas, @NonNull RectF local, int color) {
        if (mNerdTypeface == null) return; // e.g. a bare-module test environment: skip rather than
        // draw tofu boxes for a font asset that failed to load.
        mNerdPaint.setColor(color);
        mNerdPaint.setTextSize(Math.min(dp(11), local.height() * 0.68f));
        int n = EXTRA_KEY_GLYPHS.length;
        float inset = dp(6);
        float slot = (local.width() - inset * 2f) / n;
        float baseline = local.centerY() + mNerdPaint.getTextSize() * 0.32f;
        for (int i = 0; i < n; i++) {
            float x = local.left + inset + slot * (i + 0.5f);
            canvas.drawText(EXTRA_KEY_GLYPHS[i], x, baseline, mNerdPaint);
        }
    }

    // ---- Canvas: Terminal / Home / Display ----------------------------------------------------

    private void drawCanvasBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.CANVAS);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        int fill = themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high);
        int onVariant = themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        mFillPaint.setColor(fill);
        canvas.drawRect(rect, mFillPaint);

        CanvasKind kind = canvasKind();
        switch (kind) {
            case HOME_GRID:
                drawHomeGrid(canvas, rect, accent, onVariant);
                break;
            case DISPLAY:
                drawDisplayCanvas(canvas, rect, onVariant, accent);
                break;
            case TERMINAL:
            default:
                drawTerminalCard(canvas, rect, onVariant, accent);
                break;
        }

        if (canvasPillLabel() != null) {
            int onPrimary = themeColor(com.termux.shared.R.attr.termuxColorOnPrimary,
                R.color.termux_on_primary);
            Drawable icon = kind == CanvasKind.HOME_GRID ? mIconHomeGrid : mIconDisplay;
            drawPill(canvas, Block.CANVAS, rect, false, icon, accent, onPrimary);
        }
    }

    private void drawTerminalCard(@NonNull Canvas canvas, @NonNull RectF rect, int lineColor,
                                   int accent) {
        float pad = dp(8);
        RectF card = mScratchRectA;
        card.set(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad);

        float lineHeight = Math.min(dp(3), card.height() * 0.08f);
        float lineGap = lineHeight * 1.8f;
        float[] widths = {0.62f, 0.85f, 0.45f};
        float y = card.top + lineHeight;
        mFillPaint.setColor(lineColor);
        mFillPaint.setAlpha(120);
        for (float w : widths) {
            mScratchRectB.set(card.left, y, card.left + card.width() * w, y + lineHeight);
            canvas.drawRoundRect(mScratchRectB, lineHeight / 2f, lineHeight / 2f, mFillPaint);
            y += lineGap;
        }
        mFillPaint.setAlpha(255);

        mTextPaint.setColor(accent);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        float promptSize = Math.min(dp(9), card.height() * 0.16f);
        mTextPaint.setTextSize(promptSize);
        float promptY = card.bottom - dp(6);
        canvas.drawText("~ $", card.left, promptY, mTextPaint);

        float promptWidth = mTextPaint.measureText("~ $ ");
        float cursorSize = promptSize * 0.85f;
        mFillPaint.setColor(accent);
        mScratchRectB.set(card.left + promptWidth, promptY - cursorSize,
            card.left + promptWidth + cursorSize * 0.55f, promptY + cursorSize * 0.15f);
        canvas.drawRect(mScratchRectB, mFillPaint);
    }

    private void drawHomeGrid(@NonNull Canvas canvas, @NonNull RectF rect, int accent, int gridColor) {
        if (mLayout == null) return;
        float pad = dp(8);
        RectF area = mScratchRectA;
        area.set(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad);
        if (mGridCollapsed) {
            mFillPaint.setColor(accent);
            mFillPaint.setAlpha(70);
            canvas.drawRoundRect(area, dp(6), dp(6), mFillPaint);
            mFillPaint.setAlpha(255);
            return; // the "n×m" text rides on the canvas pill instead of a second label here
        }
        int columns = Math.max(1, mLayout.widgetColumns);
        int rows = Math.max(1, mLayout.widgetRows);
        float cellGap = dp(3);
        float cellW = (area.width() - cellGap * (columns - 1)) / columns;
        float cellH = (area.height() - cellGap * (rows - 1)) / rows;
        mDashPaint.setColor(gridColor);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                float left = area.left + c * (cellW + cellGap);
                float top = area.top + r * (cellH + cellGap);
                mScratchRectB.set(left, top, left + cellW, top + cellH);
                canvas.drawRoundRect(mScratchRectB, dp(2), dp(2), mDashPaint);
            }
        }
    }

    private void drawDisplayCanvas(@NonNull Canvas canvas, @NonNull RectF rect, int lineColor,
                                    int accent) {
        if (mLayout == null) return;
        float pad = dp(9);
        RectF desk = mScratchRectA;
        desk.set(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad);

        float winInsetX = desk.width() * 0.16f;
        float winTop = desk.top + desk.height() * 0.10f;
        float winBottom = desk.bottom - desk.height() * 0.22f;
        RectF window = mScratchRectB;
        window.set(desk.left + winInsetX, winTop, desk.right - winInsetX, winBottom);
        mLinePaint.setColor(lineColor);
        canvas.drawRoundRect(window, dp(3), dp(3), mLinePaint);

        mFillPaint.setColor(lineColor);
        mFillPaint.setAlpha(90);
        RectF titlebar = mScratchRectA;
        titlebar.set(window.left, window.top, window.right,
            window.top + Math.min(dp(5), window.height() * 0.2f));
        canvas.drawRoundRect(titlebar, dp(2), dp(2), mFillPaint);
        mFillPaint.setAlpha(255);

        if (mLayout.keyboardMode == KeyboardMode.OVERLAY) {
            drawFloatingKeyGrid(canvas, rect, accent);
        }
    }

    private void drawFloatingKeyGrid(@NonNull Canvas canvas, @NonNull RectF rect, int color) {
        float pad = dp(6);
        float height = Math.min(rect.height() * 0.34f, dp(30));
        RectF keys = mScratchRectB;
        keys.set(rect.left + pad, rect.bottom - height - pad, rect.right - pad, rect.bottom - pad);
        mFillPaint.setColor(color);
        mFillPaint.setAlpha(55);
        canvas.drawRoundRect(keys, dp(3), dp(3), mFillPaint);
        mFillPaint.setAlpha(255);

        float cellW = keys.width() / DISPLAY_KEY_GRID_COLUMNS;
        float cellH = keys.height() / DISPLAY_KEY_GRID_ROWS;
        mDashPaint.setColor(color);
        for (int r = 0; r < DISPLAY_KEY_GRID_ROWS; r++) {
            for (int c = 0; c < DISPLAY_KEY_GRID_COLUMNS; c++) {
                float left = keys.left + c * cellW;
                float top = keys.top + r * cellH;
                mScratchRectA.set(left + dp(1), top + dp(1), left + cellW - dp(1), top + cellH - dp(1));
                canvas.drawRoundRect(mScratchRectA, dp(1.5f), dp(1.5f), mDashPaint);
            }
        }
    }

    // ---- Pills ----------------------------------------------------------------------------------

    /**
     * Draws the band's high-contrast identifier: a themed pill holding the block's glyph and, when
     * there is room, its one-word label; on a vertical band the whole pill rotates to lie along it.
     * Falls back to glyph-only rather than clipping, and to a small accent dot when a block (the
     * alphabets strip) has no glyph of its own and no room for the word either.
     */
    private void drawPill(@NonNull Canvas canvas, @NonNull Block block, @NonNull RectF rect,
                           boolean vertical, @Nullable Drawable icon, int bgColor, int contentColor) {
        String label = block == Block.CANVAS ? canvasPillLabel() : pillLabel(block);
        boolean showLabel = label != null && isPillLabelShown(block);
        if (label == null && icon == null) return;

        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        RectF local = mScratchRectA;
        if (local.width() < dp(6) || local.height() < dp(6)) {
            endBandOrientation(canvas, saved);
            return;
        }
        float cx = local.centerX();
        float cy = local.centerY();

        if (icon == null && !showLabel) {
            mFillPaint.setColor(bgColor);
            canvas.drawCircle(cx, cy, Math.min(dp(3), Math.min(local.width(), local.height()) * 0.3f),
                mFillPaint);
            endBandOrientation(canvas, saved);
            return;
        }

        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTextPaint.setTextSize(pillTextSizePx());
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        float textWidth = showLabel ? mTextPaint.measureText(label) : 0f;
        float glyphSize = icon != null ? pillGlyphSizePx(local.height()) : 0f;
        float gap = (icon != null && showLabel) ? pillGapPx() : 0f;
        float pad = pillPaddingPx();
        float contentWidth = glyphSize + gap + textWidth;
        float pillHeight = Math.max(dp(4), Math.min(Math.max(glyphSize, mTextPaint.getTextSize()) + pad,
            local.height() - dp(2)));
        float pillWidth = Math.max(pillHeight,
            Math.min(contentWidth + pad * 2f, Math.max(dp(4), local.width() - dp(4))));

        mScratchRectB.set(cx - pillWidth / 2f, cy - pillHeight / 2f, cx + pillWidth / 2f, cy + pillHeight / 2f);
        mFillPaint.setColor(bgColor);
        canvas.drawRoundRect(mScratchRectB, pillHeight / 2f, pillHeight / 2f, mFillPaint);

        boolean rtl = isRtl();
        float contentLeft = cx - contentWidth / 2f;
        float iconLeft = rtl ? contentLeft + textWidth + gap : contentLeft;
        float textLeft = rtl ? contentLeft : contentLeft + glyphSize + gap;

        if (icon != null) {
            DrawableCompat.setTint(icon, contentColor);
            int top = Math.round(cy - glyphSize / 2f);
            int left = Math.round(iconLeft);
            icon.setBounds(left, top, left + Math.round(glyphSize), top + Math.round(glyphSize));
            icon.draw(canvas);
        }
        if (showLabel) {
            mTextPaint.setColor(contentColor);
            canvas.drawText(label, textLeft, cy + mTextPaint.getTextSize() * 0.32f, mTextPaint);
        }
        endBandOrientation(canvas, saved);
    }

    /**
     * Rotates the canvas so a vertical band can be drawn with the same horizontal-strip code as a
     * bottom/top one, and writes the equivalent horizontal rect (centered on the band, in the
     * rotated space) into {@code outLocal}. Returns the canvas save count to restore with
     * {@link #endBandOrientation}, or -1 when the band is already horizontal (no rotation done).
     */
    private int beginBandOrientation(@NonNull Canvas canvas, @NonNull RectF rect, boolean vertical,
                                      @NonNull RectF outLocal) {
        if (!vertical) {
            outLocal.set(rect);
            return -1;
        }
        float cx = rect.centerX();
        float cy = rect.centerY();
        int saved = canvas.save();
        // RTL flips which end of the physical (screen-relative) edge reads as "first", so the
        // rotated content still reads start-to-end for the current layout direction.
        canvas.rotate(isRtl() ? 90f : -90f, cx, cy);
        float halfLength = rect.height() / 2f;
        float halfThickness = rect.width() / 2f;
        outLocal.set(cx - halfLength, cy - halfThickness, cx + halfLength, cy + halfThickness);
        return saved;
    }

    private void endBandOrientation(@NonNull Canvas canvas, int saved) {
        if (saved != -1) canvas.restoreToCount(saved);
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Block tapped = blockAt(event.getX(), event.getY());
            if (tapped != null && mListener != null) mListener.onBlockTapped(tapped);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private Block blockAt(float x, float y) {
        // Smaller, more specific blocks first, so a corner where two strips meet resolves to the
        // narrower one (the alphabets row rides a thin strip inside the wider apps row).
        Block[] order = {Block.ALPHABETS_ROW, Block.STATUS_BAR, Block.EXTRA_KEYS,
            Block.APPS_ROW, Block.CANVAS};
        for (Block block : order) {
            RectF rect = mBlockRects.get(block);
            if (rect != null && rect.contains(x, y)) return block;
        }
        return null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @ColorInt
    private int themeColor(@AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(this, attr,
            ContextCompat.getColor(getContext(), fallbackColorRes));
    }
}
