package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowChipTargetTest {

    @Test
    public void displayChipOnAWindowMovesTheWallToTheDisplay() {
        WindowChipTarget target = WindowChipTarget.of(3, false, 3, true, 0);
        assertEquals(WindowChipTarget.Kind.DISPLAY, target.kind);
        assertTrue(target.moveWall);
    }

    @Test
    public void displayChipOverTheDisplayStaysPut() {
        WindowChipTarget target = WindowChipTarget.of(3, true, 3, true, 0);
        assertEquals(WindowChipTarget.Kind.DISPLAY, target.kind);
        assertFalse(target.moveWall);
    }

    @Test
    public void windowChipOverTheDisplayGoesBackToThatWindow() {
        WindowChipTarget target = WindowChipTarget.of(1, true, 3, true, 0);
        assertEquals(WindowChipTarget.Kind.WINDOW, target.kind);
        assertEquals(1, target.index);
        assertTrue(target.moveWall);
    }

    @Test
    public void windowChipOnAWindowSwitchesInPlace() {
        WindowChipTarget target = WindowChipTarget.of(2, false, 3, true, 0);
        assertEquals(WindowChipTarget.Kind.WINDOW, target.kind);
        assertEquals(2, target.index);
        assertFalse(target.moveWall);
    }

    @Test
    public void floatChipsFollowTheDisplaysChip() {
        WindowChipTarget withDisplay = WindowChipTarget.of(4, true, 3, true, 2);
        assertEquals(WindowChipTarget.Kind.HOME_FLOAT, withDisplay.kind);
        assertEquals(0, withDisplay.index);
        WindowChipTarget without = WindowChipTarget.of(4, false, 3, false, 2);
        assertEquals(WindowChipTarget.Kind.HOME_FLOAT, without.kind);
        assertEquals(1, without.index);
    }

    @Test
    public void chipsPastTheEndAndNegativeAreNothing() {
        assertEquals(WindowChipTarget.Kind.NONE, WindowChipTarget.of(-1, false, 3, true, 0).kind);
        assertEquals(WindowChipTarget.Kind.NONE, WindowChipTarget.of(5, false, 3, true, 1).kind);
    }

    /** The regression: over the display the bar selects the display's chip among the windows'. */
    @Test
    public void overTheDisplayTheDisplaysChipIsSelected() {
        assertEquals(3, WindowChipTarget.selectedChip(true, true, 1, 3));
        assertEquals(1, WindowChipTarget.selectedChip(false, true, 1, 3));
        assertEquals(2, WindowChipTarget.selectedChip(false, true, 7, 3));
    }
}
