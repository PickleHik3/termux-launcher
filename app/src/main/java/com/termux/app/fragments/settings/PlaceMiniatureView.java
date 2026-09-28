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
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
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
 * The phone is a small rendering of the launcher's own chrome as Material surfaces — an outlined
 * pane, a status strip with its clock and readings, the pinned apps as a row of discs, the
 * alphabets index as a strip of letters, the extra keys as a row of glyph slots, and the keyboard
 * as its block of keys with the grabber pill at its top — each one a rounded card inside the
 * phone, tinted from the app's own theme attrs so it reads correctly in every theme. Nothing is
 * written over the bands: each one is named in the legend by a swatch of its colour, its glyph and
 * its word, and a row the arrangement leaves out is still listed there, dimmed and marked hidden,
 * so the picture never silently drops one. Tapping a band or its legend row reports the block, so
 * the settings page can scroll to its row.
 *
 * <p>The picture is also the editor. Every band with a placement carries a six-dot grip at its
 * trailing end, centred across it (a column's at its top); a touch-down on one lifts that bar at
 * once — no long press — and for the rest of the gesture the view keeps the preference list from
 * stealing the touch. While a bar is lifted every edge it may legally stand on is outlined, the
 * tray under the phone offers to put it away, and a release over either reports the new placement.
 * A release anywhere else springs the bar back and reports nothing. {@link MiniatureDragPolicy} owns which targets exist and which one
 * the finger is over.
 *
 * <p>The artwork is drawn from {@code project-docs/layout-editor/miniature-material}: its
 * geometry is in units of a 240-wide phone, and {@link #unitPx} is what one of those comes to on
 * the frame this view is drawing, so the same picture holds at every size the editor gives it.
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

        /**
         * The same, told which side of the keyboard a bottom gap was on: {@code underKeyboard} is
         * the far one, where {@code index} counts from the phone's bottom up to the keyboard. A
         * listener that does not tell the sides apart hears every drop as over it.
         */
        default void onBarDropped(@NonNull Block bar, @Nullable Edge edge, int index,
                                  boolean underKeyboard) {
            onBarDropped(bar, edge, index);
        }
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
        {"󰥻", "󰉹", "", "", ""};
    private static final int APPS_ROW_ICON_COUNT = 5;
    private static final int APPS_ROW_ACTIVE_INDEX = APPS_ROW_ICON_COUNT - 1;

    private static final float LEGEND_TEXT_SP = 12f;
    private static final float LEGEND_MAX_FONT_SCALE = 1.3f;
    private static final float LEGEND_SWATCH_DP = 20f;
    private static final float LEGEND_SWATCH_GAP_DP = 8f;
    private static final float LEGEND_ROW_GAP_DP = 6f;
    private static final float LEGEND_TO_FRAME_GAP_DP = 18f;
    /** The legend never claims more than this share of the width; longer labels are ellipsized. */
    private static final float LEGEND_MAX_WIDTH_FRACTION = 0.52f;
    private static final int HIDDEN_ALPHA = 110;

    // ---- The design's geometry, in units of a 240-wide phone ----------------------------------
    /** The phone's short side, which is what one unit is a 240th of. */
    private static final float PHONE_SHORT_SIDE_UNITS = 240f;
    /** The phone's own edge: a rounded card with a hairline rim. */
    private static final float FRAME_RADIUS_U = 24f;
    private static final float FRAME_STROKE_U = 1.5f;
    /** What the phone keeps between its rim and the elements: sides, top, and the chin. */
    private static final float FRAME_INSET_SIDE_U = 12f;
    private static final float FRAME_INSET_TOP_U = 8f;
    private static final float FRAME_INSET_BOTTOM_U = 16f;
    /** Half the gap between two elements, taken off every block on every side. */
    private static final float CARD_PAD_U = 3f;
    /** The elements' corner radii. */
    private static final float PANE_RADIUS_U = 20f;
    /**
     * The dock's corner until the editor says otherwise: the follow-the-style radius a Floating
     * dock ships with. Every band and the keyboard are rounded from this one figure, scaled to the
     * picture ({@link #surfaceRadiusUnits}); each card then stops at a true half-capsule of its own.
     */
    public static final float DEFAULT_DOCK_RADIUS_DP =
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
            .resolveAutoCornerRadiusDp(com.termux.shared.termux.settings.preferences
                .TermuxAppSharedPreferences.SurfaceSlot.DOCK, true);
    /** The short side a phone is taken to have when the view has no screen to ask: 411dp. */
    private static final float REFERENCE_SHORT_SIDE_DP = 411f;
    /** The hairline every resting card wears, and the heavier one a lifted card does. */
    private static final float CARD_STROKE_U = 1f;
    private static final float LIFTED_STROKE_U = 1.5f;
    /** How far the lifted card's solid underlay sits below it: the tonal raise. */
    private static final float LIFT_OFFSET_U = 4f;
    /**
     * The six-dot grip: two columns of three, from its centre. Big enough to find before a finger
     * goes looking for it — the dots were 1.6 units and read as dust on a thin band.
     */
    private static final float GRIP_DOT_RADIUS_U = 2.2f;
    private static final float GRIP_PITCH_X_U = 6.5f;
    private static final float GRIP_PITCH_Y_U = 5.5f;
    /** How far apart two dots of a squeezed column stay, in dot radii, so they never merge. */
    private static final float GRIP_MIN_PITCH_RADII = 2.4f;
    /** The grip's centre from the card's trailing edge; across a row it is always the middle. */
    private static final float GRIP_INSET_END_U = 13f;
    /** A column's grip: its centre down from the column's top. */
    private static final float GRIP_INSET_TOP_U = 16f;
    /** What a band's own content keeps clear of the grip at its trailing end. */
    private static final float GRIP_CLEARANCE_U = 22f;
    /** The share of the phone's content height the keyboard block takes when it is on. */
    private static final float KEYBOARD_FRACTION = 0.26f;
    /** The keyboard's design viewport, which its keys are laid out in and scaled from. */
    private static final float KEYBOARD_DESIGN_W = 216f;
    private static final float KEYBOARD_DESIGN_H = 128f;
    /** The design heights of the rows, which their content is scaled against. */
    private static final float STATUS_DESIGN_H = 32f;
    private static final float APPS_DESIGN_H = 48f;
    private static final float ALPHABETS_DESIGN_H = 36f;
    private static final float EXTRA_KEYS_DESIGN_H = 56f;
    /**
     * The minimised index's pull tab ({@code alphabets_tab}): 48 &times; 28 with the design's
     * radius, laid over the pane at the leading end of its edge, {@value #TAB_EDGE_GAP_U} in from
     * the pane's own edge, the way {@code miniature_portrait_terminal} has it.
     */
    private static final float TAB_LENGTH_U = 48f;
    private static final float TAB_THICKNESS_U = 28f;
    private static final float TAB_EDGE_GAP_U = 8f;

    // ---- The theme's roles, as the design maps them ---------------------------------------------
    /** Outlines and text lines: the on-surface-variant at the design's 45%. */
    private static final int DIM_ALPHA = 115;
    /** A resting grip: the on-surface-variant at 85%, so it reads on the thinnest band. */
    private static final int GRIP_ALPHA = 217;
    /** Text and glyphs that have to read at the real size: the same ink, less faded. */
    private static final int TEXT_ALPHA = 205;
    /** The tonal layer a lifted card or an active tray wears: the accent at 9%. */
    private static final int ACCENT_WASH_ALPHA = 23;
    /** A widget tile: the container tone laid thinly over the pane. */
    private static final int TILE_ALPHA = 140;
    /** The keyboard's tinted glass over its own card. */
    private static final int KEYBOARD_TINT_ALPHA = 153;
    /** The placeholder a lifted bar leaves where it stood. */
    private static final int PLACEHOLDER_ALPHA = 60;
    private static final int SLOT_HOVER_ALPHA = 60;
    /** The gaps of the hovered edge that the finger is not on; enough to read, not to compete. */
    private static final int GAP_LINE_ALPHA = 110;

    /** The strip under the phone: the hidden bars live there, and a lifted bar can be put there. */
    private static final float TRAY_HEIGHT_DP = 40f;
    private static final float TRAY_GAP_DP = 12f;
    private static final float TRAY_RADIUS_DP = 12f;
    private static final float TRAY_STROKE_DP = 1.25f;
    private static final float TRAY_DASH_DP = 4f;
    private static final float TRAY_TEXT_SP = 11f;
    /** Half the platform's minimum target: what a grip is hit-tested with around its centre. */
    private static final float GRIP_TOUCH_HALF_DP = 24f;
    private static final float GHOST_SCALE = 1.06f;
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
    /** The fine dash the edge outlines wear, and the coarser one the tray's drop zone does. */
    private final DashPathEffect mSlotDash;
    private final DashPathEffect mTrayDash;
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNerdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mLegendPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFrameRect = new RectF();
    /** The phone inside its rim and its chin: what the elements are laid out in. */
    private final RectF mContentRect = new RectF();
    /** One design unit on this frame, in pixels. */
    private float mUnit = 1f;
    /** The one corner radius every band and the keyboard share, in design units. */
    private float mSurfaceRadiusU;
    private final Path mClipPath = new Path();
    /** The floating keyboard's card, or a split keyboard's halves and the whole they cut. */
    private final RectF mKeyboardPartA = new RectF();
    private final RectF mKeyboardPartB = new RectF();
    private final RectF mKeyboardWhole = new RectF();
    /** Reused scratch rects for whatever a draw call is computing right now; never read across
     *  two different shapes, only within one draw-then-move-on sequence. */
    private final RectF mScratchRectA = new RectF();
    private final RectF mScratchRectB = new RectF();
    private final RectF mScratchRectC = new RectF();
    /** The hit rectangle {@link #gripTouchInto} builds for whichever grip is being tested. */
    private final RectF mScratchGripTouch = new RectF();
    private final Map<Block, RectF> mBlockRects = new EnumMap<>(Block.class);
    private final Map<Block, RectF> mLegendRects = new EnumMap<>(Block.class);
    /** The keyboard's block along the bottom while the place shows it; empty otherwise. */
    private final RectF mKeyboardRect = new RectF();
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
    /** Where the lifted copy is drawn this frame; its own rect, since the card drawing scribbles
     *  on the shared scratch rects. */
    private final RectF mGhostRect = new RectF();
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
        mFillPaint.setStyle(Paint.Style.FILL);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setStrokeWidth(dp(1f));
        mSlotDash = new DashPathEffect(new float[]{dp(2f), dp(2f)}, 0f);
        mTrayDash = new DashPathEffect(new float[]{dp(TRAY_DASH_DP), dp(TRAY_DASH_DP)}, 0f);
        mDashPaint.setPathEffect(mSlotDash);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(dp(1f));
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
        mSurfaceRadiusU = surfaceRadiusUnits(DEFAULT_DOCK_RADIUS_DP, screenShortSideDp());
    }

    /**
     * The dock's corner radius, in dp, that the bands and the keyboard are rounded from. The
     * picture is the phone at {@value #PHONE_SHORT_SIDE_UNITS} units across its short side, so the
     * real radius is scaled by that against the real screen's short side: the cards corner as the
     * surfaces do, one system, rather than each band a number of its own.
     */
    public void setDockCornerRadiusDp(float radiusDp) {
        float units = surfaceRadiusUnits(radiusDp, screenShortSideDp());
        if (units == mSurfaceRadiusU) return;
        mSurfaceRadiusU = units;
        invalidate();
    }

    /**
     * A real radius, in dp, in the picture's units: the phone's short side is
     * {@value #PHONE_SHORT_SIDE_UNITS} of them, the screen's is {@code screenShortSideDp}.
     */
    @VisibleForTesting
    public static float surfaceRadiusUnits(float radiusDp, float screenShortSideDp) {
        float side = screenShortSideDp > 0f ? screenShortSideDp : REFERENCE_SHORT_SIDE_DP;
        return Math.max(0f, radiusDp) * PHONE_SHORT_SIDE_UNITS / side;
    }

    /**
     * The radius a card of this size is drawn at: the shared radius, no more than a true
     * half-capsule of its shorter side. A thin band is then a capsule and a tall one — the
     * keyboard — keeps the whole radius, which is what the real surfaces do.
     */
    @VisibleForTesting
    public static float cardRadiusPx(float surfaceRadiusPx, float cardWidthPx, float cardHeightPx) {
        float half = Math.max(0f, Math.min(cardWidthPx, cardHeightPx)) / 2f;
        return Math.max(0f, Math.min(surfaceRadiusPx, half));
    }

    /** The shared radius in view pixels, at this frame's unit. */
    @VisibleForTesting
    public float surfaceRadiusPx() {
        return u(mSurfaceRadiusU);
    }

    private float cardRadius(@NonNull RectF card) {
        return cardRadiusPx(surfaceRadiusPx(), card.width(), card.height());
    }

    private float screenShortSideDp() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (metrics.density <= 0f) return REFERENCE_SHORT_SIDE_DP;
        return Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density;
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
            if (slot.edge == edge && slot.index == index && !slot.underKeyboard) return slot;
        }
        return null;
    }

    /** One gap under the keyboard, counted from the phone's bottom up; null while not offered. */
    @Nullable
    @VisibleForTesting
    public MiniatureDragPolicy.Slot underKeyboardSlotFor(int index) {
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.underKeyboard && slot.index == index) return slot;
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

    /**
     * The keyboard's block along the bottom of the phone, in view pixels, or null while the place
     * does not show one: the keyboard element off, Home (where it opens over the page), or the
     * display with a keyboard that floats.
     */
    @Nullable
    @VisibleForTesting
    public RectF keyboardRect() {
        return mKeyboardRect.isEmpty() ? null : new RectF(mKeyboardRect);
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
        // One design unit: the phone's short side is 240 of them, whichever way it stands.
        mUnit = Math.max(0.01f, Math.min(frameWidth, frameHeight) / PHONE_SHORT_SIDE_UNITS);
        // The elements stand inside the rim and above the chin, the way the artwork lays them.
        boolean landscape = mOrientation == PlaceOrientation.LANDSCAPE;
        mContentRect.set(mFrameRect.left + u(FRAME_INSET_SIDE_U),
            mFrameRect.top + u(FRAME_INSET_TOP_U),
            mFrameRect.right - u(FRAME_INSET_SIDE_U),
            mFrameRect.bottom - u(landscape ? FRAME_INSET_TOP_U : FRAME_INSET_BOTTOM_U));
        if (mContentRect.width() < 0f || mContentRect.height() < 0f) mContentRect.set(mFrameRect);
        float trayTop = mFrameRect.bottom + dp(TRAY_GAP_DP);
        // The tray takes the view's width, not the phone's: a portrait phone is too narrow for
        // "Drop here to hide" or for two chips to keep their names, and the tray reads as a shelf
        // under the phone either way.
        mTrayRect.set(pad, trayTop, viewWidth - pad, trayTop + dp(TRAY_HEIGHT_DP));

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
        mKeyboardRect.setEmpty();
        mRemaining = new RectF(mContentRect);
        if (mLayout == null) {
            mGridCollapsed = false;
            mSlots.clear();
            return;
        }
        // The bands under the keyboard are the bottom's outermost, so they claim first; with no
        // keyboard drawn they are simply the bottom stack's outer end, as on the screen.
        List<Element> underKeyboard = EdgeStackPolicy.underKeyboard(mLayout);
        for (Element element : underKeyboard) {
            takeEdgeStrip(blockOf(element), Edge.BOTTOM,
                MiniatureDragPolicy.bandFraction(element));
        }
        // The keyboard has no edge and no order: when the place shows it, it is the block along
        // the bottom that everything else — the dock's rows included, lifted above it the way the
        // real dock is — stands above.
        if (showsKeyboard()) takeKeyboardStrip();
        // One loop over the model instead of a hand-written running order: each edge is a stack,
        // outermost first, and a band claims its share of whatever the bands outside it left.
        // The edges are claimed in the order the screen itself does: the rows take the whole width
        // first — top, then bottom, which is what puts a bottom status bar above the dock rather
        // than under it — and the side columns then stand in what is left between them, flanking
        // the canvas and nothing else, the way P4 made the screen do it. Claimed the other way a
        // column ran the height of the phone, past the dock and into its corner, and took the
        // grip that lifts it down there with it.
        for (Edge edge : CLAIM_ORDER) {
            for (Element element : EdgeStackPolicy.stack(mLayout, edge)) {
                if (underKeyboard.contains(element)) continue;
                takeEdgeStrip(blockOf(element), edge, MiniatureDragPolicy.bandFraction(element));
            }
        }
        mBlockRects.put(Block.CANVAS, new RectF(mRemaining));
        // The minimised index claims no band: its tab is laid over the canvas once the canvas is
        // known, and it is a block like any other from here, so it carries a grip and lifts.
        if (isAzTab()) mBlockRects.put(Block.ALPHABETS_ROW, tabBlock(mRemaining));
        computeContent();
        computeGrips();
        computeTrayChips();
        computeSlots();
        updateContentDescription();
    }

    /**
     * Whether the picture shows the keyboard as a block of the phone: only while the keyboard
     * element is on, and only on a place whose content it actually shrinks — the terminal, and
     * the display with a keyboard that resizes it. Home's keyboard opens over the page, and a
     * floating one over the display is drawn on the pane instead.
     */
    private boolean showsKeyboard() {
        if (mLayout == null || !mLayout.keyboardShown) return false;
        switch (mPlace) {
            case TERMINAL: return true;
            case DISPLAY: return mLayout.keyboardMode == KeyboardMode.RESIZE;
            case WIDGETS:
            default: return false;
        }
    }

    /** The keyboard's block: its share of the content's height, off the bottom, before any edge. */
    private void takeKeyboardStrip() {
        float h = mRemaining.height() * KEYBOARD_FRACTION;
        mKeyboardRect.set(mRemaining.left, mRemaining.bottom - h, mRemaining.right,
            mRemaining.bottom);
        mRemaining.bottom -= h;
    }

    /** Whether the arrangement stands the A–Z index minimised, as its pull tab over the pane. */
    @VisibleForTesting
    public boolean isAzTab() {
        return mLayout != null && PlaceChromePolicy.azTabShown(mLayout);
    }

    /**
     * The tab's block on the canvas: the pill and the card padding around it, at the leading end
     * of the index's edge and {@value #TAB_EDGE_GAP_U} in from the pane's own edge. Along a row
     * the leading end is the left, the right in a right-to-left layout; down a column it is the
     * top. A canvas too small for it shrinks it rather than letting it past the pane.
     */
    @NonNull
    private RectF tabBlock(@NonNull RectF canvasRect) {
        Edge edge = EdgeStackPolicy.edgeOf(mLayout, Element.AZ);
        boolean column = edge.isOnSide();
        float pad = u(CARD_PAD_U);
        float length = Math.min(u(TAB_LENGTH_U) + 2f * pad,
            column ? canvasRect.height() : canvasRect.width());
        float thickness = Math.min(u(TAB_THICKNESS_U) + 2f * pad,
            (column ? canvasRect.width() : canvasRect.height()) / 2f);
        // The pane card is the canvas less the padding; the pill, the block less the same padding,
        // stands the gap in from the card, so the block stands the gap in from the canvas.
        float gap = u(TAB_EDGE_GAP_U);
        RectF rect = new RectF();
        switch (edge) {
            case TOP:
            case BOTTOM: {
                float left = isRtl() ? canvasRect.right - length : canvasRect.left;
                float top = edge == Edge.TOP ? canvasRect.top + gap
                    : canvasRect.bottom - gap - thickness;
                rect.set(left, top, left + length, top + thickness);
                break;
            }
            case LEFT:
            case RIGHT:
            default: {
                float left = edge == Edge.LEFT ? canvasRect.left + gap
                    : canvasRect.right - gap - thickness;
                rect.set(left, canvasRect.top, left + thickness, canvasRect.top + length);
                break;
            }
        }
        return rect;
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

    /** A grip at the trailing end of every band the user may move; the canvas gets none. */
    private void computeGrips() {
        for (Block bar : BARS) {
            RectF band = mBlockRects.get(bar);
            if (band == null || band.isEmpty()) continue;
            mGripRects.put(bar, gripFor(band, isBarVertical(bar)));
        }
    }

    /**
     * The grip glyph's own rectangle: the six dots' extent, in the one spot every row carries it —
     * {@value #GRIP_INSET_END_U} units in from the card's trailing edge, centred across the row —
     * squeezed to the card on a band too thin for the whole column of dots. A column is too
     * narrow for a trailing end, so its grip stands centred across it,
     * {@value #GRIP_INSET_TOP_U} down from its top.
     */
    @NonNull
    private RectF gripFor(@NonNull RectF band, boolean vertical) {
        RectF grip = new RectF();
        gripInto(band, vertical, grip);
        return grip;
    }

    private void gripInto(@NonNull RectF band, boolean vertical, @NonNull RectF out) {
        float pad = u(CARD_PAD_U);
        float halfWidth = u(GRIP_PITCH_X_U) / 2f + u(GRIP_DOT_RADIUS_U);
        float innerHeight = Math.max(0f, band.height() - 2f * pad);
        // The card's own rim and a hair of air stay clear of the dots.
        float halfHeight = gripHalfHeightPx(u(GRIP_PITCH_Y_U) + u(GRIP_DOT_RADIUS_U),
            innerHeight, u(CARD_STROKE_U) + u(1f));
        float cy;
        float cx;
        if (vertical) {
            cy = band.top + pad + Math.min(u(GRIP_INSET_TOP_U), innerHeight / 2f);
            cx = band.centerX();
        } else {
            cy = band.centerY();
            float end = Math.min(u(GRIP_INSET_END_U), Math.max(0f, band.width() - 2f * pad) / 2f);
            cx = isRtl() ? band.left + pad + end : band.right - pad - end;
        }
        out.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
    }

    /** Whether a bar stands as a column rather than a row, which turns its content with it. */
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
        float gap = dp(6);
        float width = (mTrayRect.width() - gap * (hidden.size() - 1)) / hidden.size();
        float inset = dp(4);
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
        List<Block> hidden = new ArrayList<>(4);
        if (mLayout == null) return hidden;
        for (Block bar : BARS) {
            if (isBlockHidden(bar)) hidden.add(bar);
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
            // The gaps under the keyboard, only while there is a keyboard drawn to be under.
            if (!mKeyboardRect.isEmpty())
                addUnderKeyboardSlots(targets.gapsUnderKeyboard(), free, bar.element());
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
        // Along the bottom these are the gaps over the keyboard: they start from its middle, and
        // the half under that is the far side's (addUnderKeyboardSlots).
        boolean overKeyboard = edge == Edge.BOTTOM && !mKeyboardRect.isEmpty();
        List<Element> bands = edge == Edge.BOTTOM
            ? EdgeStackPolicy.overKeyboard(mLayout) : EdgeStackPolicy.stack(mLayout, edge);
        float[] gapDepths = gapDepths(edge, bands, gaps, depthOf(edge, free, true), dragged);
        addSlots(edge, gapDepths, overKeyboard ? keyboardMiddleDepth() : 0f, frameDepth(edge),
            free, dragged, false);
    }

    /**
     * The gaps under the keyboard, from the phone's bottom edge up to the keyboard's middle: one
     * outside the outermost band standing there, one between each pair, one against the keyboard,
     * or the one a bare side has, which is the keyboard's own bottom.
     */
    private void addUnderKeyboardSlots(int gaps, @NonNull RectF free, @NonNull Element dragged) {
        if (gaps <= 0 || mLayout == null) return;
        float[] gapDepths = gapDepths(Edge.BOTTOM, EdgeStackPolicy.underKeyboard(mLayout), gaps,
            depthOf(Edge.BOTTOM, mKeyboardRect, true), dragged);
        addSlots(Edge.BOTTOM, gapDepths, 0f, keyboardMiddleDepth(), free, dragged, true);
    }

    /** How far up from the phone's bottom the keyboard block's middle stands. */
    private float keyboardMiddleDepth() {
        return mFrameRect.bottom - mKeyboardRect.centerY();
    }

    /**
     * One slot per gap, laid from {@code start} inwards: each reaches halfway to its neighbours,
     * the first back to {@code start} and the last a band's thickness past its line, never past
     * {@code limit}.
     */
    private void addSlots(@NonNull Edge edge, @NonNull float[] gapDepths, float start, float limit,
                          @NonNull RectF free, @NonNull Element dragged, boolean underKeyboard) {
        int gaps = gapDepths.length;
        float span = edge.isOnSide() ? free.width() : free.height();
        float thickness = Math.max(dp(9),
            Math.min(span * MiniatureDragPolicy.bandFraction(dragged), span * 0.45f));
        for (int index = 0; index < gaps; index++) {
            float from = index == 0 ? start : (gapDepths[index - 1] + gapDepths[index]) / 2f;
            float to = index == gaps - 1
                ? gapDepths[index] + thickness
                : (gapDepths[index] + gapDepths[index + 1]) / 2f;
            if (underKeyboard && index == gaps - 1) to = limit;
            to = Math.min(limit, Math.max(to, from + dp(2)));
            mSlots.add(new MiniatureDragPolicy.Slot(edge, index, coordinateAt(edge,
                gapDepths[index]), slotLeft(edge, free, from, to), slotTop(edge, free, from, to),
                slotRight(edge, free, from, to), slotBottom(edge, free, from, to),
                underKeyboard));
        }
    }

    /**
     * Where each of a stack's gaps sits, as a distance in from the screen edge: outside the
     * outermost band, between each pair, and inside the innermost. The lifted bar's own band is
     * not one of them — it is the thing being moved — and a stack that ends up bare has its one
     * gap at {@code bareDepth}: where the canvas starts, or under the keyboard its own bottom.
     */
    @NonNull
    private float[] gapDepths(@NonNull Edge edge, @NonNull List<Element> stack, int gaps,
                              float bareDepth, @NonNull Element dragged) {
        float[] depths = new float[gaps];
        int at = 0;
        RectF last = null;
        for (Element element : stack) {
            if (element == dragged) continue;
            RectF band = mBlockRects.get(blockOf(element));
            if (band == null || band.isEmpty()) continue;
            if (at < gaps) depths[at++] = depthOf(edge, band, true);
            last = band;
        }
        if (at < gaps) {
            depths[at++] = last == null ? bareDepth : depthOf(edge, last, false);
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

    /** The word for where a bar stands: its edge, or that it is hidden, or minimised there. */
    @NonNull
    private String barPosition(@NonNull Block bar) {
        if (mLayout == null || isBlockHidden(bar)) {
            return getContext().getString(R.string.settings_layout_row_hidden);
        }
        Element element = elementOf(bar);
        boolean underKeyboard = element != null
            && EdgeStackPolicy.standsUnderKeyboard(mLayout, element)
            && EdgeStackPolicy.claimsBand(mLayout, element);
        String edge = getContext().getString(underKeyboard
            ? R.string.settings_layout_edge_under_keyboard
            : LayoutChooserModel.edgeLabel(edgeOfBlock(bar)));
        if (bar == Block.ALPHABETS_ROW && isAzTab()) {
            return getContext().getString(R.string.settings_layout_miniature_minimised_format,
                edge);
        }
        return edge;
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
        if (block == Block.ALPHABETS_ROW && isAzTab()) {
            return getContext().getString(R.string.settings_layout_miniature_minimised_format,
                label);
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
    // The artwork names four roles and the theme supplies them: the surface for the phone, the
    // pane and the keyboard's own card; the raised container for every strip; the on-surface
    // variant, dimmed for outlines and glyphs and stronger for resting grips; and the accent for
    // what is held, chosen or about to take a drop.

    /** The band's fill; the legend swatch uses the same one so the two are read as one thing. */
    @ColorInt
    private int bandFill(@NonNull Block block) {
        return blockColor(getContext(), block);
    }

    /**
     * The colour a block is drawn in, for anything outside the miniature that has to point at the
     * same band — the Layout page's rows carry a swatch of it beside the element's name. Every
     * strip is the theme's raised container; the canvas is the surface itself.
     */
    @ColorInt
    public static int blockColor(@NonNull Context host, @NonNull Block block) {
        if (block == Block.CANVAS)
            return hostColor(host, com.termux.shared.R.attr.termuxColorSurfaceBase,
                R.color.termux_surface_base);
        return hostColor(host, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high);
    }

    @ColorInt
    private static int hostColor(@NonNull Context host, @AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(host, attr, ContextCompat.getColor(host, fallbackColorRes));
    }

    /** The surface: the phone, the pane, the keyboard's card and a lifted card's underlay. */
    @ColorInt
    private int surface() {
        return themeColor(com.termux.shared.R.attr.termuxColorSurfaceBase,
            R.color.termux_surface_base);
    }

    /** The raised container every strip is made of. */
    @ColorInt
    private int container() {
        return themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high);
    }

    /** The ink the design draws outlines, glyphs and text with, before it is dimmed. */
    @ColorInt
    private int onVariant() {
        return themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
    }

    /** Outlines and key silhouettes: the ink at the design's 45%. */
    @ColorInt
    private int dim() {
        return ColorUtils.setAlphaComponent(onVariant(), DIM_ALPHA);
    }

    /** A resting grip: the ink at readable emphasis, over any band, light or dark. */
    @ColorInt
    private int gripInk() {
        return ColorUtils.setAlphaComponent(onVariant(), GRIP_ALPHA);
    }

    /** Text and glyphs, which have to read at the real size: the ink, a little less faded. */
    @ColorInt
    private int text() {
        return ColorUtils.setAlphaComponent(onVariant(), TEXT_ALPHA);
    }

    /** The screen's own ink, for the legend and the tray's chip names. */
    @ColorInt
    private int surfaceInk() {
        return themeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
    }

    @ColorInt
    private int accent() {
        return themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
    }

    /** The accent as a tonal layer over a container: a lifted card, an active tray. */
    @ColorInt
    private int accentWash() {
        return ColorUtils.setAlphaComponent(accent(), ACCENT_WASH_ALPHA);
    }

    /** The phone's corner radius: the design's, at this frame's unit. */
    @VisibleForTesting
    float frameRadiusPx() {
        return u(FRAME_RADIUS_U);
    }

    /** The phone's rim, at this frame's unit. */
    @VisibleForTesting
    float frameStrokePx() {
        return u(FRAME_STROKE_U);
    }

    /** One design unit on this frame: a 240th of the phone's short side. */
    @VisibleForTesting
    float unitPx() {
        return mUnit;
    }

    // ---- Drawing -------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null || mFrameRect.isEmpty()) return;
        float radius = frameRadiusPx();
        mFillPaint.setColor(surface());
        canvas.drawRoundRect(mFrameRect, radius, radius, mFillPaint);

        // The elements are clipped to the phone's rounded outline, so a lifted card's underlay
        // that reaches the rim follows the curve instead of poking a square corner past it.
        mClipPath.reset();
        mClipPath.addRoundRect(mFrameRect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(mClipPath);
        drawCanvasBlock(canvas);
        drawKeyboardBlock(canvas);
        drawBandBlock(canvas, Block.EXTRA_KEYS);
        drawBandBlock(canvas, Block.APPS_ROW);
        drawBandBlock(canvas, Block.ALPHABETS_ROW);
        drawBandBlock(canvas, Block.STATUS_BAR);
        drawSlots(canvas);
        canvas.restoreToCount(saved);

        mFramePaint.setStrokeWidth(frameStrokePx());
        mFramePaint.setColor(dim());
        canvas.drawRoundRect(mFrameRect, radius, radius, mFramePaint);

        drawTray(canvas);
        drawLegend(canvas);
        drawGhost(canvas);
    }

    /** One design unit, in pixels, for this frame. */
    private float u(float units) {
        return units * mUnit;
    }

    /**
     * The card an element is drawn as, inside the block it claims: the block less half the gap
     * between neighbours on every side, so two adjacent bands read as two cards with air between
     * them rather than one strip with a line across it.
     */
    private void cardOf(@NonNull RectF block, @NonNull RectF out) {
        float pad = Math.min(u(CARD_PAD_U), Math.min(block.width(), block.height()) / 4f);
        out.set(block.left + pad, block.top + pad, block.right - pad, block.bottom - pad);
    }

    /**
     * One band on the phone: its card and its content, or — while it is the bar in the air — the
     * faint placeholder that keeps its place until the drop lands.
     */
    private void drawBandBlock(@NonNull Canvas canvas, @NonNull Block block) {
        RectF rect = mBlockRects.get(block);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        if (mDraggedBar == block) {
            cardOf(rect, mScratchRectA);
            float radius = cardRadius(mScratchRectA);
            mDashPaint.setPathEffect(mSlotDash);
            mDashPaint.setStrokeWidth(dp(1f));
            mDashPaint.setColor(dim());
            mDashPaint.setAlpha(PLACEHOLDER_ALPHA);
            canvas.drawRoundRect(mScratchRectA, radius, radius, mDashPaint);
            return;
        }
        drawBandCard(canvas, block, rect, isBarVertical(block), false);
        RectF grip = mGripRects.get(block);
        if (grip != null) drawGrip(canvas, grip, gripInk());
    }

    /**
     * A band's card at any rectangle — its own block on the phone, or the copy under the finger:
     * the container, the hairline and the content, and the tonal raise around a lifted one.
     *
     * @param lifted whether this is the copy in the air, which wears the accent
     */
    private void drawBandCard(@NonNull Canvas canvas, @NonNull Block block, @NonNull RectF rect,
                              boolean vertical, boolean lifted) {
        RectF card = mScratchRectC;
        cardOf(rect, card);
        float radius = cardRadius(card);
        if (lifted) {
            // The raise: a solid underlay a little below, then the card with a wash of accent.
            mScratchRectB.set(card);
            mScratchRectB.offset(0f, u(LIFT_OFFSET_U));
            mFillPaint.setColor(surface());
            canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
        }
        mFillPaint.setColor(bandFill(block));
        canvas.drawRoundRect(card, radius, radius, mFillPaint);
        if (lifted) {
            mFillPaint.setColor(accentWash());
            canvas.drawRoundRect(card, radius, radius, mFillPaint);
        }
        mLinePaint.setColor(lifted ? accent() : dim());
        mLinePaint.setStrokeWidth(u(lifted ? LIFTED_STROKE_U : CARD_STROKE_U));
        canvas.drawRoundRect(card, radius, radius, mLinePaint);

        if (block == Block.STATUS_BAR && vertical) {
            // The column keeps its digits upright: the hour over the minutes, as the real one.
            drawStatusColumnContent(canvas, card);
            return;
        }
        if (block == Block.ALPHABETS_ROW && isAzTab()) {
            drawAlphabetsTabContent(canvas, card, vertical);
            return;
        }
        int saved = beginBandOrientation(canvas, card, vertical, mScratchRectA);
        shrinkForGrip(mScratchRectA, vertical);
        switch (block) {
            case STATUS_BAR: drawStatusContent(canvas, mScratchRectA); break;
            case APPS_ROW: drawAppsContent(canvas, mScratchRectA); break;
            case ALPHABETS_ROW: drawAlphabetsContent(canvas, mScratchRectA); break;
            case EXTRA_KEYS:
            default: drawExtraKeysContent(canvas, mScratchRectA); break;
        }
        endBandOrientation(canvas, saved);
    }

    /** The content scale for a row: the phone's unit, or less where the row is thinner than
     *  the design drew it, so nothing spills over the card. */
    private float rowScale(@NonNull RectF local, float designHeightUnits) {
        return Math.max(0.01f, Math.min(mUnit, local.height() / designHeightUnits));
    }

    // ---- Status bar --------------------------------------------------------------------------

    /** The clock at the start, the readings — signal and battery — at the end. */
    private void drawStatusContent(@NonNull Canvas canvas, @NonNull RectF local) {
        float k = rowScale(local, STATUS_DESIGN_H);
        float cy = local.centerY();
        mTextPaint.setColor(text());
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(10f * k);
        canvas.drawText("12:40", local.left + 8f * k, cy - (mTextPaint.ascent()
            + mTextPaint.descent()) / 2f, mTextPaint);
        drawStatusReadings(canvas, local.right - 7f * k, cy, k, false);
    }

    /**
     * The signal's three rising bars and the battery, laid out leftward from {@code endX} — or
     * stacked upward from {@code cy} when the bar stands as a column.
     */
    private void drawStatusReadings(@NonNull Canvas canvas, float endX, float cy, float k,
                                    boolean stacked) {
        mFillPaint.setColor(dim());
        float barW = 1.4f * k;
        float barGap = 1.1f * k;
        float batteryW = 7f * k;
        float batteryH = 4f * k;
        if (stacked) {
            // Column: the battery on top, the signal bars under it, both centred on endX.
            mScratchRectB.set(endX - batteryW / 2f, cy - batteryH - 2.5f * k, endX + batteryW / 2f,
                cy - 2.5f * k);
            canvas.drawRoundRect(mScratchRectB, k, k, mFillPaint);
            float x = endX - (3f * barW + 2f * barGap) / 2f;
            float base = cy + 2.5f * k + 5.5f * k;
            for (int i = 0; i < 3; i++) {
                float h = (2.5f + 1.5f * i) * k;
                mScratchRectB.set(x, base - h, x + barW, base);
                canvas.drawRoundRect(mScratchRectB, barW / 2f, barW / 2f, mFillPaint);
                x += barW + barGap;
            }
            return;
        }
        // Row: the battery at the end, its nub outward, and the bars just before it.
        float batteryLeft = endX - batteryW;
        mScratchRectB.set(batteryLeft, cy - batteryH / 2f, endX, cy + batteryH / 2f);
        canvas.drawRoundRect(mScratchRectB, k, k, mFillPaint);
        mScratchRectB.set(endX, cy - k, endX + k, cy + k);
        canvas.drawRect(mScratchRectB, mFillPaint);
        float x = batteryLeft - 4f * k - barW;
        float base = cy + batteryH / 2f + 0.5f * k;
        for (int i = 2; i >= 0; i--) {
            float h = (2.5f + 1.5f * i) * k;
            mScratchRectB.set(x, base - h, x + barW, base);
            canvas.drawRoundRect(mScratchRectB, barW / 2f, barW / 2f, mFillPaint);
            x -= barW + barGap;
        }
    }

    /** A status column: the hour over the minutes at its top, the readings at its foot. */
    private void drawStatusColumnContent(@NonNull Canvas canvas, @NonNull RectF card) {
        float k = Math.max(0.01f, Math.min(mUnit, card.width() / 40f));
        float cx = card.centerX();
        float top = card.top + u(GRIP_CLEARANCE_U) + 4f * k;
        mTextPaint.setColor(text());
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(9f * k);
        float lineH = 10f * k;
        if (top + 2f * lineH < card.bottom - 24f * k) {
            canvas.drawText("12", cx, top + lineH - 2f * k, mTextPaint);
            canvas.drawText("40", cx, top + 2f * lineH - 2f * k, mTextPaint);
        }
        if (card.height() > 40f * k)
            drawStatusReadings(canvas, cx, card.bottom - 12f * k, k, true);
    }

    // ---- Apps row -----------------------------------------------------------------------------

    /** Five discs; the last one wears the accent, because that app is the one that is open. */
    private void drawAppsContent(@NonNull Canvas canvas, @NonNull RectF local) {
        float k = rowScale(local, APPS_DESIGN_H);
        float inset = 6f * k;
        float slot = (local.width() - inset * 2f) / APPS_ROW_ICON_COUNT;
        float radius = Math.min(9f * k, Math.min(slot * 0.42f, local.height() * 0.36f));
        float cy = local.centerY();
        for (int i = 0; i < APPS_ROW_ICON_COUNT; i++) {
            float cx = local.left + inset + slot * (i + 0.5f);
            boolean active = i == APPS_ROW_ACTIVE_INDEX;
            mFillPaint.setColor(active ? accent() : dim());
            canvas.drawCircle(cx, cy, radius, mFillPaint);
            // A mark on each disc, so it reads as an icon rather than a dot: a dash or a square.
            mFillPaint.setColor(surface());
            float mark = radius * 0.5f;
            if (i % 2 == 0) {
                mScratchRectB.set(cx - mark, cy - mark * 0.3f, cx + mark, cy + mark * 0.3f);
                canvas.drawRoundRect(mScratchRectB, mark * 0.3f, mark * 0.3f, mFillPaint);
            } else {
                mScratchRectB.set(cx - mark * 0.7f, cy - mark * 0.7f, cx + mark * 0.7f,
                    cy + mark * 0.7f);
                canvas.drawRoundRect(mScratchRectB, mark * 0.25f, mark * 0.25f, mFillPaint);
            }
        }
    }

    // ---- Alphabets row -------------------------------------------------------------------------

    /** A few letters, spread along the strip. */
    private void drawAlphabetsContent(@NonNull Canvas canvas, @NonNull RectF local) {
        float k = rowScale(local, ALPHABETS_DESIGN_H);
        mTextPaint.setColor(text());
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(Math.min(8f * k, local.height() * 0.6f));
        int n = ALPHABETS_SAMPLE.length;
        float inset = 8f * k;
        float slot = (local.width() - inset * 2f) / n;
        float baseline = local.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        for (int i = 0; i < n; i++) {
            float x = local.left + inset + slot * (i + 0.5f);
            canvas.drawText(ALPHABETS_SAMPLE[i], x, baseline, mTextPaint);
        }
    }

    /**
     * The pull tab: the letter A at its leading end, upright on every edge, clear of the grip at
     * its trailing end — the top of a column's.
     */
    private void drawAlphabetsTabContent(@NonNull Canvas canvas, @NonNull RectF card,
                                         boolean vertical) {
        float across = vertical ? card.width() : card.height();
        float k = Math.max(0.01f, Math.min(mUnit, across / TAB_THICKNESS_U));
        RectF room = mScratchRectA;
        room.set(card);
        if (vertical) {
            room.top += Math.min(u(GRIP_CLEARANCE_U), card.height() / 2f);
        } else {
            shrinkForGrip(room, false);
        }
        mTextPaint.setColor(text());
        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(Math.min(10f * k, across * 0.5f));
        float baseline = room.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        canvas.drawText("A", room.centerX(), baseline, mTextPaint);
        mTextPaint.setTypeface(Typeface.DEFAULT);
    }

    // ---- Extra keys ---------------------------------------------------------------------------

    /** Five glyph slots: a rounded key each, with the key's own glyph on it. */
    private void drawExtraKeysContent(@NonNull Canvas canvas, @NonNull RectF local) {
        float k = rowScale(local, EXTRA_KEYS_DESIGN_H);
        int n = EXTRA_KEY_GLYPHS.length;
        float inset = 8f * k;
        float slot = (local.width() - inset * 2f) / n;
        float size = Math.min(24f * k, Math.min(slot * 0.8f, local.height() * 0.62f));
        float radius = 6f * k;
        float cy = local.centerY();
        for (int i = 0; i < n; i++) {
            float cx = local.left + inset + slot * (i + 0.5f);
            mScratchRectB.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            mFillPaint.setColor(dim());
            canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
            if (mNerdTypeface == null) continue; // a bare-module test environment: no tofu boxes
            mNerdPaint.setColor(surface());
            mNerdPaint.setTextSize(size * 0.62f);
            canvas.drawText(EXTRA_KEY_GLYPHS[i], cx,
                cy - (mNerdPaint.ascent() + mNerdPaint.descent()) / 2f, mNerdPaint);
        }
    }

    // ---- Canvas: Terminal / Home / Display ----------------------------------------------------

    /** The pane: an outlined card on the surface, with the place's own content on it. */
    private void drawCanvasBlock(@NonNull Canvas canvas) {
        RectF rect = mBlockRects.get(Block.CANVAS);
        if (rect == null || rect.isEmpty() || mLayout == null) return;
        RectF pane = mScratchRectC;
        cardOf(rect, pane);
        float radius = u(PANE_RADIUS_U);
        mFillPaint.setColor(surface());
        canvas.drawRoundRect(pane, radius, radius, mFillPaint);
        mLinePaint.setColor(dim());
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        canvas.drawRoundRect(pane, radius, radius, mLinePaint);

        int saved = canvas.save();
        mClipPath.reset();
        mClipPath.addRoundRect(pane, radius, radius, Path.Direction.CW);
        canvas.clipPath(mClipPath);
        switch (canvasKind()) {
            case HOME_GRID:
                drawHomeGrid(canvas, pane);
                break;
            case DISPLAY:
                drawDisplayCanvas(canvas, pane);
                break;
            case TERMINAL:
            default:
                drawTerminalLines(canvas, pane);
                break;
        }
        canvas.restoreToCount(saved);
    }

    /** Rows of short character marks, a prompt and a cursor: text without saying anything. */
    private void drawTerminalLines(@NonNull Canvas canvas, @NonNull RectF pane) {
        float k = mUnit;
        float left = pane.left + 14f * k;
        float right = pane.right - 14f * k;
        float y = pane.top + 20f * k;
        float pitch = 13f * k;
        float charPitch = 7f * k;
        float dashW = 4.5f * k;
        float dashH = Math.max(1f, 1.4f * k);
        int[] lengths = {16, 12, 16, 9, 14, 11};
        mFillPaint.setColor(dim());
        int line = 0;
        while (y + pitch * 2.2f < pane.bottom && line < lengths.length) {
            float x = left;
            for (int c = 0; c < lengths[line] && x + dashW <= right; c++) {
                mScratchRectB.set(x, y, x + dashW, y + dashH);
                canvas.drawRoundRect(mScratchRectB, dashH / 2f, dashH / 2f, mFillPaint);
                x += charPitch;
            }
            y += pitch;
            line++;
        }
        // The prompt and its cursor block sit on the next line, wherever the text stopped.
        if (y + pitch <= pane.bottom) {
            mTextPaint.setColor(text());
            mTextPaint.setTypeface(Typeface.MONOSPACE);
            mTextPaint.setTextAlign(Paint.Align.LEFT);
            mTextPaint.setTextSize(8f * k);
            float baseline = y + 7f * k;
            canvas.drawText(">_", left, baseline, mTextPaint);
            float cursorLeft = left + mTextPaint.measureText(">_ ");
            mFillPaint.setColor(accent());
            mScratchRectB.set(cursorLeft, baseline - 6f * k, cursorLeft + 4f * k, baseline + k);
            canvas.drawRect(mScratchRectB, mFillPaint);
        }
    }

    /**
     * Home's widget grid, as the layout counts it: one faint tile per cell, and on the first two
     * a clock face and a few lines, so the tiles read as widgets rather than as a grid.
     */
    private void drawHomeGrid(@NonNull Canvas canvas, @NonNull RectF pane) {
        if (mLayout == null) return;
        float pad = 10f * mUnit;
        RectF area = mScratchRectA;
        area.set(pane.left + pad, pane.top + pad, pane.right - pad, pane.bottom - pad);
        int tile = ColorUtils.setAlphaComponent(container(), TILE_ALPHA);
        if (mGridCollapsed) {
            mFillPaint.setColor(tile);
            canvas.drawRoundRect(area, u(8f), u(8f), mFillPaint);
            return; // the "n×m" figure is in the legend, so nothing else is written here
        }
        int columns = Math.max(1, mLayout.widgetColumns);
        int rows = Math.max(1, mLayout.widgetRows);
        float cellGap = 6f * mUnit;
        float cellW = (area.width() - cellGap * (columns - 1)) / columns;
        float cellH = (area.height() - cellGap * (rows - 1)) / rows;
        float radius = Math.min(u(10f), Math.min(cellW, cellH) * 0.3f);
        boolean decorated = Math.min(cellW, cellH) >= u(22f);
        float areaLeft = area.left;
        float areaTop = area.top;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                float left = areaLeft + c * (cellW + cellGap);
                float top = areaTop + r * (cellH + cellGap);
                mScratchRectB.set(left, top, left + cellW, top + cellH);
                mFillPaint.setColor(tile);
                canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
                if (!decorated) continue;
                if (r == 0 && c == 0) drawClockFace(canvas, mScratchRectB);
                else if (r == 0 && c == 1) drawTextLines(canvas, mScratchRectB);
            }
        }
    }

    /** A ring and two hands, centred in a tile. */
    private void drawClockFace(@NonNull Canvas canvas, @NonNull RectF tile) {
        float radius = Math.min(tile.width(), tile.height()) * 0.3f;
        float cx = tile.centerX();
        float cy = tile.centerY();
        mLinePaint.setColor(dim());
        mLinePaint.setStrokeWidth(Math.max(1f, radius * 0.12f));
        canvas.drawCircle(cx, cy, radius, mLinePaint);
        canvas.drawLine(cx, cy, cx, cy - radius * 0.62f, mLinePaint);
        canvas.drawLine(cx, cy, cx + radius * 0.45f, cy + radius * 0.2f, mLinePaint);
    }

    /** Three lines of text of falling length, in a tile. */
    private void drawTextLines(@NonNull Canvas canvas, @NonNull RectF tile) {
        float inset = tile.width() * 0.16f;
        float lineH = Math.max(1f, tile.height() * 0.07f);
        float pitch = lineH * 2.6f;
        float y = tile.top + tile.height() * 0.3f;
        float[] widths = {0.68f, 0.52f, 0.6f};
        mFillPaint.setColor(dim());
        for (float w : widths) {
            if (y + lineH > tile.bottom - inset) break;
            canvas.drawRoundRect(tile.left + inset, y, tile.left + inset + tile.width() * w,
                y + lineH, lineH / 2f, lineH / 2f, mFillPaint);
            y += pitch;
        }
    }

    /** The display: a window on the desk, and the floating keyboard over it when it floats. */
    private void drawDisplayCanvas(@NonNull Canvas canvas, @NonNull RectF pane) {
        if (mLayout == null) return;
        float pad = 10f * mUnit;
        RectF desk = mScratchRectA;
        desk.set(pane.left + pad, pane.top + pad, pane.right - pad, pane.bottom - pad);

        float winInsetX = desk.width() * 0.14f;
        float winTop = desk.top + desk.height() * 0.08f;
        float winBottom = desk.bottom - desk.height() * 0.24f;
        RectF window = mScratchRectB;
        window.set(desk.left + winInsetX, winTop, desk.right - winInsetX, winBottom);
        // A window on the display is a raised surface, the same tone the tiles wear.
        mFillPaint.setColor(ColorUtils.setAlphaComponent(container(), TILE_ALPHA));
        float radius = u(6f);
        canvas.drawRoundRect(window, radius, radius, mFillPaint);
        mLinePaint.setColor(dim());
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        canvas.drawRoundRect(window, radius, radius, mLinePaint);
        float barH = Math.min(u(8f), window.height() * 0.2f);
        mFillPaint.setColor(dim());
        mScratchRectA.set(window.left, window.top, window.right, window.top + barH);
        canvas.drawRoundRect(mScratchRectA, radius, radius, mFillPaint);

        if (mLayout.keyboardMode == KeyboardMode.OVERLAY) {
            float inset = 8f * mUnit;
            float height = Math.min(pane.height() * 0.36f, u(60f));
            mScratchRectA.set(pane.left + inset, pane.bottom - height - inset,
                pane.right - inset, pane.bottom - inset);
            drawKeyboardCard(canvas, mScratchRectA);
        }
    }

    // ---- Keyboard -----------------------------------------------------------------------------

    /**
     * The keyboard's block along the bottom of the phone, while the place shows one, drawn in the
     * form the place types with: docked across the block, floating as a smaller card lifted off
     * it, split as two halves with the parting between them. The block itself is the same in all
     * three, so the drop targets around it do not move with the form.
     */
    private void drawKeyboardBlock(@NonNull Canvas canvas) {
        if (mKeyboardRect.isEmpty()) return;
        cardOf(mKeyboardRect, mScratchRectC);
        PlaceLayout.KeyboardForm form = mLayout == null
            ? PlaceLayout.KeyboardForm.DOCKED : mLayout.keyboardForm;
        switch (form) {
            case FLOATING: {
                RectF card = mKeyboardPartA;
                floatingKeyboardCardInto(mScratchRectC, card);
                // The raise a lifted card wears, so it reads as over the place, not in it.
                mScratchRectB.set(card);
                mScratchRectB.offset(0f, u(LIFT_OFFSET_U) / 2f);
                float radius = cardRadius(card);
                mFillPaint.setColor(ColorUtils.setAlphaComponent(onVariant(), PLACEHOLDER_ALPHA));
                canvas.drawRoundRect(mScratchRectB, radius, radius, mFillPaint);
                drawKeyboardCard(canvas, card);
                break;
            }
            case SPLIT: {
                RectF left = mKeyboardPartA;
                RectF right = mKeyboardPartB;
                splitKeyboardHalvesInto(mScratchRectC, left, right);
                // The card pass reuses the scratch rects; the whole keyboard is held apart.
                mKeyboardWhole.set(mScratchRectC);
                drawKeyboardHalf(canvas, mKeyboardWhole, left);
                drawKeyboardHalf(canvas, mKeyboardWhole, right);
                break;
            }
            case DOCKED:
            default:
                drawKeyboardCard(canvas, mScratchRectC);
                break;
        }
    }

    /** The floating keyboard's card: 72% of the block's width and 82% of its height, centred. */
    @VisibleForTesting
    static void floatingKeyboardCardInto(@NonNull RectF block, @NonNull RectF out) {
        float width = block.width() * 0.72f;
        float height = block.height() * 0.82f;
        out.set(block.centerX() - width / 2f, block.centerY() - height / 2f,
            block.centerX() + width / 2f, block.centerY() + height / 2f);
    }

    /** The split keyboard's two halves: 40% of the block's width each, at its two ends. */
    @VisibleForTesting
    static void splitKeyboardHalvesInto(@NonNull RectF block, @NonNull RectF left,
                                        @NonNull RectF right) {
        float half = block.width() * 0.4f;
        left.set(block.left, block.top, block.left + half, block.bottom);
        right.set(block.right - half, block.top, block.right, block.bottom);
    }

    /** One half of a split keyboard: its own card, and the whole keyboard's keys that fall on it. */
    private void drawKeyboardHalf(@NonNull Canvas canvas, @NonNull RectF whole,
                                  @NonNull RectF half) {
        int saved = canvas.save();
        mClipPath.reset();
        float radius = cardRadius(half);
        mClipPath.addRoundRect(half, radius, radius, Path.Direction.CW);
        canvas.clipPath(mClipPath);
        drawKeyboardCard(canvas, whole, half);
        canvas.restoreToCount(saved);
    }

    /**
     * The keyboard as the design draws it: a surface card with a tint of container inside, the
     * grabber pill at its top, and four rows of keys — the space bar wide — laid out in its own
     * 216x128 viewport and scaled to fit whatever card it is given.
     */
    private void drawKeyboardCard(@NonNull Canvas canvas, @NonNull RectF card) {
        drawKeyboardCard(canvas, card, card);
    }

    /**
     * The same, with the keys laid out over {@code keys} and the card drawn at {@code card}: a
     * split half is the half's card with the whole keyboard's keys under its clip.
     */
    private void drawKeyboardCard(@NonNull Canvas canvas, @NonNull RectF keys,
                                  @NonNull RectF card) {
        float radius = cardRadius(card);
        mFillPaint.setColor(surface());
        canvas.drawRoundRect(card, radius, radius, mFillPaint);
        mScratchRectB.set(card);
        mScratchRectB.inset(u(2f), u(2f));
        mFillPaint.setColor(ColorUtils.setAlphaComponent(container(), KEYBOARD_TINT_ALPHA));
        float inner = Math.max(0f, radius - u(2f));
        canvas.drawRoundRect(mScratchRectB, inner, inner, mFillPaint);
        mLinePaint.setColor(dim());
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        canvas.drawRoundRect(card, radius, radius, mLinePaint);

        float k = Math.max(0.01f, Math.min(keys.width() / KEYBOARD_DESIGN_W,
            keys.height() / KEYBOARD_DESIGN_H));
        float x0 = keys.centerX() - KEYBOARD_DESIGN_W * k / 2f;
        float y0 = keys.centerY() - KEYBOARD_DESIGN_H * k / 2f;
        mFillPaint.setColor(dim());
        // The grabber: the one part of the keyboard that is not a key, and not the layout grip.
        mScratchRectB.set(x0 + 90f * k, y0 + 8f * k, x0 + 126f * k, y0 + 11f * k);
        canvas.drawRoundRect(mScratchRectB, 1.5f * k, 1.5f * k, mFillPaint);

        float keyW = 15f * k;
        float keyH = 18f * k;
        float keyR = 4.5f * k;
        float pitch = 18f * k;
        float rowPitch = 23f * k;
        // Row one: ten keys. Row two: nine, stepped in by half a key. Row three: seven between
        // two wide ends. Row four: two small, the space bar, two small.
        drawKeyRow(canvas, x0 + 19.5f * k, y0 + 24f * k, 10, keyW, keyH, keyR, pitch);
        drawKeyRow(canvas, x0 + 28.5f * k, y0 + 24f * k + rowPitch, 9, keyW, keyH, keyR, pitch);
        float y3 = y0 + 24f * k + 2f * rowPitch;
        drawKey(canvas, x0 + 19.5f * k, y3, 24f * k, keyH, keyR);
        drawKeyRow(canvas, x0 + 46.5f * k, y3, 7, keyW, keyH, keyR, pitch);
        drawKey(canvas, x0 + 172.5f * k, y3, 24f * k, keyH, keyR);
        float y4 = y0 + 24f * k + 3f * rowPitch;
        drawKey(canvas, x0 + 19.5f * k, y4, keyW, keyH, keyR);
        drawKey(canvas, x0 + 37.5f * k, y4, keyW, keyH, keyR);
        drawKey(canvas, x0 + 55.5f * k, y4, 105f * k, keyH, keyR);
        drawKey(canvas, x0 + 163.5f * k, y4, keyW, keyH, keyR);
        drawKey(canvas, x0 + 181.5f * k, y4, keyW, keyH, keyR);
    }

    private void drawKeyRow(@NonNull Canvas canvas, float left, float top, int count, float w,
                            float h, float r, float pitch) {
        for (int i = 0; i < count; i++) drawKey(canvas, left + i * pitch, top, w, h, r);
    }

    private void drawKey(@NonNull Canvas canvas, float left, float top, float w, float h, float r) {
        mScratchRectB.set(left, top, left + w, top + h);
        canvas.drawRoundRect(mScratchRectB, r, r, mFillPaint);
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
        int onSurface = surfaceInk();
        int outline = themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant);
        boolean rtl = isRtl();
        mLegendPaint.setTextSize(legendTextSizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        float textBaselineOffset = -(mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;
        mLinePaint.setStrokeWidth(dp(1f));

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
                mLinePaint.setColor(outline);
                canvas.drawRoundRect(mScratchRectA, swatchRadius, swatchRadius, mLinePaint);
            }
            drawSwatchGlyph(canvas, block, mScratchRectA, hidden ? outline : onVariant());

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

    /**
     * Keeps a band's own content clear of the grip at its trailing end. In a row the grip is at
     * the trailing corner; in a column it is at the top, which the rotation maps to the local
     * end for LTR and the local start for RTL — the same side a row's trailing corner lands on.
     */
    private void shrinkForGrip(@NonNull RectF local, boolean vertical) {
        float room = Math.min(u(GRIP_CLEARANCE_U), local.width() / 2f);
        if (isRtl()) local.left += room;
        else local.right -= room;
    }

    /**
     * Half the grip's height on a card with {@code innerHeightPx} of room: the whole column of
     * dots where it fits, and no taller than the card less {@code clearancePx} on each side where
     * it does not — the A–Z band is the one this is for.
     */
    @VisibleForTesting
    static float gripHalfHeightPx(float wantedHalfPx, float innerHeightPx, float clearancePx) {
        float room = Math.max(0f, innerHeightPx / 2f - Math.max(0f, clearancePx));
        return Math.max(0f, Math.min(wantedHalfPx, room));
    }

    /**
     * The dots' radius for a grip {@code halfHeightPx} tall: the design's, or — where the column
     * has been squeezed — small enough that three rows still stand
     * {@value #GRIP_MIN_PITCH_RADII} radii apart. Never under half a pixel.
     */
    @VisibleForTesting
    static float gripDotRadiusPx(float designRadiusPx, float halfHeightPx) {
        float fit = Math.max(0f, halfHeightPx) / (1f + GRIP_MIN_PITCH_RADII);
        return Math.max(0.5f, Math.min(designRadiusPx, fit));
    }

    /**
     * The six dots, filling their rectangle: two columns at its sides, three rows from its top to
     * its bottom. The colour says whether the bar is held.
     */
    private void drawGrip(@NonNull Canvas canvas, @NonNull RectF grip, int color) {
        float cx = grip.centerX();
        float cy = grip.centerY();
        float radius = gripDotRadiusPx(Math.min(u(GRIP_DOT_RADIUS_U), grip.width() / 4f),
            grip.height() / 2f);
        float pitchX = Math.max(0f, grip.width() - 2f * radius);
        float pitchY = Math.max(0f, grip.height() / 2f - radius);
        mFillPaint.setColor(color);
        for (int c = -1; c <= 1; c += 2) {
            for (int r = -1; r <= 1; r++) {
                canvas.drawCircle(cx + c * pitchX / 2f, cy + r * pitchY, radius, mFillPaint);
            }
        }
    }

    /**
     * Every edge the lifted bar may stand on, outlined as one region the way it always has been,
     * and — on the edge under the finger — the gaps inside it: a thin line where each would put the
     * band, the one being dropped into filled and drawn solid. Nothing here animates; the picture
     * changes when the finger moves to another gap and not otherwise.
     */
    private void drawSlots(@NonNull Canvas canvas) {
        if (mSlots.isEmpty()) return;
        int accent = accent();
        float radius = dp(4);
        MiniatureDragPolicy.Slot hoveredSlot = mHoverSlot;
        Edge hovered = hoveredSlot == null ? null : hoveredSlot.edge;
        mDashPaint.setPathEffect(mSlotDash);
        mDashPaint.setStrokeWidth(dp(1f));
        // The bottom is two regions while the keyboard stands on it: the gaps over it, and the
        // ones under it, outlined apart so the keyboard between them is not a target.
        for (Edge edge : Edge.values()) {
            for (boolean underKeyboard : new boolean[] {false, true}) {
                if (!edgeRegion(edge, underKeyboard, mScratchRectA)) continue;
                mDashPaint.setColor(accent);
                canvas.drawRoundRect(mScratchRectA, radius, radius, mDashPaint);
            }
        }
        if (hovered == null) return;
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (!slot.sameGroup(hoveredSlot)) continue;
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
    }

    /**
     * One edge's gaps on one side of the keyboard as the single region they cover, or false while
     * that side offers none. Only the bottom has an under side.
     */
    private boolean edgeRegion(@NonNull Edge edge, boolean underKeyboard, @NonNull RectF out) {
        boolean any = false;
        for (MiniatureDragPolicy.Slot slot : mSlots) {
            if (slot.edge != edge || slot.underKeyboard != underKeyboard) continue;
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
     * or no room.
     */
    @VisibleForTesting
    @Nullable
    TrayState trayState() {
        if (mLayout == null || mTrayRect.isEmpty()) return null;
        if (isTrayOffered()) return TrayState.OFFERING;
        return mTrayChipRects.isEmpty() ? TrayState.EMPTY : TrayState.CHIPS;
    }

    /**
     * The strip under the phone: always there, so the place a bar goes when it is put away is
     * visible before anything is dragged. At rest it is a dashed drop zone with one word in it;
     * it fills with chips as bars are hidden, and while a bar is in the air and may be dropped on
     * it its outline and its word turn to the accent, with a wash of it once the finger is over.
     */
    private void drawTray(@NonNull Canvas canvas) {
        TrayState state = trayState();
        if (state == null) return;
        float radius = dp(TRAY_RADIUS_DP);
        boolean offering = state == TrayState.OFFERING;
        boolean active = offering && mHoverSlot != null && mHoverSlot.isTray();
        if (active) {
            mFillPaint.setColor(accentWash());
            canvas.drawRoundRect(mTrayRect, radius, radius, mFillPaint);
        }
        mDashPaint.setPathEffect(mTrayDash);
        mDashPaint.setColor(offering ? accent() : dim());
        mDashPaint.setStrokeWidth(dp(TRAY_STROKE_DP));
        canvas.drawRoundRect(mTrayRect, radius, radius, mDashPaint);
        if (state == TrayState.CHIPS) {
            drawTrayChips(canvas);
            return;
        }
        drawTrayLabel(canvas, getContext().getString(offering
            ? R.string.settings_layout_drop_to_hide : R.string.settings_layout_tray_empty),
            offering ? accent() : onVariant());
    }

    /** The shelf's one word, centred in it, as real text. */
    private void drawTrayLabel(@NonNull Canvas canvas, @NonNull String copy, int color) {
        mLegendPaint.setTextSize(traySizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        mLegendPaint.setLetterSpacing(0f);
        mLegendPaint.setColor(color);
        CharSequence shown = TextUtils.ellipsize(copy, mLegendPaint,
            mTrayRect.width() - dp(8), TextUtils.TruncateAt.END);
        float width = mLegendPaint.measureText(shown, 0, shown.length());
        float baseline = mTrayRect.centerY()
            - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;
        canvas.drawText(shown, 0, shown.length(), mTrayRect.centerX() - width / 2f, baseline,
            mLegendPaint);
    }

    /** One chip per hidden bar: a container pill with its name and the grip that brings it back. */
    private void drawTrayChips(@NonNull Canvas canvas) {
        if (mTrayChipRects.isEmpty()) return;
        int onSurface = surfaceInk();
        mLegendPaint.setTextSize(traySizePx());
        mLegendPaint.setTypeface(Typeface.DEFAULT);
        mLegendPaint.setLetterSpacing(0f);
        float radius = dp(10);
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        for (Map.Entry<Block, RectF> entry : mTrayChipRects.entrySet()) {
            Block bar = entry.getKey();
            RectF chip = entry.getValue();
            if (mDraggedBar == bar) continue; // it is the copy under the finger
            mFillPaint.setColor(bandFill(bar));
            canvas.drawRoundRect(chip, radius, radius, mFillPaint);
            mLinePaint.setColor(dim());
            canvas.drawRoundRect(chip, radius, radius, mLinePaint);

            String name = barName(bar);
            RectF grip = mGripRects.get(bar);
            if (grip != null) drawGrip(canvas, grip, gripInk());
            if (name == null) continue;
            float textLeft = chip.left + dp(8);
            float textRoom = Math.max(0f, (grip == null ? chip.right : grip.left)
                - dp(6) - textLeft);
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

    /**
     * The lifted bar wherever the finger has taken it: the same card, drawn raised — a solid
     * underlay a little below it, a wash of accent over it, an accent rim and an accent grip.
     */
    private void drawGhost(@NonNull Canvas canvas) {
        if (mDraggedBar == null || mLiftOrigin.isEmpty()) return;
        float scale = mGhostScale.value;
        float cx = mLiftOrigin.centerX() + mGhostX.value;
        float cy = mLiftOrigin.centerY() + mGhostY.value;
        float halfWidth = mLiftOrigin.width() / 2f * scale;
        float halfHeight = mLiftOrigin.height() / 2f * scale;
        mGhostRect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
        boolean vertical = mBlockRects.containsKey(mDraggedBar) && isBarVertical(mDraggedBar);
        drawBandCard(canvas, mDraggedBar, mGhostRect, vertical, true);
        gripInto(mGhostRect, vertical, mGhostGripRect);
        drawGrip(canvas, mGhostGripRect, accent());
    }

    // ---- The drag ------------------------------------------------------------------------------

    /**
     * The grip a finger is on, within its target, or null. The nearest wins so two grips that
     * end up close together on a small picture — three stacked rows are closer than a fingertip
     * — still resolve to one.
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
     * What a finger has to land on to lift this bar: the platform's 48dp target centred on the
     * grip, never scaled down with the artwork. Along the band it stops at the band's own ends,
     * or the target would reach into the band standing next to it and take its taps; across the
     * band it may reach past it — a column the picture draws is a few dp wide, and a grip drawn
     * to fit inside it is far narrower than a fingertip.
     */
    private void gripTouchInto(@NonNull Block bar, @NonNull RectF grip, @NonNull RectF out) {
        float half = dp(GRIP_TOUCH_HALF_DP);
        float cx = grip.centerX();
        float cy = grip.centerY();
        out.set(cx - half, cy - half, cx + half, cy + half);
        RectF band = mBlockRects.get(bar);
        if (band == null) band = mTrayChipRects.get(bar);
        if (band == null || band.isEmpty()) return;
        if (mBlockRects.containsKey(bar) && isBarVertical(bar)) {
            out.top = Math.max(out.top, band.top);
            out.bottom = Math.min(out.bottom, band.bottom);
            return;
        }
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
            mDropListener.onBarDropped(bar, slot.edge, slot.isTray() ? -1 : slot.index,
                slot.underKeyboard);
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
                if (mDraggedBar != null && !mSpringingBack) {
                    // The editor's card is a sheet that scrolls and pulls; while a bar is in the
                    // air neither may take the finger, so the claim is restated on every move in
                    // case anything between here and the sheet let it go.
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    moveDrag(event.getX(), event.getY());
                }
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
