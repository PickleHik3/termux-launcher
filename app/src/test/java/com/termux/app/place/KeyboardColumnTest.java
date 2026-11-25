package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The keyboard leaves with the page it belongs to (#307, 2026-11-25). */
public class KeyboardColumnTest {

    private static final int W = 1080;

    @Test public void itLeavesOnlyWhenTheArrivingPageHasItDown() {
        // Home 2 with the floating terminal, keyboard up, to Home 1 with nothing to type into.
        assertTrue(KeyboardColumn.leavesWithPage(true, false));
        assertFalse(KeyboardColumn.leavesWithPage(true, true));
        assertFalse(KeyboardColumn.leavesWithPage(false, true));
        assertFalse(KeyboardColumn.leavesWithPage(false, false));
    }

    @Test public void itSlidesWithTheLeavingPageAndIsOffScreenAtTheLanding() {
        for (int direction : new int[] {KeyboardColumn.LEAVES_LEFT, KeyboardColumn.LEAVES_RIGHT}) {
            assertEquals(0f, KeyboardColumn.leavingTxPx(direction, 0f, W), 0f);
            assertEquals(direction * W / 2f, KeyboardColumn.leavingTxPx(direction, 0.5f, W), 0f);
            // At the landing it is a whole wall away with its page: nothing vanishes on screen.
            assertEquals(direction * (float) W, KeyboardColumn.leavingTxPx(direction, 1f, W), 0f);
        }
    }

    @Test public void heldOrReversedItFollowsTheSeam() {
        float held = KeyboardColumn.leavingTxPx(KeyboardColumn.LEAVES_LEFT, 0.3f, W);
        assertEquals(-0.3f * W, held, 0.01f);
        // Past either end it stays at that end.
        assertEquals(0f, KeyboardColumn.leavingTxPx(KeyboardColumn.LEAVES_LEFT, -0.2f, W), 0f);
        assertEquals(-W, KeyboardColumn.leavingTxPx(KeyboardColumn.LEAVES_LEFT, 1.4f, W), 0f);
    }

    @Test public void anUnknownDirectionKeepsTheScenesPose() {
        assertEquals(0f, KeyboardColumn.leavingTxPx(KeyboardColumn.LEAVES_UNKNOWN, 0.6f, W), 0f);
    }
}
