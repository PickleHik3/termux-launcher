package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DisplayAppChipsTest {
    @Test public void emptyDisplayHasNoChips() {
        DisplayAppChips chips = new DisplayAppChips(Collections.<Integer>emptyList(), -1);
        assertEquals(0, chips.count());
        assertEquals(-1, chips.selected());
        assertEquals(-1, chips.tapWindowId(0));
    }

    @Test public void oneAppIsOneSelectedChip() {
        DisplayAppChips chips = new DisplayAppChips(Arrays.asList(42), 0);
        assertEquals(1, chips.count());
        assertEquals(0, chips.selected());
        assertEquals(42, chips.tapWindowId(0));
        assertEquals(42, chips.closeWindowId(0));
    }

    @Test public void threeAppsSelectTheFrontOneAndMapIds() {
        DisplayAppChips chips = new DisplayAppChips(Arrays.asList(7, 8, 9), 1);
        assertEquals(3, chips.count());
        assertEquals(1, chips.selected());
        assertEquals(9, chips.tapWindowId(2));
        assertEquals(8, chips.closeWindowId(1));
        assertEquals(-1, chips.closeWindowId(3));
        assertEquals(-1, chips.tapWindowId(-1));
    }

    @Test public void outOfRangeFrontMeansNoSelection() {
        assertEquals(-1, new DisplayAppChips(Arrays.asList(1, 2), 5).selected());
    }
}
