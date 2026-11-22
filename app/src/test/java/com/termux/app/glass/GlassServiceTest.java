package com.termux.app.glass;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;

import com.termux.app.window.FramePipeline;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.FrameStep;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The glass service (ADR 0029 sections 1 and 4): a policy event moves kinds at once and the frame
 * table's last step re-dresses each moved surface back to front; nothing else re-dresses; the Live
 * core runs on every drawn frame only while it holds a picture.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassServiceTest {

    private FramePipeline frames;
    private GlassService glass;
    private final List<String> redressed = new ArrayList<>();

    @Before
    public void setUp() {
        frames = new FramePipeline();
        glass = new GlassService(frames);
    }

    private static View view() {
        return new View(RuntimeEnvironment.getApplication());
    }

    private GlassKind register(String element, int priority) {
        return glass.register(new GlassEntry(element, view(), GlassKind.LIVE, priority),
            kind -> redressed.add(element + "=" + kind));
    }

    private static void frame() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
    }

    @Test
    public void flippingLiveReDressesEverySurfaceInTheNextFrameBackToFront() {
        register("dock", GlassEntry.PRIORITY_DOCK);
        register("float", GlassEntry.PRIORITY_FLOAT);
        register("widget", GlassEntry.PRIORITY_WIDGET);
        frame();
        assertTrue(redressed.isEmpty());

        glass.setLiveOn(true);
        assertTrue("the event only books the frame", redressed.isEmpty());
        assertEquals(GlassKind.LIVE, glass.granted("dock"));
        frame();

        assertEquals(Arrays.asList("widget=LIVE", "float=LIVE", "dock=LIVE"), redressed);
        assertEquals(1, glass.policyEvents());
    }

    @Test
    public void framesWithoutAPolicyEventReDressNothing() {
        glass.setLiveOn(true);
        register("dock", GlassEntry.PRIORITY_DOCK);
        register("widget", GlassEntry.PRIORITY_WIDGET);
        // Motion, landings and what sits above are frames like any other: no kind moves.
        for (int i = 0; i < 5; i++) {
            frames.flush("test frame " + i);
        }
        assertTrue(redressed.isEmpty());
        assertEquals(GlassKind.LIVE, glass.granted("widget"));
    }

    @Test
    public void batterySaverOrATrimTakesEffectAtOnce() {
        glass.setLiveOn(true);
        register("dock", GlassEntry.PRIORITY_DOCK);
        // Battery saver puts the Live policy out.
        glass.setLiveOn(false);
        frame();
        assertEquals(Arrays.asList("dock=WALLPAPER"), redressed);
        redressed.clear();
        glass.setLiveOn(true);
        glass.setMemoryShort(true);
        frame();
        // Live on then off again inside one frame: the surface is told once, its kind now.
        assertEquals(GlassKind.WALLPAPER, glass.granted("dock"));
        assertEquals(1, redressed.size());
    }

    @Test
    public void aSecondViewForAnElementIsRefused() {
        View dock = view();
        glass.register(new GlassEntry("dock", dock, GlassKind.LIVE, GlassEntry.PRIORITY_DOCK), null);
        assertNull(glass.register(new GlassEntry("dock", view(), GlassKind.LIVE,
            GlassEntry.PRIORITY_DOCK), null));
        // The same view again is the entry it already has.
        assertEquals(GlassKind.WALLPAPER, glass.register(new GlassEntry("dock", dock,
            GlassKind.LIVE, GlassEntry.PRIORITY_DOCK), null));
        assertEquals(GlassKind.WALLPAPER, glass.grantedTo("dock", dock));
        assertNull(glass.grantedTo("dock", view()));
        assertEquals(1, glass.size());
    }

    @Test
    public void aFreedSlotGoesBackToTheSurfaceThatGaveWay() {
        glass.setLiveOn(true);
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            register("widget" + i, GlassEntry.PRIORITY_WIDGET);
        }
        register("waiting", GlassEntry.PRIORITY_WIDGET);
        register("float", GlassEntry.PRIORITY_FLOAT);
        frame();
        // The float took the newest widget's slot.
        assertEquals(Arrays.asList("widget7=WALLPAPER"), redressed);
        glass.unregister("widget0");
        frame();
        assertEquals(Arrays.asList("widget7=WALLPAPER", "widget7=LIVE"), redressed);
        assertEquals(GlassKind.WALLPAPER, glass.granted("waiting"));
    }

    @Test
    public void theLiveCoreIsArmedOnlyWhileItHoldsAPicture() {
        boolean[] holds = {true};
        glass.setLiveCore(() -> holds[0]);
        frames.flush("test");
        assertTrue(frames.isArmed(FrameStep.GLASS_COMMIT));
        holds[0] = false;
        frames.flush("test");
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
    }

    @Test
    public void batterySaverIsWatched() {
        int[] heard = {0};
        Application app = RuntimeEnvironment.getApplication();
        glass.watchPowerSave(app, () -> heard[0]++);
        app.sendBroadcast(new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        frame();
        assertEquals(1, heard[0]);
        glass.watchPowerSave(app, null);
        app.sendBroadcast(new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        frame();
        assertEquals(1, heard[0]);
    }

    @Test
    public void aSurfaceDisplacedByANewcomerLetsGoAtOnceNotAtTheNextFrame() {
        glass.setLiveOn(true);
        for (int i = 0; i < GlassRegistry.LIVE_SLOTS; i++) {
            register("widget" + i, GlassEntry.PRIORITY_WIDGET);
        }
        frame();
        redressed.clear();
        // A panel registers: the widget it displaces is told inside the registration, so the
        // recorder never has to evict a picture the registry still counts as Live.
        glass.register(new GlassEntry("panel", view(), GlassKind.LIVE, GlassEntry.PRIORITY_DOCK,
            com.termux.app.window.WindowLayer.SHEETS, null), null);
        assertEquals(Arrays.asList("widget7=WALLPAPER"), redressed);
        assertEquals(1, glass.policyEvents());
        frame();
        assertEquals(Arrays.asList("widget7=WALLPAPER"), redressed);
    }

    @Test
    public void aSurfaceThatWantsEveryFrameIsVisitedInLayerOrderAndArmsTheCommit() {
        List<String> visits = new ArrayList<>();
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
        glass.register(new GlassEntry("panel", view(), GlassKind.SNAPSHOT,
            GlassEntry.PRIORITY_DOCK, com.termux.app.window.WindowLayer.SHEETS,
            () -> visits.add("panel")), null);
        glass.register(new GlassEntry("slab", view(), GlassKind.WALLPAPER,
            GlassEntry.PRIORITY_WIDGET, com.termux.app.window.WindowLayer.PAGES,
            () -> visits.add("slab")), null);
        assertTrue("a surface that wants frames arms the commit", frames.isArmed(FrameStep.GLASS_COMMIT));
        frames.flush("test");
        assertEquals(Arrays.asList("slab", "panel"), visits);
        // Every drawn frame, with no policy event.
        frames.flush("test");
        assertEquals(4, visits.size());
        assertEquals(0, glass.policyEvents());
        glass.unregister("panel");
        glass.unregister("slab");
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
    }

    @Test
    public void aSheetAtRestTakesTheSlotsOfWhatItHidesAndGivesThemBackAsItLeaves() {
        glass.setLiveOn(true);
        // Not on screen (no window here), on the pages beneath the sheets: hidden by the card.
        register("widget", GlassEntry.PRIORITY_WIDGET);
        glass.register(new GlassEntry("drawer", view(), GlassKind.LIVE, GlassEntry.PRIORITY_DOCK,
            WindowLayer.SHEETS, null), kind -> redressed.add("drawer=" + kind));
        glass.register(new GlassEntry("drawer-search", view(), GlassKind.LIVE,
            GlassEntry.PRIORITY_FLOAT, WindowLayer.SHEETS, null),
            kind -> redressed.add("drawer-search=" + kind));
        frame();
        redressed.clear();

        glass.cover("drawer", new android.graphics.Rect(0, 100, 1080, 2200));
        frame();
        assertEquals(Arrays.asList("widget=WALLPAPER"), redressed);
        assertEquals(GlassKind.LIVE, glass.granted("drawer"));
        assertEquals(GlassKind.LIVE, glass.granted("drawer-search"));
        // A surface that comes while the card stands is covered as it comes.
        assertEquals(GlassKind.WALLPAPER, register("float", GlassEntry.PRIORITY_FLOAT));

        redressed.clear();
        glass.uncover("drawer");
        frame();
        assertEquals(GlassKind.LIVE, glass.granted("widget"));
        assertEquals(GlassKind.LIVE, glass.granted("float"));

        // The card's entry going lets everything back too.
        glass.cover("drawer", new android.graphics.Rect(0, 100, 1080, 2200));
        assertEquals(GlassKind.WALLPAPER, glass.granted("widget"));
        glass.unregister("drawer");
        assertEquals(GlassKind.LIVE, glass.granted("widget"));
    }
}
