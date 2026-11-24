package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.layouteditor.BarPlaceOffers.Label;
import com.termux.app.layouteditor.BarPlaceOffers.Offer;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.statusbar.StatusPlace;

import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Which dashed places a selected bar shows, and the pane hold that becomes the status place. */
public class BarPlaceOffersTest {

    private static PlaceLayout shipped() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static FrameState state(LayoutKind kind, StatusPlace place) {
        return new FrameState(shipped(), PlaceOrientation.PORTRAIT, kind, place);
    }

    private static Offer find(List<Offer> offers, Label label, Edge edge) {
        for (Offer offer : offers)
            if (offer.label == label && (edge == null || offer.landing.edge == edge)) return offer;
        return null;
    }

    @Test public void aBarOnTheBottomOffersTheOtherEdgesAndHideButNotItsOwn() {
        List<Offer> offers = BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.EXTRA_KEYS, true);
        assertNull(find(offers, Label.MOVE_HERE, Edge.BOTTOM));
        assertTrue(find(offers, Label.MOVE_HERE, Edge.TOP) != null);
        assertTrue(find(offers, Label.MOVE_HERE, Edge.LEFT) != null);
        assertTrue(find(offers, Label.MOVE_HERE, Edge.RIGHT) != null);
        assertTrue(find(offers, Label.HIDE, null) != null);
        assertNull(find(offers, Label.PUT_IN_PANE, null));
        assertNull(find(offers, Label.BRING_BACK, null));
    }

    @Test public void everyOfferIsOneThePolicyAllows() {
        for (Bar bar : new Bar[] {Bar.STATUS_BAR, Bar.APPS_ROW, Bar.AZ_INDEX, Bar.EXTRA_KEYS,
            Bar.KEYBOARD}) {
            for (Offer offer : BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
                bar, true))
                assertTrue(bar + " " + offer.label, offer.landing.allowed);
        }
    }

    @Test public void theKeyboardOffersHideAndNoMove() {
        List<Offer> offers = BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.KEYBOARD, true);
        assertEquals(1, offers.size());
        assertEquals(Label.HIDE, offers.get(0).label);
        assertTrue(offers.get(0).onBar());
    }

    @Test public void theStatusBarOffersThePaneOnAWorkspaceLayoutOnly() {
        assertTrue(find(BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.STATUS_BAR, true), Label.PUT_IN_PANE, null) != null);
        assertNull(find(BarPlaceOffers.of(state(LayoutKind.HOME, StatusPlace.STRIP),
            Bar.STATUS_BAR, true), Label.PUT_IN_PANE, null));
    }

    @Test public void theStatusBarInThePaneOffersEveryEdgeAndNotThePane() {
        List<Offer> offers = BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.PANE),
            Bar.STATUS_BAR, true);
        assertNull(find(offers, Label.PUT_IN_PANE, null));
        for (Edge edge : Edge.values()) {
            Offer offer = find(offers, Label.MOVE_HERE, edge);
            assertTrue(edge.toString(), offer != null);
            assertEquals(StatusPlace.STRIP, offer.landing.statusPlace);
        }
    }

    @Test public void aPutAwayBarOffersOnlyTheWayBackOnItsOwnEdge() {
        List<Offer> offers = BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.EXTRA_KEYS, false);
        assertEquals(1, offers.size());
        assertEquals(Label.BRING_BACK, offers.get(0).label);
        assertEquals(Edge.BOTTOM, offers.get(0).landing.edge);
        assertEquals(Zone.EDGE, offers.get(0).landing.zone);
    }

    @Test public void aPutAwayKeyboardComesBackOnTheBottom() {
        List<Offer> offers = BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.KEYBOARD, false);
        assertEquals(1, offers.size());
        assertEquals(Label.BRING_BACK, offers.get(0).label);
    }

    @Test public void thePageBarOffersNothingOutsideHome() {
        assertTrue(BarPlaceOffers.of(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.PAGE_BAR, true).isEmpty());
    }

    @Test public void theHomePageBarOffersHideAndAPutAwayOneOffersTheWayBack() {
        FrameState home = state(LayoutKind.HOME, StatusPlace.STRIP);
        List<Offer> shown = BarPlaceOffers.of(home, Bar.PAGE_BAR, true);
        assertEquals(1, shown.size());
        assertEquals(Label.HIDE, shown.get(0).label);
        List<Offer> away = BarPlaceOffers.of(home, Bar.PAGE_BAR, false);
        assertEquals(1, away.size());
        assertEquals(Label.BRING_BACK, away.get(0).label);
        assertTrue(away.get(0).landing.allowed);
    }

    // ------------------------------------------------- the pane hold, to the status place write

    private static BarDragGeometry.Frame frameWithPane() {
        BarDragGeometry.Frame frame = new BarDragGeometry.Frame(400, 800);
        frame.addBand(Bar.STATUS_BAR, Edge.TOP, false, 0, 0, 400, 40);
        frame.setPane(0, 40, 400, 700, 100);
        return frame;
    }

    @Test public void thePaneHoldWritesThePaneAsTheStatusPlace() {
        FrameState state = state(LayoutKind.TERMINAL, StatusPlace.STRIP);
        Hold hold = BarDragGeometry.holdAt(frameWithPane(), Bar.STATUS_BAR, 200, 300);
        assertEquals(Zone.PANE, hold.zone);
        Landing landing = BarLandingPolicy.land(state, Bar.STATUS_BAR, hold);
        assertTrue(landing.allowed);
        assertEquals(StatusPlace.PANE,
            BarDrop.statusPlaceAfter(Bar.STATUS_BAR, landing, StatusPlace.STRIP));
        assertNull(BarDrop.layoutAfter(state.layout, Bar.STATUS_BAR, landing));
    }

    @Test public void theThinRowOnAnEdgeWritesTheStrip() {
        FrameState state = state(LayoutKind.TERMINAL, StatusPlace.PANE);
        Hold hold = BarDragGeometry.holdAt(frameWithPane(), Bar.STATUS_BAR, 200, 20);
        Landing landing = BarLandingPolicy.land(state, Bar.STATUS_BAR, hold);
        assertEquals(Zone.EDGE, landing.zone);
        assertEquals(StatusPlace.STRIP,
            BarDrop.statusPlaceAfter(Bar.STATUS_BAR, landing, StatusPlace.PANE));
    }

    @Test public void noPlaceWriteWhenNothingChangesOrNotTheStatusBar() {
        Landing strip = BarLandingPolicy.land(state(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.STATUS_BAR, Hold.edge(Edge.BOTTOM, 0));
        assertNull(BarDrop.statusPlaceAfter(Bar.STATUS_BAR, strip, StatusPlace.STRIP));
        Landing keys = BarLandingPolicy.land(state(LayoutKind.TERMINAL, StatusPlace.PANE),
            Bar.EXTRA_KEYS, Hold.edge(Edge.TOP, 0));
        assertNull(BarDrop.statusPlaceAfter(Bar.EXTRA_KEYS, keys, StatusPlace.PANE));
    }

    @Test public void onHomeThePaneIsNotATarget() {
        FrameState home = state(LayoutKind.HOME, StatusPlace.STRIP);
        Landing landing = BarLandingPolicy.land(home, Bar.STATUS_BAR, Hold.pane());
        assertFalse(landing.allowed);
        assertNull(BarDrop.statusPlaceAfter(Bar.STATUS_BAR, landing, StatusPlace.STRIP));
    }
}
