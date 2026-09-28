package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.PlaceArrangeModel;
import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.List;

/**
 * One Layout editor session, as decisions rather than views: which orientation the miniature is
 * showing, which one a drop writes, whether the live place behind the editor follows it, whether
 * anything has moved since the editor opened, and how to put it all back.
 *
 * <p>The miniature draws either orientation whatever the phone is in, so "shown" and "the phone's"
 * are two different questions. A drop always writes the shown one — that is the orientation the
 * user is looking at a picture of — and the place behind the editor can only follow while the two
 * agree. Editing the other orientation therefore changes the miniature and nothing else until the
 * phone is turned.
 *
 * <p>The rows beneath the miniature are the same question asked of the same store: what the layout
 * offers that no bar can be dragged into — how the keyboard stands, how many cells the widget grid
 * has, and how tall the dock and the keyboard stand. They come from {@link PlaceArrangeModel},
 * which already answers for one orientation at a time, so the toggle moves the rows exactly as it
 * moves the picture.
 *
 * <p>The layout is every place's (ADR 0003), so there is no place to pick and every write lands
 * everywhere. The session still knows which place it was opened over, because that is what the
 * miniature draws — Home's grid, the terminal's text, the display's screen — and what decides the
 * rows worth offering: the grid only on Home, the keyboard's mode only over the display.
 *
 * <p>Pure: a store in, an answer out, no views, so every case above is testable without a window.
 * {@link LayoutEditorController} is the shell that draws it.
 */
public final class LayoutEditorPlan {

    /**
     * How much of the screen's height the portrait miniature's phone frame stands in. It is the
     * same at rest and pulled up: the pull shows more of the list the picture stands in, it does
     * not reshape the picture.
     */
    public static final float PORTRAIT_FRAME_SCREEN_FRACTION = 0.42f;

    /**
     * How much of a portrait screen the card stands in at rest. The editor is a sheet from the
     * bottom edge rather than a panel over the whole screen: what is left above it is the live
     * place it is a picture of, still showing its status strip and its chip row, so a drop can be
     * seen landing on the real thing rather than only on the miniature. The sheet can then be
     * pulled up to {@link #expandedCardBudgetPx}, which is as far as the top inset allows.
     *
     * <p>A landscape screen has no height to give away — its card is already the short edge — so
     * the rule is the portrait screen's alone, and {@link #cardBudgetPx(int, int)} says which it
     * is.
     */
    public static final float PORTRAIT_CARD_SCREEN_FRACTION = 0.80f;

    /**
     * The headings the rows stand under, in the order they stand in, and whether that element's
     * own choices stand there too. Everything else about a place's arrangement is a bar, and a bar
     * is moved on the picture rather than picked from a row — which is why the Dock heading carries
     * nothing but its height: where the dock stands is a drag on the miniature.
     */
    private enum Section {
        DOCK(Element.PINNED_APPS, false),
        // On, Minimised to a pull tab, or Off: the tab has no edge band to drag on the picture,
        // so the three-way choice is a row, with the edge beside it.
        AZ_INDEX(Element.AZ_INDEX, true),
        KEYBOARD(Element.KEYBOARD, true),
        WIDGET_GRID(Element.WIDGET_GRID, true);

        @NonNull final Element element;
        final boolean offersChoices;

        Section(@NonNull Element element, boolean offersChoices) {
            this.element = element;
            this.offersChoices = offersChoices;
        }
    }

    /** What one drop on the miniature did. */
    public enum Drop {
        /** The bar cannot stand there: nothing was written and nothing moved. */
        NONE,
        /** Written for an orientation the phone is not in: the miniature moves, the place does not. */
        MINIATURE,
        /** Written for the orientation on screen: the live place follows it. */
        LIVE
    }

    /**
     * One row beneath the miniature. The element and the place it takes among that element's own
     * groups are what the row is; the group is only what it says right now, so a row that has just
     * been picked on — or that a rotation has moved — is re-read through {@link #row} rather than
     * trusted.
     */
    public static final class Row {
        @NonNull public final Element element;
        public final int index;
        @NonNull public final PlaceArrangeModel.Group group;

        Row(@NonNull Element element, int index, @NonNull PlaceArrangeModel.Group group) {
            this.element = element;
            this.index = index;
            this.group = group;
        }
    }

    @NonNull private final PlaceLayoutStore mPlaces;
    /** The place the miniature draws and the live place behind the card; never a write target. */
    @NonNull private PaneWallPage mPlace;
    /** The arrangement as the editor found it; what Discard and ↺ put back. */
    @NonNull private final PlaceArrangeSnapshot mEntry;
    @NonNull private final String mEntrySignature;
    @NonNull private PlaceOrientation mDeviceOrientation;
    @NonNull private PlaceOrientation mShownOrientation;

    private LayoutEditorPlan(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                             @NonNull PlaceOrientation deviceOrientation) {
        mPlaces = places;
        mPlace = place;
        mDeviceOrientation = deviceOrientation;
        // The editor opens on what the user is looking at; the toggle is how the other one is asked
        // for.
        mShownOrientation = deviceOrientation;
        mEntry = PlaceArrangeSnapshot.capture(places);
        mEntrySignature = mEntry.signature();
    }

    /** Opens a session over one place, showing the orientation the phone is in. */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation) {
        return new LayoutEditorPlan(places, place, deviceOrientation);
    }

    @NonNull
    public PaneWallPage place() {
        return mPlace;
    }

    /** The orientation on the miniature, which is also the one every drop writes. */
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
     * Whether a pick on this row lands on the live place: any row while the miniature shows the
     * phone's orientation, and a row shared by both orientations — the keyboard's on/off switch
     * — whichever one it shows.
     */
    public boolean follows(@NonNull Row row) {
        return liveFollows() || row.group.sharedByOrientations;
    }

    /** The arrangement the miniature draws: the shown orientation's, resolved. */
    @NonNull
    public PlaceLayout shownLayout() {
        return mPlaces.resolve(mShownOrientation);
    }

    /**
     * The rows beneath the miniature: the dock's height, the keyboard, and on Home the grid, for
     * the orientation on the toggle. A pick or a drag writes through the group's own writer,
     * the same way a drop writes through the picture.
     */
    @NonNull
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>(8);
        for (Section section : Section.values()) {
            List<PlaceArrangeModel.Group> groups = groupsOf(section);
            for (int index = 0; index < groups.size(); index++)
                rows.add(new Row(section.element, index, groups.get(index)));
        }
        return rows;
    }

    /** One row's answer, read fresh, or null where the place no longer offers it. */
    @Nullable
    public PlaceArrangeModel.Group row(@NonNull Element element, int index) {
        for (Section section : Section.values()) {
            if (section.element != element)
                continue;
            List<PlaceArrangeModel.Group> groups = groupsOf(section);
            return index < 0 || index >= groups.size() ? null : groups.get(index);
        }
        return null;
    }

    /** Everything under one heading: what that element offers, then how big it stands. */
    @NonNull
    private List<PlaceArrangeModel.Group> groupsOf(@NonNull Section section) {
        List<PlaceArrangeModel.Group> groups = new ArrayList<>(4);
        if (section.offersChoices)
            groups.addAll(
                PlaceArrangeModel.groups(mPlaces, mPlace, mShownOrientation, section.element));
        groups.addAll(PlaceArrangeModel.sizes(mPlaces, mPlace, mShownOrientation, section.element));
        return groups;
    }

    /**
     * The place on the miniature. A second door opened while the editor is up moves it rather than
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
     * The phone turned mid-session. The miniature goes with it: the editor is a picture of the
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
        if (!LayoutChooserModel.applyDrop(mPlaces, mShownOrientation, bar, edge, index))
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

    /**
     * How tall the miniature has to be for its phone frame to stand at the size this orientation
     * asks for: {@value #PORTRAIT_FRAME_SCREEN_FRACTION} of the screen's height in portrait, and
     * as wide as the card's column in landscape, where a frame sized from the height would be a
     * sliver.
     *
     * <p>Either frame is bounded twice. By the column's width: a frame wider than the card it
     * stands in is drawn at the width it has, and the height it asked for beyond that is dead air
     * above and below it. And by what the resting card has left once its chrome and a peek of the
     * rows have theirs: a landscape phone is about as wide as the screen is tall, so a frame sized
     * from the width alone would push every row below the fold; the portrait frame only meets that
     * bound at a large font scale, where the chrome around it has grown, and there the picture
     * gives way rather than the rows.
     *
     * @param frameAspect  the frame's width over its height, for the orientation asked about
     * @param reservedPx   the room the miniature keeps under the frame for the hide tray
     * @param columnPx     the width of the card's body, which the frame can be as wide as
     * @param roomBelowPx  the room that has to stay for what stands around the canvas — the card's
     *     own chrome plus the peek of the rows under it
     * @param restPx       the card's resting height
     */
    public static int miniatureHeightPx(@NonNull PlaceOrientation orientation, int screenHeightPx,
                                        float frameAspect, int reservedPx, int columnPx,
                                        int roomBelowPx, int restPx) {
        float aspect = Math.max(frameAspect, 0.01f);
        float frameHeight = orientation == PlaceOrientation.LANDSCAPE
            ? Math.max(0, columnPx) / aspect
            : Math.min(PORTRAIT_FRAME_SCREEN_FRACTION * screenHeightPx,
                Math.max(0, columnPx) / aspect);
        int roomForFrame = Math.max(0, restPx - roomBelowPx - reservedPx);
        return Math.round(Math.min(frameHeight, roomForFrame)) + reservedPx;
    }

    /**
     * How much height the card asks for at rest: the whole screen where the screen is already
     * short, and {@value #PORTRAIT_CARD_SCREEN_FRACTION} of it on a portrait screen, where the
     * rest is the live place above the sheet.
     *
     * <p>The screen's own shape decides it, not the orientation on the toggle: flipping the
     * miniature to the other orientation changes the picture, and the card it stands in is still
     * the one this screen has room for.
     */
    public static int cardBudgetPx(int screenWidthPx, int screenHeightPx) {
        return screenWidthPx >= screenHeightPx ? screenHeightPx
            : Math.round(PORTRAIT_CARD_SCREEN_FRACTION * screenHeightPx);
    }

    /**
     * How much height the card may take pulled all the way up: everything the host has under the
     * top inset, less the air the sheet keeps at the top and its own bottom margin, which is
     * {@code roomPx}.
     */
    public static int expandedCardBudgetPx(int roomPx) {
        return Math.max(0, roomPx);
    }

    /**
     * The card's resting height: {@link #cardBudgetPx(int, int)}, and never more than the room it
     * could be pulled up to. On a landscape screen the two meet, so the card already stands as
     * tall as it can and a pull has no travel.
     */
    public static int restingCardHeightPx(int screenWidthPx, int screenHeightPx,
                                          int expandedPx) {
        int resting = cardBudgetPx(screenWidthPx, screenHeightPx);
        return expandedPx > 0 ? Math.min(resting, expandedPx) : resting;
    }

    /**
     * The width the miniature's frame asks for of its own accord, which is what decides whether
     * the Layout editor's body can put the rows beside it rather than under it.
     *
     * <p>A landscape frame is as wide as the screen, so it never leaves a pane for the rows and
     * the body falls back to one column — which is the case P1 bounds. A portrait frame is a
     * sliver, and beside it there is room for everything.
     */
    public static int miniatureNaturalWidthPx(@NonNull PlaceOrientation orientation,
                                              int screenWidthPx, int screenHeightPx,
                                              float frameAspect) {
        if (orientation == PlaceOrientation.LANDSCAPE)
            return screenWidthPx;
        return Math.round(PORTRAIT_FRAME_SCREEN_FRACTION * screenHeightPx
            * Math.max(frameAspect, 0.01f));
    }

    /**
     * How tall the miniature stands in a pane of its own, where there are no rows beneath it to
     * leave room for and the frame is bounded by the pane rather than by a fraction of the screen.
     *
     * <p>The frame takes the pane's width or the pane's height, whichever runs out first, so a
     * miniature marooned between two empty gutters becomes a miniature that fills its column.
     */
    public static int miniatureHeightInPanePx(float frameAspect, int reservedPx, int paneWidthPx,
                                              int paneHeightPx) {
        float fromWidth = Math.max(0, paneWidthPx) / Math.max(frameAspect, 0.01f);
        int room = Math.max(0, paneHeightPx - reservedPx);
        return Math.round(Math.min(fromWidth, room)) + reservedPx;
    }
}
