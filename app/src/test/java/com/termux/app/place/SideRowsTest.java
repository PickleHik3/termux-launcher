package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/**
 * Who carries each row through a crossing (ADR 0030 section 4, with the developer's decisions on
 * #260): a row both ends show in the same place (edge, and over or under the keyboard) is the stack's and stays put; a row one end shows
 * goes with that end's side; within a side nothing moves. And which landings may move rows.
 */
public class SideRowsTest {

    private static final Side H = Side.HOME;
    private static final Side WS = Side.WORKSPACE;

    @Test public void anAppsRowInOnePlaceAtBothEndsStaysPutOtherwiseItGoesWithHome() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL) {
            // Pong's window stands its apps under the keyboard, Home over it: Home's, not the stack's.
            SideRows.Carrier expected = StackLanes.where(f.window, Element.APPS)
                == StackLanes.where(f.home, Element.APPS)
                ? SideRows.Carrier.STACK : SideRows.Carrier.HOME;
            assertSame(f.name, expected,
                SideRows.carrier(Element.APPS, H, f.home, WS, f.window));
            assertSame(f.name, expected,
                SideRows.carrier(Element.APPS, WS, f.window, H, f.home));
        }
    }

    @Test public void aRowOnlyHomeShowsGoesWithHome() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout bare = home.withSlot(Element.APPS, Slot.hiddenFrom(Edge.BOTTOM, 0))
            .withSlot(Element.AZ, Slot.hiddenFrom(Edge.BOTTOM, 1));
        assertSame(SideRows.Carrier.HOME, SideRows.carrier(Element.APPS, H, home, WS, bare));
        assertSame(SideRows.Carrier.HOME, SideRows.carrier(Element.APPS, WS, bare, H, home));
        assertSame(SideRows.Carrier.WORKSPACE, SideRows.carrier(Element.APPS, WS, home, H, bare));
        assertSame(SideRows.Carrier.NONE, SideRows.carrier(Element.APPS, H, bare, WS, bare));
    }

    @Test public void aRowOverTheKeyboardOnOneEndAndUnderItOnTheOtherGoesWithItsOwner() {
        for (Element e : new Element[] {Element.APPS, Element.AZ}) {
            PlaceLayout over = LayoutFixtures.HOME.withSlot(e, new Slot(false, Edge.BOTTOM, 0, false));
            PlaceLayout under = LayoutFixtures.HOME.withSlot(e, new Slot(false, Edge.BOTTOM, 0, true));
            assertSame(e.name(), StackLanes.Where.BOTTOM_UNDER, StackLanes.where(under, e));
            assertSame(e.name(), SideRows.Carrier.HOME, SideRows.carrier(e, H, over, WS, under));
            assertSame(e.name(), SideRows.Carrier.HOME, SideRows.carrier(e, H, under, WS, over));
            assertSame(e.name(), SideRows.Carrier.HOME, SideRows.carrier(e, WS, over, H, under));
            assertSame(e.name(), SideRows.Carrier.HOME, SideRows.carrier(e, WS, under, H, over));
            assertSame(e.name(), SideRows.Carrier.STACK, SideRows.carrier(e, H, under, WS, under));
            assertSame(e.name(), SideRows.Carrier.STACK, SideRows.carrier(e, H, over, WS, over));
        }
    }

    @Test public void aRowOnTwoEdgesGoesWithItsOwner() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout left = home.withSlot(Element.APPS, Slot.on(Edge.LEFT, 0));
        assertSame(SideRows.Carrier.HOME, SideRows.carrier(Element.APPS, H, home, WS, left));
        assertSame(SideRows.Carrier.HOME, SideRows.carrier(Element.APPS, WS, left, H, home));
    }

    @Test public void withinASideNoRowMoves() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout left = home.withSlot(Element.APPS, Slot.on(Edge.LEFT, 0));
        for (Side side : Side.values()) for (Element e : Element.values()) {
            assertSame(SideRows.Carrier.STACK, SideRows.carrier(e, side, home, side, left));
        }
    }

    @Test public void theStatusBarIsNeverASidesRow() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout window = LayoutFixtures.STATUS_BOTTOM.window;
        assertSame(SideRows.Carrier.STACK, SideRows.carrier(Element.STATUS, H, home, WS, window));
    }

    @Test public void theExtraKeysInTheStackAreTheWorkspacesWhateverHomeLists() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout window = LayoutFixtures.PONG.window;
        PlaceLayout homeWithKeys = home.withSlot(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, 0));
        // Home has its own bar in the pill layer: the stack's is the Workspace's in every pair.
        assertSame(SideRows.Carrier.WORKSPACE,
            SideRows.carrier(Element.EXTRA_KEYS, H, home, WS, window));
        assertSame(SideRows.Carrier.WORKSPACE,
            SideRows.carrier(Element.EXTRA_KEYS, WS, window, H, home));
        assertSame(SideRows.Carrier.WORKSPACE,
            SideRows.carrier(Element.EXTRA_KEYS, H, homeWithKeys, WS, window));
        assertSame(SideRows.Carrier.WORKSPACE,
            SideRows.carrier(Element.EXTRA_KEYS, H, home, WS, home));
    }

    @Test public void eachExtraKeysBarGoesWithItsOwnSide() {
        assertSame(SideRows.Carrier.HOME, SideRows.extraKeysCarrier(H));
        assertSame(SideRows.Carrier.WORKSPACE, SideRows.extraKeysCarrier(WS));
    }

    @Test public void onlyALandingWithinASideMayMoveRows() {
        assertTrue(SideRows.landingMayMoveRows(H, H, true));
        assertTrue(SideRows.landingMayMoveRows(WS, WS, true));
        assertFalse(SideRows.landingMayMoveRows(H, H, false));
        assertFalse(SideRows.landingMayMoveRows(H, WS, true));
        assertFalse(SideRows.landingMayMoveRows(WS, H, false));
    }

    @Test public void aCarriedRowTakesItsSidesSlide() {
        float[] tx = {-300f, 780f};
        assertEquals(0f, SideRows.across(SideRows.Carrier.STACK, tx), 0f);
        assertEquals(0f, SideRows.across(SideRows.Carrier.NONE, tx), 0f);
        assertEquals(-300f, SideRows.across(SideRows.Carrier.HOME, tx), 0f);
        assertEquals(780f, SideRows.across(SideRows.Carrier.WORKSPACE, tx), 0f);
    }
}
