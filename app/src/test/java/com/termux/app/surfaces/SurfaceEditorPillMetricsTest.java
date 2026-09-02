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
}
