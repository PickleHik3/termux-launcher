package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The Workspace's chips are its row in row order, the display window's chip among them like any
 * window's (ADR 0008, amended 2026-10-19), then the Home floats.
 */
public class WindowChipTargetTest {

    @Test
    public void everyChipOfTheRowIsAWindowsTheDisplaysIncluded() {
        // a D b: the display's chip is the row's second, a window chip like the others.
        for (int chip = 0; chip < 3; chip++) {
            WindowChipTarget target = WindowChipTarget.of(chip, 3, 0);
            assertEquals(WindowChipTarget.Kind.WINDOW, target.kind);
            assertEquals(chip, target.index);
        }
    }

    @Test
    public void floatChipsFollowTheRowsChips() {
        WindowChipTarget first = WindowChipTarget.of(3, 3, 2);
        assertEquals(WindowChipTarget.Kind.HOME_FLOAT, first.kind);
        assertEquals(0, first.index);
        WindowChipTarget second = WindowChipTarget.of(4, 3, 2);
        assertEquals(WindowChipTarget.Kind.HOME_FLOAT, second.kind);
        assertEquals(1, second.index);
    }

    @Test
    public void chipsPastTheEndAndNegativeAreNothing() {
        assertEquals(WindowChipTarget.Kind.NONE, WindowChipTarget.of(-1, 3, 0).kind);
        assertEquals(WindowChipTarget.Kind.NONE, WindowChipTarget.of(5, 3, 1).kind);
    }

    @Test
    public void theSelectedChipIsTheRowsWindowInFrontHeldToTheRow() {
        assertEquals("the display's place", 1, WindowChipTarget.selectedChip(1, 3));
        assertEquals(2, WindowChipTarget.selectedChip(7, 3));
        assertEquals(0, WindowChipTarget.selectedChip(-2, 3));
    }
}
