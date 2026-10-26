package com.termux.app.layouteditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.dock.DockStyle;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The move-and-size engine under the Appearance editor (appearance-and-settings-rework §6 item 6:
 * "the Layout editor's canvas session stays the move/size engine underneath"). It owns one
 * layout session: the arrangement the editor opened on, its share of the editor's one dirty
 * state, Undo and Done ({@link #isDirty}, {@link #revert}, {@link #end}), and every write the
 * cards make to the arrangement: whether an element shows, the edge it stands on, the dock's and
 * the keyboard's heights, the Dock design, the Search field, the keyboard's form and Home's own
 * layout. Every write goes straight through, so the live place under the editor follows it in
 * the orientation the phone is in; the other orientation moves on the layout canvas alone.
 *
 * <p>The layout canvas ({@link Views}) stands over the editor's frame only while the frame shows
 * the other orientation or a surface is being dragged: a hold on a surface in the frame hands the
 * finger to it, and the canvas's own lift and drop move the element. Its handles still resize the
 * dock, the keyboard and Home's grid while it shows.</p>
 *
 * <p>Every decision belongs to {@link LayoutEditorPlan}; this class is the wiring. {@link Host}
 * is the seam to the activity.</p>
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
         * them back.
         */
        void holdPaneWallOnPlace(@NonNull PaneWallPage place, boolean held);

        /**
         * Slide the held wall to {@code place}, the way the wall slides between its halves, and
         * keep its gestures held. Jumps where the host cannot slide.
         */
        default void slidePaneWallToPlace(@NonNull PaneWallPage place) {
            holdPaneWallOnPlace(place, true);
        }

        int themeColor(int attr, int fallbackRes);

        /**
         * How many keys the extra-keys bar holds in its first row, which is how many glyphs the
         * canvas's extra-keys bar draws; or -1 where the host does not know.
         */
        default int extraKeyCount() {
            return -1;
        }

        /** The extra-keys bar's first row as the real bar shows it; empty where unknown. */
        @NonNull
        default List<LayoutCanvasView.KeySlot> extraKeySlots() {
            return new ArrayList<>();
        }

        /** How thick the live dock's pinned apps stand, in dp, or -1 with no dock on screen. */
        default int measuredDockHeightDp() {
            return -1;
        }

        /** How tall the live keyboard stands, in dp, or -1 while it is down. */
        default int measuredKeyboardHeightDp() {
            return -1;
        }
    }

    /** The views the hosting editor lends: the canvas, in a host standing on the frame's rect. */
    public static final class Views {
        @NonNull final ViewGroup canvasHost;
        @NonNull final LayoutCanvasView canvas;

        public Views(@NonNull ViewGroup canvasHost, @NonNull LayoutCanvasView canvas) {
            this.canvasHost = canvasHost;
            this.canvas = canvas;
        }
    }

    @NonNull private final Host mHost;
    @Nullable private Views mViews;
    @Nullable private LayoutEditorPlan mPlan;
    /** Told whenever something may have moved, so the host's one Undo can show or go. */
    @Nullable private Runnable mOnChanged;
    /** Told when the session moved to another page, so the host restates what that page wears. */
    @Nullable private Runnable mOnPlaceShown;
    @Nullable private java.util.function.BiConsumer<Boolean, Boolean> mOnTrayOffer;
    /** Told when the canvas's selection moves (a tap on an element while it shows). */
    @Nullable private Consumer<LayoutCanvasView.Block> mOnSelection;
    @NonNull private LayoutStyle mStyle = LayoutStyle.parse(TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE);
    private float mCornersDp = TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS;
    private float mMarginDp = TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;
    private int mExtraKeyCount = -1;
    private List<LayoutCanvasView.KeySlot> mKeySlots = new ArrayList<>();
    /** The handle a finger is on, whose readout a late measurement may restate; or null. */
    @Nullable private LayoutCanvasView.Handle mHeldHandle;
    /** A held write owes the live place a re-lay, or the canvas a restatement. */
    private boolean mRelayHeld;
    private boolean mSyncHeld;

    public LayoutEditorController(@NonNull Host host) {
        mHost = host;
    }

    /** Whether a session is open. */
    public boolean isActive() {
        return mPlan != null;
    }

    /** The place the session is open on, or null while it is not. */
    @Nullable
    public PaneWallPage editedPlace() {
        return mPlan == null ? null : mPlan.place();
    }

    /** The orientation the canvas and every write are on, or null while no session is open. */
    @Nullable
    public PlaceOrientation shownOrientation() {
        return mPlan == null ? null : mPlan.shownOrientation();
    }

    /** Whether the shown orientation is not the phone's: the live place cannot follow it. */
    public boolean showsOtherOrientation() {
        return mPlan != null && !mPlan.liveFollows();
    }

    /**
     * The Style of the whole chrome and the user's Corners and Margin, in dp, which the canvas
     * draws every shape from.
     */
    public void setShape(@NonNull LayoutStyle style, float cornersDp, float marginDp) {
        mStyle = style;
        mCornersDp = cornersDp;
        mMarginDp = marginDp;
        Views views = mViews;
        if (views != null)
            views.canvas.setShape(style, cornersDp, marginDp);
    }

    public void setOnChangedListener(@Nullable Runnable listener) {
        mOnChanged = listener;
    }

    public void setOnPlaceShownListener(@Nullable Runnable listener) {
        mOnPlaceShown = listener;
    }

    public void setOnSelectionListener(@Nullable Consumer<LayoutCanvasView.Block> listener) {
        mOnSelection = listener;
    }

    // ------------------------------------------------------------------------------------ entry

    /** Takes the views the hosting editor lends; lending the same ones again is a no-op. */
    public void attach(@NonNull Views views) {
        if (mViews == views)
            return;
        mViews = views;
        bind(views);
        if (mPlan != null)
            sync();
    }

    /** Opens the session on one place, the place on screen for null. */
    public boolean begin(@Nullable PaneWallPage place) {
        return begin(place, null);
    }

    /**
     * {@link #begin(PaneWallPage)}, continuing a session whose arrangement at open was
     * {@code carriedEntry} (a configuration relaunch). A session already open moves to the place
     * instead, so Undo still puts back the arrangement it opened on.
     *
     * @return false where there is no store to edit yet
     */
    public boolean begin(@Nullable PaneWallPage place,
                         @Nullable List<PlaceArrangeSnapshot> carriedEntry) {
        PlaceLayoutStore places = mHost.places();
        if (places == null)
            return false;
        PaneWallPage target = place != null ? place : mHost.placeOnScreen();
        if (mPlan == null) {
            mPlan = LayoutEditorPlan.enter(places, target, mHost.placeOrientation(), carriedEntry);
            mExtraKeyCount = mHost.extraKeyCount();
            mKeySlots = mHost.extraKeySlots();
        } else {
            mPlan.showPlace(target);
        }
        mHost.holdPaneWallOnPlace(target, true);
        sync();
        return true;
    }

    /** Whether anything has moved since the session opened, in either orientation. */
    public boolean isDirty() {
        return mPlan != null && mPlan.isDirty();
    }

    /** The arrangements the open session found, or null with none open. */
    @Nullable
    public List<PlaceArrangeSnapshot> entryArrangement() {
        return mPlan == null ? null : mPlan.entries();
    }

    /** Undo: every bar back where the session found it, without leaving the editor. */
    public void revert() {
        if (mPlan == null)
            return;
        mPlan.revert();
        mRelayHeld = false;
        mSyncHeld = false;
        mHost.applyPlaceArrangement();
        if (mViews != null)
            mViews.canvas.setSelectedBlock(null);
        sync();
    }

    /** Back while an element is in the air: it goes home and nothing is written. */
    public boolean cancelGesture() {
        Views views = mViews;
        return mPlan != null && views != null && views.canvas.cancelLift();
    }

    /** Whether the canvas has an element lifted under a finger. */
    public boolean isLifting() {
        Views views = mViews;
        return views != null && views.canvas.isLifting();
    }

    /** Closes the session, keeping what is written; the wall's gestures are handed back. */
    public void end() {
        if (mPlan == null)
            return;
        relayHeldWrites();
        PaneWallPage place = mPlan.place();
        mPlan = null;
        mHeldHandle = null;
        Views views = mViews;
        if (views != null) {
            views.canvas.cancelLift();
            views.canvas.setSelectedBlock(null);
            views.canvas.setHandleReadout(null);
        }
        mHost.holdPaneWallOnPlace(place, false);
    }

    /** The phone turned: the canvas's default and the next write go with it. */
    public void onPlaceOrientationChanged() {
        Views views = mViews;
        if (mPlan == null || views == null)
            return;
        views.canvas.post(() -> {
            if (mPlan == null)
                return;
            mPlan.onDeviceOrientationChanged(mHost.placeOrientation());
            sync();
        });
    }

    // ------------------------------------------------------------------------- the halves

    /** Whether the session edits a Home page. */
    public boolean showsHome() {
        return mPlan != null && mPlan.showsHome();
    }

    /**
     * Moves the session to a Home page or back to its window, and slides the live wall there
     * ({@code animate}) or jumps it, so the place behind the editor still follows every write.
     */
    public void showHome(boolean home, boolean animate) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return;
        if (plan.showsHome() == home) {
            // The session already edits that half; the wall may still stand on the other one.
            if (animate) mHost.slidePaneWallToPlace(plan.place());
            else mHost.holdPaneWallOnPlace(plan.place(), true);
            return;
        }
        PaneWallPage place = plan.showHome(home);
        Views views = mViews;
        if (views != null) {
            views.canvas.cancelLift();
            views.canvas.setSelectedBlock(null);
        }
        if (animate) mHost.slidePaneWallToPlace(place);
        else mHost.holdPaneWallOnPlace(place, true);
        if (mOnPlaceShown != null)
            mOnPlaceShown.run();
        sync();
    }

    /** Whether Home holds a layout of its own. */
    public boolean hasHomeLayout() {
        return mPlan != null && mPlan.hasHomeLayout();
    }

    /** Home's own layout on or off; the page being edited stays. */
    public void setHomeLayout(boolean on) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || plan.hasHomeLayout() == on)
            return;
        plan.setHomeLayout(on);
        Views views = mViews;
        if (views != null) {
            views.canvas.cancelLift();
            views.canvas.setSelectedBlock(null);
        }
        mHost.applyPlaceArrangement();
        if (mOnPlaceShown != null)
            mOnPlaceShown.run();
        sync();
    }

    /** The orientation the canvas shows and the cards write: the phone's, or the other one. */
    public void showOrientation(@NonNull PlaceOrientation orientation) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || plan.shownOrientation() == orientation)
            return;
        plan.showOrientation(orientation);
        if (mViews != null)
            mViews.canvas.setSelectedBlock(null);
        sync();
    }

    // ---------------------------------------------------------------------- the cards' writes

    /** Whether {@code element} stands on its edge in the shown layout (not put away). */
    public boolean isShown(@NonNull Element element) {
        return mPlan != null && !mPlan.shownLayout().slot(element).hidden;
    }

    /** Whether the page being edited wears {@code element} at all. */
    public boolean offers(@NonNull Element element) {
        PlaceLayoutStore places = mHost.places();
        return mPlan != null && places != null
            && com.termux.app.chrome.DressResolver.of(places).dressOf(mPlan.place()).wears(element);
    }

    /** Puts {@code element} away, or brings it back to the edge it left. */
    public void setShown(@NonNull Element element, boolean shown) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || isShown(element) == shown)
            return;
        ownHomeLayoutFor(element);
        LayoutEditorPlan.Drop drop;
        if (shown) {
            LayoutEditorPlan.TrayItem item = trayItemOf(element);
            drop = item == null ? LayoutEditorPlan.Drop.NONE : plan.restore(item);
        } else {
            drop = plan.drop(MiniatureDragPolicy.Bar.of(element), null);
        }
        afterWrite(drop);
    }

    /** Whether the keyboard is on (the switch the palette's Keyboard on/off flips). */
    public boolean isKeyboardShown() {
        return mPlan != null && mPlan.shownLayout().keyboardShown;
    }

    public void setKeyboardShown(boolean shown) {
        if (mPlan == null)
            return;
        if (shown != mPlan.shownLayout().keyboardShown)
            ownHomeLayoutFor(null);
        afterWrite(mPlan.setKeyboardShown(shown));
    }

    /** What the edited layout puts away, in the tray's order (the Layout editor's tray chips). */
    @NonNull
    public List<LayoutEditorPlan.TrayItem> trayItems() {
        return mPlan == null ? new ArrayList<>() : mPlan.trayItems();
    }

    /** Whether the edited layout carries the Drawer tile. */
    public boolean drawerTile() {
        return mPlan != null && mPlan.drawerTile();
    }

    public void setDrawerTile(boolean on) {
        if (mPlan == null)
            return;
        if (on != mPlan.drawerTile())
            ownHomeLayoutFor(null);
        afterWrite(mPlan.setDrawerTile(on));
    }

    /**
     * Home and the Workspace keep each their own hidden set, always. While Home wears the shared
     * layout a hide or a show on either half would land on both, so the first one gives Home a
     * layout of its own, seeded from the shared one so nothing visibly changes, before it is
     * written. Quiet about the canvas: a drop that triggers it is still being reported.
     *
     * @param element what is about to be hidden or shown, or null for the keyboard and the
     *     dock's Search field and Drawer tile, which Home always wears
     */
    private void ownHomeLayoutFor(@Nullable Element element) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || plan.hasHomeLayout())
            return;
        PlaceLayoutStore places = mHost.places();
        if (!plan.showsHome() && places != null && element != null
            && !com.termux.app.chrome.DressResolver.of(places)
                .dressOf(PaneWallPage.WIDGETS).wears(element))
            return;
        plan.setHomeLayout(true);
        mHost.applyPlaceArrangement();
        if (mOnPlaceShown != null)
            mOnPlaceShown.run();
    }

    /** Told when a lifted element is offered the stack to be put away in, and when it hovers it. */
    public void setOnTrayOfferListener(@Nullable java.util.function.BiConsumer<Boolean, Boolean> l) {
        mOnTrayOffer = l;
    }

    /** The edge {@code element} stands on (or returns to while it is put away). */
    @Nullable
    public PlaceLayout.Edge edgeOf(@NonNull Element element) {
        return mPlan == null ? null : mPlan.shownLayout().slot(element).edge;
    }

    /**
     * The edges the layout engine lets {@code element} stand on in the shown layout, in
     * {@link PlaceLayout.Edge} order, its own edge always among them.
     */
    @NonNull
    public List<PlaceLayout.Edge> edgesFor(@NonNull Element element) {
        List<PlaceLayout.Edge> out = new ArrayList<>(4);
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return out;
        PlaceLayout layout = plan.shownLayout();
        MiniatureDragPolicy.Targets targets = MiniatureDragPolicy.targets(plan.place(),
            plan.shownOrientation(), layout, MiniatureDragPolicy.Bar.of(element));
        PlaceLayout.Edge own = layout.slot(element).edge;
        for (PlaceLayout.Edge edge : PlaceLayout.Edge.values()) {
            if (edge == own) {
                out.add(edge);
                continue;
            }
            for (EdgeStackPolicy.Drop drop : targets.drops) {
                if (drop.edge == edge) {
                    out.add(edge);
                    break;
                }
            }
        }
        return out;
    }

    /** Moves {@code element} to {@code edge}, in the band it has always taken there. */
    public void moveTo(@NonNull Element element, @NonNull PlaceLayout.Edge edge) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || plan.shownLayout().slot(element).edge == edge)
            return;
        afterWrite(plan.drop(MiniatureDragPolicy.Bar.of(element), edge));
    }

    /** The dock's height scale in the shown orientation, or -1 with no session. */
    public float dockHeightScale() {
        return mPlan == null ? -1f : mPlan.dockHeightScale();
    }

    /** The dock's height, written through the session so its Undo and dirty state cover it. */
    public void setDockHeightScale(float scale) {
        if (mPlan != null)
            afterWrite(mPlan.setDockHeightScale(scale));
    }

    /**
     * {@link #setDockHeightScale} under a finger that has not let go: written, but the
     * arrangement is re-laid only on {@link #relayHeldWrites}.
     */
    public void holdDockHeightScale(float scale) {
        if (mPlan == null)
            return;
        LayoutEditorPlan.Drop drop = mPlan.setDockHeightScale(scale);
        if (drop == LayoutEditorPlan.Drop.LIVE)
            mRelayHeld = true;
        if (drop != LayoutEditorPlan.Drop.NONE)
            mSyncHeld = true;
    }

    /** The keyboard's height scale in the shown orientation, or -1 with no session. */
    public float keyboardHeightScale() {
        return mPlan == null ? -1f : mPlan.keyboardHeightScale();
    }

    /** The keyboard's height; {@code held} while a finger is still on the slider. */
    public void setKeyboardHeightScale(float scale, boolean held) {
        if (mPlan == null)
            return;
        LayoutEditorPlan.Drop drop = mPlan.setKeyboardHeightScale(scale);
        if (held) {
            if (drop == LayoutEditorPlan.Drop.LIVE) mRelayHeld = true;
            if (drop != LayoutEditorPlan.Drop.NONE) mSyncHeld = true;
            return;
        }
        afterWrite(drop);
    }

    /** The air under the keyboard's last key row in the shown orientation, or -1 with no session. */
    public int keyboardChinDp() {
        return mPlan == null ? -1 : mPlan.keyboardChinDp();
    }

    /**
     * The keyboard's bottom padding (its chin), as its canvas handle writes it; {@code held} while
     * a finger is still on the card's strip.
     */
    public void setKeyboardChinDp(int dp, boolean held) {
        if (mPlan == null)
            return;
        LayoutEditorPlan.Drop drop = mPlan.setKeyboardChinDp(dp);
        if (held) {
            if (drop == LayoutEditorPlan.Drop.LIVE) mRelayHeld = true;
            if (drop != LayoutEditorPlan.Drop.NONE) mSyncHeld = true;
            return;
        }
        afterWrite(drop);
    }

    /** Home's widget grid in the shown orientation, as {columns, rows}; null with no session. */
    @Nullable
    public int[] widgetGrid() {
        return mPlan == null ? null : new int[] {mPlan.widgetColumns(), mPlan.widgetRows()};
    }

    /** Home's widget grid, as its corner handle writes it. */
    public void setWidgetGrid(int columns, int rows) {
        if (mPlan != null)
            afterWrite(mPlan.setWidgetGrid(columns, rows));
    }

    /** The held writes' one re-lay and restatement; nothing when none are held. */
    public void relayHeldWrites() {
        boolean relay = mRelayHeld;
        boolean sync = mSyncHeld;
        mRelayHeld = false;
        mSyncHeld = false;
        if (relay)
            mHost.applyPlaceArrangement();
        if (sync && mPlan != null)
            sync();
    }

    /** The edited layout's Dock design. */
    @NonNull
    public DockStyle dockStyle() {
        return mPlan == null ? DockStyle.CHOICES.get(0) : mPlan.dockStyle();
    }

    public void setDockStyle(@NonNull DockStyle style) {
        if (mPlan != null)
            afterWrite(mPlan.setDockStyle(style));
    }

    /** Whether the edited layout carries the Search field. */
    public boolean searchField() {
        return mPlan != null && mPlan.searchField();
    }

    public void setSearchField(boolean on) {
        if (mPlan == null)
            return;
        if (on != mPlan.searchField())
            ownHomeLayoutFor(null);
        afterWrite(mPlan.setSearchField(on));
    }

    /** The keyboard's form in the shown orientation. */
    @NonNull
    public PlaceLayout.KeyboardForm keyboardForm() {
        return mPlan == null ? PlaceLayout.KeyboardForm.DOCKED : mPlan.keyboardForm();
    }

    public void setKeyboardForm(@NonNull PlaceLayout.KeyboardForm form) {
        if (mPlan != null)
            afterWrite(mPlan.setKeyboardForm(form));
    }

    /** The tray item that brings {@code element} back. */
    @Nullable
    static LayoutEditorPlan.TrayItem trayItemOf(@NonNull Element element) {
        for (LayoutEditorPlan.TrayItem item : LayoutEditorPlan.TrayItem.values()) {
            if (item.element == element)
                return item;
        }
        return null;
    }

    /** The tray item a canvas block stands for; null for what has none. */
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

    // ------------------------------------------------------------------------------ the canvas

    @SuppressLint("ClickableViewAccessibility")
    private void bind(@NonNull Views views) {
        views.canvas.setLegendVisible(false);
        views.canvas.setFillsView(true);
        // No restore tray stands by the canvas: an element is put away by its card's switch.
        views.canvas.setExternalTrayRect(null);
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
                if (mOnSelection != null)
                    mOnSelection.accept(selected);
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

            @Override public void onWallStripHeightDragged(int dp) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.WALL_STRIP_HEIGHT,
                        mPlan.setWallStripHeightDp(dp));
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

            @Override public void onTrayOfferChanged(boolean offered, boolean hovered) {
                if (mOnTrayOffer != null)
                    mOnTrayOffer.accept(offered, hovered);
            }

            @Override public void onKeyboardPutAway() {
                if (mPlan != null) {
                    ownHomeLayoutFor(null);
                    afterWrite(mPlan.setKeyboardShown(false));
                }
            }

            @Override public void onKeyboardRestored() {
                if (mPlan != null)
                    afterWrite(mPlan.setKeyboardShown(true));
            }
        });
    }

    /** A bar dropped into a gap in an edge's stack. */
    @VisibleForTesting
    void onBarDropped(@NonNull LayoutCanvasView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index, boolean underKeyboard) {
        MiniatureDragPolicy.Bar bar = LayoutCanvasView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (edge == null)
            ownHomeLayoutFor(bar.element());
        if (mPlan.drop(bar, edge, index, underKeyboard) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** The store the session edits, or null before the launcher has one. */
    @Nullable
    public PlaceLayoutStore places() {
        return mHost.places();
    }

    /**
     * Something wrote the store's layout keys directly (the editor's Match rows): the chrome is
     * arranged again and the canvas re-read, as after a revert.
     */
    public void applyStoreWrite() {
        if (mPlan == null)
            return;
        mHost.applyPlaceArrangement();
        sync();
    }

    /** Re-reads the canvas from the plan, and tells the host something may have moved. */
    public void sync() {
        Views views = mViews;
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return;
        if (views != null) {
            views.canvas.setShape(mStyle, mCornersDp, mMarginDp);
            views.canvas.setExtraKeyCount(mExtraKeyCount);
            views.canvas.setExtraKeySlots(mKeySlots);
            views.canvas.setSizes(plan.dockHeightScale(), plan.keyboardHeightScale(),
                plan.keyboardChinDp());
            views.canvas.setWallStrip(plan.showsWallStrip() ? plan.wallStripHeightDp() : 0);
            PlaceLayoutStore places = mHost.places();
            if (places != null)
                views.canvas.setStatusCompact(places.isStatusCompact(plan.shownOrientation()));
            views.canvas.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        }
        notifyChanged();
    }

    private void notifyChanged() {
        if (mOnChanged != null)
            mOnChanged.run();
    }

    /** A write landed: the live place follows it where it was written for the phone's orientation. */
    private void afterWrite(@NonNull LayoutEditorPlan.Drop drop) {
        if (drop == LayoutEditorPlan.Drop.NONE)
            return;
        if (drop == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** A handle moved: the value written through, and its readout restated. */
    private void onHandleWrite(@NonNull LayoutCanvasView.Handle handle,
                               @NonNull LayoutEditorPlan.Drop drop) {
        mHeldHandle = handle;
        afterWrite(drop);
        Views views = mViews;
        if (views == null)
            return;
        views.canvas.setHandleReadout(readoutFor(handle));
        views.canvas.post(() -> {
            if (mViews == views && mHeldHandle == handle)
                views.canvas.setHandleReadout(readoutFor(handle));
        });
    }

    /** What a held handle reads out, in real units. */
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
            case WALL_STRIP_HEIGHT:
                return context.getString(R.string.layout_editor_readout_wall_strip,
                    plan.wallStripHeightDp());
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
}
