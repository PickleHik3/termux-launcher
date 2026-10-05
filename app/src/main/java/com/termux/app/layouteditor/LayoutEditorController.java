package com.termux.app.layouteditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.RectF;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.slider.Slider;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Layout mode of the one editor (appearance-layout-editor SPEC §3.5, layout editor v2): the layout
 * canvas of the place the user is looking at, drawn at the full size of the editor's frame, with
 * the Portrait / Landscape toggle and eye-off in the editor's bottom area. The layout it edits is
 * every place's (ADR 0003), so it has no place to pick: a write lands on Home, the terminal and
 * the display alike.
 *
 * <p>This class owns no screen of its own. The editor that hosts it
 * ({@link com.termux.app.surfaces.SurfaceEditorController}) lends it the canvas, what stands over
 * the canvas (the move control and the keyboard's tools) and the bottom area's orientation toggle,
 * eye-off and hidden tiles ({@link Views}), opens a session when the editor opens
 * ({@link #begin}), and asks it for its share of the editor's one dirty state, one Undo and one
 * Done ({@link #isDirty}, {@link #revert}, {@link #end}).
 *
 * <p>The canvas is the whole of Layout mode (DECISIONS items 2 to 8). A bar is pressed anywhere and
 * moved past the touch slop to lift it, and dropped on an edge, or on eye-off to put it away (the
 * only hide target, highlighted while it would accept the drop); while it hovers a target it takes
 * the shape the shape model says it would have there. Back or a release outside the canvas
 * cancels. A tap selects an element: one outline along its own shape, its valid edges outlined,
 * the move control outside it, whose menu sends it to an edge or hides it, and on the dock, the
 * keyboard and Home's grid a handle that resizes it with a readout while it is held. The keyboard,
 * selected, shows its type chips and Key radius in the sheet's Row B. Eye-off carries the count of hidden
 * elements and opens their tiles in the sheet; a tile brings its element back to the edge it left,
 * or is dragged onto the canvas to an edge of the user's choosing. Every write goes straight
 * through, so the live place under the canvas follows every edit made in the orientation the phone
 * is actually in; the other orientation moves on the canvas alone until the phone is turned.
 *
 * <p>Every decision here — what is shown, what a write does, whether the place follows, whether
 * anything has moved — belongs to {@link LayoutEditorPlan}; this class is the wiring that draws
 * its answers. {@link Host} is the seam to the activity.
 */
public final class LayoutEditorController {

    /** What the editor needs from the activity: the places and the chrome pass. */
    public interface Host {
        @NonNull Context context();

        /** Where the shared arrangement is kept, or null before the preferences exist. */
        @Nullable PlaceLayoutStore places();

        /** The place the chrome on screen belongs to: what an unnamed door opens the editor on. */
        @NonNull PaneWallPage placeOnScreen();

        /** The orientation the phone is in, which is the only one the live place can follow. */
        @NonNull PlaceOrientation placeOrientation();

        /**
         * A bar moved: re-lay every piece of chrome the arrangement decides, for the place on
         * screen, without tearing the editor down.
         */
        void applyPlaceArrangement();

        /**
         * Bring the pane wall to the place the editor is open on and hold its gestures, or hand
         * them back. What the canvas draws is what the user is looking at, so the wall stands
         * still on that place for as long as the editor is up.
         */
        void holdPaneWallOnPlace(@NonNull PaneWallPage place, boolean held);

        int themeColor(int attr, int fallbackRes);

        /**
         * Not read any more: the canvas's corners are the shape model's, from the user's Corners
         * ({@link LayoutEditorController#setShape}), not the dock's own radius.
         */
        default float dockCornerRadiusDp() {
            return 0f;
        }

        /**
         * How many keys the extra-keys bar holds in its first row, which is how many glyphs the
         * canvas's extra-keys bar draws; or -1 where the host does not know, which draws the
         * pack's seven. Only the activity has the parsed {@code extra-keys} property. The app
         * icons bar takes nothing from the host: it always draws seven placeholders (DECISIONS
         * item 8).
         */
        default int extraKeyCount() {
            return -1;
        }

        /**
         * The extra-keys bar's first row, in order, each key as the real bar shows it: the icon
         * it draws (a fresh Drawable the canvas may size and tint) or, for a key with no icon,
         * its text. Empty where unknown, which leaves the canvas on the pack's glyphs.
         */
        @NonNull
        default List<LayoutCanvasView.KeySlot> extraKeySlots() {
            return new ArrayList<>();
        }

        /**
         * How thick the live dock's pinned apps stand, in dp, as last laid out — or -1 when there
         * is no dock on screen to measure. What the dock's handle reads out.
         */
        default int measuredDockHeightDp() {
            return -1;
        }

        /**
         * How tall the live keyboard stands, in dp, as last laid out — or -1 while it is down.
         * What the keyboard's height handle reads out.
         */
        default int measuredKeyboardHeightDp() {
            return -1;
        }
    }

    /**
     * The bottom area's half of Row B's swaps: the hidden tiles in Corner radius and Margin's
     * place, and back; and the keyboard's tools (its type chips and Key radius) in the same place
     * while the keyboard is selected. Either way the sheet keeps its height.
     */
    public interface TilesHost {
        void setHiddenTilesOpen(boolean open);

        /** The keyboard's tools in Row B's place while {@code shown}, Corner radius and Margin back otherwise. */
        default void setKeyboardToolsShown(boolean shown) {}
    }

    /**
     * Key radius, which the hosting editor owns (it is a look value, written with the editor's
     * keyboard preview) and Layout mode shows beside the keyboard's type chips while the keyboard
     * is selected (DECISIONS item 6).
     */
    public interface KeyRadius {
        int value();

        int max();

        @NonNull String label(int value);

        void onChanged(int value, boolean dragging);

        void onReleased();
    }

    /**
     * The views the hosting editor lends Layout mode. The canvas stands in the frame and the move
     * control over the editor round it, so it may stand in the gutter outside the phone; the
     * orientation toggle, eye-off with its highlight, the hidden tiles and the keyboard's tools
     * stand in the bottom area.
     */
    public static final class Views {
        /** What the canvas and what stands over it stand in: the frame's own rect. */
        @NonNull final ViewGroup canvasHost;
        @NonNull final LayoutCanvasView canvas;
        /** The keyboard's tools: the type chips and Key radius, in the sheet's Row B. */
        @NonNull final View keyboardTools;
        @NonNull final ChipGroup keyboardForms;
        @NonNull final TextView keyRadiusLabel;
        @NonNull final Slider keyRadius;
        @NonNull final MaterialButton moveControl;
        @NonNull final MaterialButtonToggleGroup orientation;
        /** Eye-off: the only hide drop target, the tiles' toggle, the count badge's anchor. */
        @NonNull final MaterialButton hiddenControl;
        /** The highlight behind eye-off, larger than its 48dp target: the drop area itself. */
        @NonNull final View hiddenHighlight;
        @NonNull final ChipGroup hiddenTiles;
        @NonNull final TilesHost tilesHost;

        public Views(@NonNull ViewGroup canvasHost, @NonNull LayoutCanvasView canvas,
                     @NonNull View keyboardTools, @NonNull ChipGroup keyboardForms,
                     @NonNull TextView keyRadiusLabel, @NonNull Slider keyRadius,
                     @NonNull MaterialButton moveControl,
                     @NonNull MaterialButtonToggleGroup orientation,
                     @NonNull MaterialButton hiddenControl, @NonNull View hiddenHighlight,
                     @NonNull ChipGroup hiddenTiles, @NonNull TilesHost tilesHost) {
            this.canvasHost = canvasHost;
            this.canvas = canvas;
            this.keyboardTools = keyboardTools;
            this.keyboardForms = keyboardForms;
            this.keyRadiusLabel = keyRadiusLabel;
            this.keyRadius = keyRadius;
            this.moveControl = moveControl;
            this.orientation = orientation;
            this.hiddenControl = hiddenControl;
            this.hiddenHighlight = hiddenHighlight;
            this.hiddenTiles = hiddenTiles;
            this.tilesHost = tilesHost;
        }
    }

    @NonNull private final Host mHost;
    @Nullable private Views mViews;
    @Nullable private LayoutEditorPlan mPlan;
    /** Told whenever something may have moved, so the host's one Undo can show or go. */
    @Nullable private Runnable mOnChanged;
    /** True while a toggle, a chip group or a slider is being restated, so it writes nothing. */
    private boolean mRestatingToggle;
    /** The Style of the whole chrome, and the user's Corners and Margin in dp, which the canvas draws. */
    @NonNull private LayoutStyle mStyle = LayoutStyle.parse(TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE);
    private float mCornersDp = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
    private float mMarginDp = TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;
    /** How many extra keys the canvas draws glyphs for, and what they are; read when a session opens. */
    private int mExtraKeyCount = -1;
    private List<LayoutCanvasView.KeySlot> mKeySlots = new ArrayList<>();
    /** The handle a finger is on, whose readout a late measurement may restate; or null. */
    @Nullable private LayoutCanvasView.Handle mHeldHandle;
    /** What eye-off was last restated for, so it is redrawn only when that changes. */
    @Nullable private List<LayoutEditorPlan.TrayItem> mTrayShown;
    /** The hidden tiles, and whether they are open in the sheet. */
    @Nullable private HiddenTiles mTiles;
    private boolean mTilesOpen;
    @Nullable private BadgeDrawable mHiddenBadge;
    /** Whether a lifted element may be dropped on eye-off, and whether the finger is over it. */
    private boolean mTrayOffered;
    private boolean mTrayHovered;
    /** The move control's menu while it is up. */
    @Nullable private PopupMenu mMoveMenu;
    /** Key radius, as the hosting editor binds it; null hides the control. */
    @Nullable private KeyRadius mKeyRadius;
    private boolean mKeyRadiusDragging;

    public LayoutEditorController(@NonNull Host host) {
        mHost = host;
    }

    /** Whether a session is open: the editor is up, in either mode. */
    public boolean isActive() {
        return mPlan != null;
    }

    /** The place the session is open on, or null while it is not. */
    @Nullable
    @VisibleForTesting
    public PaneWallPage editedPlace() {
        return mPlan == null ? null : mPlan.place();
    }

    /** The orientation the canvas is showing, or null while no session is open. */
    @Nullable
    @VisibleForTesting
    public PlaceOrientation shownOrientation() {
        return mPlan == null ? null : mPlan.shownOrientation();
    }

    /**
     * The Style of the whole chrome and the user's Corners and Margin, in dp, which the layout
     * canvas draws every shape from. The editor that owns those controls tells it on every change;
     * a change of Style makes the canvas morph from one shape to the other.
     */
    public void setShape(@NonNull LayoutStyle style, float cornersDp, float marginDp) {
        mStyle = style;
        mCornersDp = cornersDp;
        mMarginDp = marginDp;
        Views views = mViews;
        if (views != null)
            views.canvas.setShape(style, cornersDp, marginDp);
    }

    /** The host's hook for "something may have moved": its Undo and its dirty state. */
    public void setOnChangedListener(@Nullable Runnable listener) {
        mOnChanged = listener;
    }

    /** Key radius's owner; the control shows beside the type chips while the keyboard is selected. */
    public void setKeyRadius(@Nullable KeyRadius keyRadius) {
        mKeyRadius = keyRadius;
        if (mViews != null)
            syncKeyboardTools(mViews);
    }

    // ------------------------------------------------------------------------------------ entry

    /**
     * Takes the views the hosting editor lends. Bound once per set of views; lending the same
     * ones again is a no-op.
     */
    public void attach(@NonNull Views views) {
        if (mViews == views)
            return;
        mViews = views;
        mTrayShown = null;
        mHiddenBadge = null;
        bind(views);
        if (mPlan != null)
            sync();
    }

    /**
     * Opens the session on one place — the place on screen for null. A door opened while the
     * session is already open moves it to that place rather than starting over, so what Undo
     * puts back is still the arrangement the editor opened on.
     *
     * @return false where there is no store to edit yet
     */
    public boolean begin(@Nullable PaneWallPage place) {
        PlaceLayoutStore places = mHost.places();
        if (places == null)
            return false;
        PaneWallPage target = place != null ? place : mHost.placeOnScreen();
        if (mPlan == null) {
            mPlan = LayoutEditorPlan.enter(places, target, mHost.placeOrientation());
            mExtraKeyCount = mHost.extraKeyCount();
            mKeySlots = mHost.extraKeySlots();
        } else {
            mPlan.showPlace(target);
        }
        mHost.holdPaneWallOnPlace(target, true);
        sync();
        return true;
    }

    /**
     * Whether anything has moved since the session opened, in either orientation. The host folds
     * it into the editor's one dirty state.
     */
    public boolean isDirty() {
        return mPlan != null && mPlan.isDirty();
    }

    /** The dock's height scale in the orientation being edited, or -1 with no session. */
    public float dockHeightScale() {
        return mPlan == null ? -1f : mPlan.dockHeightScale();
    }

    /**
     * The dock's height, as the Look editor's Dock size slider writes it: the same write the
     * dock handle makes in Layout mode, so the session's Undo and dirty state cover it.
     */
    public void setDockHeightScale(float scale) {
        if (mPlan != null)
            afterCanvasWrite(mPlan.setDockHeightScale(scale));
    }

    /** Undo: every bar back where the session found it, without leaving the editor. */
    public void revert() {
        if (mPlan == null)
            return;
        mPlan.revert();
        // The bars have to be back on their edges before the chrome is re-read.
        mHost.applyPlaceArrangement();
        if (mViews != null)
            mViews.canvas.setSelectedBlock(null);
        sync();
    }

    /**
     * Back while Layout mode is up: a lifted bar goes home and nothing is written (DECISIONS item
     * 4). True when Back was spent on that, so the editor does not also ask to close.
     */
    public boolean cancelGesture() {
        Views views = mViews;
        return mPlan != null && views != null && views.canvas.cancelLift();
    }

    /**
     * Closes the session, keeping what is written (every write already went through; Discard is
     * {@link #revert} first). The wall's gestures are handed back.
     */
    public void end() {
        if (mPlan == null)
            return;
        PaneWallPage place = mPlan.place();
        mPlan = null;
        mHeldHandle = null;
        mTrayShown = null;
        Views views = mViews;
        if (views != null) {
            // The next session opens with nothing selected, nothing over the canvas, the tiles shut.
            views.canvas.cancelLift();
            views.canvas.setSelectedBlock(null);
            views.canvas.setHandleReadout(null);
            views.tilesHost.setKeyboardToolsShown(false);
            views.moveControl.setVisibility(View.GONE);
            dismissMoveMenu();
            setTilesOpen(views, false);
            showHiddenOffer(views, false, false);
        }
        mHost.holdPaneWallOnPlace(place, false);
    }

    /** The phone turned: the canvas's default goes with it, and so does what the next drop writes. */
    public void onPlaceOrientationChanged() {
        Views views = mViews;
        if (mPlan == null || views == null)
            return;
        // A rotation is delivered before the window is re-laid out, so the display metrics the
        // canvas is sized from are still the old orientation's until the next pass.
        views.canvas.post(() -> {
            if (mPlan == null)
                return;
            mPlan.onDeviceOrientationChanged(mHost.placeOrientation());
            sync();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bind(@NonNull Views views) {
        views.canvas.setLegendVisible(false);
        views.canvas.setFillsView(true);
        // Eye-off's rect is restated on every touch-down as well, before the canvas can lift
        // anything: the bottom area may have slid in since the last layout pass said where it was.
        views.canvas.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                syncTrayTarget(views);
            return false;
        });
        views.canvas.setOnBarDroppedListener(new LayoutCanvasView.OnBarDroppedListener() {
            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, false);
            }

            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index,
                                     boolean underKeyboard) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, underKeyboard);
            }
        });
        views.canvas.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override public void onSelectionChanged(@Nullable LayoutCanvasView.Block selected) {
                dismissMoveMenu();
                // The keyboard's tools stand in Row B's place: the tiles give it up to them.
                if (showsKeyRadius(selected) && mTilesOpen)
                    setTilesOpen(views, false);
                syncKeyboardTools(views);
                syncMoveControl(views);
            }

            @Override public void onDockHeightDragged(float scale) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.DOCK_HEIGHT,
                        mPlan.setDockHeightScale(scale));
            }

            @Override public void onKeyboardHeightDragged(float scale) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.KEYBOARD_HEIGHT,
                        mPlan.setKeyboardHeightScale(scale));
            }

            @Override public void onKeyboardChinDragged(int dp) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.KEYBOARD_CHIN,
                        mPlan.setKeyboardChinDp(dp));
            }

            @Override public void onWidgetGridDragged(int columns, int rows) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.WIDGET_GRID,
                        mPlan.setWidgetGrid(columns, rows));
            }

            @Override public void onHandleReleased() {
                mHeldHandle = null;
                views.canvas.setHandleReadout(null);
                notifyChanged();
            }

            @Override public void onKeyboardPutAway() {
                if (mPlan != null)
                    afterCanvasWrite(mPlan.setKeyboardShown(false));
            }

            @Override public void onTrayOfferChanged(boolean offered, boolean hovered) {
                showHiddenOffer(views, offered, hovered);
                // Nothing stands over the canvas while a bar is in the air.
                syncMoveControl(views);
            }

            @Override public void onHiddenChipTapped(@NonNull LayoutCanvasView.Block block) {
                restore(block);
            }

            @Override public void onKeyboardRestored() {
                if (mPlan != null)
                    afterCanvasWrite(mPlan.setKeyboardShown(true));
            }
        });
        views.keyboardForms.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty())
                onKeyboardFormPicked(checkedIds.get(0));
        });
        views.keyboardForms.setContentDescription(
            mHost.context().getString(R.string.layout_editor_keyboard_forms));
        paintKeyboardTools(views);
        bindKeyRadius(views);
        // What stands over the canvas follows the elements it is placed by, which move whenever
        // the canvas is laid out again: a new size, a new orientation, a keyboard grown by its
        // handle. Posted, since layout params may change and a layout pass is no place for that.
        views.canvas.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft,
                                                oldTop, oldRight, oldBottom) ->
            views.canvas.post(() -> {
                if (mViews == views) {
                    syncKeyboardTools(views);
                    syncMoveControl(views);
                    syncTrayTarget(views);
                }
            }));
        // Eye-off is the drop target, and it stands in the bottom area: wherever either of the two
        // moves, the canvas is told where its highlight now is in the canvas's own coordinates.
        View.OnLayoutChangeListener trayMoved = (view, left, top, right, bottom, oldLeft,
                                                 oldTop, oldRight, oldBottom) -> {
            if (mViews == views)
                syncTrayTarget(views);
        };
        views.hiddenControl.addOnLayoutChangeListener(trayMoved);
        views.hiddenHighlight.addOnLayoutChangeListener(trayMoved);
        mTiles = new HiddenTiles(views.canvas, views.hiddenTiles, this::restore);
        views.hiddenControl.setOnClickListener(tapped -> {
            // Nothing hidden: there is nothing to list, and the tiles stay shut.
            boolean open = !mTilesOpen && mTiles != null && !mTiles.isEmpty();
            setTilesOpen(views, open);
        });
        views.moveControl.setOnClickListener(this::showMoveMenu);
        views.orientation.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggle || mPlan == null)
                return;
            PlaceOrientation picked = checkedId == R.id.layout_editor_orientation_landscape
                ? PlaceOrientation.LANDSCAPE : PlaceOrientation.PORTRAIT;
            if (picked == mPlan.shownOrientation())
                return;
            mPlan.showOrientation(picked);
            // The selection was on the other orientation's picture.
            views.canvas.setSelectedBlock(null);
            sync();
        });
    }

    // ------------------------------------------------------------------------------- the drops

    /**
     * A bar dropped into a gap in an edge's stack, or on eye-off when {@code edge} is null. The
     * write lands on the orientation the canvas is showing; the live place is re-laid only when
     * that is the orientation the phone is in.
     */
    @VisibleForTesting
    void onBarDropped(@NonNull LayoutCanvasView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index) {
        onBarDropped(block, edge, index, false);
    }

    /** As above, on either side of the keyboard; {@code underKeyboard} is the far one. */
    @VisibleForTesting
    void onBarDropped(@NonNull LayoutCanvasView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index, boolean underKeyboard) {
        MiniatureDragPolicy.Bar bar = LayoutCanvasView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (mPlan.drop(bar, edge, index, underKeyboard) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** A tile tapped, or a hidden element brought back: to the edge it left. */
    private void restore(@NonNull LayoutCanvasView.Block block) {
        LayoutEditorPlan current = mPlan;
        LayoutEditorPlan.TrayItem item = trayItemOf(block);
        if (current != null && item != null)
            afterCanvasWrite(current.restore(item));
    }

    /** Re-reads the canvas, the toggle, eye-off and the tiles from the plan. */
    public void sync() {
        Views views = mViews;
        LayoutEditorPlan plan = mPlan;
        if (views == null || plan == null)
            return;
        mRestatingToggle = true;
        views.orientation.check(plan.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? R.id.layout_editor_orientation_landscape : R.id.layout_editor_orientation_portrait);
        mRestatingToggle = false;
        views.canvas.setShape(mStyle, mCornersDp, mMarginDp);
        views.canvas.setExtraKeyCount(mExtraKeyCount);
        views.canvas.setExtraKeySlots(mKeySlots);
        views.canvas.setSizes(plan.dockHeightScale(), plan.keyboardHeightScale(),
            plan.keyboardChinDp());
        // The status bar's collapsed/expanded state is a per-orientation key the status swipe
        // sets; the canvas draws whichever the shown orientation holds (spec §5, DECISIONS
        // item 7). The keyboard's form and the minimal variant are the plan's layout's own.
        PlaceLayoutStore places = mHost.places();
        if (places != null)
            views.canvas.setStatusCompact(places.isStatusCompact(plan.shownOrientation()));
        views.canvas.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        syncHidden(views, plan);
        syncTrayTarget(views);
        syncKeyboardTools(views);
        syncMoveControl(views);
        notifyChanged();
    }

    private void notifyChanged() {
        if (mOnChanged != null)
            mOnChanged.run();
    }

    // ------------------------------------------------------------------------------ the canvas

    /**
     * A write from the canvas landed: the live place follows it when it was written for the
     * orientation on screen — or, for the keyboard's switch, always — and the canvas, eye-off and
     * the tiles are read again. A write that changed nothing does neither.
     */
    private void afterCanvasWrite(@NonNull LayoutEditorPlan.Drop drop) {
        if (drop == LayoutEditorPlan.Drop.NONE)
            return;
        if (drop == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /**
     * A handle moved: the value written through, and its readout restated. The readout's dp come
     * from the live launcher once it has laid the new size out, so it is restated once more on the
     * next frame.
     */
    private void onHandleWrite(@NonNull LayoutCanvasView.Handle handle,
                               @NonNull LayoutEditorPlan.Drop drop) {
        mHeldHandle = handle;
        afterCanvasWrite(drop);
        Views views = mViews;
        if (views == null)
            return;
        views.canvas.setHandleReadout(readoutFor(handle));
        views.canvas.post(() -> {
            if (mViews == views && mHeldHandle == handle)
                views.canvas.setHandleReadout(readoutFor(handle));
        });
    }

    /**
     * What a held handle reads out, in the store's real units (spec §3.5: no bare percents). The
     * dock and the keyboard are measured on the live launcher while the canvas shows the phone's
     * own orientation; for the other one there is nothing live to measure, and the readout says
     * the multiple of the unscaled height the store keeps.
     */
    @Nullable
    private String readoutFor(@NonNull LayoutCanvasView.Handle handle) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return null;
        Context context = mHost.context();
        switch (handle) {
            case DOCK_HEIGHT: {
                int dp = plan.liveFollows() ? mHost.measuredDockHeightDp() : -1;
                return dp > 0
                    ? context.getString(R.string.layout_editor_readout_dock_dp, dp)
                    : context.getString(R.string.layout_editor_readout_dock_scale,
                        scaleText(plan.dockHeightScale()));
            }
            case KEYBOARD_HEIGHT: {
                int dp = plan.liveFollows() ? mHost.measuredKeyboardHeightDp() : -1;
                return dp > 0
                    ? context.getString(R.string.layout_editor_readout_keyboard_dp, dp)
                    : context.getString(R.string.layout_editor_readout_keyboard_scale,
                        scaleText(plan.keyboardHeightScale()));
            }
            case KEYBOARD_CHIN:
                return context.getString(R.string.layout_editor_readout_chin,
                    plan.keyboardChinDp());
            case WIDGET_GRID:
            default: {
                PlaceLayout layout = plan.shownLayout();
                return context.getString(R.string.layout_editor_readout_grid,
                    layout.widgetColumns, layout.widgetRows);
            }
        }
    }

    @NonNull
    private static String scaleText(float scale) {
        return String.format(Locale.getDefault(), "%.2f", scale);
    }

    // ------------------------------------------------------------------- the move control

    /**
     * The move control (DECISIONS items 4 and 5): a 48dp icon button outside the selected element,
     * while it has anywhere to go and nothing is in the air. Hidden otherwise. It stands in the
     * gutter outside the phone where there is room ({@link #syncMoveControlRoom}), so it covers
     * neither the bar nor anything else on the canvas.
     */
    private void syncMoveControl(@NonNull Views views) {
        if (mPlan != null && !views.canvas.isLifting())
            syncMoveControlRoom(views);
        RectF rect = mPlan == null || views.canvas.isLifting() ? null
            : views.canvas.moveControlRect();
        MaterialButton control = views.moveControl;
        if (rect == null) {
            if (control.getVisibility() != View.GONE)
                control.setVisibility(View.GONE);
            return;
        }
        LayoutCanvasView.Block selected = views.canvas.selectedBlock();
        if (selected != null) {
            control.setContentDescription(mHost.context().getString(
                R.string.layout_editor_move_element, views.canvas.chipName(selected)));
        }
        // The control may stand in another parent than the canvas (the editor round the frame):
        // the canvas's rect is taken there through their places in the window.
        float[] at = canvasOriginIn(views.canvas, control);
        control.setTranslationX(at[0] + rect.left - control.getLeft());
        control.setTranslationY(at[1] + rect.top - control.getTop());
        if (control.getVisibility() != View.VISIBLE)
            control.setVisibility(View.VISIBLE);
    }

    /**
     * Tells the canvas where the move control may stand outside the phone: the control's own
     * parent (the editor round the frame) in the canvas's coordinates, past the system bars and
     * above the sheet. While the control stands in the frame itself there is no such room.
     */
    private void syncMoveControlRoom(@NonNull Views views) {
        LayoutCanvasView canvas = views.canvas;
        ViewParent parent = views.moveControl.getParent();
        if (!(parent instanceof View) || parent == views.canvasHost || !canvas.isAttachedToWindow()) {
            canvas.setMoveControlRoom(null);
            return;
        }
        View area = (View) parent;
        int[] areaAt = new int[2];
        int[] canvasAt = new int[2];
        area.getLocationInWindow(areaAt);
        canvas.getLocationInWindow(canvasAt);
        float left = areaAt[0];
        float top = areaAt[1];
        float right = left + area.getWidth();
        float bottom = top + area.getHeight();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(area);
        View root = area.getRootView();
        if (insets != null && root != null) {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            left = Math.max(left, bars.left);
            top = Math.max(top, bars.top);
            right = Math.min(right, root.getWidth() - bars.right);
            bottom = Math.min(bottom, root.getHeight() - bars.bottom);
        }
        ViewParent sheetParent = views.hiddenControl.getParent();
        if (sheetParent instanceof View && ((View) sheetParent).isShown()) {
            int[] sheetAt = new int[2];
            ((View) sheetParent).getLocationInWindow(sheetAt);
            bottom = Math.min(bottom, sheetAt[1]);
        }
        if (right <= left || bottom <= top) {
            canvas.setMoveControlRoom(null);
            return;
        }
        canvas.setMoveControlRoom(new RectF(left - canvasAt[0], top - canvasAt[1],
            right - canvasAt[0], bottom - canvasAt[1]));
    }

    /** Where {@code canvas}'s origin stands in {@code view}'s parent, through the window. */
    @NonNull
    private static float[] canvasOriginIn(@NonNull View canvas, @NonNull View view) {
        ViewParent parent = view.getParent();
        if (parent == canvas.getParent() || !(parent instanceof View))
            return new float[] {canvas.getLeft(), canvas.getTop()};
        int[] canvasAt = new int[2];
        int[] parentAt = new int[2];
        canvas.getLocationInWindow(canvasAt);
        ((View) parent).getLocationInWindow(parentAt);
        return new float[] {canvasAt[0] - parentAt[0], canvasAt[1] - parentAt[1]};
    }

    /**
     * The move control's menu: the destinations the canvas offers the selected element, in its
     * order, each the accessibility move action it runs (top, bottom, left, right, hide).
     */
    private void showMoveMenu(@NonNull View anchor) {
        Views views = mViews;
        if (views == null || mPlan == null)
            return;
        LayoutCanvasView.Block selected = views.canvas.selectedBlock();
        if (selected == null)
            return;
        List<LayoutCanvasView.Destination> destinations = moveMenuItems(views.canvas, selected);
        if (destinations.isEmpty())
            return;
        dismissMoveMenu();
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        Menu items = menu.getMenu();
        for (int i = 0; i < destinations.size(); i++) {
            items.add(Menu.NONE, i, i,
                LayoutCanvasView.labelOf(destinations.get(i)));
        }
        menu.setOnMenuItemClickListener(item -> {
            int at = item.getItemId();
            if (at < 0 || at >= destinations.size() || mPlan == null)
                return false;
            views.canvas.moveTo(selected, destinations.get(at));
            return true;
        });
        menu.setOnDismissListener(dismissed -> {
            if (mMoveMenu == menu)
                mMoveMenu = null;
        });
        mMoveMenu = menu;
        menu.show();
    }

    /** What the move menu lists for {@code block}: the canvas's own destinations, in order. */
    @NonNull
    @VisibleForTesting
    static List<LayoutCanvasView.Destination> moveMenuItems(@NonNull LayoutCanvasView canvas,
                                                           @NonNull LayoutCanvasView.Block block) {
        return canvas.moveDestinations(block);
    }

    private void dismissMoveMenu() {
        PopupMenu menu = mMoveMenu;
        mMoveMenu = null;
        if (menu != null)
            menu.dismiss();
    }

    // ------------------------------------------------------------------ eye-off and the tiles

    /**
     * Eye-off: its count badge, absent while nothing is hidden, and its accessibility line; and
     * the tiles restated, shut once nothing is left to list. Restated only when what is hidden
     * changes.
     */
    private void syncHidden(@NonNull Views views, @NonNull LayoutEditorPlan plan) {
        List<LayoutEditorPlan.TrayItem> items = plan.trayItems();
        if (!items.equals(mTrayShown)) {
            mTrayShown = new ArrayList<>(items);
            showHiddenBadge(views, items.size());
            views.hiddenControl.setContentDescription(views.canvas.hiddenDescription());
            androidx.appcompat.widget.TooltipCompat.setTooltipText(views.hiddenControl,
                views.canvas.hiddenDescription());
        }
        if (mTiles != null) {
            mTiles.update();
            if (mTilesOpen && mTiles.isEmpty())
                setTilesOpen(views, false);
        }
        showHiddenOffer(views, mTrayOffered, mTrayHovered);
    }

    /** Opens or shuts the hidden tiles in the sheet's Row B. */
    private void setTilesOpen(@NonNull Views views, boolean open) {
        mTilesOpen = open;
        if (open && mTiles != null)
            mTiles.update();
        views.tilesHost.setHiddenTilesOpen(open);
        views.hiddenControl.setSelected(open);
        showHiddenOffer(views, mTrayOffered, mTrayHovered);
    }

    /** Whether the hidden tiles are open, for a test to read. */
    @VisibleForTesting
    boolean areTilesOpen() {
        return mTilesOpen;
    }

    /**
     * Eye-off's look (DECISIONS items 2 and 12): at rest its glyph in onSurfaceVariant, or in the
     * primary while something is hidden; open, primaryContainer with onPrimaryContainer. While a
     * lifted element may be put away the highlight behind it shows in primaryContainer, and takes
     * a primary rim once the finger is over it. Only an accepted drop highlights.
     */
    private void showHiddenOffer(@NonNull Views views, boolean offered, boolean hovered) {
        mTrayOffered = offered;
        mTrayHovered = offered && hovered;
        boolean filled = mTrayShown != null && !mTrayShown.isEmpty();
        MaterialButton control = views.hiddenControl;
        int primary = EditorM3.color(control, androidx.appcompat.R.attr.colorPrimary);
        int muted = EditorM3.color(control,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        int container = EditorM3.color(control,
            com.google.android.material.R.attr.colorPrimaryContainer);
        int onContainer = EditorM3.color(control,
            com.google.android.material.R.attr.colorOnPrimaryContainer);
        boolean selected = mTilesOpen || offered;
        control.setIconTint(ColorStateList.valueOf(selected ? onContainer
            : filled ? primary : muted));
        control.setBackgroundTintList(ColorStateList.valueOf(mTilesOpen && !offered
            ? container : 0));
        View highlight = views.hiddenHighlight;
        if (!offered) {
            if (highlight.getVisibility() != View.INVISIBLE)
                highlight.setVisibility(View.INVISIBLE);
            return;
        }
        // A circle round the control: half its own size, no radius of the editor's own.
        MaterialShapeDrawable wash = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
            .setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
        wash.setFillColor(ColorStateList.valueOf(container));
        if (mTrayHovered) {
            float density = mHost.context().getResources().getDisplayMetrics().density;
            wash.setStroke(2f * density, primary);
        }
        highlight.setBackground(wash);
        if (highlight.getVisibility() != View.VISIBLE)
            highlight.setVisibility(View.VISIBLE);
    }

    /**
     * Tells the canvas where eye-off's drop area stands — its highlight, larger than its 48dp
     * target — in the canvas's own coordinates. The two are in different parents, the frame and
     * the bottom area, so the rect is taken from their places in the window; neither is under the
     * frame's scale.
     */
    private void syncTrayTarget(@NonNull Views views) {
        View control = views.hiddenControl;
        View highlight = views.hiddenHighlight;
        LayoutCanvasView canvas = views.canvas;
        if (!control.isShown() || control.getWidth() <= 0) {
            canvas.setExternalTrayRect(null);
            return;
        }
        View area = highlight.getWidth() > 0 ? highlight : control;
        canvas.setExternalTrayRect(dropArea(area, canvas));
    }

    /** {@code area}'s rect in {@code canvas}'s coordinates, through their places in the window. */
    @NonNull
    @VisibleForTesting
    static RectF dropArea(@NonNull View area, @NonNull View canvas) {
        int[] areaAt = new int[2];
        int[] canvasAt = new int[2];
        area.getLocationInWindow(areaAt);
        canvas.getLocationInWindow(canvasAt);
        float left = areaAt[0] - canvasAt[0];
        float top = areaAt[1] - canvasAt[1];
        return new RectF(left, top, left + area.getWidth(), top + area.getHeight());
    }

    /**
     * The count on eye-off: a Material badge at the icon's top end, shown while something is
     * hidden and absent otherwise. Created once; attached to the button itself (its overlay, no
     * custom parent) once the button has been laid out, since the badge takes its place from the
     * anchor's bounds at attach time, and placed again whenever the button moves.
     */
    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void showHiddenBadge(@NonNull Views views, int count) {
        if (mHiddenBadge == null) {
            BadgeDrawable badge = BadgeDrawable.create(views.hiddenControl.getContext());
            badge.setBadgeGravity(BadgeDrawable.TOP_END);
            mHiddenBadge = badge;
            attachBadgeWhenLaidOut(views, badge);
        }
        mHiddenBadge.setVisible(count > 0);
        if (count > 0)
            mHiddenBadge.setNumber(count);
        else
            mHiddenBadge.clearNumber();
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void attachBadgeWhenLaidOut(@NonNull Views views, @NonNull BadgeDrawable badge) {
        MaterialButton control = views.hiddenControl;
        Runnable attach = () -> {
            if (mViews != views || mHiddenBadge != badge)
                return;
            // Towards the button's centre by the icon's inset, so the badge sits on the glyph's
            // top-end corner rather than on the 48dp touch target's.
            badge.setHorizontalOffset(Math.max(0, (control.getWidth() - control.getIconSize()) / 2));
            badge.setVerticalOffset(Math.max(0, (control.getHeight() - control.getIconSize()) / 2));
            BadgeUtils.attachBadgeDrawable(badge, control, null);
        };
        if (control.isLaidOut() && control.getWidth() > 0) control.post(attach);
        else control.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left <= 0)
                    return;
                view.removeOnLayoutChangeListener(this);
                view.post(attach);
            }
        });
        // Eye-off moves with the sheet (Undo, a mode switch, a rotation): the badge follows.
        control.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop,
                                           oldRight, oldBottom) -> {
            if (mHiddenBadge == badge && (left != oldLeft || top != oldTop || right != oldRight
                || bottom != oldBottom))
                badge.updateBadgeCoordinates(view);
        });
    }

    /** The tray item a tile for this block stands for; null for what has none. */
    @Nullable
    @VisibleForTesting
    static LayoutEditorPlan.TrayItem trayItemOf(@NonNull LayoutCanvasView.Block block) {
        switch (block) {
            case STATUS_BAR: return LayoutEditorPlan.TrayItem.STATUS_BAR;
            case APPS_ROW: return LayoutEditorPlan.TrayItem.PINNED_APPS;
            case ALPHABETS_ROW: return LayoutEditorPlan.TrayItem.AZ_INDEX;
            case EXTRA_KEYS: return LayoutEditorPlan.TrayItem.EXTRA_KEYS;
            case KEYBOARD: return LayoutEditorPlan.TrayItem.KEYBOARD;
            default: return null;
        }
    }

    // --------------------------------------------------------------------- the keyboard's tools

    /** The three type chips, in the order the group holds them, and the forms they stand for. */
    private static final int[] FORM_CHIP_IDS = {
        R.id.layout_editor_keyboard_form_docked, R.id.layout_editor_keyboard_form_floating,
        R.id.layout_editor_keyboard_form_split};
    private static final PlaceLayout.KeyboardForm[] FORM_CHIP_FORMS = {
        PlaceLayout.KeyboardForm.DOCKED, PlaceLayout.KeyboardForm.FLOATING,
        PlaceLayout.KeyboardForm.SPLIT};

    /** Whether Key radius shows: in Layout mode, with the keyboard selected (DECISIONS item 6). */
    @VisibleForTesting
    static boolean showsKeyRadius(@Nullable LayoutCanvasView.Block selected) {
        return selected == LayoutCanvasView.Block.KEYBOARD;
    }

    /**
     * The keyboard's type chips restyled (DECISIONS items 6 and 12): a checked chip in
     * primaryContainer with its glyph in onPrimaryContainer. They stand on the sheet's own
     * surface, in Row B, with no card of their own.
     */
    private void paintKeyboardTools(@NonNull Views views) {
        int container = EditorM3.color(views.keyboardForms,
            com.google.android.material.R.attr.colorPrimaryContainer);
        int onContainer = EditorM3.color(views.keyboardForms,
            com.google.android.material.R.attr.colorOnPrimaryContainer);
        int quiet = EditorM3.color(views.keyboardForms,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        int[][] states = {{android.R.attr.state_checked}, {}};
        for (int i = 0; i < views.keyboardForms.getChildCount(); i++) {
            View child = views.keyboardForms.getChildAt(i);
            if (!(child instanceof Chip))
                continue;
            Chip chip = (Chip) child;
            chip.setChipBackgroundColor(new ColorStateList(states, new int[] {container, 0}));
            chip.setChipIconTint(new ColorStateList(states, new int[] {onContainer, quiet}));
        }
    }

    private void bindKeyRadius(@NonNull Views views) {
        views.keyRadius.addOnChangeListener((slider, value, fromUser) -> {
            KeyRadius binding = mKeyRadius;
            if (mRestatingToggle || !fromUser || binding == null)
                return;
            int radius = Math.round(value);
            binding.onChanged(radius, mKeyRadiusDragging);
            String label = binding.label(radius);
            views.keyRadiusLabel.setText(label);
            views.keyRadius.setContentDescription(label);
        });
        views.keyRadius.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mKeyRadiusDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mKeyRadiusDragging = false;
                KeyRadius binding = mKeyRadius;
                if (binding != null) binding.onReleased();
            }
        });
    }

    /**
     * The keyboard's tools (spec §3.5, DECISIONS item 6): while the keyboard is selected they take
     * the sheet's Row B, in Corner radius and Margin's place (the hidden tiles' swap), with the
     * shown orientation's form checked and Key radius at its stored value; on deselect the row
     * comes back. The sheet's height never changes, and nothing stands over the canvas.
     */
    private void syncKeyboardTools(@NonNull Views views) {
        LayoutEditorPlan plan = mPlan;
        RectF keyboard = views.canvas.keyboardRect();
        boolean shown = plan != null && keyboard != null
            && showsKeyRadius(views.canvas.selectedBlock()) && !views.canvas.isLifting();
        if (!shown) {
            views.tilesHost.setKeyboardToolsShown(false);
            return;
        }
        mRestatingToggle = true;
        try {
            PlaceLayout.KeyboardForm form = plan.keyboardForm();
            for (int i = 0; i < FORM_CHIP_IDS.length; i++) {
                if (FORM_CHIP_FORMS[i] == form)
                    views.keyboardForms.check(FORM_CHIP_IDS[i]);
            }
            KeyRadius binding = mKeyRadius;
            boolean radius = binding != null;
            views.keyRadiusLabel.setVisibility(radius ? View.VISIBLE : View.GONE);
            views.keyRadius.setVisibility(radius ? View.VISIBLE : View.GONE);
            if (radius && !mKeyRadiusDragging) {
                int max = Math.max(1, binding.max());
                int value = Math.max(0, Math.min(max, binding.value()));
                if (views.keyRadius.getValue() > max)
                    views.keyRadius.setValue(0f);
                views.keyRadius.setValueTo(max);
                views.keyRadius.setValue(value);
                String label = binding.label(value);
                views.keyRadiusLabel.setText(label);
                views.keyRadius.setContentDescription(label);
            }
        } finally {
            mRestatingToggle = false;
        }
        views.tilesHost.setKeyboardToolsShown(true);
    }

    private void onKeyboardFormPicked(int checkedId) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || mRestatingToggle)
            return;
        for (int i = 0; i < FORM_CHIP_IDS.length; i++) {
            if (FORM_CHIP_IDS[i] == checkedId) {
                afterCanvasWrite(plan.setKeyboardForm(FORM_CHIP_FORMS[i]));
                return;
            }
        }
    }
}
