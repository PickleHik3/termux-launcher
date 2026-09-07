package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The dock is one plane: the slab owns the only transform, every row standing on it either inherits
 * that transform or is handed exactly the same answer, and every spring settles once without
 * overshoot.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockPlankContentLayersTest {

    /** Matches DockPlankController.MAX_TILT_DEG. */
    private static final float MAX_TILT_DEG = 3f;

    @Test public void rowsInsideTheSlabAreLeftEntirelyAlone() {
        Fixture f = fixture(true, true);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);

        assertTrue(f.plank.getRotationY() > 0.5f);
        for (View row : f.rows) {
            assertEquals(0f, row.getRotationY(), 0f);
            assertEquals(0f, row.getRotationX(), 0f);
            assertEquals(0f, row.getTranslationX(), 0f);
            assertEquals(1f, row.getScaleX(), 0f);
        }
    }

    /** Both dock styles: the hinged edge-to-edge bar and the floating capsule with its press dip. */
    @Test public void everyDetachedRowGetsTheSlabTransformVerbatim() {
        for (boolean hinge : new boolean[]{true, false}) {
            Fixture f = fixture(hinge, false);
            f.controller.onPointerDown(0.95f, 0.5f);
            run(f.controller, 30);

            assertTrue(f.plank.getRotationY() > 0.5f);
            for (View row : f.rows) {
                assertEquals(f.plank.getRotationY(), row.getRotationY(), 0f);
                assertEquals(f.plank.getRotationX(), row.getRotationX(), 0f);
                assertEquals(f.plank.getTranslationX(), row.getTranslationX(), 0f);
                assertEquals(f.plank.getTranslationY(), row.getTranslationY(), 0f);
                assertEquals(f.plank.getScaleX(), row.getScaleX(), 0f);
                assertEquals(f.plank.getScaleY(), row.getScaleY(), 0f);
            }
        }
    }

    /**
     * One plane means one pivot line, not one pivot number: a row sitting higher up the stack must
     * rotate about the slab's own hinge, expressed in the row's coordinates. Rotating each row
     * about its own edge instead shears the rows against the glass they stand on.
     */
    @Test public void theSlabsPivotLineIsMappedIntoEachRowsOwnCoordinates() {
        for (boolean hinge : new boolean[]{true, false}) {
            Fixture f = fixture(hinge, false);
            f.controller.onPointerDown(0.95f, 0.5f);
            run(f.controller, 30);

            for (int i = 0; i < f.rows.length; i++) {
                float offsetY = f.rows[i].getTop() - f.plank.getTop();
                assertEquals("row " + i, f.plank.getPivotY() - offsetY,
                    f.rows[i].getPivotY(), 0.001f);
                assertEquals("row " + i, f.plank.getPivotX(), f.rows[i].getPivotX(), 0.001f);
            }
        }
    }

    /** A hidden row costs a visibility read and nothing else, and its neighbours still ride. */
    @Test public void aHiddenRowIsSkippedAndLeftNeutral() {
        Fixture f = fixture(true, false);
        f.rows[1].setVisibility(View.GONE);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);

        assertEquals(0f, f.rows[1].getRotationY(), 0f);
        assertEquals(1f, f.rows[1].getScaleX(), 0f);
        assertEquals(f.plank.getRotationY(), f.rows[0].getRotationY(), 0f);
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

    @Test public void droppingTheLayersLeavesThemNeutral() {
        Fixture f = fixture(true, false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);
        assertTrue(f.rows[0].getRotationY() != 0f);

        f.controller.setContentLayers();
        for (View row : f.rows) {
            assertEquals(0f, row.getRotationY(), 0f);
            assertEquals(0f, row.getRotationX(), 0f);
            assertEquals(0f, row.getTranslationX(), 0f);
            assertEquals(1f, row.getScaleX(), 0f);
        }
    }

    /**
     * The app drawer's plane lifts and fades the very same rows: the plank hands them back the
     * instant it stops being the one pushing them, so the two never write the same property.
     */
    @Test public void handingTheRowsBackLeavesThePlankAndEveryRowNeutral() {
        Fixture f = fixture(true, false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);
        assertTrue(f.rows[0].getRotationY() != 0f);

        f.controller.releaseToNeutral();

        assertEquals(0f, f.plank.getRotationY(), 0f);
        assertEquals(0f, f.plank.getTranslationX(), 0f);
        for (View row : f.rows) {
            assertEquals(0f, row.getRotationY(), 0f);
            assertEquals(0f, row.getRotationX(), 0f);
            assertEquals(0f, row.getTranslationX(), 0f);
            assertEquals(0f, row.getTranslationY(), 0f);
            assertEquals(1f, row.getScaleX(), 0f);
        }
    }

    /**
     * Handed over, the plank is not a writer at all: the drawer's lift on the same rows survives a
     * chrome re-apply, and the next finger on the dock takes ownership straight back.
     */
    @Test public void aHandedOverPlankWritesNothingUntilTheNextTouch() {
        Fixture f = fixture(true, false);
        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);
        f.controller.releaseToNeutral();

        f.plank.setTranslationY(-24f);
        f.rows[0].setTranslationY(-24f);
        f.controller.setEnabled(true);
        run(f.controller, 5);

        assertEquals(-24f, f.plank.getTranslationY(), 0f);
        assertEquals(-24f, f.rows[0].getTranslationY(), 0f);

        f.controller.onPointerDown(0.95f, 0.5f);
        run(f.controller, 30);
        assertTrue(f.rows[0].getRotationY() > 0.5f);
    }

    private static void run(DockPlankController controller, int frames) {
        long nanos = 0L;
        for (int i = 0; i < frames; i++) {
            nanos += 16_666_666L;
            controller.doFrame(nanos);
        }
    }

    /**
     * The dock's shape: a stack holding the glass plank and, on it, the two content rows — the
     * pinned-apps layer and the letters below it. {@code rowsInsidePlank} is the keyboard-down
     * state, where the plank is the whole stack and the rows are its descendants.
     */
    private static Fixture fixture(boolean hinge, boolean rowsInsidePlank) {
        Application context = RuntimeEnvironment.getApplication();
        RelativeLayout stack = new RelativeLayout(context);
        FrameLayout plank = new FrameLayout(context);
        FrameLayout apps = new FrameLayout(context);
        FrameLayout letters = new FrameLayout(context);
        stack.addView(plank);
        if (rowsInsidePlank) {
            plank.addView(apps);
            plank.addView(letters);
        } else {
            stack.addView(apps);
            stack.addView(letters);
        }
        stack.layout(0, 0, 1080, 160);
        plank.layout(0, 0, 1080, 160);
        // Two rows stacked on the plank: icons at the top of it, letters below them.
        apps.layout(0, 0, 1080, 100);
        letters.layout(0, 100, 1080, 160);
        DockPlankController controller = new DockPlankController(plank, null, null);
        controller.setMotionEnabled(true);
        controller.setHingeMode(hinge);
        controller.setContentLayers(apps, letters);
        controller.setEnabled(true);
        return new Fixture(controller, plank, new View[]{apps, letters});
    }

    private static final class Fixture {
        final DockPlankController controller;
        final View plank;
        final View[] rows;
        Fixture(DockPlankController controller, View plank, View[] rows) {
            this.controller = controller;
            this.plank = plank;
            this.rows = rows;
        }
    }
}
