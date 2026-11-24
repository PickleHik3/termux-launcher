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
import com.termux.app.dock.DockSpec;
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
import com.termux.app.terminal.inappkeyboard.FloatingKeyboardGeometry;
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
     *   <li>Docked Home bottom, keyboard up, with a foot: Home's keyboard covers the dock (#309),
     *       which stands at its keyboard-down rect and stops at the foot's top, while the shape
     *       model's one card runs on to the screen's edge: pong with a foot 2412 vs 2349.</li>
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
        "DOCK shape≠layout: DOCKED HOME kbUp bottom",
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
        "DOCK shape≠layout: FLOATING TERMINAL kbUp top",
        // The floating-form keyboard: the activity hands the shape model no frame (a null box),
        // so the model stands its default card (90 % of the opening's width, Margin above the
        // opening's bottom) while the frame is parked where FloatingKeyboardGeometry puts it.
        // floating keyboard fixture, frame [54,1742 - 1026,2412]: Docked model top and bottom
        // 2163 (the opening's bottom, an empty keyboard band); Floating model 82.35..997.65 wide,
        // top and bottom 2070.5.
        "KEYBOARD shape≠frame: DOCKED TERMINAL bottom",
        "KEYBOARD shape≠frame: DOCKED TERMINAL top",
        "KEYBOARD shape≠frame: FLOATING TERMINAL bottom",
        "KEYBOARD shape≠frame: FLOATING TERMINAL left",
        "KEYBOARD shape≠frame: FLOATING TERMINAL right",
        "KEYBOARD shape≠frame: FLOATING TERMINAL top",
        // The status bar under Floating: the shape model stands its card Margin in from the
        // screen's edges (pong: [31.5,31.5 - 1048.5,283.5]) while the bar is laid out full width
        // from the screen's top ([0,0 - 1080,252]); Docked they agree.
        "STATUS shape≠layout: FLOATING HOME kbDown bottom",
        "STATUS shape≠layout: FLOATING HOME kbDown left",
        "STATUS shape≠layout: FLOATING HOME kbDown right",
        "STATUS shape≠layout: FLOATING HOME kbDown top",
        "STATUS shape≠layout: FLOATING HOME kbUp bottom",
        "STATUS shape≠layout: FLOATING HOME kbUp left",
        "STATUS shape≠layout: FLOATING HOME kbUp right",
        "STATUS shape≠layout: FLOATING HOME kbUp top",
        "STATUS shape≠layout: FLOATING TERMINAL kbDown bottom",
        "STATUS shape≠layout: FLOATING TERMINAL kbDown left",
        "STATUS shape≠layout: FLOATING TERMINAL kbDown right",
        "STATUS shape≠layout: FLOATING TERMINAL kbDown top",
        "STATUS shape≠layout: FLOATING TERMINAL kbUp bottom",
        "STATUS shape≠layout: FLOATING TERMINAL kbUp left",
        "STATUS shape≠layout: FLOATING TERMINAL kbUp right",
        "STATUS shape≠layout: FLOATING TERMINAL kbUp top"));

    private static final Set<String> FOUND = new TreeSet<>();
    /** One case per key found: the fixture and the two numbers, for the message. */
    private static final java.util.Map<String, String> CASES = new java.util.TreeMap<>();

    @BeforeClass public static void clearFound() {
        FOUND.clear();
        CASES.clear();
    }

    @AfterClass public static void everyDisagreementIsNamedAndEveryNameStillDisagrees() {
        StringBuilder msg = new StringBuilder("parity exceptions found vs named");
        for (String key : FOUND)
            if (!PARITY_EXCEPTIONS.contains(key)) msg.append("\n  ").append(CASES.get(key));
        assertEquals(msg.toString(), PARITY_EXCEPTIONS, FOUND);
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        List<Object[]> out = new ArrayList<>();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures()) out.add(new Object[] {f});
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
        boolean dockFoot;
        int containerTop, containerH, slack, kbDown, kbLaid, kbFoot, underRows, underBlock;
        int columnTop, columnH, columnBottom;
        int hostTop, hostBottom, sideMargin;
        int underTop, underBottom;
        /** How far below the container Home's rows are drawn while its keyboard covers them. */
        float rowsDrop;
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
        v.peekStands = DockPlacement.peekCardStandsOn(page.kind, pass.homeKeyboardAlwaysOn,
            DockSurfaceSpec.forStyle(design(page)).peek, pass.toolbarShown, layout, false,
            up, pass.imeLiftPx > 0, v.dock.promptFieldBandPx > 0);
        v.floats = DockPlacement.floats(floating, floating && v.peekStands);
        // The kept band: the dock's host owns it as its foot (DockSpec), or the Docked keyboard
        // wears it under its keys while it is laid out with nothing under it.
        boolean floatingForm = layout.keyboardForm == KeyboardForm.FLOATING;
        v.dockFoot = DockSpec.footExists(pass.toolbarShown, v.floats, up, false, f.keptBandPx);
        boolean keyboardFoot = !floating && !floatingForm && !v.r.bandsUnderKeyboard
            && f.keyboardLaidOutPx() > 0;
        BottomInsets insets = BottomInsets.forKept(0, f.keptBandPx, v.dockFoot,
            v.r.bottomMarginPx, v.r.flushBottomInsetPx, keyboardFoot);
        v.kbDown = up ? 0 : f.keyboardLaidOutPx();
        v.slack = laneSlack(page, pass.dock) + Math.max(0,
            f.settle(PageRef.TERMINAL, false, floating).flushPaddingPx - v.r.flushPaddingPx);
        v.containerH = v.r.stackHeightPx + insets.stackExtraHeightPx + insets.keyboardFootPx
            + v.kbDown + v.slack;
        v.containerTop = f.heightPx - insets.stackMarginPx - v.containerH;
        v.containerLaidOut = v.r.stackShown || v.kbDown > 0;
        v.kbLaid = up ? pass.keyboardHeightPx : v.kbDown;
        v.kbFoot = v.kbLaid > 0 ? insets.keyboardFootPx : 0;
        if (v.r.bandsUnderKeyboard)
            for (Element e : EdgeStackPolicy.underKeyboard(layout))
                v.underRows += bandOf(e, v.dock, pass);
        v.underBlock = v.r.bandsUnderKeyboard ? v.r.underKeyboardTopMarginPx + v.underRows
            + v.r.underKeyboardBottomMarginPx : 0;
        v.columnH = v.kbLaid + v.kbFoot + v.underBlock;
        v.columnBottom = v.containerH - insets.stackPaddingPx - insets.keyboardColumnMarginPx;
        v.columnTop = v.columnBottom - v.columnH;
        v.sideMargin = DockHostBounds.sideMarginPx(v.floats, v.dock.horizontalInsetPx);
        int statusAbove = statusOnPlank(layout) ? 0 : v.r.statusBandPx;
        v.unified = !v.r.bandsUnderKeyboard
            && ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(pass.toolbarShown, up,
                floating, true, HomeKeyboardCover.liftsRows(page.kind, pass.homeKeyboardAlwaysOn));
        v.rowsDrop = HomeKeyboardCover.rowsDropPx(
            HomeKeyboardCover.liftsRows(page.kind, pass.homeKeyboardAlwaysOn), v.kbLaid,
            StackScene.keyboardTranslationPx(0f, v.kbDown));
        v.hostTop = statusAbove + v.dock.slabTopInsetPx + v.slack;
        v.hostBottom = v.unified || v.dockFoot ? v.containerH : v.columnTop;
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
        g.keyboardHeightPx = v.kbLaid + v.kbFoot;
        g.keyboardFootPx = v.kbFoot;
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

    /** The extra-keys row's top as drawn: on the column's top, the bands nearer the edge under it. */
    private static float drawnKeysRowTop(Views v, PlaceLayout layout, StackLayout.Input pass) {
        float below = 0f;
        for (Element e : EdgeStackPolicy.overKeyboard(layout)) {
            if (e == Element.EXTRA_KEYS) break;
            below += bandOf(e, v.dock, pass);
        }
        return v.containerTop + v.frame.ty[StackScene.V_CONTAINER] + v.frame.ty[StackScene.V_ROWS]
            + v.columnTop - below - bandOf(Element.EXTRA_KEYS, v.dock, pass);
    }

    /** The host as drawn on the screen: laid out, moved by the transforms, cut by the clips. */
    private static Box drawnHost(Views v, int widthPx) {
        float shift = v.containerTop + v.frame.ty[StackScene.V_CONTAINER];
        float top = shift + v.hostTop + v.frame.ty[StackScene.V_HOST] + v.rowsDrop;
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
        // The activity measures the A-Z bar's own host off the dock (chromeThicknessPx): its
        // letter band there, the dock's band on it.
        float az = PlaceChromePolicy.azRowOnDock(layout) ? bandOf(Element.AZ, dock, pass)
            : PlaceChromePolicy.azRowShown(layout)
                ? com.termux.app.terminal.AccessoryStackLayoutPolicy.computeAzRowHeightPx(true,
                    false, false, f.density) : 0f;
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

    // ---- Two cards under Floating -------------------------------------------------------------

    /** {@code page}'s rest with both sides' status flags set the way the activity sets them. */
    private RestCards.Rest restWithOther(PageRef page, boolean up) {
        RestCards.Rest rest = f.cardsRest(page, up, true);
        rest.statusOnDockPlank = statusOnPlank(f.layoutOf(page));
        PageRef otherPage = page.isHome() ? PageRef.TERMINAL : PageRef.HOME;
        rest.other.statusOnDockPlank = statusOnPlank(f.layoutOf(otherPage));
        return rest;
    }

    private RestCards.Rest restAlone(PageRef page, boolean up) {
        RestCards.Rest rest = f.cardsRestOf(page, up, true);
        rest.statusOnDockPlank = statusOnPlank(f.layoutOf(page));
        return rest;
    }

    private static void assertSameCard(String at, RestCards.Card want, RestCards.Card got) {
        assertEquals(at + " id", want.id, got.id);
        assertBox(at + " " + want.id, want.box, got.box);
        assertEquals(at + " " + want.id + " corners", want.corners, got.corners);
        assertEquals(at + " " + want.id + " pieces", want.pieces, got.pieces);
        for (Edge e : Edge.values())
            assertEquals(at + " " + want.id + " " + e, want.edge(e), got.edge(e));
    }

    @Test public void whileApartEachSideKeepsItsCardAndTheContainerOneMargin() {
        final int w = f.widthPx;
        for (boolean up : new boolean[] {false, true}) {
            String at = f.name + " " + (up ? "kbUp" : "kbDown");
            RestCards homeRest = RestCards.of(restWithOther(PageRef.HOME, up));
            RestCards termRest = RestCards.of(restWithOther(PageRef.TERMINAL, up));
            RestCards homeSolo = RestCards.of(restAlone(PageRef.HOME, up));
            RestCards termSolo = RestCards.of(restAlone(PageRef.TERMINAL, up));
            assertEquals(at, termRest.apart(), homeRest.apart());
            if (!termRest.apart()) {
                // Together only where the Terminal's rest wears Home's Peek card (#305): byte for
                // byte the one side alone.
                assertNotNull(at, termSolo.card(CardId.PEEK));
                for (RestCards[] pair : new RestCards[][] {{homeRest, homeSolo},
                        {termRest, termSolo}}) {
                    assertTrue(at, pair[0].parked().isEmpty());
                    assertEquals(at, pair[1].cards().size(), pair[0].cards().size());
                    for (int i = 0; i < pair[1].cards().size(); i++)
                        assertSameCard(at, pair[1].cards().get(i), pair[0].cards().get(i));
                    for (StackView view : StackView.values())
                        assertEquals(at + " " + view, pair[1].laidOut(view),
                            pair[0].laidOut(view));
                }
                continue;
            }
            // (i) Home draws what it drew alone; the Terminal's dock card is the keys card, or
            // with rows beside the keys (#305) its own back host, the one host's card.
            StackView termView = RestCards.terminalCardView(true, f.window,
                statusOnPlank(f.window));
            boolean keysCard = termView == StackView.KEYS_CARD;
            assertEquals(at, homeSolo.cards().size(), homeRest.cards().size());
            for (int i = 0; i < homeSolo.cards().size(); i++)
                assertSameCard(at + " home", homeSolo.cards().get(i), homeRest.cards().get(i));
            assertEquals(at, termSolo.cards().size(), termRest.cards().size());
            for (int i = 0; i < termSolo.cards().size(); i++) {
                RestCards.Card want = termSolo.cards().get(i);
                RestCards.Card got = termRest.cards().get(i);
                if (want.id == CardId.DOCK && !up && keysCard) {
                    // The keys card is the keys band only (ADR 0032, the developer's 2026-11-21
                    // decision): with the keyboard down the capsule alone stands the flush
                    // padding taller, and the card does not. The keys stand where they stood in
                    // the capsule, under the flush padding's upper part, so the card's top is the
                    // capsule's that much lower; its sides are the capsule's.
                    assertEquals(at + " terminal DOCK id", want.id, got.id);
                    assertEquals(at + " terminal DOCK left", want.box.left, got.box.left, EPS);
                    assertTrue(at + " terminal DOCK top within the capsule",
                        got.box.top >= want.box.top - EPS);
                    assertEquals(at + " terminal DOCK right", want.box.right, got.box.right, EPS);
                    assertTrue(at + " terminal DOCK is never taller than the capsule",
                        got.box.bottom <= want.box.bottom + EPS);
                    assertEquals(at + " terminal DOCK pieces", want.pieces, got.pieces);
                } else {
                    assertSameCard(at + " terminal", want, got);
                }
                // An A-Z bar off the dock is the Terminal's own card on its own edge host unless
                // Home draws it at the same rect (#307 round 3).
                // The keyboard is the Terminal's own card on its own column unless both rests draw
                // it alike (#307 round 3).
                StackView wantView = want.view == StackView.HOST ? termView
                    : want.view == StackView.AZ_EDGE && !sameAz(homeRest, got)
                    ? StackView.TERMINAL_AZ_EDGE
                    : want.view == StackView.KEYBOARD_COLUMN
                        && termRest.laidOut(StackView.TERMINAL_KEYBOARD) != null
                    ? StackView.TERMINAL_KEYBOARD : want.view;
                assertEquals(at, wantView, got.view);
            }
            // Each side parks the other's glass, on screen it draws none of it; a Terminal with
            // no card at its rest (#305) has none to park.
            boolean termHostCard = false;
            for (RestCards.Card c : termSolo.cards()) termHostCard |= c.view == StackView.HOST;
            for (RestCards.Card c : termRest.cards())
                termHostCard |= StackEnd.sideOf(c.view, true) == com.termux.app.page.Side.TERMINAL.ordinal();
            assertEquals(at, !termHostCard, homeRest.parked().isEmpty());
            assertFalse(at, termRest.parked().isEmpty());
            // Each side's parked cards stand on that side's own views (#307: its A-Z edge host
            // and keyboard column too).
            for (RestCards.Card c : homeRest.parked())
                assertEquals(at + " " + c, com.termux.app.page.Side.TERMINAL.ordinal(),
                    StackEnd.sideOf(c.view, true));
            for (RestCards.Card c : termRest.parked())
                assertEquals(at + " " + c, com.termux.app.page.Side.HOME.ordinal(),
                    StackEnd.sideOf(c.view, true));
            for (RestCards.Card c : homeRest.cards())
                assertTrue(at + " Home draws no Terminal card", c.view != termView);
            StackLayout.Input termPass = f.pass(PageRef.TERMINAL, up, true);
            Views tv = views(PageRef.TERMINAL, up, true, termPass);
            if (keysCard) {
            // (ii) The keys card is the extra-keys row's band inset by the dock's side margin,
            // its corners Corners clamped to half its height, a rim on every edge.
            RestCards.Card keys = termRest.card(CardId.DOCK);
            assertNotNull(at, keys);
            assertEquals(at, StackView.KEYS_CARD, keys.view);
            int side = DockHostBounds.sideMarginPx(true, tv.dock.horizontalInsetPx);
            // One size in both keyboard states: the keys band only, never the flush padding's
            // lower half the capsule wears with the keyboard down.
            assertEquals(at + " keys card height",
                bandOf(Element.EXTRA_KEYS, tv.dock, termPass), keys.box.height(), EPS);
            assertEquals(at + " keys card left", side, keys.box.left, EPS);
            assertEquals(at + " keys card right", w - side, keys.box.right, EPS);
            // Where the keys row is drawn: on the column, above the bands nearer the edge.
            assertEquals(at + " keys card top", drawnKeysRowTop(tv, f.layoutOf(PageRef.TERMINAL),
                termPass), keys.box.top, EPS);
            float radius = Math.min(cornersPx(termPass, f.density), keys.box.height() / 2f);
            assertEquals(at + " keys card corners", radius, keys.corners.topLeft, EPS);
            assertEquals(at + " keys card corners", radius, keys.corners.bottomRight, EPS);
            for (Edge e : Edge.values())
                assertEquals(at + " keys card " + e, EdgeKind.RIM, keys.edge(e));
            assertEquals(at + " keys card pieces",
                java.util.Collections.singletonList(PieceId.EXTRA_KEYS), keys.pieces);
            }
            // (iii) A view is laid out the same at both rests.
            assertBox(at + " HOST laid out at both", homeRest.laidOut(StackView.HOST),
                termRest.laidOut(StackView.HOST));
            assertEquals(at + " KEYS_CARD laid out at both",
                String.valueOf(homeRest.laidOut(StackView.KEYS_CARD)),
                String.valueOf(termRest.laidOut(StackView.KEYS_CARD)));
            assertEquals(at + " TERMINAL_HOST laid out at both",
                String.valueOf(homeRest.laidOut(StackView.TERMINAL_HOST)),
                String.valueOf(termRest.laidOut(StackView.TERMINAL_HOST)));
            // (iv) One container margin, and Home's drawn cards that much lower than laid out.
            int homeMargin = Math.max(0, f.settle(PageRef.HOME, up, true).bottomMarginPx);
            int termMargin = Math.max(0, f.settle(PageRef.TERMINAL, up, true).bottomMarginPx);
            int margin = SideCards.containerMarginPx(homeMargin, termMargin, true, true);
            int drop = SideCards.homeSideDropPx(homeMargin, termMargin, true);
            assertEquals(at, termMargin, margin);
            assertEquals(at + " home container bottom", f.heightPx - margin,
                homeRest.laidOut(StackView.CONTAINER).bottom, EPS);
            assertEquals(at + " terminal container bottom", f.heightPx - margin,
                termRest.laidOut(StackView.CONTAINER).bottom, EPS);
            StackLayout.Input homePass = f.pass(PageRef.HOME, up, true);
            Views hv = views(PageRef.HOME, up, true, homePass);
            assertEquals(at + " home container drop", drop,
                hv.containerTop - homeRest.laidOut(StackView.CONTAINER).top, EPS);
            if (hv.hostLaidOut) {
                // Laid out, so without the container's keyboard-down share.
                assertEquals(at + " home host drop", drop,
                    hv.containerTop + hv.hostTop - homeRest.laidOut(StackView.HOST).top, EPS);
            }
            assertEquals(at + " terminal drop", 0f,
                tv.containerTop - termRest.laidOut(StackView.CONTAINER).top, EPS);
        }
    }

    private void checkRest(PageRef page, boolean up, boolean floating) {
        final String at = f.name + " " + page + " " + (floating ? "FLOATING" : "DOCKED") + " "
            + (up ? "kbUp" : "kbDown");
        StackLayout.Input pass = f.pass(page, up, floating);
        PlaceLayout layout = f.layoutOf(page);
        // One side alone: today's views, whatever the two-card rule says (checkApart holds that).
        RestCards.Rest rest = f.cardsRestOf(page, up, floating);
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
        // The foot: the kept band, worn by the host while it owns it or carries a keyboard that
        // wears it, or by the Docked keyboard's own host; never under a Peek card, which runs
        // past the screen's bottom itself.
        RestCards.Card foot = cards.card(CardId.FOOT);
        float footTop = f.heightPx - f.keptBandPx;
        boolean hostFoot = f.keptBandPx > 0 && v.hostLaidOut && !v.peekStands
            && host.bottom >= f.heightPx && (v.dockFoot || v.unified && v.kbFoot > 0);
        boolean wantsKeyboard = up && v.kbLaid > 0 && layout.keyboardForm != KeyboardForm.FLOATING
            && !v.unified;
        boolean keyboardFoot = f.keptBandPx > 0 && wantsKeyboard && v.kbFoot > 0;
        assertEquals(at + " foot card", hostFoot || keyboardFoot, foot != null);
        if (foot != null) {
            assertEquals(at + " foot view", hostFoot ? StackView.HOST : StackView.KEYBOARD_COLUMN,
                foot.view);
            float left = hostFoot ? v.sideMargin : 0f;
            assertBox(at + " foot", new Box(left, footTop, f.widthPx - left, f.heightPx),
                foot.box);
            assertTrue(at, foot.corners.isSquare());
            assertEquals(at, EdgeKind.JOIN, foot.edge(Edge.TOP));
            assertEquals(at, EdgeKind.SCREEN, foot.edge(Edge.BOTTOM));
            assertEquals(at, EdgeKind.SCREEN, foot.edge(Edge.LEFT));
            assertEquals(at, EdgeKind.SCREEN, foot.edge(Edge.RIGHT));
        }
        if (dockCard != null) {
            // With a foot the dock's card stops at the band, and the two together are the host.
            assertBox(at + " dock box", hostFoot ? new Box(host.left, host.top, host.right,
                footTop) : host, dockCard.box);
            checkDock(at, v, page, up, floating, pass, layout, dockCard, cornersPx,
                hostFoot ? f.heightPx : Float.NaN);
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

        // The keyboard: in the stack's column, or its floating frame.
        RestCards.Card keyboardCard = cards.card(CardId.KEYBOARD);
        boolean floatingForm = layout.keyboardForm == KeyboardForm.FLOATING;
        assertEquals(at + " keyboard card", wantsKeyboard || up && floatingForm,
            keyboardCard != null);
        if (!up) assertFalse(at + " a down keyboard is cut whole", v.frame.visible(StackEnd.KEYBOARD));
        if (keyboardCard != null && floatingForm) {
            checkFloatingForm(at, page, up, floating, pass, cornersPx, keyboardCard,
                cards.laidOut(StackView.FLOATING_KEYBOARD));
        } else if (keyboardCard != null) {
            checkKeyboard(at, v, page, up, floating, pass, layout, cornersPx, keyboardCard,
                keyboardFoot ? footTop : Float.NaN);
        } else {
            assertNull(at, cards.laidOut(StackView.FLOATING_KEYBOARD));
        }

        // The A-Z bar: where the layout stands it and which card carries it.
        checkAz(at, layout, cards, v, pass);

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
            // The shape model's own box for the bar against where the bar is laid out.
            String key = "STATUS shape≠layout: " + (floating ? "FLOATING" : "DOCKED") + " "
                + page + " " + (up ? "kbUp" : "kbDown");
            note(key, "left", piece.box.left, statusCard.box.left);
            note(key, "top", piece.box.top, statusCard.box.top);
            note(key, "right", piece.box.right, statusCard.box.right);
            note(key, "bottom", piece.box.bottom, statusCard.box.bottom);
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
        // The icons as the dock's dress pads the row (DockDress): with the ticks leading the row
        // (the row next to the canvas), the strip, then the air it leaves beside the ticks; else
        // the air. The icon is centred in what the two paddings leave of the row's view.
        boolean ticksLead = com.termux.app.launcher.paging.PageTickStrip.ticksLeadRow(Edge.BOTTOM,
            com.termux.app.dock.DockGeometry.appsRowNextToCanvas(layout));
        int strip = v.dock.appsRowStripBandPx;
        int beside = Math.max(0, v.dock.appsRowTickSideAirPx - strip);
        int padTop = ticksLead ? beside : v.dock.appsTopPaddingPx;
        int padBottom = ticksLead ? v.dock.appsBottomPaddingPx : beside;
        float rowView = Math.max(0, v.dock.appsRowBandPx - strip);
        float iconCentre = rowsBottom - below - appsBand + (ticksLead ? strip : 0) + padTop
            + Math.max(0f, rowView - padTop - padBottom) / 2f - v.hostTop;
        float iconTop = iconCentre - v.dock.slotIconPx / 2f;
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
                v.frame.ty[StackScene.V_CONTAINER] + v.frame.ty[StackScene.V_HOST]
                    + v.rowsDrop), f.density);
        assertNotNull(at, card);
        // The card's top line is the icons' centre line as the row draws them, and the
        // reflection hangs from their lower edge: mirrored there, each icon's reflection is
        // centred one icon below its centre.
        assertEquals(at + " peek top is the icons' centre", host.top + iconCentre, peek.box.top, EPS);
        assertEquals(at + " reflection centred an icon below", iconCentre + v.dock.slotIconPx,
            2f * card.mirrorAxisY - iconCentre, EPS);
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
                           float cornersPx, float footBottom) {
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
        // The shape model has no foot: its card is the dock's and the foot's together.
        Box got = Float.isNaN(footBottom) ? dock.box
            : new Box(dock.box.left, dock.box.top, dock.box.right, footBottom);
        note(key, "left", want.left, got.left);
        note(key, "top", want.top, got.top);
        note(key, "right", want.right, got.right);
        note(key, "bottom", want.bottom, got.bottom);
    }

    private void note(String key, String edge, float shape, float layout) {
        if (Math.abs(shape - layout) <= SHAPE_EPS) return;
        FOUND.add(key + " " + edge);
        if (!CASES.containsKey(key + " " + edge))
            CASES.put(key + " " + edge, key + " " + edge + " :: " + f.name + " " + shape + " vs "
                + layout);
    }

    private void checkKeyboard(String at, Views v, PageRef page, boolean up, boolean floating,
                               StackLayout.Input pass, PlaceLayout layout, float cornersPx,
                               RestCards.Card kb, float footTop) {
        Box drawn = drawnRegion(v, StackEnd.KEYBOARD, 0, f.widthPx);
        if (!Float.isNaN(footTop)) drawn = new Box(drawn.left, drawn.top, drawn.right, footTop);
        assertTrue(at + " keyboard drawn", v.frame.visible(StackEnd.KEYBOARD));
        boolean split = layout.keyboardForm == KeyboardForm.SPLIT;
        List<PieceId> ids = split ? Arrays.asList(PieceId.KEYBOARD_LEFT, PieceId.KEYBOARD_RIGHT)
            : java.util.Collections.singletonList(PieceId.KEYBOARD);
        assertEquals(at + " keyboard pieces", ids, kb.pieces);
        // The host's side margin and top gap as the activity dresses the host.
        int margin = com.termux.app.chrome.ChromePolicy.keyboardHostSideMarginPx(false,
            TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                pass.dock.rowsHalf().marginDp), floating, f.density);
        int gap = com.termux.app.chrome.ChromePolicy.keyboardHostTopMarginPx(floating, false,
            f.density);
        if (floating) {
            assertEquals(at + " keyboard gap", UnderKeyboardBand.keyboardGapPx(f.density), gap);
            assertEquals(at + " keyboard side inset", DockLayoutPolicy.surfaceHorizontalInsetPx(
                TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                    pass.dock.rowsHalf().marginDp), true, f.density), margin);
            assertBox(at + " keyboard card", new Box(margin, drawn.top + gap, f.widthPx - margin,
                drawn.bottom), kb.box);
            float radius = Math.min(cornersPx, kb.box.height() / 2f);
            assertEquals(at, radius, kb.corners.topLeft, EPS);
            assertEquals(at, radius, kb.corners.bottomRight, EPS);
            for (Edge e : Edge.values()) assertEquals(at + " " + e, EdgeKind.RIM, kb.edge(e));
        } else {
            assertEquals(at + " docked keyboard is flush", 0, margin);
            assertEquals(at + " docked keyboard has no gap", 0, gap);
            assertBox(at + " keyboard card", drawn, kb.box);
            // Docked, the keyboard is a slice of the frame: its corners and edges are the shape
            // model's, as the activity clips and rims its host (chromeClipOf, KEYBOARD_PIECES).
            ChromeShape shape = shape(page, up, false, pass, v.dock, cornersPx);
            LiveChromeShape.Clip clip = LiveChromeShape.outlineOf(shape, ids);
            assertNotNull(at, clip);
            assertEquals(at + " TL", clip.topLeft, kb.corners.topLeft, 0f);
            assertEquals(at + " BR", clip.bottomRight, kb.corners.bottomRight, 0f);
            int rim = LiveChromeShape.rimEdges(shape, ids);
            for (Edge e : Edge.values())
                assertEquals(at + " keyboard rim " + e,
                    (rim & com.termux.app.chrome.ChromeEdgeRule.edgeBit(e)) != 0,
                    kb.edge(e).drawsRim());
            assertEquals(at, EdgeKind.SCREEN, kb.edge(Edge.LEFT));
            assertEquals(at, EdgeKind.SCREEN, kb.edge(Edge.RIGHT));
            // Joined to the rows over it; a rim with nothing over the keyboard (its keys under
            // it, #305's fixture).
            if (!EdgeStackPolicy.overKeyboard(f.layoutOf(page)).isEmpty())
                assertEquals(at, EdgeKind.JOIN, kb.edge(Edge.TOP));
            // Under the keyboard stand the bands, or the foot the keyboard's card joins (Home's
            // Docked keyboard has a card of its own since #309, and a foot with the kept band).
            assertEquals(at, v.r.bandsUnderKeyboard || !Float.isNaN(footTop)
                ? EdgeKind.JOIN : EdgeKind.SCREEN, kb.edge(Edge.BOTTOM));
        }
    }

    /**
     * The floating-form keyboard: its frame where FloatingKeyboardGeometry parks it in its room,
     * as FloatingKeyboardController composes it. The activity hands the shape model no frame (a
     * null box), so the model's own keyboard card is a second, disagreeing source.
     */
    private void checkFloatingForm(String at, PageRef page, boolean up, boolean floating,
                                   StackLayout.Input pass, float cornersPx, RestCards.Card kb,
                                   @Nullable Box laidOut) {
        Box room = f.floatingKeyboardRoom();
        int roomW = Math.round(room.width());
        int frameW = FloatingKeyboardGeometry.frameWidthPx(roomW,
            LayoutFixtures.FLOATING_KEYBOARD_WIDTH_SCALE, 0);
        float left = room.left + FloatingKeyboardGeometry.positionPx(
            FloatingKeyboardGeometry.DEFAULT_X_FRACTION,
            FloatingKeyboardGeometry.travelPx(roomW, frameW));
        float top = room.top + FloatingKeyboardGeometry.positionPx(
            FloatingKeyboardGeometry.DEFAULT_Y_FRACTION,
            FloatingKeyboardGeometry.travelPx(Math.round(room.height()), f.keyboardPx));
        Box want = new Box(left, top, left + frameW, top + f.keyboardPx);
        assertBox(at + " floating keyboard", want, kb.box);
        assertBox(at + " floating keyboard laid out", want, laidOut);
        assertEquals(at, StackView.FLOATING_KEYBOARD, kb.view);
        for (Edge e : Edge.values()) assertEquals(at + " " + e, EdgeKind.RIM, kb.edge(e));
        // The shape model handed this frame gives the card's corners.
        PlaceLayout layout = f.layoutOf(page).withKeyboardShown(true);
        ChromeShapeModel.Thickness thickness = ChromeShapeModel.Thickness.of(0, 0, 0, 0, 0, 0, 0,
            0, f.keyboardPx);
        LayoutStyle style = floating ? LayoutStyle.FLOATING : LayoutStyle.DOCKED;
        float marginPx = Math.max(0, pass.dock.rowsHalf().marginDp) * f.density;
        ChromeShape.Piece piece = ChromeShapeModel.shape(ChromeShapeModel.input(layout, style,
            f.widthPx, f.heightPx, thickness).corners(cornersPx).margin(marginPx)
            .floatingKeyboardBox(want).build()).piece(PieceId.KEYBOARD);
        assertNotNull(at, piece);
        assertEquals(at + " floating keyboard corners", piece.corners, kb.corners);
        // What the activity's shape model says with no frame handed to it.
        StackLayout.Input upPass = f.pass(page, true, floating);
        ChromeShape.Piece blind = shape(page, true, floating, upPass,
            StackLayout.layout(upPass, new DockGeometry()).dock, cornersPx)
            .piece(PieceId.KEYBOARD);
        if (blind == null) return;
        String key = "KEYBOARD shape≠frame: " + (floating ? "FLOATING" : "DOCKED") + " "
            + page;
        note(key, "left", blind.box.left, kb.box.left);
        note(key, "top", blind.box.top, kb.box.top);
        note(key, "right", blind.box.right, kb.box.right);
        note(key, "bottom", blind.box.bottom, kb.box.bottom);
    }

    /**
     * The A-Z bar: its placement is what PlaceChromePolicy reads off the layout; on the dock's
     * stack it rides the card whose rows it stands among (the dock's over the keyboard, the bands'
     * under it, or the Peek card when it carries the row), and a tab, a hidden bar or one off the
     * stack has no card here.
     */
    private void checkAz(String at, PlaceLayout layout, RestCards cards, Views v,
                         StackLayout.Input pass) {
        RestCards.AzPlacement want = PlaceChromePolicy.azTabShown(layout)
            ? RestCards.AzPlacement.TAB
            : PlaceChromePolicy.azRowOnDock(layout) ? RestCards.AzPlacement.DOCK_ROW
            : PlaceChromePolicy.azRowShown(layout) ? RestCards.AzPlacement.EDGE
            : RestCards.AzPlacement.HIDDEN;
        assertEquals(at + " a-z placement", want, cards.azPlacement());
        RestCards.Card carrier = cards.carrierOf(PieceId.AZ);
        CardId wantCarrier = null;
        if (want == RestCards.AzPlacement.DOCK_ROW && v.hostLaidOut) {
            boolean under = EdgeStackPolicy.standsUnderKeyboard(layout, Element.AZ);
            if (v.peekStands) {
                if (DockPlacement.peekCardCarriesAz(layout, false)) wantCarrier = CardId.PEEK;
            } else if (under) {
                if (v.underLaidOut) wantCarrier = CardId.UNDER;
            } else {
                wantCarrier = CardId.DOCK;
            }
        } else if (want == RestCards.AzPlacement.EDGE) {
            // Off the dock the bar stands on its own edge host with a glass sheet of its own
            // (place_az_bar_host), a card of its own (#307).
            wantCarrier = CardId.AZ;
        }
        assertEquals(at + " a-z carrier", wantCarrier, carrier == null ? null : carrier.id);
        // A carrier on the stack stands on the dock's A-Z band; the edge card is its host's own.
        if (carrier != null && carrier.id != CardId.PEEK && carrier.id != CardId.AZ)
            assertTrue(at + " a-z band laid out", bandOf(Element.AZ, v.dock, pass) > 0);
    }

    /** Whether Home's rest draws the A-Z card at {@code card}'s rect: one shared card (#307). */
    private static boolean sameAz(@androidx.annotation.NonNull RestCards home,
                                  @androidx.annotation.NonNull RestCards.Card card) {
        RestCards.Card az = home.card(CardId.AZ);
        return az != null && az.box.equals(card.box);
    }

}
