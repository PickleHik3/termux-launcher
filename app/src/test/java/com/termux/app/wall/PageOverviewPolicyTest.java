package com.termux.app.wall;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageOverviewPolicy.Kind;
import com.termux.app.wall.PageOverviewPolicy.Removal;

import org.junit.Test;

/**
 * The page overview's rules: the two halves of the strip, dragging inside a half only, where an
 * added card goes, removal with and without undo, and Add display.
 */
public class PageOverviewPolicyTest {

    // A strip of 3 Home pages, then 3 windows with the display at row place 1.
    private static final int HOME = 3;
    private static final int ROW = 3;
    private static final int SLOT = 1;

    @Test
    public void homePagesComeFirstThenTheWindowsWithTheDisplayWhereItsSlotSays() {
        assertEquals(6, PageOverviewPolicy.cardCount(HOME, ROW));
        assertEquals(Kind.HOME_PAGE, PageOverviewPolicy.kindAt(0, HOME, SLOT));
        assertEquals(Kind.HOME_PAGE, PageOverviewPolicy.kindAt(2, HOME, SLOT));
        assertEquals(Kind.TERMINAL, PageOverviewPolicy.kindAt(3, HOME, SLOT));
        assertEquals(Kind.DISPLAY, PageOverviewPolicy.kindAt(4, HOME, SLOT));
        assertEquals(Kind.TERMINAL, PageOverviewPolicy.kindAt(5, HOME, SLOT));
        assertEquals("no display: every window is a terminal",
            Kind.TERMINAL, PageOverviewPolicy.kindAt(4, HOME, DisplaySlot.NONE));
        assertEquals(1, PageOverviewPolicy.rowPlace(4, HOME));
        assertEquals(4, PageOverviewPolicy.positionOfRow(1, HOME));
    }

    @Test
    public void eachHalfHasItsOwnBounds() {
        assertArrayEquals(new int[] {0, 2}, PageOverviewPolicy.halfBounds(1, HOME, ROW));
        assertArrayEquals(new int[] {3, 5}, PageOverviewPolicy.halfBounds(3, HOME, ROW));
        assertTrue(PageOverviewPolicy.sameHalf(0, 2, HOME));
        assertTrue(PageOverviewPolicy.sameHalf(3, 5, HOME));
        assertFalse(PageOverviewPolicy.sameHalf(2, 3, HOME));
    }

    @Test
    public void aDropInsideItsHalfLandsAndADropAcrossTheSeamIsRefused() {
        assertEquals(0, PageOverviewPolicy.dropTarget(2, 0, HOME, ROW));
        assertEquals(5, PageOverviewPolicy.dropTarget(3, 5, HOME, ROW));
        assertEquals("a Home page over a window springs back",
            PageOverviewPolicy.NONE, PageOverviewPolicy.dropTarget(2, 3, HOME, ROW));
        assertEquals("a window over a Home page springs back",
            PageOverviewPolicy.NONE, PageOverviewPolicy.dropTarget(3, 2, HOME, ROW));
        assertEquals("off the strip", PageOverviewPolicy.NONE,
            PageOverviewPolicy.dropTarget(1, 6, HOME, ROW));
        assertEquals("its own place", 1, PageOverviewPolicy.dropTarget(1, 1, HOME, ROW));
    }

    @Test
    public void onlyTheHeldCardsHalfMakesRoom() {
        assertEquals("held across the seam: nothing moves",
            2, PageOverviewPolicy.shiftedPlace(2, 0, 0));
        // Page 0 held at 2: pages 1 and 2 move up one place; the windows stay.
        assertEquals(2, PageOverviewPolicy.shiftedPlace(0, 0, 2));
        assertEquals(0, PageOverviewPolicy.shiftedPlace(1, 0, 2));
        assertEquals(1, PageOverviewPolicy.shiftedPlace(2, 0, 2));
        assertEquals(3, PageOverviewPolicy.shiftedPlace(3, 0, 2));
        // Window 5 held at 3: windows 3 and 4 move down one place.
        assertEquals(4, PageOverviewPolicy.shiftedPlace(3, 5, 3));
        assertEquals(5, PageOverviewPolicy.shiftedPlace(4, 5, 3));
        assertEquals(2, PageOverviewPolicy.shiftedPlace(2, 5, 3));
    }

    @Test
    public void addPageGoesAfterTheFocusedPageOrAfterTheLastPageFromAWindow() {
        assertEquals(1, PageOverviewPolicy.addPageIndex(0, HOME));
        assertEquals(3, PageOverviewPolicy.addPageIndex(2, HOME));
        assertEquals("a window focused: after the last Home page",
            3, PageOverviewPolicy.addPageIndex(4, HOME));
    }

    @Test
    public void addWindowGoesAfterTheFocusedWindowOrFirstFromAHomePage() {
        assertEquals(1, PageOverviewPolicy.addWindowRow(3, HOME, ROW));
        assertEquals(3, PageOverviewPolicy.addWindowRow(5, HOME, ROW));
        assertEquals("a Home page focused: the first window",
            0, PageOverviewPolicy.addWindowRow(1, HOME, ROW));
        assertEquals("an empty Workspace", 0, PageOverviewPolicy.addWindowRow(2, HOME, 0));
    }

    @Test
    public void onlyOneDisplayCanBeAdded() {
        assertTrue(PageOverviewPolicy.canAddDisplay(false));
        assertFalse(PageOverviewPolicy.canAddDisplay(true));
    }

    @Test
    public void theLastHomePageHasNoDeleteAndEverythingElseDoes() {
        assertFalse(PageOverviewPolicy.canDelete(Kind.HOME_PAGE, 1));
        assertTrue(PageOverviewPolicy.canDelete(Kind.HOME_PAGE, 2));
        assertTrue(PageOverviewPolicy.canDelete(Kind.TERMINAL, 1));
        assertTrue(PageOverviewPolicy.canDelete(Kind.DISPLAY, 1));
    }

    @Test
    public void somethingThatHoldsAnythingGoesWithUndoAndEmptyThingsGoAtOnce() {
        assertEquals(Removal.REFUSED, PageOverviewPolicy.removal(Kind.HOME_PAGE, 1, 0, false));
        assertEquals(Removal.AT_ONCE, PageOverviewPolicy.removal(Kind.HOME_PAGE, 2, 0, false));
        assertEquals(Removal.WITH_UNDO, PageOverviewPolicy.removal(Kind.HOME_PAGE, 2, 3, false));
        assertEquals(Removal.AT_ONCE, PageOverviewPolicy.removal(Kind.TERMINAL, 2, 0, false));
        assertEquals(Removal.WITH_UNDO, PageOverviewPolicy.removal(Kind.TERMINAL, 2, 0, true));
        assertEquals("an open display always", Removal.WITH_UNDO,
            PageOverviewPolicy.removal(Kind.DISPLAY, 2, 0, false));
    }

    @Test
    public void homePagesTheFirstTerminalAndTheDisplayHaveTheHomeButton() {
        assertTrue(PageOverviewPolicy.showsHomeButton(Kind.HOME_PAGE, false));
        assertTrue("the terminal a start in the Workspace opens on",
            PageOverviewPolicy.showsHomeButton(Kind.TERMINAL, true));
        assertFalse("not every terminal", PageOverviewPolicy.showsHomeButton(Kind.TERMINAL, false));
        assertTrue(PageOverviewPolicy.showsHomeButton(Kind.DISPLAY, false));
    }

    @Test
    public void aCardHeldForUndoLeavesTheVisibleStripAndTheRestCloseUp() {
        assertEquals(1, PageOverviewPolicy.visibleOf(1, 2));
        assertEquals(PageOverviewPolicy.NONE, PageOverviewPolicy.visibleOf(2, 2));
        assertEquals(2, PageOverviewPolicy.visibleOf(3, 2));
        assertEquals(3, PageOverviewPolicy.visibleOf(3, PageOverviewPolicy.NONE));
        assertEquals(1, PageOverviewPolicy.realOf(1, 2));
        assertEquals(3, PageOverviewPolicy.realOf(2, 2));
        for (int real = 0; real < 6; real++) {
            if (real == 2) continue;
            assertEquals(real, PageOverviewPolicy.realOf(PageOverviewPolicy.visibleOf(real, 2), 2));
        }
    }

    @Test
    public void focusAfterARemovalIsTheCardThatMovedIntoItsPlace() {
        assertEquals(2, PageOverviewPolicy.focusAfterRemoval(2, 5));
        assertEquals("the last card went", 4, PageOverviewPolicy.focusAfterRemoval(5, 5));
        assertEquals(PageOverviewPolicy.NONE, PageOverviewPolicy.focusAfterRemoval(0, 0));
    }

    @Test
    public void aCardMapsToItsWallPosition() {
        assertEquals(WallRow.homePage(1), PageOverviewPolicy.wallPosition(1, HOME));
        assertEquals(WallRow.window(0), PageOverviewPolicy.wallPosition(3, HOME));
        assertEquals(WallRow.window(2), PageOverviewPolicy.wallPosition(5, HOME));
    }

    @Test
    public void theOverviewOpensOnThePageTheWallRestsOn() {
        assertEquals(1, PageOverviewPolicy.openingFocus(true, 1, 0, HOME, ROW));
        assertEquals(4, PageOverviewPolicy.openingFocus(false, 0, 1, HOME, ROW));
        assertEquals("no windows: Home", 2, PageOverviewPolicy.openingFocus(false, 2, 0, HOME, 0));
        assertEquals("clamped", 2, PageOverviewPolicy.openingFocus(true, 9, 0, HOME, ROW));
    }

    @Test
    public void aPinchInNeedsRealTravelAndAQuarterOfTheSpan() {
        assertTrue(PageOverviewPolicy.isPinchIn(400f, 250f, 60f));
        assertFalse("too little travel", PageOverviewPolicy.isPinchIn(400f, 360f, 60f));
        assertFalse("not a quarter of the span", PageOverviewPolicy.isPinchIn(1000f, 900f, 60f));
        assertFalse("spreading", PageOverviewPolicy.isPinchIn(400f, 500f, 60f));
        assertFalse("no start", PageOverviewPolicy.isPinchIn(0f, 0f, 60f));
    }
}
