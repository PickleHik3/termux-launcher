package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarLensMetrics.Bar;
import com.termux.app.statusbar.StatusBarLensMetrics.Mark;
import com.termux.app.statusbar.StatusBarLensPolicy.Growth;
import com.termux.app.statusbar.StatusBarLensPolicy.Placement;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The place icons: where each sits and how present it is as the wall moves. */
public class StatusBarLensPolicyTest {

    private static final List<PaneWallPage> RING = Arrays.asList(PaneWallPage.WIDGETS,
        PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    @Test public void neighboursRestOneWidthAwayOnTheSideTheySlideInFrom() {
        assertEquals(-1f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.WIDGETS, 0f, 400), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.DISPLAY, 0f, 400), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.TERMINAL, 0f, 400), 0.001f);
        // On the ring the far place waits on the near side: from Display, Widgets is to the right.
        assertEquals(1f, StatusBarLensPolicy.distance(RING, PaneWallPage.DISPLAY,
            PaneWallPage.WIDGETS, 0f, 400), 0.001f);
    }

    @Test public void allThreeIconsAreWholeWithinAWidthAndTheLeavingOneDissolves() {
        assertEquals(1f, StatusBarLensPolicy.alpha(0f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.alpha(-1f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.alpha(1f), 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.alpha(1.25f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.alpha(-1.5f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.scale(0f), 0.001f);
        assertEquals(0.86f, StatusBarLensPolicy.scale(1f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.tintWeight(0f), 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.tintWeight(0.5f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.tintWeight(1f), 0.001f);
    }

    @Test public void aDragCarriesTheArrivingIconHomeAsTheHomeIconLeaves() {
        float home = 12f, leftPeek = -18f, rightPeek = 382f, size = 36f;
        // At rest: home, and the two neighbours half past their edges.
        assertEquals(12f, StatusBarLensPolicy.iconX(0f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-18f, StatusBarLensPolicy.iconX(-1f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(382f, StatusBarLensPolicy.iconX(1f, home, leftPeek, rightPeek, size), 0.001f);
        // Dragging left by half a width: the right neighbour is halfway home, the home icon
        // halfway to the left edge, and the left neighbour half a size past its edge and fading.
        assertEquals(197f, StatusBarLensPolicy.iconX(0.5f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-3f, StatusBarLensPolicy.iconX(-0.5f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-45f, StatusBarLensPolicy.iconX(-1.5f, home, leftPeek, rightPeek, size), 0.001f);
    }

    // ------------------------------------------------- the lens opens towards the screen's middle

    /** The phone of record: 1080x2400, a 96dp bar at density 2.75, a 360dp card 4dp clear of it. */
    private static final int W = 1080;
    private static final int H = 2400;
    private static final int BAR = 264;
    private static final int COLUMN = 209;
    private static final int CARD_W = 990;
    private static final int CARD_H = 600;
    private static final int GAP = 11;

    @Test public void everyEdgeOpensTowardsTheMiddleOfTheScreen() {
        assertEquals(Growth.DOWN, StatusBarLensPolicy.growthFor(Edge.TOP));
        assertEquals(Growth.UP, StatusBarLensPolicy.growthFor(Edge.BOTTOM));
        assertEquals(Growth.RIGHT, StatusBarLensPolicy.growthFor(Edge.LEFT));
        assertEquals(Growth.LEFT, StatusBarLensPolicy.growthFor(Edge.RIGHT));
        assertTrue(StatusBarLensPolicy.isVertical(Growth.UP));
        assertTrue(StatusBarLensPolicy.isVertical(Growth.DOWN));
        assertFalse(StatusBarLensPolicy.isVertical(Growth.LEFT));
        assertFalse(StatusBarLensPolicy.isVertical(Growth.RIGHT));
    }

    @Test public void aCardIsAnchoredClearOfTheBarAndCentredAcrossIt() {
        // A top bar drops its card below itself, centred on the canvas's width.
        assertEquals(new Placement((W - CARD_W) / 2, BAR + GAP),
            StatusBarLensPolicy.card(Growth.DOWN, 0, 0, W, BAR,
                CARD_W, CARD_H, GAP, 0, 0, W, H));
        // A bottom bar raises the same card above itself — the whole of it on screen, which is
        // what a card that always dropped downward was not.
        assertEquals(new Placement((W - CARD_W) / 2, H - BAR - GAP - CARD_H),
            StatusBarLensPolicy.card(Growth.UP, 0, H - BAR, W, H,
                CARD_W, CARD_H, GAP, 0, 0, W, H));
        // A column lays the card out on the perpendicular axis: beside the bar, centred on height,
        // at the width the run beside the bar allows.
        int besideColumn = StatusBarLensPolicy.widthCapPx(Growth.RIGHT, 0, COLUMN, GAP, 0, W, 33);
        assertEquals(W - COLUMN - GAP - 33, besideColumn);
        assertEquals(new Placement(COLUMN + GAP, (H - CARD_H) / 2),
            StatusBarLensPolicy.card(Growth.RIGHT, 0, 0, COLUMN, H,
                besideColumn, CARD_H, GAP, 0, 0, W, H));
        // A row's card is capped by the canvas alone, which is what every card has always had.
        assertEquals(W - 66, StatusBarLensPolicy.widthCapPx(Growth.DOWN, 0, W, GAP, 0, W, 33));
    }

    @Test public void aCardTooBigForTheRoomBesideTheBarIsMovedRatherThanLost() {
        // A 360dp card off a right-hand column would start at -130: clamped to the canvas instead.
        assertEquals(new Placement(0, (H - CARD_H) / 2),
            StatusBarLensPolicy.card(Growth.LEFT, W - COLUMN, 0, W, H,
                CARD_W, CARD_H, GAP, 0, 0, W, H));
        // And one taller than the run above a bottom bar rests on the canvas's own top edge.
        assertEquals(new Placement((W - CARD_W) / 2, 0),
            StatusBarLensPolicy.card(Growth.UP, 0, H - BAR, W, H,
                CARD_W, H, GAP, 0, 0, W, H));
    }

    @Test public void aCardThatGrowsKeepsTheEdgeTheBarPinnedAndStaysOnTheCanvas() {
        // The same card, twice as tall — the size the stats card takes on once its process list
        // arrives. Whatever it grows to, the edge facing the bar is the edge that does not move.
        int taller = CARD_H * 2;
        Placement up = StatusBarLensPolicy.card(Growth.UP, 0, H - BAR, W, H,
            CARD_W, CARD_H, GAP, 0, 0, W, H);
        Placement upGrown = StatusBarLensPolicy.card(Growth.UP, 0, H - BAR, W, H,
            CARD_W, taller, GAP, 0, 0, W, H);
        assertEquals("a bottom bar pins the card's bottom", up.y + CARD_H, upGrown.y + taller);
        assertEquals(H - BAR - GAP, upGrown.y + taller);
        assertTrue("and the growth runs up the canvas, not off it", upGrown.y >= 0);

        Placement down = StatusBarLensPolicy.card(Growth.DOWN, 0, 0, W, BAR,
            CARD_W, CARD_H, GAP, 0, 0, W, H);
        Placement downGrown = StatusBarLensPolicy.card(Growth.DOWN, 0, 0, W, BAR,
            CARD_W, taller, GAP, 0, 0, W, H);
        assertEquals("a top bar pins the card's top", down.y, downGrown.y);
        assertEquals(BAR + GAP, downGrown.y);
        assertTrue(downGrown.y + taller <= H);

        // Off a column the card grows across the screen instead, and the side facing the bar holds.
        int narrow = 400;
        int wide = 700;
        Placement right = StatusBarLensPolicy.card(Growth.RIGHT, 0, 0, COLUMN, H,
            narrow, CARD_H, GAP, 0, 0, W, H);
        Placement rightGrown = StatusBarLensPolicy.card(Growth.RIGHT, 0, 0, COLUMN, H,
            wide, CARD_H, GAP, 0, 0, W, H);
        assertEquals("a left-hand column pins the card's left", right.x, rightGrown.x);
        assertEquals(COLUMN + GAP, rightGrown.x);
        assertTrue(rightGrown.x + wide <= W);

        Placement left = StatusBarLensPolicy.card(Growth.LEFT, W - COLUMN, 0, W, H,
            narrow, CARD_H, GAP, 0, 0, W, H);
        Placement leftGrown = StatusBarLensPolicy.card(Growth.LEFT, W - COLUMN, 0, W, H,
            wide, CARD_H, GAP, 0, 0, W, H);
        assertEquals("a right-hand column pins the card's right",
            left.x + narrow, leftGrown.x + wide);
        assertEquals(W - COLUMN - GAP, leftGrown.x + wide);
        assertTrue(leftGrown.x >= 0);
    }

    @Test public void theCardSlidesInOutOfTheBarItCameFrom() {
        assertEquals(-8f, StatusBarLensPolicy.enterOffsetYPx(Growth.DOWN, 8f), 0.001f);
        assertEquals(8f, StatusBarLensPolicy.enterOffsetYPx(Growth.UP, 8f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.enterOffsetYPx(Growth.LEFT, 8f), 0.001f);
        assertEquals(-8f, StatusBarLensPolicy.enterOffsetXPx(Growth.RIGHT, 8f), 0.001f);
        assertEquals(8f, StatusBarLensPolicy.enterOffsetXPx(Growth.LEFT, 8f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.enterOffsetXPx(Growth.UP, 8f), 0.001f);
    }

    @Test public void theStatusRowKeepsTheScreenEdgeItsBarStandsOn() {
        // 40px down inside a 96px bar on a top bar, and the same on a bottom one: the row stays at
        // the panel's foot while the clock's band grows upward above it. It used to be mirrored to
        // 32, which lifted a bottom bar's row clear off the screen's edge the moment it opened.
        assertEquals(40, StatusBarLensPolicy.rowOffsetPx(Edge.TOP, 96, 24, 40));
        assertEquals(40, StatusBarLensPolicy.rowOffsetPx(Edge.BOTTOM, 96, 24, 40));
        // Never past the panel's own end.
        assertEquals(72, StatusBarLensPolicy.rowOffsetPx(Edge.BOTTOM, 96, 24, 90));
        // A column's row runs out the rest of the bar rather than sitting in it.
        assertEquals(90, StatusBarLensPolicy.rowOffsetPx(Edge.LEFT, 96, 24, 90));
    }

    @Test public void theClockLeadsTheRowOnEitherRowEdgeAndOnNeitherColumn() {
        assertTrue(StatusBarLensPolicy.slotLeadsRow(Edge.TOP));
        assertTrue(StatusBarLensPolicy.slotLeadsRow(Edge.BOTTOM));
        assertFalse(StatusBarLensPolicy.slotLeadsRow(Edge.LEFT));
        assertFalse(StatusBarLensPolicy.slotLeadsRow(Edge.RIGHT));
    }

    // -------------------------------------------------------- the peek is legible and reachable

    /**
     * The landscape window of the 17 Sep review: 1300x600 at density 2, where the neighbouring
     * place marks were reported as small, low-contrast fragments. The bar is 34dp of that in the
     * compact form it rests in and 68dp open.
     */
    private static final float DENSITY = 2f;
    private static final int BAR_W = 1300;
    private static final int COMPACT_H = 68;   // 34dp
    private static final int EXPANDED_H = 136; // 68dp

    private static List<Mark> landscapeMarks(TopStatusBarState state, boolean displayRunning) {
        int height = state == TopStatusBarState.COMPACT ? COMPACT_H : EXPANDED_H;
        return StatusBarLensMetrics.marks(Bar.of(BAR_W, height, DENSITY, false, state),
            RING, PaneWallPage.TERMINAL, 0f, BAR_W, displayRunning);
    }

    private static Mark markFor(List<Mark> marks, PaneWallPage page) {
        for (Mark mark : marks) if (mark.page == page) return mark;
        throw new AssertionError("no mark for " + page);
    }

    @Test public void everyMarkIsAtLeastThePlatformsMinimumTargetInBothFormsOfTheBar() {
        // 48dp each way, or the whole of the bar on an axis with less than that to give — a 34dp
        // compact bar cannot be 48dp thick, and the target takes all of it rather than a strip of
        // it. Before: the drawn half-tile plus 8dp of air, which is 17dp along a compact bar.
        for (TopStatusBarState state : TopStatusBarState.values()) {
            int height = state == TopStatusBarState.COMPACT ? COMPACT_H : EXPANDED_H;
            float minAlong = StatusBarLensMetrics.minTargetPx(DENSITY, BAR_W);
            float minAcross = StatusBarLensMetrics.minTargetPx(DENSITY, height);
            for (Mark mark : landscapeMarks(state, true)) {
                assertTrue(state + " " + mark.page + " target " + mark.target,
                    mark.target.width() >= minAlong - 0.01f);
                assertTrue(state + " " + mark.page + " target " + mark.target,
                    mark.target.height() >= minAcross - 0.01f);
                // And it is a patch of the bar, not a rectangle hanging off one end of it.
                assertTrue(mark.target.left >= -0.01f && mark.target.right <= BAR_W + 0.01f);
                assertTrue(mark.target.top >= -0.01f && mark.target.bottom <= height + 0.01f);
            }
        }
    }

    @Test public void aNeighboursTargetReachesInwardBecauseItsOuterHalfIsPastTheBar() {
        List<Mark> marks = landscapeMarks(TopStatusBarState.COMPACT, true);
        // The compact row carries no mark at home, so the two neighbours are all there is.
        assertEquals(2, marks.size());
        Mark left = markFor(marks, PaneWallPage.WIDGETS);
        Mark right = markFor(marks, PaneWallPage.DISPLAY);
        assertEquals(0f, left.centerX, 0.01f);
        assertEquals(BAR_W, right.centerX, 0.01f);
        assertEquals(0f, left.target.left, 0.01f);
        assertEquals(96f, left.target.right, 0.01f);
        assertEquals(BAR_W - 96f, right.target.left, 0.01f);
        assertEquals(BAR_W, right.target.right, 0.01f);
        // Across a bar too thin for 48dp the target is the whole thickness.
        assertEquals(0f, left.target.top, 0.01f);
        assertEquals(COMPACT_H, left.target.bottom, 0.01f);
    }

    @Test public void aPeekingMarkStaysAboveTheLegibilityFloorInBothFormsOfTheBar() {
        // It used to read at 0.62 ink, halved again by a dissolve that ran to nothing exactly where
        // the glyph is drawn: 0.31 reaching the glass, which is the fragment the review saw.
        for (TopStatusBarState state : TopStatusBarState.values()) {
            for (Mark mark : landscapeMarks(state, true)) {
                if (mark.home) continue;
                assertTrue(state + " " + mark.page + " ink " + mark.effectiveInk,
                    mark.effectiveInk >= StatusBarLensMetrics.EFFECTIVE_INK_FLOOR);
            }
        }
        Mark peek = markFor(landscapeMarks(TopStatusBarState.COMPACT, true), PaneWallPage.WIDGETS);
        assertEquals(0.85f, peek.ink, 0.001f);
        assertEquals(0.5f, peek.fadeOuterAlpha, 0.001f);
        assertEquals(0.6375f, peek.effectiveInk, 0.001f);
        // Quieter in colour, not in ink: the neighbour is still drained towards the neutral, and
        // only the mark at home wears a glow.
        assertEquals(StatusBarLensMetrics.NEIGHBOUR_DRAIN, peek.drain, 0.001f);
        assertEquals(0f, peek.glow, 0.001f);
        Mark home = markFor(landscapeMarks(TopStatusBarState.EXPANDED, true), PaneWallPage.TERMINAL);
        assertEquals(1f, home.ink, 0.001f);
        assertEquals(0f, home.drain, 0.001f);
        assertTrue(home.glow > 0f);
    }

    @Test public void aDisplayThatIsNotRunningIsQuieterButNeverFallsThroughTheFloor() {
        // At home it reads exactly as it always has: 0.6 of full ink says nothing is running.
        List<Mark> atHome = StatusBarLensMetrics.marks(
            Bar.of(BAR_W, EXPANDED_H, DENSITY, false, TopStatusBarState.EXPANDED),
            RING, PaneWallPage.DISPLAY, 0f, BAR_W, false);
        assertEquals(StatusBarLensMetrics.STOPPED_DISPLAY_INK,
            markFor(atHome, PaneWallPage.DISPLAY).ink, 0.001f);
        // Peeking, 0.6 of an already drained mark used to leave 0.37 ink and 0.19 on the glass.
        // The floor catches it first, and the dissolve keeps three quarters of that.
        for (TopStatusBarState state : TopStatusBarState.values()) {
            Mark stopped = markFor(landscapeMarks(state, false), PaneWallPage.DISPLAY);
            assertEquals(StatusBarLensMetrics.PEEK_INK_FLOOR, stopped.ink, 0.001f);
            assertEquals(0.525f, stopped.effectiveInk, 0.001f);
            assertTrue(stopped.effectiveInk >= StatusBarLensMetrics.EFFECTIVE_INK_FLOOR);
            // Still the quieter of the two neighbours, which is the whole point of the dimming.
            Mark running = markFor(landscapeMarks(state, true), PaneWallPage.DISPLAY);
            assertTrue(stopped.ink < running.ink);
        }
    }

    @Test public void aColumnsMarksQueueDownItAndTakeTheWholeOfItsWidth() {
        int columnW = 68;  // 34dp
        int columnH = 600;
        List<Mark> marks = StatusBarLensMetrics.marks(
            Bar.of(columnW, columnH, DENSITY, true, TopStatusBarState.COMPACT),
            RING, PaneWallPage.TERMINAL, 0f, columnH, true);
        assertEquals(2, marks.size());
        Mark above = markFor(marks, PaneWallPage.WIDGETS);
        assertEquals(columnW / 2f, above.centerX, 0.01f);
        assertEquals(0f, above.centerY, 0.01f);
        assertEquals(96f, above.target.height(), 0.01f);
        assertEquals(columnW, above.target.width(), 0.01f);
        Mark below = markFor(marks, PaneWallPage.DISPLAY);
        assertEquals(columnH, below.centerY, 0.01f);
        assertEquals(columnH - 96f, below.target.top, 0.01f);
    }

    @Test public void aMarkThatHasLeftIsNeitherDrawnNorTappable() {
        // Two widths away the leaving mark has dissolved; nothing of it is reported.
        List<Mark> marks = StatusBarLensMetrics.marks(
            Bar.of(BAR_W, EXPANDED_H, DENSITY, false, TopStatusBarState.EXPANDED),
            RING, PaneWallPage.TERMINAL, BAR_W, BAR_W, true);
        for (Mark mark : marks) assertTrue(mark.page != PaneWallPage.DISPLAY);
    }

    @Test public void theRowStartsAfterTheHomeMarkAndItsGap() {
        assertEquals(Math.round((20f + 36f + 8f) * DENSITY),
            StatusBarLensMetrics.leadingCellWidthPx(DENSITY));
    }
}
