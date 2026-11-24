package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.Slot;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/** A landing becomes the layout the draft takes; plain JVM. */
public class BarDropTest {

    private static PlaceLayout shipped() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static Landing edge(Edge edge, int index) {
        return new Landing(true, Zone.EDGE, edge, index, false, null, null);
    }

    @Test public void aBarDroppedOnAnotherEdgeStandsThere() {
        PlaceLayout next = BarDrop.layoutAfter(shipped(), Bar.APPS_ROW, edge(Edge.TOP, 1));
        assertEquals(Edge.TOP, next.slot(Element.APPS).edge);
        assertEquals(Element.STATUS, EdgeStackPolicy.stack(next, Edge.TOP).get(0));
        assertEquals(Element.APPS, EdgeStackPolicy.stack(next, Edge.TOP).get(1));
    }

    @Test public void aBarDroppedInItsOwnStackIsReordered() {
        PlaceLayout next = BarDrop.layoutAfter(shipped(), Bar.EXTRA_KEYS, edge(Edge.BOTTOM, 0));
        assertEquals(Element.EXTRA_KEYS, EdgeStackPolicy.stack(next, Edge.BOTTOM).get(0));
    }

    @Test public void aRefusedLandingWritesNothing() {
        Landing refused = new Landing(false, Zone.EDGE, Edge.TOP, 0, false, null, null);
        assertNull(BarDrop.layoutAfter(shipped(), Bar.APPS_ROW, refused));
    }

    @Test public void zonesThatAreNotAnEdgeAreNotALayoutWrite() {
        Landing hidden = new Landing(true, Zone.HIDDEN, null, 0, false, null, null);
        Landing pane = new Landing(true, Zone.PANE, null, 0, false, null, null);
        assertNull(BarDrop.layoutAfter(shipped(), Bar.APPS_ROW, hidden));
        assertNull(BarDrop.layoutAfter(shipped(), Bar.STATUS_BAR, pane));
    }

    @Test public void theKeyboardAndThePageBarHaveNoSlot() {
        assertNull(BarDrop.layoutAfter(shipped(), Bar.KEYBOARD, edge(Edge.BOTTOM, 0)));
        assertNull(BarDrop.layoutAfter(shipped(), Bar.PAGE_BAR, edge(Edge.TOP, 0)));
    }
}
