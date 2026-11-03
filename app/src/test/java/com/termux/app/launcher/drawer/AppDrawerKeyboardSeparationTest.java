package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

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
 * The open drawer over the built-in keyboard its search raised: the plane ends one pane gap above
 * the keyboard, and the strip under the pill is the keyboard's glass again rather than faded out
 * with the drawer.
 *
 * <p>Reproduction (before the fix): with the plane fully open and the keyboard fully revealed,
 * {@code applyFrame} handed the strip {@code 1 - p = 0}; the host's clip ends at the keyboard top,
 * so nothing of the drawer covers the band under the pill and the wallpaper showed bare there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerKeyboardSeparationTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2412;
    private static final float KEYBOARD_TOP = 1730f;

    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private AppDrawerPlaneView plane;

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
        ReflectionHelpers.setField(controller, "mPlane", plane);
        ReflectionHelpers.setField(controller, "mHostLayout", root);
        ReflectionHelpers.setField(controller, "mDockRect", new Frame(0f, 1560f, WIDTH, 1720f));
        ReflectionHelpers.setField(controller, "mHasBands", true);
        ReflectionHelpers.setField(controller, "mExtraKeysBand",
            new AppDrawerAccessoryChoreography.Band(KEYBOARD_TOP, 0f));
        ReflectionHelpers.setField(controller, "mKeyboardBand",
            new AppDrawerAccessoryChoreography.Band(KEYBOARD_TOP, 620f));
        ReflectionHelpers.setField(controller, "mCapturedGapPx", 0f);
        ReflectionHelpers.setField(controller, "mOpenRadiusPx", 40f);
        ReflectionHelpers.setField(controller, "mSeedRadiusPx", 40f);
    }

    // ------------------------------------------------------------------ the strip under the pill

    @Test public void dockedStripIsTheKeyboardsGlassWithTheKeyboardUp() {
        docked();
        reveal(1f);

        applyFrame(1f);

        assertEquals(1f, host.decorNavStripAlpha, 0f);
    }

    @Test public void dockedStripStillFadesUnderThePlaneWithTheKeyboardDown() {
        docked();
        reveal(0f);

        applyFrame(1f);
        assertEquals(0f, host.decorNavStripAlpha, 0f);
        applyFrame(0.25f);
        assertEquals(0.75f, host.decorNavStripAlpha, 1e-6f);
    }

    @Test public void stripAlphaFollowsTheReveal() {
        assertEquals(1f, AppDrawerKeyboardSeparation.navStripAlpha(0f, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.navStripAlpha(1f, 0f), 0f);
        assertEquals(0.5f, AppDrawerKeyboardSeparation.navStripAlpha(1f, 0.5f), 1e-6f);
        assertEquals(1f, AppDrawerKeyboardSeparation.navStripAlpha(1f, 1f), 0f);
        assertEquals(0.6f, AppDrawerKeyboardSeparation.navStripAlpha(0.5f, 0.2f), 1e-6f);
    }

    // ------------------------------------------------------------------ the plane's bottom

    @Test public void dockedPlaneEndsOnePaneGapAboveTheKeyboardWithPaneCorners() {
        docked();
        host.paneGapPx = 26f;
        host.paneCornerRadiusPx = 55f;
        reveal(1f);

        applyFrame(1f);

        Frame frame = plane.getFrame();
        assertEquals(KEYBOARD_TOP - 26f, frame.bottom, 1e-3f);
        assertEquals(55f, (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
        // Still off the bezel at the top, by the larger radius, so no top corner shows.
        assertEquals(-55f, frame.top, 1e-3f);
    }

    @Test public void floatingPanelEndsOnePaneGapAboveTheKeyboardCardWithItsOwnCorners() {
        floating();
        host.paneGapPx = 26f;
        host.paneCornerRadiusPx = 55f;
        reveal(1f);

        applyFrame(1f);

        Frame frame = plane.getFrame();
        assertEquals(KEYBOARD_TOP - 26f, frame.bottom, 1e-3f);
        assertEquals(125f, frame.top, 1e-3f);
        assertEquals(40f, (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
    }

    @Test public void contentEndsWhereThePlaneDoes() {
        floating();
        host.paneGapPx = 26f;
        reveal(1f);

        Frame content = ReflectionHelpers.callInstanceMethod(controller, "contentRect");

        assertEquals(KEYBOARD_TOP - 26f, content.bottom, 1e-3f);
    }

    @Test public void keyboardDownLeavesThePlaneAsItWas() {
        docked();
        host.paneGapPx = 26f;
        host.paneCornerRadiusPx = 55f;
        reveal(0f);

        applyFrame(1f);

        Frame frame = plane.getFrame();
        assertEquals(HEIGHT + 40f, frame.bottom, 1e-3f);
        assertEquals(-40f, frame.top, 1e-3f);
        assertEquals(40f, (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
    }

    @Test public void bottomInsetMaths() {
        // Docked and Floating alike: keyboard top less the pane gap.
        assertEquals(1704f, AppDrawerKeyboardSeparation.revealedPlaneBottomPx(1730f, 26f, 11f), 0f);
        // No pane gap from the host: the gap measured at the pull.
        assertEquals(1719f, AppDrawerKeyboardSeparation.revealedPlaneBottomPx(1730f, -1f, 11f), 0f);
        // Docked moves to the pane's corners with the reveal; Floating keeps its own.
        assertEquals(40f, AppDrawerKeyboardSeparation.revealRadiusPx(false, 40f, 60f, 0f), 0f);
        assertEquals(50f, AppDrawerKeyboardSeparation.revealRadiusPx(false, 40f, 60f, 0.5f), 1e-6f);
        assertEquals(60f, AppDrawerKeyboardSeparation.revealRadiusPx(false, 40f, 60f, 1f), 0f);
        assertEquals(40f, AppDrawerKeyboardSeparation.revealRadiusPx(true, 40f, 60f, 1f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.revealRadiusPx(false, 40f, 0f, 1f), 0f);
        // The bezel push follows a larger radius; an unpushed top is left alone.
        assertEquals(-60f, AppDrawerKeyboardSeparation.revealPlaneTopPx(false, -40f, 60f), 0f);
        assertEquals(-40f, AppDrawerKeyboardSeparation.revealPlaneTopPx(false, -40f, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.revealPlaneTopPx(false, 0f, 60f), 0f);
        assertEquals(125f, AppDrawerKeyboardSeparation.revealPlaneTopPx(true, 125f, 60f), 0f);
    }

    // ------------------------------------------------------------------ helpers

    /** Docked: the open rect runs one radius past both bezels. */
    private void docked() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        ReflectionHelpers.setField(controller, "mOpenRect",
            new Frame(0f, -40f, WIDTH, HEIGHT + 40f));
    }

    /** Floating: the panel inside the system bars. */
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
