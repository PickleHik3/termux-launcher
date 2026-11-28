package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.TerlGlassWear;
import com.termux.app.glass.GlassRegistry;
import com.termux.app.glass.GlassResidency;
import com.termux.app.glass.GlassRoute;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The tiling desktop's live window residency: which places and windows hold views across renders,
 * and the route, area and place every window's glass is handed.
 */
public class WindowResidencyTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final GlassResidency.Tier PONG = GlassResidency.tier(12L * GIB, false);
    private static final GlassResidency.Tier SMALL = GlassResidency.tier(3L * GIB, false);
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

    /** Review P2-1: a place exactly two away leaves after the dwell; it does not stay for good. */
    @Test
    public void aPlaceTwoAwayLeavesAfterItsDwell() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 2, NONE);
        residency.holdLevels(0, 5);
        residency.begin(100L, 3, NONE);
        assertTrue("waiting out its dwell", residency.holdLevels(0, 5).contains(1));
        residency.begin(100L + GlassResidency.DWELL_MS, 3, NONE);
        assertFalse(residency.holdLevels(0, 5).contains(1));
    }

    /** Review P2-1: beyond the adjacent ring, places are capped by the RAM tier, farthest first. */
    @Test
    public void placesBeyondTheAdjacentRingAreCappedByTheTier() {
        WindowResidency residency = new WindowResidency(SMALL);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(1, 5);
        residency.begin(10L, 2, NONE);
        residency.holdLevels(1, 5);
        residency.begin(20L, 4, NONE);
        // 4 in front, 3 and 5 beside it; 1 and 2 wait out their dwell, but the small tier keeps one.
        assertEquals(new HashSet<>(Arrays.asList(2, 3, 4, 5)), residency.holdLevels(1, 5));
    }

    @Test
    public void aPredictedLandingIsHeldAheadOfTheJump() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, Collections.singleton(5));
        assertTrue(residency.holdLevels(0, 5).contains(5));
        assertTrue(residency.isLanding(5));
    }

    @Test
    public void aTileIsLiveOnTheParityRouteWithNoArea() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(10, 0f, false, GlassResidency.Surface.TILE, 5_000_000L, 1, true);
        residency.note(11, 0.5f, false, GlassResidency.Surface.TILE, 5_000_000L, 1, true);
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertEquals(GlassRoute.PARITY, routing(commit, 10).route);
        assertEquals(0L, routing(commit, 10).areaPx);
        assertTrue(routing(commit, 10).hot);
        assertEquals(GlassRoute.PARITY, routing(commit, 11).route);
        assertFalse(routing(commit, 11).hot);
    }

    @Test
    public void aFloatTakesTheEffectRouteGroupedByItsPlace() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(20, 0f, false, GlassResidency.Surface.FLOAT, 6_000L, 1, true);
        residency.note(21, 0f, false, GlassResidency.Surface.FLOAT, 3_000L, WindowResidency.PINNED_LEVEL, true);
        residency.note(22, 1f, false, GlassResidency.Surface.FLOAT, 4_000L, 2, true);
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertEquals(GlassRoute.EFFECT, routing(commit, 20).route);
        assertTrue(routing(commit, 20).hot);
        assertEquals(6_000L, routing(commit, 20).areaPx);
        assertEquals("a pinned Float shows over the place in front", GlassRoute.EFFECT, routing(commit, 21).route);
        assertFalse(routing(commit, 22).hot);
        assertEquals("a neighbour's Float is checked with its place", 2, routing(commit, 22).group);
    }

    /** Review P3-2: the area budget is the tier's resident budget less the blur layers and pool. */
    @Test
    public void theAreaBudgetComesFromTheResidentBudget() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertEquals((LiveBackdrop.residentBudgetBytes(PONG, 1080, 2412) - LiveBackdrop.BUDGET_BYTES) / 4L,
            commit.effectAreaBudgetPx);
        assertEquals(GlassResidency.effectAreaBudgetPx(PONG, 1080, 2412, 0L), commit.effectAreaBudgetPx);
    }

    @Test
    public void aColumnThatNeverEnteredHoldsNothingAndIsNotRouted() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        assertFalse(residency.holdsWindow(40, 1.5f, false));
        residency.note(40, 1.5f, false, GlassResidency.Surface.TILE, 4_000L, 1, false);
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertNull(routing(commit, 40));
        assertFalse(commit.rerender);
    }

    @Test
    public void aColumnScrolledIntoTheBandKeepsItsViews() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(40, 1f, false, GlassResidency.Surface.TILE, 4_000L, 1, true);
        residency.commit(1080, 2412);
        residency.begin(500L, 1, NONE);
        residency.holdLevels(0, 3);
        assertTrue(residency.holdsWindow(40, 1.8f, false));
        assertTrue("beyond the leave ring it waits out the dwell", residency.holdsWindow(40, 3f, false));
    }

    @Test
    public void theWarmCapLetsAColumnGoAndAsksForAnotherRender() {
        WindowResidency residency = new WindowResidency(SMALL);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        for (long id = 1; id <= 6; id++) residency.note(id, 1f, false, GlassResidency.Surface.TILE, 1L, 1, true);
        residency.commit(1080, 2412);
        residency.begin(10L, 1, NONE);
        residency.holdLevels(0, 3);
        for (long id = 1; id <= 6; id++) {
            residency.note(id, 1f + id / 10f, false, GlassResidency.Surface.TILE, 1L, 1, true);
        }
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertTrue(commit.rerender);
        residency.begin(20L, 1, NONE);
        residency.holdLevels(0, 3);
        assertFalse(residency.holdsWindow(6, 1.6f, false));
        assertFalse(residency.holdsWindow(5, 1.5f, false));
        assertTrue(residency.holdsWindow(4, 1.4f, false));
    }

    /**
     * Review P2-1: the windows of a place waiting out its dwell (Dwindle tiles, Floats, full-screen
     * and Linux windows) count against the warm cap like columns do: the small tier keeps four.
     */
    @Test
    public void aFarPlacesWindowsCountAgainstTheWarmCap() {
        WindowResidency residency = new WindowResidency(SMALL);
        residency.begin(0L, 3, NONE);
        residency.holdLevels(1, 5);
        for (long id = 1; id <= 8; id++) {
            residency.note(id, 1f, false, id <= 6 ? GlassResidency.Surface.TILE : GlassResidency.Surface.FLOAT,
                1L, 2, true);
        }
        residency.commit(1080, 2412);
        // Workspace 4 in front: Workspace 2 is two away, waiting out its dwell, still built.
        residency.begin(10L, 4, NONE);
        residency.holdLevels(1, 5);
        for (long id = 1; id <= 8; id++) {
            residency.note(id, 2f, false, id <= 6 ? GlassResidency.Surface.TILE : GlassResidency.Surface.FLOAT,
                1L, 2, true);
        }
        WindowResidency.Commit commit = residency.commit(1080, 2412);
        assertTrue("the cap let four go: render again", commit.rerender);
        residency.begin(20L, 4, NONE);
        residency.holdLevels(1, 5);
        int held = 0;
        for (long id = 1; id <= 8; id++) if (residency.holdsWindow(id, 2f, false)) held++;
        assertEquals(SMALL.warmWindows, held);
    }

    /** Review P2-3: while a gesture runs, the routes given at rest stand; only new windows are added. */
    @Test
    public void provisionalRoutesKeepWhatRestGaveAndAddOnlyNewWindows() {
        WindowResidency residency = new WindowResidency(PONG);
        residency.begin(0L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(20, 0f, false, GlassResidency.Surface.FLOAT, 6_000L, 1, true);
        Map<String, GlassRegistry.Routing> rest = residency.commit(1080, 2412).routes;
        residency.begin(10L, 2, NONE);
        residency.holdLevels(0, 3);
        residency.note(20, 1f, false, GlassResidency.Surface.FLOAT, 6_000L, 1, true);
        residency.note(30, 0f, false, GlassResidency.Surface.FLOAT, 5_000L, 2, true);
        Map<String, GlassRegistry.Routing> during = residency.provisional(new HashMap<>(rest));
        assertEquals("the Float in front keeps its grant until rest", rest.get(TerlGlassWear.windowElement(20)),
            during.get(TerlGlassWear.windowElement(20)));
        assertTrue(during.get(TerlGlassWear.windowElement(20)).hot);
        assertFalse("a new window comes off screen", during.get(TerlGlassWear.windowElement(30)).hot);
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
        residency.note(40, 1f, false, GlassResidency.Surface.TILE, 1L, 1, true);
        residency.commit(1080, 2412);
        residency.begin(10L, 1, NONE);
        residency.holdLevels(0, 3);
        residency.note(40, 1.5f, false, GlassResidency.Surface.TILE, 1L, 1, true);
        residency.commit(1080, 2412);
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
