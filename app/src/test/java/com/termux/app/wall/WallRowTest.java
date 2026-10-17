package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The row's arithmetic (ADR 0008): the seam out of Home, steps between windows, where a new
 * window goes, Start in, and the saved row position.
 */
public class WallRowTest {

    @Test
    public void aSwipeLeavesHomeOnlyFromTheLastPageAndOnlyWhenTheRowGoesOn() {
        assertTrue(WallRow.swipeLeavesHome(2, 3, 1, true));
        assertFalse("an earlier Home page pages Home", WallRow.swipeLeavesHome(1, 3, 1, true));
        assertFalse("no window: the swipe stops", WallRow.swipeLeavesHome(2, 3, 1, false));
        assertFalse("backward never leaves Home", WallRow.swipeLeavesHome(0, 3, -1, true));
        assertTrue("one Home page is its own last page", WallRow.swipeLeavesHome(0, 1, 1, true));
        assertTrue("an empty count reads as one page", WallRow.swipeLeavesHome(0, 0, 1, true));
    }

    @Test
    public void theWorkspaceIsOnTheRowOnlyWhileTheCurrentSessionHasAWindow() {
        assertTrue(WallRow.hasWorkspace(true, 1));
        assertFalse("the last window closed: the Workspace is gone", WallRow.hasWorkspace(true, 0));
        assertTrue("not bound yet: assumed", WallRow.hasWorkspace(false, 0));
    }

    @Test
    public void anEmptyServiceStartsAShellOnlyOnAColdStartOrWhenAsked() {
        assertTrue("cold start: the first shell", WallRow.startsShellOnConnect(true, false, false));
        assertFalse("emptied by the user: Home stays",
            WallRow.startsShellOnConnect(true, true, false));
        assertTrue("New session asks for one", WallRow.startsShellOnConnect(true, true, true));
        assertFalse("a shell is already there", WallRow.startsShellOnConnect(false, false, true));
    }

    @Test
    public void aStepStaysInsideItsKindWhileThereIsAPageThatWay() {
        assertTrue(WallRow.hasInnerStep(0, 3, 1));
        assertTrue(WallRow.hasInnerStep(2, 3, -1));
        assertFalse("past the last window is the next stretch", WallRow.hasInnerStep(2, 3, 1));
        assertFalse("before the first window is Home", WallRow.hasInnerStep(0, 3, -1));
        assertFalse(WallRow.hasInnerStep(0, 1, 1));
        assertFalse(WallRow.hasInnerStep(1, 3, 0));
    }

    @Test
    public void arrivingAtHomeFromTheRightShowsTheLastHomePage() {
        assertEquals(2, WallRow.lastHomePage(3));
        assertEquals(0, WallRow.lastHomePage(1));
        assertEquals(0, WallRow.lastHomePage(0));
    }

    @Test
    public void aNewWindowLandsRightAfterTheCurrentOne() {
        assertEquals(2, WallRow.insertionIndex(1, 3));
        assertEquals("after the last is the end", 3, WallRow.insertionIndex(2, 3));
        assertEquals("the first window of an empty session", 0, WallRow.insertionIndex(0, 0));
        assertEquals(1, WallRow.insertionIndex(0, 1));
    }

    @Test
    public void startInDecidesTheColdStartPage() {
        assertEquals(PaneWallPage.WIDGETS, WallRow.coldStart(WallRow.START_IN_HOME));
        assertEquals(PaneWallPage.TERMINAL, WallRow.coldStart(WallRow.START_IN_WORKSPACE));
        assertEquals("unset is Home", PaneWallPage.WIDGETS, WallRow.coldStart(null));
        assertEquals(PaneWallPage.WIDGETS, WallRow.coldStart("TERMINAL"));
    }

    @Test
    public void theSavedPositionIsTheHalfAndTheIndexWithinIt() {
        assertEquals(WallRow.HALF_HOME, WallRow.halfOf(PaneWallPage.WIDGETS));
        assertEquals(WallRow.HALF_WORKSPACE, WallRow.halfOf(PaneWallPage.TERMINAL));
        assertEquals(WallRow.HALF_WORKSPACE, WallRow.halfOf(PaneWallPage.DISPLAY));
        assertEquals(1, WallRow.savedIndex(WallRow.homePage(1), 4));
        assertEquals(2, WallRow.savedIndex(WallRow.window(2), 4));
        assertEquals("the Display page stands after the windows", 4,
            WallRow.savedIndex(WallRow.display(), 4));
    }

    @Test
    public void restoreFindsThePositionOnTheRowAsItIsNow() {
        assertEquals(WallRow.homePage(1), WallRow.restore(WallRow.HALF_HOME, 1, 3, 2, false));
        assertEquals("a Home page that went is the last one", WallRow.homePage(1),
            WallRow.restore(WallRow.HALF_HOME, 4, 2, 2, false));
        assertEquals(WallRow.window(1), WallRow.restore(WallRow.HALF_WORKSPACE, 1, 3, 2, false));
        assertEquals(WallRow.display(), WallRow.restore(WallRow.HALF_WORKSPACE, 2, 3, 2, true));
        assertEquals("no display: the last window", WallRow.window(1),
            WallRow.restore(WallRow.HALF_WORKSPACE, 2, 3, 2, false));
        assertEquals("an empty Workspace: Home page 1", WallRow.homePage(0),
            WallRow.restore(WallRow.HALF_WORKSPACE, 0, 3, 0, false));
    }

    @Test
    public void unknownSavedNamesFallBackToHomePageOne() {
        assertEquals(WallRow.homePage(0), WallRow.restore("TERMINAL", 2, 3, 3, true));
        assertEquals(WallRow.homePage(0), WallRow.restore("WIDGETS", 2, 3, 3, true));
        assertEquals(WallRow.homePage(0), WallRow.restore(null, 2, 3, 3, true));
    }
}
