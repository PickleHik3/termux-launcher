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
 * The dock is one plane: the slab owns the only transform, the icon row inherits it, and every
 * spring settles once without overshoot.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockPlankIconLayerTest {

    /** Matches DockPlankController.MAX_TILT_DEG. */
    private static final float MAX_TILT_DEG = 3f;

    @Test public void iconsInsideTheSlabAreLeftEntirelyAlone() {
        Fixture f = fixture(true, true);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);

        assertTrue(f.plank.getRotationY() > 0.5f);
        assertEquals(0f, f.icons.getRotationY(), 0f);
        assertEquals(0f, f.icons.getRotationX(), 0f);
        assertEquals(0f, f.icons.getTranslationX(), 0f);
        assertEquals(1f, f.icons.getScaleX(), 0f);
    }

    @Test public void aDetachedIconRowGetsTheSlabTransformVerbatim() {
        Fixture f = fixture(true, false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);

        assertTrue(f.plank.getRotationY() > 0.5f);
        assertEquals(f.plank.getRotationY(), f.icons.getRotationY(), 0f);
        assertEquals(f.plank.getRotationX(), f.icons.getRotationX(), 0f);
        assertEquals(f.plank.getTranslationX(), f.icons.getTranslationX(), 0f);
        assertEquals(f.plank.getScaleX(), f.icons.getScaleX(), 0f);
    }

    @Test public void bothDockStylesTiltAndSlideAndNeitherExceedsTheTiltCap() {
        for (boolean hinge : new boolean[]{true, false}) {
            Fixture f = fixture(hinge, true);
            f.controller.onPointerDown(1f, 1f);
            run(f.controller, 60);

            assertTrue(f.plank.getRotationY() > 0.5f);
            assertTrue(f.plank.getRotationY() <= MAX_TILT_DEG + 0.001f);
            assertTrue(Math.abs(f.plank.getRotationX()) <= MAX_TILT_DEG + 0.001f);
            // The rotation is never mathematically isolated: it carries a small slide.
            assertTrue(f.plank.getTranslationX() > 0.5f);
            // The hinged bar keeps its bottom edge pinned and overscans to cover the slide.
            // Touch at the bottom-right corner: the capsule slides that way, the hinged bar does not
            // move vertically at all.
            assertEquals(hinge ? 0f : 1f, Math.signum(f.plank.getTranslationY()), 0f);
            assertTrue(hinge ? f.plank.getScaleX() > 1f : f.plank.getScaleX() < 1f);
        }
    }

    @Test public void nothingOvershootsItsTargetOnTheWayInOrOut() {
        Fixture f = fixture(true, true);
        f.controller.onPointerDown(1f, 0.5f);
        for (int i = 0; i < 120; i++) {
            f.controller.doFrame(16_666_666L * (i + 1));
            assertTrue("tilt overshot: " + f.plank.getRotationY(),
                f.plank.getRotationY() <= MAX_TILT_DEG + 0.001f);
        }
        float peakShift = f.plank.getTranslationX();

        f.controller.onPointerUp();
        for (int i = 120; i < 400; i++) {
            f.controller.doFrame(16_666_666L * (i + 1));
            // A critically damped return never crosses neutral on the way back.
            assertTrue("tilt rang: " + f.plank.getRotationY(), f.plank.getRotationY() >= -0.001f);
            assertTrue("slide rang: " + f.plank.getTranslationX(),
                f.plank.getTranslationX() >= -0.001f && f.plank.getTranslationX() <= peakShift + 0.001f);
        }
        assertEquals(0f, f.plank.getRotationY(), 0.02f);
        assertEquals(1f, f.plank.getScaleX(), 0.002f);
    }

    @Test public void droppingTheLayerLeavesItNeutral() {
        Fixture f = fixture(true, false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);
        assertTrue(f.icons.getRotationY() != 0f);

        f.controller.setIconLayer(null);
        assertEquals(0f, f.icons.getRotationY(), 0f);
        assertEquals(0f, f.icons.getRotationX(), 0f);
        assertEquals(0f, f.icons.getTranslationX(), 0f);
        assertEquals(1f, f.icons.getScaleX(), 0f);
    }

    private static void run(DockPlankController controller, int frames) {
        long nanos = 0L;
        for (int i = 0; i < frames; i++) {
            nanos += 16_666_666L;
            controller.doFrame(nanos);
        }
    }

    private static Fixture fixture(boolean hinge, boolean iconsInsidePlank) {
        Application context = RuntimeEnvironment.getApplication();
        FrameLayout plank = new FrameLayout(context);
        FrameLayout icons = new FrameLayout(context);
        if (iconsInsidePlank) {
            plank.addView(icons);
        }
        plank.layout(0, 0, 1080, 160);
        icons.layout(0, 0, 1080, 160);
        DockPlankController controller = new DockPlankController(plank, null, null);
        controller.setMotionEnabled(true);
        controller.setHingeMode(hinge);
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
