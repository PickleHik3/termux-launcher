package com.termux.app.terminal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WindowReorderPolicyTest {

    /** Four 40px chips with 4px gaps: 0-40, 44-84, 88-128, 132-172; centres 20, 64, 108, 152. */
    private static final int[] LEFTS = {0, 44, 88, 132};
    private static final int[] RIGHTS = {40, 84, 128, 172};
    private static final float MARGIN = 6f;

    private static int drop(int from, float centre, int previous) {
        return WindowReorderPolicy.dropIndex(LEFTS, RIGHTS, from, centre, previous, MARGIN, null);
    }

    @Test
    public void atRestTheChipDropsWhereItWas() {
        assertEquals(0, drop(0, 20f, 0));
        assertEquals(2, drop(2, 108f, 2));
        assertEquals(3, drop(3, 152f, 3));
    }

    @Test
    public void draggingRightPastOneAndTwoNeighbours() {
        assertEquals(0, drop(0, 64f, 0));
        assertEquals(1, drop(0, 71f, 0));
        assertEquals(2, drop(0, 115f, 0));
        assertEquals(2, drop(0, 115f, 1));
        assertEquals(3, drop(0, 400f, 0));
    }

    @Test
    public void draggingLeftPastOneAndTwoNeighbours() {
        assertEquals(3, drop(3, 108f, 3));
        assertEquals(2, drop(3, 101f, 3));
        assertEquals(1, drop(3, 57f, 3));
        assertEquals(0, drop(3, -50f, 3));
    }

    @Test
    public void theBoundaryHoldsWhicheverSideTheChipCameFrom() {
        // Between 58 and 70 around chip 1's centre (64) the answer is whatever it was.
        assertEquals(0, drop(0, 66f, 0));
        assertEquals(1, drop(0, 66f, 1));
        assertEquals(1, drop(0, 60f, 1));
        assertEquals(0, drop(0, 57f, 1));
    }

    @Test
    public void firstAndLastPlacesAreTheEnds() {
        assertEquals(3, drop(1, 10_000f, 1));
        assertEquals(0, drop(2, -10_000f, 2));
    }

    @Test
    public void aBarWithOneChipLiftsNothing() {
        int[] lefts = {0};
        int[] rights = {40};
        assertFalse(WindowReorderPolicy.canLift(0, 1, null));
        assertEquals(0, WindowReorderPolicy.dropIndex(lefts, rights, 0, 500f, 0, MARGIN, null));
        assertArrayEquals(lefts, WindowReorderPolicy.shiftedLefts(lefts, rights, 0, 0, null));
    }

    @Test
    public void floatChipsNeitherLiftNorShiftNorAreCrossed() {
        // Chips 0-2 are the row's windows, chip 3 a Home float.
        boolean[] movable = {true, true, true, false};
        assertFalse(WindowReorderPolicy.canLift(3, 4, movable));
        assertTrue(WindowReorderPolicy.canLift(1, 4, movable));
        assertEquals(3, WindowReorderPolicy.dropIndex(LEFTS, RIGHTS, 3, -100f, 3, MARGIN, movable));
        // The lifted window stops at the end of its run, short of the float.
        assertEquals(2, WindowReorderPolicy.dropIndex(LEFTS, RIGHTS, 0, 10_000f, 0, MARGIN,
            movable));
        int[] shifted = WindowReorderPolicy.shiftedLefts(LEFTS, RIGHTS, 0, 2, movable);
        assertEquals(132, shifted[3]);
        // All floats: nothing can be lifted at all.
        boolean[] floats = {false, false};
        assertFalse(WindowReorderPolicy.canLift(0, 2, floats));
    }

    @Test
    public void othersPartForTheHoveringChip() {
        // Chip 0 hovering at 2: chips 1 and 2 close up to the start, chip 0's slot is third.
        assertArrayEquals(new int[]{88, 0, 44, 132},
            WindowReorderPolicy.shiftedLefts(LEFTS, RIGHTS, 0, 2, null));
        // Chip 3 hovering at 0: everyone else moves along one.
        assertArrayEquals(new int[]{44, 88, 132, 0},
            WindowReorderPolicy.shiftedLefts(LEFTS, RIGHTS, 3, 0, null));
        // Hovering where it was, nobody moves.
        assertArrayEquals(LEFTS, WindowReorderPolicy.shiftedLefts(LEFTS, RIGHTS, 1, 1, null));
    }

    @Test
    public void unequalWidthsPackInTheNewOrder() {
        int[] lefts = {0, 104, 148};
        int[] rights = {100, 144, 248};
        // The wide first chip hovering last: the narrow one leads, the other wide one follows.
        assertArrayEquals(new int[]{148, 0, 44},
            WindowReorderPolicy.shiftedLefts(lefts, rights, 0, 2, null));
    }

    @Test
    public void autoScrollOnlyNearAnEdgeAndOnlyWhereThereIsRoom() {
        // Viewport 300, zone 30, steps up to 12.
        assertEquals(0, WindowReorderPolicy.autoScrollStep(150f, 300, 30, 12, 50, 200));
        assertEquals(-12, WindowReorderPolicy.autoScrollStep(0f, 300, 30, 12, 50, 200));
        assertEquals(-6, WindowReorderPolicy.autoScrollStep(15f, 300, 30, 12, 50, 200));
        assertEquals(12, WindowReorderPolicy.autoScrollStep(300f, 300, 30, 12, 50, 200));
        // At either end the strip has nowhere to go.
        assertEquals(0, WindowReorderPolicy.autoScrollStep(0f, 300, 30, 12, 0, 200));
        assertEquals(0, WindowReorderPolicy.autoScrollStep(300f, 300, 30, 12, 200, 200));
        // Never past the end.
        assertEquals(3, WindowReorderPolicy.autoScrollStep(300f, 300, 30, 12, 197, 200));
        // A bar that fits never scrolls.
        assertEquals(0, WindowReorderPolicy.autoScrollStep(0f, 300, 30, 12, 0, 0));
    }

    @Test
    public void aMostlyVerticalMoveLeavesTheRow() {
        assertFalse(WindowReorderPolicy.leavesRow(80f, 30f, 24f));
        assertFalse(WindowReorderPolicy.leavesRow(0f, 20f, 24f));
        assertTrue(WindowReorderPolicy.leavesRow(10f, 30f, 24f));
    }

    @Test
    public void moveKeepsTheCurrentWindowCurrent() {
        List<String> row = new ArrayList<>(Arrays.asList("1", "2", "3"));
        // Window 1 is current and dragged to the end: still current, now third.
        assertEquals(2, WindowReorderPolicy.move(row, 0, 2, 0));
        assertEquals(Arrays.asList("2", "3", "1"), row);
        // Another window moved across the current one shifts it.
        assertEquals(1, WindowReorderPolicy.move(row, 0, 2, 2));
        assertEquals(Arrays.asList("3", "1", "2"), row);
        assertEquals(1, WindowReorderPolicy.move(row, 2, 0, 0));
        assertEquals(Arrays.asList("2", "3", "1"), row);
        // A window moved without crossing the current one leaves it be.
        assertEquals(0, WindowReorderPolicy.move(row, 1, 2, 0));
        assertEquals(Arrays.asList("2", "1", "3"), row);
        // Nothing to do.
        assertEquals(1, WindowReorderPolicy.move(row, 1, 1, 1));
        assertEquals(1, WindowReorderPolicy.move(row, 1, 5, 1));
        assertEquals(Arrays.asList("2", "1", "3"), row);
    }

    @Test
    public void targetIndexReadsLeftRightAndPlaces() {
        assertEquals(0, WindowReorderPolicy.targetIndex("left", 1, 3));
        assertEquals(0, WindowReorderPolicy.targetIndex("left", 0, 3));
        assertEquals(2, WindowReorderPolicy.targetIndex("RIGHT", 1, 3));
        assertEquals(2, WindowReorderPolicy.targetIndex("right", 2, 3));
        assertEquals(0, WindowReorderPolicy.targetIndex("1", 2, 3));
        assertEquals(2, WindowReorderPolicy.targetIndex(" 3 ", 0, 3));
        assertEquals(WindowReorderPolicy.INVALID, WindowReorderPolicy.targetIndex("0", 0, 3));
        assertEquals(WindowReorderPolicy.INVALID, WindowReorderPolicy.targetIndex("4", 0, 3));
        assertEquals(WindowReorderPolicy.INVALID, WindowReorderPolicy.targetIndex("up", 0, 3));
        assertEquals(WindowReorderPolicy.INVALID, WindowReorderPolicy.targetIndex(null, 0, 3));
        assertEquals(WindowReorderPolicy.INVALID, WindowReorderPolicy.targetIndex("1", 0, 0));
    }
}
