package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Places;
import com.termux.app.layouteditor.BarLandingPolicy.Scope;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
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
import java.util.Map;

/** Where a held bar may land, over every bar and every edge; plain JVM, no android.view. */
public class BarLandingPolicyTest {

    private static PlaceLayout layout(Map<Element, Slot> slots) {
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    /** Status on top, apps and A-Z and extra keys on the bottom, as shipped. */
    private static PlaceLayout shipped() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        return layout(slots);
    }

    private static FrameState frame(LayoutKind kind, StatusPlace place) {
        return new FrameState(shipped(), PlaceOrientation.PORTRAIT, kind, place);
    }

    private static FrameState workspace() {
        return frame(LayoutKind.TERMINAL, StatusPlace.STRIP);
    }

    private static final Bar[] ELEMENT_BARS = {
        Bar.STATUS_BAR, Bar.APPS_ROW, Bar.AZ_INDEX, Bar.EXTRA_KEYS};

    // ------------------------------------------------------------- each element bar, each edge

    @Test public void everyElementBarMayLandOnEveryEdge() {
        for (Bar bar : ELEMENT_BARS) {
            Places places = BarLandingPolicy.places(workspace(), bar);
            for (Edge edge : Edge.values()) {
                assertTrue(bar + " on " + edge, places.offers(edge));
                Landing landing = BarLandingPolicy.land(workspace(), bar, Hold.edge(edge, 0));
                assertTrue(bar + " on " + edge, landing.allowed);
                assertEquals(Zone.EDGE, landing.zone);
                assertEquals(edge, landing.edge);
                assertEquals(0, landing.index);
            }
        }
    }

    @Test public void everyElementBarMayLandOnEveryEdgeInLandscapeToo() {
        FrameState land = new FrameState(shipped(), PlaceOrientation.LANDSCAPE,
            LayoutKind.HOME, StatusPlace.STRIP);
        for (Bar bar : ELEMENT_BARS)
            for (Edge edge : Edge.values())
                assertTrue(bar + " on " + edge,
                    BarLandingPolicy.land(land, bar, Hold.edge(edge, 0)).allowed);
    }

    @Test public void aGapIsOnePerBandTheBarIsNot() {
        // Bottom holds apps, A-Z, extra keys: the status bar sees four gaps, apps sees three.
        assertEquals(4, BarLandingPolicy.places(workspace(), Bar.STATUS_BAR).gapsOn(Edge.BOTTOM));
        assertEquals(3, BarLandingPolicy.places(workspace(), Bar.APPS_ROW).gapsOn(Edge.BOTTOM));
        // Top holds the status bar alone: the status bar has one gap there, apps has two.
        assertEquals(1, BarLandingPolicy.places(workspace(), Bar.STATUS_BAR).gapsOn(Edge.TOP));
        assertEquals(2, BarLandingPolicy.places(workspace(), Bar.APPS_ROW).gapsOn(Edge.TOP));
    }

    @Test public void aGapBeyondTheStackIsRefused() {
        Landing landing = BarLandingPolicy.land(workspace(), Bar.APPS_ROW, Hold.edge(Edge.TOP, 3));
        assertFalse(landing.allowed);
        assertNull(landing.scope);
    }

    @Test public void theLineIsTheGapTheBarWouldTake() {
        Landing landing = BarLandingPolicy.land(workspace(), Bar.AZ_INDEX, Hold.edge(Edge.BOTTOM, 2));
        assertEquals(Edge.BOTTOM, landing.edge);
        assertEquals(2, landing.index);
        assertFalse(landing.underKeyboard);
    }

    @Test public void dockRowsMayStandUnderTheKeyboardButTheStatusBarMayNot() {
        for (Bar bar : new Bar[] {Bar.APPS_ROW, Bar.AZ_INDEX, Bar.EXTRA_KEYS}) {
            assertTrue(BarLandingPolicy.places(workspace(), bar).underKeyboardGaps > 0);
            Landing landing = BarLandingPolicy.land(workspace(), bar, Hold.underKeyboard(0));
            assertTrue(bar.toString(), landing.allowed);
            assertTrue(landing.underKeyboard);
            assertEquals(Edge.BOTTOM, landing.edge);
        }
        assertEquals(0, BarLandingPolicy.places(workspace(), Bar.STATUS_BAR).underKeyboardGaps);
        assertFalse(
            BarLandingPolicy.land(workspace(), Bar.STATUS_BAR, Hold.underKeyboard(0)).allowed);
    }

    @Test public void aLayoutMayHoldItsBarsOnEitherEdge() {
        // Status on the bottom, apps and A-Z on the top, extra keys on the left: the policy reads
        // the stacks as they stand, not as they shipped.
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.BOTTOM, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.TOP, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.LEFT, Element.EXTRA_KEYS));
        FrameState state = new FrameState(layout(slots), PlaceOrientation.PORTRAIT,
            LayoutKind.HOME, StatusPlace.STRIP);
        assertEquals(3, BarLandingPolicy.places(state, Bar.EXTRA_KEYS).gapsOn(Edge.TOP));
        assertEquals(2, BarLandingPolicy.places(state, Bar.STATUS_BAR).gapsOn(Edge.LEFT));
        assertEquals(1, BarLandingPolicy.places(state, Bar.EXTRA_KEYS).gapsOn(Edge.LEFT));
        assertEquals(1, BarLandingPolicy.places(state, Bar.STATUS_BAR).gapsOn(Edge.BOTTOM));
        assertEquals(2, BarLandingPolicy.places(state, Bar.APPS_ROW).gapsOn(Edge.BOTTOM));
    }

    // ------------------------------------------------------------------------------- hide

    @Test public void everyBarMayBeDroppedOnTheHiddenColumn() {
        for (Bar bar : Bar.values()) {
            LayoutKind kind = bar == Bar.PAGE_BAR ? LayoutKind.HOME : LayoutKind.TERMINAL;
            FrameState state = frame(kind, StatusPlace.STRIP);
            assertTrue(bar.toString(), BarLandingPolicy.places(state, bar).hide);
            Landing landing = BarLandingPolicy.land(state, bar, Hold.hidden());
            assertTrue(bar.toString(), landing.allowed);
            assertEquals(Zone.HIDDEN, landing.zone);
            assertNull(landing.edge);
        }
    }

    // ------------------------------------------------------------------- status bar and pane

    @Test public void theStatusBarMayLandOverThePaneOnWorkspaceAndMinimalButNotHome() {
        assertTrue(BarLandingPolicy.places(frame(LayoutKind.TERMINAL, StatusPlace.STRIP),
            Bar.STATUS_BAR).pane);
        assertTrue(BarLandingPolicy.places(frame(LayoutKind.TERMINAL_MINIMAL, StatusPlace.STRIP),
            Bar.STATUS_BAR).pane);
        assertFalse(BarLandingPolicy.places(frame(LayoutKind.HOME, StatusPlace.STRIP),
            Bar.STATUS_BAR).pane);
        assertFalse(BarLandingPolicy.land(frame(LayoutKind.HOME, StatusPlace.STRIP),
            Bar.STATUS_BAR, Hold.pane()).allowed);
        Landing landing = BarLandingPolicy.land(workspace(), Bar.STATUS_BAR, Hold.pane());
        assertTrue(landing.allowed);
        assertEquals(Zone.PANE, landing.zone);
        assertEquals(StatusPlace.PANE, landing.statusPlace);
        assertEquals(Scope.EVERY_PAGE, landing.scope);
    }

    @Test public void onlyTheStatusBarGoesOverThePane() {
        for (Bar bar : Bar.values()) {
            if (bar == Bar.STATUS_BAR) continue;
            assertFalse(bar.toString(), BarLandingPolicy.places(workspace(), bar).pane);
            assertFalse(bar.toString(),
                BarLandingPolicy.land(workspace(), bar, Hold.pane()).allowed);
        }
    }

    @Test public void droppingTheStatusBarOnAnEdgeMakesItTheStrip() {
        Landing landing = BarLandingPolicy.land(frame(LayoutKind.TERMINAL, StatusPlace.PANE),
            Bar.STATUS_BAR, Hold.edge(Edge.BOTTOM, 0));
        assertTrue(landing.allowed);
        assertEquals(StatusPlace.STRIP, landing.statusPlace);
        assertEquals(Scope.EVERY_PAGE, landing.scope);
    }

    @Test public void theStatusBarInThePaneStillOffersEveryEdge() {
        Places places = BarLandingPolicy.places(frame(LayoutKind.TERMINAL, StatusPlace.PANE),
            Bar.STATUS_BAR);
        for (Edge edge : Edge.values()) assertTrue(edge.toString(), places.offers(edge));
    }

    // -------------------------------------------------------------------------------- keyboard

    @Test public void theKeyboardStandsOnTheBottomEdgeAlone() {
        Places places = BarLandingPolicy.places(workspace(), Bar.KEYBOARD);
        assertTrue(places.offers(Edge.BOTTOM));
        for (Edge edge : new Edge[] {Edge.TOP, Edge.LEFT, Edge.RIGHT}) {
            assertFalse(edge.toString(), places.offers(edge));
            assertFalse(edge.toString(),
                BarLandingPolicy.land(workspace(), Bar.KEYBOARD, Hold.edge(edge, 0)).allowed);
        }
        Landing landing =
            BarLandingPolicy.land(workspace(), Bar.KEYBOARD, Hold.edge(Edge.BOTTOM, 0));
        assertTrue(landing.allowed);
        assertEquals(Edge.BOTTOM, landing.edge);
        assertEquals(Scope.WORKSPACE_PAGES, landing.scope);
        assertFalse(
            BarLandingPolicy.land(workspace(), Bar.KEYBOARD, Hold.underKeyboard(0)).allowed);
    }

    // ---------------------------------------------------------------------------------- page bar

    @Test public void thePageBarIsHomesAndHasNoEdgeToLandOn() {
        FrameState homeState = frame(LayoutKind.HOME, StatusPlace.STRIP);
        Places home = BarLandingPolicy.places(homeState, Bar.PAGE_BAR);
        assertTrue(home.available);
        assertTrue(home.hide);
        for (Edge edge : Edge.values()) {
            assertFalse(edge.toString(), home.offers(edge));
            assertFalse(edge.toString(),
                BarLandingPolicy.land(homeState, Bar.PAGE_BAR, Hold.edge(edge, 0)).allowed);
        }
        Places other = BarLandingPolicy.places(workspace(), Bar.PAGE_BAR);
        assertFalse(other.available);
        assertFalse(other.hide);
        assertFalse(BarLandingPolicy.land(workspace(), Bar.PAGE_BAR, Hold.hidden()).allowed);
    }

    // -------------------------------------------------------------------------------- scope

    @Test public void aLayoutBarsChangeReachesItsLayoutOnly() {
        FrameState homeState = frame(LayoutKind.HOME, StatusPlace.STRIP);
        FrameState minimal = frame(LayoutKind.TERMINAL_MINIMAL, StatusPlace.STRIP);
        assertEquals(Scope.HOME_PAGES,
            BarLandingPolicy.land(homeState, Bar.APPS_ROW, Hold.edge(Edge.TOP, 0)).scope);
        assertEquals(Scope.WORKSPACE_PAGES,
            BarLandingPolicy.land(workspace(), Bar.EXTRA_KEYS, Hold.edge(Edge.TOP, 0)).scope);
        assertEquals(Scope.MINIMAL_LAYOUT,
            BarLandingPolicy.land(minimal, Bar.AZ_INDEX, Hold.edge(Edge.LEFT, 0)).scope);
        assertEquals(Scope.MINIMAL_LAYOUT,
            BarLandingPolicy.land(minimal, Bar.KEYBOARD, Hold.edge(Edge.BOTTOM, 0)).scope);
        assertEquals(Scope.HOME_PAGES,
            BarLandingPolicy.land(homeState, Bar.PAGE_BAR, Hold.hidden()).scope);
    }

    @Test public void aRetiredKindIsScopedAsTheTerminalLayout() {
        assertEquals(Scope.WORKSPACE_PAGES, BarLandingPolicy.land(
            frame(LayoutKind.DISPLAY, StatusPlace.STRIP), Bar.APPS_ROW,
            Hold.edge(Edge.TOP, 0)).scope);
    }

    @Test public void theStatusBarsEdgeIsOneValueButPuttingItAwayIsTheLayouts() {
        for (LayoutKind kind : new LayoutKind[] {LayoutKind.HOME, LayoutKind.TERMINAL,
            LayoutKind.TERMINAL_MINIMAL}) {
            FrameState state = frame(kind, StatusPlace.STRIP);
            assertEquals(Scope.EVERY_PAGE,
                BarLandingPolicy.land(state, Bar.STATUS_BAR, Hold.edge(Edge.BOTTOM, 0)).scope);
        }
        assertEquals(Scope.HOME_PAGES, BarLandingPolicy.land(
            frame(LayoutKind.HOME, StatusPlace.STRIP), Bar.STATUS_BAR, Hold.hidden()).scope);
        assertEquals(Scope.WORKSPACE_PAGES,
            BarLandingPolicy.land(workspace(), Bar.STATUS_BAR, Hold.hidden()).scope);
        assertEquals(Scope.MINIMAL_LAYOUT, BarLandingPolicy.land(
            frame(LayoutKind.TERMINAL_MINIMAL, StatusPlace.STRIP),
            Bar.STATUS_BAR, Hold.hidden()).scope);
    }

    @Test public void placesNameTheScopeOfARearrangement() {
        assertEquals(Scope.EVERY_PAGE, BarLandingPolicy.places(workspace(), Bar.STATUS_BAR).scope);
        assertEquals(Scope.WORKSPACE_PAGES,
            BarLandingPolicy.places(workspace(), Bar.APPS_ROW).scope);
    }
}
