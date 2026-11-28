package com.termux.app.glass;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The glass registry (ADR 0029 sections 1 to 4): one entry per element, kinds granted from the
 * policy and an 8-slot budget by priority, changed only on a policy event, committed back to front.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassRegistryTest {

    private GlassRegistry registry;

    @Before
    public void setUp() {
        registry = new GlassRegistry();
        registry.setLiveOn(true);
    }

    private static View view() {
        return new View(RuntimeEnvironment.getApplication());
    }

    private static GlassEntry live(String element, int priority) {
        return new GlassEntry(element, view(), GlassKind.LIVE, priority);
    }

    @Test
    public void itRefusesASecondEntryForAnElement() {
        assertTrue(registry.register(live("dock", GlassEntry.PRIORITY_DOCK)));
        GlassEntry twin = live("dock", GlassEntry.PRIORITY_DOCK);
        assertFalse(registry.register(twin));
        assertEquals(1, registry.size());
        // The first entry stands.
        assertTrue(registry.entry("dock") != twin);
    }

    @Test
    public void liveIsGrantedAtRegistrationWithinEightSlots() {
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("widget" + i, GlassEntry.PRIORITY_WIDGET));
        }
        registry.register(live("widget8", GlassEntry.PRIORITY_WIDGET));
        assertEquals(GlassKind.LIVE, registry.granted("widget7"));
        // No worse surface to give way: Wallpaper until a slot frees.
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget8"));
        assertEquals(GlassRegistry.LIVE_SLOTS, registry.liveCount());
    }

    @Test
    public void aBetterSurfaceTakesTheNewestWorstSlot() {
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("widget" + i, GlassEntry.PRIORITY_WIDGET));
        }
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        assertEquals(GlassKind.LIVE, registry.granted("dock"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget7"));
        assertEquals(GlassKind.LIVE, registry.granted("widget6"));
        assertEquals(GlassRegistry.LIVE_SLOTS, registry.liveCount());
        // Only the surface that gave way is re-dressed.
        int changed = 0;
        for (int i = 0; i < registry.size(); i++) {
            if (registry.takeChanged(i)) {
                changed++;
                assertEquals("widget7", registry.entryAt(i).element);
            }
        }
        assertEquals(1, changed);
    }

    @Test
    public void aFreedSlotGoesToTheBestSurfaceWaiting() {
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("widget" + i, GlassEntry.PRIORITY_WIDGET));
        }
        registry.register(live("widget8", GlassEntry.PRIORITY_WIDGET));
        registry.register(live("float", GlassEntry.PRIORITY_FLOAT));
        // The float took widget7's slot; widget7 and widget8 wait.
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget7"));
        registry.unregister("widget0");
        assertEquals(GlassKind.LIVE, registry.granted("widget7"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget8"));
    }

    @Test
    public void unregisteringASurfaceThatWasNotLiveChangesNoOtherKind() {
        for (int i = 0; i <= GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("widget" + i, GlassEntry.PRIORITY_WIDGET));
        }
        for (int i = 0; i < registry.size(); i++) registry.takeChanged(i);
        registry.unregister("widget8");
        assertFalse(registry.anyChanged());
    }

    @Test
    public void theLiveSwitchTurnsEveryLiveSurfaceAtOnceAndBack() {
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        registry.register(live("float", GlassEntry.PRIORITY_FLOAT));
        registry.register(new GlassEntry("bar", view(), GlassKind.WALLPAPER,
            GlassEntry.PRIORITY_DOCK));
        registry.setLiveOn(false);
        assertEquals(GlassKind.WALLPAPER, registry.granted("dock"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("float"));
        assertTrue(registry.anyChanged());
        registry.setLiveOn(true);
        assertEquals(GlassKind.LIVE, registry.granted("dock"));
        assertEquals(GlassKind.LIVE, registry.granted("float"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("bar"));
    }

    @Test
    public void aSurfaceRegisteredWhileLiveIsOffIsWallpaper() {
        registry.setLiveOn(false);
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        assertEquals(GlassKind.WALLPAPER, registry.granted("dock"));
        assertFalse(registry.anyChanged());
    }

    @Test
    public void aMemoryTrimIsAPolicyEvent() {
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        registry.setMemoryShort(true);
        assertEquals(GlassKind.WALLPAPER, registry.granted("dock"));
        registry.setMemoryShort(false);
        assertEquals(GlassKind.LIVE, registry.granted("dock"));
    }

    @Test
    public void theCompositorFallsBackToWallpaper() {
        registry.register(new GlassEntry("keyboard", view(), GlassKind.COMPOSITOR,
            GlassEntry.PRIORITY_DOCK));
        assertEquals(GlassKind.WALLPAPER, registry.granted("keyboard"));
        registry.setCompositorOn(true);
        assertEquals(GlassKind.COMPOSITOR, registry.granted("keyboard"));
        // A Compositor surface takes no Live slot.
        assertEquals(0, registry.liveCount());
    }

    @Test
    public void aSettingThatDidNotMoveIsNoEvent() {
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        registry.setLiveOn(true);
        registry.setMemoryShort(false);
        assertFalse(registry.anyChanged());
    }

    @Test
    public void theCommitOrderIsBackToFront() {
        registry.register(live("dock", GlassEntry.PRIORITY_DOCK));
        registry.register(live("widgetA", GlassEntry.PRIORITY_WIDGET));
        registry.register(live("float", GlassEntry.PRIORITY_FLOAT));
        registry.register(live("widgetB", GlassEntry.PRIORITY_WIDGET));
        assertEquals("widgetA", registry.entryAt(0).element);
        assertEquals("widgetB", registry.entryAt(1).element);
        assertEquals("float", registry.entryAt(2).element);
        assertEquals("dock", registry.entryAt(3).element);
    }

    @Test
    public void anElementWithNoEntryHasNoKind() {
        assertNull(registry.granted("dock"));
        assertFalse(registry.unregister("dock"));
    }

    private static GlassEntry in(String element, int priority, WindowLayer layer) {
        return new GlassEntry(element, view(), GlassKind.LIVE, priority, layer, null);
    }

    @Test
    public void theCommitWalksTheWindowLayersBackToFront() {
        // Registered in the wrong order on purpose: a panel in the sheets, the dock in the chrome,
        // a widget and a float on the pages, the keyboard's frost on the ground.
        registry.register(in("panel", GlassEntry.PRIORITY_DOCK, WindowLayer.SHEETS));
        registry.register(in("dock", GlassEntry.PRIORITY_DOCK, WindowLayer.CHROME));
        registry.register(in("float", GlassEntry.PRIORITY_FLOAT, WindowLayer.PAGES));
        registry.register(in("widget", GlassEntry.PRIORITY_WIDGET, WindowLayer.PAGES));
        registry.register(in("frost", GlassEntry.PRIORITY_DOCK, WindowLayer.GROUND));
        String[] order = {"frost", "widget", "float", "dock", "panel"};
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], registry.entryAt(i).element);
        }
    }

    @Test
    public void aLayerOutranksThePriorityInTheCommitButNotInTheBudget() {
        // A dock-priority panel in the sheets is drawn after a float, yet the budget still serves
        // it before the float: the order a surface is drawn in is not the order it is paid for.
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(in("float" + i, GlassEntry.PRIORITY_FLOAT, WindowLayer.PAGES));
        }
        registry.register(in("panel", GlassEntry.PRIORITY_DOCK, WindowLayer.SHEETS));
        assertEquals(GlassKind.LIVE, registry.granted("panel"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("float7"));
        assertEquals("panel", registry.entryAt(registry.size() - 1).element);
    }

    @Test
    public void aPanelFreesItsSlotForTheSurfaceThatGaveWay() {
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(in("widget" + i, GlassEntry.PRIORITY_WIDGET, WindowLayer.PAGES));
        }
        registry.register(in("panel", GlassEntry.PRIORITY_DOCK, WindowLayer.SHEETS));
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget7"));
        registry.unregister("panel");
        assertEquals(GlassKind.LIVE, registry.granted("widget7"));
        assertEquals(GlassRegistry.LIVE_SLOTS, registry.liveCount());
    }

    @Test
    public void itCountsTheEntriesThatWantEveryFrame() {
        registry.register(in("dock", GlassEntry.PRIORITY_DOCK, WindowLayer.CHROME));
        assertEquals(0, registry.frameCount());
        registry.register(new GlassEntry("slab", view(), GlassKind.WALLPAPER,
            GlassEntry.PRIORITY_WIDGET, WindowLayer.PAGES, () -> { }));
        assertEquals(1, registry.frameCount());
        registry.unregister("slab");
        assertEquals(0, registry.frameCount());
    }

    @Test
    public void aCoveredSurfaceGivesItsLiveSlotToTheSheetOverIt() {
        // Eight floats fill every slot; the drawer's capsule, a float's place in the budget,
        // waits on Wallpaper: none is worse than it to give way.
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("float" + i, GlassEntry.PRIORITY_FLOAT));
        }
        registry.register(new GlassEntry("drawer-search", view(), GlassKind.LIVE,
            GlassEntry.PRIORITY_FLOAT, WindowLayer.SHEETS, null));
        assertEquals(GlassKind.WALLPAPER, registry.granted("drawer-search"));
        // The card comes to rest over the floats: they cannot be seen, so they hold no slot, and
        // the capsule riding the card is Live.
        java.util.HashSet<String> covered = new java.util.HashSet<>();
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) covered.add("float" + i);
        registry.setCovered(covered);
        assertEquals(GlassKind.LIVE, registry.granted("drawer-search"));
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            assertEquals(GlassKind.WALLPAPER, registry.granted("float" + i));
            assertTrue(registry.covered("float" + i));
        }
        assertEquals(1, registry.liveCount());
        // The card leaves: the floats come back in budget order, the oldest first, and the
        // capsule, the newest float, waits again.
        registry.setCovered(java.util.Collections.emptySet());
        assertEquals(GlassKind.LIVE, registry.granted("float0"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("drawer-search"));
        assertFalse(registry.covered("float0"));
    }

    @Test
    public void aSurfaceRegisteredUnderTheSheetIsNotLive() {
        assertTrue(registry.register(live("widget0", GlassEntry.PRIORITY_WIDGET), true));
        assertEquals(GlassKind.WALLPAPER, registry.granted("widget0"));
        assertEquals(0, registry.liveCount());
    }

    // ---- Routes (live window residency, 2026-11-28) ---------------------------------------------

    private static java.util.Map<String, GlassRegistry.Routing> routes(Object... elementRouting) {
        java.util.Map<String, GlassRegistry.Routing> map = new java.util.HashMap<>();
        for (int i = 0; i < elementRouting.length; i += 2) {
            map.put((String) elementRouting[i], (GlassRegistry.Routing) elementRouting[i + 1]);
        }
        return map;
    }

    private static GlassRegistry.Routing parity() {
        return new GlassRegistry.Routing(GlassRoute.PARITY, 0L, true);
    }

    private static GlassRegistry.Routing effect(long areaPx, boolean hot) {
        return new GlassRegistry.Routing(GlassRoute.EFFECT, areaPx, hot);
    }

    @Test
    public void aWindowIsGrantedLiveAtTheTilesPriority() {
        registry.register(live("terl-window-1", GlassEntry.PRIORITY_TILE));
        assertEquals(GlassKind.LIVE, registry.granted("terl-window-1"));
        assertEquals(GlassEntry.PRIORITY_TILE, registry.entry("terl-window-1").priority);
        // Floats outrank tiles, tiles outrank widgets.
        assertTrue(GlassEntry.PRIORITY_FLOAT < GlassEntry.PRIORITY_TILE);
        assertTrue(GlassEntry.PRIORITY_TILE < GlassEntry.PRIORITY_WIDGET);
    }

    @Test
    public void parityTilesAreLiveWithoutTakingASlot() {
        java.util.Map<String, GlassRegistry.Routing> routes = new java.util.HashMap<>();
        for (int i = 0; i < 12; i++) routes.put("tile" + i, parity());
        registry.setRoutes(routes);
        for (int i = 0; i < 12; i++) registry.register(live("tile" + i, GlassEntry.PRIORITY_TILE));
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            registry.register(live("float" + i, GlassEntry.PRIORITY_FLOAT));
        }
        for (int i = 0; i < 12; i++) {
            assertEquals(GlassKind.LIVE, registry.granted("tile" + i));
            assertEquals(GlassRoute.PARITY, registry.route("tile" + i));
        }
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            assertEquals(GlassKind.LIVE, registry.granted("float" + i));
            assertEquals(GlassRoute.EFFECT, registry.route("float" + i));
        }
        assertEquals(12 + GlassRegistry.LIVE_SLOTS, registry.liveCount());
        assertEquals(GlassRegistry.LIVE_SLOTS, registry.effectCount());
    }

    @Test
    public void aRouteChangeIsAPolicyEventThatRedressesTheSurface() {
        registry.register(live("tile", GlassEntry.PRIORITY_TILE));
        assertEquals(GlassRoute.EFFECT, registry.route("tile"));
        registry.setRoutes(routes("tile", parity()));
        assertEquals(GlassKind.LIVE, registry.granted("tile"));
        assertEquals(GlassRoute.PARITY, registry.route("tile"));
        assertTrue("the surface lets its layers go", registry.takeChanged(0));
        registry.setRoutes(routes("tile", parity()));
        assertFalse("the same routes again are no event", registry.anyChanged());
    }

    @Test
    public void shortMemoryLeavesParityTilesLiveAndTakesTheEffectSurfaces() {
        registry.setRoutes(routes("tile", parity(), "float", effect(100L, true)));
        registry.register(live("tile", GlassEntry.PRIORITY_TILE));
        registry.register(live("float", GlassEntry.PRIORITY_FLOAT));
        registry.setMemoryShort(true);
        assertEquals(GlassKind.LIVE, registry.granted("tile"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("float"));
        registry.setLiveOn(false);
        assertEquals("Live off: every window draws the stored frame", GlassKind.WALLPAPER,
            registry.granted("tile"));
    }

    @Test
    public void theAreaBudgetDropsTheWorstPriorityNewestFirst() {
        registry.setRoutes(routes("bar", effect(300L, true), "float0", effect(400L, true),
            "float1", effect(400L, true), "scene-tile", effect(500L, true)));
        registry.register(live("bar", GlassEntry.PRIORITY_DOCK));
        registry.register(live("float0", GlassEntry.PRIORITY_FLOAT));
        registry.register(live("float1", GlassEntry.PRIORITY_FLOAT));
        registry.register(live("scene-tile", GlassEntry.PRIORITY_TILE));
        assertEquals(1600L, registry.effectAreaPx());
        registry.setEffectAreaBudget(1000L);
        assertEquals(GlassKind.LIVE, registry.granted("bar"));
        assertEquals(GlassKind.LIVE, registry.granted("float0"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("float1"));
        assertEquals(GlassKind.WALLPAPER, registry.granted("scene-tile"));
        assertEquals(700L, registry.effectAreaPx());
        registry.setEffectAreaBudget(1700L);
        assertEquals(GlassKind.LIVE, registry.granted("float1"));
        assertEquals(GlassKind.LIVE, registry.granted("scene-tile"));
    }

    @Test
    public void aWarmEffectSurfaceHoldsNoArea() {
        registry.setEffectAreaBudget(500L);
        registry.setRoutes(routes("float-above", effect(400L, false), "float-here", effect(400L, true)));
        registry.register(live("float-above", GlassEntry.PRIORITY_FLOAT));
        registry.register(live("float-here", GlassEntry.PRIORITY_FLOAT));
        assertEquals(GlassKind.LIVE, registry.granted("float-above"));
        assertEquals(GlassKind.LIVE, registry.granted("float-here"));
        assertEquals(400L, registry.effectAreaPx());
    }

    @Test
    public void aRoutingGivenBeforeTheEntryComesIsTakenWhenItComes() {
        registry.setRoutes(routes("terl-window-7", parity()));
        registry.register(live("terl-window-7", GlassEntry.PRIORITY_TILE));
        assertEquals(GlassRoute.PARITY, registry.route("terl-window-7"));
        assertFalse(registry.anyChanged());
    }
}
