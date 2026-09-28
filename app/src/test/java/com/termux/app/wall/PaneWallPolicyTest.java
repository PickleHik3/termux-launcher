package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * The wall's geometry and page set. A missing page is skipped rather than left as a dead swipe,
 * and the outer edges resist instead of sliding the wall off itself.
 */
public class PaneWallPolicyTest {

    private static final int WIDTH = 1080;
    private static final float EPS = 0.01f;

    private static List<PaneWallPage> all() {
        return PaneWallPolicy.availablePages(false, true, true);
    }

    @Test
    public void everyPageIsThereWhenTheInstallHasAllOfThem() {
        assertEquals(List.of(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            all());
    }

    @Test
    public void terminalOnlyInstallsHaveNoWidgetsPage() {
        assertEquals(List.of(PaneWallPage.TERMINAL),
            PaneWallPolicy.availablePages(true, true, false));
    }

    @Test
    public void widgetsOffLeavesTheDisplayPageReachable() {
        assertEquals(List.of(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            PaneWallPolicy.availablePages(false, false, true));
    }

    @Test
    public void theTerminalIsAlwaysAPage() {
        assertTrue(PaneWallPolicy.availablePages(true, false, false)
            .contains(PaneWallPage.TERMINAL));
        assertEquals(PaneWallPage.TERMINAL, PaneWallPolicy.homePage());
    }

    @Test
    public void neighboursAreComputedOverTheAvailablePagesOnly() {
        List<PaneWallPage> twoPages = PaneWallPolicy.availablePages(false, false, true);
        // With no Widgets page, a left swipe from the terminal has nowhere to go...
        assertEquals(PaneWallPage.TERMINAL,
            PaneWallPolicy.neighbour(twoPages, PaneWallPage.TERMINAL, -1));
        assertFalse(PaneWallPolicy.hasNeighbour(twoPages, PaneWallPage.TERMINAL, -1));
        // ...while a right swipe still reaches the Display page.
        assertEquals(PaneWallPage.DISPLAY,
            PaneWallPolicy.neighbour(twoPages, PaneWallPage.TERMINAL, 1));
        assertTrue(PaneWallPolicy.hasNeighbour(twoPages, PaneWallPage.TERMINAL, 1));
    }

    @Test
    public void threePlacesFormARingThatWrapsBothWays() {
        assertTrue(PaneWallPolicy.isRing(all()));
        assertEquals(PaneWallPage.DISPLAY, PaneWallPolicy.neighbour(all(), PaneWallPage.WIDGETS, -1));
        assertEquals(PaneWallPage.WIDGETS, PaneWallPolicy.neighbour(all(), PaneWallPage.DISPLAY, 1));
        assertTrue(PaneWallPolicy.hasNeighbour(all(), PaneWallPage.WIDGETS, -1));
        assertTrue(PaneWallPolicy.hasNeighbour(all(), PaneWallPage.DISPLAY, 1));
        // Two steps round a ring of three is one step the other way: right twice from the
        // terminal is the Widgets page.
        assertEquals(PaneWallPage.WIDGETS, PaneWallPolicy.neighbour(all(), PaneWallPage.TERMINAL, 2));
    }

    @Test
    public void twoPlacesStayALineWithAnEnd() {
        List<PaneWallPage> twoPages = PaneWallPolicy.availablePages(false, true, false);
        assertFalse(PaneWallPolicy.isRing(twoPages));
        assertEquals(PaneWallPage.WIDGETS, PaneWallPolicy.neighbour(twoPages, PaneWallPage.WIDGETS, -1));
        assertEquals(PaneWallPage.TERMINAL, PaneWallPolicy.neighbour(twoPages, PaneWallPage.TERMINAL, 1));
        assertFalse(PaneWallPolicy.hasNeighbour(twoPages, PaneWallPage.TERMINAL, 1));
    }

    @Test
    public void relativePositionIsTheShorterWayRoundARing() {
        assertEquals(-1, PaneWallPolicy.relativePosition(all(), PaneWallPage.TERMINAL, PaneWallPage.WIDGETS));
        assertEquals(1, PaneWallPolicy.relativePosition(all(), PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        // From the Widgets page the Display page is one step to the left, not two to the right.
        assertEquals(-1, PaneWallPolicy.relativePosition(all(), PaneWallPage.WIDGETS, PaneWallPage.DISPLAY));
        assertEquals(1, PaneWallPolicy.relativePosition(all(), PaneWallPage.WIDGETS, PaneWallPage.TERMINAL));
        assertEquals(-1, PaneWallPolicy.relativePosition(all(), PaneWallPage.DISPLAY, PaneWallPage.TERMINAL));
        assertEquals(1, PaneWallPolicy.relativePosition(all(), PaneWallPage.DISPLAY, PaneWallPage.WIDGETS));
        assertEquals(0, PaneWallPolicy.relativePosition(all(), PaneWallPage.DISPLAY, PaneWallPage.DISPLAY));
        // On a line the distance is what it is.
        List<PaneWallPage> twoPages = PaneWallPolicy.availablePages(false, false, true);
        assertEquals(-1, PaneWallPolicy.relativePosition(twoPages, PaneWallPage.DISPLAY, PaneWallPage.TERMINAL));
    }

    @Test
    public void theTilesAreTheNeighboursLeftFirst() {
        assertEquals(java.util.Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.DISPLAY),
            PaneWallPolicy.tiles(all(), PaneWallPage.TERMINAL));
        assertEquals(java.util.Arrays.asList(PaneWallPage.DISPLAY, PaneWallPage.TERMINAL),
            PaneWallPolicy.tiles(all(), PaneWallPage.WIDGETS));
        assertEquals(java.util.Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS),
            PaneWallPolicy.tiles(all(), PaneWallPage.DISPLAY));
        // Two places: the one other page, and the side it is on is its relative position.
        List<PaneWallPage> twoPages = PaneWallPolicy.availablePages(false, true, false);
        assertEquals(java.util.Collections.singletonList(PaneWallPage.WIDGETS),
            PaneWallPolicy.tiles(twoPages, PaneWallPage.TERMINAL));
        assertEquals(java.util.Collections.singletonList(PaneWallPage.TERMINAL),
            PaneWallPolicy.tiles(twoPages, PaneWallPage.WIDGETS));
    }

    @Test
    public void aDragTowardsAnExistingPageMovesOneToOne() {
        assertEquals(-300f, PaneWallPolicy.offsetForDrag(-300f, WIDTH, true, true), EPS);
        assertEquals(300f, PaneWallPolicy.offsetForDrag(300f, WIDTH, true, true), EPS);
    }

    @Test
    public void aDragPastTheOuterPageResists() {
        float offset = PaneWallPolicy.offsetForDrag(-400f, WIDTH, true, false);
        assertEquals(-400f * PaneWallPolicy.EDGE_RESISTANCE, offset, EPS);
        assertTrue("the wall must never slide a whole page off its own edge",
            Math.abs(offset) < WIDTH * PaneWallPolicy.EDGE_RESISTANCE + EPS);
    }

    @Test
    public void noDragEverExposesMoreThanOnePage() {
        assertEquals(-WIDTH, PaneWallPolicy.offsetForDrag(-4000f, WIDTH, true, true), EPS);
    }

    /** A phone's density, so the flick thresholds below are the dp figures at 3x. */
    private static final float DENSITY = 3f;
    /** One over the flick's speed, in px/s. */
    private static final float FLICK = PaneWallPolicy.DRAG_COMMIT_VELOCITY_DP_PER_SEC * DENSITY + 1f;
    /** One over the least distance a flick must have moved the wall, in px. */
    private static final float FLICK_DISTANCE =
        PaneWallPolicy.DRAG_COMMIT_FLING_MIN_DISTANCE_DP * DENSITY + 1f;

    @Test
    public void aShortDragSpringsBack() {
        assertEquals(0, PaneWallPolicy.settle(-200f, 0f, WIDTH, DENSITY, true, true));
    }

    @Test
    public void aDragPastTheCommitFractionChangesPage() {
        float past = -WIDTH * PaneWallPolicy.DRAG_COMMIT_FRACTION - 1f;
        assertEquals(1, PaneWallPolicy.settle(past, 0f, WIDTH, DENSITY, true, true));
        assertEquals(-1, PaneWallPolicy.settle(-past, 0f, WIDTH, DENSITY, true, true));
    }

    /**
     * A quick flick pages short of the commit distance. The speed that counts is the pager's
     * usual, in dp, not a share of the width: at two widths a second a 250 ms flick on a phone
     * sprang back and only a long drag paged (pong, 2026-09-28).
     */
    @Test
    public void aQuickFlickPagesShortOfTheCommitDistance() {
        assertEquals(1, PaneWallPolicy.settle(-FLICK_DISTANCE, -FLICK, WIDTH, DENSITY, true, true));
        assertEquals(-1, PaneWallPolicy.settle(FLICK_DISTANCE, FLICK, WIDTH, DENSITY, true, true));
        // Two widths a second is far more than a flick needs.
        assertEquals(1, PaneWallPolicy.settle(-FLICK_DISTANCE, -2f * WIDTH, WIDTH, DENSITY,
            true, true));
        // Under the speed, the distance alone decides.
        assertEquals(0, PaneWallPolicy.settle(-FLICK_DISTANCE, -FLICK + 2f, WIDTH, DENSITY,
            true, true));
    }

    @Test
    public void aTwitchOfAHeldFingerIsNotAFlick() {
        // Fast, but the wall has barely moved: a held finger's release jitter.
        float twitch = PaneWallPolicy.DRAG_COMMIT_FLING_MIN_DISTANCE_DP * DENSITY - 1f;
        assertEquals(0, PaneWallPolicy.settle(-twitch, -FLICK, WIDTH, DENSITY, true, true));
        assertEquals(0, PaneWallPolicy.settle(0f, -10_000f, WIDTH, DENSITY, true, true));
    }

    @Test
    public void aFlickCommitsWhateverWayTheDragWasHeading() {
        // Dragged well past the commit distance to the right, then flicked back left.
        assertEquals(1, PaneWallPolicy.settle(WIDTH * 0.5f, -FLICK, WIDTH, DENSITY, true, true));
    }

    @Test
    public void aCommitTowardsAMissingPageStaysPut() {
        float past = -WIDTH * PaneWallPolicy.DRAG_COMMIT_FRACTION - 1f;
        assertEquals(0, PaneWallPolicy.settle(past, 0f, WIDTH, DENSITY, true, false));
        assertEquals(0, PaneWallPolicy.settle(past, -FLICK, WIDTH, DENSITY, true, false));
    }

    @Test
    public void aZeroWidthWallNeverCommits() {
        assertEquals(0, PaneWallPolicy.settle(-500f, -5000f, 0, DENSITY, true, true));
        assertEquals(0f, PaneWallPolicy.offsetForDrag(-500f, 0, true, true), EPS);
    }

    /**
     * The terminal's own frame line is chrome over the wall, not a rim the page carries, so it
     * is faded by the page's own position: like a rim over the first stretch of the page's
     * departure, then gone, rather than rising again for the page arriving in its place.
     */
    @Test
    public void aPagesOwnFrameFadesAsThePageLeavesAndStaysGoneOnceItIsAway() {
        assertEquals(1f, PaneWallPolicy.pageOutlineAlpha(0f, WIDTH), EPS);
        float fade = WIDTH * PaneWallPolicy.OUTLINE_FADE_FRACTION;
        assertEquals(0.5f, PaneWallPolicy.pageOutlineAlpha(-fade / 2f, WIDTH), EPS);
        assertEquals(0.5f, PaneWallPolicy.pageOutlineAlpha(fade / 2f, WIDTH), EPS);
        assertEquals(0f, PaneWallPolicy.pageOutlineAlpha(-fade, WIDTH), EPS);
        // Where a rim's curve turns back up for the arriving page, this one stays down.
        assertEquals(0f, PaneWallPolicy.pageOutlineAlpha(-WIDTH * 0.95f, WIDTH), EPS);
        assertEquals(0f, PaneWallPolicy.pageOutlineAlpha(WIDTH, WIDTH), EPS);
        assertEquals(1f, PaneWallPolicy.pageOutlineAlpha(0f, 0), EPS);
    }

    @Test
    public void wallGoAcceptsTheNamesAndTheRelativeDirections() {
        List<PaneWallPage> pages = all();
        assertEquals(PaneWallPage.WIDGETS,
            PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "widgets"));
        assertEquals(PaneWallPage.DISPLAY,
            PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, " Display "));
        assertEquals(PaneWallPage.TERMINAL,
            PaneWallPolicy.parsePage(pages, PaneWallPage.WIDGETS, "right"));
        assertEquals(PaneWallPage.WIDGETS,
            PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "left"));
        assertNull(PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "nowhere"));
        assertNull(PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, null));
    }

    @Test
    public void thePagesOutlinesFadeOutOverTheFirstStretchAndBackInOverTheLast() {
        float fade = PaneWallPolicy.OUTLINE_FADE_FRACTION;
        // At rest, and at the other rest a tap starts from, every outline shows.
        assertEquals(1f, PaneWallPolicy.outlineAlpha(0f, WIDTH), EPS);
        assertEquals(1f, PaneWallPolicy.outlineAlpha(WIDTH, WIDTH), EPS);
        assertEquals(1f, PaneWallPolicy.outlineAlpha(-WIDTH, WIDTH), EPS);
        // Half way through the fade, half the outline; past it, none, for the whole middle.
        assertEquals(0.5f, PaneWallPolicy.outlineAlpha(WIDTH * fade / 2f, WIDTH), EPS);
        assertEquals(0.5f, PaneWallPolicy.outlineAlpha(-WIDTH * (1f - fade / 2f), WIDTH), EPS);
        assertEquals(0f, PaneWallPolicy.outlineAlpha(WIDTH * fade, WIDTH), EPS);
        assertEquals(0f, PaneWallPolicy.outlineAlpha(WIDTH * 0.5f, WIDTH), EPS);
        assertEquals(0f, PaneWallPolicy.outlineAlpha(-WIDTH * 0.7f, WIDTH), EPS);
        // The commit at release moves the offset by a width and the number not at all.
        assertEquals(PaneWallPolicy.outlineAlpha(WIDTH * 0.95f, WIDTH),
            PaneWallPolicy.outlineAlpha(WIDTH * 0.95f - WIDTH, WIDTH), EPS);
        // Before the first layout there is nothing to fade.
        assertEquals(1f, PaneWallPolicy.outlineAlpha(300f, 0), EPS);
    }

    @Test
    public void onlyAWallShowingTwoPlacesFadesTheOutlines() {
        List<PaneWallPage> ring = all();
        assertTrue(PaneWallPolicy.blendsPlaces(ring, PaneWallPage.TERMINAL, 200f));
        assertTrue(PaneWallPolicy.blendsPlaces(ring, PaneWallPage.TERMINAL, -200f));
        // On the ring every page has a neighbour either side; after the commit toward Home the
        // page the wall left is the neighbour on the other side.
        assertTrue(PaneWallPolicy.blendsPlaces(ring, PaneWallPage.WIDGETS, -800f));
        assertFalse(PaneWallPolicy.blendsPlaces(ring, PaneWallPage.TERMINAL, 0f));
        // A two-page line has an outer edge: pressed into it, the page is alone on screen.
        List<PaneWallPage> line = PaneWallPolicy.availablePages(false, false, true);
        assertFalse(PaneWallPolicy.blendsPlaces(line, PaneWallPage.TERMINAL, 200f));
        assertTrue(PaneWallPolicy.blendsPlaces(line, PaneWallPage.TERMINAL, -200f));
        assertFalse(PaneWallPolicy.blendsPlaces(line, PaneWallPage.DISPLAY, -200f));
    }

    @Test
    public void wallGoCannotReachAPageThisInstallDoesNotHave() {
        List<PaneWallPage> pages = PaneWallPolicy.availablePages(true, true, false);
        assertNull(PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "widgets"));
        assertNull(PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "display"));
        assertEquals(PaneWallPage.TERMINAL,
            PaneWallPolicy.parsePage(pages, PaneWallPage.TERMINAL, "terminal"));
    }
}
