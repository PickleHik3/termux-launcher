package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

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
import com.termux.app.Spring;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A phone-shaped miniature of one place's resolved {@link PlaceLayout} with a legend beside it.
 * The phone is a faithful, small rendering of the launcher's own chrome — status bar, pinned apps,
 * A–Z index, extra keys and the place-specific canvas underneath them — every band tinted with the
 * app's own theme attrs so it reads correctly in every theme. Nothing is written over the bands:
 * each one is named in the legend by a swatch of its colour, its glyph and its word, and a row the
 * arrangement leaves out is still listed there, dimmed and marked hidden, so the picture never
 * silently drops one. Tapping a band or its legend row reports the block, so the settings page can
 * scroll to its row.
 *
 * <p>The picture is also the editor. Every band with a placement carries a grip at its trailing
 * end; a touch-down on one lifts that bar at once — no long press — and for the rest of the gesture
 * the view keeps the preference list from stealing the touch. While a bar is lifted every edge it
 * may legally stand on is outlined, the tray under the phone offers to put it away, and a release
 * over either reports the new placement. A release anywhere else springs the bar back and reports
 * nothing. {@link MiniatureDragPolicy} owns which targets exist and which one the finger is over.
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

    /**
     * Reports a bar dropped on a legal target: the edge it now stands on, or {@code null} for the
     * tray, which is the same as hidden. Nothing is reported for a release that landed nowhere.
     */
    public interface OnBarDroppedListener {
        void onBarDropped(@NonNull Block bar, @Nullable Edge edge);
    }

    /** Legend order, top to bottom: the way the rows stack on a default portrait screen. */
    private static final Block[] LEGEND_ORDER = {
        Block.STATUS_BAR, Block.CANVAS, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS};

    /** The bands with a placement to change, in the order the tray lists them. */
    private static final Block[] BARS = {
        Block.STATUS_BAR, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS};

    private static final float STATUS_BAR_FRACTION = 0.11f;
    private static final float ROW_FRACTION = 0.15f;
    private static final float ALPHABETS_FRACTION = 0.08f;
    /** Portrait: narrow and tall; landscape: wide and short — a phone silhouette either way. */
    private static final float PORTRAIT_ASPECT = 9f / 19.5f;
    private static final float LANDSCAPE_ASPECT = 19.5f / 9f;
    private static final float DEFAULT_HEIGHT_DP = 188f;

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

    private static final float LEGEND_TEXT_SP = 12f;
    private static final float LEGEND_MAX_FONT_SCALE = 1.3f;
    private static final float LEGEND_SWATCH_DP = 20f;
    private static final float LEGEND_SWATCH_GAP_DP = 8f;
    private static final float LEGEND_ROW_GAP_DP = 6f;
    private static final float LEGEND_TO_FRAME_GAP_DP = 18f;
    /** The legend never claims more than this share of the width; longer labels are ellipsized. */
    private static final float LEGEND_MAX_WIDTH_FRACTION = 0.52f;
    private static final int HIDDEN_ALPHA = 110;

    /** The strip under the phone: the hidden bars live there, and a lifted bar can be put there. */
    private static final float TRAY_HEIGHT_DP = 34f;
    private static final float TRAY_GAP_DP = 6f;
    private static final float TRAY_TEXT_SP = 10f;
    private static final float GRIP_LENGTH_DP = 11f;
    private static final float GRIP_THICKNESS_DP = 6f;
    private static final float GRIP_END_GAP_DP = 3f;
    /** How far past the glyph a finger still counts as being on the grip. */
    private static final float GRIP_TOUCH_SLOP_DP = 7f;
    private static final int GRIP_ALPHA = 155;
    private static final int GHOST_ALPHA = 190;
    private static final int LIFTED_BAND_ALPHA = 70;
    private static final int SLOT_HOVER_ALPHA = 60;
    private static final float GHOST_SCALE = 1.08f;
    /** The dock plank's press constants: a lift and a spring-back are the same kind of motion. */
    private static final float SPRING_STIFFNESS = 320f;
    private static final float SPRING_DAMPING = 22f;

    @Nullable private PlaceLayout mLayout;
    @NonNull private PlaceOrientation mOrientation = PlaceOrientation.PORTRAIT;
    @NonNull private PaneWallPage mPlace = PaneWallPage.TERMINAL;
    @Nullable private OnBlockTappedListener mListener;
    private boolean mLegendVisible = true;

    private final Paint mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNerdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mLegendPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFrameRect = new RectF();
    private final Path mClipPath = new Path();
    /** Reused scratch rects for whatever a draw call is computing right now; never read across
     *  two different shapes, only within one draw-then-move-on sequence. */
    private final RectF mScratchRectA = new RectF();
    private final RectF mScratchRectB = new RectF();
    private final Map<Block, RectF> mBlockRects = new EnumMap<>(Block.class);
    private final Map<Block, RectF> mLegendRects = new EnumMap<>(Block.class);
    private RectF mRemaining = new RectF();
    private boolean mGridCollapsed;
    private float mLegendLabelWidth;

    // ---- The drag: one gesture's worth of state, all of it cleared when it ends ----------------
    @Nullable private OnBarDroppedListener mDropListener;
    /** The grip a finger is on, and the bar it lifted; null while nothing is lifted. */
    @Nullable private Block mDraggedBar;
    /** Where the finger went down, so the copy travels with it from where the bar stood. */
    private float mDownX;
    private float mDownY;
    /** Where the lifted copy started, so it can be drawn at the finger and sprung back. */
    private final RectF mLiftOrigin = new RectF();
    private final RectF mGhostGripRect = new RectF();
    private final List<MiniatureDragPolicy.Slot> mSlots = new ArrayList<>();
    @Nullable private MiniatureDragPolicy.Slot mHoverSlot;
    private final RectF mTrayRect = new RectF();
    private final Map<Block, RectF> mTrayChipRects = new EnumMap<>(Block.class);
    private final Map<Block, RectF> mGripRects = new EnumMap<>(Block.class);
    /** The lifted copy's offset from where it started, sprung back to zero on a release. */
    private final Spring mGhostX = new Spring(0f, SPRING_STIFFNESS, SPRING_DAMPING);
    private final Spring mGhostY = new Spring(0f, SPRING_STIFFNESS, SPRING_DAMPING);
    private final Spring mGhostScale = new Spring(1f, SPRING_STIFFNESS, SPRING_DAMPING);
    private boolean mSpringingBack;
    private long mLastFrameNanos;
    private final Runnable mMotionTick = this::advanceMotion;

    @Nullable private final Typeface mNerdTypeface;
    @NonNull private final Drawable mIconStatus;
    @NonNull private final Drawable mIconApps;
    @NonNull private final Drawable mIconKeys;
    @NonNull private final Drawable mIconHomeGrid;
    @NonNull private final Drawable mIconDisplay;
    @NonNull private final Drawable mIconTerminal;

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
        mLegendPaint.setStyle(Paint.Style.FILL);
        mLegendPaint.setTextAlign(Paint.Align.LEFT);
        mNerdTypeface = NerdFontSpans.typeface(context);
        mNerdPaint.setStyle(Paint.Style.FILL);
        mNerdPaint.setTextAlign(Paint.Align.CENTER);
        if (mNerdTypeface != null) mNerdPaint.setTypeface(mNerdTypeface);

        mIconStatus = loadIcon(R.drawable.ic_symbol_notifications);
        mIconApps = loadIcon(R.drawable.ic_symbol_apps);
        mIconKeys = loadIcon(R.drawable.ic_symbol_keyboard);
        mIconHomeGrid = loadIcon(R.drawable.ic_symbol_grid_view);
        mIconDisplay = loadIcon(R.drawable.ic_symbol_desktop_windows);
        mIconTerminal = loadIcon(R.drawable.ic_symbol_terminal);
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
        // A new arrangement is the answer to the drag that wrote it, or someone else's write while
        // a finger was down; either way the gesture is over and nothing may spring back into it.
        endDrag();
        // The blocks are laid out from the frame, and the frame from the view's size; a new
        // arrangement, place or orientation at the same size never reaches onSizeChanged, so the
        // blocks are recomputed here or the old picture would be drawn again.
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    public void setOnBarDroppedListener(@Nullable OnBarDroppedListener listener) {
        mDropListener = listener;
    }

    /** The grip a finger lifts this bar by, in view pixels, or null while the bar has none. */
    @Nullable
    @VisibleForTesting
    public RectF gripRect(@NonNull Block bar) {
        RectF rect = mGripRects.get(bar);
        return rect == null ? null : new RectF(rect);
    }

    /** The strip under the phone: the hidden bars' chips, and a lifted bar's way out. */
    @NonNull
    @VisibleForTesting
    public RectF trayRect() {
        return new RectF(mTrayRect);
    }

    /** The chip a hidden bar is listed as in the tray, or null while the bar is on the phone. */
    @Nullable
    @VisibleForTesting
    public RectF trayChipRect(@NonNull Block bar) {
        RectF rect = mTrayChipRects.get(bar);
        return rect == null ? null : new RectF(rect);
    }

    /** The bar the finger is holding, or null when nothing is lifted. */
    @Nullable
    @VisibleForTesting
    public Block draggedBar() {
        return mDraggedBar;
    }

    /** Every target the lifted bar may be dropped on; empty while nothing is lifted. */
    @NonNull
    @VisibleForTesting
    public List<MiniatureDragPolicy.Slot> slots() {
        return new ArrayList<>(mSlots);
    }

    /** The slot outlined for one edge while a bar is lifted, or null when that edge is illegal. */
    @Nullable
    @VisibleForTesting
    public MiniatureDragPolicy.Slot slotFor(@NonNull Edge edge) {
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.edge == edge) return slot;
        }
        return null;
    }

    /** The rectangle a block is drawn in, in view pixels, or null while it is not on the picture. */
    @Nullable
    @VisibleForTesting
    public RectF blockRect(@NonNull Block block) {
        RectF rect = mBlockRects.get(block);
        return rect == null ? null : new RectF(rect);
    }

    /** The legend row naming a block, in view pixels; every block has one once a layout is set. */
    @Nullable
    @VisibleForTesting
    public RectF legendRect(@NonNull Block block) {
        RectF rect = mLegendRects.get(block);
        return rect == null ? null : new RectF(rect);
    }

    /** Whether the arrangement leaves this block off the screen — the legend then marks it so. */
    @VisibleForTesting
    public boolean isBlockHidden(@NonNull Block block) {
        if (mLayout == null) return false;
        switch (block) {
            case APPS_ROW: return mLayout.appsRow == RowPlacement.HIDDEN;
            case EXTRA_KEYS: return mLayout.extraKeys == RowPlacement.HIDDEN;
            case ALPHABETS_ROW:
                return !mLayout.azRowShown;
            case STATUS_BAR:
            case CANVAS:
            default: return false;
        }
    }

    public void setOnBlockTappedListener(@Nullable OnBlockTappedListener listener) {
        mListener = listener;
    }

    /**
     * Whether the legend stands beside the phone. Off, the phone takes the whole view and the
     * bands are the only tap targets — the Layout page names every element in its own rows, so a
     * second list beside two miniatures would say everything twice.
     */
    public void setLegendVisible(boolean visible) {
        if (mLegendVisible == visible) return;
        mLegendVisible = visible;
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    /** Whether a legend is drawn beside the phone. */
    @VisibleForTesting
    public boolean isLegendVisible() {
        return mLegendVisible;
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

    // ---- Geometry ------------------------------------------------------------------------------

    /**
     * Places the phone and the legend side by side, centred as one group: the legend takes the
     * width its longest label needs (capped), the phone takes what is left at its orientation's
     * aspect. In RTL the legend stands on the left and the phone on the right.
     */
    private void layoutFrame(int viewWidth, int viewHeight) {
        float pad = dp(4);
        float availableWidth = Math.max(0f, viewWidth - 2 * pad);
        // The tray's room is reserved whether or not anything is in it, so neither the phone nor
        // the rows below it move when a bar is hidden or a drag starts.
        float trayHeight = dp(TRAY_HEIGHT_DP) + dp(TRAY_GAP_DP);
        float availableHeight = Math.max(0f, viewHeight - 2 * pad - trayHeight);

        mLegendPaint.setTextSize(legendTextSizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        float swatch = dp(LEGEND_SWATCH_DP);
        float swatchGap = dp(LEGEND_SWATCH_GAP_DP);
        float longest = 0f;
        for (Block block : LEGEND_ORDER) {
            String label = legendLabel(block);
            if (label != null) longest = Math.max(longest, mLegendPaint.measureText(label));
        }
        float legendWidth = mLegendVisible
            ? Math.min(swatch + swatchGap + longest, availableWidth * LEGEND_MAX_WIDTH_FRACTION)
            : 0f;
        mLegendLabelWidth = Math.max(0f, legendWidth - swatch - swatchGap);
        float legendGap = mLegendVisible ? dp(LEGEND_TO_FRAME_GAP_DP) : 0f;

        float frameAreaWidth = Math.max(0f, availableWidth - legendWidth - legendGap);
        float aspect = mOrientation == PlaceOrientation.LANDSCAPE
            ? LANDSCAPE_ASPECT : PORTRAIT_ASPECT;
        float frameHeight = availableHeight;
        float frameWidth = frameHeight * aspect;
        if (frameWidth > frameAreaWidth) {
            frameWidth = frameAreaWidth;
            frameHeight = frameWidth / aspect;
        }

        float groupWidth = frameWidth + legendGap + legendWidth;
        float groupLeft = (viewWidth - groupWidth) / 2f;
        float frameLeft;
        float legendLeft;
        if (isRtl()) {
            legendLeft = groupLeft;
            frameLeft = groupLeft + legendWidth + legendGap;
        } else {
            frameLeft = groupLeft;
            legendLeft = groupLeft + frameWidth + legendGap;
        }
        float frameTop = (viewHeight - trayHeight - frameHeight) / 2f;
        mFrameRect.set(frameLeft, frameTop, frameLeft + frameWidth, frameTop + frameHeight);
        float trayTop = mFrameRect.bottom + dp(TRAY_GAP_DP);
        mTrayRect.set(mFrameRect.left, trayTop, mFrameRect.right, trayTop + dp(TRAY_HEIGHT_DP));

        layoutLegend(legendLeft, legendWidth, Math.round(viewHeight - trayHeight), swatch);
        computeBlocks();
    }

    private void layoutLegend(float left, float width, int viewHeight, float swatch) {
        mLegendRects.clear();
        if (mLayout == null || !mLegendVisible) return;
        float textHeight = mLegendPaint.getFontMetrics(null);
        float rowHeight = Math.max(swatch, textHeight) + dp(LEGEND_ROW_GAP_DP);
        float total = rowHeight * LEGEND_ORDER.length;
        float y = (viewHeight - total) / 2f;
        for (Block block : LEGEND_ORDER) {
            mLegendRects.put(block, new RectF(left, y, left + width, y + rowHeight));
            y += rowHeight;
        }
    }

    private void computeBlocks() {
        mBlockRects.clear();
        mGripRects.clear();
        mTrayChipRects.clear();
        mRemaining = new RectF(mFrameRect);
        if (mLayout == null) {
            mGridCollapsed = false;
            mSlots.clear();
            return;
        }
        boolean azShown = !isBlockHidden(Block.ALPHABETS_ROW);
        // The bar's own edge only applies while it stands alone; riding under the apps row always
        // reads as bottom regardless of what is stored (PlaceChromePolicy.azBarEdge).
        Edge azEdge = PlaceChromePolicy.azBarEdge(mLayout);

        takeEdgeStrip(Block.STATUS_BAR, mLayout.statusBarEdge, STATUS_BAR_FRACTION);
        // A top bar is claimed right after the status bar, before any side column, so it always
        // reads as the band directly under the status bar.
        if (azShown && azEdge == Edge.TOP) {
            takeEdgeStrip(Block.ALPHABETS_ROW, Edge.TOP, ALPHABETS_FRACTION);
        }
        // Strips are claimed from the outside in. Two facts, both verified against the device:
        // (1) a side rail/column pair puts the pinned-apps rail at the screen edge and pads the
        // extra-keys column in by the rail's width, so a side apps row is claimed before a side
        // extra-keys row; (2) the bottom stack still reads extra keys, then the A–Z index, then
        // pinned apps, so a bottom extra-keys row is still claimed before a bottom apps row.
        if (mLayout.appsRow.isOnSide()) {
            takeEdgeStrip(Block.APPS_ROW, edgeOf(mLayout.appsRow), ROW_FRACTION);
        }
        if (mLayout.extraKeys.isOnSide()) {
            takeEdgeStrip(Block.EXTRA_KEYS, edgeOf(mLayout.extraKeys), ROW_FRACTION);
        }
        // A side bar is innermost of the side columns — the rail and the extra-keys column pad it
        // in first, same as the device.
        if (azShown && azEdge.isOnSide()) {
            takeEdgeStrip(Block.ALPHABETS_ROW, azEdge, ALPHABETS_FRACTION);
        }
        if (mLayout.extraKeys == RowPlacement.BOTTOM) {
            takeEdgeStrip(Block.EXTRA_KEYS, Edge.BOTTOM, ROW_FRACTION);
        }
        if (azShown && azEdge == Edge.BOTTOM) {
            takeEdgeStrip(Block.ALPHABETS_ROW, Edge.BOTTOM, ALPHABETS_FRACTION);
        }
        if (mLayout.appsRow == RowPlacement.BOTTOM) {
            takeEdgeStrip(Block.APPS_ROW, Edge.BOTTOM, ROW_FRACTION);
        }
        mBlockRects.put(Block.CANVAS, new RectF(mRemaining));
        computeContent();
        computeGrips();
        computeTrayChips();
        computeSlots();
        updateContentDescription();
    }

    // ---- Grips, tray and slots -----------------------------------------------------------------

    /** A grip at the trailing end of every band the user may move; the canvas never gets one. */
    private void computeGrips() {
        for (Block bar : BARS) {
            RectF band = mBlockRects.get(bar);
            if (band == null || band.isEmpty()) continue;
            mGripRects.put(bar, gripFor(band, isBarVertical(bar)));
        }
    }

    /**
     * The grip glyph's own rectangle: at the far end of the band, along its length, and never
     * thicker than the band it rides in.
     */
    @NonNull
    private RectF gripFor(@NonNull RectF band, boolean vertical) {
        RectF grip = new RectF();
        gripInto(band, vertical, grip);
        return grip;
    }

    private void gripInto(@NonNull RectF band, boolean vertical, @NonNull RectF out) {
        float gap = dp(GRIP_END_GAP_DP);
        float length = Math.min(dp(GRIP_LENGTH_DP),
            (vertical ? band.height() : band.width()) * 0.35f);
        float thickness = Math.min(dp(GRIP_THICKNESS_DP),
            (vertical ? band.width() : band.height()) * 0.55f);
        if (vertical) {
            float cx = band.centerX();
            float top = isRtl() ? band.top + gap : band.bottom - gap - length;
            out.set(cx - thickness / 2f, top, cx + thickness / 2f, top + length);
            return;
        }
        float cy = band.centerY();
        float left = isRtl() ? band.left + gap : band.right - gap - length;
        out.set(left, cy - thickness / 2f, left + length, cy + thickness / 2f);
    }

    /** Whether a bar stands as a column rather than a row, which turns its grip with it. */
    private boolean isBarVertical(@NonNull Block bar) {
        if (mLayout == null) return false;
        switch (bar) {
            case STATUS_BAR: return isVerticalEdge(mLayout.statusBarEdge);
            case APPS_ROW: return mLayout.appsRow.isOnSide();
            case ALPHABETS_ROW: return PlaceChromePolicy.azBarEdge(mLayout).isOnSide();
            case EXTRA_KEYS: return mLayout.extraKeys.isOnSide();
            default: return false;
        }
    }

    /**
     * A chip in the tray per bar the arrangement leaves off the phone, sharing the strip equally.
     * The chips carry the same grip the bands do, so a bar comes back the way it went away.
     */
    private void computeTrayChips() {
        List<Block> hidden = hiddenBars();
        if (hidden.isEmpty() || mTrayRect.isEmpty()) return;
        float gap = dp(4);
        float width = (mTrayRect.width() - gap * (hidden.size() - 1)) / hidden.size();
        float inset = dp(3);
        for (int i = 0; i < hidden.size(); i++) {
            float left = mTrayRect.left + i * (width + gap);
            RectF chip = new RectF(left, mTrayRect.top + inset, left + width,
                mTrayRect.bottom - inset);
            mTrayChipRects.put(hidden.get(i), chip);
            mGripRects.put(hidden.get(i), gripFor(chip, false));
        }
    }

    /** The bars the arrangement leaves off the phone, in the order the tray lists them. */
    @NonNull
    private List<Block> hiddenBars() {
        List<Block> hidden = new ArrayList<>(3);
        if (mLayout == null) return hidden;
        for (Block bar : BARS) {
            if (bar != Block.STATUS_BAR && isBlockHidden(bar)) hidden.add(bar);
        }
        return hidden;
    }

    /**
     * Where the lifted bar may land. Edge slots sit against the free area inside whatever already
     * stands on that edge — one edge takes a stack of bars, so a slot is drawn beside its
     * neighbours rather than over them — and the tray is offered to a bar that may hide.
     */
    private void computeSlots() {
        mSlots.clear();
        mHoverSlot = null;
        MiniatureDragPolicy.Bar bar = mDraggedBar == null ? null : barOf(mDraggedBar);
        if (bar == null || mLayout == null) return;
        MiniatureDragPolicy.Targets targets =
            MiniatureDragPolicy.targets(mPlace, mOrientation, mLayout, bar);
        RectF free = mBlockRects.get(Block.CANVAS);
        if (free != null && !free.isEmpty()) {
            for (Edge edge : targets.edges) mSlots.add(slotAt(edge, free));
        }
        if (targets.tray && !mTrayRect.isEmpty()) {
            mSlots.add(new MiniatureDragPolicy.Slot(null, mTrayRect.left, mTrayRect.top,
                mTrayRect.right, mTrayRect.bottom));
        }
    }

    @NonNull
    private MiniatureDragPolicy.Slot slotAt(@NonNull Edge edge, @NonNull RectF free) {
        float fraction = mDraggedBar == Block.STATUS_BAR ? STATUS_BAR_FRACTION
            : mDraggedBar == Block.ALPHABETS_ROW ? ALPHABETS_FRACTION : ROW_FRACTION;
        boolean vertical = isVerticalEdge(edge);
        float span = vertical ? free.width() : free.height();
        float thickness = Math.max(dp(9), Math.min(span * fraction, span * 0.45f));
        switch (edge) {
            case TOP:
                return new MiniatureDragPolicy.Slot(edge, free.left, free.top, free.right,
                    free.top + thickness);
            case LEFT:
                return new MiniatureDragPolicy.Slot(edge, free.left, free.top,
                    free.left + thickness, free.bottom);
            case RIGHT:
                return new MiniatureDragPolicy.Slot(edge, free.right - thickness, free.top,
                    free.right, free.bottom);
            case BOTTOM:
            default:
                return new MiniatureDragPolicy.Slot(edge, free.left, free.bottom - thickness,
                    free.right, free.bottom);
        }
    }

    /** The policy's name for one of the miniature's bands, or null for a band with no placement. */
    @Nullable
    public static MiniatureDragPolicy.Bar barOf(@NonNull Block block) {
        switch (block) {
            case STATUS_BAR: return MiniatureDragPolicy.Bar.STATUS_BAR;
            case APPS_ROW: return MiniatureDragPolicy.Bar.APPS_ROW;
            case ALPHABETS_ROW: return MiniatureDragPolicy.Bar.AZ_INDEX;
            case EXTRA_KEYS: return MiniatureDragPolicy.Bar.EXTRA_KEYS;
            case CANVAS:
            default: return null;
        }
    }

    /**
     * The picture read aloud: every bar and where it stands, hidden ones included. The grips are
     * decorative — a screen reader changes these values in the page's own rows, which is why
     * nothing here is only reachable by dragging.
     */
    private void updateContentDescription() {
        if (mLayout == null) {
            setContentDescription(null);
            return;
        }
        StringBuilder description = new StringBuilder();
        for (Block bar : BARS) {
            String name = barName(bar);
            if (name == null) continue;
            if (description.length() > 0) description.append(", ");
            description.append(getContext().getString(
                R.string.settings_layout_chooser_group_format, name, barPosition(bar)));
        }
        setContentDescription(description);
    }

    @Nullable
    private String barName(@NonNull Block bar) {
        switch (bar) {
            case STATUS_BAR:
                return getContext().getString(R.string.settings_layout_miniature_status);
            case APPS_ROW:
                return getContext().getString(R.string.settings_layout_miniature_apps);
            case ALPHABETS_ROW:
                return getContext().getString(R.string.settings_layout_miniature_alphabets);
            case EXTRA_KEYS:
                return getContext().getString(R.string.settings_layout_miniature_keys);
            default:
                return null;
        }
    }

    /** The word for where a bar stands: its edge, or that it is hidden. */
    @NonNull
    private String barPosition(@NonNull Block bar) {
        if (mLayout == null || isBlockHidden(bar)) {
            return getContext().getString(R.string.settings_layout_row_hidden);
        }
        switch (bar) {
            case STATUS_BAR:
                return getContext().getString(LayoutChooserModel.edgeLabel(mLayout.statusBarEdge));
            case APPS_ROW:
                return getContext().getString(LayoutChooserModel.rowLabel(mLayout.appsRow));
            case ALPHABETS_ROW:
                return getContext().getString(
                    LayoutChooserModel.edgeLabel(PlaceChromePolicy.azBarEdge(mLayout)));
            case EXTRA_KEYS:
            default:
                return getContext().getString(LayoutChooserModel.rowLabel(mLayout.extraKeys));
        }
    }

    /** Decisions that do not need a {@link Canvas} to make, recomputed whenever the blocks move. */
    private void computeContent() {
        mGridCollapsed = false;
        if (mLayout == null || canvasKind() != CanvasKind.HOME_GRID) return;
        RectF canvasRect = mBlockRects.get(Block.CANVAS);
        if (canvasRect == null) return;
        int columns = Math.max(1, mLayout.widgetColumns);
        int rows = Math.max(1, mLayout.widgetRows);
        float cellWidth = canvasRect.width() / columns;
        float cellHeight = canvasRect.height() / rows;
        mGridCollapsed = Math.min(cellWidth, cellHeight) < dp(4);
    }

    /** The legend's word for a block, marked hidden when the arrangement leaves it out. */
    @Nullable
    private String legendLabel(@NonNull Block block) {
        if (mLayout == null) return null;
        String label;
        switch (block) {
            case STATUS_BAR: label = getContext().getString(R.string.settings_layout_miniature_status); break;
            case APPS_ROW: label = getContext().getString(R.string.settings_layout_miniature_apps); break;
            case ALPHABETS_ROW:
                label = getContext().getString(R.string.settings_layout_miniature_alphabets); break;
            case EXTRA_KEYS: label = getContext().getString(R.string.settings_layout_miniature_keys); break;
            case CANVAS:
            default: label = canvasLabel(); break;
        }
        if (isBlockHidden(block)) {
            return getContext().getString(R.string.settings_layout_miniature_hidden_format, label);
        }
        return label;
    }

    @NonNull
    private String canvasLabel() {
        switch (canvasKind()) {
            case HOME_GRID:
                return getContext().getString(R.string.settings_layout_miniature_grid_format,
                    mLayout == null ? 0 : mLayout.widgetColumns,
                    mLayout == null ? 0 : mLayout.widgetRows);
            case DISPLAY:
                return getContext().getString(
                    mLayout != null && mLayout.keyboardMode == KeyboardMode.OVERLAY
                        ? R.string.settings_layout_miniature_display_overlay
                        : R.string.settings_layout_miniature_display);
            case TERMINAL:
            default:
                return getContext().getString(R.string.settings_layout_miniature_terminal);
        }
    }

    private float legendTextSizePx() {
        float fontScale = getResources().getConfiguration().fontScale;
        if (fontScale <= 0f) fontScale = 1f;
        float clamped = Math.min(fontScale, LEGEND_MAX_FONT_SCALE);
        return LEGEND_TEXT_SP * getResources().getDisplayMetrics().density * clamped;
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

    // ---- Colours -------------------------------------------------------------------------------

    /** The band's fill; the legend swatch uses the same one so the two are read as one thing. */
    @ColorInt
    private int bandFill(@NonNull Block block) {
        return blockColor(getContext(), block);
    }

    /**
     * The colour a block is drawn in, for anything outside the miniature that has to point at the
     * same band — the Layout page's rows carry a swatch of it beside the element's name.
     */
    @ColorInt
    public static int blockColor(@NonNull Context host, @NonNull Block block) {
        switch (block) {
            case STATUS_BAR:
                return hostColor(host, com.termux.shared.R.attr.termuxColorPrimary,
                    R.color.termux_primary);
            case APPS_ROW:
                return hostColor(host, com.termux.shared.R.attr.termuxColorSecondary,
                    R.color.termux_secondary);
            case ALPHABETS_ROW:
                return hostColor(host, com.termux.shared.R.attr.termuxColorAccentContainer,
                    R.color.termux_accent_container);
            case EXTRA_KEYS:
                return hostColor(host, com.termux.shared.R.attr.termuxColorTertiaryContainer,
                    R.color.termux_tertiary_container);
            case CANVAS:
            default:
                return hostColor(host, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                    R.color.termux_surface_panel_high);
        }
    }

    @ColorInt
    private static int hostColor(@NonNull Context host, @AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(host, attr, ContextCompat.getColor(host, fallbackColorRes));
    }

    @ColorInt
    private int bandOnFill(@NonNull Block block) {
        switch (block) {
            case STATUS_BAR:
                return themeColor(com.termux.shared.R.attr.termuxColorOnPrimary, R.color.termux_on_primary);
            case APPS_ROW:
                return themeColor(com.termux.shared.R.attr.termuxColorOnSecondary,
                    R.color.termux_on_secondary);
            case ALPHABETS_ROW:
                return themeColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
                    R.color.termux_on_accent_container);
            case EXTRA_KEYS:
                return themeColor(com.termux.shared.R.attr.termuxColorOnTertiaryContainer,
                    R.color.termux_on_tertiary_container);
            case CANVAS:
            default:
                return themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant);
        }
    }

    // ---- Drawing -------------------------------------------------------------------------------

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
        drawGrips(canvas);
        drawSlots(canvas);
        canvas.restoreToCount(saved);

        mFramePaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        canvas.drawRoundRect(mFrameRect, radius, radius, mFramePaint);

        drawTray(canvas);
        drawLegend(canvas);
        drawGhost(canvas);
    }

    /**
     * The band the finger is holding keeps its place as a faint version of itself while the lifted
     * copy carries the paint, so the picture never loses the row that is being moved.
     */
    private boolean drawLiftedPlaceholder(@NonNull Canvas canvas, @NonNull Block block,
                                          @NonNull RectF rect) {
        if (mDraggedBar != block) return false;
        mFillPaint.setColor(bandFill(block));
        mFillPaint.setAlpha(LIFTED_BAND_ALPHA);
        canvas.drawRect(rect, mFillPaint);
        mFillPaint.setAlpha(255);
        return true;
    }

    // ---- Status bar --------------------------------------------------------------------------

    private void drawStatusBarBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.STATUS_BAR);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        if (drawLiftedPlaceholder(canvas, Block.STATUS_BAR, rect)) return;
        mFillPaint.setColor(bandFill(Block.STATUS_BAR));
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = isVerticalEdge(mLayout.statusBarEdge);
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.STATUS_BAR, vertical);
        drawClockAndDots(canvas, mScratchRectA, bandOnFill(Block.STATUS_BAR));
        endBandOrientation(canvas, saved);
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
        if (drawLiftedPlaceholder(canvas, Block.APPS_ROW, rect)) return;
        int active = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        mFillPaint.setColor(bandFill(Block.APPS_ROW));
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = mLayout.appsRow.isOnSide();
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.APPS_ROW, vertical);
        drawAppIcons(canvas, mScratchRectA, bandOnFill(Block.APPS_ROW), active);
        endBandOrientation(canvas, saved);
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
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        if (drawLiftedPlaceholder(canvas, Block.ALPHABETS_ROW, rect)) return;
        mFillPaint.setColor(bandFill(Block.ALPHABETS_ROW));
        canvas.drawRect(rect, mFillPaint);

        // A side bar is a column of upright letters: rotate so the same left-to-right layout below
        // draws them stacked along the column's length instead of squeezed across its thinness.
        boolean vertical = PlaceChromePolicy.azBarEdge(mLayout).isOnSide();
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.ALPHABETS_ROW, vertical);
        drawAlphabetLetters(canvas, mScratchRectA, bandOnFill(Block.ALPHABETS_ROW));
        endBandOrientation(canvas, saved);
    }

    private void drawAlphabetLetters(@NonNull Canvas canvas, @NonNull RectF local, int color) {
        mTextPaint.setColor(color);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(Math.min(dp(7), local.height() * 0.62f));
        int n = ALPHABETS_SAMPLE.length;
        float inset = dp(8);
        float slot = (local.width() - inset * 2f) / n;
        float baseline = local.centerY() + mTextPaint.getTextSize() * 0.32f;
        for (int i = 0; i < n; i++) {
            float x = local.left + inset + slot * (i + 0.5f);
            canvas.drawText(ALPHABETS_SAMPLE[i], x, baseline, mTextPaint);
        }
    }

    // ---- Extra keys ---------------------------------------------------------------------------

    private void drawExtraKeysBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.EXTRA_KEYS);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        if (drawLiftedPlaceholder(canvas, Block.EXTRA_KEYS, rect)) return;
        mFillPaint.setColor(bandFill(Block.EXTRA_KEYS));
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = mLayout.extraKeys.isOnSide();
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.EXTRA_KEYS, vertical);
        drawExtraKeyGlyphs(canvas, mScratchRectA, bandOnFill(Block.EXTRA_KEYS));
        endBandOrientation(canvas, saved);
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
        int onVariant = bandOnFill(Block.CANVAS);
        int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        mFillPaint.setColor(bandFill(Block.CANVAS));
        canvas.drawRect(rect, mFillPaint);

        switch (canvasKind()) {
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
            return; // the "n×m" figure is in the legend, so nothing else is written here
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

    // ---- Legend ----------------------------------------------------------------------------------

    /**
     * One row per block beside the phone: a swatch in the band's own colour carrying the band's
     * glyph, then its word. A block the arrangement leaves out keeps its row, dimmed and worded as
     * hidden, so a reader can tell "hidden" from "not shown here" at a glance.
     */
    private void drawLegend(@NonNull Canvas canvas) {
        if (mLayout == null || !mLegendVisible) return;
        float swatch = dp(LEGEND_SWATCH_DP);
        float swatchGap = dp(LEGEND_SWATCH_GAP_DP);
        float swatchRadius = dp(5);
        int onSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        int outline = themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant);
        boolean rtl = isRtl();
        mLegendPaint.setTextSize(legendTextSizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        float textBaselineOffset = -(mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;

        for (Block block : LEGEND_ORDER) {
            RectF row = mLegendRects.get(block);
            String label = legendLabel(block);
            if (row == null || label == null) continue;
            boolean hidden = isBlockHidden(block);
            float cy = row.centerY();

            float swatchLeft = rtl ? row.right - swatch : row.left;
            mScratchRectA.set(swatchLeft, cy - swatch / 2f, swatchLeft + swatch, cy + swatch / 2f);
            if (hidden) {
                mLinePaint.setColor(outline);
                canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mLinePaint);
            } else {
                mFillPaint.setColor(bandFill(block));
                canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mFillPaint);
                if (block == Block.CANVAS) {
                    mLinePaint.setColor(outline);
                    canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mLinePaint);
                }
            }
            drawSwatchGlyph(canvas, block, mScratchRectA, hidden ? outline : bandOnFill(block));

            float textLeft = rtl ? row.left : swatchLeft + swatch + swatchGap;
            CharSequence shown = TextUtils.ellipsize(label, mLegendPaint, mLegendLabelWidth,
                TextUtils.TruncateAt.END);
            mLegendPaint.setColor(onSurface);
            mLegendPaint.setAlpha(hidden ? HIDDEN_ALPHA : 255);
            if (rtl) {
                float width = mLegendPaint.measureText(shown, 0, shown.length());
                textLeft = swatchLeft - swatchGap - width;
            }
            canvas.drawText(shown, 0, shown.length(), textLeft, cy + textBaselineOffset, mLegendPaint);
            mLegendPaint.setAlpha(255);
        }
    }

    private void drawSwatchGlyph(@NonNull Canvas canvas, @NonNull Block block, @NonNull RectF swatch,
                                 int color) {
        if (block == Block.ALPHABETS_ROW) {
            mTextPaint.setColor(color);
            mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setTextSize(swatch.height() * 0.5f);
            canvas.drawText("AZ", swatch.centerX(), swatch.centerY() + mTextPaint.getTextSize() * 0.36f,
                mTextPaint);
            return;
        }
        Drawable icon;
        switch (block) {
            case STATUS_BAR: icon = mIconStatus; break;
            case APPS_ROW: icon = mIconApps; break;
            case EXTRA_KEYS: icon = mIconKeys; break;
            case CANVAS:
            default:
                switch (canvasKind()) {
                    case HOME_GRID: icon = mIconHomeGrid; break;
                    case DISPLAY: icon = mIconDisplay; break;
                    case TERMINAL:
                    default: icon = mIconTerminal; break;
                }
                break;
        }
        float size = swatch.height() * 0.62f;
        int left = Math.round(swatch.centerX() - size / 2f);
        int top = Math.round(swatch.centerY() - size / 2f);
        DrawableCompat.setTint(icon, color);
        icon.setBounds(left, top, left + Math.round(size), top + Math.round(size));
        icon.draw(canvas);
    }

    // ---- Grips, slots, tray and the lifted copy ------------------------------------------------

    /** Keeps a band's own content clear of the grip riding its trailing end. */
    private void shrinkForGrip(@NonNull RectF local, @NonNull Block bar, boolean vertical) {
        if (!mGripRects.containsKey(bar)) return;
        float room = dp(GRIP_LENGTH_DP) + dp(GRIP_END_GAP_DP) * 2f;
        if (!vertical && isRtl()) {
            local.left = Math.min(local.right, local.left + room);
        } else {
            local.right = Math.max(local.left, local.right - room);
        }
    }

    private void drawGrips(@NonNull Canvas canvas) {
        for (Block bar : BARS) {
            RectF band = mBlockRects.get(bar);
            RectF grip = mGripRects.get(bar);
            // The lifted bar's grip travels on the copy under the finger instead.
            if (band == null || grip == null || band.isEmpty() || mDraggedBar == bar) continue;
            drawGrip(canvas, grip, bandOnFill(bar), isBarVertical(bar));
        }
    }

    /**
     * The grip glyph: two columns of three dots, turned into three columns of two along a bar that
     * stands as a column, in the bar's own on-colour at reduced alpha.
     */
    private void drawGrip(@NonNull Canvas canvas, @NonNull RectF grip, int color, boolean vertical) {
        int columns = vertical ? 3 : 2;
        int rows = vertical ? 2 : 3;
        float radius = Math.max(dp(0.55f),
            Math.min(grip.width() / (columns * 3f), grip.height() / (rows * 3f)));
        mFillPaint.setColor(color);
        mFillPaint.setAlpha(GRIP_ALPHA);
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                canvas.drawCircle(grip.left + grip.width() * (c + 0.5f) / columns,
                    grip.top + grip.height() * (r + 0.5f) / rows, radius, mFillPaint);
            }
        }
        mFillPaint.setAlpha(255);
    }

    /** Every edge the lifted bar may stand on, outlined; the one under the finger filled. */
    private void drawSlots(@NonNull Canvas canvas) {
        if (mSlots.isEmpty()) return;
        int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        float radius = dp(4);
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.isTray()) continue; // the tray stands under the phone and draws itself
            mScratchRectA.set(slot.left, slot.top, slot.right, slot.bottom);
            if (slot == mHoverSlot) {
                mFillPaint.setColor(accent);
                mFillPaint.setAlpha(SLOT_HOVER_ALPHA);
                canvas.drawRoundRect(mScratchRectA, radius, radius, mFillPaint);
                mFillPaint.setAlpha(255);
            }
            mDashPaint.setColor(accent);
            canvas.drawRoundRect(mScratchRectA, radius, radius, mDashPaint);
        }
    }

    /** Whether the tray is one of the lifted bar's legal targets. */
    private boolean isTrayOffered() {
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.isTray()) return true;
        }
        return false;
    }

    /**
     * The strip under the phone: the hidden bars' chips at rest, and the way to put a bar away
     * while one is lifted. Nothing is drawn while it is empty and no bar is in the air, and the
     * status bar — which never hides — is not offered it at all.
     */
    private void drawTray(@NonNull Canvas canvas) {
        if (mLayout == null || mTrayRect.isEmpty() || mDraggedBar == Block.STATUS_BAR) return;
        float radius = dp(8);
        if (isTrayOffered()) {
            int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                R.color.termux_primary);
            boolean hovered = mHoverSlot != null && mHoverSlot.isTray();
            if (hovered) {
                mFillPaint.setColor(accent);
                mFillPaint.setAlpha(SLOT_HOVER_ALPHA);
                canvas.drawRoundRect(mTrayRect, radius, radius, mFillPaint);
                mFillPaint.setAlpha(255);
            }
            mDashPaint.setColor(accent);
            canvas.drawRoundRect(mTrayRect, radius, radius, mDashPaint);
            mLegendPaint.setTextSize(traySizePx());
            mLegendPaint.setTypeface(Typeface.DEFAULT);
            mLegendPaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                R.color.termux_on_surface_variant));
            String copy = getContext().getString(R.string.settings_layout_drop_to_hide);
            CharSequence shown = TextUtils.ellipsize(copy, mLegendPaint,
                mTrayRect.width() - dp(8), TextUtils.TruncateAt.END);
            float width = mLegendPaint.measureText(shown, 0, shown.length());
            float baseline = mTrayRect.centerY()
                - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;
            canvas.drawText(shown, 0, shown.length(), mTrayRect.centerX() - width / 2f, baseline,
                mLegendPaint);
            return;
        }
        drawTrayChips(canvas);
    }

    /** One chip per hidden bar: its colour, its name and the grip that brings it back. */
    private void drawTrayChips(@NonNull Canvas canvas) {
        if (mTrayChipRects.isEmpty()) return;
        int onSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface);
        mLegendPaint.setTextSize(traySizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        float radius = dp(7);
        for (Map.Entry<Block, RectF> entry : mTrayChipRects.entrySet()) {
            Block bar = entry.getKey();
            RectF chip = entry.getValue();
            if (mDraggedBar == bar) continue; // it is the copy under the finger
            mFillPaint.setColor(bandFill(bar));
            mFillPaint.setAlpha(150);
            canvas.drawRoundRect(chip, radius, radius, mFillPaint);
            mFillPaint.setAlpha(255);

            String name = barName(bar);
            RectF grip = mGripRects.get(bar);
            if (grip != null) drawGrip(canvas, grip, bandOnFill(bar), false);
            if (name == null) continue;
            float textLeft = chip.left + dp(6);
            float textRoom = Math.max(0f, (grip == null ? chip.right : grip.left)
                - dp(4) - textLeft);
            CharSequence shown = TextUtils.ellipsize(name, mLegendPaint, textRoom,
                TextUtils.TruncateAt.END);
            mLegendPaint.setColor(onSurface);
            canvas.drawText(shown, 0, shown.length(), textLeft,
                chip.centerY() - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f,
                mLegendPaint);
        }
    }

    private float traySizePx() {
        float fontScale = getResources().getConfiguration().fontScale;
        if (fontScale <= 0f) fontScale = 1f;
        return TRAY_TEXT_SP * getResources().getDisplayMetrics().density
            * Math.min(fontScale, LEGEND_MAX_FONT_SCALE);
    }

    /** The lifted bar, slightly enlarged and semi-transparent, wherever the finger has taken it. */
    private void drawGhost(@NonNull Canvas canvas) {
        if (mDraggedBar == null || mLiftOrigin.isEmpty()) return;
        float scale = mGhostScale.value;
        float cx = mLiftOrigin.centerX() + mGhostX.value;
        float cy = mLiftOrigin.centerY() + mGhostY.value;
        float halfWidth = mLiftOrigin.width() / 2f * scale;
        float halfHeight = mLiftOrigin.height() / 2f * scale;
        mScratchRectB.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
        mFillPaint.setColor(bandFill(mDraggedBar));
        mFillPaint.setAlpha(GHOST_ALPHA);
        canvas.drawRoundRect(mScratchRectB, dp(4), dp(4), mFillPaint);
        mFillPaint.setAlpha(255);
        boolean vertical = mScratchRectB.height() > mScratchRectB.width();
        gripInto(mScratchRectB, vertical, mGhostGripRect);
        drawGrip(canvas, mGhostGripRect, bandOnFill(mDraggedBar), vertical);
    }

    // ---- The drag ------------------------------------------------------------------------------

    /**
     * The grip a finger is on, within a slop of it, or null. The nearest wins so two grips that
     * end up close together on a small picture still resolve to one.
     */
    @Nullable
    private Block gripAt(float x, float y) {
        float slop = dp(GRIP_TOUCH_SLOP_DP);
        Block best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Map.Entry<Block, RectF> entry : mGripRects.entrySet()) {
            RectF grip = entry.getValue();
            if (x < grip.left - slop || x > grip.right + slop
                || y < grip.top - slop || y > grip.bottom + slop) continue;
            float dx = grip.centerX() - x;
            float dy = grip.centerY() - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Lifts a bar with the finger already down on its grip. Refuses a bar the arrangement leaves
     * nowhere to go, so a lift never starts a gesture that cannot end anywhere.
     */
    private boolean beginDrag(@NonNull Block bar, float x, float y) {
        if (mLayout == null || barOf(bar) == null) return false;
        RectF origin = mBlockRects.get(bar);
        if (origin == null) origin = mTrayChipRects.get(bar);
        if (origin == null || origin.isEmpty()) return false;
        mDraggedBar = bar;
        computeSlots();
        if (mSlots.isEmpty()) {
            mDraggedBar = null;
            return false;
        }
        mLiftOrigin.set(origin);
        mDownX = x;
        mDownY = y;
        mGhostX.reset(0f);
        mGhostY.reset(0f);
        mGhostScale.reset(1f);
        mGhostScale.target = GHOST_SCALE;
        mSpringingBack = false;
        // The preference list must not take the touch for the rest of the gesture, however far
        // the finger travels off the picture.
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        mHoverSlot = MiniatureDragPolicy.slotUnder(mSlots, x, y);
        startMotion();
        invalidate();
        return true;
    }

    private void moveDrag(float x, float y) {
        mGhostX.reset(x - mDownX);
        mGhostY.reset(y - mDownY);
        mHoverSlot = MiniatureDragPolicy.slotUnder(mSlots, x, y);
        invalidate();
    }

    /** Over a target, the new placement is reported at once; anywhere else the bar goes back. */
    private void releaseDrag() {
        Block bar = mDraggedBar;
        MiniatureDragPolicy.Slot slot = mHoverSlot;
        if (bar == null) return;
        if (slot == null) {
            springBack();
            return;
        }
        endDrag();
        if (mDropListener != null) mDropListener.onBarDropped(bar, slot.edge);
    }

    private void springBack() {
        if (mDraggedBar == null) return;
        mSpringingBack = true;
        mHoverSlot = null;
        mSlots.clear();
        mGhostX.target = 0f;
        mGhostY.target = 0f;
        mGhostScale.target = 1f;
        startMotion();
        invalidate();
    }

    private void endDrag() {
        boolean lifted = mDraggedBar != null;
        mDraggedBar = null;
        mSpringingBack = false;
        mSlots.clear();
        mHoverSlot = null;
        mLiftOrigin.setEmpty();
        mGhostX.reset(0f);
        mGhostY.reset(0f);
        mGhostScale.reset(1f);
        mLastFrameNanos = 0L;
        removeCallbacks(mMotionTick);
        if (lifted) invalidate();
    }

    private void startMotion() {
        if (mLastFrameNanos == 0L) mLastFrameNanos = System.nanoTime();
        removeCallbacks(mMotionTick);
        postOnAnimation(mMotionTick);
    }

    /**
     * The lift's swell and a release's spring-back, on the shared integrator. Reduced motion snaps
     * both, which ends the gesture on the next frame instead of animating it home.
     */
    private void advanceMotion() {
        if (mDraggedBar == null) return;
        long now = System.nanoTime();
        float dt = Spring.clampDelta((now - mLastFrameNanos) / 1_000_000_000f);
        mLastFrameNanos = now;
        boolean reduced = isReducedMotion();
        boolean moving = mGhostScale.tick(reduced, dt);
        if (mSpringingBack) {
            moving |= mGhostX.tick(reduced, dt);
            moving |= mGhostY.tick(reduced, dt);
        }
        invalidate();
        if (moving) {
            postOnAnimation(mMotionTick);
            return;
        }
        mLastFrameNanos = 0L;
        if (mSpringingBack) endDrag();
    }

    private boolean isReducedMotion() {
        return Settings.Global.getFloat(getContext().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    // ---- Shared drawing helpers --------------------------------------------------------------

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

    /**
     * A touch-down on a grip lifts that bar at once and the gesture belongs to the drag from
     * there. A touch-down anywhere else is the tap it has always been, decided on the way up, and
     * the preference list keeps its own scroll.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Block grip = gripAt(event.getX(), event.getY());
                if (grip != null) beginDrag(grip, event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (mDraggedBar != null && !mSpringingBack) moveDrag(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (mDraggedBar != null) {
                    releaseDrag();
                    return true;
                }
                Block tapped = blockAt(event.getX(), event.getY());
                if (tapped != null && mListener != null) mListener.onBlockTapped(tapped);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mDraggedBar != null) springBack();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private Block blockAt(float x, float y) {
        // A tray chip is the bar it names, so a hidden bar's chooser is a tap away as well as a
        // drag: nothing on this picture is reachable only by dragging.
        for (Map.Entry<Block, RectF> entry : mTrayChipRects.entrySet()) {
            if (entry.getValue().contains(x, y)) return entry.getKey();
        }
        // A legend row is the block it names — and a hidden block's only tap target.
        for (Block block : LEGEND_ORDER) {
            RectF rect = mLegendRects.get(block);
            if (rect != null && rect.contains(x, y)) return block;
        }
        // Smaller, more specific blocks first, so a corner where two strips meet resolves to the
        // narrower one (the alphabets row rides a thin strip between two wider ones).
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
