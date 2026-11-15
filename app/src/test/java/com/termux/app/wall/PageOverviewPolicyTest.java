package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageOverviewPolicy.Kind;
import com.termux.app.wall.PageOverviewPolicy.Removal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The page overview's rules: one card per page, the Terminal page one card however many windows
 * it holds, where a dragged card may land, where an added card goes, removal with and without
 * undo, and Add display.
 */
public class PageOverviewPolicyTest {

    /** A strip from its shape: H a Home page, T the Terminal page, D a Display page, A Assistant. */
    private static List<Kind> strip(String shape) {
        List<Kind> out = new ArrayList<>();
        for (char c : shape.toCharArray()) {
            out.add(c == 'H' ? Kind.HOME_PAGE : c == 'T' ? Kind.TERMINAL
                : c == 'A' ? Kind.ASSISTANT : Kind.DISPLAY);
        }
        return out;
    }

    @Test
    public void theTerminalPageIsOneCardWhateverItsWindows() {
        // The strip is read from the wall's pages alone: the windows are not an input to it.
        List<Integer> order = PageOverviewPolicy.wallOrder(2, true, 1,
            new int[] {2}, new boolean[] {true});
        List<Kind> kinds = PageOverviewPolicy.kinds(order, new boolean[] {false});
        assertEquals(strip("HTHD"), kinds);
        for (int windows : new int[] {0, 1, 4}) {
            int front = PageOverviewPolicy.frontWindow(windows - 1, windows);
            assertEquals("the window in front with " + windows,
                Math.max(0, windows - 1), front);
            assertEquals("tapping it opens the window in front with " + windows,
                WallRow.window(front), PageOverviewPolicy.wallPosition(1, kinds, front));
        }
    }

    @Test
    public void aWallWithoutATerminalPageHasNoTerminalCard() {
        List<Kind> kinds = PageOverviewPolicy.kinds(
            PageOverviewPolicy.wallOrder(2, false, 0, new int[0], new boolean[0]), new boolean[0]);
        assertEquals(strip("HH"), kinds);
        assertEquals(PageOverviewPolicy.NONE, PageOverviewPolicy.terminalAt(kinds));
    }

    @Test
    public void theFrontWindowIsTheRowsCurrentOneClamped() {
        assertEquals(2, PageOverviewPolicy.frontWindow(2, 4));
        assertEquals(3, PageOverviewPolicy.frontWindow(9, 4));
        assertEquals(0, PageOverviewPolicy.frontWindow(-1, 4));
        assertEquals("no window", 0, PageOverviewPolicy.frontWindow(0, 0));
    }

    @Test
    public void theTerminalCardIsDrawnLiveOnlyWhileItHasAWindow() {
        assertFalse(PageOverviewPolicy.drawsLive(Kind.TERMINAL, 0));
        assertTrue(PageOverviewPolicy.drawsLive(Kind.TERMINAL, 1));
        assertTrue(PageOverviewPolicy.drawsLive(Kind.TERMINAL, 4));
        assertTrue(PageOverviewPolicy.drawsLive(Kind.HOME_PAGE, 0));
        assertFalse(PageOverviewPolicy.drawsLive(Kind.DISPLAY, 0));
        assertFalse(PageOverviewPolicy.drawsLive(Kind.ASSISTANT, 0));
    }

    @Test
    public void theTerminalCardMovesLikeAHomeCard() {
        List<Kind> s = strip("HHTHD");
        assertEquals("to the front", 0, PageOverviewPolicy.dropTarget(2, 0, s));
        assertEquals("one place right", 3, PageOverviewPolicy.dropTarget(2, 3, s));
        assertEquals("past a Display page", 4, PageOverviewPolicy.dropTarget(2, 4, s));
        assertEquals("its own place", 2, PageOverviewPolicy.dropTarget(2, 2, s));
        assertEquals("off the strip", PageOverviewPolicy.NONE,
            PageOverviewPolicy.dropTarget(2, 5, s));
    }

    @Test
    public void aHomeCardLandsAnywhereTheTerminalCardTakesNoInside() {
        List<Kind> s = strip("HTDH");
        assertEquals("onto the Terminal card's place", 1, PageOverviewPolicy.dropTarget(0, 1, s));
        assertEquals("past the Terminal and Display cards", 3,
            PageOverviewPolicy.dropTarget(0, 3, s));
        assertEquals(0, PageOverviewPolicy.dropTarget(3, 0, s));
        assertEquals("a Display card stays", PageOverviewPolicy.NONE,
            PageOverviewPolicy.dropTarget(2, 0, s));
        assertEquals("an Assistant card stays", PageOverviewPolicy.NONE,
            PageOverviewPolicy.dropTarget(1, 0, strip("HAT")));
    }

    @Test
    public void theHeldCardMakesRoomWhereItWouldLand() {
        assertEquals("held where it cannot land: nothing moves",
            2, PageOverviewPolicy.shiftedPlace(2, 0, 0));
        // The Terminal card (1) held at 3: the cards between move up one place.
        assertEquals(3, PageOverviewPolicy.shiftedPlace(1, 1, 3));
        assertEquals(1, PageOverviewPolicy.shiftedPlace(2, 1, 3));
        assertEquals(2, PageOverviewPolicy.shiftedPlace(3, 1, 3));
        assertEquals(0, PageOverviewPolicy.shiftedPlace(0, 1, 3));
        // Card 3 held at 0: cards 0 to 2 move down one place.
        assertEquals(1, PageOverviewPolicy.shiftedPlace(0, 3, 0));
        assertEquals(3, PageOverviewPolicy.shiftedPlace(2, 3, 0));
    }

    @Test
    public void addPageGoesAfterTheFocusedHomePageOrAfterTheLastHomePage() {
        List<Kind> s = strip("HTHH");
        assertEquals(1, PageOverviewPolicy.addPageIndex(0, s));
        assertEquals(4, PageOverviewPolicy.addPageIndex(3, s));
        assertEquals("the Terminal card focused: after the last Home page",
            4, PageOverviewPolicy.addPageIndex(1, s));
        assertEquals("no Home page", 0, PageOverviewPolicy.addPageIndex(0, strip("T")));
    }

    @Test
    public void onlyOneDisplayCanBeAdded() {
        assertTrue(PageOverviewPolicy.canAddDisplay(false));
        assertFalse(PageOverviewPolicy.canAddDisplay(true));
    }

    @Test
    public void theLastHomePageAndTheTerminalPageHaveNoRemoveButton() {
        assertFalse(PageOverviewPolicy.canDelete(Kind.HOME_PAGE, 1));
        assertTrue(PageOverviewPolicy.canDelete(Kind.HOME_PAGE, 2));
        assertFalse("its menu's Remove Terminal asks first",
            PageOverviewPolicy.canDelete(Kind.TERMINAL, 2));
        assertTrue(PageOverviewPolicy.canDelete(Kind.DISPLAY, 1));
        assertTrue(PageOverviewPolicy.canDelete(Kind.ASSISTANT, 1));
    }

    @Test
    public void somethingThatHoldsAnythingGoesWithUndoAndEmptyThingsGoAtOnce() {
        assertEquals(Removal.REFUSED, PageOverviewPolicy.removal(Kind.HOME_PAGE, 1, 0));
        assertEquals(Removal.AT_ONCE, PageOverviewPolicy.removal(Kind.HOME_PAGE, 2, 0));
        assertEquals(Removal.WITH_UNDO, PageOverviewPolicy.removal(Kind.HOME_PAGE, 2, 3));
        assertEquals(Removal.REFUSED, PageOverviewPolicy.removal(Kind.TERMINAL, 2, 0));
        assertEquals(Removal.WITH_UNDO, PageOverviewPolicy.removal(Kind.ASSISTANT, 2, 0));
        assertEquals("an open display always", Removal.WITH_UNDO,
            PageOverviewPolicy.removal(Kind.DISPLAY, 2, 0));
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
        List<Kind> s = strip("HTHDA");
        assertEquals(WallRow.homePage(0), PageOverviewPolicy.wallPosition(0, s, 2));
        assertEquals("the window in front", WallRow.window(2),
            PageOverviewPolicy.wallPosition(1, s, 2));
        assertEquals(WallRow.homePage(1), PageOverviewPolicy.wallPosition(2, s, 2));
        assertEquals(PaneWallPage.DISPLAY, PageOverviewPolicy.wallPosition(3, s, 2).kind);
        assertEquals(PaneWallPage.ASSISTANT, PageOverviewPolicy.wallPosition(4, s, 2).kind);
    }

    @Test
    public void theOverviewOpensOnThePageTheWallRestsOn() {
        List<Kind> s = strip("HTHH");
        assertEquals("Home page 2", 2, PageOverviewPolicy.openingFocus(s, true, 1));
        assertEquals("on the Terminal page", 1, PageOverviewPolicy.openingFocus(s, false, 0));
        assertEquals("clamped to the last Home page", 3,
            PageOverviewPolicy.openingFocus(s, true, 9));
        assertEquals("no Terminal page: the Home card", 1,
            PageOverviewPolicy.openingFocus(strip("HH"), false, 1));
        assertEquals("no Home card: the first card", 0,
            PageOverviewPolicy.openingFocus(strip("DT"), true, 0));
        assertEquals(PageOverviewPolicy.NONE,
            PageOverviewPolicy.openingFocus(new ArrayList<>(), true, 0));
    }

    @Test
    public void theStripsKindsFollowTheWallOrder() {
        List<Integer> order = Arrays.asList(0, PageOverviewPolicy.TERMINAL_PAGE, -1, 1, -2);
        assertEquals(strip("HTDHA"),
            PageOverviewPolicy.kinds(order, new boolean[] {false, true}));
        assertEquals(1, PageOverviewPolicy.terminalAt(strip("HTDHA")));
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
