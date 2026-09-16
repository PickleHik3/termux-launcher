package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;
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
}
