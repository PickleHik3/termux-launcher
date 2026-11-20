package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.statusbar.StatusBarEdgeGeometry;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/** A handful of rests on pong, read card by card; the parity test covers every combination. */
public class RestCardsTest {

    private static final LayoutFixtures.Fixture PONG = LayoutFixtures.PONG;
    private static final LayoutFixtures.Fixture TRAY = PONG.withDesign(DockStyle.GLASS_TRAY);

    private static RestCards rest(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                  boolean floating) {
        return RestCards.of(new RestCards.Rest(f.pass(page, up, floating), page, f.widthPx,
            f.heightPx, f.density, f.extraKeysRowPx, f.keyboardLaidOutPx(),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, false, f.density),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, true, false, f.density)));
    }

    private static DockLayout dock(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                   boolean floating) {
        return StackLayout.layout(f.pass(page, up, floating), new DockGeometry()).dock;
    }

    private static float cornersPx(LayoutFixtures.Fixture f, PageRef page, boolean floating) {
        return RestCards.cornersPx(f.pass(page, false, floating).dock.rowsHalf(), f.density);
    }

    private static DockStyle design(LayoutFixtures.Fixture f, PageRef page) {
        return page.isHome() ? f.homeDesign : f.terminalDesign;
    }

    @Test public void dockedTrayOnHomeWithTheKeyboardDownIsOneFlushCardAtTheScreen() {
        RestCards cards = rest(TRAY, PageRef.HOME, false, false);
        RestCards.Card dock = cards.card(CardId.DOCK);
        assertNotNull(dock);
        assertNull(cards.card(CardId.PEEK));
        assertNull(cards.card(CardId.KEYBOARD));
        assertEquals(StackView.HOST, dock.view);
        assertEquals(0f, dock.box.left, 0f);
        assertEquals(PONG.widthPx, dock.box.right, 0f);
        assertEquals(PONG.heightPx, dock.box.bottom, 0f);
        float corners = cornersPx(TRAY, PageRef.HOME, false);
        assertEquals(corners, dock.corners.topLeft, 0f);
        assertEquals(corners, dock.corners.topRight, 0f);
        assertEquals(0f, dock.corners.bottomLeft, 0f);
        assertEquals(0f, dock.corners.bottomRight, 0f);
        assertEquals(EdgeKind.SCREEN, dock.edge(Edge.BOTTOM));
        assertEquals(EdgeKind.SCREEN, dock.edge(Edge.LEFT));
        assertEquals(EdgeKind.SCREEN, dock.edge(Edge.RIGHT));
    }

    @Test public void floatingTrayOnTheTerminalIsACardInsetByMarginWithItsRimAllRound() {
        RestCards cards = rest(TRAY, PageRef.TERMINAL, false, true);
        RestCards.Card dock = cards.card(CardId.DOCK);
        assertNotNull(dock);
        DockLayout layout = dock(TRAY, PageRef.TERMINAL, false, true);
        assertEquals(layout.horizontalInsetPx, dock.box.left, 0f);
        assertEquals(PONG.widthPx - layout.horizontalInsetPx, dock.box.right, 0f);
        float radius = Math.min(cornersPx(TRAY, PageRef.TERMINAL, true), dock.box.height() / 2f);
        assertEquals(radius, dock.corners.topLeft, 0f);
        assertEquals(radius, dock.corners.topRight, 0f);
        assertEquals(radius, dock.corners.bottomRight, 0f);
        assertEquals(radius, dock.corners.bottomLeft, 0f);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, dock.edge(edge));
    }

    @Test public void floatingPeekOnHomeIsTheScreenWidePeekCardAndNoDockCard() {
        LayoutFixtures.Fixture peek = PONG.withDesign(DockStyle.PEEK_GLASS);
        RestCards cards = rest(peek, PageRef.HOME, false, true);
        RestCards.Card card = cards.card(CardId.PEEK);
        assertNotNull(card);
        assertNull(cards.card(CardId.DOCK));
        assertEquals(PeekGlassGeometry.sideInsetPx(PONG.widthPx), card.box.left, 0f);
        assertEquals(PONG.widthPx - PeekGlassGeometry.sideInsetPx(PONG.widthPx), card.box.right,
            0f);
        assertEquals(PONG.heightPx, card.box.bottom, 0f);
        float radius = PeekGlassGeometry.cornerRadiusPx(card.box.width(), PONG.density);
        assertEquals(radius, card.corners.topLeft, 0f);
        assertEquals(radius, card.corners.topRight, 0f);
        assertEquals(0f, card.corners.bottomLeft, 0f);
        assertEquals(EdgeKind.SCREEN, card.edge(Edge.BOTTOM));
        assertEquals(EdgeKind.RIM, card.edge(Edge.TOP));
        assertTrue(card.pieces.contains(ChromeShape.PieceId.APPS));
    }

    @Test public void floatingPeekOnTheTerminalIsTheFloatingDockCardSinceTheExtraKeysRefuseIt() {
        LayoutFixtures.Fixture peek = PONG.withDesign(DockStyle.PEEK_GLASS);
        RestCards cards = rest(peek, PageRef.TERMINAL, false, true);
        assertNull(cards.card(CardId.PEEK));
        RestCards.Card dock = cards.card(CardId.DOCK);
        assertNotNull(dock);
        assertEquals(EdgeKind.RIM, dock.edge(Edge.BOTTOM));
        assertTrue(dock.box.left > 0f);
    }

    @Test public void dockedWithTheKeyboardUpOneSurfaceCarriesTheKeyboardSoItHasNoCardOfItsOwn() {
        for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
            RestCards cards = rest(TRAY, page, true, false);
            RestCards.Card dock = cards.card(CardId.DOCK);
            if (dock == null) continue;
            if (!EdgeStackPolicy.underKeyboard(PONG.layoutOf(page)).isEmpty()) continue;
            assertNull(page + " keyboard card", cards.card(CardId.KEYBOARD));
            assertTrue(page + " carries the keyboard",
                dock.pieces.contains(ChromeShape.PieceId.KEYBOARD));
            assertEquals(PONG.heightPx, dock.box.bottom, 0f);
        }
    }

    @Test public void theKeyboardsOwnCardStandsWhenTheDockIsFloating() {
        RestCards cards = rest(TRAY, PageRef.TERMINAL, true, true);
        RestCards.Card keyboard = cards.card(CardId.KEYBOARD);
        assertNotNull(keyboard);
        assertEquals(StackView.KEYBOARD_COLUMN, keyboard.view);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, keyboard.edge(edge));
    }

    /** The stack's height is the bands and the keyboard laid out, on every layout and Style. */
    @Test public void theBandsAndTheKeyboardSumToTheStacksHeightOnEveryFixture() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.allDesigns())
            for (boolean floating : new boolean[] {false, true})
                for (boolean up : new boolean[] {false, true})
                    for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
            StackLayout.Input pass = f.pass(page, up, floating);
            StackLayout.Result r = StackLayout.layout(pass, new DockGeometry());
            PlaceLayout layout = pass.dock.arrangement;
            int rows = 0;
            if (pass.toolbarShown) {
                for (Element e : EdgeStackPolicy.stack(layout, Edge.BOTTOM))
                    rows += RestCards.bandPx(e, r.dock, pass);
            }
            int air = r.underKeyboardTopMarginPx + r.underKeyboardBottomMarginPx;
            int total = rows + r.statusBandPx + air + r.flushPaddingPx + pass.keyboardHeightPx;
            assertEquals(f.name + " " + page + " floating=" + floating + " up=" + up,
                r.stackHeightPx, total);
            assertEquals(f.name + " rows are the dock's combined rows",
                pass.toolbarShown ? r.dock.combinedHeight(pass.toolbarHeightPx,
                    pass.extraKeysRowEnabled) : 0, rows);
        }
    }
}
