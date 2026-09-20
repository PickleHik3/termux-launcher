package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
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
     * Reports a bar dropped on a legal target: the edge it now stands on and the gap in that
     * edge's stack it landed in, 0 outermost, or {@code null} and {@code -1} for the tray, which
     * is the same as hidden. Nothing is reported for a release that landed nowhere.
     */
    public interface OnBarDroppedListener {
        void onBarDropped(@NonNull Block bar, @Nullable Edge edge, int index);
    }

    /** Legend order, top to bottom: the way the rows stack on a default portrait screen. */
    private static final Block[] LEGEND_ORDER = {
        Block.STATUS_BAR, Block.CANVAS, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS};

    /** The bands with a placement to change, in the order the tray lists them. */
    private static final Block[] BARS = {
        Block.STATUS_BAR, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS};

    /** The order the edges claim their strips in; see {@link #computeBlocks}. */
    private static final Edge[] CLAIM_ORDER = {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT};

    /** Portrait: narrow and tall; landscape: wide and short — a phone silhouette either way. */
    private static final float PORTRAIT_ASPECT = 9f / 19.5f;
    private static final float LANDSCAPE_ASPECT = 19.5f / 9f;
    private static final float DEFAULT_HEIGHT_DP = 188f;

    private static final String[] ALPHABETS_SAMPLE = {"A", "F", "M", "S", "Z"};
    /**
     * Sampled from the launcher's own default extra-keys row
     * ({@code TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS}: the keyboard toggle, the
     * session browser, and the three wall pages) — a preview illustrates the shape of the row, not
     * whatever the user has actually edited it to.
     */
    private static final String[] EXTRA_KEY_GLYPHS =
        {"󰥻", "󰉹", "", "", ""};
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

    // ---- The screen's own tones ----------------------------------------------------------------
    // The miniature shows what the screen shows, so it borrows the screen's palette rather than a
    // colour per role: every strip is the same dark glass, and the accent is spent only where the
    // screen itself spends it — the clock, the open app, the letters, the pane's rim.
    /** A strip's glass: the surface laid over the canvas, near-opaque the way the real ones are. */
    private static final int BAND_GLASS_ALPHA = 217;
    /** How far the top of the canvas is lifted off the surface, toward the ink. */
    private static final float CANVAS_LIFT = 0.06f;
    /** The phone's own edge: a hairline, not an outline. */
    private static final float FRAME_RADIUS_DP = 18f;
    private static final float FRAME_STROKE_DP = 1f;
    private static final int FRAME_STROKE_ALPHA = 41;
    /** Accent on a band, at the weight the screen wears it. */
    private static final int ACCENT_INK_ALPHA = 217;
    /** The dock's other icons: present, not competing with the open one. */
    private static final int DOCK_ICON_ALPHA = 71;
    /** A widget tile, a window, a key plane: ink laid thinly over the canvas. */
    private static final int TILE_ALPHA = 36;
    private static final int TILE_EDGE_ALPHA = 92;
    /** The terminal pane: a translucent well inside a thin accent rim. */
    private static final int PANE_FILL_ALPHA = 150;
    private static final float PANE_RIM_DP = 1.2f;
    private static final float PANE_RADIUS_DP = 10f;
    /** The shelf under the phone at rest, and the edge its chips carry. */
    private static final int TRAY_RIM_ALPHA = 56;
    private static final float TRAY_RADIUS_DP = 10f;

    /** The strip under the phone: the hidden bars live there, and a lifted bar can be put there. */
    private static final float TRAY_HEIGHT_DP = 34f;
    private static final float TRAY_GAP_DP = 6f;
    private static final float TRAY_TEXT_SP = 10f;
    private static final float GRIP_LENGTH_DP = 11f;
    private static final float GRIP_THICKNESS_DP = 6f;
    private static final float GRIP_END_GAP_DP = 3f;
    /** How far past the glyph a finger still counts as being on the grip. */
    private static final float GRIP_TOUCH_SLOP_DP = 7f;
    /** The drag dots are a handle, so they wear the accent rather than the band's own ink. */
    private static final int GRIP_ALPHA = 140;
    private static final int GHOST_ALPHA = 190;
    private static final int LIFTED_BAND_ALPHA = 70;
    private static final int SLOT_HOVER_ALPHA = 60;
    /** The gaps of the hovered edge that the finger is not on; enough to read, not to compete. */
    private static final int GAP_LINE_ALPHA = 110;
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
    /** The canvas behind the strips: a soft vertical gradient off the surface, rebuilt with it. */
    private final Paint mSurfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    /** The hit rectangle {@link #gripTouchInto} builds for whichever grip is being tested. */
    private final RectF mScratchGripTouch = new RectF();
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
        mFramePaint.setStrokeWidth(dp(FRAME_STROKE_DP));
        mFramePaint.setColor(ink(FRAME_STROKE_ALPHA));
        mSurfacePaint.setStyle(Paint.Style.FILL);
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

    /**
     * The outermost gap offered on one edge while a bar is lifted, or null when that edge is
     * offered none.
     */
    @Nullable
    @VisibleForTesting
    public MiniatureDragPolicy.Slot slotFor(@NonNull Edge edge) {
        return slotFor(edge, 0);
    }

    /** One gap of one edge, counted from the screen edge inwards; null while it is not offered. */
    @Nullable
    @VisibleForTesting
    public MiniatureDragPolicy.Slot slotFor(@NonNull Edge edge, int index) {
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.edge == edge && slot.index == index) return slot;
        }
        return null;
    }

    /** The gap the finger is over, or null while it is over none; what a release would write. */
    @Nullable
    @VisibleForTesting
    public MiniatureDragPolicy.Slot hoveredSlot() {
        return mHoverSlot;
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
        Element element = elementOf(block);
        return mLayout != null && element != null && !EdgeStackPolicy.isShown(mLayout, element);
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

    /**
     * The phone frame's width over its height, for one orientation. The Layout editor sizes its
     * canvas from it, so the frame it asks for is the frame this view would draw.
     */
    public static float frameAspect(@NonNull PlaceOrientation orientation) {
        return orientation == PlaceOrientation.LANDSCAPE ? LANDSCAPE_ASPECT : PORTRAIT_ASPECT;
    }

    /**
     * The height the view spends on everything that is not the phone: its own padding and the
     * tray's room, which is kept whether or not anything is in it. A caller sizing the view to a
     * frame adds this to the frame height it wants.
     */
    public float reservedHeightPx() {
        return 2 * dp(4) + dp(TRAY_HEIGHT_DP) + dp(TRAY_GAP_DP);
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
        // The tray takes the view's width, not the phone's: a portrait phone is too narrow for
        // "Drop here to hide" or for two chips to keep their names, and the tray reads as a shelf
        // under the phone either way.
        mTrayRect.set(pad, trayTop, viewWidth - pad, trayTop + dp(TRAY_HEIGHT_DP));
        buildSurfaceShader();

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
        // One loop over the model instead of a hand-written running order: each edge is a stack,
        // outermost first, and a band claims its share of whatever the bands outside it left.
        // The edges are claimed in the order the screen itself does: the rows take the whole width
        // first — top, then bottom, which is what puts a bottom status bar above the dock rather
        // than under it — and the side columns then stand in what is left between them, flanking
        // the canvas and nothing else, the way P4 made the screen do it. Claimed the other way a
        // column ran the height of the phone, past the dock and into its corner, and took the
        // grip that lifts it down there with it.
        for (Edge edge : CLAIM_ORDER) {
            for (Element element : EdgeStackPolicy.stack(mLayout, edge))
                takeEdgeStrip(blockOf(element), edge, MiniatureDragPolicy.bandFraction(element));
        }
        mBlockRects.put(Block.CANVAS, new RectF(mRemaining));
        computeContent();
        computeGrips();
        computeTrayChips();
        computeSlots();
        updateContentDescription();
    }

    /** The model's name for one of the miniature's bands, or null for a band with no placement. */
    @Nullable
    private static Element elementOf(@NonNull Block block) {
        switch (block) {
            case STATUS_BAR: return Element.STATUS;
            case APPS_ROW: return Element.APPS;
            case ALPHABETS_ROW: return Element.AZ;
            case EXTRA_KEYS: return Element.EXTRA_KEYS;
            case CANVAS:
            default: return null;
        }
    }

    /** The band one element is drawn as. */
    @NonNull
    private static Block blockOf(@NonNull Element element) {
        switch (element) {
            case STATUS: return Block.STATUS_BAR;
            case APPS: return Block.APPS_ROW;
            case AZ: return Block.ALPHABETS_ROW;
            case EXTRA_KEYS:
            default: return Block.EXTRA_KEYS;
        }
    }

    /**
     * The edge a band is drawn on. The alphabets index riding the pinned apps row has no edge of
     * its own — it goes wherever that row goes — which is the model's answer, not a case here.
     */
    @NonNull
    private Edge edgeOfBlock(@NonNull Block block) {
        Element element = elementOf(block);
        if (mLayout == null || element == null) return Edge.BOTTOM;
        return EdgeStackPolicy.edgeOf(mLayout, element);
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
        return mLayout != null && elementOf(bar) != null && edgeOfBlock(bar).isOnSide();
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
     * Where the lifted bar may land: every gap in every edge's stack, and the tray under the phone
     * for a bar that may hide. An edge with nothing on it offers the one gap it has; an edge
     * carrying bands offers the gap outside the outermost, one between each pair, and one against
     * the canvas — so a drop says which band the lifted bar lands above as well as which edge.
     *
     * <p>The gaps of one edge cover it end to end: each reaches halfway to its neighbours, and the
     * innermost reaches a band's thickness into the canvas, so there is no dead strip between two
     * of them for a finger to fall into.
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
            for (Edge edge : Edge.values())
                addEdgeSlots(edge, targets.gapsOn(edge), free, bar.element());
        }
        if (targets.tray && !mTrayRect.isEmpty()) {
            mSlots.add(new MiniatureDragPolicy.Slot(null, -1, 0f, mTrayRect.left, mTrayRect.top,
                mTrayRect.right, mTrayRect.bottom));
        }
    }

    /** One slot per gap this edge offers, laid along it from the screen edge inwards. */
    private void addEdgeSlots(@NonNull Edge edge, int gaps, @NonNull RectF free,
                              @NonNull Element dragged) {
        if (gaps <= 0 || mLayout == null) return;
        float[] gapDepths = gapDepths(edge, gaps, free, dragged);
        float span = edge.isOnSide() ? free.width() : free.height();
        float thickness = Math.max(dp(9),
            Math.min(span * MiniatureDragPolicy.bandFraction(dragged), span * 0.45f));
        float limit = frameDepth(edge);
        for (int index = 0; index < gaps; index++) {
            float from = index == 0 ? 0f : (gapDepths[index - 1] + gapDepths[index]) / 2f;
            float to = index == gaps - 1
                ? gapDepths[index] + thickness
                : (gapDepths[index] + gapDepths[index + 1]) / 2f;
            to = Math.min(limit, Math.max(to, from + dp(2)));
            mSlots.add(new MiniatureDragPolicy.Slot(edge, index, coordinateAt(edge,
                gapDepths[index]), slotLeft(edge, free, from, to), slotTop(edge, free, from, to),
                slotRight(edge, free, from, to), slotBottom(edge, free, from, to)));
        }
    }

    /**
     * Where each of an edge's gaps sits, as a distance in from the screen edge: outside the
     * outermost band, between each pair, and inside the innermost. The lifted bar's own band is
     * not one of them — it is the thing being moved — and an edge that ends up bare has its one
     * gap where the canvas starts.
     */
    @NonNull
    private float[] gapDepths(@NonNull Edge edge, int gaps, @NonNull RectF free,
                              @NonNull Element dragged) {
        float[] depths = new float[gaps];
        int at = 0;
        RectF last = null;
        for (Element element : EdgeStackPolicy.stack(mLayout, edge)) {
            if (element == dragged) continue;
            RectF band = mBlockRects.get(blockOf(element));
            if (band == null || band.isEmpty()) continue;
            if (at < gaps) depths[at++] = depthOf(edge, band, true);
            last = band;
        }
        if (at < gaps) {
            depths[at++] = last == null ? depthOf(edge, free, true) : depthOf(edge, last, false);
        }
        // A band too thin to have been drawn leaves its gap on top of the one inside it.
        for (int rest = at; rest < gaps; rest++) depths[rest] = depths[rest - 1];
        return depths;
    }

    /** How far in from the screen edge one of a rectangle's sides stands. */
    private float depthOf(@NonNull Edge edge, @NonNull RectF rect, boolean outerSide) {
        switch (edge) {
            case TOP: return (outerSide ? rect.top : rect.bottom) - mFrameRect.top;
            case BOTTOM: return mFrameRect.bottom - (outerSide ? rect.bottom : rect.top);
            case LEFT: return (outerSide ? rect.left : rect.right) - mFrameRect.left;
            case RIGHT:
            default: return mFrameRect.right - (outerSide ? rect.right : rect.left);
        }
    }

    /** The whole frame, measured the same way, so nothing is laid out past the phone. */
    private float frameDepth(@NonNull Edge edge) {
        return edge.isOnSide() ? mFrameRect.width() : mFrameRect.height();
    }

    /** The view coordinate a depth stands at: a y on a row's edge, an x on a column's. */
    private float coordinateAt(@NonNull Edge edge, float depth) {
        switch (edge) {
            case TOP: return mFrameRect.top + depth;
            case BOTTOM: return mFrameRect.bottom - depth;
            case LEFT: return mFrameRect.left + depth;
            case RIGHT:
            default: return mFrameRect.right - depth;
        }
    }

    // A slot spans its two depths across the edge, and the canvas's own width or height along it,
    // so two edges overlap at a corner exactly as far as they always have.
    private float slotLeft(@NonNull Edge edge, @NonNull RectF free, float from, float to) {
        if (edge == Edge.LEFT) return coordinateAt(edge, from);
        if (edge == Edge.RIGHT) return coordinateAt(edge, to);
        return free.left;
    }

    private float slotRight(@NonNull Edge edge, @NonNull RectF free, float from, float to) {
        if (edge == Edge.LEFT) return coordinateAt(edge, to);
        if (edge == Edge.RIGHT) return coordinateAt(edge, from);
        return free.right;
    }

    private float slotTop(@NonNull Edge edge, @NonNull RectF free, float from, float to) {
        if (edge == Edge.TOP) return coordinateAt(edge, from);
        if (edge == Edge.BOTTOM) return coordinateAt(edge, to);
        return free.top;
    }

    private float slotBottom(@NonNull Edge edge, @NonNull RectF free, float from, float to) {
        if (edge == Edge.TOP) return coordinateAt(edge, to);
        if (edge == Edge.BOTTOM) return coordinateAt(edge, from);
        return free.bottom;
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
        return getContext().getString(LayoutChooserModel.edgeLabel(edgeOfBlock(bar)));
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
        int surface = hostColor(host, com.termux.shared.R.attr.termuxColorSurfaceBase,
            R.color.termux_surface_base);
        // The canvas is the screen itself; a strip is the same surface laid over it as glass.
        if (block == Block.CANVAS) return surface;
        return ColorUtils.setAlphaComponent(surface, BAND_GLASS_ALPHA);
    }

    @ColorInt
    private static int hostColor(@NonNull Context host, @AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(host, attr, ContextCompat.getColor(host, fallbackColorRes));
    }

    /** What is written on a band: one ink for every strip, the way the screen writes on glass. */
    @ColorInt
    private int bandOnFill(@NonNull Block block) {
        if (block == Block.CANVAS) {
            return themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                R.color.termux_on_surface_variant);
        }
        return surfaceInk();
    }

    /** The screen's ink. */
    @ColorInt
    private int surfaceInk() {
        return themeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
    }

    /** The screen's ink at the given alpha, for the thin fills and hairlines drawn over it. */
    @ColorInt
    private int ink(int alpha) {
        return ColorUtils.setAlphaComponent(surfaceInk(), alpha);
    }

    @ColorInt
    private int accent() {
        return themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
    }

    /** The accent as it lands on a band: the clock, the open app's icon, the letters. */
    @ColorInt
    private int accentInk() {
        return ColorUtils.setAlphaComponent(accent(), ACCENT_INK_ALPHA);
    }

    @ColorInt
    private int surfaceBase() {
        return themeColor(com.termux.shared.R.attr.termuxColorSurfaceBase,
            R.color.termux_surface_base);
    }

    /** The phone's corner radius; the tray and the legend read it so they round the same way. */
    @VisibleForTesting
    float frameRadiusPx() {
        return dp(FRAME_RADIUS_DP);
    }

    @VisibleForTesting
    float frameStrokePx() {
        return mFramePaint.getStrokeWidth();
    }

    /**
     * The canvas gradient: the surface at the bottom, lifted a little toward the ink at the top,
     * so the screen behind the strips has depth without being a picture of anything.
     */
    private void buildSurfaceShader() {
        if (mFrameRect.isEmpty()) {
            mSurfacePaint.setShader(null);
            return;
        }
        int base = surfaceBase();
        int lifted = ColorUtils.blendARGB(base, surfaceInk(), CANVAS_LIFT);
        mSurfacePaint.setColor(base);
        mSurfacePaint.setShader(new LinearGradient(0f, mFrameRect.top, 0f, mFrameRect.bottom,
            lifted, base, Shader.TileMode.CLAMP));
    }

    // ---- Drawing -------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null || mFrameRect.isEmpty()) return;
        float radius = frameRadiusPx();
        canvas.drawRoundRect(mFrameRect, radius, radius, mSurfacePaint);

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

        mFramePaint.setColor(ink(FRAME_STROKE_ALPHA));
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

        boolean vertical = isBarVertical(Block.STATUS_BAR);
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.STATUS_BAR, vertical);
        drawClockAndDots(canvas, mScratchRectA, bandOnFill(Block.STATUS_BAR), accentInk());
        endBandOrientation(canvas, saved);
    }

    private void drawClockAndDots(@NonNull Canvas canvas, @NonNull RectF local, int color,
                                  int accent) {
        mTextPaint.setColor(accent);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        float textSize = Math.min(dp(8), Math.max(dp(5), local.height() * 0.55f));
        mTextPaint.setTextSize(textSize);
        canvas.drawText("12:40", local.left + dp(5), local.centerY() + textSize * 0.32f, mTextPaint);

        mFillPaint.setColor(ColorUtils.setAlphaComponent(color, 150));
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
        int active = accentInk();
        mFillPaint.setColor(bandFill(Block.APPS_ROW));
        canvas.drawRect(rect, mFillPaint);

        boolean vertical = isBarVertical(Block.APPS_ROW);
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
            // Only the app that is open wears the accent; the rest are ink at a whisper.
            mFillPaint.setColor(i == APPS_ROW_ACTIVE_INDEX ? activeColor
                : ColorUtils.setAlphaComponent(color, DOCK_ICON_ALPHA));
            canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
        }
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
        boolean vertical = isBarVertical(Block.ALPHABETS_ROW);
        int saved = beginBandOrientation(canvas, rect, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, Block.ALPHABETS_ROW, vertical);
        drawAlphabetLetters(canvas, mScratchRectA, accentInk());
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

        boolean vertical = isBarVertical(Block.EXTRA_KEYS);
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
        // The frame's gradient is the screen; the canvas only draws what stands on it.
        int onVariant = bandOnFill(Block.CANVAS);
        int accent = accent();

        switch (canvasKind()) {
            case HOME_GRID:
                drawHomeGrid(canvas, rect);
                break;
            case DISPLAY:
                drawDisplayCanvas(canvas, rect);
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

        // The pane on the screen is a translucent well behind a thin accent rim.
        float paneRadius = dp(PANE_RADIUS_DP);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(surfaceBase(), PANE_FILL_ALPHA));
        canvas.drawRoundRect(card, paneRadius, paneRadius, mFillPaint);
        float rimStroke = mLinePaint.getStrokeWidth();
        mLinePaint.setStrokeWidth(dp(PANE_RIM_DP));
        mLinePaint.setColor(accent);
        canvas.drawRoundRect(card, paneRadius, paneRadius, mLinePaint);
        mLinePaint.setStrokeWidth(rimStroke);
        // Inside the rim, never past it: on a squeezed canvas the inset takes what is there.
        float wellInset = Math.min(dp(5), Math.min(card.width(), card.height()) / 4f);
        card.inset(wellInset, wellInset);

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

    private void drawHomeGrid(@NonNull Canvas canvas, @NonNull RectF rect) {
        if (mLayout == null) return;
        float pad = dp(8);
        RectF area = mScratchRectA;
        area.set(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad);
        if (mGridCollapsed) {
            mFillPaint.setColor(ink(TILE_ALPHA));
            canvas.drawRoundRect(area, dp(6), dp(6), mFillPaint);
            return; // the "n×m" figure is in the legend, so nothing else is written here
        }
        int columns = Math.max(1, mLayout.widgetColumns);
        int rows = Math.max(1, mLayout.widgetRows);
        float cellGap = dp(3);
        float cellW = (area.width() - cellGap * (columns - 1)) / columns;
        float cellH = (area.height() - cellGap * (rows - 1)) / rows;
        // Widgets are tiles on the screen, not wireframes: a thin wash of ink, rounded.
        mFillPaint.setColor(ink(TILE_ALPHA));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                float left = area.left + c * (cellW + cellGap);
                float top = area.top + r * (cellH + cellGap);
                mScratchRectB.set(left, top, left + cellW, top + cellH);
                canvas.drawRoundRect(mScratchRectB, dp(3), dp(3), mFillPaint);
            }
        }
    }

    private void drawDisplayCanvas(@NonNull Canvas canvas, @NonNull RectF rect) {
        if (mLayout == null) return;
        float pad = dp(9);
        RectF desk = mScratchRectA;
        desk.set(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad);

        float winInsetX = desk.width() * 0.16f;
        float winTop = desk.top + desk.height() * 0.10f;
        float winBottom = desk.bottom - desk.height() * 0.22f;
        RectF window = mScratchRectB;
        window.set(desk.left + winInsetX, winTop, desk.right - winInsetX, winBottom);
        // A window on the display is a lit surface, the same wash the home tiles use.
        mFillPaint.setColor(ink(TILE_ALPHA));
        canvas.drawRoundRect(window, dp(4), dp(4), mFillPaint);

        mFillPaint.setColor(ink(TILE_EDGE_ALPHA));
        RectF titlebar = mScratchRectA;
        titlebar.set(window.left, window.top, window.right,
            window.top + Math.min(dp(5), window.height() * 0.2f));
        canvas.drawRoundRect(titlebar, dp(3), dp(3), mFillPaint);

        if (mLayout.keyboardMode == KeyboardMode.OVERLAY) {
            drawFloatingKeyGrid(canvas, rect);
        }
    }

    private void drawFloatingKeyGrid(@NonNull Canvas canvas, @NonNull RectF rect) {
        float pad = dp(6);
        float height = Math.min(rect.height() * 0.34f, dp(30));
        RectF keys = mScratchRectB;
        keys.set(rect.left + pad, rect.bottom - height - pad, rect.right - pad, rect.bottom - pad);
        // The floating keyboard is glass over the display, with its keys as tiles on it.
        mFillPaint.setColor(ColorUtils.setAlphaComponent(surfaceBase(), BAND_GLASS_ALPHA));
        canvas.drawRoundRect(keys, dp(4), dp(4), mFillPaint);

        float cellW = keys.width() / DISPLAY_KEY_GRID_COLUMNS;
        float cellH = keys.height() / DISPLAY_KEY_GRID_ROWS;
        mFillPaint.setColor(ink(TILE_ALPHA));
        for (int r = 0; r < DISPLAY_KEY_GRID_ROWS; r++) {
            for (int c = 0; c < DISPLAY_KEY_GRID_COLUMNS; c++) {
                float left = keys.left + c * cellW;
                float top = keys.top + r * cellH;
                mScratchRectA.set(left + dp(1), top + dp(1), left + cellW - dp(1), top + cellH - dp(1));
                canvas.drawRoundRect(mScratchRectA, dp(1.5f), dp(1.5f), mFillPaint);
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
                // The bands share one glass tone, so every swatch takes the hairline the canvas
                // used to take alone; its glyph is what tells one row from the next.
                mFillPaint.setColor(bandFill(block));
                canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mFillPaint);
                mLinePaint.setColor(outline);
                canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mLinePaint);
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
            drawGrip(canvas, grip, accent(), isBarVertical(bar));
        }
    }

    /**
     * The grip glyph: two columns of three dots, turned into three columns of two along a bar that
     * stands as a column, in the accent at reduced alpha so it reads as something to take hold of.
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

    /**
     * Every edge the lifted bar may stand on, outlined as one region the way it always has been,
     * and — on the edge under the finger — the gaps inside it: a thin line where each would put the
     * band, the one being dropped into filled and drawn solid. Nothing here animates; the picture
     * changes when the finger moves to another gap and not otherwise.
     */
    private void drawSlots(@NonNull Canvas canvas) {
        if (mSlots.isEmpty()) return;
        int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        float radius = dp(4);
        Edge hovered = mHoverSlot == null ? null : mHoverSlot.edge;
        for (Edge edge : Edge.values()) {
            if (!edgeRegion(edge, mScratchRectA)) continue;
            mDashPaint.setColor(accent);
            canvas.drawRoundRect(mScratchRectA, radius, radius, mDashPaint);
        }
        if (hovered == null) return;
        float stroke = mLinePaint.getStrokeWidth();
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.edge != hovered) continue;
            boolean under = slot == mHoverSlot;
            if (under) {
                mScratchRectB.set(slot.left, slot.top, slot.right, slot.bottom);
                mFillPaint.setColor(accent);
                mFillPaint.setAlpha(SLOT_HOVER_ALPHA);
                canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
                mFillPaint.setAlpha(255);
            }
            mLinePaint.setColor(accent);
            mLinePaint.setAlpha(under ? 255 : GAP_LINE_ALPHA);
            mLinePaint.setStrokeWidth(dp(under ? 2f : 1.2f));
            if (hovered.isOnSide())
                canvas.drawLine(slot.line, slot.top, slot.line, slot.bottom, mLinePaint);
            else canvas.drawLine(slot.left, slot.line, slot.right, slot.line, mLinePaint);
        }
        mLinePaint.setAlpha(255);
        mLinePaint.setStrokeWidth(stroke);
    }

    /** One edge's gaps as the single region they cover, or false while the edge offers none. */
    private boolean edgeRegion(@NonNull Edge edge, @NonNull RectF out) {
        boolean any = false;
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.edge != edge) continue;
            if (!any) out.set(slot.left, slot.top, slot.right, slot.bottom);
            else out.union(slot.left, slot.top, slot.right, slot.bottom);
            any = true;
        }
        return any;
    }

    /** Whether the tray is one of the lifted bar's legal targets. */
    private boolean isTrayOffered() {
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.isTray()) return true;
        }
        return false;
    }

    /** What the shelf under the phone is showing; null while it is not drawn at all. */
    @VisibleForTesting
    enum TrayState { OFFERING, CHIPS, EMPTY }

    /**
     * The shelf's state: a lifted bar may be dropped on it, it holds chips for the bars that are
     * put away, or it is resting and empty. Null while there is nothing to draw — no arrangement,
     * no room, or the status bar in the air, since that one never hides.
     */
    @VisibleForTesting
    @Nullable
    TrayState trayState() {
        if (mLayout == null || mTrayRect.isEmpty() || mDraggedBar == Block.STATUS_BAR) return null;
        if (isTrayOffered()) return TrayState.OFFERING;
        return mTrayChipRects.isEmpty() ? TrayState.EMPTY : TrayState.CHIPS;
    }

    /**
     * The strip under the phone: always there, so the place a bar goes when it is put away is
     * visible before anything is dragged. At rest it is a dashed outline with one word in it; it
     * fills with chips as bars are hidden, and its rim turns to the accent while a bar is in the
     * air and may be dropped on it.
     */
    private void drawTray(@NonNull Canvas canvas) {
        TrayState state = trayState();
        if (state == null) return;
        float radius = dp(TRAY_RADIUS_DP);
        boolean offering = state == TrayState.OFFERING;
        if (offering && mHoverSlot != null && mHoverSlot.isTray()) {
            mFillPaint.setColor(accent());
            mFillPaint.setAlpha(SLOT_HOVER_ALPHA);
            canvas.drawRoundRect(mTrayRect, radius, radius, mFillPaint);
            mFillPaint.setAlpha(255);
        }
        mDashPaint.setColor(offering ? accent() : ink(TRAY_RIM_ALPHA));
        canvas.drawRoundRect(mTrayRect, radius, radius, mDashPaint);
        if (state == TrayState.CHIPS) {
            drawTrayChips(canvas);
            return;
        }
        drawTrayLabel(canvas, getContext().getString(offering
            ? R.string.settings_layout_drop_to_hide : R.string.settings_layout_tray_empty));
    }

    /** The shelf's one word, centred in it. */
    private void drawTrayLabel(@NonNull Canvas canvas, @NonNull String copy) {
        mLegendPaint.setTextSize(traySizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        mLegendPaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        CharSequence shown = TextUtils.ellipsize(copy, mLegendPaint,
            mTrayRect.width() - dp(8), TextUtils.TruncateAt.END);
        float width = mLegendPaint.measureText(shown, 0, shown.length());
        float baseline = mTrayRect.centerY()
            - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;
        canvas.drawText(shown, 0, shown.length(), mTrayRect.centerX() - width / 2f, baseline,
            mLegendPaint);
    }

    /** One chip per hidden bar: the same glass as its band, its name and the grip that brings
     *  it back. */
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
            canvas.drawRoundRect(chip, radius, radius, mFillPaint);
            mLinePaint.setColor(ink(FRAME_STROKE_ALPHA));
            canvas.drawRoundRect(chip, radius, radius, mLinePaint);

            String name = barName(bar);
            RectF grip = mGripRects.get(bar);
            if (grip != null) drawGrip(canvas, grip, accent(), false);
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
        // The copy is the same glass as the band it came from, so an accent hairline is what tells
        // the eye it is the thing in the air.
        mLinePaint.setColor(accentInk());
        canvas.drawRoundRect(mScratchRectB, dp(4), dp(4), mLinePaint);
        boolean vertical = mScratchRectB.height() > mScratchRectB.width();
        gripInto(mScratchRectB, vertical, mGhostGripRect);
        drawGrip(canvas, mGhostGripRect, accent(), vertical);
    }

    // ---- The drag ------------------------------------------------------------------------------

    /**
     * The grip a finger is on, within a slop of it, or null. The nearest wins so two grips that
     * end up close together on a small picture still resolve to one.
     */
    @Nullable
    private Block gripAt(float x, float y) {
        Block best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Map.Entry<Block, RectF> entry : mGripRects.entrySet()) {
            RectF grip = entry.getValue();
            gripTouchInto(entry.getKey(), grip, mScratchGripTouch);
            if (!mScratchGripTouch.contains(x, y)) continue;
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
     * What a finger has to land on to lift this bar: the grip glyph with the touch slop around it,
     * widened across the whole thickness of the band it rides. A column the picture draws is a few
     * dp wide, so a grip drawn to fit inside it is far narrower than a fingertip — this makes the
     * end of the band the target, whichever way the band stands, while the rest of it stays a tap.
     */
    private void gripTouchInto(@NonNull Block bar, @NonNull RectF grip, @NonNull RectF out) {
        float slop = dp(GRIP_TOUCH_SLOP_DP);
        out.set(grip.left - slop, grip.top - slop, grip.right + slop, grip.bottom + slop);
        RectF band = mBlockRects.get(bar);
        if (band == null || band.isEmpty()) return;
        // Across the band: the wider of the glyph's slop and the band itself, so a thin column is
        // still a fingertip wide. Along it: never past the band's own ends, or the slop would reach
        // into the band standing next to it and take its taps.
        if (isBarVertical(bar)) {
            out.left = Math.min(out.left, band.left);
            out.right = Math.max(out.right, band.right);
            out.top = Math.max(out.top, band.top);
            out.bottom = Math.min(out.bottom, band.bottom);
            return;
        }
        out.top = Math.min(out.top, band.top);
        out.bottom = Math.max(out.bottom, band.bottom);
        out.left = Math.max(out.left, band.left);
        out.right = Math.min(out.right, band.right);
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
        if (mDropListener != null) {
            mDropListener.onBarDropped(bar, slot.edge, slot.isTray() ? -1 : slot.index);
        }
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
