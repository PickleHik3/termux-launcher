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
import com.termux.app.wall.PageRef;

import org.junit.Test;

/** A handful of rests on pong, read card by card; the parity test covers every combination. */
public class RestCardsTest {

    private static final LayoutFixtures.Fixture PONG = LayoutFixtures.PONG;
    private static final LayoutFixtures.Fixture TRAY = PONG.withDesign(DockStyle.GLASS_TRAY);

    private static RestCards rest(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                  boolean floating) {
        return RestCards.of(f.cardsRest(page, up, floating));
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

    /**
     * The Terminal's dock and keyboard are one surface (Home's are not since #309: its keyboard
     * covers the dock, see {@link #homeKeyboardUpDrawsOverTheDockAtItsKeyboardDownRect}).
     */
    @Test public void dockedWithTheKeyboardUpOneSurfaceCarriesTheKeyboardSoItHasNoCardOfItsOwn() {
        for (PageRef page : new PageRef[] {PageRef.TERMINAL}) {
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

    private static RestCards homeRest(LayoutFixtures.Fixture f, boolean up, boolean floating,
                                      boolean alwaysOn) {
        RestCards.Rest rest = f.cardsRest(PageRef.HOME, up, floating);
        rest.pass.homeKeyboardAlwaysOn = alwaysOn;
        return RestCards.of(rest);
    }

    private static RestCards.Card dockOrPeek(RestCards cards) {
        RestCards.Card card = cards.card(CardId.DOCK);
        return card != null ? card : cards.card(CardId.PEEK);
    }

    /** #309: Home's rows stand at their keyboard-down rect and the keyboard's card covers them. */
    @Test public void homeKeyboardUpDrawsOverTheDockAtItsKeyboardDownRect() {
        for (LayoutFixtures.Fixture f : new LayoutFixtures.Fixture[] {TRAY,
                PONG.withDesign(DockStyle.PEEK_GLASS)})
            for (boolean floating : new boolean[] {false, true}) {
                String at = f.name + " " + f.homeDesign + (floating ? " floating" : " docked");
                RestCards down = homeRest(f, false, floating, false);
                RestCards up = homeRest(f, true, floating, false);
                RestCards.Card downDock = dockOrPeek(down);
                RestCards.Card upDock = dockOrPeek(up);
                assertNotNull(at, downDock);
                assertNotNull(at, upDock);
                assertEquals(at + " same card", downDock.id, upDock.id);
                assertEquals(at + " top", downDock.box.top, upDock.box.top, 0.01f);
                assertEquals(at + " left", downDock.box.left, upDock.box.left, 0f);
                assertEquals(at + " right", downDock.box.right, upDock.box.right, 0f);
                assertEquals(at + " drop down", 0f, down.rowsDropPx(), 0f);
                assertTrue(at + " drop up", up.rowsDropPx() > 0f);
                assertTrue(at + " nothing covered while down", down.covered().isEmpty());
                RestCards.Card keyboard = up.card(CardId.KEYBOARD);
                assertNotNull(at + " the keyboard's own card", keyboard);
                assertNull(at + " not one surface", dockCarriesKeyboard(up));
                boolean coversDock = false;
                for (RestCards.Card c : up.covered()) coversDock |= c.id == upDock.id;
                assertTrue(at + " covers the dock's card", coversDock);
                assertTrue(at + " the card runs under the keyboard's",
                    upDock.box.bottom > keyboard.box.top);
            }
    }

    private static RestCards.Card dockCarriesKeyboard(RestCards cards) {
        RestCards.Card dock = cards.card(CardId.DOCK);
        return dock != null && dock.pieces.contains(ChromeShape.PieceId.KEYBOARD) ? dock : null;
    }

    /** The cover applies only to a Home whose keyboard is raised on demand. */
    @Test public void anAlwaysOnHomeKeepsRidingTheKeyboard() {
        RestCards up = homeRest(TRAY, true, false, true);
        assertEquals(0f, up.rowsDropPx(), 0f);
        assertTrue(up.covered().isEmpty());
        assertNotNull(dockCarriesKeyboard(up));
    }

    @Test public void theKeyboardsOwnCardStandsWhenTheDockIsFloating() {
        RestCards cards = rest(TRAY, PageRef.TERMINAL, true, true);
        RestCards.Card keyboard = cards.card(CardId.KEYBOARD);
        assertNotNull(keyboard);
        // The Terminal's keyboard card is its own column's while the cards stand apart (#307).
        assertEquals(cards.apart() ? StackView.TERMINAL_KEYBOARD : StackView.KEYBOARD_COLUMN,
            keyboard.view);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, keyboard.edge(edge));
    }

    @Test public void dockedTrayOnHomeWithAKeptBandStandsTheFootUnderTheDockCard() {
        LayoutFixtures.Fixture foot = LayoutFixtures.TL16.withFoot(LayoutFixtures.KEPT_BAND_PX,
            LayoutFixtures.FLUSH_PX).withDesign(DockStyle.GLASS_TRAY);
        RestCards cards = rest(foot, PageRef.HOME, false, false);
        RestCards.Card dock = cards.card(CardId.DOCK);
        RestCards.Card card = cards.card(CardId.FOOT);
        assertNotNull(dock);
        assertNotNull(card);
        float top = foot.heightPx - LayoutFixtures.KEPT_BAND_PX;
        assertEquals(top, dock.box.bottom, 0f);
        assertEquals(top, card.box.top, 0f);
        assertEquals(foot.heightPx, card.box.bottom, 0f);
        assertEquals(StackView.HOST, card.view);
        assertEquals(EdgeKind.JOIN, card.edge(Edge.TOP));
        assertEquals(EdgeKind.SCREEN, card.edge(Edge.BOTTOM));
    }

    @Test public void theFootIsOneRectOnHomeAndTheTerminalKeyboardDown() {
        LayoutFixtures.Fixture foot = LayoutFixtures.TL16.withFoot(LayoutFixtures.KEPT_BAND_PX,
            LayoutFixtures.FLUSH_PX).withDesign(DockStyle.GLASS_TRAY);
        RestCards.Card home = rest(foot, PageRef.HOME, false, false).card(CardId.FOOT);
        RestCards.Card terminal = rest(foot, PageRef.TERMINAL, false, false).card(CardId.FOOT);
        assertNotNull(home);
        assertNotNull(terminal);
        assertEquals(home.box.toString(), terminal.box.toString());
    }

    @Test public void aFloatingDockOwnsNoFoot() {
        LayoutFixtures.Fixture foot = LayoutFixtures.TL16.withFoot(LayoutFixtures.KEPT_BAND_PX,
            LayoutFixtures.FLUSH_PX).withDesign(DockStyle.GLASS_TRAY);
        assertNull(rest(foot, PageRef.TERMINAL, false, true).card(CardId.FOOT));
    }

    @Test public void theFloatingFormKeyboardIsItsOwnFrameParkedAtTheBottomOfItsRoom() {
        LayoutFixtures.Fixture f = LayoutFixtures.FLOATING_KEYBOARD;
        RestCards cards = rest(f, PageRef.TERMINAL, true, false);
        RestCards.Card keyboard = cards.card(CardId.KEYBOARD);
        assertNotNull(keyboard);
        assertEquals(StackView.FLOATING_KEYBOARD, keyboard.view);
        assertEquals(f.heightPx, keyboard.box.bottom, 0f);
        assertEquals(Math.round(f.widthPx * LayoutFixtures.FLOATING_KEYBOARD_WIDTH_SCALE),
            keyboard.box.width(), 0f);
        assertEquals((f.widthPx - keyboard.box.width()) / 2f, keyboard.box.left, 1f);
        for (Edge edge : Edge.values()) assertEquals(EdgeKind.RIM, keyboard.edge(edge));
        assertNull(rest(f, PageRef.TERMINAL, false, false).card(CardId.KEYBOARD));
    }

    @Test public void theSplitKeyboardIsOneHostAcrossItsTwoHalves() {
        RestCards cards = rest(LayoutFixtures.SPLIT_KEYBOARD, PageRef.TERMINAL, true, false);
        RestCards.Card keyboard = cards.card(CardId.KEYBOARD);
        assertNotNull(keyboard);
        assertEquals(java.util.Arrays.asList(ChromeShape.PieceId.KEYBOARD_LEFT,
            ChromeShape.PieceId.KEYBOARD_RIGHT), keyboard.pieces);
        assertEquals(0f, keyboard.box.left, 0f);
        assertEquals(PONG.widthPx, keyboard.box.right, 0f);
    }

    @Test public void theAzRowOnTheDockRidesTheDockCardAndATabOrAHiddenBarHasNone() {
        LayoutFixtures.Fixture az = LayoutFixtures.AZ_ON_DOCK.withDesign(DockStyle.GLASS_TRAY);
        RestCards cards = rest(az, PageRef.TERMINAL, false, true);
        assertEquals(RestCards.AzPlacement.DOCK_ROW, cards.azPlacement());
        assertEquals(cards.card(CardId.DOCK), cards.carrierOf(ChromeShape.PieceId.AZ));
        assertEquals(RestCards.AzPlacement.TAB,
            RestCards.azPlacementOf(az.window.withAzTab(true)));
        assertEquals(RestCards.AzPlacement.EDGE, RestCards.azPlacementOf(
            az.window.withSlot(Element.AZ, new Slot(false, Edge.TOP, 0, false))));
        RestCards plain = rest(TRAY, PageRef.TERMINAL, false, true);
        assertEquals(RestCards.AzPlacement.HIDDEN, plain.azPlacement());
        assertNull(plain.carrierOf(ChromeShape.PieceId.AZ));
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

    /**
     * Home's keyboard raised on demand draws over the dock (#309). While the cards stand apart the
     * host is laid out from this answer, so its rect, its drawn cards and its dress are Home's
     * keyboard-down rest's at both keyboard states, and with the keyboard up the keyboard's card
     * covers its glass. Not apart the host is laid out by the stack's own rules: its top never
     * moves, and only where the dock owns the foot under the gesture pill (a kept band) does its
     * bottom change hands with the keyboard.
     */
    @Test public void homesHostUnderTheCoverIsOneRectForBothKeyboardStates() {
        int apart = 0;
        int together = 0;
        for (LayoutFixtures.Fixture base : LayoutFixtures.cardFixtures())
            for (DockStyle design : DockStyle.CHOICES)
                for (boolean floating : new boolean[] {false, true}) {
            LayoutFixtures.Fixture f = base.withHomeDesign(design);
            String at = f.name + (floating ? " FLOATING" : " DOCKED");
            RestCards down = RestCards.of(f.cardsRest(PageRef.HOME, false, false, floating));
            RestCards up = RestCards.of(f.cardsRest(PageRef.HOME, true, false, floating));
            ChromeShape.Box was = down.laidOut(StackView.HOST);
            ChromeShape.Box now = up.laidOut(StackView.HOST);
            if (was == null || now == null) {
                assertEquals(at, was == null, now == null);
                continue;
            }
            assertEquals(at, down.apart(), up.apart());
            if (up.apart()) {
                apart++;
                assertEquals(at + " laid out", was.toString(), now.toString());
                assertEquals(at, hostCards(down), hostCards(up));
                RestCards.HostLook look = up.homeHostLook();
                assertNotNull(at, look);
                assertEquals(at + " dressed as the keyboard-down rest", false, look.keyboardUp);
                assertEquals(at, down.homeHostLook().foot, look.foot);
                assertEquals(at, String.valueOf(down.homeHostLook().peekCut),
                    String.valueOf(look.peekCut));
                assertEquals(at, String.valueOf(down.homeHostRect()),
                    String.valueOf(up.homeHostRect()));
                if (up.card(CardId.KEYBOARD) != null)
                    for (RestCards.Card card : up.cards())
                        if (card.view == StackView.HOST && card.id != CardId.FOOT)
                            assertTrue(at + " covered " + card, up.covered().contains(card));
            } else {
                together++;
                // Together only where the Terminal's rest wears Home's Peek card (#305).
                assertNotNull(at, RestCards.of(f.cardsRestOf(PageRef.TERMINAL, false, floating))
                    .card(CardId.PEEK));
                assertEquals(at + " top", was.top, now.top, 0f);
                if (was.bottom != now.bottom)
                    assertTrue(at + " only the foot changes hands", f.keptBandPx > 0);
            }
        }
        assertTrue("some fixtures stand apart", apart > 0);
        // Since #305 every fixture stands apart but a Terminal wearing Home's Peek card.
        assertTrue("a Terminal wearing the Peek card stands together", together > 0);
    }

    private static String hostCards(RestCards cards) {
        StringBuilder out = new StringBuilder();
        for (RestCards.Card card : cards.cards())
            if (card.view == StackView.HOST) out.append(card).append(' ');
        return out.toString();
    }

    /** A view's cards as the dress reads them: box, corners, each edge's kind and the pieces. */
    private static String cardsOn(java.util.List<RestCards.Card> cards, StackView view) {
        StringBuilder out = new StringBuilder();
        for (RestCards.Card card : cards) {
            if (card.view != view) continue;
            out.append(card.id).append('@').append(card.box).append(' ').append(card.corners);
            for (Edge edge : Edge.values()) out.append(' ').append(card.edge(edge));
            out.append(' ').append(card.pieces).append("; ");
        }
        return out.toString();
    }

    /**
     * Docked, while the cards stand apart (#304): the Terminal's own host carries the card the one
     * host wore at the Terminal's rest, box, corners, edges and pieces alike (the flush air above
     * the keys and the foot under the gesture pill included), whichever page is in front; Home's
     * host keeps Home's with the keyboard down.
     */
    @Test public void dockedTheTerminalsHostIsTheOneHostsCardAtTheTerminalsRest() {
        int cases = 0;
        int feet = 0;
        for (LayoutFixtures.Fixture base : LayoutFixtures.cardFixtures()) {
            for (DockStyle design : DockStyle.CHOICES) {
                LayoutFixtures.Fixture f = base.withHomeDesign(design);
                for (boolean up : new boolean[] {false, true}) {
                    RestCards terminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, up, false,
                        false));
                    if (!terminal.apart()) continue;
                    String label = f.name + " " + design + " terminal kb=" + up;
                    RestCards alone = RestCards.of(f.cardsRestOf(PageRef.TERMINAL, up, false));
                    String want = cardsOn(alone.cards(), StackView.HOST);
                    // A Terminal with no card at its rest (#305) shows none.
                    assertEquals(label, want, cardsOn(terminal.cards(), StackView.TERMINAL_HOST));
                    assertEquals(label, "", cardsOn(terminal.cards(), StackView.HOST));
                    // Parked with the Terminal while Home is in front, at the same rest.
                    RestCards home = RestCards.of(f.cardsRest(PageRef.HOME, false, up, false));
                    assertEquals(label + " parked", want,
                        cardsOn(home.parked(), StackView.TERMINAL_HOST));
                    assertEquals(label + " laid out", terminal.laidOut(StackView.TERMINAL_HOST),
                        home.laidOut(StackView.TERMINAL_HOST));
                    // Home's card at Home's rest is the one host's there.
                    RestCards homeAlone = RestCards.of(f.cardsRestOf(PageRef.HOME, false, false));
                    assertEquals(label + " home", cardsOn(homeAlone.cards(), StackView.HOST),
                        cardsOn(home.cards(), StackView.HOST));
                    if (want.contains("FOOT")) feet++;
                    cases++;
                }
            }
        }
        assertTrue("some Docked rests stand apart", cases > 0);
        assertTrue("some wear the foot", feet > 0);
    }

    /**
     * Floating with the apps row or A-Z beside the keys (#305): the cards stand apart and the
     * Terminal's card is its own back host, the one host's Floating card at the Terminal's rest
     * (box, corners, edges, pieces: the rows beside the keys included), never the keys row's
     * card; Home's host keeps Home's card (the Peek card, or the apps row's) at Home's rest.
     */
    @Test public void floatingWithRowsBesideTheKeysTheTerminalsHostIsTheOneHostsCard() {
        int cases = 0;
        int peeks = 0;
        for (LayoutFixtures.Fixture base : LayoutFixtures.cardFixtures()) {
            if (SideCards.keysAlone(base.layoutOf(PageRef.TERMINAL))) continue;
            for (DockStyle design : DockStyle.CHOICES) {
                LayoutFixtures.Fixture f = base.withHomeDesign(design);
                for (boolean up : new boolean[] {false, true}) {
                    RestCards terminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, up, false,
                        true));
                    if (!terminal.apart()) continue;
                    String label = f.name + " " + design + " terminal kb=" + up;
                    RestCards alone = RestCards.of(f.cardsRestOf(PageRef.TERMINAL, up, true));
                    String want = cardsOn(alone.cards(), StackView.HOST);
                    assertEquals(label, want, cardsOn(terminal.cards(), StackView.TERMINAL_HOST));
                    assertEquals(label, "", cardsOn(terminal.cards(), StackView.KEYS_CARD));
                    assertEquals(label, null, terminal.laidOut(StackView.KEYS_CARD));
                    assertEquals(label, "", cardsOn(terminal.cards(), StackView.HOST));
                    RestCards home = RestCards.of(f.cardsRest(PageRef.HOME, false, up, true));
                    assertEquals(label + " parked", want,
                        cardsOn(home.parked(), StackView.TERMINAL_HOST));
                    RestCards homeAlone = RestCards.of(f.cardsRestOf(PageRef.HOME, false, true));
                    assertEquals(label + " home", cardsOn(homeAlone.cards(), StackView.HOST),
                        cardsOn(home.cards(), StackView.HOST));
                    if (home.card(CardId.PEEK) != null) peeks++;
                    cases++;
                }
            }
        }
        assertTrue("some Floating rests with rows beside the keys stand apart", cases > 0);
        assertTrue("Home wears the Peek card in some", peeks > 0);
    }

    /**
     * The three layouts that still shared the glass host after #305's first round stand apart
     * too (#305): a Terminal with no keys row, its keys under the keyboard, and its status bar on
     * the dock's plank. Home's card travels with Home and is Home's rest's; the Terminal's card,
     * where it has one, is its own host's, the one host's card at the Terminal's rest (box,
     * corners, edges, pieces), under either Style. A Terminal with no card of its own shows none.
     * The one case left on the one host: a Terminal whose rest wears the Peek card, which is
     * Home's card at Home's rect, so it stays put.
     */
    @Test public void theLayoutsThatSharedTheHostStandApartEachSideWithItsOwnCard() {
        int own = 0;
        int none = 0;
        int peek = 0;
        LayoutFixtures.Fixture[] shared = {LayoutFixtures.NO_KEYS_ROW, LayoutFixtures.KEYS_UNDER,
            LayoutFixtures.STATUS_ON_PLANK};
        assertTrue(LayoutFixtures.STATUS_ON_PLANK.cardsRestOf(PageRef.TERMINAL, false, false)
            .statusOnDockPlank);
        for (LayoutFixtures.Fixture base : shared)
            for (DockStyle design : DockStyle.CHOICES)
                for (boolean floating : new boolean[] {false, true})
                    for (boolean up : new boolean[] {false, true}) {
            LayoutFixtures.Fixture f = base.withDesign(design);
            String label = f.name + (floating ? " FLOATING" : " DOCKED") + " kb=" + up;
            RestCards terminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, up, false, floating));
            RestCards alone = RestCards.of(f.cardsRestOf(PageRef.TERMINAL, up, floating));
            String want = cardsOn(alone.cards(), StackView.HOST);
            if (alone.card(CardId.PEEK) != null) {
                // The Terminal's rest wears the Peek card, Home's at Home's rect: one card both
                // ends draw in one place, so it stays put on the one host (ADR 0032, 3).
                assertTrue(label + " together", !terminal.apart());
                assertEquals(label, want, cardsOn(terminal.cards(), StackView.HOST));
                RestCards homeAlone = RestCards.of(f.cardsRestOf(PageRef.HOME, false, floating));
                assertEquals(label + " Home's card", cardsOn(homeAlone.cards(), StackView.HOST),
                    want);
                peek++;
                continue;
            }
            assertTrue(label + " apart", terminal.apart());
            assertEquals(label, StackView.TERMINAL_HOST, RestCards.terminalCardView(floating,
                f.window, f.cardsRestOf(PageRef.TERMINAL, up, floating).statusOnDockPlank));
            assertEquals(label, want, cardsOn(terminal.cards(), StackView.TERMINAL_HOST));
            assertEquals(label, "", cardsOn(terminal.cards(), StackView.HOST));
            assertNull(label, terminal.laidOut(StackView.KEYS_CARD));
            RestCards home = RestCards.of(f.cardsRest(PageRef.HOME, false, up, floating));
            assertTrue(label + " apart from Home", home.apart());
            assertEquals(label + " parked", want, cardsOn(home.parked(), StackView.TERMINAL_HOST));
            RestCards homeAlone = RestCards.of(f.cardsRestOf(PageRef.HOME, false, floating));
            assertEquals(label + " home", cardsOn(homeAlone.cards(), StackView.HOST),
                cardsOn(home.cards(), StackView.HOST));
            if (want.isEmpty()) none++;
            else own++;
        }
        assertTrue("some Terminals carry a card of their own", own > 0);
        assertTrue("some Terminals have none", none > 0);
        assertTrue("some Terminals wear Home's Peek card", peek > 0);
    }
    /**
     * The one host stays shared only while the Terminal's Peek card is Home's card at Home's rect
     * (ADR 0032, 2026-11-24): wherever Home's rest draws no Peek card, or draws it elsewhere (its
     * own design or its keyboard up), the two cards stand apart, whichever page is in front.
     */
    @Test public void theTerminalsPeekCardStaysSharedOnlyAtHomesRect() {
        int together = 0;
        int apart = 0;
        for (LayoutFixtures.Fixture base : LayoutFixtures.cardFixtures())
            for (DockStyle homeDesign : DockStyle.CHOICES)
                for (DockStyle terminalDesign : DockStyle.CHOICES)
                    for (boolean floating : new boolean[] {false, true})
                        for (boolean homeUp : new boolean[] {false, true}) {
            LayoutFixtures.Fixture f = base.withDesign(terminalDesign).withHomeDesign(homeDesign);
            RestCards.Card mine = RestCards.of(f.cardsRestOf(PageRef.TERMINAL, false, floating))
                .card(CardId.PEEK);
            if (mine == null) continue;
            RestCards.Card home = RestCards.of(f.cardsRestOf(PageRef.HOME, homeUp, floating))
                .card(CardId.PEEK);
            boolean shared = home != null && home.toString().equals(mine.toString());
            String label = f.name + " " + homeDesign + "/" + terminalDesign
                + (floating ? " FLOATING" : " DOCKED") + " home kb=" + homeUp;
            RestCards terminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, false, homeUp,
                floating));
            RestCards front = RestCards.of(f.cardsRest(PageRef.HOME, homeUp, false, floating));
            assertEquals(label, !shared, terminal.apart());
            assertEquals(label + " Home in front", !shared, front.apart());
            if (shared) together++;
            else apart++;
        }
        assertTrue("some Terminals wear Home's Peek card at Home's rect", together > 0);
        assertTrue("some Terminals wear a Peek card Home's rest does not draw there", apart > 0);
    }
    /**
     * While the cards stand apart with two keyboard cards, each keyboard view is one side's at
     * both rests (#307): {@link StackView#KEYBOARD_COLUMN} is laid out at Home's rest and carries
     * Home's keyboard card, parked while the Terminal is in front, and
     * {@link StackView#TERMINAL_KEYBOARD} at the Terminal's, parked while Home is in front.
     */
    @Test public void apartTheKeyboardColumnIsHomesAndTheTerminalKeyboardTheTerminalsAtBothRests() {
        int cases = 0;
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : new boolean[] {false, true})
                for (boolean homeUp : new boolean[] {false, true})
                    for (boolean terminalUp : new boolean[] {false, true}) {
            RestCards atTerminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, terminalUp, homeUp,
                floating));
            if (atTerminal.laidOut(StackView.TERMINAL_KEYBOARD) == null) continue;
            String label = f.name + (floating ? " FLOATING" : " DOCKED") + " home kb=" + homeUp
                + " terminal kb=" + terminalUp;
            RestCards atHome = RestCards.of(f.cardsRest(PageRef.HOME, homeUp, terminalUp,
                floating));
            assertEquals(label + " Home's column",
                String.valueOf(atHome.laidOut(StackView.KEYBOARD_COLUMN)),
                String.valueOf(atTerminal.homeKeyboardColumn()));
            assertEquals(label + " Home's column, by its view",
                String.valueOf(atHome.laidOut(StackView.KEYBOARD_COLUMN)),
                String.valueOf(atTerminal.laidOut(StackView.KEYBOARD_COLUMN)));
            assertEquals(label + " the Terminal's keyboard",
                String.valueOf(atHome.laidOut(StackView.TERMINAL_KEYBOARD)),
                String.valueOf(atTerminal.laidOut(StackView.TERMINAL_KEYBOARD)));
            for (RestCards.Card card : atTerminal.cards())
                assertTrue(label + " " + card, card.view != StackView.KEYBOARD_COLUMN);
            for (RestCards.Card card : atHome.cards())
                assertTrue(label + " " + card, card.view != StackView.TERMINAL_KEYBOARD);
            StringBuilder homeKeys = new StringBuilder();
            for (RestCards.Card card : atHome.cards())
                if (card.view == StackView.KEYBOARD_COLUMN) homeKeys.append(card).append(' ');
            StringBuilder parked = new StringBuilder();
            for (RestCards.Card card : atTerminal.parked())
                if (card.view == StackView.KEYBOARD_COLUMN) parked.append(card).append(' ');
            assertEquals(label + " Home's keyboard card parked", homeKeys.toString(),
                parked.toString());
            cases++;
        }
        assertTrue("some rests draw two keyboard cards", cases > 0);
    }
}
