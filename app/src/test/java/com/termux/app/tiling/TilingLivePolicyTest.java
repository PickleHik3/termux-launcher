package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/** Where places stand and how far a column is from the screen (#418). */
public class TilingLivePolicyTest {

    @Test
    public void homeIsLevelZeroAndAWorkspaceItsNumber() {
        assertEquals(0, TilingLivePolicy.level(Place.home(0)));
        assertEquals(0, TilingLivePolicy.level(Place.home(3)));
        assertEquals(1, TilingLivePolicy.level(Place.workspace(1)));
        assertEquals(4, TilingLivePolicy.level(Place.workspace(4)));
    }

    @Test
    public void aPlaceStandsOneHeightPerLevelFromThePosition() {
        assertEquals(0f, TilingLivePolicy.offsetPx(1, 1f, 800), 0f);
        assertEquals(800f, TilingLivePolicy.offsetPx(1, 0f, 800), 0f);
        assertEquals(-800f, TilingLivePolicy.offsetPx(1, 2f, 800), 0f);
        assertEquals(400f, TilingLivePolicy.offsetPx(2, 1.5f, 800), 0f);
    }

    @Test
    public void aPlaceIsOnScreenOnlyWhileAnyOfItShows() {
        assertTrue(TilingLivePolicy.onScreen(1, 1f));
        assertTrue(TilingLivePolicy.onScreen(1, 0.01f));
        assertFalse(TilingLivePolicy.onScreen(1, 0f));
        assertFalse(TilingLivePolicy.onScreen(3, 1f));
        assertTrue(TilingLivePolicy.onScreen(2, 1.5f));
    }

    @Test
    public void homeRidesAboveWorkspaceOneAndIsAwayPastIt() {
        assertEquals(0f, TilingLivePolicy.homeTravel(true, 0f), 0f);
        assertEquals(0.5f, TilingLivePolicy.homeTravel(true, 0.5f), 0f);
        assertEquals(1f, TilingLivePolicy.homeTravel(true, 1f), 0f);
        assertEquals("never further than away", 1f, TilingLivePolicy.homeTravel(true, 3f), 0f);
        assertEquals("never below its rest", 0f, TilingLivePolicy.homeTravel(true, -0.1f), 0f);
    }

    @Test
    public void withoutHomeItIsAlwaysAway() {
        assertEquals(1f, TilingLivePolicy.homeTravel(false, 0f), 0f);
        assertEquals(1f, TilingLivePolicy.homeTravel(false, 1f), 0f);
    }

    @Test
    public void aColumnOnScreenIsNoScreenAwayAndOthersAreTheirGapInWidths() {
        assertEquals(0f, TilingLivePolicy.screensAway(100f, 600f, 0f, 1000f), 0f);
        assertEquals(0f, TilingLivePolicy.screensAway(900f, 1400f, 0f, 1000f), 0f);
        assertEquals(0.5f, TilingLivePolicy.screensAway(1500f, 2000f, 0f, 1000f), 1e-6f);
        assertEquals(1f, TilingLivePolicy.screensAway(-2500f, -1000f, 0f, 1000f), 1e-6f);
        // A column whose edge touches the screen's is just off it.
        assertEquals(0f, TilingLivePolicy.screensAway(1000f, 1500f, 0f, 1000f), 0f);
        assertEquals(0f, TilingLivePolicy.screensAway(10f, 20f, 0f, 0f), 0f);
    }
}
