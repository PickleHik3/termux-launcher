package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Where the wallpaper sits under the wall. The spare width is spread over the whole row — Home
 * pages, then windows, then the Display page — the pan follows the row's live position
 * continuously, through the seam between Home and the Workspace as through any other step, and a
 * picture that is only one screen wide never moves at all.
 */
public class WallParallaxTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2412;
    /** Half a screen: what a 1.5x picker crop leaves beyond one screen. */
    private static final int SPARE = 540;
    private static final float EPS = 0.01f;

    // ---- span: which pictures pan ---------------------------------------------------------

    @Test
    public void thePickerCropsOneAndAHalfScreens() {
        assertEquals(1620, WallParallax.pickerWidthPx(WIDTH));
    }

    @Test
    public void aWidePictureSpansItsWholeWidthAtScreenHeight() {
        assertEquals(1620, WallParallax.spanPx(1620, HEIGHT, WIDTH, HEIGHT));
        // The same picture stored smaller still spans by its aspect, not its pixels.
        assertEquals(1620, WallParallax.spanPx(810, 1206, WIDTH, HEIGHT));
    }

    @Test
    public void aLegacyOneScreenPictureSpansOneScreenAndSoNeverPans() {
        assertEquals(WIDTH, WallParallax.spanPx(1080, 2412, WIDTH, HEIGHT));
        // A few pixels either side, from an aspect measured against a different decor, is no pan.
        assertEquals(WIDTH, WallParallax.spanPx(1100, 2412, WIDTH, HEIGHT));
        assertEquals(WIDTH, WallParallax.spanPx(1060, 2412, WIDTH, HEIGHT));
        assertEquals(0f, WallParallax.offsetPx(2.5f, 5,
            WallParallax.spanPx(1080, 2412, WIDTH, HEIGHT) - WIDTH), 0f);
    }

    @Test
    public void nothingWiderThanThePickerCropIsEverSpanned() {
        assertEquals(1620, WallParallax.spanPx(4000, 2412, WIDTH, HEIGHT));
    }

    @Test
    public void anUnreadablePictureSpansOneScreen() {
        assertEquals(WIDTH, WallParallax.spanPx(0, 0, WIDTH, HEIGHT));
    }

    // ---- the row ------------------------------------------------------------------------------

    @Test
    public void theRowIsHomePagesThenWindowsTheDisplayWindowAmongThem() {
        assertEquals(5, WallParallax.rowCount(2, 3));
        // Home always has a page, even before its repository has counted any.
        assertEquals(1, WallParallax.rowCount(0, 0));

        assertEquals(0, WallParallax.rowIndex(PaneWallPage.WIDGETS, 0, 2, 3));
        assertEquals(1, WallParallax.rowIndex(PaneWallPage.WIDGETS, 1, 2, 3));
        assertEquals("window 1 sits right after the last Home page", 2,
            WallParallax.rowIndex(PaneWallPage.TERMINAL, 0, 2, 3));
        assertEquals(4, WallParallax.rowIndex(PaneWallPage.TERMINAL, 2, 2, 3));
    }

    @Test
    public void anIndexPastItsKindIsHeldAtTheKindsLastPage() {
        assertEquals(1, WallParallax.rowIndex(PaneWallPage.WIDGETS, 7, 2, 3));
        assertEquals(4, WallParallax.rowIndex(PaneWallPage.TERMINAL, 9, 2, 3));
        assertEquals(0, WallParallax.rowIndex(PaneWallPage.WIDGETS, -1, 2, 3));
    }

    @Test
    public void aRowOfTheDisplayWindowAlonePutsItRightAfterHome() {
        assertEquals(3, WallParallax.rowCount(2, 1));
        assertEquals(2, WallParallax.rowIndex(PaneWallPage.TERMINAL, 0, 2, 1));
    }

    // ---- rest positions: the whole spare over the row --------------------------------------------

    @Test
    public void aOnePageRowNeverPans() {
        assertEquals(0f, WallParallax.offsetPx(0f, 1, SPARE), 0f);
        assertEquals(0f, WallParallax.offsetPx(0.4f, 1, SPARE), 0f);
    }

    @Test
    public void aTwoPageRowPansTheWholeSpareInOneStep() {
        assertEquals(0f, WallParallax.offsetPx(0f, 2, SPARE), EPS);
        assertEquals(SPARE, WallParallax.offsetPx(1f, 2, SPARE), EPS);
        assertEquals(SPARE / 2f, WallParallax.offsetPx(0.5f, 2, SPARE), EPS);
    }

    @Test
    public void aFivePageRowRestsAtAnEvenQuarterOfTheSparePerPage() {
        assertEquals(0f, WallParallax.offsetPx(0f, 5, SPARE), EPS);
        assertEquals(135f, WallParallax.offsetPx(1f, 5, SPARE), EPS);
        assertEquals(270f, WallParallax.offsetPx(2f, 5, SPARE), EPS);
        assertEquals(405f, WallParallax.offsetPx(3f, 5, SPARE), EPS);
        assertEquals(SPARE, WallParallax.offsetPx(4f, 5, SPARE), EPS);
        // Mid-drag between the second and third page.
        assertEquals(135f + 0.3f * 135f, WallParallax.offsetPx(1.3f, 5, SPARE), EPS);
    }

    @Test
    public void theRowsEndsHoldThePictureAtItsEdges() {
        assertEquals(0f, WallParallax.offsetPx(-0.4f, 5, SPARE), EPS);
        assertEquals(SPARE, WallParallax.offsetPx(4.3f, 5, SPARE), EPS);
    }

    @Test
    public void theRowsLastWindowIsAtThePicturesRightEdge() {
        // Two terminal windows and the display window last: five pages.
        int count = WallParallax.rowCount(2, 3);
        int display = WallParallax.rowIndex(PaneWallPage.TERMINAL, 2, 2, 3);
        assertEquals(SPARE, WallParallax.offsetPx(display, count, SPARE), EPS);
        // The window before it sits one step short of it.
        int before = WallParallax.rowIndex(PaneWallPage.TERMINAL, 1, 2, 3);
        assertEquals(SPARE * 3f / 4f, WallParallax.offsetPx(before, count, SPARE), EPS);
    }

    @Test
    public void noSpareMeansNoMotionWhereverTheRowIs() {
        assertEquals(0f, WallParallax.offsetPx(3.7f, 6, 0), 0f);
    }

    // ---- motion ---------------------------------------------------------------------------

    @Test
    public void aWallSlideRunsFromTheCurrentPageToTheIncomingOne() {
        assertEquals(2f, WallParallax.slidePosition(2f, 1f, 0f, WIDTH), EPS);
        assertEquals(1.5f, WallParallax.slidePosition(2f, 1f, 540f, WIDTH), EPS);
        assertEquals(2.4f, WallParallax.slidePosition(2f, 3f, -432f, WIDTH), EPS);
        // Never past the incoming page, however far the offset reads.
        assertEquals(1f, WallParallax.slidePosition(2f, 1f, 2000f, WIDTH), EPS);
    }

    @Test
    public void theSeamIsCrossedWithoutAJump() {
        // Two Home pages and three windows: the last Home page is 1, window 1 is 2.
        int lastHome = WallParallax.rowIndex(PaneWallPage.WIDGETS, 1, 2, 3);
        int firstWindow = WallParallax.rowIndex(PaneWallPage.TERMINAL, 0, 2, 3);
        // A drag of 0.4 widths off the last Home page towards window 1...
        float dragging = WallParallax.slidePosition(lastHome, firstWindow, -432f, WIDTH);
        // ...commits: the wall re-bases the offset against the Workspace (offset += 1 width), and
        // the page it came from is now the one on the left.
        float sliding = WallParallax.slidePosition(firstWindow, lastHome, -432f + WIDTH, WIDTH);
        assertEquals(dragging, sliding, EPS);
        assertEquals(1.4f, sliding, EPS);
        assertEquals(WallParallax.offsetPx(dragging, 5, SPARE),
            WallParallax.offsetPx(sliding, 5, SPARE), EPS);
    }

    @Test
    public void anOverDragAtTheRowsEndHoldsStill() {
        // Nothing beyond Home page 1: the incoming page is the current one, so nothing moves.
        assertEquals(0f, WallParallax.slidePosition(0f, 0f, 300f, WIDTH), EPS);
    }

    @Test
    public void anUnmeasuredWallRestsAtTheCurrentPage() {
        assertEquals(3f, WallParallax.slidePosition(3f, 2f, 100f, 0), EPS);
    }

    @Test
    public void aHomePageSwipeIsContinuousThroughItsCommit() {
        // Home page 1 dragged 0.3 widths to the left: Home page 2 is coming in from the right.
        float dragging = WallParallax.innerPosition(0, -0.3f, WallParallax.ADJACENT);
        assertEquals(0.3f, dragging, EPS);
        // The swipe commits: page 2 is in front and enters from 0.7 widths to the right.
        float entering = WallParallax.innerPosition(1, 0.7f, WallParallax.ADJACENT);
        assertEquals(dragging, entering, EPS);
        assertEquals(1f, WallParallax.innerPosition(1, 0f, WallParallax.ADJACENT), EPS);
        // Dragging back towards page 1 from page 2.
        assertEquals(0.75f, WallParallax.innerPosition(1, 0.25f, WallParallax.ADJACENT), EPS);
    }

    @Test
    public void aWindowStepByTheBorderDragCarriesOnFromTheFinger() {
        // Window 2 (row 3 of a two-Home row) dragged 0.3 widths left: the wall's offset carries it.
        float dragging = WallParallax.slidePosition(3f, 4f, -0.3f * WIDTH, WIDTH);
        // Released: the wall rests, window 3 is in front and arrives from 0.7 widths right.
        float arriving = WallParallax.innerPosition(4, 0.7f, 3);
        assertEquals(dragging, arriving, EPS);
    }

    @Test
    public void aJumpOfSeveralWindowsPansOverAllOfThemOnTheArrivalsCurve() {
        // From window 1 (row 2) to window 4 (row 5) by the bar: the arrival starts a whole width out.
        assertEquals(2f, WallParallax.innerPosition(5, 1f, 2), EPS);
        assertEquals(3.5f, WallParallax.innerPosition(5, 0.5f, 2), EPS);
        assertEquals(5f, WallParallax.innerPosition(5, 0f, 2), EPS);
        // And back the other way, arriving from the left.
        assertEquals(5f, WallParallax.innerPosition(2, -1f, 5), EPS);
    }
}
