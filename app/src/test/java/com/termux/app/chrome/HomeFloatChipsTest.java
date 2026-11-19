package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@link HomeFloatChips}: Home's chips are the floats of the Home page shown, in opening order. */
public class HomeFloatChipsTest {

    @Test
    public void onlyTheFloatsOfThePageShownGetAChip() {
        HomeFloatChips<String> chips = new HomeFloatChips<String>(1)
            .add("a", 1, 0, false, false)
            .add("b", 2, 1, false, false)
            .add("c", 3, 2, false, false)
            .add("d", 4, 1, true, false);
        assertEquals(2, chips.size());
        assertEquals("b", chips.chips().get(0).window);
        assertEquals("d", chips.chips().get(1).window);
        assertTrue(chips.chips().get(1).minimised);
    }

    @Test
    public void aFloatOnNoPageNeverGetsOne() {
        HomeFloatChips<String> chips = new HomeFloatChips<String>(0)
            .add("hosted", 1, -1, false, true);
        assertEquals(0, chips.size());
        assertEquals(-1, chips.selected());
    }

    @Test
    public void chipsStandInTheOrderTheFloatsWereOpened() {
        // Added in z-order (the last raised last), shown in opening order: raising one never
        // reshuffles them.
        HomeFloatChips<String> chips = new HomeFloatChips<String>(0)
            .add("second", 20, 0, false, false)
            .add("third", 30, 0, false, true)
            .add("first", 10, 0, false, false);
        assertEquals("first", chips.chips().get(0).window);
        assertEquals("second", chips.chips().get(1).window);
        assertEquals("third", chips.chips().get(2).window);
        assertEquals(2, chips.selected());
    }

    @Test
    public void theSelectedChipIsTheFocusedFloatUnlessItIsMinimised() {
        assertEquals(1, new HomeFloatChips<String>(0)
            .add("a", 1, 0, false, false)
            .add("b", 2, 0, false, true).selected());
        assertEquals(-1, new HomeFloatChips<String>(0)
            .add("a", 1, 0, false, false)
            .add("b", 2, 0, true, true).selected());
        assertEquals(-1, new HomeFloatChips<String>(0)
            .add("a", 1, 0, false, false).selected());
    }

    @Test
    public void aTapBringsThatFloatForwardAndAMissIsNothing() {
        HomeFloatChips<String> chips = new HomeFloatChips<String>(0)
            .add("a", 1, 0, true, false)
            .add("b", 2, 0, false, true);
        assertEquals("a", chips.tap(0));
        assertEquals("b", chips.tap(1));
        assertNull(chips.tap(2));
        assertNull(chips.tap(-1));
    }

    @Test
    public void chipOfFindsTheFloatsChipForItsMinimiseFlight() {
        HomeFloatChips<String> chips = new HomeFloatChips<String>(0)
            .add("a", 1, 0, false, false)
            .add("b", 2, 0, false, false)
            .add("elsewhere", 3, 1, false, false);
        assertEquals(1, chips.chipOf("b"));
        assertEquals(-1, chips.chipOf("elsewhere"));
        assertFalse(chips.chips().isEmpty());
    }
}
