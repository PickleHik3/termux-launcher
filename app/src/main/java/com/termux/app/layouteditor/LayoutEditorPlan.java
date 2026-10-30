package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 * <p>One layout dresses both halves (ADR 0009), so a write there lands on every window and on
 * Home. Only while Home holds a Home layout does a Home page wear one of its own: the session
 * edits the layout of the page it stands on, as its dress says, and the Home filter ({@link
 * #showHome}) moves it between a Home page and its window, each layout's arrangement at entry
 * kept for Undo. The session knows which place it is on, because that is what the layout
 * canvas draws — Home's grid, the terminal's text, the display's screen — and what decides the
 * handles worth offering: the grid's only on Home. Which elements the tray and a drop offer, and
 * whether the dock's design is picked here, are the edited layout's page's {@link Dress}.
 *
 * <p>Pure: a store in, an answer out, no views, so every case above is testable without a window.
 * {@link LayoutEditorController} is the wiring under the Appearance editor's cards. The shrunken
 * Layout mode that drew this on a canvas is retired (editor-redesign §1); the session now always
 * shows the phone's orientation, and {@link #showOrientation} is left for the policy tests.
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

    /** The store every variant is reached through. */
    @NonNull private final PlaceLayoutStore mRoot;
    /**
     * The store pinned to the variant being edited, so nothing here can reach another one. The
     * Home filter ({@link #showHome}) is the only thing that moves it.
     */
    @NonNull private PlaceLayoutStore mPlaces;
    @NonNull private LayoutVariant mVariant;
    /** What a page wears: the layout the session edits there, and the elements it offers. */
    @NonNull private final DressResolver mDresses;
    /**
     * The page whose layout is being edited, so whose {@link Dress} says what is offered: the page
     * the session opened on, until the Home filter moves it. A second door ({@link #showPlace})
     * moves the picture and leaves it.
     */
    @NonNull private PaneWallPage mDressedPage;
    /** The place the layout canvas draws and the live place behind the card; never a write target. */
    @NonNull private PaneWallPage mPlace;
    /**
     * The window the Home filter hands back to when it is turned off: the window the editor was
     * opened over, or the terminal when it was opened over Home.
     */
    @NonNull private PaneWallPage mWindowPlace;
    /**
     * The arrangement of every variant as the editor found it; what Discard and ↺ put back. All
     * the session may touch are taken at open (never the Minimal layout from a normal-mode
     * session, nor the normal one from a Minimal-mode session), the one the session opened on
     * first: a card writes the layout
     * the half on screen wears, and a write must never land on a layout the session has no entry
     * for, or Discard would leave it behind. A carried session keeps the entries it brought; the
     * sizes the Home and normal layouts share (linked, #84) come from the entry already kept for
     * the other ({@link #enterVariant}).
     */
    @NonNull private final Map<LayoutVariant, PlaceArrangeSnapshot> mEntries =
        new java.util.LinkedHashMap<>();
    @NonNull private final Map<LayoutVariant, String> mEntrySignatures =
        new java.util.LinkedHashMap<>();
    @NonNull private PlaceOrientation mDeviceOrientation;
    @NonNull private PlaceOrientation mShownOrientation;
    /**
     * Every page the session has dressed, by id, and whether it held a page layout when the
     * session first reached it: the flags Discard puts back too (ADR 0015 §3).
     */
    @NonNull private final Map<Integer, Boolean> mEntryPageLayouts = new java.util.LinkedHashMap<>();
    @NonNull private final Map<Integer, com.termux.app.wall.WallPages.PageKind> mEntryPageKinds =
        new java.util.LinkedHashMap<>();

    private LayoutEditorPlan(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                             @NonNull PlaceOrientation deviceOrientation,
                             @Nullable List<PlaceArrangeSnapshot> carriedEntries) {
        mRoot = places;
        mDresses = DressResolver.of(places);
        mDressedPage = place;
        LayoutVariant variant = dress().layout();
        mVariant = variant;
        mPlaces = places.forVariant(variant);
        mPlace = place;
        mWindowPlace = place == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL : place;
        mDeviceOrientation = deviceOrientation;
        // The editor opens on what the user is looking at; the toggle is how the other one is asked
        // for.
        mShownOrientation = deviceOrientation;
        // A session carried over an activity relaunch keeps the arrangements it opened on, every
        // variant it had reached; the one being edited now is taken fresh if it had not.
        if (carriedEntries != null) {
            for (PlaceArrangeSnapshot entry : carriedEntries)
                keepEntry(entry);
        }
        enterVariant(variant);
        // What the session may touch: the layout the page wears and the Workspace layout the
        // launcher stands in. A session opened in normal mode never snapshots (so never reverts)
        // Minimal; one opened in Minimal edits Minimal alone. The page's own layout flag is
        // remembered so Discard puts it back (ADR 0015 §3).
        LayoutVariant workspace = LayoutVariant.of(places.isMinimal());
        enterVariant(workspace);
        rememberPage(places.shownPageId(place), DressResolver.kindOf(place));
    }

    /** Keeps {@code pageId}'s page-layout flag as the session first found it. */
    private void rememberPage(int pageId, @NonNull com.termux.app.wall.WallPages.PageKind kind) {
        if (pageId < 0 || mEntryPageLayouts.containsKey(pageId))
            return;
        mEntryPageLayouts.put(pageId, mRoot.hasPageLayout(pageId));
        mEntryPageKinds.put(pageId, kind);
    }

    // ------------------------------------------------------------------ the page-layout chip

    /** The page being dressed, by id; -1 where the launcher has not named it. */
    public int pageId() {
        return mRoot.shownPageId(mDressedPage);
    }

    /** The kind of the page being dressed. */
    @NonNull
    public com.termux.app.wall.WallPages.PageKind pageKind() {
        return DressResolver.kindOf(mDressedPage);
    }

    /**
     * Whether the "<kind> | <page>" chip is offered: the page is known and the session is not on
     * the minimal layout, which is one switch for every window.
     */
    public boolean offersPageLayout() {
        return pageId() >= 0 && mVariant != LayoutVariant.MINIMAL;
    }

    /** The chip's page side: whether the page being dressed wears a layout of its own. */
    public boolean pageLayoutSide() {
        int id = pageId();
        return id >= 0 && mRoot.hasPageLayout(id);
    }

    /**
     * The chip: the page side gives the page a layout of its own, seeded from what it showed,
     * and the session edits it from then on; the kind side puts the page back on its kind's.
     * Either way the flag and the page layout are in the session, so Discard puts them back.
     */
    public void setPageLayoutSide(boolean pageSide) {
        int id = pageId();
        if (id < 0 || mVariant == LayoutVariant.MINIMAL || pageLayoutSide() == pageSide)
            return;
        com.termux.app.wall.WallPages.PageKind kind = pageKind();
        rememberPage(id, kind);
        // The page layout as it stood is kept before the seed writes over it.
        if (pageSide)
            enterVariant(LayoutVariant.page(id, kind));
        mRoot.setPageLayout(id, kind, pageSide);
        mVariant = dress().layout();
        mPlaces = mRoot.forVariant(mVariant);
        enterVariant(mVariant);
    }

    /** Whether any page's layout flag differs from what the session found. */
    private boolean pageFlagsMoved() {
        for (Map.Entry<Integer, Boolean> entry : mEntryPageLayouts.entrySet()) {
            if (mRoot.hasPageLayout(entry.getKey()) != entry.getValue())
                return true;
        }
        return false;
    }

    private void keepEntry(@NonNull PlaceArrangeSnapshot entry) {
        mEntries.put(entry.variant(), entry);
        mEntrySignatures.put(entry.variant(), entry.signature());
    }

    /**
     * Takes the variant's arrangement as it stands, the first time the session reaches it. Its
     * sizes may already have been edited through a layout it shares them with (the Home and
     * normal layouts, {@link PlaceLayoutStore#sizesLinked}), so those come from that layout's
     * entry: both entries then hold the sizes as the session found them.
     */
    private void enterVariant(@NonNull LayoutVariant variant) {
        if (mEntries.containsKey(variant))
            return;
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(mRoot, variant);
        for (PlaceArrangeSnapshot kept : mEntries.values()) {
            if (PlaceLayoutStore.sizesLinked(variant, kept.variant())) {
                entry = entry.withSizesOf(kept);
                break;
            }
        }
        keepEntry(entry);
    }

    /**
     * Opens a session over one page, showing the orientation the phone is in, on the layout that
     * page is arranged by: the Home layout on a Home page, and on a window the Workspace layout
     * the launcher is standing in, the minimal one while minimal mode is on.
     */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation) {
        return new LayoutEditorPlan(places, place, deviceOrientation, null);
    }

    /**
     * {@link #enter(PlaceLayoutStore, PaneWallPage, PlaceOrientation)} continuing a session whose
     * arrangements at open were {@code carriedEntries} (an activity relaunch), so its Undo and its
     * unsaved state are the same, for every variant it had reached.
     */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation,
                                         @Nullable List<PlaceArrangeSnapshot> carriedEntries) {
        return new LayoutEditorPlan(places, place, deviceOrientation, carriedEntries);
    }

    /** What the page whose layout is being edited wears, as the store stands now. */
    @NonNull
    private Dress dress() {
        return mDresses.dressOf(mDressedPage);
    }

    /**
     * The arrangements as the editor found them, every variant the session has reached, to carry
     * the session over a relaunch.
     */
    @NonNull
    public List<PlaceArrangeSnapshot> entries() {
        return new ArrayList<>(mEntries.values());
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

    /**
     * The arrangement the layout canvas draws: the shown orientation's, resolved as the edited
     * layout's page wears it, so a Home page on the shared layout is drawn without the extra keys
     * and with its own keyboard element.
     */
    @NonNull
    public PlaceLayout shownLayout() {
        return mRoot.resolve(mShownOrientation, mVariant, mDressedPage);
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

    /**
     * Whether the edited layout's dock wears the Wall strip, so the canvas draws its first row and
     * the dock's selection carries the row's own handle.
     */
    public boolean showsWallStrip() {
        return dockStyle() == DockStyle.WALL_STRIP;
    }

    /** The Wall strip's first row in the shown orientation, in dp. */
    public int wallStripHeightDp() {
        return mPlaces.wallStripHeightDp(mShownOrientation);
    }

    /** The Strip row's handle dragged: its height, for the edited layout's shown orientation. */
    @NonNull
    public Drop setWallStripHeightDp(int dp) {
        mPlaces.setWallStripHeightDp(mShownOrientation, dp);
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** Home's widget grid in the shown orientation: its columns. */
    public int widgetColumns() {
        return mPlaces.widgetColumns(mShownOrientation);
    }

    /** Home's widget grid in the shown orientation: its rows. */
    public int widgetRows() {
        return mPlaces.widgetRows(mShownOrientation);
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
     * orientations and every window — the palette's Keyboard on/off — so the live place follows it
     * whichever orientation the toggle shows. On a Home page it is always on instead, Home's own
     * whichever layout Home wears: whether the keyboard is up is each half's, not the layout's.
     */
    @NonNull
    public Drop setKeyboardShown(boolean shown) {
        boolean home = mDressedPage == PaneWallPage.WIDGETS;
        if ((home ? mRoot.isHomeKeyboardAlwaysOn() : mPlaces.isKeyboardShown()) == shown)
            return Drop.NONE;
        if (home)
            mRoot.setHomeKeyboardAlwaysOn(shown);
        else
            mPlaces.setKeyboardShown(shown);
        return Drop.LIVE;
    }

    // ------------------------------------------------------------------------ the Dock design

    /**
     * The edited layout's Dock design as picked, whether or not it is drawn yet: what the picker
     * marks. The shared layout's while Home wears it, the Home layout's while Home holds one.
     */
    @NonNull
    public DockStyle dockStyle() {
        return mPlaces.dockStyle();
    }

    /**
     * A Dock design picked, for the edited layout. One value for both orientations, so the live
     * dock follows it whichever orientation the toggle shows. Every layout holds one, Home's and
     * the windows' alike (#93).
     */
    @NonNull
    public Drop setDockStyle(@NonNull DockStyle style) {
        // Only what the picker offers: the Prompt line is the Search field now (#96), the Taskbar
        // the Drawer tile (#97).
        if (!DockStyle.CHOICES.contains(style) || style == dockStyle())
            return Drop.NONE;
        mPlaces.setDockStyle(style);
        return Drop.LIVE;
    }

    // ------------------------------------------------------------------- the Search field

    /** Whether the edited layout turns the Search field on: what the toggle shows. */
    public boolean searchField() {
        return mPlaces.searchField();
    }

    /**
     * The Search field turned on or off for the edited layout, both orientations, its design
     * left as it is; the live dock grows or loses the field under its apps row.
     */
    @NonNull
    public Drop setSearchField(boolean on) {
        if (on == searchField())
            return Drop.NONE;
        mPlaces.setSearchField(on);
        return Drop.LIVE;
    }

    // -------------------------------------------------------------------- the Drawer tile

    /** Whether the edited layout turns the Drawer tile on: what the toggle shows. */
    public boolean drawerTile() {
        return mPlaces.drawerTile();
    }

    /**
     * The Drawer tile turned on or off for the edited layout, both orientations, its design and
     * the tile's place in the row left as they are; the live dock's row takes or gives back the
     * tile's slot.
     */
    @NonNull
    public Drop setDrawerTile(boolean on) {
        if (on == drawerTile())
            return Drop.NONE;
        mPlaces.setDrawerTile(on);
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
     * arrangement puts away, then the keyboard while it is switched off. Only what the page whose
     * layout is being edited wears: Home's tray never holds the status bar or the extra keys,
     * and its keyboard chip, brought back, turns always on on.
     */
    @NonNull
    public List<TrayItem> trayItems() {
        List<TrayItem> items = new ArrayList<>(5);
        PlaceLayout layout = shownLayout();
        Dress dress = dress();
        for (TrayItem item : TrayItem.values()) {
            if (item.element == null) {
                if (!layout.keyboardShown) items.add(item);
            } else if (dress.wears(item.element) && layout.slot(item.element).hidden) {
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
        if (!dress().wears(item.element))
            return Drop.NONE;
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
        if (place != PaneWallPage.WIDGETS)
            mWindowPlace = place;
    }

    /** Whether the Home filter is on: the canvas is drawing a Home page. */
    public boolean showsHome() {
        return mPlace == PaneWallPage.WIDGETS;
    }

    /**
     * The Home filter, beside the Portrait / Landscape toggle: on, the canvas shows a Home page and
     * every write lands on the Home layout; off, it shows the window the editor was opened over
     * (the terminal when that was Home) and writes the Workspace's layout, the normal one or the
     * minimal one as minimal mode says. The orientation stays as it was. Each layout's arrangement
     * at entry is kept, so Undo and the unsaved question cover both.
     *
     * @return the place the canvas now shows, which the live wall is to stand on
     */
    @NonNull
    public PaneWallPage showHome(boolean home) {
        PaneWallPage place = home ? PaneWallPage.WIDGETS : mWindowPlace;
        mPlace = place;
        mDressedPage = place;
        rememberPage(pageId(), pageKind());
        mVariant = dress().layout();
        mPlaces = mRoot.forVariant(mVariant);
        enterVariant(mVariant);
        return place;
    }

    /** The Portrait / Landscape toggle. Moves the picture and the target of the next drop. */
    public void showOrientation(@NonNull PlaceOrientation orientation) {
        mShownOrientation = orientation;
    }

    /**
     * The phone turned mid-session: the session goes with it (rotate to edit, editor-redesign
     * §1). The editor is the live place, so every write lands on the orientation on screen.
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
        // A bar the page does not wear stands nowhere on it: Home's status bar and extra keys.
        if (!dress().wears(bar.element()))
            return Drop.NONE;
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

    /**
     * Whether anything has moved since the editor opened, in any layout the session has reached
     * — the unsaved-changes question.
     */
    public boolean isDirty() {
        if (pageFlagsMoved())
            return true;
        for (Map.Entry<LayoutVariant, String> entry : mEntrySignatures.entrySet()) {
            if (!entry.getValue().equals(
                    PlaceArrangeSnapshot.capture(mRoot, entry.getKey()).signature()))
                return true;
        }
        return false;
    }

    /**
     * Puts every layout back the way the editor found it, and Home's own-layout switch; a layout
     * nothing moved is left unwritten.
     */
    public void revert() {
        boolean switchMoved = pageFlagsMoved();
        for (Map.Entry<Integer, Boolean> entry : mEntryPageLayouts.entrySet()) {
            int id = entry.getKey();
            if (mRoot.hasPageLayout(id) != entry.getValue())
                mRoot.setPageLayout(id, mEntryPageKinds.get(id), entry.getValue());
        }
        for (Map.Entry<LayoutVariant, PlaceArrangeSnapshot> entry : mEntries.entrySet()) {
            String atOpen = mEntrySignatures.get(entry.getKey());
            if (atOpen == null || !atOpen.equals(
                    PlaceArrangeSnapshot.capture(mRoot, entry.getKey()).signature()))
                entry.getValue().restore(mRoot);
        }
        // The edited layout only changes if Home's switch did; minimal mode toggled under the
        // open editor does not move what it edits.
        if (switchMoved) {
            mVariant = dress().layout();
            mPlaces = mRoot.forVariant(mVariant);
        }
    }

    // ------------------------------------------------------------------------- Dock card

    /**
     * The shown orientation's arrangement as this layout stores it, before the page's dress: what
     * a drop is written against ({@link LayoutChooserModel#applyDrop}).
     */
    @NonNull
    public PlaceLayout arrangedLayout() {
        return mPlaces.resolve(mShownOrientation);
    }

    /**
     * Every slot of {@code next} that differs from {@link #arrangedLayout}, written for the shown
     * orientation: the Dock card's Position rows and its restacking. The page's dress decides
     * what it may write, as for a drop.
     */
    @NonNull
    public Drop writeSlots(@NonNull PlaceLayout next) {
        PlaceLayout now = arrangedLayout();
        boolean wrote = false;
        for (Element element : Element.values()) {
            if (!dress().wears(element)) continue;
            Slot slot = next.slot(element);
            if (slot.equals(now.slot(element))) continue;
            mPlaces.setSlot(mShownOrientation, element, slot);
            wrote = true;
        }
        if (!wrote) return Drop.NONE;
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** The Dock card's Bars list for the shown orientation, or null where it never was stored. */
    @Nullable
    public String barOrder() {
        return mPlaces.barOrder(mShownOrientation);
    }

    public void setBarOrder(@NonNull String order) {
        mPlaces.setBarOrder(mShownOrientation, order);
    }
}
