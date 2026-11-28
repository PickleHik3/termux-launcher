package com.termux.app.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Live window residency (live-window-residency-2026-11-28.md): rings with promotion, hysteresis
 * and eviction, routes, RAM tiers, the area budget and the trim table.
 */
public class GlassResidencyTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final Set<Long> NO_LANDINGS = Collections.emptySet();

    private static Map<Long, Float> at(Object... keyDistance) {
        Map<Long, Float> map = new HashMap<>();
        for (int i = 0; i < keyDistance.length; i += 2) {
            map.put(((Number) keyDistance[i]).longValue(), ((Number) keyDistance[i + 1]).floatValue());
        }
        return map;
    }

    // ---- Rings: promotion ------------------------------------------------------------------------

    @Test
    public void onScreenIsHotOneGestureAwayIsWarmTheRestIsCold() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        assertTrue(rings.update(at(1, 0f, 2, 0.5f, 3, 1f, 4, 1.5f), NO_LANDINGS, 0L, 16));
        assertEquals(GlassResidency.Ring.HOT, rings.ring(1));
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
        assertEquals(GlassResidency.Ring.WARM, rings.ring(3));
        assertEquals(GlassResidency.Ring.COLD, rings.ring(4));
        assertEquals(new HashSet<>(java.util.Arrays.asList(1L, 2L, 3L)), rings.held());
    }

    @Test
    public void aPredictedLandingIsWarmAheadOfTheGesture() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(1, 0f, 5, 4f), Collections.singleton(5L), 0L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(5));
    }

    @Test
    public void aKeyLeftOutIsColdAtOnce() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(1, 0f, 2, 1f), NO_LANDINGS, 0L, 16);
        assertTrue(rings.update(at(1, 0f), NO_LANDINGS, 10L, 16));
        assertEquals(GlassResidency.Ring.COLD, rings.ring(2));
    }

    // ---- Rings: hysteresis -----------------------------------------------------------------------

    @Test
    public void aWarmWindowInTheBandBetweenTheRingsNeverLeaves() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 1f), NO_LANDINGS, 0L, 16);
        // Scrolled to 1.8 screens away and left there for a minute: still inside the leave ring.
        rings.update(at(2, 1.8f), NO_LANDINGS, 100L, 16);
        rings.update(at(2, 1.8f), NO_LANDINGS, 60_000L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
    }

    @Test
    public void beyondTheLeaveRingAWindowWaitsOutTheDwell() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 1f), NO_LANDINGS, 0L, 16);
        rings.update(at(2, 3f), NO_LANDINGS, 1_000L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
        assertEquals(1_000L + GlassResidency.DWELL_MS, rings.nextChangeMs());
        rings.update(at(2, 3f), NO_LANDINGS, 1_000L + GlassResidency.DWELL_MS - 1L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
        rings.update(at(2, 3f), NO_LANDINGS, 1_000L + GlassResidency.DWELL_MS, 16);
        assertEquals(GlassResidency.Ring.COLD, rings.ring(2));
    }

    @Test
    public void backAndForthAcrossAColumnEdgeNeverDropsAndRebuilds() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(7, 0.9f), NO_LANDINGS, 0L, 16);
        boolean dropped = false;
        for (int i = 1; i <= 40; i++) {
            float distance = i % 2 == 0 ? 0.9f : 2.5f;
            rings.update(at(7, distance), NO_LANDINGS, i * 300L, 16);
            dropped |= rings.ring(7) == GlassResidency.Ring.COLD;
        }
        assertFalse("a window swung across the edge every 300 ms is never dropped", dropped);
    }

    @Test
    public void comingBackInsideTheEnterRingRestartsTheDwell() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 3f), Collections.singleton(2L), 0L, 16);
        rings.update(at(2, 3f), NO_LANDINGS, 1_500L, 16);
        rings.update(at(2, 0.5f), NO_LANDINGS, 1_800L, 16);
        rings.update(at(2, 3f), NO_LANDINGS, 2_500L, 16);
        rings.update(at(2, 3f), NO_LANDINGS, 4_000L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
        rings.update(at(2, 3f), NO_LANDINGS, 4_500L, 16);
        assertEquals(GlassResidency.Ring.COLD, rings.ring(2));
    }

    @Test
    public void aColdWindowBeyondTheEnterRingStaysCold() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 1.5f), NO_LANDINGS, 0L, 16);
        assertEquals(GlassResidency.Ring.COLD, rings.ring(2));
        assertEquals(Long.MAX_VALUE, rings.nextChangeMs());
    }

    // ---- Rings: eviction -------------------------------------------------------------------------

    @Test
    public void theWarmCapLetsTheFarthestGoAndKeepsLandingsAndTheAdjacentRing() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(1, 1f, 2, 1f, 3, 1f, 4, 1f, 5, 1f, 9, 4f), Collections.singleton(9L), 0L, 16);
        // Everything scrolls out a little: 1, 2 into the band, 3 farther, 9 a landing far away.
        rings.update(at(1, 1.2f, 2, 1.4f, 3, 1.9f, 4, 0.5f, 5, 0f, 9, 4f), Collections.singleton(9L), 10L, 2);
        assertEquals(GlassResidency.Ring.WARM, rings.ring(9));
        assertEquals(GlassResidency.Ring.WARM, rings.ring(1));
        assertEquals(GlassResidency.Ring.COLD, rings.ring(2));
        assertEquals(GlassResidency.Ring.COLD, rings.ring(3));
        assertEquals("the adjacent ring is never capped", GlassResidency.Ring.WARM, rings.ring(4));
        assertEquals(GlassResidency.Ring.HOT, rings.ring(5));
    }

    @Test
    public void clearLetsEverythingGo() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(1, 0f, 2, 1f), NO_LANDINGS, 0L, 16);
        rings.clear();
        assertTrue(rings.held().isEmpty());
        assertEquals(GlassResidency.Ring.COLD, rings.ring(1));
    }

    // ---- Routes ----------------------------------------------------------------------------------

    @Test
    public void aTileOverTheGroundTakesTheParityRoute() {
        GlassResidency.Tier tier = GlassResidency.tier(12L * GIB, false);
        assertSame(GlassRoute.PARITY, GlassResidency.route(GlassResidency.Surface.TILE, false,
            GlassResidency.Ring.HOT, tier));
        assertSame(GlassRoute.PARITY, GlassResidency.route(GlassResidency.Surface.TILE, false,
            GlassResidency.Ring.WARM, tier));
    }

    @Test
    public void onlyAnOnScreenTileOverALiveSceneFollowsTheScene() {
        GlassResidency.Tier tier = GlassResidency.tier(12L * GIB, false);
        assertSame(GlassRoute.EFFECT, GlassResidency.route(GlassResidency.Surface.TILE, true,
            GlassResidency.Ring.HOT, tier));
        assertSame(GlassRoute.PARITY, GlassResidency.route(GlassResidency.Surface.TILE, true,
            GlassResidency.Ring.WARM, tier));
        // The smallest tier never gives a tile a scene ground.
        assertSame(GlassRoute.PARITY, GlassResidency.route(GlassResidency.Surface.TILE, true,
            GlassResidency.Ring.HOT, GlassResidency.tier(3L * GIB, false)));
    }

    @Test
    public void floatsCarriedWindowsTheBarAndSheetsTakeTheEffectRoute() {
        GlassResidency.Tier tier = GlassResidency.tier(3L * GIB, true);
        for (GlassResidency.Surface surface : new GlassResidency.Surface[] {GlassResidency.Surface.FLOAT,
                GlassResidency.Surface.CARRIED, GlassResidency.Surface.BAR, GlassResidency.Surface.SHEET}) {
            assertSame(surface.name(), GlassRoute.EFFECT,
                GlassResidency.route(surface, false, GlassResidency.Ring.WARM, tier));
        }
    }

    // ---- Tiers and the area budget ---------------------------------------------------------------

    @Test
    public void theTiersGoByRam() {
        assertEquals(2.5f, GlassResidency.tier(12L * GIB, false).effectScreens, 0f);
        assertEquals(16, GlassResidency.tier(12L * GIB, false).warmWindows);
        assertEquals(1.75f, GlassResidency.tier(8L * GIB, false).effectScreens, 0f);
        assertEquals(12, GlassResidency.tier(8L * GIB, false).warmWindows);
        assertEquals(1f, GlassResidency.tier(6L * GIB, false).effectScreens, 0f);
        assertEquals(8, GlassResidency.tier(6L * GIB, false).warmWindows);
        assertEquals(0.5f, GlassResidency.tier(4L * GIB, false).effectScreens, 0f);
        assertEquals(4, GlassResidency.tier(4L * GIB, false).warmWindows);
        assertSame("a low-RAM device takes the smallest tier", GlassResidency.tier(4L * GIB, false),
            GlassResidency.tier(16L * GIB, true));
    }

    @Test
    public void theAreaBudgetLeavesRoomForTheHeaviestNeighbour() {
        GlassResidency.Tier pong = GlassResidency.tier(12L * GIB, false);
        long screen = 1080L * 2412L;
        // Pong's screen: 2.5 screens = 6,512,400 px (26.0 MB of 4-byte layers).
        assertEquals(Math.round(2.5 * screen), GlassResidency.effectAreaBudgetPx(pong, 1080, 2412, 0L));
        assertEquals(26_049_600L, GlassResidency.effectAreaBudgetPx(pong, 1080, 2412, 0L) * 4L);
        assertEquals(Math.round(2.5 * screen) - screen,
            GlassResidency.effectAreaBudgetPx(pong, 1080, 2412, screen));
        assertEquals(0L, GlassResidency.effectAreaBudgetPx(pong, 1080, 2412, 10L * screen));
    }

    // ---- Navigation centre -----------------------------------------------------------------------

    @Test
    public void theNavigationCentreHoldsInsideItsBand() {
        assertEquals(2, GlassResidency.navigationCentre(2, 2.6f, 0, 5));
        assertEquals(2, GlassResidency.navigationCentre(2, 1.3f, 0, 5));
        assertEquals(3, GlassResidency.navigationCentre(2, 2.8f, 0, 5));
        assertEquals(1, GlassResidency.navigationCentre(2, 1.2f, 0, 5));
        // Hovering at half a level between 2 and 3 changes nothing either way.
        assertEquals(2, GlassResidency.navigationCentre(2, 2.5f, 0, 5));
        assertEquals(3, GlassResidency.navigationCentre(3, 2.5f, 0, 5));
        assertEquals(1, GlassResidency.navigationCentre(0, 0.9f, 1, 5));
    }

    // ---- Trim ------------------------------------------------------------------------------------

    @Test
    public void eachTrimLetsGoWhatItsRowSays() {
        assertFalse(GlassResidency.trim(0).any());
        GlassResidency.Trim moderate = GlassResidency.trim(5);
        assertTrue(moderate.dropBeyondAdjacent);
        assertTrue(moderate.halveWarm);
        assertFalse(moderate.frontOnly);
        assertFalse(moderate.memoryShort);
        for (int level : new int[] {10, 15, 20, 40, 80}) {
            GlassResidency.Trim trim = GlassResidency.trim(level);
            assertTrue("level " + level, trim.frontOnly);
            assertTrue("level " + level, trim.memoryShort);
        }
    }

    @Test
    public void theTrimLevelsAreAndroidsOwn() {
        assertEquals(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            GlassResidency.TRIM_RUNNING_MODERATE);
        assertEquals(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, GlassResidency.TRIM_RUNNING_LOW);
        assertEquals(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            GlassResidency.TRIM_RUNNING_CRITICAL);
        assertEquals(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, GlassResidency.TRIM_UI_HIDDEN);
    }

    @Test
    public void peekAnswersWhatUpdateWouldWithoutMovingAnything() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 1f), NO_LANDINGS, 0L, 16);
        assertEquals(GlassResidency.Ring.WARM, rings.peek(2, 3f, false, 100L));
        assertEquals(GlassResidency.Ring.COLD, rings.peek(3, 1.5f, false, 100L));
        assertEquals(GlassResidency.Ring.WARM, rings.peek(3, 1.5f, true, 100L));
        assertEquals(GlassResidency.Ring.HOT, rings.peek(3, 0f, false, 100L));
        assertEquals("nothing moved", GlassResidency.Ring.COLD, rings.ring(3));
    }

    @Test
    public void aTrimLetsTheBandGoAndKeepsTheAdjacentRing() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(1, 0f, 2, 1f, 3, 1f), NO_LANDINGS, 0L, 16);
        rings.update(at(1, 0f, 2, 0.6f, 3, 1.8f), NO_LANDINGS, 10L, 16);
        rings.letGoBeyond(GlassResidency.ENTER_SCREENS);
        assertEquals(GlassResidency.Ring.HOT, rings.ring(1));
        assertEquals(GlassResidency.Ring.WARM, rings.ring(2));
        assertEquals(GlassResidency.Ring.COLD, rings.ring(3));
    }

    @Test
    public void aWindowInTheBandBooksNoDwell() {
        GlassResidency.Rings rings = new GlassResidency.Rings();
        rings.update(at(2, 1f), NO_LANDINGS, 0L, 16);
        rings.update(at(2, 1.5f), NO_LANDINGS, 10_000L, 16);
        assertEquals("nothing can change until it moves", Long.MAX_VALUE, rings.nextChangeMs());
    }
}
