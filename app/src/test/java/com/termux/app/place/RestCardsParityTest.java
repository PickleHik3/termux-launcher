package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.chrome.LiveChromeShape;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockHostBounds;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockPlacement;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.UnderKeyboardBand;
import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.statusbar.StatusBarEdgeGeometry;
import com.termux.app.wall.PageRef;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The per-rest card answer ({@link RestCards}) is where today's views are laid out and drawn, on
 * every fixture in every Dock design, Docked and Floating, keyboard up and down, on Home and the
 * Terminal. The expected rects are worked out here from the stack's parts the way the activity's
 * relative layout puts the views, then run through {@link StackScene} with the keyboard's rest
 * share (the container sinks by a down keyboard's height, the bands under it are held up), and
 * read off the frame's transforms; they never go through {@link RestCards}.
 *
 * <p>The shape model is a second source for a Docked or Floating dock card's box: it says where
 * the card should stand, the views say where it does. Where today's code disagrees with itself
 * the disagreement is a named parity exception below, not a change of behaviour; the set found
 * must equal the set named, so an exception that starts agreeing fails here.
 */
@RunWith(Parameterized.class)
public class RestCardsParityTest {

    /**
     * Shape model against the laid-out host, one key per (card, Style, page, keyboard, edge), over
     * every fixture and design. This is today's code disagreeing with itself, not RestCards. The
     * numbers are one representative case each (pong, glass tray, px; shape model vs host):
     * <ul>
     *   <li>Docked top: the shape model's card starts at the apps band's top, the host's sheet
     *       starts the design's slab inset lower (the band of page dashes): Home 2244 vs 2261,
     *       the Terminal's likewise (tl16 2134 vs 2151).</li>
     *   <li>Docked Terminal bottom: the model's one card runs down through the keyboard and the
     *       bands under it, the host stops where its column starts: kbDown 2412 vs 2261, kbUp
     *       2412 vs 1591.</li>
     *   <li>Floating: the model stands cards Margin apart and from the screen's edge, the host
     *       keeps its own 4dp gap over the keyboard and the dock's gap under it: Home kbDown
     *       bottom 2380.5 vs 2396 and top 2214.5 vs 2247 (the slab inset on top of the gap),
     *       Home kbUp bottom 1679 vs 1726 and top 1513 vs 1577, Terminal kbDown bottom 2380.5 vs
     *       2236 (the model's card also holds the bands under the keyboard) and top 2133.5 vs
     *       2138, Terminal kbUp bottom 1498.5 vs 1566 and top 1400.5 vs 1468.</li>
     * </ul>
     */
    static final Set<String> PARITY_EXCEPTIONS = new TreeSet<>(Arrays.asList(
        "DOCK shape≠layout: DOCKED HOME kbDown top",
        "DOCK shape≠layout: DOCKED HOME kbUp top",
        "DOCK shape≠layout: DOCKED TERMINAL kbDown bottom",
        "DOCK shape≠layout: DOCKED TERMINAL kbDown top",
        "DOCK shape≠layout: DOCKED TERMINAL kbUp bottom",
        "DOCK shape≠layout: DOCKED TERMINAL kbUp top",
        "DOCK shape≠layout: FLOATING HOME kbDown bottom",
        "DOCK shape≠layout: FLOATING HOME kbDown top",
        "DOCK shape≠layout: FLOATING HOME kbUp bottom",
        "DOCK shape≠layout: FLOATING HOME kbUp top",
        "DOCK shape≠layout: FLOATING TERMINAL kbDown bottom",
        "DOCK shape≠layout: FLOATING TERMINAL kbDown top",
        "DOCK shape≠layout: FLOATING TERMINAL kbUp bottom",
        "DOCK shape≠layout: FLOATING TERMINAL kbUp top"));

    private static final Set<String> FOUND = new TreeSet<>();

    @BeforeClass public static void clearFound() { FOUND.clear(); }

    @AfterClass public static void everyDisagreementIsNamedAndEveryNameStillDisagrees() {
        assertEquals("parity exceptions found vs named", PARITY_EXCEPTIONS, FOUND);
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        List<Object[]> out = new ArrayList<>();
        for (LayoutFixtures.Fixture f : LayoutFixtures.allDesigns()) out.add(new Object[] {f});
        return out;
    }

    private static final float EPS = 0.01f;
    /** A shape-model card and a laid-out host are one thing when within a pixel of each other. */
    private static final float SHAPE_EPS = 1f;

    private final LayoutFixtures.Fixture f;

    public RestCardsParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    // ---- The views as laid out, worked out apart from RestCards ----------------------------

    /** One rest's views in the container's pixels, and where the container stands. */
    private static final class Views {
        StackLayout.Result r;
        DockLayout dock;
        boolean peekStands, floats, unified, containerLaidOut, hostLaidOut, underLaidOut;
        int containerTop, containerH, slack, kbDown, kbLaid, underRows, underBlock;
        int columnTop, columnH, columnBottom;
        int hostTop, hostBottom, sideMargin;
        int underTop, underBottom;
        StackGeometry g;
        StackScene.Frame frame = new StackScene.Frame();
    }

    private static int bandOf(Element e, DockLayout d, StackLayout.Input pass) {
        switch (e) {
            case APPS:
                return AccessoryStackLayoutPolicy.computeCombinedHeight(d.appsBarHeightPx > 0,
                    false, false, d.appsBarHeightPx + d.promptFieldBandPx + d.wallStripBandPx, 0,
                    0, d.indicatorBandHeightPx);
            case AZ:
                return AccessoryStackLayoutPolicy.computeCombinedHeight(false,
                    d.azRowHeightPx > 0, false, 0, d.azRowHeightPx, 0, 0);
            case EXTRA_KEYS:
                return AccessoryStackLayoutPolicy.computeCombinedHeight(false, false,
                    pass.extraKeysRowEnabled, 0, 0, pass.toolbarHeightPx, 0);
            default:
                return 0;
        }
    }

    private int laneSlack(PageRef page, DockGeometry.Snapshot snapshot) {
        PageRef other = page.isHome() ? PageRef.TERMINAL : PageRef.HOME;
        if (!StackLanes.hasLanes(f.layoutOf(page), f.layoutOf(other), Side.of(page.isHome())))
            return 0;
        int front = StackLayout.restRowsPx(new StackLayout.RestInput(snapshot, page)
            .ownRows(f.extraKeysRowPx));
        int back = StackLayout.restRowsPx(new StackLayout.RestInput(snapshot, other)
            .ownRows(f.extraKeysRowPx));
        return Math.max(0, back - front);
    }

    private static boolean statusOnPlank(PlaceLayout layout) {
        List<Element> bottom = EdgeStackPolicy.stack(layout, Edge.BOTTOM);
        return bottom.contains(Element.STATUS)
            && !AccessoryStackLayoutPolicy.statusKeepsOwnGlass(bottom);
    }

    private Views views(PageRef page, boolean up, boolean floating, StackLayout.Input pass) {
        Views v = new Views();
        PlaceLayout layout = f.layoutOf(page);
        v.r = StackLayout.layout(pass, new DockGeometry());
        v.dock = v.r.dock;
        BottomInsets insets = BottomInsets.forKept(0, 0, false, v.r.bottomMarginPx,
            v.r.flushBottomInsetPx);
        v.kbDown = up ? 0 : f.keyboardLaidOutPx();
        v.slack = laneSlack(page, pass.dock);
        v.containerH = v.r.stackHeightPx + insets.stackExtraHeightPx + v.kbDown + v.slack;
        v.containerTop = f.heightPx - insets.stackMarginPx - v.containerH;
        v.containerLaidOut = v.r.stackShown || v.kbDown > 0;
        v.kbLaid = up ? pass.keyboardHeightPx : v.kbDown;
        if (v.r.bandsUnderKeyboard)
            for (Element e : EdgeStackPolicy.underKeyboard(layout))
                v.underRows += bandOf(e, v.dock, pass);
        v.underBlock = v.r.bandsUnderKeyboard ? v.r.underKeyboardTopMarginPx + v.underRows
            + v.r.underKeyboardBottomMarginPx : 0;
        v.columnH = v.kbLaid + v.underBlock;
        v.columnBottom = v.containerH - insets.stackPaddingPx - insets.keyboardColumnMarginPx;
        v.columnTop = v.columnBottom - v.columnH;
        v.peekStands = DockPlacement.peekCardStandsOn(page.kind,
            DockSurfaceSpec.forStyle(design(page)).peek, pass.toolbarShown, layout, false,
            up, pass.imeLiftPx > 0, v.dock.promptFieldBandPx > 0);
        v.floats = DockPlacement.floats(floating, floating && v.peekStands);
        v.sideMargin = DockHostBounds.sideMarginPx(v.floats, v.dock.horizontalInsetPx);
        int statusAbove = statusOnPlank(layout) ? 0 : v.r.statusBandPx;
        v.unified = !v.r.bandsUnderKeyboard
            && ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(pass.toolbarShown, up,
                floating, true);
        v.hostTop = statusAbove + v.dock.slabTopInsetPx + v.slack;
        v.hostBottom = v.unified ? v.containerH : v.columnTop;
        v.hostLaidOut = v.containerLaidOut && pass.toolbarShown && v.hostBottom > v.hostTop;
        v.underBottom = v.columnBottom - v.r.underKeyboardBottomMarginPx;
        v.underTop = v.underBottom - v.underRows;
        v.underLaidOut = v.containerLaidOut && v.r.bandsUnderKeyboard && pass.toolbarShown;

        StackEnd end = new StackEnd(page);
        // A region only shows where an end has a look for it; its rect is what is read here.
        for (int region = 0; region < StackEnd.REGION_COUNT; region++)
            end.looks[region] = new com.termux.app.chrome.StackMaterial.RegionLook();
        StackGeometry g = end.rest;
        v.g = g;
        g.displayWidthPx = f.widthPx;
        g.displayHeightPx = f.heightPx;
        g.containerWidthPx = f.widthPx;
        g.containerHeightPx = v.containerH;
        g.stackPaddingBottomPx = insets.stackPaddingPx;
        g.layerPresent[StackGeometry.HOST] = v.hostLaidOut;
        g.layerTopPx[StackGeometry.HOST] = v.hostTop;
        g.layerWidthPx[StackGeometry.HOST] = f.widthPx - 2 * v.sideMargin;
        g.layerHeightPx[StackGeometry.HOST] = Math.max(0, v.hostBottom - v.hostTop);
        g.keyboardPresent = v.kbLaid > 0;
        g.keyboardInColumn = v.kbLaid > 0;
        g.keyboardTopPx = v.columnTop;
        g.keyboardWidthPx = f.widthPx;
        g.keyboardHeightPx = v.kbLaid;
        g.belowKeyboardInColumnPx = v.underBlock + insets.stackPaddingPx;
        g.underShown = v.underLaidOut;
        g.underTopPx = v.underTop;
        g.underBottomPx = v.underBottom;
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.withKeyboardRest(new StackScene.Motion(),
            StackScene.keyboardTranslationPx(0f, v.kbDown), motion);
        StackScene.at(end, end, 0f, motion, g, v.frame);
        return v;
    }

    /** The host as drawn on the screen: laid out, moved by the transforms, cut by the clips. */
    private static Box drawnHost(Views v, int widthPx) {
        float shift = v.containerTop + v.frame.ty[StackScene.V_CONTAINER];
        float top = shift + v.hostTop + v.frame.ty[StackScene.V_HOST];
        float bottom = top + (v.hostBottom - v.hostTop);
        if (v.frame.clipped[StackScene.V_HOST])
            bottom = Math.min(bottom, top + v.frame.clipBottom[StackScene.V_HOST]);
        if (v.frame.clipped[StackScene.V_CONTAINER])
            bottom = Math.min(bottom, shift + v.frame.clipBottom[StackScene.V_CONTAINER]);
        return new Box(v.sideMargin, top, widthPx - v.sideMargin, bottom);
    }

    private static Box drawnRegion(Views v, int region, float left, float right) {
        float shift = v.containerTop + v.frame.ty[StackScene.V_CONTAINER];
        return new Box(left, shift + v.frame.regionTop[region], right,
            shift + v.frame.regionBottom[region]);
    }

    // ---- The shape model, built the way the activity builds it -----------------------------

    private ChromeShape shape(PageRef page, boolean up, boolean floating,
                              StackLayout.Input pass, DockLayout dock, float cornersPx) {
        PlaceLayout layout = f.layoutOf(page).withKeyboardShown(up);
        float status = StatusBarEdgeGeometry.thicknessPx(Edge.TOP, floating, false, f.density);
        float apps = bandOf(Element.APPS, dock, pass);
        float az = bandOf(Element.AZ, dock, pass);
        float keys = bandOf(Element.EXTRA_KEYS, dock, pass);
        float keyboard = up ? pass.keyboardHeightPx : 0f;
        return LiveChromeShape.of(layout, floating ? LayoutStyle.FLOATING : LayoutStyle.DOCKED,
            f.widthPx, f.heightPx, ChromeShapeModel.Thickness.of(status, status, apps, apps, az,
                az, keys, keys, keyboard), cornersPx,
            Math.max(0, pass.dock.rowsHalf().marginDp) * f.density, 0f, f.density, 1,
            ChromeShapeModel.SplitAxis.STACKED, 0f, null);
    }

    private static float cornersPx(StackLayout.Input pass, float density) {
        int dp = pass.dock.rowsHalf().cornersDp;
        if (dp < 0) dp = TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(null, true);
        return dp * density;
    }

    private com.termux.app.dock.DockStyle design(PageRef page) {
        return page.isHome() ? f.homeDesign : f.terminalDesign;
    }

    private static void assertBox(String at, Box want, @Nullable Box got) {
        assertNotNull(at + " missing, want " + want, got);
        assertEquals(at + " left", want.left, got.left, EPS);
        assertEquals(at + " top", want.top, got.top, EPS);
        assertEquals(at + " right", want.right, got.right, EPS);
        assertEquals(at + " bottom", want.bottom, got.bottom, EPS);
    }

    // ---- The parity ------------------------------------------------------------------------

    @Test public void everyRestIsWhereTodaysViewsAreLaidOutAndDrawn() {
        for (boolean floating : new boolean[] {false, true})
            for (boolean up : new boolean[] {false, true})
                for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL})
                    checkRest(page, up, floating);
    }

    private void checkRest(PageRef page, boolean up, boolean floating) {
        final String at = f.name + " " + page + " " + (floating ? "FLOATING" : "DOCKED") + " "
            + (up ? "kbUp" : "kbDown");
        StackLayout.Input pass = f.pass(page, up, floating);
        PlaceLayout layout = f.layoutOf(page);
        RestCards.Rest rest = new RestCards.Rest(pass, page, f.widthPx, f.heightPx, f.density,
            f.extraKeysRowPx, f.keyboardLaidOutPx(),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, false, f.density),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, true, false, f.density));
        rest.statusOnDockPlank = statusOnPlank(layout);
        RestCards cards = RestCards.of(rest);
        Views v = views(page, up, floating, pass);
        final int w = f.widthPx;
        final float cornersPx = cornersPx(pass, f.density);

        // The laid-out rects.
        float cTop = v.containerTop;
        assertBox(at + " container", new Box(0, cTop, w, cTop + v.containerH),
            v.containerLaidOut ? cards.laidOut(StackView.CONTAINER) : null);
        if (!v.containerLaidOut) assertNull(at, cards.laidOut(StackView.CONTAINER));
        if (v.hostLaidOut) {
            assertBox(at + " host laid out", new Box(v.sideMargin, cTop + v.hostTop,
                w - v.sideMargin, cTop + v.hostBottom), cards.laidOut(StackView.HOST));
        } else {
            assertNull(at + " host", cards.laidOut(StackView.HOST));
        }
        if (v.containerLaidOut && v.columnH > 0) {
            assertBox(at + " column", new Box(0, cTop + v.columnTop, w, cTop + v.columnBottom),
                cards.laidOut(StackView.KEYBOARD_COLUMN));
        } else {
            assertNull(at + " column", cards.laidOut(StackView.KEYBOARD_COLUMN));
        }
        if (v.underLaidOut) {
            assertBox(at + " under", new Box(0, cTop + v.underTop, w, cTop + v.underBottom),
                cards.laidOut(StackView.UNDER));
        } else {
            assertNull(at + " under", cards.laidOut(StackView.UNDER));
        }

        // The dock's host: the Peek card, or the dock's card.
        RestCards.Card dockCard = cards.card(CardId.DOCK);
        RestCards.Card peekCard = cards.card(CardId.PEEK);
        Box host = drawnHost(v, w);
        assertEquals(at + " peek card", v.hostLaidOut && v.peekStands, peekCard != null);
        assertEquals(at + " dock card", v.hostLaidOut && !v.peekStands, dockCard != null);
        if (peekCard != null) checkPeek(at, v, pass, layout, peekCard, host);
        if (dockCard != null) {
            assertBox(at + " dock box", host, dockCard.box);
            checkDock(at, v, page, up, floating, pass, layout, dockCard, cornersPx);
        }

        // The bands under the keyboard.
        RestCards.Card underCard = cards.card(CardId.UNDER);
        boolean wantsUnder = v.underLaidOut && !v.peekStands;
        assertEquals(at + " under card", wantsUnder, underCard != null);
        if (underCard != null) {
            int inset = Math.max(0, v.dock.horizontalInsetPx);
            assertBox(at + " under box", drawnRegion(v, StackEnd.UNDER, inset, w - inset),
                underCard.box);
            float radius = v.dock.capsule ? v.dock.capsuleCornerRadiusPx(
                Math.round(underCard.box.height())) : 0f;
            assertEquals(at + " under corners", radius, underCard.corners.topLeft, EPS);
            assertEquals(at + " under corners", radius, underCard.corners.bottomRight, EPS);
            EdgeKind side = floating ? EdgeKind.RIM : EdgeKind.SCREEN;
            assertEquals(at, floating ? EdgeKind.RIM : EdgeKind.JOIN,
                underCard.edge(Edge.TOP));
            assertEquals(at, side, underCard.edge(Edge.BOTTOM));
            assertEquals(at, side, underCard.edge(Edge.LEFT));
            assertEquals(at, side, underCard.edge(Edge.RIGHT));
        }

        // The keyboard.
        RestCards.Card keyboardCard = cards.card(CardId.KEYBOARD);
        boolean wantsKeyboard = up && v.kbLaid > 0 && layout.keyboardForm != KeyboardForm.FLOATING
            && !v.unified;
        assertEquals(at + " keyboard card", wantsKeyboard, keyboardCard != null);
        if (!up) assertFalse(at + " a down keyboard is cut whole", v.frame.visible(StackEnd.KEYBOARD));
        if (keyboardCard != null) checkKeyboard(at, v, floating, cornersPx, keyboardCard);

        // The status bar.
        RestCards.Card statusCard = cards.card(CardId.STATUS);
        boolean statusTop = EdgeStackPolicy.stack(layout, Edge.TOP).contains(Element.STATUS);
        assertEquals(at + " status card", statusTop, statusCard != null);
        if (statusCard != null) {
            int t = StatusBarEdgeGeometry.thicknessPx(Edge.TOP, floating, false, f.density);
            assertBox(at + " status", new Box(0, 0, w, t), statusCard.box);
            assertBox(at + " status laid out", new Box(0, 0, w, t),
                cards.laidOut(StackView.STATUS_BAR));
            ChromeShape shape = shape(page, up, floating, pass, v.dock, cornersPx);
            ChromeShape.Piece piece = shape.piece(PieceId.STATUS);
            assertNotNull(at, piece);
            assertEquals(at + " status corners", piece.corners, statusCard.corners);
            for (Edge e : Edge.values())
                assertEquals(at + " status " + e, piece.kind(e), statusCard.edge(e));
        } else {
            assertNull(at + " status laid out", cards.laidOut(StackView.STATUS_BAR));
        }
        assertEquals(at, page, cards.page());
    }

    private void checkPeek(String at, Views v, StackLayout.Input pass, PlaceLayout layout,
                           RestCards.Card peek, Box host) {
        // The apps band's top, up from where the rows stand, bands nearer the edge below it.
        float rowsBottom = v.columnTop;
        float below = 0f;
        float appsBand = 0f;
        List<Element> over = new ArrayList<>(EdgeStackPolicy.stack(layout, Edge.BOTTOM));
        over.removeAll(EdgeStackPolicy.underKeyboard(layout));
        for (Element e : over) {
            if (e == Element.APPS) {
                appsBand = bandOf(e, v.dock, pass);
                break;
            }
            below += bandOf(e, v.dock, pass);
        }
        float iconTop = rowsBottom - below - appsBand + v.dock.appsTopPaddingPx - v.hostTop;
        int slots = Math.max(1, pass.dock.pinnedSlotCount);
        float[] icons = new float[slots * 4];
        for (int i = 0; i < slots; i++) {
            icons[i * 4] = i * v.dock.slotIconPx;
            icons[i * 4 + 1] = iconTop;
            icons[i * 4 + 2] = (i + 1) * v.dock.slotIconPx;
            icons[i * 4 + 3] = iconTop + v.dock.slotIconPx;
        }
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(icons, slots, 0f, f.widthPx,
            Math.round(v.hostBottom - v.hostTop), f.widthPx,
            PeekGlassGeometry.screenBottomInHost(f.heightPx, v.containerTop + v.hostTop,
                v.frame.ty[StackScene.V_CONTAINER] + v.frame.ty[StackScene.V_HOST]), f.density);
        assertNotNull(at, card);
        assertBox(at + " peek", new Box(card.left, host.top + card.top, card.right, f.heightPx),
            peek.box);
        assertEquals(at, PeekGlassGeometry.sideInsetPx(f.widthPx), peek.box.left, EPS);
        assertEquals(at, card.radiusPx, peek.corners.topLeft, EPS);
        assertEquals(at, card.radiusPx, peek.corners.topRight, EPS);
        assertEquals(at, 0f, peek.corners.bottomLeft, 0f);
        assertEquals(at, EdgeKind.SCREEN, peek.edge(Edge.BOTTOM));
        assertEquals(at, EdgeKind.RIM, peek.edge(Edge.TOP));
        assertTrue(at, peek.pieces.contains(PieceId.APPS));
        assertEquals(at, DockPlacement.peekCardCarriesAz(layout, false),
            peek.pieces.contains(PieceId.AZ));
    }

    private void checkDock(String at, Views v, PageRef page, boolean up, boolean floating,
                           StackLayout.Input pass, PlaceLayout layout, RestCards.Card dock,
                           float cornersPx) {
        ChromeShape shape = shape(page, up, floating, pass, v.dock, cornersPx);
        List<PieceId> ids = new ArrayList<>();
        for (Element e : AccessoryStackLayoutPolicy.plankBands(
                EdgeStackPolicy.stack(layout, Edge.BOTTOM))) {
            if (!EdgeStackPolicy.standsUnderKeyboard(layout, e)) ids.add(PieceId.of(e));
        }
        if (v.unified) ids.add(PieceId.KEYBOARD);
        assertEquals(at + " pieces", ids, dock.pieces);
        if (floating) {
            float radius = Math.min(cornersPx, dock.box.height() / 2f);
            assertEquals(at + " corner", radius, dock.corners.topLeft, EPS);
            assertEquals(at + " corner", radius, dock.corners.bottomLeft, EPS);
            assertEquals(at + " corner vs the dock's own", v.dock.capsuleCornerRadiusPx(
                Math.round(v.hostBottom - v.hostTop)), dock.corners.topRight, EPS);
            for (Edge e : Edge.values()) assertEquals(at + " " + e, EdgeKind.RIM, dock.edge(e));
        } else {
            LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape, ids);
            assertNotNull(at, clip);
            assertEquals(at + " TL", clip.topLeft, dock.corners.topLeft, 0f);
            assertEquals(at + " TR", clip.topRight, dock.corners.topRight, 0f);
            assertEquals(at + " BR", clip.bottomRight, dock.corners.bottomRight, 0f);
            assertEquals(at + " BL", clip.bottomLeft, dock.corners.bottomLeft, 0f);
            int rim = LiveChromeShape.rimEdges(shape, ids);
            for (Edge e : Edge.values())
                assertEquals(at + " rim " + e, (rim & com.termux.app.chrome.ChromeEdgeRule.edgeBit(e)) != 0,
                    dock.edge(e).drawsRim());
        }
        // The shape model against where the host is laid out and drawn.
        ChromeShape.Piece first = null;
        for (PieceId id : ids) if (first == null) first = shape.piece(id);
        if (first == null) return;
        ChromeShape.Card card = shape.cardOf(first);
        if (card == null) return;
        String key = "DOCK shape≠layout: " + (floating ? "FLOATING" : "DOCKED") + " "
            + page + " " + (up ? "kbUp" : "kbDown");
        Box want = card.box;
        Box got = dock.box;
        note(key, "left", want.left, got.left);
        note(key, "top", want.top, got.top);
        note(key, "right", want.right, got.right);
        note(key, "bottom", want.bottom, got.bottom);
    }

    private static void note(String key, String edge, float shape, float layout) {
        if (Math.abs(shape - layout) <= SHAPE_EPS) return;
        FOUND.add(key + " " + edge);
    }

    private void checkKeyboard(String at, Views v, boolean floating, float cornersPx,
                               RestCards.Card kb) {
        Box drawn = drawnRegion(v, StackEnd.KEYBOARD, 0, f.widthPx);
        assertTrue(at + " keyboard drawn", v.frame.visible(StackEnd.KEYBOARD));
        if (floating) {
            int margin = DockLayoutPolicy.surfaceHorizontalInsetPx(
                TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                    f.pass(PageRef.TERMINAL, true, true).dock.rowsHalf().marginDp), true,
                f.density);
            int gap = UnderKeyboardBand.keyboardGapPx(f.density);
            assertBox(at + " keyboard card", new Box(margin, drawn.top + gap, f.widthPx - margin,
                drawn.bottom), kb.box);
            float radius = Math.min(cornersPx, kb.box.height() / 2f);
            assertEquals(at, radius, kb.corners.topLeft, EPS);
            assertEquals(at, radius, kb.corners.bottomRight, EPS);
            for (Edge e : Edge.values()) assertEquals(at + " " + e, EdgeKind.RIM, kb.edge(e));
        } else {
            assertBox(at + " keyboard card", drawn, kb.box);
            assertTrue(at, kb.corners.isSquare());
            assertEquals(at, EdgeKind.SCREEN, kb.edge(Edge.LEFT));
            assertEquals(at, EdgeKind.SCREEN, kb.edge(Edge.RIGHT));
            assertEquals(at, EdgeKind.JOIN, kb.edge(Edge.TOP));
            assertEquals(at, v.r.bandsUnderKeyboard ? EdgeKind.JOIN : EdgeKind.SCREEN,
                kb.edge(Edge.BOTTOM));
        }
    }
}
