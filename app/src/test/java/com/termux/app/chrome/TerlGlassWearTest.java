package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Terl's glass in the glass service (ADR 0029, pong 11-28): the control bar is the dock's entry,
 * a sheet a panel that holds its entry while it is in the window, and a policy event or a landed
 * stored frame dresses the glass again in the drawable its view already wears.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class TerlGlassWearTest {

    private FramePipeline frames;
    private GlassService glass;
    private TerlGlassWear wear;
    private FrameLayout window;
    private final List<String> log = new ArrayList<>();

    /** A builder that counts its builds and samples {@link #frame}. */
    private final class Counting implements TerlGlassWear.Builder {
        int builds;
        int liveBuilds;
        @Nullable Object frame = new Object();

        @Nullable @Override public Drawable build(@Nullable LivePicture live) {
            builds++;
            if (live != null) liveBuilds++;
            return new ColorDrawable(0x80000000);
        }

        @Nullable @Override public Object frameNow() {
            return frame;
        }
    }

    @Before
    public void setUp() {
        frames = new FramePipeline();
        glass = new GlassService(frames);
        glass.setLiveSource(new GlassService.LiveSource() {
            @Override public Object attach(View surface, float radiusPx, int priority) {
                log.add("attach");
                return null;
            }
            @Override public void detach(View surface) { log.add("detach"); }
        });
        wear = new TerlGlassWear(() -> glass);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        window = new FrameLayout(activity);
        activity.setContentView(window);
    }

    private View attached() {
        View view = new View(window.getContext());
        window.addView(view);
        return view;
    }

    @Test
    public void theControlBarIsTheDocksEntryInTheChromeLayer() {
        View bar = attached();
        wear.wear(bar, TerlGlassWear.element(true, "CONTROL_BAR", 1), WindowLayer.CHROME, 24f, true,
            new Counting());
        GlassEntry entry = glass.entry(TerlGlassWear.BAR_ELEMENT);
        assertNotNull("the bar holds the dock's entry", entry);
        assertSame(bar, entry.view);
        assertEquals(WindowLayer.CHROME, entry.layer);
        assertEquals(GlassEntry.PRIORITY_DOCK, entry.priority);
        assertEquals(GlassKind.LIVE, entry.wants);
    }

    @Test
    public void aSheetHoldsItsEntryWhileInTheWindowAndLetsItGoWhenItLeaves() {
        View sheet = attached();
        String element = TerlGlassWear.element(false, "SHEETS", System.identityHashCode(sheet));
        wear.wear(sheet, element, WindowLayer.SHEETS, 24f, true, new Counting());
        assertNotNull(glass.entry(element));
        assertEquals(WindowLayer.SHEETS, glass.entry(element).layer);
        window.removeView(sheet);
        assertNull("a closed sheet holds no entry", glass.entry(element));
        assertTrue("and its picture is let go", log.contains("detach"));
    }

    @Test
    public void anOpaqueGlassHoldsNoEntryAndSamplesNothing() {
        View bar = attached();
        Counting builder = new Counting();
        builder.frame = null;
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 0f, false, builder);
        assertNull(glass.entry(TerlGlassWear.BAR_ELEMENT));
        assertEquals(1, builder.builds);
    }

    @Test
    public void aPolicyEventDressesTheGlassAgainInTheDrawableTheViewWears() {
        View bar = attached();
        Counting builder = new Counting();
        Drawable worn = wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        bar.setBackground(worn);
        assertEquals("Live off: the stored frame", GlassKind.WALLPAPER,
            glass.granted(TerlGlassWear.BAR_ELEMENT));
        assertFalse(log.contains("attach"));
        // The Live blur switch: the service grants Live and, at its commit (the frame's last
        // step), the glass asks the recorder for its layers.
        glass.setLiveOn(true);
        frames.flush("test");
        assertEquals(GlassKind.LIVE, glass.granted(TerlGlassWear.BAR_ELEMENT));
        assertEquals("dressed again on the policy event", 2, builder.builds);
        assertTrue("the Live layers are asked for through the registry", log.contains("attach"));
        assertSame("in the drawable the bar already wears", worn, bar.getBackground());
    }

    @Test
    public void aTwinWearsTheStoredFrameWithoutTakingTheEntry() {
        View v1Dock = attached();
        glass.register(new GlassEntry(TerlGlassWear.BAR_ELEMENT, v1Dock, GlassKind.LIVE,
            GlassEntry.PRIORITY_DOCK, WindowLayer.CHROME, null), null);
        View bar = attached();
        Counting builder = new Counting();
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        assertSame("the holder keeps it", v1Dock, glass.entry(TerlGlassWear.BAR_ELEMENT).view);
        assertEquals(1, builder.builds);
        assertEquals(0, builder.liveBuilds);
    }

    @Test
    public void aStoredGlassIsDressedAgainOnlyWhenItsFrameMoves() {
        // Pong 11-28: dressed while the cache was empty, the bar kept a tint alone, content sharp
        // behind it, until a look change; the frame landing never reached it.
        View bar = attached();
        Counting builder = new Counting();
        builder.frame = null;
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        wear.refreshStored();
        assertEquals("nothing landed: nothing is built", 1, builder.builds);
        Object landed = new Object();
        builder.frame = landed;
        wear.refreshStored();
        assertEquals("the frame landed: dressed with it", 2, builder.builds);
        assertTrue(wear.holds(landed));
        wear.refreshStored();
        assertEquals(2, builder.builds);
        wear.forgetFrames();
        assertFalse("a cleared cache's frame is not held", wear.holds(landed));
    }

    @Test
    public void elementsNeverCollideAcrossSheetsAndTheBarIsTheDock() {
        assertEquals("dock", TerlGlassWear.element(true, "CONTROL_BAR", 7));
        assertFalse(TerlGlassWear.element(false, "SHEETS", 7)
            .equals(TerlGlassWear.element(false, "SHEETS", 8)));
        assertFalse(TerlGlassWear.element(false, "CONTROL_BAR", 7).equals("dock"));
    }

    @Test
    public void restoresOnlyAStoredGlassWhoseFrameMoved() {
        Object a = new Object();
        assertFalse(TerlGlassWear.restores(false, a, a));
        assertTrue(TerlGlassWear.restores(false, a, new Object()));
        assertTrue(TerlGlassWear.restores(false, null, a));
        assertFalse("Live draws the recorder's layers", TerlGlassWear.restores(true, null, a));
        assertNull("an opaque glass asks for nothing", TerlGlassWear.wants(Build.VERSION_CODES.S, false));
        assertEquals(GlassKind.LIVE, TerlGlassWear.wants(Build.VERSION_CODES.S, true));
        assertNull("below Android 12 the stored frame", TerlGlassWear.wants(Build.VERSION_CODES.R, true));
    }
}
