package com.termux.app.glass;

import android.app.Application;
import android.os.Build;
import android.view.View;

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
}
