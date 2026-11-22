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
}
