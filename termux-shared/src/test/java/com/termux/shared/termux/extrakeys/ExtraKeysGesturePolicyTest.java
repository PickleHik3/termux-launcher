package com.termux.shared.termux.extrakeys;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExtraKeysGesturePolicyTest {

    @Test
    public void leftDominantSwipeTurnsToTheNextPage() {
        assertEquals(1, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(40f, 8f, 10f));
    }

    @Test
    public void rightDominantSwipeTurnsBackAPage() {
        assertEquals(-1, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(-40f, 2f, 10f));
    }

    @Test
    public void tapsAndVerticalSwipesDoNotTurnThePage() {
        assertEquals(0, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(8f, 1f, 10f));
        assertEquals(0, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(-8f, 1f, 10f));
        assertEquals(0, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(12f, 24f, 10f));
        assertEquals(0, ExtraKeysGesturePolicy.toolbarPageSwipeDirection(-12f, 24f, 10f));
    }
}
