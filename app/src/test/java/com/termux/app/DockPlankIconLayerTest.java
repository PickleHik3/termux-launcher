package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The dock's icon row is driven by the same springs as the glass under it, including in the
 * edge-to-edge style where the slab itself is deliberately held flat.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockPlankIconLayerTest {

    @Test public void flatSlabStillTiltsAndShiftsTheIconsTowardTheFinger() {
        Fixture f = fixture(false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 20);

        assertEquals(0f, f.plank.getRotationY(), 0.001f);
        assertTrue(f.icons.getRotationY() > 0.5f);
        assertTrue(f.icons.getTranslationX() > 0.5f);

        f.controller.onPointerUp();
        run(f.controller, 400);
        assertEquals(0f, f.icons.getRotationY(), 0.05f);
        assertEquals(0f, f.icons.getTranslationX(), 0.5f);
    }

    @Test public void tiltingSlabCarriesTheIconsWithIt() {
        Fixture f = fixture(true);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 20);

        assertTrue(f.plank.getRotationY() > 0.5f);
        // The icons take a share of the slab's own tilt on top of it, never the opposite sign.
        assertTrue(f.icons.getRotationY() > 0f);
        assertTrue(f.icons.getRotationY() < f.plank.getRotationY());
    }

    @Test public void droppingTheLayerLeavesItNeutral() {
        Fixture f = fixture(false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 20);
        assertTrue(f.icons.getRotationY() != 0f);

        f.controller.setIconLayer(null);
        assertEquals(0f, f.icons.getRotationY(), 0f);
        assertEquals(0f, f.icons.getRotationX(), 0f);
        assertEquals(0f, f.icons.getTranslationX(), 0f);
        assertEquals(0f, f.icons.getTranslationY(), 0f);
    }

    private static void run(DockPlankController controller, int frames) {
        long nanos = 0L;
        for (int i = 0; i < frames; i++) {
            nanos += 16_666_666L;
            controller.doFrame(nanos);
        }
    }

    private static Fixture fixture(boolean motionEnabled) {
        Application context = RuntimeEnvironment.getApplication();
        View plank = new View(context);
        plank.layout(0, 0, 1080, 160);
        FrameLayout icons = new FrameLayout(context);
        icons.layout(0, 0, 1080, 160);
        DockPlankController controller = new DockPlankController(plank, null, null);
        controller.setMotionEnabled(motionEnabled);
        controller.setHingeMode(!motionEnabled);
        controller.setIconLayer(icons);
        controller.setEnabled(true);
        return new Fixture(controller, plank, icons);
    }

    private static final class Fixture {
        final DockPlankController controller;
        final View plank;
        final View icons;
        Fixture(DockPlankController controller, View plank, View icons) {
            this.controller = controller;
            this.plank = plank;
            this.icons = icons;
        }
    }
}
