package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.List;

/**
 * One Layout editor session, as decisions rather than views: which orientation the layout canvas is
 * showing, which one a drop writes, whether the live place behind the editor follows it, whether
 * anything has moved since the editor opened, and how to put it all back.
 *
 * <p>The layout canvas draws either orientation whatever the phone is in, so "shown" and "the phone's"
 * are two different questions. A drop always writes the shown one — that is the orientation the
 * user is looking at a picture of — and the place behind the editor can only follow while the two
 * agree. Editing the other orientation therefore changes the layout canvas and nothing else until the
 * phone is turned.
 *
 * <p>There are no rows under the canvas (spec §3.5): what used to be a row is a handle on the
 * canvas — the dock's height, the keyboard's height and chin, Home's grid — the tray, which puts
 * any element away and brings it back, and the keyboard's type chips. Each writes the orientation
 * on the toggle through the store's own setters, the same way a drop does, so the snapshot and the
 * dirty check see every one of them.
 *
 * <p>The layout is every place's (ADR 0003), so there is no place to pick and every write lands
 * everywhere. The session still knows which place it was opened over, because that is what the
 * layout canvas draws — Home's grid, the terminal's text, the display's screen — and what decides
 * the handles worth offering: the grid's only on Home.
 *
 * <p>Pure: a store in, an answer out, no views, so every case above is testable without a window.
 * {@link LayoutEditorController} is the wiring that draws it, in Layout mode of the one editor.
 * The canvas stands in the editor's frame at its full size (spec §3.5), so there is no sheet or
 * layout canvas to size here any more.
 */
public final class LayoutEditorPlan {

    /** What one drop on the layout canvas did. */
    public enum Drop {
        /** The bar cannot stand there: nothing was written and nothing moved. */
        NONE,
        /** Written for an orientation the phone is not in: the layout canvas moves, the place does not. */
        MINIATURE,
        /** Written for the orientation on screen: the live place follows it. */
        LIVE
    }

    /** The store pinned to the variant being edited, so nothing here can reach the other one. */
    @NonNull private final PlaceLayoutStore mPlaces;
    @NonNull private final LayoutVariant mVariant;
    /** The place the layout canvas draws and the live place behind the card; never a write target. */
    @NonNull private PaneWallPage mPlace;
    /** The arrangement as the editor found it; what Discard and ↺ put back. */
    @NonNull private final PlaceArrangeSnapshot mEntry;
    @NonNull private final String mEntrySignature;
    @NonNull private PlaceOrientation mDeviceOrientation;
    @NonNull private PlaceOrientation mShownOrientation;

    private LayoutEditorPlan(@NonNull PlaceLayoutStore places, @NonNull LayoutVariant variant,
                             @NonNull PaneWallPage place, @NonNull PlaceOrientation deviceOrientation) {
        mVariant = variant;
        mPlaces = places.forVariant(variant);
        mPlace = place;
        mDeviceOrientation = deviceOrientation;
        // The editor opens on what the user is looking at; the toggle is how the other one is asked
        // for.
        mShownOrientation = deviceOrientation;
        mEntry = PlaceArrangeSnapshot.capture(mPlaces, variant);
        mEntrySignature = mEntry.signature();
    }

    /**
     * Opens a session over one place, showing the orientation the phone is in, on the layout the
     * launcher is standing in: the minimal one while minimal mode is on.
     */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation) {
        return enter(places, places.activeVariant(), place, deviceOrientation);
    }

    /** Opens a session over one variant of the layout. */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull LayoutVariant variant,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation) {
        return new LayoutEditorPlan(places, variant, place, deviceOrientation);
    }

    /** Which layout the session is editing, which the card says so it is never a surprise. */
    @NonNull
    public LayoutVariant variant() {
        return mVariant;
    }

    @NonNull
    public PaneWallPage place() {
        return mPlace;
    }

    /** The orientation on the layout canvas, which is also the one every drop writes. */
    @NonNull
    public PlaceOrientation shownOrientation() {
        return mShownOrientation;
    }

    /** The orientation the phone is in, which is what the place behind the editor is showing. */
    @NonNull
    public PlaceOrientation deviceOrientation() {
        return mDeviceOrientation;
    }

    /** Whether a write lands on the arrangement the live place behind the editor is drawing. */
    public boolean liveFollows() {
        return mShownOrientation == mDeviceOrientation;
    }

    /** The arrangement the layout canvas draws: the shown orientation's, resolved. */
    @NonNull
    public PlaceLayout shownLayout() {
        return mPlaces.resolve(mShownOrientation);
    }

    // ------------------------------------------------------------------------ what a handle does

    /*
     * Layout has no rows (spec §3.5): every size is a handle on the canvas and every on/off is the
     * tray. These are what those write, for the orientation on the toggle, through the store's own
     * setters and clamps, so the snapshot and the dirty check see them like any drop.
     */

    /** The dock's height in the shown orientation, as a multiple of its unscaled height. */
    public float dockHeightScale() {
        return mPlaces.dockHeightScale(mShownOrientation);
    }

    /** The keyboard's height in the shown orientation, as a multiple of its unscaled height. */
    public float keyboardHeightScale() {
        return mPlaces.keyboardHeightScale(mShownOrientation);
    }

    /** The air under the keyboard's last key row in the shown orientation, in dp. */
    public int keyboardChinDp() {
        return mPlaces.keyboardChinDp(mShownOrientation);
    }

    @NonNull
    public PlaceLayout.KeyboardForm keyboardForm() {
        return mPlaces.keyboardForm(mShownOrientation);
    }

    /** The dock's handle dragged. */
    @NonNull
    public Drop setDockHeightScale(float scale) {
        mPlaces.setDockHeightScale(mShownOrientation, scale);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** The keyboard's top handle dragged. */
    @NonNull
    public Drop setKeyboardHeightScale(float scale) {
        mPlaces.setKeyboardHeightScale(mShownOrientation, scale);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** The keyboard's bottom handle dragged: the chin, which Settings called bottom padding. */
    @NonNull
    public Drop setKeyboardChinDp(int dp) {
        mPlaces.setKeyboardChinDp(mShownOrientation, dp);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** Home's corner handle dragged: the grid in whole cells. */
    @NonNull
    public Drop setWidgetGrid(int columns, int rows) {
        mPlaces.setWidgetColumns(mShownOrientation, columns);
        mPlaces.setWidgetRows(mShownOrientation, rows);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /**
     * One of the keyboard's type chips: docked, floating or split, for the shown orientation of
     * the variant being edited, which is the key Settings' Keyboard type row used to write.
     */
    @NonNull
    public Drop setKeyboardForm(@NonNull PlaceLayout.KeyboardForm form) {
        if (form == keyboardForm())
            return Drop.NONE;
        mPlaces.setKeyboardForm(mShownOrientation, form);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /**
     * The keyboard dropped in the tray, or brought back out of it. One switch for both
     * orientations and every place — the palette's Keyboard on/off — so the live place follows it
     * whichever orientation the toggle shows.
     */
    @NonNull
    public Drop setKeyboardShown(boolean shown) {
        if (mPlaces.isKeyboardShown() == shown)
            return Drop.NONE;
        mPlaces.setKeyboardShown(shown);
        return Drop.LIVE;
    }

    // ------------------------------------------------------------------------------- the tray

    /** One chip in the tray: a hidden bar, or the keyboard switched off. */
    public enum TrayItem {
        STATUS_BAR(Element.STATUS),
        PINNED_APPS(Element.APPS),
        AZ_INDEX(Element.AZ),
        EXTRA_KEYS(Element.EXTRA_KEYS),
        KEYBOARD(null);

        /** The bar this chip brings back, or null for the keyboard, which has no slot. */
        @Nullable public final Element element;

        TrayItem(@Nullable Element element) {
            this.element = element;
        }
    }

    /**
     * What the tray holds for the shown orientation, in the order it lists them: the bars the
     * arrangement puts away, then the keyboard while it is switched off.
     */
    @NonNull
    public List<TrayItem> trayItems() {
        List<TrayItem> items = new ArrayList<>(5);
        PlaceLayout layout = shownLayout();
        for (TrayItem item : TrayItem.values()) {
            if (item.element == null) {
                if (!layout.keyboardShown) items.add(item);
            } else if (layout.slot(item.element).hidden) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * A tray chip tapped: the element comes back to the edge it was put away from, at the place
     * in that edge's stack it held, the way a bar that was never moved comes back to its default
     * edge (spec §3.6). The keyboard is switched back on.
     */
    @NonNull
    public Drop restore(@NonNull TrayItem item) {
        if (item.element == null)
            return setKeyboardShown(true);
        Slot slot = mPlaces.slot(mShownOrientation, item.element);
        if (!slot.hidden && !shownLayout().slot(item.element).hidden)
            return Drop.NONE;
        // The terminal's own toolbar switch can have put the extra keys away everywhere; setSlot
        // turns it back on, since placing the keys somewhere is asking to see them.
        mPlaces.setSlot(mShownOrientation, item.element, slot.withHidden(false));
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }
    /**
     * The place on the layout canvas. A second door opened while the editor is up moves it rather than
     * starting a session over, so what Discard puts back is still the arrangement the user first
     * opened the editor on. It changes the picture and the rows offered, never what is written.
     */
    public void showPlace(@NonNull PaneWallPage place) {
        mPlace = place;
    }

    /** The Portrait / Landscape toggle. Moves the picture and the target of the next drop. */
    public void showOrientation(@NonNull PlaceOrientation orientation) {
        mShownOrientation = orientation;
    }

    /**
     * The phone turned mid-session. The layout canvas goes with it: the editor is a picture of the
     * place behind it, and leaving it on the orientation the user can no longer see would make the
     * next drop land somewhere they are not looking.
     */
    public void onDeviceOrientationChanged(@NonNull PlaceOrientation orientation) {
        mDeviceOrientation = orientation;
        mShownOrientation = orientation;
    }

    /**
     * A bar dropped into a gap in an edge's stack, or in the tray when {@code edge} is null.
     * Writes the shown orientation's keys, for every place, and says whether the live place behind
     * has to follow.
     *
     * @param index the position in that edge's stack, 0 outermost, or negative for the band the
     *     bar has always taken there
     */
    @NonNull
    public Drop drop(@NonNull MiniatureDragPolicy.Bar bar, @Nullable PlaceLayout.Edge edge,
                     int index) {
        return drop(bar, edge, index, false);
    }

    /** A bar dropped on either side of the keyboard; {@code underKeyboard} is the far one. */
    @NonNull
    public Drop drop(@NonNull MiniatureDragPolicy.Bar bar, @Nullable PlaceLayout.Edge edge,
                     int index, boolean underKeyboard) {
        if (!LayoutChooserModel.applyDrop(mPlaces, mShownOrientation, bar, edge, index,
            underKeyboard))
            return Drop.NONE;
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** A bar dropped on an edge without a gap picked. */
    @NonNull
    public Drop drop(@NonNull MiniatureDragPolicy.Bar bar, @Nullable PlaceLayout.Edge edge) {
        return drop(bar, edge, -1);
    }

    /**
     * Whether this arrangement has left the canvas narrow enough to say so: bars down the side of
     * a portrait screen, with little width beside them. The editor shows a line about it and
     * nothing else — a narrow canvas is allowed, it is only worth knowing about.
     */
    public boolean warnsNarrowCanvas() {
        return MiniatureDragPolicy.warnsNarrowCanvas(shownLayout(), mShownOrientation);
    }

    /**
     * Whether the status bar's own slot has landed on a side edge, where it never rests expanded
     * ({@code StatusBarGesturePolicy.expansionAllowed}). The editor shows a line about it and
     * nothing else — a side status bar is allowed, it is only worth knowing about.
     */
    public boolean warnsSideStatusBar() {
        return shownLayout().slot(com.termux.app.place.Element.STATUS).edge.isOnSide();
    }

    /**
     * Whether the toggle is showing the orientation the phone is not in, where the place behind
     * the card cannot follow what is being edited until the phone is turned. The editor shows a
     * line about it and nothing else — the other orientation is what the toggle is for, it is only
     * worth knowing which one the edits are landing on.
     */
    public boolean warnsOtherOrientation() {
        return !liveFollows();
    }

    /** Whether anything has moved since the editor opened — the unsaved-changes question. */
    public boolean isDirty() {
        return !mEntrySignature.equals(PlaceArrangeSnapshot.capture(mPlaces).signature());
    }

    /** Puts the arrangement back the way the editor found it. */
    public void revert() {
        mEntry.restore(mPlaces);
    }
}
