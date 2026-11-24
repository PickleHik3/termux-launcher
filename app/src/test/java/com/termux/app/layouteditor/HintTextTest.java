package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Places;
import com.termux.app.layouteditor.BarLandingPolicy.Scope;
import com.termux.app.layouteditor.HintText.Line;
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

/** The hint's line and reach for each state; plain JVM. */
public class HintTextTest {

    private static FrameState state(LayoutKind kind) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        PlaceLayout layout = new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
        return new FrameState(layout, PlaceOrientation.PORTRAIT, kind, StatusPlace.STRIP);
    }

    @Test
    public void restIsOneLineUntilSomethingIsHidden() {
        assertEquals(Line.REST, HintText.atRest(false).line);
        assertEquals(Line.REST_HIDDEN, HintText.atRest(true).line);
        assertNull(HintText.atRest(true).bar);
        assertNull(HintText.atRest(true).scope);
    }

    @Test
    public void selectedBarReadsItsReachFromThePolicy() {
        Places places = BarLandingPolicy.places(state(LayoutKind.HOME), Bar.APPS_ROW);
        HintText hint = HintText.selected(Bar.APPS_ROW, false, places.scope, places.hideScope);
        assertEquals(Line.SELECTED_MOVABLE, hint.line);
        assertEquals(Bar.APPS_ROW, hint.bar);
        assertEquals(Scope.HOME_PAGES, hint.scope);
    }

    @Test
    public void selectedReachFollowsTheLayoutKind() {
        Places workspace = BarLandingPolicy.places(state(LayoutKind.TERMINAL), Bar.EXTRA_KEYS);
        Places minimal = BarLandingPolicy.places(state(LayoutKind.TERMINAL_MINIMAL), Bar.EXTRA_KEYS);
        assertEquals(Scope.WORKSPACE_PAGES,
            HintText.selected(Bar.EXTRA_KEYS, false, workspace.scope, workspace.hideScope).scope);
        assertEquals(Scope.MINIMAL_LAYOUT,
            HintText.selected(Bar.EXTRA_KEYS, false, minimal.scope, minimal.hideScope).scope);
    }

    @Test
    public void statusBarMovesOnEveryPageButHidesOnItsLayout() {
        Places places = BarLandingPolicy.places(state(LayoutKind.TERMINAL), Bar.STATUS_BAR);
        assertEquals(Scope.EVERY_PAGE,
            HintText.selected(Bar.STATUS_BAR, false, places.scope, places.hideScope).scope);
        HintText hidden = HintText.selected(Bar.STATUS_BAR, true, places.scope, places.hideScope);
        assertEquals(Line.SELECTED_HIDDEN, hidden.line);
        assertEquals(Scope.WORKSPACE_PAGES, hidden.scope);
    }

    @Test
    public void aBarThatCannotMoveOffersHideOnly() {
        HintText hint = HintText.selected(Bar.PAGE_BAR, false, null, Scope.HOME_PAGES);
        assertEquals(Line.SELECTED_HIDE_ONLY, hint.line);
        assertEquals(Scope.HOME_PAGES, hint.scope);
    }

    @Test
    public void draggingOverAnEdgeSaysPlaceWithTheLandingsScope() {
        FrameState state = state(LayoutKind.TERMINAL);
        Landing landing = BarLandingPolicy.land(state, Bar.APPS_ROW, Hold.edge(Edge.TOP, 0));
        HintText hint = HintText.dragging(Bar.APPS_ROW, landing);
        assertEquals(Line.DRAG_PLACE, hint.line);
        assertEquals(Bar.APPS_ROW, hint.bar);
        assertEquals(Scope.WORKSPACE_PAGES, hint.scope);
    }

    @Test
    public void draggingOverTheColumnSaysHide() {
        Landing landing = BarLandingPolicy.land(state(LayoutKind.HOME), Bar.AZ_INDEX, Hold.hidden());
        HintText hint = HintText.dragging(Bar.AZ_INDEX, landing);
        assertEquals(Line.DRAG_HIDE, hint.line);
        assertEquals(Scope.HOME_PAGES, hint.scope);
    }

    @Test
    public void draggingTheStatusBarOverThePaneSaysEveryPage() {
        Landing landing = BarLandingPolicy.land(state(LayoutKind.TERMINAL), Bar.STATUS_BAR, Hold.pane());
        HintText hint = HintText.dragging(Bar.STATUS_BAR, landing);
        assertEquals(Line.DRAG_PANE, hint.line);
        assertEquals(Scope.EVERY_PAGE, hint.scope);
    }

    @Test
    public void draggingWhereItMayNotGoIsRefusedWithNoName() {
        Landing landing = BarLandingPolicy.land(state(LayoutKind.TERMINAL), Bar.KEYBOARD, Hold.edge(Edge.TOP, 0));
        HintText hint = HintText.dragging(Bar.KEYBOARD, landing);
        assertEquals(Line.DRAG_REFUSED, hint.line);
        assertNull(hint.bar);
        assertNull(hint.scope);
        assertEquals(hint, HintText.dragging(Bar.AZ_INDEX, null));
    }

    @Test
    public void equalHintsCompareEqualSoTheViewCanSkipSetText() {
        assertEquals(HintText.atRest(true), HintText.atRest(true));
        assertNotEquals(HintText.atRest(true), HintText.atRest(false));
        assertNotEquals(
            HintText.selected(Bar.APPS_ROW, false, Scope.HOME_PAGES, Scope.HOME_PAGES),
            HintText.selected(Bar.APPS_ROW, false, Scope.MINIMAL_LAYOUT, Scope.MINIMAL_LAYOUT));
    }
}
