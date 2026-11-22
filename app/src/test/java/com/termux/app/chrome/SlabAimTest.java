package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.widget.FrameLayout;

import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pane slabs read their place from the glass service's commit instead of the activity's push
 * (ADR 0029 section 10): a slab whose page moved draws again in the frame the page moved
 * in, a slab that did not move is never asked to, and a frame with no slab moving draws nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SlabAimTest {

    /** A slab that counts the draws asked of it. */
    private static final class Slab extends android.view.View {
        int invalidations;

        Slab(android.content.Context context) { super(context); }

        @Override public void invalidate() {
            invalidations++;
            super.invalidate();
        }
    }

    private FramePipeline frames;
    private GlassService glass;
    private Activity activity;
    private FrameLayout root;
    private Slab slab;
    private SlabAim aim;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        slab = new Slab(activity);
        root.addView(slab, new FrameLayout.LayoutParams(300, 200));
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idle();
        frames = new FramePipeline();
        glass = new GlassService(frames);
        GlassService.install(activity.getWindow().getDecorView(), glass);
        aim = new SlabAim(slab);
    }

    private void frame() {
        frames.flush("test frame");
    }

    @Test
    public void aSlabRegistersAsWallpaperOnThePagesLayerAndLetsGoOnDetach() {
        aim.attach();
        String element = "slab@" + Integer.toHexString(System.identityHashCode(slab));
        assertEquals(GlassKind.WALLPAPER, glass.granted(element));
        assertEquals(WindowLayer.PAGES, glass.entry(element).layer);
        assertTrue("it asks to be visited, so the commit runs on every drawn frame",
            frames.isArmed(FrameStep.GLASS_COMMIT));
        assertTrue(aim.attached());
        aim.detach();
        assertEquals(0, glass.size());
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
    }

    @Test
    public void aSlabIsDrawnAgainOnlyOnTheFramesItsPlaceMoved() {
        aim.attach();
        frame();
        int first = slab.invalidations;
        assertTrue("the first frame aims it where it stands", first > 0);
        // Frames with nothing moving ask nothing of it.
        frame();
        frame();
        assertEquals(first, slab.invalidations);
        // A page sliding carries the slab: a motion its owner published, as the wall's slide.
        GlassAnchor.setMotion(slab, 40f, 0f);
        frame();
        assertEquals(first + 1, slab.invalidations);
        frame();
        assertEquals(first + 1, slab.invalidations);
    }

    @Test
    public void theWallpaperPanningDrawsEverySlabAgain() {
        WallpaperParallax parallax = new WallpaperParallax();
        aim.setParallax(parallax);
        aim.attach();
        frame();
        int first = slab.invalidations;
        frame();
        assertEquals(first, slab.invalidations);
        parallax.setOffsetPx(120f);
        frame();
        assertEquals(first + 1, slab.invalidations);
    }

    @Test
    public void aWindowWithoutAServiceRegistersNothing() {
        Slab other = new Slab(activity);
        SlabAim lonely = new SlabAim(other);
        // Not in any window: no decor tag to find, so no entry and no push owed.
        lonely.attach();
        assertFalse(lonely.attached());
    }
}
