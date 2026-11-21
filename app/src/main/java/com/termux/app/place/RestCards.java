package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.chrome.LiveChromeShape;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockHostBounds;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockPlacement;
import com.termux.app.dock.DockSpec;
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
import com.termux.app.terminal.inappkeyboard.FloatingKeyboardGeometry;
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
 *   <li>The lane slack: read from the two layouts' rests, plus the Terminal's rest flush padding
 *       over the pass's ({@link Rest#restFlushPaddingPx}, {@code StackLanes#flushSlackPx}).</li>
 * </ul>
 *
 * <p>Two cards under Floating (ADR 0032, #302): while {@link SideCards#apart} holds, Home's card
 * stays on the glass host and the Terminal's extra-keys card is {@link StackView#KEYS_CARD}, each
 * laid out at its own rest at both rests. The caller fills {@link Rest#other} (the other side's
 * rest, one level) and the answer then holds the container's one margin, draws Home's side
 * {@link SideCards#homeSideDropPx} below its layout, and lists the other side's cards, which a
 * view stands laid out for but draws off screen with its side, in {@link #parked()}. A rest with
 * no {@code other} answers as it did before.
 *
 * <p>The foot under the gesture pill ({@link CardId#FOOT}) is the kept navigation band
 * ({@link Rest#keptBandPx}) wherever a card reaches through it: the dock's host while it owns
 * the foot ({@code DockSpec#footExists}), or the Docked keyboard while it wears the band as its
 * own ({@link BottomInsets#keyboardWearsFoot}). The card above it stops at the band's top. A
 * Peek card already runs past the screen's bottom and carries the band itself.
 *
 * <p>The keyboard's card in each form: Docked, a slice of the column (one piece, or the split
 * keyboard's two halves under one host); Floating, a capsule inset by the host's side margin
 * ({@code ChromePolicy#keyboardHostSideMarginPx}); the floating form, its own frame where
 * {@link FloatingKeyboardGeometry} parks it in its room ({@link Rest#floatingKeyboardRoom}).
 *
 * <p>The A–Z bar: {@link #azPlacement} says where the layout stands it and {@link #carrierOf}
 * which card carries it. Off the dock (an edge strip on the top or a side) it is not a card here:
 * its host stands in the content column's own edge stack, whose bounds no pass carries.
 */
public final class RestCards {

    public enum CardId { STATUS, DOCK, PEEK, UNDER, KEYBOARD, FOOT }

    /** The views that carry cards or are laid out around them. */
    public enum StackView {
        CONTAINER, HOST, KEYBOARD_COLUMN, UNDER, STATUS_BAR, FLOATING_KEYBOARD,
        /** The Terminal's extra-keys card while it stands apart from Home's (SideCards). */
        KEYS_CARD
    }

    /** Where the layout stands the A–Z bar (ResolvedArrangement's azRowOnDock, azBarEdge, azTab). */
    public enum AzPlacement {
        /** A row of the dock's stack: the dock's, the bands under the keyboard's or Peek's card. */
        DOCK_ROW,
        /** A bar of its own on the top or a side, in the content column's edge stack. */
        EDGE,
        /** The pull tab: on screen, but no band and no glass at rest. */
        TAB,
        HIDDEN
    }

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
        /** The navigation band the content keeps clear at the screen's bottom (BottomInsets#keptPx). */
        public int keptBandPx = 0;
        /** The Terminal's flush padding at its own rest, as the activity last noted it. */
        public int restFlushPaddingPx = 0;
        /** The user's chin under the keyboard's keys (Settings), px. */
        public int keyboardChinPx = 0;
        /** The floating-form keyboard's room (its frame's parent), screen px; null while none. */
        @Nullable public Box floatingKeyboardRoom;
        /** The floating frame's height and its width share, floor and parked fractions. */
        public int floatingKeyboardHeightPx = 0;
        public float floatingKeyboardWidthScale = 1f;
        public int floatingKeyboardMinWidthPx = 0;
        public float floatingKeyboardX = FloatingKeyboardGeometry.DEFAULT_X_FRACTION;
        public float floatingKeyboardY = FloatingKeyboardGeometry.DEFAULT_Y_FRACTION;
        /**
         * The other side's rest (Home for the Terminal's, the Terminal for Home's), filled the
         * same way with that side's own keyboard ({@link SideCards#restKeyboardUp}), never the
         * front's; its own {@code other} is ignored.
         * Null answers as one side alone.
         */
        @Nullable public Rest other;

        /**
         * @param measuredKeyboardPx the keyboard as the stack lays it out while it is down, its
         *                           foot included: 0 for a floating-form keyboard, which its own
         *                           frame lays out
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

    /**
     * What the glass host wears at Home's rest, worked out from the rest's own numbers: the
     * values its dress reads from the page in front while the cards stand together, and from
     * here while they stand apart ({@link SideCards}), so the host is dressed as Home's rest at
     * every rest and the landing on Home re-dresses nothing.
     */
    public static final class HostLook {
        /** Home's Dock design as its slab wears it (none where Home shows no apps row). */
        @NonNull public final com.termux.app.dock.DockStyle design;
        /** The Peek card stands: flush with the screen's edge, cut at the icons' centre line. */
        public final boolean peek;
        /** The dock is placed as the Floating card ({@link DockPlacement#floats}). */
        public final boolean floats;
        /** The dock's glass owns the foot under the gesture pill ({@link DockSpec#footExists}). */
        public final boolean foot;
        /** The keyboard state the rest was answered with. */
        public final boolean keyboardUp;
        /** The Peek card cut in the host's coordinates, as the pass cuts it; null off Peek. */
        @Nullable public final PeekGlassGeometry.Card peekCut;
        /** Where the host view is drawn at this rest, screen px (its card is cut from it). */
        @Nullable public final Box drawn;

        HostLook(@NonNull com.termux.app.dock.DockStyle design, boolean peek, boolean floats,
                 boolean foot, boolean keyboardUp, @Nullable PeekGlassGeometry.Card peekCut,
                 @Nullable Box drawn) {
            this.drawn = drawn;
            this.design = design;
            this.peek = peek && peekCut != null;
            this.floats = floats;
            this.foot = foot;
            this.keyboardUp = keyboardUp;
            this.peekCut = this.peek ? peekCut : null;
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
    @NonNull private final AzPlacement mAz;
    @NonNull private final List<Card> mCards = new ArrayList<>(6);
    @NonNull private final List<Card> mParked = new ArrayList<>(2);
    @NonNull private final Box[] mLaidOut = new Box[StackView.values().length];
    private boolean mApart;
    private int mContainerMarginPx;
    private int mHomeDropPx;
    /** How the host dresses its card at this rest (the Home half's, once adopted); null for the Terminal's. */
    @Nullable private HostLook mHostLook;

    /** The rest's answer. */
    @NonNull
    public static RestCards of(@NonNull Rest rest) {
        Rest other = rest.other;
        if (other == null || other.page.isHome() == rest.page.isHome()) {
            return new RestCards(rest, 0, false);
        }
        final boolean frontHome = rest.page.isHome();
        final DockGeometry.Half half = rest.pass.dock.rowsHalf();
        final Rest terminal = frontHome ? other : rest;
        if (!SideCards.apart(rest.pass.dock.half(PageRef.HOME).layout,
                rest.pass.dock.half(PageRef.TERMINAL).layout, half.floating,
                terminal.statusOnDockPlank)) {
            return new RestCards(rest, 0, false);
        }
        // Each side's own rest margin, worked out by the one helper; the container keeps the
        // Terminal's at both rests and Home's side is drawn the difference lower.
        final int mine = restMarginPx(rest);
        final int theirs = restMarginPx(other);
        final int homeMargin = frontHome ? mine : theirs;
        final int terminalMargin = frontHome ? theirs : mine;
        final int drop = SideCards.homeSideDropPx(homeMargin, terminalMargin, true);
        RestCards answer = new RestCards(rest, frontHome ? drop : 0, !frontHome);
        RestCards otherCards = new RestCards(other, frontHome ? 0 : drop, frontHome);
        answer.adopt(otherCards, frontHome);
        if (!frontHome) answer.mHostLook = otherCards.mHostLook;
        answer.mApart = true;
        answer.mContainerMarginPx = SideCards.containerMarginPx(homeMargin, terminalMargin, true,
            frontHome);
        answer.mHomeDropPx = drop;
        return answer;
    }

    /**
     * How the glass host is dressed at Home's rest: Home's own answer, whichever page is in
     * front. Null unless the cards stand apart, when the host is dressed from the page in front.
     */
    @Nullable
    public HostLook homeHostLook() {
        return mApart ? mHostLook : null;
    }

    /** Where the host is drawn at Home's rest ({@link #homeHostLook}); null when it is not laid out. */
    @Nullable
    public Box homeHostRect() {
        HostLook look = homeHostLook();
        return look == null ? null : look.drawn;
    }

    /** Whether Home's card and the Terminal's extra-keys card stand apart ({@link SideCards}). */
    public boolean apart() {
        return mApart;
    }

    /**
     * The container's bottom margin at this rest: the Terminal's own at both rests while the
     * cards stand apart, else the margin this rest's stack has.
     */
    public int containerMarginPx() {
        return mContainerMarginPx;
    }

    /** How far below the container's layout Home's side is drawn; 0 unless apart. */
    public int homeSideDropPx() {
        return mHomeDropPx;
    }

    /**
     * The card {@code view} carries at its own rest while the cards stand apart: this rest's, or
     * the parked other side's. Null when none stands there.
     */
    @Nullable
    public Card cardOn(@NonNull StackView view) {
        for (Card card : mCards) if (card.view == view) return card;
        for (Card card : mParked) if (card.view == view) return card;
        return null;
    }

    /**
     * The cards a view stands laid out for but draws off screen with its side: the other side's
     * glass while the two cards stand apart, empty otherwise. Their boxes are where that side
     * draws them at its own rest.
     */
    @NonNull
    public List<Card> parked() {
        return Collections.unmodifiableList(mParked);
    }

    /** The cards in a stable order: STATUS, DOCK or PEEK, a foot it wears, UNDER, KEYBOARD, its foot. */
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

    /** Where the layout stands the A–Z bar. */
    @NonNull
    public AzPlacement azPlacement() {
        return mAz;
    }

    /** The card that carries {@code piece}, null when none does (hidden, a tab, off the stack). */
    @Nullable
    public Card carrierOf(@NonNull PieceId piece) {
        for (Card card : mCards) if (card.pieces.contains(piece)) return card;
        return null;
    }

    /** {@link AzPlacement} for {@code layout}, as PlaceChromePolicy reads it. */
    @NonNull
    static AzPlacement azPlacementOf(@NonNull PlaceLayout layout) {
        if (PlaceChromePolicy.azTabShown(layout)) return AzPlacement.TAB;
        if (PlaceChromePolicy.azRowOnDock(layout)) return AzPlacement.DOCK_ROW;
        if (PlaceChromePolicy.azRowShown(layout)) return AzPlacement.EDGE;
        return AzPlacement.HIDDEN;
    }

    // ---- The answer ------------------------------------------------------------------------

    /**
     * @param homeDropPx how far below its layout Home's side is drawn (container-borne views
     *                   only); the laid-out boxes stand that much higher than the cards are drawn
     * @param keysCard   the dock's card is the Terminal's extra-keys card ({@link StackView#KEYS_CARD})
     */
    private RestCards(@NonNull Rest rest, int homeDropPx, boolean keysCard) {
        final StackLayout.Input pass = rest.pass;
        final StackView hostView = keysCard ? StackView.KEYS_CARD : StackView.HOST;
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
        mContainerMarginPx = marginPx(r);

        mAz = azPlacementOf(layout);
        final KeyboardForm form = layout.keyboardForm;
        final boolean capsule = floating || form == KeyboardForm.FLOATING;

        // The bands under the keyboard and whether the dock's glass stands as the Peek card.
        final List<Element> under = EdgeStackPolicy.underKeyboard(layout);
        final boolean peekStands = DockPlacement.peekCardStandsOn(pass.dock.rows.kind,
            DockSurfaceSpec.forStyle(half.design).peek, pass.toolbarShown, layout, false, up,
            pass.imeLiftPx > 0, dock.promptFieldBandPx > 0);
        final boolean floats = DockPlacement.floats(floating, floating && peekStands);

        // The kept band at the screen's bottom and who wears it: the dock's host while it owns
        // the foot, the Docked keyboard while it is laid out with nothing under it.
        final int kept = Math.max(0, rest.keptBandPx);
        final boolean dockFoot = DockSpec.footExists(pass.toolbarShown, floats, up, false, kept);
        final boolean keyboardLaidOutInColumn = form != KeyboardForm.FLOATING
            && (up ? pass.keyboardHeightPx > 0 : rest.measuredKeyboardPx > 0);
        final boolean keyboardFoot = BottomInsets.keyboardWearsFoot(capsule,
            form == KeyboardForm.FLOATING, false, r.bandsUnderKeyboard, keyboardLaidOutInColumn);
        // The container: on the screen's bottom, as tall as the stack, the keyboard laid out
        // while it is down, the kept band and the band both sides' rests need.
        final BottomInsets insets = BottomInsets.forKept(0, kept, dockFoot, r.bottomMarginPx,
            r.flushBottomInsetPx, keyboardFoot);
        final int kbDown = up ? 0
            : BottomInsets.keyboardKeysPx(rest.measuredKeyboardPx, insets.keyboardFootPx);
        final int slack = laneSlackPx(rest) + StackLanes.flushSlackPx(rest.restFlushPaddingPx,
            r.flushPaddingPx);
        final int containerH = StackScene.containerHeightPx(
            r.stackHeightPx + insets.containerExtraPx(), kbDown, slack);
        final float containerBottom = h - marginPx(r);
        final float containerTop = containerBottom - containerH;
        final boolean containerLaidOut = r.stackShown || kbDown > 0;
        if (containerLaidOut)
            mLaidOut[StackView.CONTAINER.ordinal()] = new Box(0f, containerTop, w, containerBottom);
        // The container is drawn lower by the keyboard's share while the keyboard is down.
        final float shift = StackScene.keyboardTranslationPx(0f, kbDown);

        // The keyboard's column on the container's bottom, with the bands under the keyboard;
        // the keyboard's host holds its foot under its keys.
        final int keyboardLaid = up ? pass.keyboardHeightPx : kbDown;
        final int keyboardFootPx = keyboardLaid > 0 ? insets.keyboardFootPx : 0;
        int underRows = 0;
        if (r.bandsUnderKeyboard) for (Element e : under) underRows += bandPx(e, dock, pass);
        final int underBlock = r.bandsUnderKeyboard
            ? r.underKeyboardTopMarginPx + underRows + r.underKeyboardBottomMarginPx : 0;
        final float columnBottom = containerBottom - insets.stackPaddingPx
            - insets.keyboardColumnMarginPx;
        final float columnTop = columnBottom - (keyboardLaid + keyboardFootPx + underBlock);
        if (containerLaidOut && keyboardLaid + keyboardFootPx + underBlock > 0)
            mLaidOut[StackView.KEYBOARD_COLUMN.ordinal()] = new Box(0f, columnTop, w, columnBottom);

        // The glass host: pinned to the container's top and above the keyboard's column, or to
        // the container's bottom while it is one surface with the Docked keyboard or owns the foot.
        final int sideMargin = DockHostBounds.sideMarginPx(floats, dock.horizontalInsetPx);
        final float hostTop = containerTop + DockHostBounds.topInsetPx(
            rest.statusOnDockPlank ? 0 : r.statusBandPx, dock.slabTopInsetPx, slack);
        final boolean unified = !r.bandsUnderKeyboard
            && ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(pass.toolbarShown, up,
                floating, rest.keyboardGlassShared);
        final float hostBottomOfStack = unified || insets.dockFootPx > 0 ? containerBottom
            : columnTop;
        // The extra-keys card is the keys band only, in both keyboard states (ADR 0032): the
        // flush padding the lone Terminal wears under its keys stays air, so the card never
        // re-sizes when the keyboard comes or goes.
        final float hostBottom = keysCard
            ? Math.min(hostBottomOfStack, hostTop + bandPx(Element.EXTRA_KEYS, dock, pass))
            : hostBottomOfStack;
        final boolean hostLaidOut = containerLaidOut && pass.toolbarShown && hostBottom > hostTop;
        if (hostLaidOut)
            mLaidOut[hostView.ordinal()] =
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
        final boolean keyboardCard = containerLaidOut && up && keyboardLaid > 0
            && form != KeyboardForm.FLOATING && !unified;
        final boolean wantsShape = statusOnTop || hostLaidOut && !peekStands && !floats
            || keyboardCard && !floating;
        final ChromeShape shape = wantsShape ? shapeOf(rest, r, dock, half, layout, style,
            cornersPx) : null;
        // The foot's top: where the band the card above it stops at starts. The host is drawn
        // moved by the container's share and cut at the container's laid-out bottom.
        final float footTop = h - kept;

        // STATUS: the bar along the top edge.
        if (statusOnTop) addStatus(rest, shape, floating, w, h, density);
        // DOCK or PEEK: the host's glass.
        PeekGlassGeometry.Card peekCut = null;
        Box hostDrawn = null;
        if (hostLaidOut) {
            hostDrawn = new Box(sideMargin, hostTop + shift, w - sideMargin,
                Math.min(hostBottom + shift, containerBottom));
            float drawnBottom = Math.min(hostBottom + shift, containerBottom);
            boolean hostFoot = kept > 0 && drawnBottom >= h && !peekStands
                && (insets.dockFootPx > 0 || unified && keyboardFootPx > 0);
            float cardBottom = hostFoot ? footTop : drawnBottom;
            if (peekStands) {
                peekCut = addPeek(rest, layout, dock, hostTop, hostBottom, columnTop, w, h, shift,
                    density);
            } else if (floats) {
                addFloatingDock(hostView, layout, pass, rest, dock, hostTop, cardBottom,
                    sideMargin, w, shift, cornersPx, unified);
            } else {
                addDockedDock(layout, pass, rest, shape, hostTop, cardBottom, w, shift, unified,
                    hostFoot);
            }
            if (hostFoot) addFoot(hostView, sideMargin, footTop, w - sideMargin, h);
        }
        if (!keysCard)
            mHostLook = new HostLook(half.design, peekStands, floats, dockFoot, up, peekCut,
                hostDrawn);
        // UNDER: the bands under the keyboard wear the dock's sheet unless the Peek card does.
        if (underLaidOut && UnderKeyboardBand.wearsSheet(r.bandsUnderKeyboard, pass.toolbarShown,
                peekStands)) {
            addUnder(under, dock, underTop, underBottom, floating, w);
        }
        // KEYBOARD: the docked-form keyboard, unless the dock's glass carries it.
        if (keyboardCard) {
            boolean keyboardFootDrawn = kept > 0 && keyboardFootPx > 0;
            addKeyboard(rest, shape, form, half, columnTop, keyboardLaid,
                keyboardFootDrawn ? footTop : columnTop + keyboardLaid + keyboardFootPx,
                keyboardFootDrawn, floating, w, cornersPx, density);
            if (keyboardFootDrawn) addFoot(StackView.KEYBOARD_COLUMN, 0f, footTop, w, h);
        }
        // KEYBOARD: the floating-form keyboard, its own frame parked in its room.
        if (up && form == KeyboardForm.FLOATING) addFloatingKeyboard(rest, cornersPx);

        // Home's side while the cards stand apart: the container stands at the Terminal's margin,
        // so what it carries is laid out that much higher than it is drawn above.
        if (homeDropPx != 0) {
            liftLaidOut(StackView.CONTAINER, homeDropPx);
            liftLaidOut(StackView.KEYBOARD_COLUMN, homeDropPx);
            liftLaidOut(StackView.HOST, homeDropPx);
            liftLaidOut(StackView.UNDER, homeDropPx);
        }
    }

    private void liftLaidOut(@NonNull StackView view, int px) {
        Box box = mLaidOut[view.ordinal()];
        if (box != null) mLaidOut[view.ordinal()] = new Box(box.left, box.top - px, box.right,
            box.bottom - px);
    }

    /**
     * Takes the other side's laid-out view and parks its cards: each stands laid out for a view
     * of its own and is drawn off screen with its side. Home's host view is the Terminal's to
     * read while the Terminal is in front, the Terminal's card view Home's.
     */
    private void adopt(@NonNull RestCards other, boolean frontHome) {
        StackView theirs = frontHome ? StackView.KEYS_CARD : StackView.HOST;
        mLaidOut[theirs.ordinal()] = other.mLaidOut[theirs.ordinal()];
        for (Card card : other.mCards) if (card.view == theirs) mParked.add(card);
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

    private void addFloatingDock(@NonNull StackView view, @NonNull PlaceLayout layout, @NonNull StackLayout.Input pass,
                                 @NonNull Rest rest, @NonNull DockLayout dock, float hostTop,
                                 float drawnBottom, int sideMargin, int w, float shift,
                                 float cornersPx, boolean unified) {
        Box box = new Box(sideMargin, hostTop + shift, w - sideMargin, drawnBottom);
        float radius = Math.min(cornersPx, box.height() / 2f);
        mCards.add(new Card(CardId.DOCK, view, box,
            new Corners(radius, radius, radius, radius), EdgeKind.RIM, EdgeKind.RIM,
            EdgeKind.RIM, EdgeKind.RIM, dockPieces(layout, rest, unified)));
    }

    private void addDockedDock(@NonNull PlaceLayout layout, @NonNull StackLayout.Input pass,
                               @NonNull Rest rest, @Nullable ChromeShape shape, float hostTop,
                               float drawnBottom, int w, float shift, boolean unified,
                               boolean footBelow) {
        List<PieceId> ids = dockPieces(layout, rest, unified);
        Box box = new Box(0f, hostTop + shift, w, drawnBottom);
        LiveChromeShape.Clip clip = shape == null ? null : LiveChromeShape.outlineOf(shape, ids);
        Corners corners = clip == null ? Corners.SQUARE : new Corners(clip.topLeft,
            clip.topRight, clip.bottomRight, clip.bottomLeft);
        mCards.add(new Card(CardId.DOCK, StackView.HOST, box, corners,
            outerKind(shape, ids, Edge.TOP),
            footBelow ? EdgeKind.JOIN : outerKind(shape, ids, Edge.BOTTOM),
            outerKind(shape, ids, Edge.LEFT), outerKind(shape, ids, Edge.RIGHT), ids));
    }

    /**
     * The Peek card: cut at the apps row's icon line, inset from the screen's sides, running on
     * past the screen's bottom (this box ends at it). {@code rowsBottom} is where the rows stand:
     * the keyboard column's top, even while the host runs on over the keyboard.
     */
    @Nullable
    private PeekGlassGeometry.Card addPeek(@NonNull Rest rest, @NonNull PlaceLayout layout, @NonNull DockLayout dock,
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
        if (card == null) return null;
        Box box = new Box(card.left, hostTopDrawn + card.top, card.right, h);
        List<PieceId> pieces = new ArrayList<>(2);
        pieces.add(PieceId.APPS);
        if (DockPlacement.peekCardCarriesAz(layout, false)) pieces.add(PieceId.AZ);
        mCards.add(new Card(CardId.PEEK, StackView.HOST, box,
            new Corners(card.radiusPx, card.radiusPx, 0f, 0f), EdgeKind.RIM, EdgeKind.SCREEN,
            EdgeKind.RIM, EdgeKind.RIM, pieces));
        return card;
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

    /**
     * The keyboard in the stack's column: its host, inset by the host's own margins (the side gap
     * and the keyboard gap a capsule keeps, the chin under a capsule), down to {@code bottom}.
     * Docked it is a slice of the frame whose edges are the shape model's; Floating a capsule
     * with a rim all round. A split keyboard is one host across its two halves.
     */
    private void addKeyboard(@NonNull Rest rest, @Nullable ChromeShape shape,
                             @NonNull KeyboardForm form, @NonNull DockGeometry.Half half,
                             float columnTop, int keyboardPx, float bottom, boolean footBelow,
                             boolean floating, int w, float cornersPx, float density) {
        List<PieceId> ids = form == KeyboardForm.SPLIT
            ? java.util.Arrays.asList(PieceId.KEYBOARD_LEFT, PieceId.KEYBOARD_RIGHT)
            : Collections.singletonList(PieceId.KEYBOARD);
        int side = ChromePolicy.keyboardHostSideMarginPx(false,
            TermuxAppSharedPreferences.clampSurfaceHorizontalInset(half.marginDp), floating,
            density);
        int top = ChromePolicy.keyboardHostTopMarginPx(floating, false, density);
        if (floating) {
            int chin = ChromePolicy.keyboardChinBottomMarginPx(true, 0, rest.keyboardChinPx);
            Box box = new Box(side, columnTop + top, w - side,
                Math.min(bottom, columnTop + keyboardPx) - chin);
            float radius = Math.min(cornersPx, box.height() / 2f);
            mCards.add(new Card(CardId.KEYBOARD, StackView.KEYBOARD_COLUMN, box,
                new Corners(radius, radius, radius, radius), EdgeKind.RIM, EdgeKind.RIM,
                EdgeKind.RIM, EdgeKind.RIM, ids));
        } else {
            Box box = new Box(side, columnTop + top, w - side, bottom);
            LiveChromeShape.Clip clip = shape == null ? null : LiveChromeShape.outlineOf(shape, ids);
            Corners corners = clip == null ? Corners.SQUARE : new Corners(clip.topLeft,
                clip.topRight, clip.bottomRight, clip.bottomLeft);
            mCards.add(new Card(CardId.KEYBOARD, StackView.KEYBOARD_COLUMN, box, corners,
                outerKind(shape, ids, Edge.TOP),
                footBelow ? EdgeKind.JOIN : outerKind(shape, ids, Edge.BOTTOM),
                outerKind(shape, ids, Edge.LEFT), outerKind(shape, ids, Edge.RIGHT), ids));
        }
    }

    /**
     * The foot under the gesture pill: the kept band, worn by the card above it ({@code view}),
     * joined to it at its top and on the screen's edge everywhere else.
     */
    private void addFoot(@NonNull StackView view, float left, float top, float right, int h) {
        mCards.add(new Card(CardId.FOOT, view, new Box(left, top, right, h), Corners.SQUARE,
            EdgeKind.JOIN, EdgeKind.SCREEN, EdgeKind.SCREEN, EdgeKind.SCREEN,
            Collections.<PieceId>emptyList()));
    }

    /**
     * The floating-form keyboard: its frame at its width share of the room, parked at its
     * fractions of the room's travel ({@link FloatingKeyboardGeometry}, as the controller puts
     * it), a card over the content with Corners clamped to half its short side and a rim all round.
     */
    private void addFloatingKeyboard(@NonNull Rest rest, float cornersPx) {
        Box room = rest.floatingKeyboardRoom;
        if (room == null || rest.floatingKeyboardHeightPx <= 0) return;
        int roomW = Math.round(room.width());
        int roomH = Math.round(room.height());
        int frameW = FloatingKeyboardGeometry.frameWidthPx(roomW, rest.floatingKeyboardWidthScale,
            rest.floatingKeyboardMinWidthPx);
        int frameH = rest.floatingKeyboardHeightPx;
        if (frameW <= 0) return;
        float left = room.left + FloatingKeyboardGeometry.positionPx(rest.floatingKeyboardX,
            FloatingKeyboardGeometry.travelPx(roomW, frameW));
        float top = room.top + FloatingKeyboardGeometry.positionPx(rest.floatingKeyboardY,
            FloatingKeyboardGeometry.travelPx(roomH, frameH));
        Box box = new Box(left, top, left + frameW, top + frameH);
        mLaidOut[StackView.FLOATING_KEYBOARD.ordinal()] = box;
        float radius = Math.max(0f, Math.min(cornersPx, Math.min(box.width(), box.height()) / 2f));
        mCards.add(new Card(CardId.KEYBOARD, StackView.FLOATING_KEYBOARD, box,
            new Corners(radius, radius, radius, radius), EdgeKind.RIM, EdgeKind.RIM,
            EdgeKind.RIM, EdgeKind.RIM, Collections.singletonList(PieceId.KEYBOARD)));
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

    /**
     * A rest's own container bottom margin: what {@code BottomInsets#stackMarginPx} holds for the
     * pass's margin. The one code both sides' margins are worked out by.
     */
    static int marginPx(@NonNull StackLayout.Result r) {
        return Math.max(0, r.bottomMarginPx);
    }

    private static int restMarginPx(@NonNull Rest rest) {
        return marginPx(StackLayout.layout(rest.pass, new DockGeometry()));
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
