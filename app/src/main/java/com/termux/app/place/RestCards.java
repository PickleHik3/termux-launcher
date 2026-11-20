package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.chrome.LiveChromeShape;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockHostBounds;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockPlacement;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.UnderKeyboardBand;
import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Corners;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.wall.PageRef;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The glass cards one rest draws, as one pure answer (ADR 0032): where each card stands on the
 * screen, its corners, which of its edges are a rim, a join or the screen's, and which pieces it
 * carries. A rest is one page in front, still, with its keyboard up or down. Nothing reads this
 * yet; it is the answer the activity's dress passes will read in place of each deriving its own.
 *
 * <p>It works the stack out the way the activity lays it out: {@link StackLayout} for the heights,
 * {@link BottomInsets} for the container's margin, {@link StackLanes} for the band the container
 * is laid out taller by, then the views the stack's relative layout puts where they stand (the
 * container on the screen's bottom, the keyboard's column on the container's bottom, the glass
 * host pinned to the container's top and above that column, or to the container's bottom while
 * the dock's glass and the Docked keyboard are one surface). A view is drawn where it is laid out
 * moved by the rest's translation: the container sinks by the keyboard's height while the
 * keyboard is down, and the bands under the keyboard are held back up by the same share.
 *
 * <p>Docked and Floating stay two formulas: a Floating card is the host inset by Margin with
 * Corners clamped to half its height and a rim on every edge; a Docked one is the slice of the
 * shape model's frame the host stands on. They are never one formula.
 *
 * <p>Numbers this class models rather than reads from a view:
 * <ul>
 *   <li>The Peek card's icons: one box per pinned slot, {@code slotIconPx} square, its top at
 *       the apps band's top plus {@code appsTopPaddingPx}. Only the centre line is read.</li>
 *   <li>The apps band's top: the rows stand on the keyboard column's top, each band at its
 *       {@link DockLayout} height (apps: its bar, Search field, Wall strip and page ticks).</li>
 *   <li>The Docked shape model's thicknesses: the bands' heights above, the status bar's
 *       the top status bar's thickness (a plain input on {@link Rest}), the keyboard's laid-out height.</li>
 *   <li>The lane slack and the flush padding: slack is read from the two layouts' rests; flush
 *       padding is whatever the pass carries (the fixtures stand none).</li>
 * </ul>
 *
 * <p>Not cards here: the floating-form keyboard (its rest rectangle is the activity's) and the
 * foot under the gesture pill (it needs the system inset, which a pass does not carry).
 */
public final class RestCards {

    public enum CardId { STATUS, DOCK, PEEK, UNDER, KEYBOARD }

    /** The views that carry cards or are laid out around them. */
    public enum StackView { CONTAINER, HOST, KEYBOARD_COLUMN, UNDER, STATUS_BAR }

    /** One rest, as the activity's geometry pass fills it. */
    public static final class Rest {
        @NonNull final StackLayout.Input pass;
        @NonNull final PageRef page;
        final int screenWidthPx;
        final int screenHeightPx;
        final float density;
        final int measuredExtraKeysRowPx;
        final int measuredKeyboardPx;
        /** The top status bar's thickness, Docked and Floating, as the status bar's geometry sizes it. */
        final int statusThicknessDockedPx;
        final int statusThicknessFloatingPx;
        /** The keyboard wears the dock's glass material, so Docked it is one surface with it. */
        public boolean keyboardGlassShared = true;
        /** A bottom status bar is a band of the dock's plank, not a bar with a sheet of its own. */
        public boolean statusOnDockPlank = false;

        /**
         * @param measuredKeyboardPx the keyboard as the stack lays it out while it is down: 0 for
         *                           a floating-form keyboard, which its own frame lays out
         */
        public Rest(@NonNull StackLayout.Input pass, @NonNull PageRef page, int screenWidthPx,
                    int screenHeightPx, float density, int measuredExtraKeysRowPx,
                    int measuredKeyboardPx, int statusThicknessDockedPx,
                    int statusThicknessFloatingPx) {
            this.pass = pass;
            this.page = page;
            this.screenWidthPx = screenWidthPx;
            this.screenHeightPx = screenHeightPx;
            this.density = density;
            this.measuredExtraKeysRowPx = measuredExtraKeysRowPx;
            this.measuredKeyboardPx = measuredKeyboardPx;
            this.statusThicknessDockedPx = statusThicknessDockedPx;
            this.statusThicknessFloatingPx = statusThicknessFloatingPx;
        }

        int statusThicknessPx(boolean floating) {
            return floating ? statusThicknessFloatingPx : statusThicknessDockedPx;
        }
    }

    /** One glass shape a rest draws, where it is drawn at rest. */
    public static final class Card {
        @NonNull public final CardId id;
        /** The view that carries it. */
        @NonNull public final StackView view;
        /** Screen px. */
        @NonNull public final Box box;
        @NonNull public final Corners corners;
        @NonNull public final List<PieceId> pieces;
        @NonNull private final EdgeKind[] mEdges;

        Card(@NonNull CardId id, @NonNull StackView view, @NonNull Box box,
             @NonNull Corners corners, @NonNull EdgeKind top, @NonNull EdgeKind bottom,
             @NonNull EdgeKind left, @NonNull EdgeKind right, @NonNull List<PieceId> pieces) {
            this.id = id;
            this.view = view;
            this.box = box;
            this.corners = corners;
            this.pieces = Collections.unmodifiableList(new ArrayList<>(pieces));
            mEdges = new EdgeKind[Edge.values().length];
            mEdges[Edge.TOP.ordinal()] = top;
            mEdges[Edge.BOTTOM.ordinal()] = bottom;
            mEdges[Edge.LEFT.ordinal()] = left;
            mEdges[Edge.RIGHT.ordinal()] = right;
        }

        @NonNull
        public EdgeKind edge(@NonNull Edge edge) {
            return mEdges[edge.ordinal()];
        }

        @NonNull @Override public String toString() {
            return id + "@" + box + " " + corners;
        }
    }

    @NonNull private final PageRef mPage;
    @NonNull private final List<Card> mCards = new ArrayList<>(4);
    @NonNull private final Box[] mLaidOut = new Box[StackView.values().length];

    /** The rest's answer. */
    @NonNull
    public static RestCards of(@NonNull Rest rest) {
        return new RestCards(rest);
    }

    /** The cards in a stable order: STATUS, DOCK or PEEK, UNDER, KEYBOARD. */
    @NonNull
    public List<Card> cards() {
        return Collections.unmodifiableList(mCards);
    }

    @Nullable
    public Card card(@NonNull CardId id) {
        for (Card card : mCards) if (card.id == id) return card;
        return null;
    }

    /** The view's laid-out rectangle in screen px, null when it is not laid out. */
    @Nullable
    public Box laidOut(@NonNull StackView view) {
        return mLaidOut[view.ordinal()];
    }

    @NonNull
    public PageRef page() {
        return mPage;
    }

    // ---- The answer ------------------------------------------------------------------------

    private RestCards(@NonNull Rest rest) {
        final StackLayout.Input pass = rest.pass;
        mPage = rest.page;
        final DockGeometry.Half half = pass.dock.rowsHalf();
        // The layout the stack stands, as StackLayout reads it for the half whose rows stand.
        final PlaceLayout layout = pass.dock.arrangement;
        final boolean floating = half.floating;
        final boolean up = pass.keyboardShown;
        final int w = rest.screenWidthPx;
        final int h = rest.screenHeightPx;
        final float density = rest.density;
        final StackLayout.Result r = StackLayout.layout(pass, new DockGeometry());
        final DockLayout dock = r.dock;
        final float cornersPx = cornersPx(half, density);

        // The container: on the screen's bottom, as tall as the stack, the keyboard laid out
        // while it is down, and the band both sides' rests need.
        final BottomInsets insets = BottomInsets.forKept(0, 0, false, r.bottomMarginPx,
            r.flushBottomInsetPx);
        final int kbDown = up ? 0
            : BottomInsets.keyboardKeysPx(rest.measuredKeyboardPx, insets.keyboardFootPx);
        final int slack = laneSlackPx(rest);
        final int containerH = StackScene.containerHeightPx(
            r.stackHeightPx + insets.containerExtraPx(), kbDown, slack);
        final float containerBottom = h - insets.stackMarginPx;
        final float containerTop = containerBottom - containerH;
        final boolean containerLaidOut = r.stackShown || kbDown > 0;
        if (containerLaidOut)
            mLaidOut[StackView.CONTAINER.ordinal()] = new Box(0f, containerTop, w, containerBottom);
        // The container is drawn lower by the keyboard's share while the keyboard is down.
        final float shift = StackScene.keyboardTranslationPx(0f, kbDown);

        // The keyboard's column on the container's bottom, with the bands under the keyboard.
        final int keyboardLaid = up ? pass.keyboardHeightPx : kbDown;
        final List<Element> under = EdgeStackPolicy.underKeyboard(layout);
        int underRows = 0;
        if (r.bandsUnderKeyboard) for (Element e : under) underRows += bandPx(e, dock, pass);
        final int underBlock = r.bandsUnderKeyboard
            ? r.underKeyboardTopMarginPx + underRows + r.underKeyboardBottomMarginPx : 0;
        final float columnBottom = containerBottom - insets.stackPaddingPx
            - insets.keyboardColumnMarginPx;
        final float columnTop = columnBottom - (keyboardLaid + underBlock);
        if (containerLaidOut && keyboardLaid + underBlock > 0)
            mLaidOut[StackView.KEYBOARD_COLUMN.ordinal()] = new Box(0f, columnTop, w, columnBottom);

        // The glass host: pinned to the container's top and above the keyboard's column, or to
        // the container's bottom while it is one surface with the Docked keyboard.
        final boolean peekStands = DockPlacement.peekCardStandsOn(pass.dock.rows.kind,
            DockSurfaceSpec.forStyle(half.design).peek, pass.toolbarShown, layout, false, up,
            pass.imeLiftPx > 0, dock.promptFieldBandPx > 0);
        final boolean floats = DockPlacement.floats(floating, floating && peekStands);
        final int sideMargin = DockHostBounds.sideMarginPx(floats, dock.horizontalInsetPx);
        final float hostTop = containerTop + DockHostBounds.topInsetPx(
            rest.statusOnDockPlank ? 0 : r.statusBandPx, dock.slabTopInsetPx, slack);
        final boolean unified = !r.bandsUnderKeyboard
            && ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(pass.toolbarShown, up,
                floating, rest.keyboardGlassShared);
        final float hostBottom = unified ? containerBottom : columnTop;
        final boolean hostLaidOut = containerLaidOut && pass.toolbarShown && hostBottom > hostTop;
        if (hostLaidOut)
            mLaidOut[StackView.HOST.ordinal()] =
                new Box(sideMargin, hostTop, w - sideMargin, hostBottom);

        // The bands under the keyboard stand on the column's bottom, above their own air.
        final float underBottom = columnBottom - r.underKeyboardBottomMarginPx;
        final float underTop = underBottom - underRows;
        final boolean underLaidOut = containerLaidOut && r.bandsUnderKeyboard
            && pass.toolbarShown;
        if (underLaidOut)
            mLaidOut[StackView.UNDER.ordinal()] = new Box(0f, underTop, w, underBottom);

        final LayoutStyle style = floating ? LayoutStyle.FLOATING : LayoutStyle.DOCKED;
        final boolean statusOnTop = EdgeStackPolicy.stack(layout, Edge.TOP)
            .contains(Element.STATUS);
        final boolean wantsShape = statusOnTop || hostLaidOut && !peekStands && !floats;
        final ChromeShape shape = wantsShape ? shapeOf(rest, r, dock, half, layout, style,
            cornersPx) : null;

        // STATUS: the bar along the top edge.
        if (statusOnTop) addStatus(rest, shape, floating, w, h, density);
        // DOCK or PEEK: the host's glass.
        if (hostLaidOut) {
            if (peekStands) {
                addPeek(rest, layout, dock, hostTop, hostBottom, columnTop, w, h, shift, density);
            } else if (floats) {
                addFloatingDock(layout, pass, rest, dock, hostTop, hostBottom, sideMargin, w,
                    shift, cornersPx, unified);
            } else {
                addDockedDock(layout, pass, rest, shape, hostTop, hostBottom, w, shift, unified);
            }
        }
        // UNDER: the bands under the keyboard wear the dock's sheet unless the Peek card does.
        if (underLaidOut && UnderKeyboardBand.wearsSheet(r.bandsUnderKeyboard, pass.toolbarShown,
                peekStands)) {
            addUnder(under, dock, underTop, underBottom, floating, w);
        }
        // KEYBOARD: the docked-form keyboard, unless the dock's glass carries it.
        if (containerLaidOut && up && keyboardLaid > 0
                && layout.keyboardForm != KeyboardForm.FLOATING && !unified) {
            addKeyboard(r, dock, columnTop, keyboardLaid, floating, w, cornersPx, density);
        }
    }

    // ---- Cards -----------------------------------------------------------------------------

    private void addStatus(@NonNull Rest rest, @Nullable ChromeShape shape, boolean floating,
                           int w, int h, float density) {
        ChromeShape.Piece piece = shape == null ? null : shape.piece(PieceId.STATUS);
        if (piece == null) return;
        // A top row spans the width and takes its thickness off the screen's top.
        Box box = new Box(0, 0, Math.max(0, w),
            Math.min(Math.max(0, h), Math.max(0, rest.statusThicknessPx(floating))));
        mLaidOut[StackView.STATUS_BAR.ordinal()] = box;
        mCards.add(new Card(CardId.STATUS, StackView.STATUS_BAR, box, piece.corners,
            piece.kind(Edge.TOP), piece.kind(Edge.BOTTOM), piece.kind(Edge.LEFT),
            piece.kind(Edge.RIGHT), Collections.singletonList(PieceId.STATUS)));
    }

    private void addFloatingDock(@NonNull PlaceLayout layout, @NonNull StackLayout.Input pass,
                                 @NonNull Rest rest, @NonNull DockLayout dock, float hostTop,
                                 float hostBottom, int sideMargin, int w, float shift,
                                 float cornersPx, boolean unified) {
        Box box = new Box(sideMargin, hostTop + shift, w - sideMargin, hostBottom + shift);
        float radius = Math.min(cornersPx, box.height() / 2f);
        mCards.add(new Card(CardId.DOCK, StackView.HOST, box,
            new Corners(radius, radius, radius, radius), EdgeKind.RIM, EdgeKind.RIM,
            EdgeKind.RIM, EdgeKind.RIM, dockPieces(layout, rest, unified)));
    }

    private void addDockedDock(@NonNull PlaceLayout layout, @NonNull StackLayout.Input pass,
                               @NonNull Rest rest, @Nullable ChromeShape shape, float hostTop,
                               float hostBottom, int w, float shift, boolean unified) {
        List<PieceId> ids = dockPieces(layout, rest, unified);
        Box box = new Box(0f, hostTop + shift, w, hostBottom + shift);
        LiveChromeShape.Clip clip = shape == null ? null : LiveChromeShape.outlineOf(shape, ids);
        Corners corners = clip == null ? Corners.SQUARE : new Corners(clip.topLeft,
            clip.topRight, clip.bottomRight, clip.bottomLeft);
        mCards.add(new Card(CardId.DOCK, StackView.HOST, box, corners,
            outerKind(shape, ids, Edge.TOP), outerKind(shape, ids, Edge.BOTTOM),
            outerKind(shape, ids, Edge.LEFT), outerKind(shape, ids, Edge.RIGHT), ids));
    }

    /**
     * The Peek card: cut at the apps row's icon line, inset from the screen's sides, running on
     * past the screen's bottom (this box ends at it). {@code rowsBottom} is where the rows stand:
     * the keyboard column's top, even while the host runs on over the keyboard.
     */
    private void addPeek(@NonNull Rest rest, @NonNull PlaceLayout layout, @NonNull DockLayout dock,
                         float hostTop, float hostBottom, float rowsBottom, int w, int h,
                         float shift, float density) {
        // The apps band's top: bands at and below the apps row, up from where the rows stand.
        List<Element> over = EdgeStackPolicy.overKeyboard(layout);
        float below = 0f;
        float appsBand = 0f;
        boolean found = false;
        for (Element e : over) {
            int band = bandPx(e, dock, rest.pass);
            if (e == Element.APPS) {
                appsBand = band;
                found = true;
                break;
            }
            below += band;
        }
        if (!found) {
            // The apps row stands under the keyboard: its bands stand on the sheet's bottom.
            List<Element> underBands = EdgeStackPolicy.underKeyboard(layout);
            rowsBottom = mLaidOut[StackView.UNDER.ordinal()] != null
                ? mLaidOut[StackView.UNDER.ordinal()].bottom : rowsBottom;
            below = 0f;
            for (Element e : underBands) {
                int band = bandPx(e, dock, rest.pass);
                if (e == Element.APPS) {
                    appsBand = band;
                    break;
                }
                below += band;
            }
        }
        float appsTop = rowsBottom - below - appsBand;
        int slots = Math.max(1, rest.pass.dock.pinnedSlotCount);
        float[] boxes = new float[slots * 4];
        float iconTop = appsTop - hostTop + dock.appsTopPaddingPx;
        float iconSide = Math.max(1, dock.slotIconPx);
        for (int i = 0; i < slots; i++) {
            boxes[i * 4] = i * iconSide;
            boxes[i * 4 + 1] = iconTop;
            boxes[i * 4 + 2] = (i + 1) * iconSide;
            boxes[i * 4 + 3] = iconTop + iconSide;
        }
        float hostTopDrawn = hostTop + shift;
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(boxes, slots, 0f, w,
            Math.round(hostBottom - hostTop), w, PeekGlassGeometry.screenBottomInHost(h,
                hostTop, shift), density);
        if (card == null) return;
        Box box = new Box(card.left, hostTopDrawn + card.top, card.right, h);
        List<PieceId> pieces = new ArrayList<>(2);
        pieces.add(PieceId.APPS);
        if (DockPlacement.peekCardCarriesAz(layout, false)) pieces.add(PieceId.AZ);
        mCards.add(new Card(CardId.PEEK, StackView.HOST, box,
            new Corners(card.radiusPx, card.radiusPx, 0f, 0f), EdgeKind.RIM, EdgeKind.SCREEN,
            EdgeKind.RIM, EdgeKind.RIM, pieces));
    }

    private void addUnder(@NonNull List<Element> under, @NonNull DockLayout dock, float top,
                          float bottom, boolean floating, int w) {
        // The card is inset by the dock's side inset inside its view, as the view's sheet is.
        int inset = UnderKeyboardBand.sideInsetPx(dock);
        Box box = new Box(inset, top, w - inset, bottom);
        float radius = UnderKeyboardBand.cornerRadiusPx(dock, Math.round(bottom - top));
        List<PieceId> pieces = new ArrayList<>(under.size());
        for (Element e : under) pieces.add(PieceId.of(e));
        EdgeKind side = floating ? EdgeKind.RIM : EdgeKind.SCREEN;
        mCards.add(new Card(CardId.UNDER, StackView.UNDER, box,
            new Corners(radius, radius, radius, radius), floating ? EdgeKind.RIM : EdgeKind.JOIN,
            side, side, side, pieces));
    }

    private void addKeyboard(@NonNull StackLayout.Result r, @NonNull DockLayout dock,
                             float columnTop, int keyboardPx, boolean floating, int w,
                             float cornersPx, float density) {
        List<PieceId> ids = Collections.singletonList(PieceId.KEYBOARD);
        Box column = new Box(0f, columnTop, w, columnTop + keyboardPx);
        if (floating) {
            // The Floating keyboard is a capsule in its column: Margin in from the sides, the
            // gap above it, the user's chin (none here) under it.
            int margin = dock.horizontalInsetPx;
            Box box = new Box(margin, columnTop + UnderKeyboardBand.keyboardGapPx(density),
                w - margin, columnTop + keyboardPx);
            float radius = Math.min(cornersPx, box.height() / 2f);
            mCards.add(new Card(CardId.KEYBOARD, StackView.KEYBOARD_COLUMN, box,
                new Corners(radius, radius, radius, radius), EdgeKind.RIM, EdgeKind.RIM,
                EdgeKind.RIM, EdgeKind.RIM, ids));
        } else {
            EdgeKind bottom = r.bandsUnderKeyboard ? EdgeKind.JOIN : EdgeKind.SCREEN;
            mCards.add(new Card(CardId.KEYBOARD, StackView.KEYBOARD_COLUMN, column,
                Corners.SQUARE, EdgeKind.JOIN, bottom, EdgeKind.SCREEN, EdgeKind.SCREEN, ids));
        }
    }

    // ---- Pieces and the shape model --------------------------------------------------------

    /** The dock pieces the host carries: the rows over the keyboard, and the keyboard if unified. */
    @NonNull
    private static List<PieceId> dockPieces(@NonNull PlaceLayout layout, @NonNull Rest rest,
                                            boolean unified) {
        List<PieceId> ids = new ArrayList<>(4);
        for (Element e : EdgeStackPolicy.overKeyboard(layout)) {
            if (e == Element.STATUS && !rest.statusOnDockPlank) continue;
            ids.add(PieceId.of(e));
        }
        if (unified) ids.add(PieceId.KEYBOARD);
        return ids;
    }

    /**
     * The edge kind the outermost of {@code ids} gives {@code edge} (what
     * {@link LiveChromeShape#rimEdges} reads for the rim, kept whole here), JOIN with no piece.
     */
    @NonNull
    private static EdgeKind outerKind(@Nullable ChromeShape shape, @NonNull List<PieceId> ids,
                                      @NonNull Edge edge) {
        if (shape == null) return EdgeKind.JOIN;
        ChromeShape.Piece outer = null;
        for (PieceId id : ids) {
            ChromeShape.Piece piece = shape.piece(id);
            if (piece == null) continue;
            if (outer == null || isOuter(piece, outer, edge)) outer = piece;
        }
        return outer == null ? EdgeKind.JOIN : outer.kind(edge);
    }

    private static boolean isOuter(@NonNull ChromeShape.Piece piece,
                                   @NonNull ChromeShape.Piece than, @NonNull Edge edge) {
        switch (edge) {
            case TOP: return piece.box.top < than.box.top;
            case BOTTOM: return piece.box.bottom > than.box.bottom;
            case LEFT: return piece.box.left < than.box.left;
            case RIGHT:
            default: return piece.box.right > than.box.right;
        }
    }

    @NonNull
    private static ChromeShape shapeOf(@NonNull Rest rest, @NonNull StackLayout.Result r,
                                       @NonNull DockLayout dock, @NonNull DockGeometry.Half half,
                                       @NonNull PlaceLayout layout, @NonNull LayoutStyle style,
                                       float cornersPx) {
        StackLayout.Input pass = rest.pass;
        boolean floating = style == LayoutStyle.FLOATING;
        float status = rest.statusThicknessPx(floating);
        float apps = bandPx(Element.APPS, dock, pass);
        float az = bandPx(Element.AZ, dock, pass);
        float keys = bandPx(Element.EXTRA_KEYS, dock, pass);
        float keyboard = pass.keyboardShown ? pass.keyboardHeightPx : 0f;
        ChromeShapeModel.Thickness thickness = ChromeShapeModel.Thickness.of(status, status,
            apps, apps, az, az, keys, keys, keyboard);
        return ChromeShapeModel.shape(ChromeShapeModel.input(
                layout.withKeyboardShown(pass.keyboardShown), style, rest.screenWidthPx,
                rest.screenHeightPx, thickness)
            .corners(cornersPx).margin(marginPx(half, rest.density)).build());
    }

    // ---- Numbers ---------------------------------------------------------------------------

    /** The band an element stands in on the dock: its row, with what the Style adds to it. */
    static int bandPx(@NonNull Element element, @NonNull DockLayout dock,
                      @NonNull StackLayout.Input pass) {
        switch (element) {
            case APPS:
                return dock.appsBarHeightPx > 0 ? dock.appsBarHeightPx + dock.promptFieldBandPx
                    + dock.wallStripBandPx + dock.indicatorBandHeightPx : 0;
            case AZ:
                return dock.azRowHeightPx > 0 ? dock.azRowHeightPx : 0;
            case EXTRA_KEYS:
                return pass.extraKeysRowEnabled ? pass.toolbarHeightPx : 0;
            default:
                return 0;
        }
    }

    /** Corners in px, as the activity's {@code layoutCornersPx} reads it (not rounded). */
    static float cornersPx(@NonNull DockGeometry.Half half, float density) {
        int dp = half.cornersDp;
        if (dp < 0) dp = TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(null, true);
        return dp * density;
    }

    private static float marginPx(@NonNull DockGeometry.Half half, float density) {
        return Math.max(0, half.marginDp) * density;
    }

    /**
     * The band the container is laid out taller by at rest: the other side's rows over the
     * front's, when the two layouts have a lane between them ({@code laneSlackPx}).
     */
    private static int laneSlackPx(@NonNull Rest rest) {
        StackLayout.Input pass = rest.pass;
        boolean frontHome = rest.page.isHome();
        PageRef front = frontHome ? PageRef.HOME : PageRef.TERMINAL;
        PageRef other = frontHome ? PageRef.TERMINAL : PageRef.HOME;
        PlaceLayout frontLayout = pass.dock.half(front).layout;
        PlaceLayout otherLayout = pass.dock.half(other).layout;
        if (!StackLanes.hasLanes(frontLayout, otherLayout, Side.of(frontHome))) return 0;
        int frontRows = StackLayout.restRowsPx(new StackLayout.RestInput(pass.dock, front)
            .ownRows(rest.measuredExtraKeysRowPx));
        int otherRows = StackLayout.restRowsPx(new StackLayout.RestInput(pass.dock, other)
            .ownRows(rest.measuredExtraKeysRowPx));
        return StackLanes.slackPx(frontRows, otherRows);
    }
}
