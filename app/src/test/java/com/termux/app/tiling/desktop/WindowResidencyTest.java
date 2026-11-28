package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.TerlGlassWear;
import com.termux.app.glass.GlassRegistry;
import com.termux.app.glass.GlassResidency;
import com.termux.app.glass.GlassRoute;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The tiling desktop's live window residency: which places and windows hold views across renders,
 * and the route and area every window's glass is handed.
 */
public class WindowResidencyTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final GlassResidency.Tier PONG = GlassResidency.tier(12L * GIB, false);
    private static final Set<Integer> NONE = Collections.emptySet();

    private static GlassRegistry.Routing routing(WindowResidency.Commit commit, long windowId) {
        return commit.routes.get(TerlGlassWear.windowElement(windowId));
    }

    @Test
    public void thePlaceInFrontAndItsNeighboursHoldViews() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 2, NONE);
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), residency.holdLevels(0, 5));
    }

    @Test
    public void aPlaceLeftBehindStaysThroughItsDwellThenGoes() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 2, NONE);
        residency.holdLevels(0, 5);
        // Moved two places up: level 1 is now 2 away, inside the leave ring, and is kept.
        residency.begin(100L, 3, NONE);
        assertTrue(residency.holdLevels(0, 5).contains(1));
        // Three places on: beyond the leave ring, kept until the dwell runs out.
        residency.begin(200L, 4, NONE);
        assertTrue(residency.holdLevels(0, 5).contains(1));
        residency.begin(200L + GlassResidency.DWELL_MS, 4, NONE);
        assertFalse(residency.holdLevels(0, 5).contains(1));
    }

    @Test
    public void aPredictedLandingIsHeldAheadOfTheJump() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, Collections.singleton(5));
        assertTrue(residency.holdLevels(0, 5).contains(5));
        assertTrue(residency.isLanding(5));
    }

    @Test
    public void aTileOverTheGroundIsLiveOnTheParityRouteWithNoArea() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(10, 0f, false, GlassResidency.Surface.TILE, 5_000_000L, 1, true, true);
        residency.note(11, 0.5f, false, GlassResidency.Surface.TILE, 5_000_000L, 1, true, true);
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertEquals(GlassRoute.PARITY, routing(commit, 10).route);
        assertEquals(0L, routing(commit, 10).areaPx);
        assertTrue(routing(commit, 10).hot);
        assertEquals(GlassRoute.PARITY, routing(commit, 11).route);
        assertFalse(routing(commit, 11).hot);
    }

    @Test
    public void onlyOnScreenTilesFollowALiveScene() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(10, 0f, false, GlassResidency.Surface.TILE, 4_000L, 1, true, true);
        residency.note(11, 1f, false, GlassResidency.Surface.TILE, 4_000L, 1, true, true);
        WindowResidency.Commit commit = residency.commit(true, 1080, 2412);
        assertEquals(GlassRoute.EFFECT, routing(commit, 10).route);
        assertEquals(4_000L, routing(commit, 10).areaPx);
        assertEquals(GlassRoute.PARITY, routing(commit, 11).route);
    }

    @Test
    public void aFloatTakesTheEffectRouteAndItsAreaCountsWhenOnScreen() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(20, 0f, false, GlassResidency.Surface.FLOAT, 6_000L, 1, true, false);
        residency.note(21, 0f, false, GlassResidency.Surface.FLOAT, 3_000L, -1, true, false);
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertEquals(GlassRoute.EFFECT, routing(commit, 20).route);
        assertTrue(routing(commit, 20).hot);
        assertEquals("a pinned Float shows over the place in front", GlassRoute.EFFECT,
            routing(commit, 21).route);
    }

    @Test
    public void theAreaBudgetLeavesRoomForTheHeaviestNeighbour() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 2, NONE);
        residency.holdLevels(0, 5);
        residency.note(30, 1f, false, GlassResidency.Surface.FLOAT, 1_000_000L, 1, true, false);
        residency.note(31, 1f, false, GlassResidency.Surface.FLOAT, 1_000_000L, 1, true, false);
        residency.note(32, 1f, false, GlassResidency.Surface.FLOAT, 1_500_000L, 3, true, false);
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertEquals(GlassResidency.effectAreaBudgetPx(PONG, 1080, 2412, 2_000_000L), commit.effectAreaBudgetPx);
    }

    @Test
    public void theCarriedWindowTakesTheEffectRouteAtTheGesturesDoor() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(10, 0f, false, GlassResidency.Surface.TILE, 4_000L, 1, true, true);
        assertEquals(GlassRoute.PARITY, routing(residency.commit(false, 1080, 2412), 10).route);
        residency.setCarried(10);
        assertEquals(GlassRoute.EFFECT, routing(residency.commit(false, 1080, 2412), 10).route);
        residency.setCarried(Long.MIN_VALUE);
        assertEquals(GlassRoute.PARITY, routing(residency.commit(false, 1080, 2412), 10).route);
    }

    @Test
    public void aColumnThatNeverEnteredHoldsNothingAndIsNotRouted() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        assertFalse(residency.holdsWindow(40, 1.5f, false));
        residency.note(40, 1.5f, false, GlassResidency.Surface.TILE, 4_000L, 1, false, true);
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertNull(routing(commit, 40));
        assertFalse(commit.rerender);
    }

    @Test
    public void aColumnScrolledIntoTheBandKeepsItsViews() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(40, 1f, false, GlassResidency.Surface.TILE, 4_000L, 1, true, true);
        residency.commit(false, 1080, 2412);
        residency.begin(500L, 1, NONE);
        residency.holdLevels(0, 3);
        assertTrue(residency.holdsWindow(40, 1.8f, false));
        assertTrue("beyond the leave ring it waits out the dwell", residency.holdsWindow(40, 3f, false));
    }

    @Test
    public void theWarmCapLetsAColumnGoAndAsksForAnotherRender() {
        GlassResidency.Tier small = GlassResidency.tier(3L * GIB, false);
        WindowResidency residency = new WindowResidency(small);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        for (long id = 1; id <= 6; id++) residency.note(id, 1f, false, GlassResidency.Surface.TILE, 1L, 1, true, true);
        residency.commit(false, 1080, 2412);
        // Six columns in the band, beyond the enter ring: the smallest tier keeps 4.
        residency.begin(10L, 1, NONE);
        residency.holdLevels(0, 3);
        for (long id = 1; id <= 6; id++) {
            residency.note(id, 1f + id / 10f, false, GlassResidency.Surface.TILE, 1L, 1, true, true);
        }
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertTrue(commit.rerender);
        // The next render no longer holds the farthest two.
        residency.begin(20L, 1, NONE);
        residency.holdLevels(0, 3);
        assertFalse(residency.holdsWindow(6, 1.6f, false));
        assertFalse(residency.holdsWindow(5, 1.5f, false));
        assertTrue(residency.holdsWindow(4, 1.4f, false));
    }

    @Test
    public void aWindowHeldByItsPlaceIsRoutedWhateverItsRing() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 5);
        // A Dwindle tile on a place two away (held by the place's band): routed, never re-rendered.
        residency.note(50, 2f, false, GlassResidency.Surface.TILE, 1L, 3, true, false);
        WindowResidency.Commit commit = residency.commit(false, 1080, 2412);
        assertEquals(GlassRoute.PARITY, routing(commit, 50).route);
        assertFalse(commit.rerender);
    }

    @Test
    public void aRunningLowTrimKeepsOnlyThePlaceInFrontUntilResume() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 2, NONE);
        residency.holdLevels(0, 5);
        residency.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        residency.begin(10L, 2, NONE);
        assertEquals(Collections.singleton(2), residency.holdLevels(0, 5));
        assertTrue(residency.resumed());
        assertFalse("nothing to rebuild the second time", residency.resumed());
        residency.begin(20L, 2, NONE);
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), residency.holdLevels(0, 5));
    }

    @Test
    public void aModerateTrimLetsTheBandGo() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(40, 1f, false, GlassResidency.Surface.TILE, 1L, 1, true, true);
        residency.commit(false, 1080, 2412);
        residency.begin(10L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(40, 1.5f, false, GlassResidency.Surface.TILE, 1L, 1, true, true);
        residency.commit(false, 1080, 2412);
        residency.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE);
        residency.begin(20L, 1, NONE);
        residency.holdLevels(0, 3);
        assertFalse(residency.holdsWindow(40, 1.5f, false));
    }

    @Test
    public void theEffectAreaIsTheWindowAndItsBlurMarginEachSide() {
        assertEquals((1038L + 106L) * (1900L + 106L), WindowResidency.effectAreaPx(1038f, 1900f, 53));
    }
}
