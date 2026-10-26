package com.termux.app.layouteditor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.List;

/**
 * The layout session under the Appearance editor. It owns one session: the arrangement the editor opened on, its share of the editor's one dirty
 * state, Undo and Done ({@link #isDirty}, {@link #revert}, {@link #end}), and every write the
 * cards make to the arrangement: whether an element shows, the edge it stands on, the dock's and
 * keyboard's heights, the Strip's height, the Dock design, the Search field and the keyboard's
 * form. Every write goes straight through, so the live place under the editor follows it. The
 * session edits the orientation the phone is in and turns with it (editor-redesign §1: the
 * shrunken Layout mode, with its canvas, its drags and its handles, is retired; its controls are
 * rows on the cards).
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
    }

    @NonNull private final Host mHost;
    @Nullable private LayoutEditorPlan mPlan;
    /** Told whenever something may have moved, so the host's one Undo can show or go. */
    @Nullable private Runnable mOnChanged;
    /** Told when the session moved to another page, so the host restates what that page wears. */
    @Nullable private Runnable mOnPlaceShown;
    /** A held write owes the live place a re-lay, or the host a restatement. */
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

    /** The orientation every write is on (the phone's), or null while no session is open. */
    @Nullable
    public PlaceOrientation shownOrientation() {
        return mPlan == null ? null : mPlan.shownOrientation();
    }

    public void setOnChangedListener(@Nullable Runnable listener) {
        mOnChanged = listener;
    }

    public void setOnPlaceShownListener(@Nullable Runnable listener) {
        mOnPlaceShown = listener;
    }

    // ------------------------------------------------------------------------------------ entry

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
        sync();
    }

    /** Closes the session, keeping what is written; the wall's gestures are handed back. */
    public void end() {
        if (mPlan == null)
            return;
        relayHeldWrites();
        PaneWallPage place = mPlan.place();
        mPlan = null;
        mHost.holdPaneWallOnPlace(place, false);
    }

    /**
     * The phone turned: every write goes to the orientation it is in now (rotate to edit). The
     * caller runs this once the window is laid out in the new configuration.
     */
    public void onPlaceOrientationChanged() {
        if (mPlan == null)
            return;
        mPlan.onDeviceOrientationChanged(mHost.placeOrientation());
        sync();
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
        if (animate) mHost.slidePaneWallToPlace(place);
        else mHost.holdPaneWallOnPlace(place, true);
        if (mOnPlaceShown != null)
            mOnPlaceShown.run();
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
        afterWrite(mPlan.setKeyboardShown(shown));
    }

    /** What the edited layout puts away, in the tray's order (the editor's hidden stack). */
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
        afterWrite(mPlan.setDrawerTile(on));
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
     * The keyboard's bottom padding (its chin, the Keyboard card's Bottom padding); {@code held} while
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

    /** The Strip type's row height in the shown orientation, in dp, or -1 with no session. */
    public int wallStripHeightDp() {
        return mPlan == null ? -1 : mPlan.wallStripHeightDp();
    }

    /** The Strip type's row height (the Dock card's Strip height), through the session's Undo. */
    public void setWallStripHeightDp(int dp) {
        if (mPlan != null)
            afterWrite(mPlan.setWallStripHeightDp(dp));
    }

    /** Home's widget grid in the shown orientation, as {columns, rows}; null with no session. */
    @Nullable
    public int[] widgetGrid() {
        return mPlan == null ? null : new int[] {mPlan.widgetColumns(), mPlan.widgetRows()};
    }

    /** Home's widget grid (the Home card's Columns and Rows). */
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

    /** The store the session edits, or null before the launcher has one. */
    @Nullable
    public PlaceLayoutStore places() {
        return mHost.places();
    }

    /**
     * Something wrote the store's layout keys directly (the editor's Match rows): the chrome is
     * arranged again and the session re-read, as after a revert.
     */
    public void applyStoreWrite() {
        if (mPlan == null)
            return;
        mHost.applyPlaceArrangement();
        sync();
    }

    /** Tells the host something may have moved, so its Undo, stack and card follow. */
    public void sync() {
        if (mPlan == null)
            return;
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

    // ------------------------------------------------------------------------- Dock card

    /** The edited layout as stored, before the page's dress, or null with no session. */
    @Nullable
    public PlaceLayout arrangedLayout() {
        return mPlan == null ? null : mPlan.arrangedLayout();
    }

    /** Writes every slot of {@code next} that moved; the live place follows. */
    public void writeSlots(@NonNull PlaceLayout next) {
        if (mPlan != null)
            afterWrite(mPlan.writeSlots(next));
    }

    /** The Dock card's Bars list as stored for the shown orientation, or null. */
    @Nullable
    public String barOrder() {
        return mPlan == null ? null : mPlan.barOrder();
    }

    public void setBarOrder(@NonNull String order) {
        if (mPlan != null)
            mPlan.setBarOrder(order);
    }
}
