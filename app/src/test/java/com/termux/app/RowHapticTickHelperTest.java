package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RowHapticTickHelperTest {

    @Test
    public void ticksOnlyWhenEstablishedSelectionChanges() {
        assertFalse(RowHapticTickHelper.isBoundaryCrossing(-1, 0));
        assertFalse(RowHapticTickHelper.isBoundaryCrossing(4, 4));
        assertTrue(RowHapticTickHelper.isBoundaryCrossing(4, 5));
        assertTrue(RowHapticTickHelper.isBoundaryCrossing(5, 2));
        assertFalse(RowHapticTickHelper.isBoundaryCrossing(2, -1));
    }
}
