package com.termux.app.surfaces;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The pill's placement rules, held at the cases the screens are drawn for: a top-anchored status
 * bar, a bottom-anchored dock, a keyboard whose only free neighbour is taken by the dock, and the
 * squeeze from issue #20.
 */
public class SurfaceEditorPillMetricsTest {

    private static final int REGION_TOP = 100;
    private static final int REGION_BOTTOM = 900;
    private static final int PILL = 180;
    private static final int STANDOFF = 16;

    @Test
    public void topAnchoredSurfaceParksThePillBelowItself() {
        // Status bar occupying 100..160: the pill sits one standoff under its bottom edge.
        assertEquals(160 + STANDOFF, SurfaceEditorPillMetrics.parkTopPx(
            100, 160, true, PILL, STANDOFF, REGION_TOP, REGION_BOTTOM));
    }

    @Test
    public void bottomAnchoredSurfaceParksThePillAboveItself() {
        // Dock top edge at 800: the pill's bottom edge lands one standoff above it.
        int top = SurfaceEditorPillMetrics.parkTopPx(
            800, 900, false, PILL, STANDOFF, REGION_TOP, REGION_BOTTOM);
        assertEquals(800 - STANDOFF - PILL, top);
    }

    @Test
    public void aSurfaceWithNoRoomOnItsOwnSideIsPushedBackInsideTheRegion() {
        // The keyboard's top edge is below the region entirely — the dock occupies the only band
        // between them — so the pill ends up above the dock rather than under the region's floor.
        int top = SurfaceEditorPillMetrics.parkTopPx(
            950, 1300, false, PILL, STANDOFF, REGION_TOP, REGION_BOTTOM);
        assertEquals(REGION_BOTTOM - PILL, top);
        assertTrue("the pill must stay inside the region", top + PILL <= REGION_BOTTOM);
    }

    @Test
    public void aTopAnchoredSurfaceTallerThanTheRegionStillLeavesTheHeaderOnScreen() {
        int top = SurfaceEditorPillMetrics.parkTopPx(
            0, 880, true, PILL, STANDOFF, REGION_TOP, REGION_BOTTOM);
        assertEquals(REGION_BOTTOM - PILL, top);
    }

    @Test
    public void aRegionShorterThanThePillPinsItToTheTop() {
        // Below this the pill overlaps what is above it rather than having its own header pushed
        // off the top of the screen, which is the failure clamping to zero used to cause.
        assertEquals(500, SurfaceEditorPillMetrics.parkTopPx(
            600, 700, false, PILL, STANDOFF, 500, 600));
        assertEquals(500, SurfaceEditorPillMetrics.parkTopPx(
            400, 500, true, PILL, STANDOFF, 500, 600));
    }

    @Test
    public void aRegionWideTargetParksAtTheRegionsFoot() {
        // The shared layer and the canvas own the whole terminal, so the card goes to the bottom of
        // it and the free room stays in one piece above — the block the user touches to pick the
        // terminal. One standoff clear of whatever bounds the region below.
        assertEquals(REGION_BOTTOM - STANDOFF - PILL,
            SurfaceEditorPillMetrics.parkRegionFootTopPx(PILL, STANDOFF, REGION_TOP, REGION_BOTTOM));
    }

    @Test
    public void footParkingNeverPlacesThePillAboveTheRegion() {
        assertEquals(500,
            SurfaceEditorPillMetrics.parkRegionFootTopPx(PILL, STANDOFF, 500, 560));
    }

    @Test
    public void theBodyGivesWayWithTheRoomAndNeverBelowItsFloor() {
        // Room to spare: the body takes what is left after the card's own chrome and the standoffs.
        assertEquals(500 - 200 - (2 * STANDOFF),
            SurfaceEditorPillMetrics.bodyCapPx(500, 200, STANDOFF, 80, 360));
        // A tall list is capped rather than filling the screen.
        assertEquals(360, SurfaceEditorPillMetrics.bodyCapPx(4000, 200, STANDOFF, 80, 360));
        // Issue #20: a system IME can collapse the band to nothing. A usable strip of list is
        // still the answer — a body measured to zero is not.
        assertEquals(80, SurfaceEditorPillMetrics.bodyCapPx(220, 200, STANDOFF, 80, 360));
        assertEquals(80, SurfaceEditorPillMetrics.bodyCapPx(0, 200, STANDOFF, 80, 360));
        assertEquals(80, SurfaceEditorPillMetrics.bodyCapPx(-40, 200, STANDOFF, 80, 360));
    }

    // ---------------------------------------------------------------- the strip gives way too

    // The card at 2.75: a 68dp preset card, its 48dp floor, the 80dp body floor and the 14dp
    // standoff, with the header, the preset names and the pills beside them.
    private static final int CARD_FULL = 187;
    private static final int CARD_MIN = 132;
    private static final int BODY_FLOOR = 220;
    private static final int BODY_CEILING = 990;
    private static final int PILL_STANDOFF = 38;
    private static final int CHROME_BESIDE_CARDS = 300;

    private static int cardHeight(int regionPx) {
        return SurfaceEditorPillMetrics.presetCardHeightPx(regionPx, CHROME_BESIDE_CARDS,
            BODY_FLOOR, PILL_STANDOFF, CARD_FULL, CARD_MIN);
    }

    private static int bodyCap(int regionPx, int cardPx) {
        return SurfaceEditorPillMetrics.bodyCapPx(regionPx, CHROME_BESIDE_CARDS + cardPx,
            PILL_STANDOFF, BODY_FLOOR, BODY_CEILING);
    }

    @Test
    public void aRegionWithRoomKeepsTheCardsAtFullSize() {
        assertEquals(CARD_FULL, cardHeight(1200));
        assertEquals(CARD_FULL, cardHeight(900));
        // And the body is nowhere near its floor there, so nothing has given way at all.
        assertTrue(bodyCap(1200, cardHeight(1200)) > BODY_FLOOR);
        assertTrue(bodyCap(900, cardHeight(900)) > BODY_FLOOR);
    }

    @Test
    public void theStripShrinksBeforeTheRowsAreCut() {
        // The failure this pins: at 700px the body is on its floor with the strip still at full
        // size, so the card overhangs its region and the rows — the controls — are what is missing.
        int full = bodyCap(700, CARD_FULL);
        int shrunk = bodyCap(700, cardHeight(700));
        assertEquals(BODY_FLOOR, full);
        assertEquals(BODY_FLOOR, shrunk);
        assertTrue("the strip gave way", cardHeight(700) < CARD_FULL);
        assertTrue("and what it gave is room the card no longer overhangs by",
            CHROME_BESIDE_CARDS + cardHeight(700) + shrunk + (2 * PILL_STANDOFF)
                < CHROME_BESIDE_CARDS + CARD_FULL + full + (2 * PILL_STANDOFF));
    }

    @Test
    public void theCardFitsItsRegionUntilTheStripIsAtItsFloor() {
        // Swept rather than sampled: at every region height the card either fits, or the strip has
        // already given everything it has and the rows are what is left to cut.
        for (int region = 400; region <= 1400; region += 7) {
            int card = cardHeight(region);
            int total = CHROME_BESIDE_CARDS + card + bodyCap(region, card) + (2 * PILL_STANDOFF);
            assertTrue("region " + region + " fits or the strip is spent",
                total <= region || card == CARD_MIN);
            assertTrue("region " + region + " keeps a usable card",
                card >= CARD_MIN && card <= CARD_FULL);
            assertTrue("region " + region + " keeps a usable body",
                bodyCap(region, card) >= BODY_FLOOR);
        }
    }

    @Test
    public void aRegionWithNothingInItStillAsksForTheSmallestUsableCard() {
        assertEquals(CARD_MIN, cardHeight(0));
        assertEquals(CARD_MIN, cardHeight(-200));
        // A floor taller than the card itself is the card's own height, not more.
        assertEquals(40, SurfaceEditorPillMetrics.presetCardHeightPx(0, 0, 0, 0, 40, 132));
    }
}
