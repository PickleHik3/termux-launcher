package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** The slot a lifted dock icon left: hidden in the air, shown again however the lift ends. */
public class LiftSourceSlotTest {

    private final Map<String, Boolean> shown = new HashMap<>();
    private final LiftSourceSlot<String> source =
        new LiftSourceSlot<>((slot, isShown) -> shown.put(slot, isShown));

    @Test
    public void theSlotIsHiddenWhileTheIconIsInTheAirAndShownAtTheEnd() {
        assertTrue(source.hide("a"));
        assertEquals(Boolean.FALSE, shown.get("a"));
        source.end();
        assertEquals(Boolean.TRUE, shown.get("a"));
        assertNull(source.hidden());
    }

    /**
     * The row drew itself again during the lift (a page turned under the icon at the screen's
     * edge, or the wall turned to Home): the new slot is the hidden one, and the end shows it,
     * not only the slot that has left the row. A drawing that matched the one on screen was
     * skipped by the row, so drawing it again never brought this slot back.
     */
    @Test
    public void aRowDrawnAgainDuringTheLiftHandsItsSlotToTheLift() {
        source.hide("old");
        source.hide("new");
        assertEquals(Boolean.TRUE, shown.get("old"));
        assertEquals(Boolean.FALSE, shown.get("new"));
        assertEquals("new", source.hidden());
        source.end();
        assertEquals(Boolean.TRUE, shown.get("new"));
    }

    @Test
    public void theSameSlotDrawnAgainStaysHidden() {
        source.hide("a");
        source.hide("a");
        assertEquals(Boolean.FALSE, shown.get("a"));
    }

    /** Every way out ends it, more than one may say so, and the first one shows the slot. */
    @Test
    public void theEndIsOnceAndNothingIsHiddenAfterIt() {
        source.hide("a");
        source.end();
        shown.clear();
        source.end();
        assertTrue("a second end does nothing", shown.isEmpty());
        assertFalse("a row drawn after the end hides nothing", source.hide("b"));
        assertTrue(shown.isEmpty());
        assertTrue(source.isEnded());
    }

    @Test
    public void anEndWithNothingHiddenDoesNothing() {
        source.end();
        assertTrue(shown.isEmpty());
    }
}
