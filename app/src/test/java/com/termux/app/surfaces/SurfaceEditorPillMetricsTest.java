package com.termux.app.surfaces;

import com.termux.app.surfaces.SurfaceEditorPillMetrics.Mode;

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
    public void theCanvasCentresInItsOwnRegion() {
        assertEquals(REGION_TOP + ((800 - PILL) / 2),
            SurfaceEditorPillMetrics.parkCenteredTopPx(PILL, REGION_TOP, REGION_BOTTOM));
    }

    @Test
    public void centringNeverPlacesThePillAboveTheRegion() {
        assertEquals(500, SurfaceEditorPillMetrics.parkCenteredTopPx(PILL, 500, 560));
    }

    @Test
    public void theShapeDegradesWithTheRoomAndNeverBelowOneRow() {
        assertEquals(Mode.FULL, SurfaceEditorPillMetrics.modeFor(400, 200, 150));
        assertEquals(Mode.FULL, SurfaceEditorPillMetrics.modeFor(200, 200, 150));
        assertEquals(Mode.COMPACT, SurfaceEditorPillMetrics.modeFor(199, 200, 150));
        assertEquals(Mode.COMPACT, SurfaceEditorPillMetrics.modeFor(150, 200, 150));
        assertEquals(Mode.ONE_ROW, SurfaceEditorPillMetrics.modeFor(149, 200, 150));
        // Issue #20: a system IME can collapse the band to nothing. One row is still the answer.
        assertEquals(Mode.ONE_ROW, SurfaceEditorPillMetrics.modeFor(0, 200, 150));
        assertEquals(Mode.ONE_ROW, SurfaceEditorPillMetrics.modeFor(-40, 200, 150));
    }
}
