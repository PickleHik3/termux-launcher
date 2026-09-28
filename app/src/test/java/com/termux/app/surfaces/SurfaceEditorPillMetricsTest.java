package com.termux.app.surfaces;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Appearance card's placement rules, held at the cases the screens are drawn for: the foot of
 * the free room, a region squeezed shorter than the card, and the squeeze from issue #20.
 */
public class SurfaceEditorPillMetricsTest {

    private static final int REGION_TOP = 100;
    private static final int REGION_BOTTOM = 900;
    private static final int PILL = 180;
    private static final int STANDOFF = 16;

    @Test
    public void theCardStandsOnTheRegionsFootWhateverItIsOpenOn() {
        // A host 1000 tall whose free room ends at the dock's top edge, 900: the card's bottom edge
        // lands one standoff above it, which is a bottom margin of the dock and the standoff.
        assertEquals(1000 - 900 + STANDOFF,
            SurfaceEditorPillMetrics.parkBottomMarginPx(1000, STANDOFF, REGION_TOP, REGION_BOTTOM));
    }

    @Test
    public void aRegionShorterThanTheStandoffPutsTheEdgeAtItsTop() {
        assertEquals(1000 - 500,
            SurfaceEditorPillMetrics.parkBottomMarginPx(1000, STANDOFF, 500, 510));
        assertEquals("and never a negative margin", 0,
            SurfaceEditorPillMetrics.parkBottomMarginPx(400, STANDOFF, 500, 600));
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
}
