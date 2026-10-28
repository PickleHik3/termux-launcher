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
        assertFalse("nowhere to go: the swipe stops", WallRow.swipeLeavesHome(2, 3, 1, false));
        assertFalse("backward never leaves Home", WallRow.swipeLeavesHome(0, 3, -1, true));
        assertTrue("one Home page is its own last page", WallRow.swipeLeavesHome(0, 1, 1, true));
        assertTrue("an empty count reads as one page", WallRow.swipeLeavesHome(0, 0, 1, true));
    }

    @Test
    public void anEmptyWorkspaceStillTakesTheSwipeWhenItCanStart() {
        assertTrue(WallRow.seamContinues(true, false));
        assertTrue("an empty Workspace that can start", WallRow.seamContinues(false, true));
        assertFalse("nothing after Home and nothing to start", WallRow.seamContinues(false, false));
    }

    @Test
    public void onlyACommittedCrossingIntoAnEmptyWorkspaceStartsAShell() {
        assertTrue(WallRow.seamStartsShell(true, PaneWallPage.TERMINAL));
        assertFalse("sprang back to Home", WallRow.seamStartsShell(true, PaneWallPage.WIDGETS));
        assertFalse("a Workspace with a window starts nothing",
            WallRow.seamStartsShell(false, PaneWallPage.TERMINAL));
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
        assertEquals("the display window is one of the Workspace's", PaneWallPage.TERMINAL,
            WallRow.coldStart(WallRow.START_IN_DISPLAY));
        assertEquals("unset is Home", PaneWallPage.WIDGETS, WallRow.coldStart(null));
        assertEquals(PaneWallPage.WIDGETS, WallRow.coldStart("TERMINAL"));
    }

    @Test
    public void theOpeningPageIsTheSavedHalfElseStartIn() {
        // A cold start: Start in decides, Home when unset.
        assertEquals(PaneWallPage.WIDGETS, WallRow.openingKind(null, WallRow.START_IN_HOME));
        assertEquals(PaneWallPage.TERMINAL, WallRow.openingKind(null, WallRow.START_IN_WORKSPACE));
        assertEquals(PaneWallPage.WIDGETS, WallRow.openingKind(null, null));
        // A recreation: the half it saved, whatever Start in says.
        assertEquals(PaneWallPage.WIDGETS,
            WallRow.openingKind(WallRow.HALF_HOME, WallRow.START_IN_WORKSPACE));
        assertEquals(PaneWallPage.TERMINAL,
            WallRow.openingKind(WallRow.HALF_WORKSPACE, WallRow.START_IN_HOME));
        assertEquals("a half this version does not know opens Home", PaneWallPage.WIDGETS,
            WallRow.openingKind("display", WallRow.START_IN_WORKSPACE));
        // It agrees with where the restore lands, half for half.
        assertEquals(WallRow.restore(WallRow.HALF_HOME, 0, 1, 0).kind,
            WallRow.openingKind(WallRow.HALF_HOME, null));
        assertEquals(WallRow.restore(WallRow.HALF_WORKSPACE, 0, 1, 1).kind,
            WallRow.openingKind(WallRow.HALF_WORKSPACE, null));
    }

    @Test
    public void theSavedPositionIsTheHalfAndTheIndexWithinIt() {
        assertEquals(WallRow.HALF_HOME, WallRow.halfOf(PaneWallPage.WIDGETS));
        assertEquals(WallRow.HALF_WORKSPACE, WallRow.halfOf(PaneWallPage.TERMINAL));
        assertEquals(1, WallRow.savedIndex(WallRow.homePage(1)));
        assertEquals("a window's place in the row, the display window's included", 2,
            WallRow.savedIndex(WallRow.window(2)));
    }

    @Test
    public void restoreFindsThePositionOnTheRowAsItIsNow() {
        assertEquals(WallRow.homePage(1), WallRow.restore(WallRow.HALF_HOME, 1, 3, 2));
        assertEquals("a Home page that went is the last one", WallRow.homePage(1),
            WallRow.restore(WallRow.HALF_HOME, 4, 2, 2));
        assertEquals(WallRow.window(1), WallRow.restore(WallRow.HALF_WORKSPACE, 1, 3, 2));
        assertEquals("the display window, not back yet: the last window", WallRow.window(1),
            WallRow.restore(WallRow.HALF_WORKSPACE, 2, 3, 2));
        assertEquals("an empty Workspace: Home page 1", WallRow.homePage(0),
            WallRow.restore(WallRow.HALF_WORKSPACE, 0, 3, 0));
    }

    /**
     * The last window or float closing with nothing else open: Home shows, and the service (and
     * the activity bound to it) stays, unless there is no Home at all (slice 4 pass, finding A).
     */
    @Test
    public void theLastShellClosingShowsHomeWheneverThereIsAHome() {
        assertTrue("Home pages on the wall", WallRow.lastShellClosedShowsHome(true, false));
        assertTrue("the phone's home app", WallRow.lastShellClosedShowsHome(false, true));
        assertTrue(WallRow.lastShellClosedShowsHome(true, true));
        assertFalse("no Home at all: plain Termux's end",
            WallRow.lastShellClosedShowsHome(false, false));
    }

    @Test
    public void unknownSavedNamesFallBackToHomePageOne() {
        assertEquals(WallRow.homePage(0), WallRow.restore("TERMINAL", 2, 3, 3));
        assertEquals(WallRow.homePage(0), WallRow.restore("WIDGETS", 2, 3, 3));
        assertEquals(WallRow.homePage(0), WallRow.restore(null, 2, 3, 3));
    }

    /** [Home 1][Terminal][Home 2] (ids 1 3 2) and the default [Home 1][Home 2][Terminal]. */
    private static WallPages mid() {
        return WallPagesMigration.seed("home", 0, "home", 2).move(3, 1).pages;
    }

    @Test
    public void runEdgeSwipeEqualsSwipeLeavesHomeInTheDefaultOrder() {
        for (int len = 1; len <= 3; len++) {
            for (int page = 0; page < len; page++) {
                assertEquals(WallRow.swipeLeavesHome(page, len, 1, true),
                    WallRow.swipeLeavesRun(page, len, 1, true));
                // Nothing lies before the first Home page: backward never leaves.
                assertFalse(WallRow.swipeLeavesRun(page, len, -1, false));
            }
        }
        assertFalse(WallRow.swipeLeavesRun(0, 2, 1, true));
        assertTrue(WallRow.swipeLeavesRun(1, 2, 1, true));
    }

    @Test
    public void aSlotBehindTheFirstPageOfARunLetsBackwardLeave() {
        assertTrue(WallRow.swipeLeavesRun(0, 1, -1, true));
        assertTrue(WallRow.swipeLeavesRun(0, 3, -1, true));
        assertFalse(WallRow.swipeLeavesRun(1, 3, -1, true));
        assertEquals(0, WallRow.arrivalPage(3, -1));
        assertEquals(2, WallRow.arrivalPage(3, 1));
    }

    @Test
    public void theSecondRunsFirstPageIsGlobalHomeIndexOne() {
        WallPages p = mid();
        assertEquals(1, WallRow.globalHomeIndex(p, 2, 0));
    }
}
