package com.termux.app.launcher.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomePageEditsTest {

    private static final int GONE = HomePageEdits.REMOVED;

    @Test
    public void movingAPageForwardShiftsThePagesBetweenBack() {
        assertArrayEquals(new int[] {2, 0, 1, 3}, HomePageEdits.moveMapping(4, 0, 2));
    }

    @Test
    public void movingAPageBackShiftsThePagesBetweenForward() {
        assertArrayEquals(new int[] {0, 2, 3, 1}, HomePageEdits.moveMapping(4, 3, 1));
        assertArrayEquals(new int[] {1, 0}, HomePageEdits.moveMapping(2, 1, 0));
    }

    @Test
    public void aMoveThatGoesNowhereChangesNothing() {
        assertFalse(HomePageEdits.canMove(3, 1, 1));
        assertFalse(HomePageEdits.canMove(3, 0, 3));
        assertFalse(HomePageEdits.canMove(3, -1, 0));
        assertArrayEquals(new int[] {0, 1, 2}, HomePageEdits.moveMapping(3, 1, 1));
    }

    @Test
    public void everyMoveIsAPermutation() {
        for (int count = 1; count <= 5; count++) {
            for (int from = 0; from < count; from++) {
                for (int to = 0; to < count; to++) {
                    int[] mapping = HomePageEdits.moveMapping(count, from, to);
                    boolean[] seen = new boolean[count];
                    for (int value : mapping) seen[value] = true;
                    for (boolean s : seen) assertTrue(count + ":" + from + "->" + to, s);
                    assertEquals(to, mapping[from]);
                }
            }
        }
    }

    @Test
    public void theLastPageIsNeverRemoved() {
        assertFalse(HomePageEdits.canRemove(1));
        assertTrue(HomePageEdits.canRemove(2));
    }

    @Test
    public void removingAskFirstOnlyWhenThePageHoldsSomething() {
        assertFalse(HomePageEdits.asksBeforeRemoving(0));
        assertTrue(HomePageEdits.asksBeforeRemoving(1));
    }

    @Test
    public void whatStoodOnARemovedPageLandsOnThePageNowInItsPlace() {
        int[] middle = {0, GONE, 1, 2};
        assertEquals("the page after it slid into its place", 1, HomePageEdits.remap(1, middle));
        assertEquals(0, HomePageEdits.remap(0, middle));
        assertEquals(2, HomePageEdits.remap(3, middle));
        int[] last = {0, 1, GONE};
        assertEquals("the last page went: the new last", 1, HomePageEdits.remap(2, last));
    }

    @Test
    public void theDefaultPageFollowsItsPage() {
        assertEquals(2, HomePageEdits.remap(0, HomePageEdits.moveMapping(4, 0, 2)));
        assertEquals(0, HomePageEdits.remap(1, HomePageEdits.moveMapping(4, 0, 2)));
        assertEquals(1, HomePageEdits.remap(2, new int[] {GONE, 0, 1}));
    }

    @Test
    public void aDefaultPastTheLastPageIsHeldToIt() {
        assertEquals(0, HomePageEdits.clampDefault(0, 1));
        assertEquals(2, HomePageEdits.clampDefault(5, 3));
        assertEquals(0, HomePageEdits.clampDefault(-2, 3));
        assertEquals(0, HomePageEdits.clampDefault(3, 0));
    }

    @Test
    public void pagesOutsideTheMappingAreLeftAlone() {
        assertEquals(-1, HomePageEdits.remap(-1, new int[] {0, GONE, 1}));
        assertEquals(7, HomePageEdits.remap(7, new int[] {0, GONE, 1}));
    }
}
