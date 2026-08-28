package com.termux.app.launcher.icon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeapBudgetTest {

    private static final int MB = 1024 * 1024;

    @Test
    public void takesTheFractionBetweenTheClamps() {
        assertEquals(8 * MB, HeapBudget.of(128, 16, 4 * MB, 16 * MB));
        assertEquals(12 * MB, HeapBudget.of(96, 8, 8 * MB, 32 * MB));
    }

    @Test
    public void clampsToTheFloorAndCeiling() {
        assertEquals(4 * MB, HeapBudget.of(0, 16, 4 * MB, 16 * MB));
        assertEquals(4 * MB, HeapBudget.of(48, 16, 4 * MB, 16 * MB));
        assertEquals(16 * MB, HeapBudget.of(512, 16, 4 * MB, 16 * MB));
    }

    @Test
    public void unknownMemoryClassReadsAsTheFloor() {
        assertEquals(4 * MB, HeapBudget.of(-64, 16, 4 * MB, 16 * MB));
    }

    @Test
    public void largeHeapsDoNotOverflow() {
        assertEquals(16 * MB, HeapBudget.of(Integer.MAX_VALUE, 1, 4 * MB, 16 * MB));
    }
}
