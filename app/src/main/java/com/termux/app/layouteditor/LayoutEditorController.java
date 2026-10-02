package com.termux.app.layouteditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Layout mode of the one editor (appearance-layout-editor SPEC §3.5): the layout canvas of the
 * place the user is looking at, drawn at the full size of the editor's frame, with the Portrait /
 * Landscape toggle and the restore tray in the editor's bottom area. The layout it edits is every
 * place's (ADR 0003), so it has no place to pick: a write lands on Home, the terminal and the
 * display alike.
 *
 * <p>This class owns no screen of its own. The editor that hosts it
 * ({@link com.termux.app.surfaces.SurfaceEditorController}) lends it the canvas, the chips that
 * stand over the canvas and the bottom area's orientation toggle and tray ({@link Views}), opens a
 * session when the editor opens ({@link #begin}), and asks it for its share of the editor's one
 * dirty state, one Undo and one Done ({@link #isDirty}, {@link #revert}, {@link #end}).
 *
 * <p>The canvas is the whole of Layout mode; there are no rows under it. A bar is pressed anywhere
 * and moved past the touch slop to lift it, and dropped on an edge, or into the tray in the bottom
 * area to put it away; while it hovers a target it takes the shape the shape model says it would
 * have there. A tap selects an element:
 * one outline, and on the dock, the keyboard and Home's grid a handle that resizes it — the dock's
 * height, the keyboard's height and its chin, the grid's cells — with a readout in real units
 * while it is held. The keyboard, selected, shows its three type chips beside it, and is dropped
 * in the tray to switch it off. The tray lists every hidden element as a chip that brings it back
 * to the edge it left (§3.6). Every write goes straight through, so the live place under the
 * canvas follows every edit made in the orientation the phone is actually in; the other
 * orientation moves on the canvas alone until the phone is turned.
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
         * How many pinned apps the dock holds, which is how many symbols the canvas's dock draws;
         * or -1 where that cannot be read, which draws the pack's own seven. The default reads the
         * launcher's pinned items; a host may answer from what it already has.
         */
        default int pinnedAppCount() {
            try {
                TermuxAppSharedPreferences preferences =
                    TermuxAppSharedPreferences.build(context(), false);
                return preferences == null ? -1
                    : new LauncherConfigRepository(preferences).loadPinnedItems().size();
            } catch (RuntimeException e) {
                return -1;
            }
        }

        /**
         * How many keys the extra-keys bar holds in its first row, which is how many glyphs the
         * canvas's extra-keys bar draws; or -1 where the host does not know, which draws the
         * pack's seven. Only the activity has the parsed {@code extra-keys} property.
         */
        default int extraKeyCount() {
            return -1;
        }

        /**
         * The pinned apps' real icons, in dock order (a folder shows its first app), each a fresh
         * copy the canvas may size and draw without touching the dock's own. Empty where they
         * cannot be read, which leaves the canvas on the pack's glyphs.
         */
        @NonNull
        default java.util.List<android.graphics.drawable.Drawable> pinnedAppIcons() {
            java.util.List<android.graphics.drawable.Drawable> out = new java.util.ArrayList<>();
            try {
                TermuxAppSharedPreferences preferences =
                    TermuxAppSharedPreferences.build(context(), false);
                if (preferences == null) return out;
                com.termux.app.launcher.data.LauncherIconResolver resolver =
                    new com.termux.app.launcher.data.LauncherIconResolver(context());
                for (com.termux.app.launcher.model.PinnedItem item :
                        new LauncherConfigRepository(preferences).loadPinnedItems()) {
                    com.termux.app.launcher.model.PinnedAppItem app = null;
                    if (item instanceof com.termux.app.launcher.model.PinnedAppItem) {
                        app = (com.termux.app.launcher.model.PinnedAppItem) item;
                    } else if (item instanceof com.termux.app.launcher.model.PinnedFolderItem
                            && !((com.termux.app.launcher.model.PinnedFolderItem) item).apps.isEmpty()) {
                        app = ((com.termux.app.launcher.model.PinnedFolderItem) item).apps.get(0);
                    }
                    if (app == null) continue;
                    android.graphics.drawable.Drawable icon =
                        resolver.resolvePinned(app.appRef, app.iconOverride);
                    if (icon == null) continue;
                    android.graphics.drawable.Drawable.ConstantState state = icon.getConstantState();
                    out.add(state == null ? icon : state.newDrawable(context().getResources()).mutate());
                }
            } catch (RuntimeException e) {
                out.clear();
            }
            return out;
        }

        /**
         * The extra-keys bar's first row, in order, each key as the real bar shows it: the icon
         * it draws (a fresh Drawable the canvas may size and tint) or, for a key with no icon,
         * its text. Empty where unknown, which leaves the canvas on the pack's glyphs.
         */
        @NonNull
        default java.util.List<com.termux.app.fragments.settings.LayoutCanvasView.KeySlot> extraKeySlots() {
            return new java.util.ArrayList<>();
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
     * The views the hosting editor lends Layout mode. The canvas and the keyboard's type chips
     * stand in the frame; the orientation toggle and the trash stand in the bottom area. The trash
     * is an icon button: the drop target a lifted element is put away in (the canvas is told its
     * rect), the anchor of the canvas's list of what is hidden, which a tap opens, and the anchor
     * of the count badge.
     */
    public static final class Views {
        /** What the canvas and the keyboard's chips stand in: the frame's own rect. */
        @NonNull final ViewGroup canvasHost;
        @NonNull final LayoutCanvasView canvas;
        @NonNull final ChipGroup keyboardForms;
        @NonNull final MaterialButtonToggleGroup orientation;
        @NonNull final MaterialButton trash;

        public Views(@NonNull ViewGroup canvasHost, @NonNull LayoutCanvasView canvas,
                     @NonNull ChipGroup keyboardForms,
                     @NonNull MaterialButtonToggleGroup orientation,
                     @NonNull MaterialButton trash) {
            this.canvasHost = canvasHost;
            this.canvas = canvas;
            this.keyboardForms = keyboardForms;
            this.orientation = orientation;
            this.trash = trash;
        }
    }

    @NonNull private final Host mHost;
    @Nullable private Views mViews;
    @Nullable private LayoutEditorPlan mPlan;
    /** Told whenever something may have moved, so the host's one Undo can show or go. */
    @Nullable private Runnable mOnChanged;
    /** True while a toggle or a chip group is being restated from the plan, so it writes nothing. */
    private boolean mRestatingToggle;
    /** The Style of the whole chrome, and the user's Corners and Margin in dp, which the canvas draws. */
    @NonNull private LayoutStyle mStyle = LayoutStyle.parse(TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE);
    private float mCornersDp = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
    private float mMarginDp = TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;
    /** How many pinned apps and extra keys the canvas draws symbols for, read when a session opens. */
    private int mPinnedAppCount = -1;
    private int mExtraKeyCount = -1;
    private java.util.List<android.graphics.drawable.Drawable> mPinnedIcons = java.util.Collections.emptyList();
    private java.util.List<com.termux.app.fragments.settings.LayoutCanvasView.KeySlot> mKeySlots =
        java.util.Collections.emptyList();
    /** The handle a finger is on, whose readout a late measurement may restate; or null. */
    @Nullable private LayoutCanvasView.Handle mHeldHandle;
    /** What the trash was last restated for, so it is redrawn only when that changes. */
    @Nullable private List<LayoutEditorPlan.TrayItem> mTrayShown;
    /** The hidden-elements popup the trash opens, and the count badge on the trash. */
    @Nullable private HiddenElementsPopup mHiddenPopup;
    @Nullable private BadgeDrawable mTrashBadge;
    /** The icon's resting background (its ripple), which the drop zone replaces while offered. */
    /** The trash's own container tint, put back once it stops being offered as the drop zone. */
    @Nullable private ColorStateList mTrashRestTint;
    /** Whether a lifted element may be dropped in the tray, and whether the finger is over it. */
    private boolean mTrayOffered;
    private boolean mTrayHovered;

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
        if (mHiddenPopup != null)
            mHiddenPopup.dismiss();
        mTrashBadge = null;
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
            mPinnedAppCount = mHost.pinnedAppCount();
            mExtraKeyCount = mHost.extraKeyCount();
            mPinnedIcons = mHost.pinnedAppIcons();
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
            // The next session opens with nothing selected and no chips over the keyboard.
            views.canvas.setSelectedBlock(null);
            views.canvas.setHandleReadout(null);
            views.keyboardForms.setVisibility(View.GONE);
            if (mHiddenPopup != null)
                mHiddenPopup.dismiss();
            showTrayOffer(views, false, false);
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
        // The tray's rect is restated on every touch-down as well, before the canvas can lift
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
                syncKeyboardForms(views);
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
                showTrayOffer(views, offered, hovered);
            }

            @Override public void onHiddenChipTapped(@NonNull LayoutCanvasView.Block block) {
                LayoutEditorPlan current = mPlan;
                LayoutEditorPlan.TrayItem item = trayItemOf(block);
                if (current != null && item != null)
                    afterCanvasWrite(current.restore(item));
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
        // The type chips stand beside the keyboard, which moves whenever the canvas is laid out
        // again: a new size, a new orientation, a keyboard grown by its handle. Posted, since the
        // chips' own layout params may change and a layout pass is no place to ask for another.
        views.canvas.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft,
                                                oldTop, oldRight, oldBottom) ->
            views.canvas.post(() -> {
                if (mViews == views) {
                    syncKeyboardForms(views);
                    syncTrayTarget(views);
                }
            }));
        // The trash is the drop target, and it stands in the bottom area: wherever either of the
        // two moves, the canvas is told where it now is in its own coordinates.
        views.trash.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft,
                                               oldTop, oldRight, oldBottom) -> {
            if (mViews == views)
                syncTrayTarget(views);
        });
        mTrashRestTint = views.trash.getBackgroundTintList();
        mHiddenPopup = new HiddenElementsPopup(views.canvas, views.trash, block -> {
            LayoutEditorPlan current = mPlan;
            LayoutEditorPlan.TrayItem item = trayItemOf(block);
            if (current != null && item != null)
                afterCanvasWrite(current.restore(item));
        });
        views.trash.setOnClickListener(tapped -> {
            // Nothing hidden: nothing to list, and the popup stays shut.
            if (mHiddenPopup != null)
                mHiddenPopup.toggle();
        });
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
     * A bar dropped into a gap in an edge's stack, or in the tray when {@code edge} is null. The
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

    /** Re-reads the canvas, the toggle and the tray from the plan. */
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
        views.canvas.setSlotCounts(mPinnedAppCount, mExtraKeyCount);
        views.canvas.setSlotContent(mPinnedIcons, mKeySlots);
        views.canvas.setSizes(plan.dockHeightScale(), plan.keyboardHeightScale(),
            plan.keyboardChinDp());
        // The status bar's collapsed/expanded state is a per-orientation key the status swipe
        // sets; the canvas draws whichever the shown orientation holds (spec §5).
        PlaceLayoutStore places = mHost.places();
        if (places != null)
            views.canvas.setStatusCompact(places.isStatusCompact(plan.shownOrientation()));
        views.canvas.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        syncTray(views, plan);
        syncTrayTarget(views);
        syncKeyboardForms(views);
        notifyChanged();
    }

    private void notifyChanged() {
        if (mOnChanged != null)
            mOnChanged.run();
    }

    // ------------------------------------------------------------------------------ the canvas

    /**
     * A write from the canvas landed: the live place follows it when it was written for the
     * orientation on screen — or, for the keyboard's switch, always — and the canvas and the tray
     * are read again. A write that changed nothing does neither.
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

    // -------------------------------------------------------------------------------- the tray

    /**
     * The trash (replacing the restore tray's chips): an outline, muted, while nothing is hidden;
     * a filled glyph in the accent with a count badge while something is. The list of what is
     * hidden is the canvas's popup, opened by a tap on it. Restated only when what is hidden changes.
     */
    private void syncTray(@NonNull Views views, @NonNull LayoutEditorPlan plan) {
        List<LayoutEditorPlan.TrayItem> items = plan.trayItems();
        if (items.equals(mTrayShown)) {
            showTrayOffer(views, mTrayOffered, mTrayHovered);
            return;
        }
        mTrayShown = new ArrayList<>(items);
        int count = items.size();
        boolean filled = MiniatureDragPolicy.trashState(count) == MiniatureDragPolicy.TrashState.FILLED;
        views.trash.setIconResource(filled ? R.drawable.ic_trash_filled : R.drawable.ic_symbol_delete);
        showTrashBadge(views, count);
        views.trash.setContentDescription(views.canvas.trashDescription());
        // Restate the popup's chips; nothing left to list closes it (a host write can change the
        // count without the canvas drawing a new layout).
        if (mHiddenPopup != null)
            mHiddenPopup.update();
        showTrayOffer(views, mTrayOffered, mTrayHovered);
    }

    /**
     * The trash at rest wears the muted ink (outline) or the accent (filled); while a lifted
     * element may be put away it is outlined as the drop zone, and filled with a wash once the
     * finger is over it.
     */
    private void showTrayOffer(@NonNull Views views, boolean offered, boolean hovered) {
        mTrayOffered = offered;
        mTrayHovered = offered && hovered;
        boolean filled = mTrayShown != null && !mTrayShown.isEmpty();
        int primary = MaterialColors.getColor(views.trash,
            androidx.appcompat.R.attr.colorPrimary,
            mHost.themeColor(androidx.appcompat.R.attr.colorPrimary,
                R.color.termux_primary));
        int muted = MaterialColors.getColor(views.trash,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            mHost.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                R.color.termux_on_surface_variant));
        views.trash.setIconTint(ColorStateList.valueOf(filled || offered ? primary : muted));
        if (!offered) {
            views.trash.setStrokeWidth(0);
            views.trash.setBackgroundTintList(mTrashRestTint);
            return;
        }
        // The button's own shape, outlined while it is offered and washed once the finger is over
        // it: the drop zone is the trash itself, not a drawable laid over it.
        float density = mHost.context().getResources().getDisplayMetrics().density;
        int outline = MaterialColors.getColor(views.trash,
            com.google.android.material.R.attr.colorOutline,
            mHost.themeColor(com.google.android.material.R.attr.colorOutline,
                R.color.termux_on_surface));
        int container = MaterialColors.getColor(views.trash,
            com.google.android.material.R.attr.colorSecondaryContainer, 0);
        views.trash.setStrokeColor(ColorStateList.valueOf(mTrayHovered ? primary : outline));
        views.trash.setStrokeWidth(Math.round((mTrayHovered ? 2f : 1f) * density));
        views.trash.setBackgroundTintList(ColorStateList.valueOf(mTrayHovered ? container : 0));
    }

    /**
     * Tells the canvas where the trash stands, in the canvas's own coordinates. The two are in
     * different parents — the frame and the bottom area — so the rect is taken from their places
     * in the window; neither is under the frame's scale.
     */
    private void syncTrayTarget(@NonNull Views views) {
        View tray = views.trash;
        LayoutCanvasView canvas = views.canvas;
        if (tray.getWidth() <= 0 || tray.getHeight() <= 0 || !tray.isShown()) {
            canvas.setExternalTrayRect(null);
            return;
        }
        int[] trayAt = new int[2];
        int[] canvasAt = new int[2];
        tray.getLocationInWindow(trayAt);
        canvas.getLocationInWindow(canvasAt);
        float left = trayAt[0] - canvasAt[0];
        float top = trayAt[1] - canvasAt[1];
        canvas.setExternalTrayRect(new RectF(left, top, left + tray.getWidth(),
            top + tray.getHeight()));
    }

    /**
     * The count on the trash: a Material badge at the icon's top end, shown while something is
     * hidden. Created once; attached to the trash button itself (its overlay, no custom parent)
     * once the button has been laid out, since the badge takes its place from the anchor's
     * bounds at attach time, and placed again whenever the button moves.
     */
    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void showTrashBadge(@NonNull Views views, int count) {
        if (mTrashBadge == null) {
            BadgeDrawable badge = BadgeDrawable.create(views.trash.getContext());
            badge.setBadgeGravity(BadgeDrawable.TOP_END);
            mTrashBadge = badge;
            attachTrashBadgeWhenLaidOut(views, badge);
        }
        mTrashBadge.setVisible(count > 0);
        if (count > 0)
            mTrashBadge.setNumber(count);
        else
            mTrashBadge.clearNumber();
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void attachTrashBadgeWhenLaidOut(@NonNull Views views, @NonNull BadgeDrawable badge) {
        MaterialButton trash = views.trash;
        Runnable attach = () -> {
            if (mViews != views || mTrashBadge != badge)
                return;
            // Towards the button's centre by the icon's inset, so the badge sits on the glyph's
            // top-end corner rather than on the 48dp touch target's.
            badge.setHorizontalOffset(Math.max(0, (trash.getWidth() - trash.getIconSize()) / 2));
            badge.setVerticalOffset(Math.max(0, (trash.getHeight() - trash.getIconSize()) / 2));
            BadgeUtils.attachBadgeDrawable(badge, trash, null);
        };
        if (trash.isLaidOut() && trash.getWidth() > 0) trash.post(attach);
        else trash.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left <= 0)
                    return;
                view.removeOnLayoutChangeListener(this);
                view.post(attach);
            }
        });
        // The trash moves with the sheet (Undo, a mode switch, a rotation): the badge follows.
        trash.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop,
                                         oldRight, oldBottom) -> {
            if (mTrashBadge == badge && (left != oldLeft || top != oldTop || right != oldRight
                || bottom != oldBottom))
                badge.updateBadgeCoordinates(view);
        });
    }

    /** The tray item the popup's chip for this block stands for; null for what has none. */
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

    // --------------------------------------------------------------------- the keyboard's type

    /** The three type chips, in the order the group holds them, and the forms they stand for. */
    private static final int[] FORM_CHIP_IDS = {
        R.id.layout_editor_keyboard_form_docked, R.id.layout_editor_keyboard_form_floating,
        R.id.layout_editor_keyboard_form_split};
    private static final PlaceLayout.KeyboardForm[] FORM_CHIP_FORMS = {
        PlaceLayout.KeyboardForm.DOCKED, PlaceLayout.KeyboardForm.FLOATING,
        PlaceLayout.KeyboardForm.SPLIT};

    /** The air between the keyboard's card and its type chips. */
    private static final float FORM_CHIPS_GAP_DP = 6f;

    /**
     * The keyboard's type chips (spec §3.5): shown beside the keyboard while it is selected, with
     * the shown orientation's form checked, and gone on deselect. Beside the phone where the
     * canvas leaves a gutter wide enough for a column of them — the other orientation fitted
     * inside the frame — and above the keyboard's leading end otherwise, clear of the handle on
     * its middle.
     */
    private void syncKeyboardForms(@NonNull Views views) {
        LayoutEditorPlan plan = mPlan;
        RectF keyboard = views.canvas.keyboardRect();
        boolean shown = plan != null && keyboard != null
            && views.canvas.selectedBlock() == LayoutCanvasView.Block.KEYBOARD;
        if (!shown) {
            views.keyboardForms.setVisibility(View.GONE);
            return;
        }
        mRestatingToggle = true;
        try {
            PlaceLayout.KeyboardForm form = plan.keyboardForm();
            for (int i = 0; i < FORM_CHIP_IDS.length; i++) {
                if (FORM_CHIP_FORMS[i] == form)
                    views.keyboardForms.check(FORM_CHIP_IDS[i]);
            }
        } finally {
            mRestatingToggle = false;
        }
        views.keyboardForms.setVisibility(View.VISIBLE);
        placeKeyboardForms(views, keyboard);
    }

    private void placeKeyboardForms(@NonNull Views views, @NonNull RectF keyboard) {
        ChipGroup chips = views.keyboardForms;
        RectF frame = views.canvas.frameRect();
        int hostWidth = views.canvasHost.getWidth() > 0 ? views.canvasHost.getWidth()
            : views.canvas.getWidth();
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        View first = chips.getChildAt(0);
        if (first == null)
            return;
        first.measure(unspecified, unspecified);
        int chipWidth = first.getMeasuredWidth();
        float gap = dpToPx(FORM_CHIPS_GAP_DP);
        boolean column = hostWidth - frame.right >= chipWidth + 2 * gap;
        ViewGroup.LayoutParams params = chips.getLayoutParams();
        int wantedWidth = column ? chipWidth : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params != null && params.width != wantedWidth) {
            params.width = wantedWidth;
            chips.setLayoutParams(params);
        }
        chips.setSingleLine(!column);
        chips.measure(column
                ? View.MeasureSpec.makeMeasureSpec(chipWidth, View.MeasureSpec.EXACTLY)
                : unspecified, unspecified);
        float width = chips.getMeasuredWidth();
        float height = chips.getMeasuredHeight();
        float x;
        float y;
        if (column) {
            x = frame.right + gap;
            y = keyboard.bottom - height;
        } else {
            x = keyboard.left + gap;
            y = keyboard.top - height - gap;
        }
        float maxX = Math.max(0f, hostWidth - width);
        chips.setTranslationX(Math.max(0f, Math.min(maxX, x)));
        chips.setTranslationY(Math.max(0f, y));
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

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            mHost.context().getResources().getDisplayMetrics());
    }
}
