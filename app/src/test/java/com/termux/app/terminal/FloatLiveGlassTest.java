package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.widget.FrameLayout;

import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.LiveBackdropFrameLayout;
import com.termux.app.chrome.LivePicture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.app.Application;

/**
 * A floating window's glass takes Live blur's picture only while the float can be seen and the
 * slab is its own and shown; the slab holds the picture while it shows it and lets it go after.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.TIRAMISU}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FloatLiveGlassTest {

    @Test
    public void wantsOnlyAShownSlabInsideAShownFloat() {
        for (int bits = 0; bits < 8; bits++) {
            boolean shown = (bits & 1) != 0;
            boolean inFloat = (bits & 2) != 0;
            boolean slab = (bits & 4) != 0;
            assertEquals("bits " + bits, bits == 7, FloatLiveGlass.wants(shown, inFloat, slab));
        }
    }

    @Test
    public void insideFollowsTheParentsAndForgetsADockedBackPane() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout floatContainer = new FrameLayout(activity);
        FrameLayout content = new LiveBackdropFrameLayout(activity);
        FrameLayout tiled = new FrameLayout(activity);
        PaneGlassBackdropView slab = new PaneGlassBackdropView(activity);
        floatContainer.addView(content);
        content.addView(slab);
        assertTrue(FloatLiveGlass.inside(slab, floatContainer));
        assertFalse(FloatLiveGlass.inside(slab, null));
        // Docked back: the pane now lives in the tiled tree.
        content.removeView(slab);
        tiled.addView(slab);
        assertFalse(FloatLiveGlass.inside(slab, floatContainer));
    }

    @Test
    public void theSlabHoldsThePictureWhileItShowsItAndFallsBackOnceReleased() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new LiveBackdropFrameLayout(activity);
        PaneGlassBackdropView slab = new PaneGlassBackdropView(activity);
        root.addView(slab);
        activity.setContentView(root);
        LiveBackdrop recorder = new LiveBackdrop(root);
        LivePicture picture = recorder.attach(slab, 20f);
        assertNotNull(picture);

        slab.setLivePicture(picture);
        assertSame(slab, picture.drawable().getCallback());
        assertSame(picture, slab.livePicture());

        recorder.releaseAll();
        assertTrue(picture.released());
        assertFalse("a released picture draws the stored frame", slab.drawsLive());

        slab.setLivePicture(null);
        assertNull(picture.drawable().getCallback());
        assertNull(slab.livePicture());
    }

    @Test
    public void noLookNoLiveRoute() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new LiveBackdropFrameLayout(activity);
        PaneGlassBackdropView slab = new PaneGlassBackdropView(activity);
        root.addView(slab);
        LivePicture picture = new LiveBackdrop(root).attach(slab, 20f);
        slab.setLivePicture(picture);
        // Live feeds the refraction: without a look the slab keeps the stored frame's route.
        assertFalse(slab.drawsLive());
    }

    @Test
    public void theRecordingReachesUpOverTheStripOnly() {
        assertEquals(96, FloatLiveGlass.reachAbove(96, 0));
        assertEquals(0, FloatLiveGlass.reachAbove(0, 0));
        assertEquals(0, FloatLiveGlass.reachAbove(0, 40));
    }

    @Test
    public void offsetInSumsTheParentsToTheFloat() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout floatContainer = new FrameLayout(activity);
        FrameLayout content = new FrameLayout(activity);
        PaneGlassBackdropView slab = new PaneGlassBackdropView(activity);
        floatContainer.addView(content);
        content.addView(slab);
        content.layout(0, 90, 500, 600);
        slab.layout(6, 4, 494, 500);
        int[] out = new int[2];
        assertTrue(FloatLiveGlass.offsetIn(slab, floatContainer, out));
        assertEquals(6, out[0]);
        assertEquals(94, out[1]);
        assertFalse(FloatLiveGlass.offsetIn(slab, new FrameLayout(activity), out));
    }

    @Test
    public void oneRecordingServesTheSlabAndTheStripWithOneSlot() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new LiveBackdropFrameLayout(activity);
        PaneGlassBackdropView slab = new PaneGlassBackdropView(activity);
        PaneGlassBackdropView strip = new PaneGlassBackdropView(activity);
        root.addView(slab);
        root.addView(strip);
        activity.setContentView(root);
        LiveBackdrop recorder = new LiveBackdrop(root);
        LivePicture picture = recorder.attach(slab, 20f);
        assertNotNull(picture);
        slab.setLivePicture(picture);
        strip.setLivePicture(picture, true);
        picture.setReachTop(90);
        picture.aimAt(0, -90);
        assertEquals("the strip takes no slot", 1, recorder.size());
        assertTrue(recorder.shows(picture.aimedDrawable()));
        // Either holder letting go leaves the other holding it.
        slab.setLivePicture(null);
        assertEquals(strip.livePicture(), picture);
        recorder.releaseAll();
        assertTrue(picture.released());
    }
}
