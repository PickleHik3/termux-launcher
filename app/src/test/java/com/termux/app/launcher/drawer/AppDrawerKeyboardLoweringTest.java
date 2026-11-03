package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The close of a drawer whose search raised the built-in keyboard: the dock comes down to its
 * keyboard-down rest with the plane and the keyboard slides out, instead of both holding at the
 * raised layout until the close finishes and the stack is laid out again.
 *
 * <p>Reproduction (pong, Floating, Home, before the fix): on a slow drag-close the dock stayed at
 * y ~ 1530 with the keyboard's band under it through the whole close, then dropped to its rest at
 * y ~ 2180 in one frame when the close completed. Before the fix the dock's translation here was
 * only the lift, so its top ended the close at 1530, and the keyboard kept its raised place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerKeyboardLoweringTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2412;
    /** The dock above the raised keyboard, where the stack's layout puts it during the drawer. */
    private static final Frame RAISED_DOCK = new Frame(24f, 1530f, WIDTH - 24f, 1680f);
    /** The dock at the pull, keyboard down: where it rests once the close has finished. */
    private static final Frame REST_DOCK = new Frame(24f, 2180f, WIDTH - 24f, 2330f);
    private static final float KEYBOARD_TOP = 1600f;
    private static final float KEYBOARD_HEIGHT = 730f;
    private static final float LIFT_PX = 21f;
    private static final float[] CLOSE = {1f, 0.9f, 0.7f, 0.5f, 0.3f, 0.2f, 0.12f, 0.1f, 0.08f,
        0.05f, 0.02f, 0f};

    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private AppDrawerPlaneView plane;
    private View dock;
    private View keyboard;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        FrameLayout root = new FrameLayout(activity);
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
        host = new FakeAppDrawerHost(activity, null);
        host.viewRoot = root;
        controller = new AppDrawerController(host);
        plane = new AppDrawerPlaneView(activity);
        dock = new View(activity);
        keyboard = new View(activity);
        ReflectionHelpers.setField(controller, "mPlane", plane);
        ReflectionHelpers.setField(controller, "mHostLayout", root);
        // The stack was laid out for the raised keyboard and the plane took that rect at p = 1.
        ReflectionHelpers.setField(controller, "mDockRect", RAISED_DOCK);
        ReflectionHelpers.setField(controller, "mPullDockRect", REST_DOCK);
        ReflectionHelpers.setField(controller, "mDockLiftGroup", new View[] {dock});
        ReflectionHelpers.setField(controller, "mKeyboardView", keyboard);
        ReflectionHelpers.setField(controller, "mHasBands", true);
        ReflectionHelpers.setField(controller, "mExtraKeysBand",
            new AppDrawerAccessoryChoreography.Band(KEYBOARD_TOP, 0f));
        ReflectionHelpers.setField(controller, "mKeyboardBand",
            new AppDrawerAccessoryChoreography.Band(KEYBOARD_TOP, KEYBOARD_HEIGHT));
        ReflectionHelpers.setField(controller, "mCapturedGapPx", 0f);
        ReflectionHelpers.setField(controller, "mOpenRadiusPx", 40f);
        ReflectionHelpers.setField(controller, "mSeedRadiusPx", 40f);
        ReflectionHelpers.setField(controller, "mLiftPx", LIFT_PX);
        // A drag-close: the finger is down, the drawer still counts as open, the keyboard is up.
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mDragging", true);
        reveal(1f);
    }

    @Test public void floatingDockComesDownWithTheCloseAndTheKeyboardLeaves() {
        floating();
        host.closeLowersKeyboard = true;
        startClose();
        assertDockAndKeyboardLeaveTogether();
    }

    @Test public void dockedDockComesDownWithTheCloseAndTheKeyboardLeaves() {
        docked();
        host.closeLowersKeyboard = true;
        startClose();
        assertDockAndKeyboardLeaveTogether();
    }

    /** A Back or fling close: the drawer is no longer open and the reveal is on its way down. */
    @Test public void aSettlingCloseLandsTheDockAtRestToo() {
        floating();
        host.closeLowersKeyboard = true;
        ReflectionHelpers.setField(controller, "mDragging", false);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        controller.close(true);
        assertTrue((boolean) ReflectionHelpers.getField(controller, "mLowering"));
        assertDockAndKeyboardLeaveTogether();
    }

    /** A keyboard that was up before the drawer, or one the user took over, stays: so does the dock. */
    @Test public void aKeyboardThatStaysKeepsTheDockWhereItIs() {
        floating();
        host.closeLowersKeyboard = false;
        startClose();
        for (float p : CLOSE) applyFrame(p);
        assertEquals(RAISED_DOCK.top, dockTop(), 1e-3f);
        assertEquals(0f, keyboard.getTranslationY(), 1e-3f);
        assertEquals(1f, keyboard.getAlpha(), 1e-6f);
    }

    /** A drag-close given up and put back open leaves the keyboard up and stops lowering. */
    @Test public void aCloseThatComesBackOpenStopsLowering() {
        floating();
        host.closeLowersKeyboard = true;
        startClose();
        applyFrame(0.4f);
        ReflectionHelpers.setField(controller, "mDragging", false);
        applyFrame(1f);
        assertFalse((boolean) ReflectionHelpers.getField(controller, "mLowering"));
        assertEquals(0f, keyboard.getTranslationY(), 1e-3f);
        assertEquals(1f, keyboard.getAlpha(), 1e-6f);
    }

    @Test public void policyCurve() {
        assertEquals(0f, AppDrawerKeyboardLowering.loweredFraction(1f), 0f);
        assertEquals(1f, AppDrawerKeyboardLowering.loweredFraction(
            AppDrawerController.GLASS_FADE_END), 0f);
        assertEquals(1f, AppDrawerKeyboardLowering.loweredFraction(0f), 0f);
        assertEquals(650f, AppDrawerKeyboardLowering.offsetPx(1530f, 2180f, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardLowering.offsetPx(1530f, 2180f, 1f), 0f);
        assertEquals(812f, AppDrawerKeyboardLowering.keyboardExitPx(1600f, 2412f, 0.05f), 0f);
        assertEquals(0f, AppDrawerKeyboardLowering.reveal(1f, 0.05f), 0f);
        assertEquals(1f, AppDrawerKeyboardLowering.reveal(1f, 1f), 0f);
    }

    // ------------------------------------------------------------------ helpers

    private void assertDockAndKeyboardLeaveTogether() {
        float previousDockTop = -Float.MAX_VALUE;
        for (float p : CLOSE) {
            applyFrame(p);
            float top = dockTop();
            // Only the lift's small hop may raise it, and only near the top of the close.
            float allowance = p > 0.5f ? LIFT_PX : 0f;
            assertTrue("p=" + p + " dock " + top + " after " + previousDockTop,
                top >= previousDockTop - allowance - 1e-3f);
            previousDockTop = top;
            if (p < AppDrawerController.GLASS_FADE_END) {
                // Not left at its raised place under a faded plane: gone past the bottom edge.
                assertTrue("p=" + p, KEYBOARD_TOP + keyboard.getTranslationY() >= HEIGHT);
                assertEquals("p=" + p, 0f, keyboard.getAlpha(), 1e-6f);
            }
        }
        // At rest, exactly where the keyboard-down layout puts the dock: nothing left to jump.
        assertEquals(REST_DOCK.top, dockTop(), 1e-3f);
        Frame frame = plane.getFrame();
        assertEquals(REST_DOCK.top, frame.top, 1e-3f);
        assertEquals(REST_DOCK.bottom, frame.bottom, 1e-3f);
    }

    private float dockTop() {
        return RAISED_DOCK.top + dock.getTranslationY();
    }

    private void startClose() {
        ReflectionHelpers.callInstanceMethod(controller, "beginLoweringIfKeyboardGoes");
    }

    private void docked() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        ReflectionHelpers.setField(controller, "mOpenRect",
            new Frame(0f, -40f, WIDTH, HEIGHT + 40f));
    }

    private void floating() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", true);
        ReflectionHelpers.setField(controller, "mOpenRect",
            new Frame(24f, 125f, WIDTH - 24f, HEIGHT - 62f));
    }

    private void reveal(float k) {
        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        ReflectionHelpers.setField(spring, "value", k);
        ReflectionHelpers.setField(spring, "target", k);
    }

    private void applyFrame(float p) {
        ReflectionHelpers.callInstanceMethod(controller, "applyFrame",
            ReflectionHelpers.ClassParameter.from(float.class, p));
    }
}
