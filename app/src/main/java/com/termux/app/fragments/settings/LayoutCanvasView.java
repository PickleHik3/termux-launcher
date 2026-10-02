package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.termux.app.ReducedMotion;
import com.termux.app.Spring;
import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Card;
import com.termux.app.place.ChromeShape.Corners;
import com.termux.app.place.ChromeShape.Pane;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The layout canvas: a to-scale picture of one place's resolved {@link PlaceLayout}, and the
 * editor that works on it. Every band stands at the launcher's own thickness in dp (see
 * {@link LayoutCanvasGeometry}: the dock from {@code DockLayoutPolicy}, the keyboard from its
 * height scale, the chin in dp), times the one scale that fits the screen's width to the canvas,
 * so what the canvas shows is the arrangement at its real proportions, not a sketch.
 *
 * <p>Every shape on it is the shape model's ({@link ChromeShapeModel}, ADR 0007), asked of the
 * arrangement, the Style and the user's Corners and Margin scaled to the canvas: the fill, the
 * selection outline, the lifted copy and its dashed placeholder are read off the model's pieces and
 * cards, and nothing here decides a corner. Under Docked the pieces are one frame, composed under
 * one outer clip with the pane's opening cut out of it; under Floating they are cards with
 * Margin's air between. Flipping Style morphs one shape into the other ({@link LayoutCanvasMorph}).
 *
 * <p>The artwork is the layout assets pack's ({@link LayoutCanvasArtwork}): status bar collapsed or
 * expanded, terminal, dock circles, A-Z letters, extra-keys glyphs, keyboard docked, floating or
 * split. Each element paints its content inside the bounds the model gave it, glyphs keep their
 * size, and nothing is stretched. Colours come from the live Material scheme.
 *
 * <p>A bar is lifted by pressing it anywhere and moving past the touch slop; a tap selects it.
 * While a bar is lifted every edge it may legally stand on is outlined, the tray under the canvas
 * offers to put it away, and a release over either reports the new placement. The lifted copy keeps
 * its shape until it hovers a target, then takes the shape it would have there, which is the
 * shape of the dashed placeholder. A release anywhere else springs the bar back and reports
 * nothing. {@link MiniatureDragPolicy} owns which targets exist and which one the finger is over.
 * A selected element wears one outline, and the handle that resizes it.
 */
public final class LayoutCanvasView extends View {

    /**
     * One region of the layout canvas. The keyboard is one too: it has no edge and no order, but
     * it can be selected, resized by its two handles, and put away in the tray.
     */
    public enum Block { STATUS_BAR, APPS_ROW, ALPHABETS_ROW, EXTRA_KEYS, CANVAS, KEYBOARD }

    /**
     * A resize handle on the selected element (spec §3.5): the dock's inner edge for its height,
     * the keyboard's top edge for its height and its bottom edge for the chin under the last key
     * row, and on Home a corner handle for the widget grid's cells. Each is one pill drawn on the
     * edge it moves, with the platform's 48dp target around it.
     */
    public enum Handle { DOCK_HEIGHT, KEYBOARD_HEIGHT, KEYBOARD_CHIN, WIDGET_GRID }

    /**
     * What an edit on the layout canvas asks for. Every value is already in the store's own units
     * and clamped to the store's range, so the editor writes it straight through; it answers with
     * {@link #setSizes} and {@link #setLayout}, which is what redraws the canvas at the new size.
     */
    public interface OnCanvasEditListener {
        /** The selection moved to another element, or went ({@code null}). */
        default void onSelectionChanged(@Nullable Block selected) {}

        /** The dock's inner edge was dragged: its height, as a multiple of its unscaled height. */
        default void onDockHeightDragged(float scale) {}

        /** The keyboard's top edge was dragged: its height, as a multiple of its unscaled one. */
        default void onKeyboardHeightDragged(float scale) {}

        /** The keyboard's bottom edge was dragged: the air under its last key row, in dp. */
        default void onKeyboardChinDragged(int dp) {}

        /** Home's corner handle was dragged: the grid in whole cells. */
        default void onWidgetGridDragged(int columns, int rows) {}

        /** The finger came off a handle; the readout goes with it. */
        default void onHandleReleased() {}

        /** The keyboard was dropped in the tray: the same switch as Keyboard on/off. */
        default void onKeyboardPutAway() {}

        /**
         * Whether the lifted element may be put away in the tray, and whether the finger is over
         * it now. A host whose tray stands outside the view ({@link #setExternalTrayRect}) draws
         * its own "Drop here to hide" from this; both are false once nothing is lifted.
         */
        default void onTrayOfferChanged(boolean offered, boolean hovered) {}

        /** A chip in the hidden-elements popup was tapped: bring that element back where it left. */
        default void onHiddenChipTapped(@NonNull Block block) {}

        /** The keyboard's chip was dragged out of the popup and dropped on the phone: switch it on. */
        default void onKeyboardRestored() {}
    }

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
        Block.STATUS_BAR, Block.CANVAS, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS,
        Block.KEYBOARD};

    /** The bands with a placement to change, in the order the tray lists them. */
    private static final Block[] BARS = {
        Block.STATUS_BAR, Block.APPS_ROW, Block.ALPHABETS_ROW, Block.EXTRA_KEYS};

    /** Portrait: narrow and tall; landscape: wide and short — a phone silhouette either way. */
    private static final float PORTRAIT_ASPECT = 9f / 19.5f;
    private static final float LANDSCAPE_ASPECT = 19.5f / 9f;
    private static final float DEFAULT_HEIGHT_DP = 188f;
    /** The shape of a real phone, which the canvas stands for: long side over short. */
    private static final float SCREEN_LONG_OVER_SHORT = 19.5f / 9f;

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
    /** The hairline every Floating card wears, and the heavier one a lifted card does. */
    private static final float CARD_STROKE_U = 1f;
    private static final float LIFTED_STROKE_U = 1.5f;
    /** How far the lifted card's solid underlay sits below it: the tonal raise. */
    private static final float LIFT_OFFSET_U = 4f;
    /**
     * A widget tile's and the display window's corner, in real dp scaled by the canvas like the
     * other metrics. These are content on the pane, not chrome: the chrome's corners are the shape
     * model's, from the user's Corners.
     */
    private static final float TILE_CORNER_DP = 6f;
    /** The corner a drop-target outline and the tray's drop zone wear. */
    private static final float SLOT_CORNER_DP = 4f;
    /** The short side a phone is taken to have when the view has no screen to ask: 411dp. */
    private static final float REFERENCE_SHORT_SIDE_DP = 411f;

    // ---- The theme's roles, as the design maps them ---------------------------------------------
    /** Outlines and text lines: the on-surface-variant at the design's 45%. */
    private static final int DIM_ALPHA = 115;
    /** The tonal layer a lifted card or an active tray wears: the accent at 9%. */
    private static final int ACCENT_WASH_ALPHA = 23;
    /** A widget tile: the container tone laid thinly over the pane. */
    private static final int TILE_ALPHA = 140;
    /** The dashed placeholder a lifted bar leaves where it would land. */
    private static final int PLACEHOLDER_ALPHA = 60;
    private static final int SLOT_HOVER_ALPHA = 60;
    /** The gaps of the hovered edge that the finger is not on; enough to read, not to compete. */
    private static final int GAP_LINE_ALPHA = 110;

    /**
     * The strip under the phone: the hidden elements live there, and a lifted one can be put there.
     * At rest the view draws nothing in it — the editor stands a row of real Material chips over
     * it, one per hidden element, or one short line when nothing is hidden — and while an element
     * is lifted and may be put away the view draws the drop zone over them.
     */
    private static final float TRAY_HEIGHT_DP = 48f;
    private static final float TRAY_GAP_DP = 12f;
    private static final float TRAY_STROKE_DP = 1.25f;
    private static final float TRAY_DASH_DP = 4f;
    private static final float TRAY_TEXT_SP = 11f;
    /** The trash icon's glyph and its badge (the list of hidden elements is a popup window the editor shows). */
    private static final float TRASH_ICON_DP = 24f;
    private static final float BADGE_RADIUS_DP = 8f;
    /** Half the platform's minimum target: what a handle is hit-tested with around its centre. */
    private static final float HANDLE_TOUCH_HALF_DP = 24f;
    /** The one outline a selected element wears: 2dp of the theme's primary. */
    private static final float SELECTION_STROKE_DP = 2f;
    /** A resize handle's pill: its length along the edge it moves and its thickness across it. */
    private static final float HANDLE_LENGTH_DP = 24f;
    private static final float HANDLE_THICKNESS_DP = 5f;
    /** The readout a handle shows while it is dragged. */
    private static final float READOUT_TEXT_SP = 12f;
    private static final float READOUT_PAD_H_DP = 10f;
    private static final float READOUT_HEIGHT_DP = 26f;
    private static final float READOUT_GAP_DP = 10f;
    /** The most of a keyboard card the chin may take, so a huge chin never eats the keys. */
    private static final float CHIN_MAX_SHARE = 0.5f;
    /** The most of the frame's height the keyboard's block may take. */
    private static final float KEYBOARD_MAX_SHARE = 0.5f;
    private static final float GHOST_SCALE = 1.06f;
    /** The dock plank's press constants: a lift and a spring-back are the same kind of motion. */
    private static final float SPRING_STIFFNESS = 320f;
    private static final float SPRING_DAMPING = 22f;
    /** The lifted copy's change of shape over a target: critically damped, so it never overshoots. */
    private static final float MORPH_DAMPING = 2f * (float) Math.sqrt(SPRING_STIFFNESS);

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
    private final TextPaint mLegendPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** The pack's artwork, and the theme's roles it is painted in; the roles are read every draw. */
    private final LayoutCanvasArtwork mArtwork = new LayoutCanvasArtwork();
    private final LayoutCanvasArtwork.Palette mPalette = new LayoutCanvasArtwork.Palette();
    private final RectF mFrameRect = new RectF();
    /** One design unit on this frame, in pixels. */
    private float mUnit = 1f;
    private final Path mClipPath = new Path();
    /** Scratch paths: the shape being filled, clipped or stroked, and the lifted copy's raise. */
    private final Path mShapePath = new Path();
    private final Path mRaisePath = new Path();
    /** Reused scratch rects for whatever a draw call is computing right now; never read across
     *  two different shapes, only within one draw-then-move-on sequence. */
    private final RectF mScratchRectA = new RectF();
    private final RectF mScratchRectB = new RectF();
    private final RectF mScratchRectC = new RectF();
    private final Map<Block, RectF> mBlockRects = new EnumMap<>(Block.class);
    private final Map<Block, RectF> mLegendRects = new EnumMap<>(Block.class);
    /**
     * The keyboard's block along the bottom while the place shows it; empty otherwise. Docked it
     * is the keyboard's rect, split the one the two halves cut, floating the card over the pane.
     */
    private final RectF mKeyboardRect = new RectF();
    private boolean mGridCollapsed;
    private float mLegendLabelWidth;

    // ---- The shape: what the model said, and the Style it was asked under ----------------------
    /** The Style of the whole chrome, and Corners and Margin in dp as Floating spends them. */
    @NonNull private LayoutStyle mStyle = LayoutStyle.DOCKED;
    private float mCornersDp = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
    private float mMarginDp = TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;
    /** What the model was asked and what it said, in the frame's own coordinates; null before a layout. */
    @Nullable private ChromeShapeModel.Input mInput;
    @Nullable private ChromeShape mShape;
    /** The keyboard's pieces: one when docked or floating, two halves when split. */
    private final List<Piece> mKeyboardPieces = new ArrayList<>(2);
    /** Whether the keyboard takes a band of the bottom stack, as docked and split do. */
    private boolean mKeyboardInStack;
    /** The shape the canvas stood in before Style flipped, while the change is under way. */
    @Nullable private ChromeShape mMorphFrom;
    private long mMorphStartMs;
    /** A progress a test holds the change at; negative follows the clock. */
    private float mMorphHeld = -1f;
    /** How many pinned apps and extra keys the bars draw; negative draws the pack's seven. */
    private int mPinnedAppCount = -1;
    private int mExtraKeyCount = -1;

    // ---- The drag: one gesture's worth of state, all of it cleared when it ends ----------------
    @Nullable private OnBarDroppedListener mDropListener;
    /** The bar a finger has lifted; null while nothing is lifted. */
    @Nullable private Block mDraggedBar;
    /** The bar a finger is down on that has not yet travelled far enough to lift it, or null. */
    @Nullable private Block mPressedBar;
    /** A press on the trash icon the canvas itself draws (a view that has no host tray). */
    private boolean mPressedTrash;
    /** Told when the canvas's own trash (a view that has no host tray) is tapped. */
    @Nullable private Runnable mTrashTapListener;
    /** The lifted element is a hidden one dragged in from the editor's popup, so it is hidden now and the tray is no target. */
    private boolean mFromPopup;
    /** How far a finger has to travel before the bar under it is lifted rather than tapped. */
    private final float mTouchSlop;
    /** Where the finger went down, so the copy travels with it from where the bar stood. */
    private float mDownX;
    private float mDownY;
    /** Where the lifted copy started, so it can be drawn at the finger and sprung back. */
    private final RectF mLiftOrigin = new RectF();
    /** Where the lifted copy is drawn this frame; its own rect, since the card drawing scribbles
     *  on the shared scratch rects. */
    private final RectF mGhostRect = new RectF();
    private final List<MiniatureDragPolicy.Slot> mSlots = new ArrayList<>();
    @Nullable private MiniatureDragPolicy.Slot mHoverSlot;
    /** The shape the hovered target would give the lifted bar, which the placeholder wears too. */
    @Nullable private Piece mDropShape;
    @Nullable private MiniatureDragPolicy.Slot mDropShapeFor;
    @Nullable private Edge mDropEdge;
    private final RectF mTrayRect = new RectF();
    /** The lifted copy's offset from where it started, sprung back to zero on a release. */
    private final Spring mGhostX = new Spring(0f, SPRING_STIFFNESS, SPRING_DAMPING);
    private final Spring mGhostY = new Spring(0f, SPRING_STIFFNESS, SPRING_DAMPING);
    private final Spring mGhostScale = new Spring(1f, SPRING_STIFFNESS, SPRING_DAMPING);
    /** How far the lifted copy has changed from its own shape to the hovered target's: 0 to 1. */
    private final Spring mGhostMorph = new Spring(0f, SPRING_STIFFNESS, MORPH_DAMPING);
    private boolean mSpringingBack;
    private long mLastFrameNanos;
    private final Runnable mMotionTick = this::advanceMotion;

    // ---- Selection and handles -----------------------------------------------------------------
    @Nullable private OnCanvasEditListener mEditListener;
    /** The element a tap selected, which wears the outline and carries the handles; or none. */
    @Nullable private Block mSelected;
    /** Each handle the selection carries, as the pill drawn on its edge. */
    private final Map<Handle, RectF> mHandleRects = new EnumMap<>(Handle.class);
    /** The handle a finger holds, or null. */
    @Nullable private Handle mDraggedHandle;
    /** What the held handle stood at when the finger went down, which the drag is measured from. */
    private float mHandleStartScale;
    private int mHandleStartChin;
    private float mHandleStartExtentPx;
    private final RectF mHandleStartRect = new RectF();
    private int mHandleLastColumns;
    private int mHandleLastRows;
    /** The readout the editor gave the held handle, drawn beside it; null when there is none. */
    @Nullable private String mReadout;
    /**
     * The three sizes the canvas draws with. They are not in {@link PlaceLayout} — the store keeps
     * them apart so a dragging finger does not retire the cached arrangement — so the editor hands
     * them over on their own ({@link #setSizes}).
     */
    private float mDockScale = TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT;
    private float mKeyboardScale = TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE;
    private int mKeyboardChinDp = TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING;
    /** The status bar's stored state for the orientation shown; null leaves the launcher's rest. */
    @Nullable private Boolean mStatusCompact;
    /** Canvas pixels per real dp: what every dp figure is multiplied by to stand on the canvas. */
    private float mCanvasScale = 1f;

    // ---- Frame-filling host (the unified editor, SPEC §3.5) -------------------------------------
    /** Whether the phone outline is the view's own bounds rather than a picture inside them. */
    private boolean mFillsView;
    /** The tray in view coordinates while it stands outside the view; empty for none. */
    private final RectF mExternalTray = new RectF();
    /** The outline's corner in px set by a frame-filling host; negative draws the artwork's. */
    private float mFrameRadiusOverridePx = -1f;
    /** What {@link OnCanvasEditListener#onTrayOfferChanged} last said. */
    private boolean mToldTrayOffered;
    private boolean mToldTrayHovered;

    @NonNull private final Drawable mIconStatus;
    @NonNull private final Drawable mIconApps;
    @NonNull private final Drawable mIconKeys;
    @NonNull private final Drawable mIconHomeGrid;
    @NonNull private final Drawable mIconDisplay;
    @NonNull private final Drawable mIconTerminal;
    @NonNull private final Drawable mIconTrash;
    @NonNull private final Drawable mIconTrashFilled;

    public LayoutCanvasView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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

        mIconStatus = loadIcon(R.drawable.ic_symbol_notifications);
        mIconApps = loadIcon(R.drawable.ic_symbol_apps);
        mIconKeys = loadIcon(R.drawable.ic_symbol_keyboard);
        mIconHomeGrid = loadIcon(R.drawable.ic_symbol_grid_view);
        mIconDisplay = loadIcon(R.drawable.ic_symbol_desktop_windows);
        mIconTerminal = loadIcon(R.drawable.ic_symbol_terminal);
        mIconTrash = loadIcon(R.drawable.ic_symbol_delete);
        mIconTrashFilled = loadIcon(R.drawable.ic_trash_filled);
    }

    /**
     * The Style of the whole chrome, and the Corners and Margin the user set, in dp. Both Styles
     * spend both: Floating's cards wear Corners with Margin of air round them; Docked's frame
     * is flush and square at the screen's edges (with no side bar it is two edge cards with their
     * inner corners rounded at Corners), and its pane is an insert with Corners as its radius, a
     * gutter of Margin inside the bars. A change of Style morphs the
     * canvas from the shape it stood in to the new one over {@value LayoutCanvasMorph#DURATION_MS}
     * ms, or jumps with reduced motion; a new Corners or Margin redraws at once, so a slider is
     * followed live.
     */
    public void setShape(@NonNull LayoutStyle style, float cornersDp, float marginDp) {
        float corners = Math.max(0f, cornersDp);
        float margin = Math.max(0f, marginDp);
        boolean flipped = style != mStyle;
        if (!flipped && Float.compare(corners, mCornersDp) == 0
            && Float.compare(margin, mMarginDp) == 0) return;
        ChromeShape before = mShape;
        mStyle = style;
        mCornersDp = corners;
        mMarginDp = margin;
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        if (flipped) {
            boolean animate = before != null && mShape != null
                && !ReducedMotion.isEnabled(getContext());
            mMorphFrom = animate ? before : null;
            mMorphStartMs = SystemClock.uptimeMillis();
            mMorphHeld = -1f;
        }
        invalidate();
    }

    /** The Style the canvas is drawn in. */
    @NonNull
    public LayoutStyle style() {
        return mStyle;
    }

    /**
     * How many pinned apps and extra keys the dock and the extra-keys bar draw, so the symbols
     * follow what the launcher really has. A negative count draws the pack's own seven, and a
     * count that does not fit the bar's length draws as many as do.
     */
    public void setSlotCounts(int pinnedApps, int extraKeys) {
        if (pinnedApps == mPinnedAppCount && extraKeys == mExtraKeyCount) return;
        mPinnedAppCount = pinnedApps;
        mExtraKeyCount = extraKeys;
        invalidate();
    }

    /**
     * The status bar's stored state for the orientation on the canvas ({@code status_compact}):
     * collapsed to its one row, or expanded with the clock and the media tile. Until it is set the
     * canvas shows the launcher's rest: landscape collapsed, portrait expanded.
     */
    public void setStatusCompact(boolean compact) {
        if (mStatusCompact != null && mStatusCompact == compact) return;
        mStatusCompact = compact;
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        invalidate();
    }

    /** Whether the status bar is drawn collapsed. */
    @VisibleForTesting
    public boolean isStatusCompact() {
        return mStatusCompact != null ? mStatusCompact
            : mOrientation == PlaceOrientation.LANDSCAPE;
    }

    /** Canvas pixels per real dp, for a test to check the to-scale mapping against. */
    @VisibleForTesting
    public float canvasScalePx() {
        return mCanvasScale;
    }

    private float screenShortSideDp() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (metrics.density <= 0f) return REFERENCE_SHORT_SIDE_DP;
        return Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density;
    }

    /** What the shape model was asked for the arrangement on the canvas; null before a layout. */
    @Nullable
    @VisibleForTesting
    ChromeShapeModel.Input shapeInput() {
        return mInput;
    }

    /** What the shape model said, in the frame's own coordinates; null before a layout. */
    @Nullable
    @VisibleForTesting
    ChromeShape shape() {
        return mShape;
    }

    /** How far a change of Style has come: 0 where it started, 1 at rest. */
    @VisibleForTesting
    float morphProgress() {
        if (mMorphFrom == null) return 1f;
        if (mMorphHeld >= 0f) return mMorphHeld;
        long elapsed = SystemClock.uptimeMillis() - mMorphStartMs;
        return LayoutCanvasMorph.clamp01(elapsed / (float) LayoutCanvasMorph.DURATION_MS);
    }

    /** Whether the canvas is part-way between the two Styles. */
    @VisibleForTesting
    boolean isStyleMorphing() {
        return mMorphFrom != null && morphProgress() < 1f;
    }

    /** Holds a change of Style at {@code progress}, which the clock would otherwise decide. */
    @VisibleForTesting
    void holdMorphAt(float progress) {
        if (mMorphFrom != null) mMorphHeld = LayoutCanvasMorph.clamp01(progress);
    }

    /**
     * One piece's rect as it is drawn right now, in view pixels: the model's own at rest, and part
     * of the way from the old Style's while Style changes. Null for a piece that is not shown.
     */
    @Nullable
    @VisibleForTesting
    RectF drawnPieceRect(@NonNull PieceId id) {
        ChromeShape shape = mShape;
        if (shape == null) return null;
        float t = morphProgress();
        if (mMorphFrom != null && t < 1f) {
            LayoutCanvasMorph.Part part = LayoutCanvasMorph.blend(mMorphFrom, shape,
                LayoutCanvasMorph.ease(t)).piece(id);
            return part == null ? null : viewRect(part.box);
        }
        Piece piece = shape.piece(id);
        return piece == null ? null : viewRect(piece.box);
    }

    public LayoutCanvasView(@NonNull Context context) {
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

    public void setOnCanvasEditListener(@Nullable OnCanvasEditListener listener) {
        mEditListener = listener;
    }

    /**
     * The dock's height, the keyboard's height and the keyboard's chin, in the store's units, for
     * the orientation on the canvas. The dock's band and the keyboard's block are drawn in
     * real size, so a handle dragged is a band that grows under the finger.
     */
    public void setSizes(float dockHeightScale, float keyboardHeightScale, int keyboardChinDp) {
        if (Float.compare(dockHeightScale, mDockScale) == 0
            && Float.compare(keyboardHeightScale, mKeyboardScale) == 0
            && keyboardChinDp == mKeyboardChinDp) return;
        mDockScale = dockHeightScale;
        mKeyboardScale = keyboardHeightScale;
        mKeyboardChinDp = keyboardChinDp;
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        invalidate();
    }

    /** The element wearing the outline, or null. */
    @Nullable
    public Block selectedBlock() {
        return mSelected;
    }

    /**
     * Selects an element, or clears the selection for null. An element the canvas is not drawing
     * — hidden, or the keyboard on a place that shows none — cannot be selected, and asking for it
     * clears the selection instead. Reports the change.
     */
    public void setSelectedBlock(@Nullable Block block) {
        Block next = block != null && isSelectable(block) ? block : null;
        if (next == mSelected) return;
        mSelected = next;
        computeHandles();
        invalidate();
        if (mEditListener != null) mEditListener.onSelectionChanged(mSelected);
    }

    /** The readout beside the held handle, in real units; null takes it away. */
    public void setHandleReadout(@Nullable String readout) {
        if (TextUtils.equals(readout, mReadout)) return;
        mReadout = readout;
        invalidate();
    }

    /** The handle a finger holds, or null. */
    @Nullable
    @VisibleForTesting
    public Handle draggedHandle() {
        return mDraggedHandle;
    }

    /** The pill a handle is drawn as, in view pixels, or null while the selection has none. */
    @Nullable
    @VisibleForTesting
    public RectF handleRect(@NonNull Handle handle) {
        RectF rect = mHandleRects.get(handle);
        return rect == null ? null : new RectF(rect);
    }

    /** The phone frame, in view pixels: what the editor lays its own chips beside. */
    @NonNull
    public RectF frameRect() {
        return new RectF(mFrameRect);
    }


    /**
     * The strip under the phone, in view pixels: where the editor's chips for the hidden elements
     * stand, and a lifted element's way out.
     */
    @NonNull
    public RectF trayRect() {
        return new RectF(mTrayRect);
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
    public RectF blockRect(@NonNull Block block) {
        if (block == Block.KEYBOARD) return keyboardRect();
        RectF rect = mBlockRects.get(block);
        return rect == null ? null : new RectF(rect);
    }

    /**
     * The keyboard's block along the bottom of the phone, in view pixels, or null while the place
     * does not show one: the keyboard element off, Home (where it opens over the page), or the
     * display with a keyboard that floats.
     */
    @Nullable
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
        if (block == Block.KEYBOARD) return mLayout != null && !mLayout.keyboardShown;
        Element element = elementOf(block);
        return mLayout != null && element != null && !EdgeStackPolicy.isShown(mLayout, element);
    }

    /** Whether a tap can select this block: it has to be on the canvas. */
    private boolean isSelectable(@NonNull Block block) {
        if (mLayout == null) return false;
        if (block == Block.KEYBOARD) return !mKeyboardRect.isEmpty();
        RectF rect = mBlockRects.get(block);
        return rect != null && !rect.isEmpty();
    }

    public void setOnBlockTappedListener(@Nullable OnBlockTappedListener listener) {
        mListener = listener;
    }

    /**
     * Whether the legend stands beside the phone. Off, the phone takes the whole view and the
     * bands are the only tap targets — the Layout page names every element in its own rows, so a
     * second list beside two layout canvases would say everything twice.
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

    /**
     * The tray's height and the air under it, in pixels. The tray always stands on the view's
     * bottom edge, so the editor can stand its chips over it with a bottom gravity and these two.
     */
    public float trayHeightPx() {
        return dp(TRAY_HEIGHT_DP);
    }

    public float trayBottomInsetPx() {
        return dp(4);
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
        if (mFillsView) {
            layoutFilledFrame(viewWidth, viewHeight);
            return;
        }
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
        // The canvas stands for a real screen of the same shape: the frame's width is the
        // screen's width in this orientation, so one dp of the launcher is this many pixels.
        mCanvasScale = LayoutCanvasGeometry.canvasScale(mFrameRect.width(), screenWidthDp());
        // The tray stands on the view's bottom edge and takes the view's width, not the phone's:
        // a portrait phone is too narrow for "Drop here to hide" or for two chips to keep their
        // names, and the editor stands its chips over this strip with a bottom gravity.
        float trayBottom = viewHeight - pad;
        mTrayRect.set(pad, trayBottom - dp(TRAY_HEIGHT_DP), viewWidth - pad, trayBottom);

        layoutLegend(legendLeft, legendWidth, Math.round(viewHeight - trayHeight), swatch);
        computeBlocks();
    }

    /**
     * The frame-filling layout: the phone outline is the view itself when the orientation shown is
     * the view's own shape, so the canvas stands exactly where the live launcher it replaces stood;
     * the other orientation is fitted, centred, inside it. No legend, no padding, and the tray is
     * wherever the host said it is ({@link #setExternalTrayRect}), which may be outside the view.
     */
    private void layoutFilledFrame(int viewWidth, int viewHeight) {
        mLegendRects.clear();
        boolean landscape = mOrientation == PlaceOrientation.LANDSCAPE;
        boolean viewLandscape = viewWidth > viewHeight;
        if (landscape == viewLandscape) {
            mFrameRect.set(0f, 0f, viewWidth, viewHeight);
        } else {
            float aspect = landscape ? LANDSCAPE_ASPECT : PORTRAIT_ASPECT;
            float gap = dp(16);
            float width = Math.max(0f, viewWidth - 2 * gap);
            float height = width / aspect;
            float maxHeight = Math.max(0f, viewHeight - 2 * gap);
            if (height > maxHeight) {
                height = maxHeight;
                width = height * aspect;
            }
            float left = (viewWidth - width) / 2f;
            float top = (viewHeight - height) / 2f;
            mFrameRect.set(left, top, left + width, top + height);
        }
        mUnit = Math.max(0.01f,
            Math.min(mFrameRect.width(), mFrameRect.height()) / PHONE_SHORT_SIDE_UNITS);
        mCanvasScale = LayoutCanvasGeometry.canvasScale(mFrameRect.width(), screenWidthDp());
        mTrayRect.set(mExternalTray);
        computeBlocks();
    }

    /**
     * Makes the phone outline the view's own bounds (SPEC §3.5: Layout is not a miniature). The
     * host sizes the view to the frame the live launcher is scaled into; the tray then stands
     * wherever {@link #setExternalTrayRect} puts it and the legend is never drawn.
     */
    public void setFillsView(boolean fills) {
        if (mFillsView == fills) return;
        mFillsView = fills;
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    /** Whether the phone outline is the view's own bounds. */
    public void setOnTrashTapListener(@Nullable Runnable listener) {
        mTrashTapListener = listener;
    }

    public boolean fillsView() {
        return mFillsView;
    }

    /**
     * Where the tray stands, in this view's coordinates, while it is a view of the host's outside
     * this one (a frame-filling canvas has no room under its phone). The rect may lie past the
     * view's bounds: a lifted element's finger keeps reporting to this view wherever it travels,
     * so a drop there still lands. Null or empty takes the tray away. Ignored unless
     * {@link #setFillsView} is on.
     */
    public void setExternalTrayRect(@Nullable RectF rectInView) {
        if (rectInView == null) {
            if (mExternalTray.isEmpty()) return;
            mExternalTray.setEmpty();
        } else {
            if (rectInView.equals(mExternalTray)) return;
            mExternalTray.set(rectInView);
        }
        if (mFillsView && mDraggedBar == null) mTrayRect.set(mExternalTray);
    }

    /**
     * The outline's corner radius, in px, for a frame-filling host that clips the view to the
     * device's own corners; negative goes back to the artwork's.
     */
    public void setFrameCornerRadiusPx(float radiusPx) {
        if (Float.compare(radiusPx, mFrameRadiusOverridePx) == 0) return;
        mFrameRadiusOverridePx = radiusPx;
        // Under Docked the frame's exposed corners are this radius, so the shapes are asked again.
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        invalidate();
    }

    /** Tells the listener when the tray's offer or the finger's hover over it changed. */
    private void notifyTrayOffer() {
        boolean offered = mDraggedBar != null && !mSpringingBack && isTrayOffered();
        boolean hovered = offered && mHoverSlot != null && mHoverSlot.isTray();
        if (offered == mToldTrayOffered && hovered == mToldTrayHovered) return;
        mToldTrayOffered = offered;
        mToldTrayHovered = hovered;
        if (mEditListener != null) mEditListener.onTrayOfferChanged(offered, hovered);
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

    /**
     * Asks the shape model for every chrome shape of the arrangement under the Style, and reads
     * the blocks off the answer: a bar's block is its piece's rect, the canvas block is the pane's
     * opening, the keyboard's block is its piece or the two halves. The slots, handles and hit
     * targets are all laid against these.
     */
    private void computeBlocks() {
        mBlockRects.clear();
        mKeyboardRect.setEmpty();
        mKeyboardPieces.clear();
        mKeyboardInStack = false;
        mInput = null;
        mShape = null;
        if (mLayout == null || mFrameRect.isEmpty()) {
            mGridCollapsed = false;
            mSlots.clear();
            return;
        }
        mInput = shapeInput(mStyle);
        mShape = ChromeShapeModel.shape(mInput);
        for (Piece piece : mShape.pieces()) {
            Element element = piece.id.element();
            if (element == null) {
                mKeyboardPieces.add(piece);
            } else if (piece.box.width() > 0f && piece.box.height() > 0f) {
                mBlockRects.put(blockOf(element), viewRect(piece.box));
            }
        }
        if (showsKeyboard() && !mKeyboardPieces.isEmpty()) {
            boolean first = true;
            for (Piece piece : mKeyboardPieces) {
                RectF rect = viewRect(piece.box);
                if (first) mKeyboardRect.set(rect);
                else mKeyboardRect.union(rect);
                first = false;
            }
            mKeyboardInStack = mLayout.keyboardForm != KeyboardForm.FLOATING;
        }
        mBlockRects.put(Block.CANVAS, viewRect(mShape.opening().box));
        computeContent();
        computeSlots();
        // A selection whose element has left the canvas — hidden, or the keyboard on a place that
        // shows none — goes with it.
        if (mSelected != null && !isSelectable(mSelected)) {
            mSelected = null;
            if (mEditListener != null) mEditListener.onSelectionChanged(null);
        }
        computeHandles();
        updateContentDescription();
    }

    /** A rect of the model's, which stands in the frame's own coordinates, in view pixels. */
    @NonNull
    private RectF viewRect(@NonNull Box box) {
        return new RectF(box.left + mFrameRect.left, box.top + mFrameRect.top,
            box.right + mFrameRect.left, box.bottom + mFrameRect.top);
    }

    /** The screen's width in dp in the orientation on the canvas, at the shape of a real phone. */
    private float screenWidthDp() {
        float shortDp = screenShortSideDp();
        return mOrientation == PlaceOrientation.LANDSCAPE
            ? shortDp * SCREEN_LONG_OVER_SHORT : shortDp;
    }

    /** The screen's height in dp in the orientation on the canvas. */
    private float screenHeightDp() {
        float shortDp = screenShortSideDp();
        return mOrientation == PlaceOrientation.LANDSCAPE
            ? shortDp : shortDp * SCREEN_LONG_OVER_SHORT;
    }

    /**
     * A band's thickness on the canvas, in pixels: the launcher's own figure in dp for this
     * element on this edge (the dock from {@code DockLayoutPolicy} at its height scale and style,
     * the status bar from its edge geometry collapsed or expanded, the toolbar and the A-Z strip
     * from their policies) times the canvas scale. {@code floating} is the Style the figure is for,
     * since the dock and the status bar stand thicker in the Floating card.
     */
    private float bandPx(@NonNull Element element, @NonNull Edge edge, boolean floating) {
        float dp;
        switch (element) {
            case STATUS:
                dp = LayoutCanvasGeometry.statusBandDp(edge, isStatusCompact(), floating);
                break;
            case APPS:
                dp = edge.isOnSide() ? LayoutCanvasGeometry.dockRailWidthDp()
                    : LayoutCanvasGeometry.dockBandHeightDp(mDockScale, floating);
                break;
            case AZ:
                dp = LayoutCanvasGeometry.azBandHeightDp();
                break;
            case EXTRA_KEYS:
            default:
                dp = LayoutCanvasGeometry.toolbarHeightDp();
                break;
        }
        return LayoutCanvasGeometry.toCanvasPx(dp, mCanvasScale);
    }

    /**
     * The arrangement the model is asked about: what the place shows of it. Home opens the
     * keyboard over its page, so it has none; the display with a keyboard that floats over it has
     * a floating card; the terminal and a display the keyboard resizes show it as the form says.
     */
    @NonNull
    private PlaceLayout shapeLayout() {
        PlaceLayout layout = mLayout;
        if (layout == null) throw new IllegalStateException("no arrangement");
        if (!layout.keyboardShown) return layout;
        switch (mPlace) {
            case WIDGETS:
                return layout.withKeyboardShown(false);
            case DISPLAY:
                if (layout.keyboardMode == KeyboardMode.OVERLAY
                    && layout.keyboardForm != KeyboardForm.FLOATING) {
                    return new PlaceLayout(layout.slots(), layout.keyboardMode,
                        KeyboardForm.FLOATING, true, layout.widgetColumns, layout.widgetRows);
                }
                return layout;
            case TERMINAL:
            default:
                return layout;
        }
    }

    /**
     * What the shape model is asked under one Style: the arrangement, the frame, every band's
     * thickness at the launcher's own dp times the canvas scale, and the user's Corners and Margin
     * and the device's screen radius at the same scale. The model caps nothing, so the bands are
     * squeezed here first when they would leave the pane's opening no room
     * ({@link LayoutCanvasGeometry#fitFactor}).
     */
    @NonNull
    private ChromeShapeModel.Input shapeInput(@NonNull LayoutStyle style) {
        PlaceLayout layout = shapeLayout();
        boolean floating = style == LayoutStyle.FLOATING;
        float scale = mCanvasScale;
        float width = mFrameRect.width();
        float height = mFrameRect.height();
        float margin = mMarginDp * scale;
        float[] row = new float[Element.values().length];
        float[] column = new float[Element.values().length];
        for (Element element : Element.values()) {
            row[element.ordinal()] = bandPx(element, Edge.BOTTOM, floating);
            column[element.ordinal()] = bandPx(element, Edge.LEFT, floating);
        }
        boolean stacked = layout.keyboardShown && layout.keyboardForm != KeyboardForm.FLOATING;
        float keyboard = keyboardBandPx(layout, height);
        // What the arrangement spends along each axis, and the air its cards would need.
        float rowsTotal = 0f;
        float columnsTotal = 0f;
        int rowBands = 0;
        int columnBands = 0;
        for (Edge edge : Edge.values()) {
            for (Element element : EdgeStackPolicy.stack(layout, edge)) {
                if (edge.isOnSide()) {
                    columnsTotal += column[element.ordinal()];
                    columnBands++;
                } else {
                    rowsTotal += row[element.ordinal()];
                    rowBands++;
                }
            }
        }
        if (stacked) {
            rowsTotal += keyboard;
            rowBands++;
        }
        // Floating's cards each leave Margin round them; Docked's bars are flush and only the
        // insert keeps a gutter of Margin on each side.
        float rowAir = floating ? margin * (rowBands + 1) : 2f * margin;
        float columnAir = floating ? margin * (columnBands + 1) : 2f * margin;
        float rowFit = LayoutCanvasGeometry.fitFactor(height, rowsTotal, rowAir);
        float columnFit = LayoutCanvasGeometry.fitFactor(width, columnsTotal, columnAir);
        ChromeShapeModel.Thickness thickness = ChromeShapeModel.Thickness.of(
            row[Element.STATUS.ordinal()] * rowFit, column[Element.STATUS.ordinal()] * columnFit,
            row[Element.APPS.ordinal()] * rowFit, column[Element.APPS.ordinal()] * columnFit,
            row[Element.AZ.ordinal()] * rowFit, column[Element.AZ.ordinal()] * columnFit,
            row[Element.EXTRA_KEYS.ordinal()] * rowFit,
            column[Element.EXTRA_KEYS.ordinal()] * columnFit, keyboard * rowFit);
        return ChromeShapeModel.input(layout, style, width, height, thickness)
            .corners(mCornersDp * scale)
            .margin(margin)
            .screenRadius(frameRadiusPx())
            .paneGapCap(TERMUX_APP.MAX_TERMINAL_PANE_GAP * scale)
            .paneGapFloor(com.termux.app.chrome.LiveChromeShape.PANE_GAP_FLOOR_DP * scale)
            .dividerThickness(scale)
            .panes(1, ChromeShapeModel.SplitAxis.SIDE_BY_SIDE)
            .build();
    }

    /**
     * The keyboard's thickness on the canvas, in pixels: its real height for its scale (the rows
     * times the scale, capped at the keyboard's share of the screen) plus the chin under its last
     * row, in dp, times the canvas scale. A floating keyboard is a card with no chin of its own.
     */
    private float keyboardBandPx(@NonNull PlaceLayout layout, float frameHeight) {
        float dp = LayoutCanvasGeometry.keyboardHeightDp(mKeyboardScale,
            mOrientation == PlaceOrientation.LANDSCAPE, screenHeightDp());
        if (layout.keyboardForm != KeyboardForm.FLOATING)
            dp += LayoutCanvasGeometry.chinDp(mKeyboardChinDp);
        return Math.min(frameHeight * KEYBOARD_MAX_SHARE,
            LayoutCanvasGeometry.toCanvasPx(dp, mCanvasScale));
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

    /** The model's name for one of the layout canvas's bands, or null for a band with no placement. */
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

    // ---- The tray and the slots ----------------------------------------------------------------

    /** Whether a bar stands as a column rather than a row, which turns its content with it. */
    private boolean isBarVertical(@NonNull Block bar) {
        return mLayout != null && elementOf(bar) != null && edgeOfBlock(bar).isOnSide();
    }

    /**
     * The elements the arrangement leaves off the phone, in the order the tray lists them: the
     * bars, then the keyboard. The editor's chips over the tray are the same list.
     */
    @NonNull
    @VisibleForTesting
    List<Block> hiddenBlocks() {
        List<Block> hidden = new ArrayList<>(5);
        if (mLayout == null) return hidden;
        for (Block bar : BARS) {
            if (isBlockHidden(bar)) hidden.add(bar);
        }
        if (isBlockHidden(Block.KEYBOARD)) hidden.add(Block.KEYBOARD);
        return hidden;
    }

    // ---- Handles -------------------------------------------------------------------------------

    /**
     * The handles the selection carries: the dock's on the pinned apps band's inner edge, the
     * keyboard's two on its top edge and on the bottom of its keys (above the chin; a floating
     * keyboard has no chin), and Home's on the corner of its first cell. Every other element is
     * selected with its outline alone; it moves by being lifted.
     */
    private void computeHandles() {
        mHandleRects.clear();
        if (mLayout == null || mSelected == null) return;
        switch (mSelected) {
            case APPS_ROW: {
                RectF band = mBlockRects.get(Block.APPS_ROW);
                if (band == null || band.isEmpty()) return;
                mHandleRects.put(Handle.DOCK_HEIGHT, edgeHandle(band, innerEdgeOf(Block.APPS_ROW),
                    band.centerX(), band.centerY()));
                return;
            }
            case KEYBOARD: {
                if (mKeyboardRect.isEmpty()) return;
                mScratchRectA.set(mKeyboardRect);
                mHandleRects.put(Handle.KEYBOARD_HEIGHT, edgeHandle(mScratchRectA, Edge.TOP,
                    mScratchRectA.centerX(), 0f));
                if (mLayout.keyboardForm == KeyboardForm.FLOATING) return;
                // The chin's handle rides the bottom of the keys, a quarter in from the leading
                // end, so its target and the height handle's are never the same spot.
                float x = isRtl() ? mScratchRectA.right - mScratchRectA.width() / 4f
                    : mScratchRectA.left + mScratchRectA.width() / 4f;
                RectF chin = edgeHandle(mScratchRectA, Edge.BOTTOM, x, 0f);
                chin.offset(0f, -chinPx(mScratchRectA));
                mHandleRects.put(Handle.KEYBOARD_CHIN, chin);
                return;
            }
            case CANVAS: {
                if (canvasKind() != CanvasKind.HOME_GRID) return;
                RectF area = gridArea();
                if (area == null) return;
                float cellW = gridCellWidth(area, mLayout.widgetColumns);
                float cellH = gridCellHeight(area, mLayout.widgetRows);
                float cx = area.left + cellW;
                float cy = area.top + cellH;
                float half = dp(HANDLE_LENGTH_DP) / 4f;
                mHandleRects.put(Handle.WIDGET_GRID,
                    new RectF(cx - half, cy - half, cx + half, cy + half));
                return;
            }
            default:
                return;
        }
    }

    /** The edge of a band that faces the canvas: the one its handle stands on. */
    @NonNull
    private Edge innerEdgeOf(@NonNull Block block) {
        switch (edgeOfBlock(block)) {
            case TOP: return Edge.BOTTOM;
            case LEFT: return Edge.RIGHT;
            case RIGHT: return Edge.LEFT;
            case BOTTOM:
            default: return Edge.TOP;
        }
    }

    /**
     * A handle's pill on one edge of a rectangle: {@value #HANDLE_LENGTH_DP}dp along the edge and
     * {@value #HANDLE_THICKNESS_DP}dp across it, centred on the edge line at {@code along} (an x
     * for a top or bottom edge, a y for a side).
     */
    @NonNull
    private RectF edgeHandle(@NonNull RectF rect, @NonNull Edge edge, float alongX, float alongY) {
        float half = dp(HANDLE_LENGTH_DP) / 2f;
        float thick = dp(HANDLE_THICKNESS_DP) / 2f;
        switch (edge) {
            case TOP:
                return new RectF(alongX - half, rect.top - thick, alongX + half, rect.top + thick);
            case BOTTOM:
                return new RectF(alongX - half, rect.bottom - thick, alongX + half,
                    rect.bottom + thick);
            case LEFT:
                return new RectF(rect.left - thick, alongY - half, rect.left + thick,
                    alongY + half);
            case RIGHT:
            default:
                return new RectF(rect.right - thick, alongY - half, rect.right + thick,
                    alongY + half);
        }
    }

    /**
     * The chin under the keys, in pixels: the real chin in dp times the canvas scale, never more
     * than {@value #CHIN_MAX_SHARE} of the card. Its handle keeps its full 48dp target even when
     * the chin is zero ({@link #handleAt}), so a chin of nothing can still be dragged open.
     */
    private float chinPx(@NonNull RectF card) {
        float px = LayoutCanvasGeometry.toCanvasPx(
            LayoutCanvasGeometry.chinDp(mKeyboardChinDp), mCanvasScale);
        return Math.min(card.height() * CHIN_MAX_SHARE, px);
    }

    /** Home's widget area inside the pane's opening, as {@link #drawHomeGrid} lays it; or null. */
    @Nullable
    private RectF gridArea() {
        RectF rect = mBlockRects.get(Block.CANVAS);
        if (rect == null || rect.isEmpty()) return null;
        RectF pane = new RectF(rect);
        float pad = 10f * mUnit;
        pane.inset(pad, pad);
        return pane.width() > 0f && pane.height() > 0f ? pane : null;
    }


    private float gridCellWidth(@NonNull RectF area, int columns) {
        int n = Math.max(1, columns);
        return (area.width() - 6f * mUnit * (n - 1)) / n;
    }

    private float gridCellHeight(@NonNull RectF area, int rows) {
        int n = Math.max(1, rows);
        return (area.height() - 6f * mUnit * (n - 1)) / n;
    }

    /**
     * The whole cells a corner handle at {@code (x, y)} asks for: as many as fit across the area
     * at the width the handle gives the first cell, so dragging it out makes the cells bigger and
     * fewer, and dragging it in makes them smaller and more. Snaps to whole cells and stays in the
     * store's range.
     */
    @VisibleForTesting
    static int cellsFor(float areaStart, float areaLength, float handleAt, float gapPx,
                        int min, int max) {
        float cell = Math.max(1f, handleAt - areaStart);
        int count = Math.round((areaLength + gapPx) / (cell + gapPx));
        return Math.max(min, Math.min(max, count));
    }

    /**
     * The handle a finger is on, within the platform's 48dp target around it, or null. The
     * nearest wins, so the keyboard's two handles on a short keyboard still resolve to one.
     */
    @Nullable
    private Handle handleAt(float x, float y) {
        Handle best = null;
        float bestDistance = Float.MAX_VALUE;
        float half = dp(HANDLE_TOUCH_HALF_DP);
        for (Map.Entry<Handle, RectF> entry : mHandleRects.entrySet()) {
            RectF rect = entry.getValue();
            float dx = rect.centerX() - x;
            float dy = rect.centerY() - y;
            if (Math.abs(dx) > half || Math.abs(dy) > half) continue;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Takes hold of a handle with the finger already down on it. */
    private boolean beginHandle(@NonNull Handle handle, float x, float y) {
        if (mLayout == null) return false;
        mDraggedHandle = handle;
        mDownX = x;
        mDownY = y;
        switch (handle) {
            case DOCK_HEIGHT: {
                RectF band = mBlockRects.get(Block.APPS_ROW);
                if (band == null) return abandonHandle();
                mHandleStartScale = mDockScale;
                mHandleStartExtentPx = edgeOfBlock(Block.APPS_ROW).isOnSide()
                    ? band.width() : band.height();
                break;
            }
            case KEYBOARD_HEIGHT:
                if (mKeyboardRect.isEmpty()) return abandonHandle();
                mHandleStartScale = mKeyboardScale;
                mHandleStartExtentPx = mKeyboardRect.height();
                break;
            case KEYBOARD_CHIN:
                if (mKeyboardRect.isEmpty()) return abandonHandle();
                mHandleStartChin = mKeyboardChinDp;
                break;
            case WIDGET_GRID:
            default: {
                RectF area = gridArea();
                RectF grip = mHandleRects.get(Handle.WIDGET_GRID);
                if (area == null || grip == null) return abandonHandle();
                mHandleStartRect.set(grip);
                mHandleLastColumns = mLayout.widgetColumns;
                mHandleLastRows = mLayout.widgetRows;
                break;
            }
        }
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        invalidate();
        return true;
    }

    private boolean abandonHandle() {
        mDraggedHandle = null;
        return false;
    }

    /**
     * The held handle followed to {@code (x, y)}: the new value worked out in the store's units
     * and reported. The canvas grows under the finger when the editor answers with the value.
     */
    private void moveHandle(float x, float y) {
        Handle handle = mDraggedHandle;
        if (handle == null || mLayout == null || mEditListener == null) return;
        float dx = x - mDownX;
        float dy = y - mDownY;
        switch (handle) {
            case DOCK_HEIGHT: {
                float grow;
                switch (edgeOfBlock(Block.APPS_ROW)) {
                    case TOP: grow = dy; break;
                    case LEFT: grow = isRtl() ? -dx : dx; break;
                    case RIGHT: grow = isRtl() ? dx : -dx; break;
                    case BOTTOM:
                    default: grow = -dy; break;
                }
                mEditListener.onDockHeightDragged(scaleFor(mHandleStartScale,
                    mHandleStartExtentPx, grow, TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
                    TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT));
                return;
            }
            case KEYBOARD_HEIGHT:
                mEditListener.onKeyboardHeightDragged(scaleFor(mHandleStartScale,
                    mHandleStartExtentPx, -dy, TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
                return;
            case KEYBOARD_CHIN: {
                // The chin is drawn to scale, so a pixel of drag is 1/scale of a real dp.
                float perDp = Math.max(0.01f, mCanvasScale);
                int chin = Math.round(mHandleStartChin + (-dy) / perDp);
                mEditListener.onKeyboardChinDragged(LayoutCanvasGeometry.chinDp(chin));
                return;
            }
            case WIDGET_GRID:
            default: {
                RectF area = gridArea();
                if (area == null) return;
                float gap = 6f * mUnit;
                int columns = cellsFor(area.left, area.width(), mHandleStartRect.centerX() + dx,
                    gap, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
                int rows = cellsFor(area.top, area.height(), mHandleStartRect.centerY() + dy,
                    gap, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
                if (columns == mHandleLastColumns && rows == mHandleLastRows) return;
                mHandleLastColumns = columns;
                mHandleLastRows = rows;
                mEditListener.onWidgetGridDragged(columns, rows);
            }
        }
    }

    /**
     * A scale dragged: the extent the band had times what the finger added to it, over the extent
     * it had, times the scale it had — so the band's edge stays under the finger — in range.
     */
    @VisibleForTesting
    static float scaleFor(float startScale, float startExtentPx, float growPx, float min,
                          float max) {
        float extent = Math.max(1f, startExtentPx);
        float scale = startScale * Math.max(0f, extent + growPx) / extent;
        return Math.max(min, Math.min(max, scale));
    }

    private void releaseHandle() {
        if (mDraggedHandle == null) return;
        mDraggedHandle = null;
        mReadout = null;
        computeHandles();
        invalidate();
        if (mEditListener != null) mEditListener.onHandleReleased();
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
        if (mDraggedBar == Block.KEYBOARD) {
            // The keyboard has no edge to stand on: the tray, which switches it off, is its one
            // target, and a release anywhere else puts it back where it was.
            if (mFromPopup) {
                // Out of the popup the keyboard is hidden already: dropped anywhere on the phone
                // it is switched back on.
                if (mLayout != null && !mFrameRect.isEmpty()) {
                    mSlots.add(new MiniatureDragPolicy.Slot(Edge.BOTTOM, mFrameRect.left,
                        mFrameRect.top, mFrameRect.right, mFrameRect.bottom));
                }
            } else if (mLayout != null && !mTrayRect.isEmpty()) {
                mSlots.add(new MiniatureDragPolicy.Slot(null, -1, 0f, mTrayRect.left,
                    mTrayRect.top, mTrayRect.right, mTrayRect.bottom));
            }
            return;
        }
        MiniatureDragPolicy.Bar bar = mDraggedBar == null ? null : barOf(mDraggedBar);
        if (bar == null || mLayout == null) return;
        MiniatureDragPolicy.Targets targets =
            MiniatureDragPolicy.targets(mPlace, mOrientation, mLayout, bar);
        RectF free = mBlockRects.get(Block.CANVAS);
        if (free != null && !free.isEmpty()) {
            for (Edge edge : Edge.values())
                addEdgeSlots(edge, targets.gapsOn(edge), free, bar.element());
            // The gaps under the keyboard, only while there is a keyboard drawn to be under.
            if (keyboardInStack())
                addUnderKeyboardSlots(targets.gapsUnderKeyboard(), free, bar.element());
        }
        if (targets.tray && !mFromPopup && !mTrayRect.isEmpty()) {
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
        boolean overKeyboard = edge == Edge.BOTTOM && keyboardInStack();
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

    /**
     * Whether the keyboard takes a band of the bottom stack, with gaps on either side of it: it
     * does docked and split, but a floating keyboard is a card over the pane, which no band stands
     * under.
     */
    private boolean keyboardInStack() {
        return mKeyboardInStack && !mKeyboardRect.isEmpty();
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

    /** The policy's name for one of the layout canvas's bands, or null for a band with no placement. */
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
        Element element = elementOf(bar);
        boolean underKeyboard = element != null
            && EdgeStackPolicy.standsUnderKeyboard(mLayout, element)
            && EdgeStackPolicy.claimsBand(mLayout, element);
        return getContext().getString(underKeyboard
            ? R.string.settings_layout_edge_under_keyboard
            : LayoutChooserModel.edgeLabel(edgeOfBlock(bar)));
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
            case KEYBOARD: label = getContext().getString(R.string.settings_layout_keyboard_title); break;
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

    // ---- Colours -------------------------------------------------------------------------------
    // The element pack's palette roles, resolved on the live Material scheme (spec 5):
    //   canvas -> colorSurface                  surface -> colorSurfaceContainerHigh
    //   terminal -> colorSurfaceContainerLow    key -> colorSurfaceContainerHighest
    //   line -> colorOutlineVariant             text / muted -> colorOnSurface / OnSurfaceVariant
    //   accent / accent_bg -> colorPrimary / colorPrimaryContainer
    //   secondary / secondary_bg -> colorTertiary / colorTertiaryContainer
    // A host whose theme lacks an attribute gets the launcher's own colour for the same role.

    /** The band's fill; the legend swatch uses the same one so the two are read as one thing. */
    @ColorInt
    private int bandFill(@NonNull Block block) {
        return blockColor(getContext(), block);
    }

    /**
     * The colour a block is drawn in, for anything outside the canvas that has to point at the
     * same band: the Layout page's rows carry a swatch of it beside the element's name. Every
     * strip is the scheme's surface container; the canvas is the scheme's surface.
     */
    @ColorInt
    public static int blockColor(@NonNull Context host, @NonNull Block block) {
        if (block == Block.CANVAS)
            return hostColor(host, com.google.android.material.R.attr.colorSurface,
                R.color.termux_surface_base);
        return hostColor(host, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            R.color.termux_surface_panel_high);
    }

    @ColorInt
    private static int hostColor(@NonNull Context host, @AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(host, attr, ContextCompat.getColor(host, fallbackColorRes));
    }

    /** The pack's canvas: the phone, and a lifted card's underlay. */
    @ColorInt
    private int surface() {
        return themeColor(com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base);
    }

    /** The pack's surface: every strip, and the keyboard's shell. */
    @ColorInt
    private int container() {
        return themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh,
            R.color.termux_surface_panel_high);
    }

    /** The pack's terminal: the pane. */
    @ColorInt
    private int terminalFill() {
        return MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerLow, surface());
    }

    /** The pack's key: a key, a bar of dim text, a media track. */
    @ColorInt
    private int keyFill() {
        return MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            ColorUtils.blendARGB(container(), onVariant(), 0.14f));
    }

    /** The pack's line: every card's hairline and the phone's rim. */
    @ColorInt
    private int lineColor() {
        return themeColor(com.google.android.material.R.attr.colorOutlineVariant,
            R.color.termux_outline_variant);
    }

    /** The pack's muted ink: secondary glyphs and text. */
    @ColorInt
    private int onVariant() {
        return themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
    }

    /** Placeholders and dashes: the muted ink at the design's 45%. */
    @ColorInt
    private int dim() {
        return ColorUtils.setAlphaComponent(onVariant(), DIM_ALPHA);
    }

    /** The pack's text. */
    @ColorInt
    private int text() {
        return surfaceInk();
    }

    /** The screen's own ink, for the legend, the tray's chip names and the pack's text. */
    @ColorInt
    private int surfaceInk() {
        return themeColor(com.google.android.material.R.attr.colorOnSurface,
            R.color.termux_on_surface);
    }

    /** The pack's accent. */
    @ColorInt
    private int accent() {
        return themeColor(androidx.appcompat.R.attr.colorPrimary,
            R.color.termux_primary);
    }

    /** The pack's accent background. */
    @ColorInt
    private int accentBg() {
        return themeColor(com.google.android.material.R.attr.colorPrimaryContainer,
            R.color.termux_accent_container);
    }

    /** The pack's secondary. */
    @ColorInt
    private int secondary() {
        return themeColor(com.google.android.material.R.attr.colorTertiary,
            R.color.termux_secondary);
    }

    /** The pack's secondary background. */
    @ColorInt
    private int secondaryBg() {
        return themeColor(com.google.android.material.R.attr.colorTertiaryContainer,
            R.color.termux_tertiary_container);
    }

    /** The accent as a tonal layer over a container: a lifted card, an active tray. */
    @ColorInt
    private int accentWash() {
        return ColorUtils.setAlphaComponent(accent(), ACCENT_WASH_ALPHA);
    }

    /** The phone's corner radius: the design's, at this frame's unit. */
    @VisibleForTesting
    float frameRadiusPx() {
        if (mFillsView && mFrameRadiusOverridePx >= 0f
            && mFrameRect.width() >= getWidth() && mFrameRect.height() >= getHeight())
            return mFrameRadiusOverridePx;
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

    /** The docked key rows, and the split ones, which never change; laid out once. */
    private static final List<List<LayoutCanvasGeometry.KeyCell>> DOCKED_KEY_ROWS =
        LayoutCanvasGeometry.dockedKeyRows();
    private static final LayoutCanvasGeometry.SplitRows SPLIT_KEY_ROWS =
        LayoutCanvasGeometry.splitKeyRows();

    /**
     * One shape the canvas draws, as the model gave it: a rect in view pixels and four corner radii
     * (the Docked frame's carries the opening cut out of it too). What the fill, the outline, the
     * lifted copy and the placeholder are painted from, and what a test compares to the model's.
     */
    static final class ShapeSpec {
        @NonNull final RectF box;
        /** Eight values, clockwise from the top left, as {@code Path.addRoundRect} takes them. */
        @NonNull final float[] radii;
        @Nullable final RectF hole;

        ShapeSpec(@NonNull RectF box, @NonNull float[] radii, @Nullable RectF hole) {
            this.box = box;
            this.radii = radii;
            this.hole = hole;
        }
    }

    @NonNull
    private ShapeSpec specOf(@NonNull Box box, @NonNull Corners corners) {
        return new ShapeSpec(viewRect(box), corners.toRadii(), null);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null || mFrameRect.isEmpty()) return;
        float radius = frameRadiusPx();
        mFillPaint.setColor(surface());
        canvas.drawRoundRect(mFrameRect, radius, radius, mFillPaint);

        // Everything is clipped to the phone's rounded outline, so a lifted card's underlay that
        // reaches the rim follows the curve instead of poking a square corner past it.
        mClipPath.reset();
        mClipPath.addRoundRect(mFrameRect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(mClipPath);
        drawChrome(canvas);
        drawPlaceholder(canvas);
        drawSlots(canvas);
        drawSelection(canvas);
        canvas.restoreToCount(saved);

        mFramePaint.setStrokeWidth(frameStrokePx());
        mFramePaint.setColor(lineColor());
        canvas.drawRoundRect(mFrameRect, radius, radius, mFramePaint);

        drawHandles(canvas);
        drawTray(canvas);
        drawTrash(canvas);
        drawLegend(canvas);
        drawGhost(canvas);
        drawReadout(canvas);
    }

    /** The pack's roles on the live Material scheme, read for this frame. */
    @NonNull
    private LayoutCanvasArtwork.Palette palette() {
        LayoutCanvasArtwork.Palette p = mPalette;
        p.canvas = surface();
        p.surface = container();
        p.terminal = terminalFill();
        p.key = keyFill();
        p.line = lineColor();
        p.text = text();
        p.muted = onVariant();
        p.accent = accent();
        p.accentBg = accentBg();
        p.secondary = secondary();
        p.secondaryBg = secondaryBg();
        return p;
    }

    /**
     * The chrome as the model shaped it: at rest the Docked frame or the Floating cards, and
     * while Style changes each piece part-way between the shape it stood in and the one it is
     * going to.
     */
    private void drawChrome(@NonNull Canvas canvas) {
        ChromeShape shape = mShape;
        if (shape == null) return;
        LayoutCanvasArtwork.Palette p = palette();
        float k = Math.max(0.01f, mCanvasScale);
        ChromeShape from = mMorphFrom;
        if (from != null && morphProgress() >= 1f) {
            mMorphFrom = null;
            mMorphHeld = -1f;
            from = null;
        }
        if (from != null) {
            drawMorphing(canvas, LayoutCanvasMorph.blend(from, shape,
                LayoutCanvasMorph.ease(morphProgress())), p, k);
            postInvalidateOnAnimation();
        } else if (mStyle == LayoutStyle.DOCKED) {
            drawDocked(canvas, shape, p, k);
        } else {
            drawFloating(canvas, shape, p, k);
        }
    }

    /**
     * Docked: the joined frame composed under one outer clip — its square box with the pane's
     * rounded insert cut out — or, with no bar at a side, the top and bottom edge cards, each
     * under its own clip (square at the screen, rounded inside); so no bar rounds, outlines or
     * joins on its own. Then the insert, wearing the one rim, and anything that floats over it.
     */
    private void drawDocked(@NonNull Canvas canvas, @NonNull ChromeShape shape,
                            @NonNull LayoutCanvasArtwork.Palette p, float k) {
        for (Card card : shape.cards()) {
            if (!card.docked()) continue;
            // An edge card whose every bar is in the air leaves only the placeholder behind.
            if (card.edge && isLiftedCard(shape, card)) continue;
            framePath(card, mShapePath);
            int saved = canvas.save();
            canvas.clipPath(mShapePath);
            mFillPaint.setColor(p.surface);
            canvas.drawPath(mShapePath, mFillPaint);
            for (Piece piece : shape.pieces()) {
                if (piece.overlay || isLiftedPiece(piece.id)) continue;
                if (!card.members.contains(piece.id)) continue;
                drawPieceArt(canvas, piece.id, viewRect(piece.box), isPieceVertical(piece.id),
                    false, p, k);
            }
            canvas.restoreToCount(saved);
        }
        drawPane(canvas, shape, true, p, k);
        drawOverlays(canvas, shape, p, k);
    }

    /**
     * Floating: each card one shape, filled and outlined with the pack's line, its pieces' artwork
     * inside it; then the pane, and the card a floating keyboard is, over it.
     */
    private void drawFloating(@NonNull Canvas canvas, @NonNull ChromeShape shape,
                              @NonNull LayoutCanvasArtwork.Palette p, float k) {
        for (Card card : shape.cards()) {
            if (isOverlayCard(shape, card) || isLiftedCard(shape, card)) continue;
            RectF box = viewRect(card.box);
            roundPath(mShapePath, box, card.corners);
            mFillPaint.setColor(p.surface);
            canvas.drawPath(mShapePath, mFillPaint);
            int saved = canvas.save();
            canvas.clipPath(mShapePath);
            for (PieceId id : card.members) {
                Piece piece = shape.piece(id);
                if (piece == null || isLiftedPiece(id)) continue;
                drawPieceArt(canvas, id, viewRect(piece.box), isPieceVertical(id), false, p, k);
            }
            canvas.restoreToCount(saved);
            strokeRim(canvas, box, card.corners, p);
        }
        drawPane(canvas, shape, true, p, k);
        drawOverlays(canvas, shape, p, k);
    }

    /** The pane: the card it is under Floating, the rounded insert on the frame under Docked. */
    private void drawPane(@NonNull Canvas canvas, @NonNull ChromeShape shape, boolean rim,
                          @NonNull LayoutCanvasArtwork.Palette p, float k) {
        if (shape.panes().isEmpty()) return;
        Pane pane = shape.panes().get(0);
        RectF box = viewRect(pane.box);
        if (box.isEmpty()) return;
        drawSurface(canvas, null, box, pane.corners, p.terminal, rim, false, p, k);
    }

    /** What floats over the pane: the keyboard, when its form is the floating one. */
    private void drawOverlays(@NonNull Canvas canvas, @NonNull ChromeShape shape,
                              @NonNull LayoutCanvasArtwork.Palette p, float k) {
        for (Piece piece : shape.pieces()) {
            if (!piece.overlay || isLiftedPiece(piece.id)) continue;
            drawSurface(canvas, piece.id, viewRect(piece.box), piece.corners, p.surface, true,
                true, p, k);
        }
    }

    /** Style changing: every piece and the pane at the shape the change has reached. */
    private void drawMorphing(@NonNull Canvas canvas, @NonNull LayoutCanvasMorph.Blend blend,
                              @NonNull LayoutCanvasArtwork.Palette p, float k) {
        for (LayoutCanvasMorph.Part part : blend.pieces) {
            if (part.overlay || part.id == null || isLiftedPiece(part.id)) continue;
            drawSurface(canvas, part.id, viewRect(part.box), part.corners, p.surface, false,
                false, p, k);
        }
        LayoutCanvasMorph.Part pane = blend.pane;
        if (pane != null && !viewRect(pane.box).isEmpty()) {
            drawSurface(canvas, null, viewRect(pane.box), pane.corners, p.terminal, false, false,
                p, k);
        }
        for (LayoutCanvasMorph.Part part : blend.pieces) {
            if (!part.overlay || part.id == null || isLiftedPiece(part.id)) continue;
            drawSurface(canvas, part.id, viewRect(part.box), part.corners, p.surface, true, true,
                p, k);
        }
    }

    /**
     * One shape filled, with the artwork of the piece it is (or the pane's, for a null id) inside
     * it, and outlined with the pack's line where {@code rim}.
     */
    private void drawSurface(@NonNull Canvas canvas, @Nullable PieceId id, @NonNull RectF box,
                             @NonNull Corners corners, @ColorInt int fill, boolean rim,
                             boolean overlay, @NonNull LayoutCanvasArtwork.Palette p, float k) {
        roundPath(mShapePath, box, corners);
        mFillPaint.setColor(fill);
        canvas.drawPath(mShapePath, mFillPaint);
        int saved = canvas.save();
        canvas.clipPath(mShapePath);
        if (id == null) drawPaneArt(canvas, box, p, k);
        else drawPieceArt(canvas, id, box, isPieceVertical(id), overlay, p, k);
        canvas.restoreToCount(saved);
        if (rim) strokeRim(canvas, box, corners, p);
    }

    /** The pack's hairline around a Floating card. */
    private void strokeRim(@NonNull Canvas canvas, @NonNull RectF box, @NonNull Corners corners,
                           @NonNull LayoutCanvasArtwork.Palette p) {
        roundPath(mShapePath, box, corners);
        mLinePaint.setColor(p.line);
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        canvas.drawPath(mShapePath, mLinePaint);
    }

    /** A rounded rect with its own four radii: every shape the model gives is drawn through this. */
    private static void roundPath(@NonNull Path out, @NonNull RectF box, @NonNull Corners corners) {
        out.reset();
        out.setFillType(Path.FillType.WINDING);
        out.addRoundRect(box, corners.toRadii(), Path.Direction.CW);
    }

    /** A Docked card: its box and corners, with each rounded insert cut out (even-odd) if any. */
    private void framePath(@NonNull Card frame, @NonNull Path out) {
        out.reset();
        out.setFillType(Path.FillType.EVEN_ODD);
        out.addRoundRect(viewRect(frame.box), frame.corners.toRadii(), Path.Direction.CW);
        for (Box hole : frame.holes)
            out.addRoundRect(viewRect(hole), frame.holeCorners.toRadii(), Path.Direction.CW);
    }

    /** Whether this card is a floating keyboard's: one piece, over the pane. */
    private static boolean isOverlayCard(@NonNull ChromeShape shape, @NonNull Card card) {
        for (PieceId id : card.members) {
            Piece piece = shape.piece(id);
            if (piece != null && piece.overlay) return true;
        }
        return false;
    }

    /** Whether every piece of this card is in the air, so it leaves only a placeholder behind. */
    private boolean isLiftedCard(@NonNull ChromeShape shape, @NonNull Card card) {
        if (card.members.isEmpty()) return false;
        for (PieceId id : card.members) {
            if (!isLiftedPiece(id)) return false;
        }
        return true;
    }

    /** Whether the bar in the air is this piece: the keyboard's pieces are the keyboard's. */
    private boolean isLiftedPiece(@NonNull PieceId id) {
        Block bar = mDraggedBar;
        if (bar == null) return false;
        if (bar == Block.KEYBOARD) return id.element() == null;
        return id.element() != null && id.element() == elementOf(bar);
    }

    private boolean isElementVertical(@NonNull Element element) {
        return mLayout != null && EdgeStackPolicy.edgeOf(mLayout, element).isOnSide();
    }

    private boolean isPieceVertical(@NonNull PieceId id) {
        Element element = id.element();
        return element != null && isElementVertical(element);
    }

    /**
     * One piece's artwork in the bounds the model gave it. A bar standing in a column keeps its
     * symbols upright; a keyboard's keys are the real rows, parted into two halves when split.
     */
    private void drawPieceArt(@NonNull Canvas canvas, @NonNull PieceId id, @NonNull RectF box,
                              boolean vertical, boolean overlay,
                              @NonNull LayoutCanvasArtwork.Palette p, float k) {
        switch (id) {
            case STATUS:
                if (vertical) mArtwork.statusColumn(canvas, box, p, k);
                else mArtwork.status(canvas, box, isStatusCompact(), p, k);
                break;
            case APPS:
                mArtwork.dock(canvas, box, vertical, mPinnedAppCount, p, k);
                break;
            case AZ:
                mArtwork.alphabet(canvas, box, vertical, p, k);
                break;
            case EXTRA_KEYS:
                mArtwork.extraKeys(canvas, box, vertical, mExtraKeyCount, p, k);
                break;
            case KEYBOARD_LEFT:
                mArtwork.keyboard(canvas, box, overlay ? 0f : chinPx(box), SPLIT_KEY_ROWS.left,
                    LayoutCanvasGeometry.HALF_UNITS, p, k);
                break;
            case KEYBOARD_RIGHT:
                mArtwork.keyboard(canvas, box, overlay ? 0f : chinPx(box), SPLIT_KEY_ROWS.right,
                    LayoutCanvasGeometry.HALF_UNITS, p, k);
                break;
            case KEYBOARD:
            default:
                mArtwork.keyboard(canvas, box, overlay ? 0f : chinPx(box), DOCKED_KEY_ROWS,
                    LayoutCanvasGeometry.ROW_UNITS, p, k);
                break;
        }
    }

    /** The place's own content on the pane: terminal lines, Home's widget grid, the display. */
    private void drawPaneArt(@NonNull Canvas canvas, @NonNull RectF pane,
                             @NonNull LayoutCanvasArtwork.Palette p, float k) {
        switch (canvasKind()) {
            case HOME_GRID:
                drawHomeGrid(canvas, pane);
                break;
            case DISPLAY:
                drawDisplayCanvas(canvas, pane);
                break;
            case TERMINAL:
            default:
                mArtwork.terminal(canvas, pane, p, k);
                break;
        }
    }

    // ---- What the shapes are, for the outline, the placeholder and the tests --------------------

    /**
     * The shape a block's fill is painted with: under Docked the frame (its box, its corners and
     * the opening cut out of it) for every bar and the opening itself for the pane; under Floating
     * the card the bar stands in, or the pane's card.
     */
    @Nullable
    @VisibleForTesting
    ShapeSpec fillShape(@NonNull Block block) {
        ChromeShape shape = mShape;
        if (shape == null) return null;
        if (block == Block.CANVAS) {
            if (shape.panes().isEmpty()) return null;
            Pane pane = shape.panes().get(0);
            return specOf(pane.box, pane.corners);
        }
        Piece piece = null;
        if (block == Block.KEYBOARD) {
            if (!mKeyboardPieces.isEmpty()) piece = mKeyboardPieces.get(0);
        } else {
            Element element = elementOf(block);
            if (element != null) piece = shape.piece(PieceId.of(element));
        }
        if (piece == null) return null;
        Card card = shape.cardOf(piece);
        if (card == null) return specOf(piece.box, piece.corners);
        return new ShapeSpec(viewRect(card.box), card.corners.toRadii(),
            card.hole == null ? null : viewRect(card.hole));
    }

    /**
     * The shapes the one outline is stroked along: the element's own piece (the keyboard's pieces,
     * or the pane's opening) at the corners the model gave it.
     */
    @NonNull
    @VisibleForTesting
    List<ShapeSpec> selectionShapes(@NonNull Block block) {
        List<ShapeSpec> out = new ArrayList<>(2);
        ChromeShape shape = mShape;
        if (shape == null) return out;
        if (block == Block.KEYBOARD) {
            if (mKeyboardRect.isEmpty()) return out;
            for (Piece piece : mKeyboardPieces) out.add(specOf(piece.box, piece.corners));
            return out;
        }
        if (block == Block.CANVAS) {
            if (!shape.panes().isEmpty()) {
                Pane pane = shape.panes().get(0);
                out.add(specOf(pane.box, pane.corners));
            }
            return out;
        }
        Element element = elementOf(block);
        Piece piece = element == null ? null : shape.piece(PieceId.of(element));
        if (piece != null) out.add(specOf(piece.box, piece.corners));
        return out;
    }

    /**
     * The shapes of the dashed placeholder a lifted bar leaves: where it would land and the shape
     * it would have there while it hovers a target, otherwise where it stood.
     */
    @NonNull
    @VisibleForTesting
    List<ShapeSpec> placeholderShapes() {
        List<ShapeSpec> out = new ArrayList<>(2);
        Block bar = mDraggedBar;
        ChromeShape shape = mShape;
        if (bar == null || shape == null) return out;
        if (bar == Block.KEYBOARD) {
            for (Piece piece : mKeyboardPieces) out.add(specOf(piece.box, piece.corners));
            return out;
        }
        Piece drop = hoveredDropShape();
        if (drop != null) {
            out.add(specOf(drop.box, drop.corners));
            return out;
        }
        Element element = elementOf(bar);
        Piece piece = element == null ? null : shape.piece(PieceId.of(element));
        if (piece != null) out.add(specOf(piece.box, piece.corners));
        return out;
    }

    /** The shape the target the finger is over would give the lifted bar, or null over none. */
    @Nullable
    private Piece hoveredDropShape() {
        MiniatureDragPolicy.Slot slot = mHoverSlot;
        return slot != null && !slot.isTray() && slot == mDropShapeFor ? mDropShape : null;
    }

    /**
     * The one outline the selected element wears: {@value #SELECTION_STROKE_DP}dp of the theme's
     * primary along its shape. Nothing else glows. A lifted element wears the lift instead.
     */
    private void drawSelection(@NonNull Canvas canvas) {
        if (mSelected == null || mSelected == mDraggedBar) return;
        float stroke = dp(SELECTION_STROKE_DP);
        mLinePaint.setColor(accent());
        mLinePaint.setStrokeWidth(stroke);
        for (ShapeSpec shape : selectionShapes(mSelected)) {
            strokeInside(canvas, shape, stroke / 2f, mLinePaint);
        }
    }

    /** A shape stroked just inside its own edge, so the line reads in full at the frame's edge. */
    private void strokeInside(@NonNull Canvas canvas, @NonNull ShapeSpec shape, float inset,
                              @NonNull Paint paint) {
        mScratchRectB.set(shape.box);
        mScratchRectB.inset(inset, inset);
        if (mScratchRectB.isEmpty()) return;
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) radii[i] = Math.max(0f, shape.radii[i] - inset);
        mShapePath.reset();
        mShapePath.setFillType(Path.FillType.WINDING);
        mShapePath.addRoundRect(mScratchRectB, radii, Path.Direction.CW);
        canvas.drawPath(mShapePath, paint);
    }

    /** The dashed outline a lifted bar leaves where it would land. */
    private void drawPlaceholder(@NonNull Canvas canvas) {
        if (mDraggedBar == null) return;
        mDashPaint.setPathEffect(mSlotDash);
        mDashPaint.setStrokeWidth(dp(1f));
        mDashPaint.setColor(dim());
        mDashPaint.setAlpha(PLACEHOLDER_ALPHA);
        for (ShapeSpec shape : placeholderShapes()) {
            strokeInside(canvas, shape, dp(0.5f), mDashPaint);
        }
    }

    /** The selection's handles: a pill on each edge it moves, a dot on the grid's corner. */
    private void drawHandles(@NonNull Canvas canvas) {
        if (mHandleRects.isEmpty() || mDraggedBar != null) return;
        int accent = accent();
        for (Map.Entry<Handle, RectF> entry : mHandleRects.entrySet()) {
            RectF rect = entry.getValue();
            boolean held = entry.getKey() == mDraggedHandle;
            if (entry.getKey() == Handle.WIDGET_GRID) {
                float r = rect.width() / 2f * (held ? 1.3f : 1f);
                mFillPaint.setColor(accent);
                canvas.drawCircle(rect.centerX(), rect.centerY(), r, mFillPaint);
                mLinePaint.setColor(surface());
                mLinePaint.setStrokeWidth(dp(1.5f));
                canvas.drawCircle(rect.centerX(), rect.centerY(), r, mLinePaint);
                continue;
            }
            float r = Math.min(rect.width(), rect.height()) / 2f;
            // A rim of the surface around the pill, so it reads on the band's own fill.
            mScratchRectB.set(rect);
            mScratchRectB.inset(-dp(1.5f), -dp(1.5f));
            mFillPaint.setColor(surface());
            canvas.drawRoundRect(mScratchRectB, r + dp(1.5f), r + dp(1.5f), mFillPaint);
            mFillPaint.setColor(accent);
            canvas.drawRoundRect(rect, r, r, mFillPaint);
        }
    }

    /**
     * The held handle's readout, in the real units the editor gave it: a small inverse pill beside
     * the handle, kept inside the view. Gone the moment the finger lifts.
     */
    private void drawReadout(@NonNull Canvas canvas) {
        if (mReadout == null || mDraggedHandle == null) return;
        RectF handle = mHandleRects.get(mDraggedHandle);
        if (handle == null) return;
        mLegendPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, READOUT_TEXT_SP,
            getResources().getDisplayMetrics()));
        mLegendPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mLegendPaint.setLetterSpacing(0f);
        float textWidth = mLegendPaint.measureText(mReadout);
        float width = textWidth + 2f * dp(READOUT_PAD_H_DP);
        float height = dp(READOUT_HEIGHT_DP);
        float left = handle.centerX() - width / 2f;
        left = Math.max(dp(2), Math.min(getWidth() - dp(2) - width, left));
        float top = handle.top - dp(READOUT_GAP_DP) - height;
        if (top < dp(2)) top = handle.bottom + dp(READOUT_GAP_DP);
        mScratchRectB.set(left, top, left + width, top + height);
        mFillPaint.setColor(surfaceInk());
        canvas.drawRoundRect(mScratchRectB, height / 2f, height / 2f, mFillPaint);
        mLegendPaint.setColor(surface());
        float baseline = mScratchRectB.centerY()
            - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f;
        canvas.drawText(mReadout, mScratchRectB.centerX() - textWidth / 2f, baseline,
            mLegendPaint);
        mLegendPaint.setTypeface(Typeface.DEFAULT);
    }

    /** One design unit, in pixels, for this frame. */
    private float u(float units) {
        return units * mUnit;
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
            canvas.drawRoundRect(area, tileRadiusPx(), tileRadiusPx(), mFillPaint);
            return; // the "n×m" figure is in the legend, so nothing else is written here
        }
        int columns = Math.max(1, mLayout.widgetColumns);
        int rows = Math.max(1, mLayout.widgetRows);
        float cellGap = 6f * mUnit;
        float cellW = (area.width() - cellGap * (columns - 1)) / columns;
        float cellH = (area.height() - cellGap * (rows - 1)) / rows;
        float radius = Math.min(tileRadiusPx(), Math.min(cellW, cellH) * 0.3f);
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

    /** A widget tile's and the display window's corner, in pixels at the canvas scale. */
    private float tileRadiusPx() {
        return TILE_CORNER_DP * mCanvasScale;
    }

    /** The corner a drop-target outline and the tray's drop zone wear, in pixels. */
    private float slotRadiusPx() {
        return SLOT_CORNER_DP * mCanvasScale;
    }

    /**
     * The display: a window on the desk. A keyboard that floats over it is a card of the chrome,
     * drawn over the pane by {@link #drawOverlays}, not here.
     */
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
        float radius = tileRadiusPx();
        canvas.drawRoundRect(window, radius, radius, mFillPaint);
        mLinePaint.setColor(lineColor());
        mLinePaint.setStrokeWidth(u(CARD_STROKE_U));
        canvas.drawRoundRect(window, radius, radius, mLinePaint);
        float barH = Math.min(u(8f), window.height() * 0.2f);
        mFillPaint.setColor(dim());
        mScratchRectA.set(window.left, window.top, window.right, window.top + barH);
        canvas.drawRoundRect(mScratchRectA, radius, radius, mFillPaint);
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
        int outline = lineColor();
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
            case EXTRA_KEYS:
            case KEYBOARD: icon = mIconKeys; break;
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

    /**
     * Every edge the lifted bar may stand on, outlined as one region the way it always has been,
     * and — on the edge under the finger — the gaps inside it: a thin line where each would put the
     * band, the one being dropped into filled and drawn solid. Nothing here animates; the picture
     * changes when the finger moves to another gap and not otherwise.
     */
    private void drawSlots(@NonNull Canvas canvas) {
        if (mSlots.isEmpty()) return;
        int accent = accent();
        float radius = slotRadiusPx();
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

    /**
     * What the trash under the phone is showing; null while it is not drawn at all. OFFERING: a
     * lifted element may be dropped on it; CHIPS: something is hidden (the filled, badged icon);
     * EMPTY: nothing is (the outline).
     */
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
        return hiddenBlocks().isEmpty() ? TrayState.EMPTY : TrayState.CHIPS;
    }

    /**
     * The strip under the phone while an element is in the air and may be put away: a drop zone
     * over the trash icon, opaque so the icon under it does not read through, that takes a wash
     * of the accent once the finger is over it; the trash is drawn over it again, accented.
     * At rest only {@link #drawTrash} paints here, and only when this view has no host tray.
     */
    private void drawTray(@NonNull Canvas canvas) {
        if (trayState() != TrayState.OFFERING) return;
        float radius = slotRadiusPx();
        boolean active = mHoverSlot != null && mHoverSlot.isTray();
        mFillPaint.setColor(surface());
        canvas.drawRoundRect(mTrayRect, radius, radius, mFillPaint);
        if (active) {
            mFillPaint.setColor(accentWash());
            canvas.drawRoundRect(mTrayRect, radius, radius, mFillPaint);
        }
        mDashPaint.setPathEffect(mTrayDash);
        mDashPaint.setColor(accent());
        mDashPaint.setStrokeWidth(dp(TRAY_STROKE_DP));
        canvas.drawRoundRect(mTrayRect, radius, radius, mDashPaint);
    }


    private float traySizePx() {
        float fontScale = getResources().getConfiguration().fontScale;
        if (fontScale <= 0f) fontScale = 1f;
        return TRAY_TEXT_SP * getResources().getDisplayMetrics().density
            * Math.min(fontScale, LEGEND_MAX_FONT_SCALE);
    }

    // ---- The trash and the hidden-elements popup -------------------------------------------------

    /** The icon's state: outline while nothing is hidden, filled and counted once something is. */
    @VisibleForTesting
    @NonNull
    MiniatureDragPolicy.TrashState trashState() {
        return MiniatureDragPolicy.trashState(hiddenBlocks().size());
    }

    /**
     * The icon's square, standing on the tray's trailing side. A host tray is the icon itself;
     * this view's own strip is wider, so the icon takes its end.
     */
    @VisibleForTesting
    @NonNull
    RectF trashRect() {
        RectF out = new RectF();
        if (mTrayRect.isEmpty()) return out;
        float side = Math.min(mTrayRect.height(), Math.min(mTrayRect.width(), dp(TRAY_HEIGHT_DP)));
        if (isRtl())
            out.set(mTrayRect.left, mTrayRect.top, mTrayRect.left + side, mTrayRect.top + side);
        else
            out.set(mTrayRect.right - side, mTrayRect.top, mTrayRect.right, mTrayRect.top + side);
        return out;
    }

    /** The accessibility line for the icon: how many are hidden, or that none is. */
    @NonNull
    public String trashDescription() {
        int count = hiddenBlocks().size();
        return count == 0 ? getContext().getString(R.string.layout_editor_trash_empty)
            : getContext().getString(R.string.layout_editor_trash_filled, count);
    }

    /** The hidden elements, in the order the popup lists them. */
    @NonNull
    public List<Block> hiddenElements() {
        return hiddenBlocks();
    }

    /** The name a hidden element's chip wears. */
    @NonNull
    public String chipName(@NonNull Block block) {
        if (block == Block.KEYBOARD) return getContext().getString(R.string.layout_editor_keyboard);
        String name = barName(block);
        return name == null ? "" : name;
    }

    /** The glyph a hidden element's chip wears as its icon. */
    @DrawableRes
    public static int chipGlyph(@NonNull Block block) {
        switch (block) {
            case STATUS_BAR: return R.drawable.ic_symbol_notifications;
            case APPS_ROW: return R.drawable.ic_symbol_apps;
            case KEYBOARD:
            case EXTRA_KEYS: return R.drawable.ic_symbol_keyboard;
            default: return R.drawable.ic_symbol_visibility_off;
        }
    }

    /**
     * A drag of a hidden element's chip out of the editor's popup: the canvas accepts it and runs
     * the lift it has always run for a bar, hover placeholder and drop included. The chip itself is
     * the system's drag shadow; the canvas draws only the targets and, over one, the shape the
     * element would take. A drop reports through the same listener a bar's drop does.
     */
    @Override
    public boolean onDragEvent(@NonNull DragEvent event) {
        Object local = event.getLocalState();
        if (!(local instanceof Block)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return beginHiddenDrag((Block) local);
            case DragEvent.ACTION_DRAG_LOCATION:
                hoverHiddenDrag(event.getX(), event.getY());
                return true;
            case DragEvent.ACTION_DROP:
                return dropHiddenDrag(event.getX(), event.getY());
            case DragEvent.ACTION_DRAG_EXITED:
                hoverHiddenDrag(-1f, -1f);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                endHiddenDrag();
                return true;
            default:
                return true;
        }
    }

    /** A hidden element's chip is being dragged: lifts it, the copy starting at the trash. */
    @VisibleForTesting
    boolean beginHiddenDrag(@NonNull Block block) {
        if (mLayout == null || mDraggedBar != null) return false;
        // The system reports no reliable start point: the copy starts at the trash.
        RectF trash = trashRect().isEmpty() ? mTrayRect : trashRect();
        float cx = trash.isEmpty() ? getWidth() / 2f : trash.centerX();
        float cy = trash.isEmpty() ? getHeight() : trash.centerY();
        float halfW = dp(48f);
        float halfH = dp(16f);
        mFromPopup = true;
        if (!liftFrom(block, new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH), cx, cy)) {
            mFromPopup = false;
            return false;
        }
        return true;
    }

    /** The chip is over {@code (x, y)} in this view: the hover placeholder follows. */
    @VisibleForTesting
    void hoverHiddenDrag(float x, float y) {
        if (mDraggedBar != null) moveDrag(x, y);
    }

    /** Dropped at {@code (x, y)}: restores through the drop listener over a target, else nothing. */
    @VisibleForTesting
    boolean dropHiddenDrag(float x, float y) {
        if (mDraggedBar == null) return false;
        moveDrag(x, y);
        if (mHoverSlot == null) {
            endDrag();
            return false;
        }
        releaseDrag();
        return true;
    }

    /** The drag ended, wherever; a drag that dropped nowhere leaves the element hidden. */
    @VisibleForTesting
    void endHiddenDrag() {
        if (mDraggedBar != null) endDrag();
    }

    /**
     * The trash, for a view that has no host tray (the Layout editor's is a real view in its
     * bottom area, beside the canvas, and draws itself): an outline while nothing is hidden, a
     * filled glyph in the accent with a count badge once something is.
     */
    private void drawTrash(@NonNull Canvas canvas) {
        if (mFillsView || mLayout == null || mTrayRect.isEmpty()) return;
        RectF box = trashRect();
        int count = hiddenBlocks().size();
        boolean filled = count > 0;
        Drawable icon = filled ? mIconTrashFilled : mIconTrash;
        DrawableCompat.setTint(icon, filled || isTrayOffered() ? accent() : onVariant());
        int half = Math.round(dp(TRASH_ICON_DP) / 2f);
        int cx = Math.round(box.centerX());
        int cy = Math.round(box.centerY());
        icon.setBounds(cx - half, cy - half, cx + half, cy + half);
        icon.draw(canvas);
        if (!filled) return;
        float r = dp(BADGE_RADIUS_DP);
        float bx = cx + half - r / 2f;
        float by = cy - half + r / 2f;
        mFillPaint.setColor(accent());
        canvas.drawCircle(bx, by, r, mFillPaint);
        mLegendPaint.setTextSize(r * 1.2f);
        mLegendPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mLegendPaint.setColor(surface());
        String text = MiniatureDragPolicy.badgeText(count);
        float width = mLegendPaint.measureText(text);
        canvas.drawText(text, bx - width / 2f,
            by - (mLegendPaint.ascent() + mLegendPaint.descent()) / 2f, mLegendPaint);
        mLegendPaint.setTypeface(Typeface.DEFAULT);
    }

    // ---- The lifted copy -----------------------------------------------------------------------

    /**
     * Where the lifted copy is this frame, before its swell: its box, the corners it wears and
     * which way it stands. It keeps the shape of the bar it came from until it hovers a target, and
     * then changes into the shape the model says the bar would have there.
     */
    private static final class Ghost {
        final RectF box = new RectF();
        @NonNull Corners corners = Corners.SQUARE;
        boolean vertical;
    }

    private final Ghost mGhost = new Ghost();

    /**
     * Works out {@link #mGhost} for a lifted bar: its own piece's size and corners while the
     * finger is over no target, and over one the size and corners of the piece the model gives it
     * there ({@link ChromeShapeModel#shapeIfDropped}), by however far the change has come. The
     * copy follows the finger and, as it changes shape, comes to be centred on it. False while no
     * bar is lifted; the keyboard, which only goes to the tray, never changes shape.
     */
    private boolean computeGhost() {
        Block bar = mDraggedBar;
        ChromeShape shape = mShape;
        if (bar == null || bar == Block.KEYBOARD || shape == null || mLiftOrigin.isEmpty())
            return false;
        Element element = elementOf(bar);
        if (element == null) return false;
        Piece base = shape.piece(PieceId.of(element));
        if (base == null && !mFromPopup) return false;
        float morph = LayoutCanvasMorph.clamp01(mGhostMorph.value);
        float cx = mLiftOrigin.centerX() + mGhostX.value
            + (mDownX - mLiftOrigin.centerX()) * morph;
        float cy = mLiftOrigin.centerY() + mGhostY.value
            + (mDownY - mLiftOrigin.centerY()) * morph;
        float w = mLiftOrigin.width();
        float h = mLiftOrigin.height();
        // A chip out of the popup has no piece on the phone yet; it changes into the target's.
        Corners corners = base == null ? Corners.SQUARE : base.corners;
        boolean vertical = base != null && isElementVertical(element);
        Piece drop = mDropShape;
        if (drop != null && morph > 0f) {
            w = LayoutCanvasMorph.lerp(w, drop.box.width(), morph);
            h = LayoutCanvasMorph.lerp(h, drop.box.height(), morph);
            corners = LayoutCanvasMorph.lerp(base.corners, drop.corners, morph);
            if (morph >= 0.5f && mDropEdge != null) vertical = mDropEdge.isOnSide();
        }
        mGhost.box.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        if (morph > 0f) {
            // Changing into a bar the length of an edge, the copy is not let off the phone.
            float shiftX = 0f;
            float shiftY = 0f;
            if (mGhost.box.left < mFrameRect.left) shiftX = mFrameRect.left - mGhost.box.left;
            else if (mGhost.box.right > mFrameRect.right)
                shiftX = mFrameRect.right - mGhost.box.right;
            if (mGhost.box.top < mFrameRect.top) shiftY = mFrameRect.top - mGhost.box.top;
            else if (mGhost.box.bottom > mFrameRect.bottom)
                shiftY = mFrameRect.bottom - mGhost.box.bottom;
            mGhost.box.offset(shiftX * morph, shiftY * morph);
        }
        mGhost.corners = corners;
        mGhost.vertical = vertical;
        return true;
    }

    /** The lifted bar's shape this frame, before its swell; null while nothing is lifted. */
    @Nullable
    @VisibleForTesting
    ShapeSpec ghostShape() {
        if (!computeGhost()) return null;
        return new ShapeSpec(new RectF(mGhost.box), mGhost.corners.toRadii(), null);
    }

    /** How far the lifted copy has changed to the hovered target's shape: 0 to 1. */
    @VisibleForTesting
    float ghostMorph() {
        return mGhostMorph.value;
    }

    /** Takes every spring of the lift to where it is going, as the frames after a gesture would. */
    @VisibleForTesting
    void settleMotion() {
        mGhostMorph.reset(mGhostMorph.target);
        mGhostScale.reset(mGhostScale.target);
        if (mSpringingBack) {
            mGhostX.reset(mGhostX.target);
            mGhostY.reset(mGhostY.target);
        }
        invalidate();
    }

    /**
     * The lifted bar wherever the finger has taken it: its shape raised — a solid underlay a
     * little below it, a wash of accent over it and an accent rim, along the same corners.
     */
    private void drawGhost(@NonNull Canvas canvas) {
        Block bar = mDraggedBar;
        if (bar == null || mLiftOrigin.isEmpty() || mShape == null) return;
        LayoutCanvasArtwork.Palette p = palette();
        float k = Math.max(0.01f, mCanvasScale);
        float scale = mGhostScale.value;
        // Out of the popup the system's drag shadow is the chip until a target gives it a shape.
        if (mFromPopup && (bar == Block.KEYBOARD || mGhostMorph.value <= 0.01f)) return;
        if (bar == Block.KEYBOARD) {
            for (Piece piece : mKeyboardPieces) {
                mGhostRect.set(viewRect(piece.box));
                mGhostRect.offset(mGhostX.value, mGhostY.value);
                scaleAboutCentre(mGhostRect, scale);
                drawLifted(canvas, mGhostRect, piece.corners, piece.id, false, piece.overlay, p, k);
            }
            return;
        }
        Element element = elementOf(bar);
        if (element == null || !computeGhost()) return;
        mGhostRect.set(mGhost.box);
        scaleAboutCentre(mGhostRect, scale);
        drawLifted(canvas, mGhostRect, mGhost.corners, PieceId.of(element), mGhost.vertical,
            false, p, k);
    }

    private static void scaleAboutCentre(@NonNull RectF rect, float scale) {
        float halfWidth = rect.width() / 2f * scale;
        float halfHeight = rect.height() / 2f * scale;
        float cx = rect.centerX();
        float cy = rect.centerY();
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
    }

    /** One lifted shape: the raise, the card with its artwork, the wash and the accent rim. */
    private void drawLifted(@NonNull Canvas canvas, @NonNull RectF box, @NonNull Corners corners,
                            @NonNull PieceId id, boolean vertical, boolean overlay,
                            @NonNull LayoutCanvasArtwork.Palette p, float k) {
        mScratchRectC.set(box);
        mScratchRectC.offset(0f, u(LIFT_OFFSET_U));
        roundPath(mRaisePath, mScratchRectC, corners);
        mFillPaint.setColor(surface());
        canvas.drawPath(mRaisePath, mFillPaint);
        roundPath(mShapePath, box, corners);
        mFillPaint.setColor(p.surface);
        canvas.drawPath(mShapePath, mFillPaint);
        int saved = canvas.save();
        canvas.clipPath(mShapePath);
        drawPieceArt(canvas, id, box, vertical, overlay, p, k);
        canvas.restoreToCount(saved);
        mFillPaint.setColor(accentWash());
        canvas.drawPath(mShapePath, mFillPaint);
        mLinePaint.setColor(accent());
        mLinePaint.setStrokeWidth(u(LIFTED_STROKE_U));
        canvas.drawPath(mShapePath, mLinePaint);
    }

    // ---- The drag ------------------------------------------------------------------------------

    /**
     * The bar under a finger, which a press may go on to lift: the keyboard (its card first, since
     * a floating one stands over the pane) or one of the bars. Anywhere on it will do; there is no
     * handle to find.
     */
    @Nullable
    private Block barAt(float x, float y) {
        if (!mKeyboardRect.isEmpty() && mKeyboardRect.contains(x, y)) return Block.KEYBOARD;
        for (Block bar : BARS) {
            RectF rect = mBlockRects.get(bar);
            if (rect != null && rect.contains(x, y)) return bar;
        }
        return null;
    }

    /**
     * Lifts a bar with the finger already down on it. Refuses a bar the arrangement leaves
     * nowhere to go, so a lift never starts a gesture that cannot end anywhere.
     */
    private boolean beginDrag(@NonNull Block bar, float x, float y) {
        if (mLayout == null || (barOf(bar) == null && bar != Block.KEYBOARD)) return false;
        RectF origin = bar == Block.KEYBOARD ? mKeyboardRect : mBlockRects.get(bar);
        if (origin == null || origin.isEmpty()) return false;
        return liftFrom(bar, origin, x, y);
    }

    /** The lift itself, from wherever the copy starts: the bar's own place or its popup chip. */
    private boolean liftFrom(@NonNull Block bar, @NonNull RectF origin, float x, float y) {
        if (mLayout == null || (barOf(bar) == null && bar != Block.KEYBOARD)) return false;
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
        mGhostMorph.reset(0f);
        mDropShape = null;
        mDropShapeFor = null;
        mDropEdge = null;
        mSpringingBack = false;
        // The preference list must not take the touch for the rest of the gesture, however far
        // the finger travels off the picture.
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        mHoverSlot = MiniatureDragPolicy.slotUnder(mSlots, x, y);
        refreshDropShape();
        startMotion();
        invalidate();
        notifyTrayOffer();
        return true;
    }

    private void moveDrag(float x, float y) {
        mGhostX.reset(x - mDownX);
        mGhostY.reset(y - mDownY);
        mHoverSlot = MiniatureDragPolicy.slotUnder(mSlots, x, y);
        refreshDropShape();
        invalidate();
        notifyTrayOffer();
    }

    /**
     * Asks the model for the shape the hovered target would give the lifted bar — the same
     * question the placeholder is drawn from — and sets the copy changing into it; over no target
     * the copy changes back to the shape it was lifted with.
     */
    private void refreshDropShape() {
        MiniatureDragPolicy.Slot slot = mHoverSlot;
        Block bar = mDraggedBar;
        Element element = bar == null ? null : elementOf(bar);
        boolean over = element != null && slot != null && !slot.isTray() && slot.edge != null
            && mInput != null && !mSpringingBack;
        if (!over) {
            mGhostMorph.target = 0f;
            startMotion();
            return;
        }
        if (slot != mDropShapeFor) {
            mDropShape = ChromeShapeModel.shapeIfDropped(mInput, element, slot.edge, slot.index,
                slot.underKeyboard);
            mDropEdge = slot.edge;
            mDropShapeFor = slot;
        }
        mGhostMorph.target = 1f;
        startMotion();
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
        boolean fromPopup = mFromPopup;
        endDrag();
        if (bar == Block.KEYBOARD) {
            // The keyboard's one target is the tray: dropped there, it is switched off. Out of the
            // popup it is the other way round.
            if (mEditListener == null) return;
            if (fromPopup) mEditListener.onKeyboardRestored();
            else if (slot.isTray()) mEditListener.onKeyboardPutAway();
            return;
        }
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
        mGhostMorph.target = 0f;
        startMotion();
        invalidate();
        notifyTrayOffer();
    }

    private void endDrag() {
        boolean lifted = mDraggedBar != null;
        mDraggedBar = null;
        mPressedBar = null;
        mFromPopup = false;
        mSpringingBack = false;
        mSlots.clear();
        mHoverSlot = null;
        mDropShape = null;
        mDropShapeFor = null;
        mDropEdge = null;
        mLiftOrigin.setEmpty();
        mGhostX.reset(0f);
        mGhostY.reset(0f);
        mGhostScale.reset(1f);
        mGhostMorph.reset(0f);
        mLastFrameNanos = 0L;
        removeCallbacks(mMotionTick);
        if (lifted) invalidate();
        notifyTrayOffer();
    }

    private void startMotion() {
        if (mLastFrameNanos == 0L) mLastFrameNanos = System.nanoTime();
        removeCallbacks(mMotionTick);
        postOnAnimation(mMotionTick);
    }

    /**
     * The lift's swell, its change of shape over a target and a release's spring-back, on the
     * shared integrator. Reduced motion snaps all of them, which ends the gesture on the next
     * frame instead of animating it home.
     */
    private void advanceMotion() {
        if (mDraggedBar == null) return;
        long now = System.nanoTime();
        float dt = Spring.clampDelta((now - mLastFrameNanos) / 1_000_000_000f);
        mLastFrameNanos = now;
        boolean reduced = ReducedMotion.isEnabled(getContext());
        boolean moving = mGhostScale.tick(reduced, dt);
        moving |= mGhostMorph.tick(reduced, dt);
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

    // ---- Shared drawing helpers --------------------------------------------------------------

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /**
     * A touch-down on the selection's handle takes the handle. On a bar — the keyboard's card
     * included, anywhere on it — it waits to see whether the finger travels past the touch slop,
     * which lifts the bar, or comes up, which is a tap. Anything else is a tap, decided on the way
     * up, which selects what it landed on — or clears the selection off the phone. A touch-down on
     * the tray at rest is not this view's: the editor's chips stand there, under it.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mPressedBar = null;
                mPressedTrash = false;
                mDownX = x;
                mDownY = y;
                if (!mFillsView && trashRect().contains(x, y)) {
                    mPressedTrash = true;
                    return true;
                }
                Handle handle = handleAt(x, y);
                if (handle != null && beginHandle(handle, x, y)) return true;
                Block pressed = barAt(x, y);
                if (pressed != null) {
                    mPressedBar = pressed;
                    // Until it is known whether this is a tap or a lift, nothing between here
                    // and the sheet may take the finger.
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                // The trash standing over the tray is the editor's own view; the touch goes on to it.
                return !mTrayRect.contains(x, y);
            }
            case MotionEvent.ACTION_MOVE:
                if (mDraggedHandle != null) {
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    moveHandle(x, y);
                    return true;
                }
                if (mPressedBar != null && mDraggedBar == null
                    && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                    Block bar = mPressedBar;
                    mPressedBar = null;
                    float downX = mDownX;
                    float downY = mDownY;
                    if (beginDrag(bar, downX, downY)) moveDrag(x, y);
                    return true;
                }
                if (mDraggedBar != null && !mSpringingBack) {
                    // The editor's card is a sheet that scrolls and pulls; while a bar is in the
                    // air neither may take the finger, so the claim is restated on every move in
                    // case anything between here and the sheet let it go.
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    moveDrag(x, y);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mDraggedHandle != null) {
                    moveHandle(x, y);
                    releaseHandle();
                    return true;
                }
                if (mDraggedBar != null) {
                    releaseDrag();
                    return true;
                }
                if (mPressedTrash) {
                    mPressedTrash = false;
                    if (trashRect().contains(x, y) && mTrashTapListener != null)
                        mTrashTapListener.run();
                    return true;
                }
                mPressedBar = null;
                Block tapped = blockAt(x, y);
                setSelectedBlock(tapped);
                if (tapped != null && mListener != null) mListener.onBlockTapped(tapped);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mPressedBar = null;
                mPressedTrash = false;
                if (mDraggedHandle != null) releaseHandle();
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
        // A legend row is the block it names — and a hidden block's only tap target.
        for (Block block : LEGEND_ORDER) {
            RectF rect = mLegendRects.get(block);
            if (rect != null && rect.contains(x, y)) return block;
        }
        if (!mKeyboardRect.isEmpty() && mKeyboardRect.contains(x, y)) return Block.KEYBOARD;
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
