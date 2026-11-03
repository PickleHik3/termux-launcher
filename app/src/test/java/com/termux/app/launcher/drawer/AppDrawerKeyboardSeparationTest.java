package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test public void midAnimationFramesRequestNoLayout() {
        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        root.addView(plane, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        // Floating with a measured card runs the scrim: its swaps must not request a layout either.
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(24f, KEYBOARD_TOP + 8f, WIDTH - 24f, KEYBOARD_TOP + 640f));
        for (boolean rounded : new boolean[] {false, true}) {
            if (rounded) floating(); else docked();
            reveal(0f);
            applyFrame(0.3f);
            layOut(root);
            for (float p : new float[] {0.35f, 0.5f, 0.75f, 0.99f}) {
                reveal(p);
                applyFrame(p);
                assertFalse("p=" + p + " rounded=" + rounded, root.isLayoutRequested());
                assertFalse(plane.isLayoutRequested());
            }
        }
    }

    /**
     * The live blur's capture is a software draw of the decor in its pre-draw hook; on pong it
     * cost ~13 ms of every open and close frame. It must never be woken while the plane moves.
     */
    @Test public void liveBlurNeverCapturesWhileThePlaneMoves() {
        com.github.mmin18.widget.RealtimeBlurView blur =
            new com.github.mmin18.widget.RealtimeBlurView(plane.getContext(), null);
        ReflectionHelpers.setField(controller, "mLiveBlur", blur);
        Object progress = ReflectionHelpers.getField(controller, "mProgress");
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(progress, "target", 1f);
        // The open's one capture has been taken and the view rested itself.
        blur.setUpdatesPaused(true);

        for (float p : new float[] {0.03f, 0.26f, 0.5f, 0.9f, 0.998f}) {
            ReflectionHelpers.setField(progress, "value", p);
            controller.applyLiveBlurRest();
            assertTrue("open p=" + p, blur.isUpdatesPaused());
        }

        // Settled open: exactly one fresh capture, then rest.
        ReflectionHelpers.setField(progress, "value", 1f);
        controller.applyLiveBlurRest();
        assertFalse(blur.isUpdatesPaused());
        blur.setUpdatesPaused(true);
        controller.applyLiveBlurRest();
        assertTrue(blur.isUpdatesPaused());

        // Close: rests on the settled capture all the way down.
        ReflectionHelpers.setField(controller, "mOpen", false);
        ReflectionHelpers.setField(progress, "target", 0f);
        for (float p : new float[] {0.9f, 0.5f, 0.1f}) {
            ReflectionHelpers.setField(progress, "value", p);
            controller.applyLiveBlurRest();
            assertTrue("close p=" + p, blur.isUpdatesPaused());
        }
    }

    @Test public void floatingScrimRunsAroundTheKeyboardCard() {
        floating();
        host.paneGapPx = 11f;
        Frame card = new Frame(24f, KEYBOARD_TOP + 8f, WIDTH - 24f, KEYBOARD_TOP + 640f);
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame", card);
        reveal(1f);

        applyFrame(1f);

        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        // No clip at the keyboard's top: the dim covers the whole host ...
        assertNull(root.getClipBounds());
        assertTrue(root.getBackground() instanceof AppDrawerScrimDrawable);
        AppDrawerScrimDrawable scrim = (AppDrawerScrimDrawable) root.getBackground();
        assertEquals(com.termux.app.GlassBackdropTint.colorFor(1f), scrim.getColor());
        // ... but for the keyboard card, cut out where it stands.
        assertEquals(new android.graphics.RectF(card.left, card.top, card.right, card.bottom),
            scrim.getHole());
        // The card itself still ends above the keyboard.
        assertEquals(KEYBOARD_TOP - 11f, plane.getFrame().bottom, 1e-3f);
    }

    @Test public void dockedDimStillStopsAtTheKeyboardTop() {
        docked();
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(0f, KEYBOARD_TOP, WIDTH, HEIGHT));
        reveal(1f);

        applyFrame(1f);

        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        assertEquals(new android.graphics.Rect(0, 0, WIDTH, Math.round(KEYBOARD_TOP)),
            root.getClipBounds());
        assertFalse(root.getBackground() instanceof AppDrawerScrimDrawable);
    }

    @Test public void keyboardDownFloatingDimIsThePlainColour() {
        floating();
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(24f, KEYBOARD_TOP, WIDTH - 24f, HEIGHT - 80f));
        reveal(1f);
        applyFrame(1f);
        reveal(0f);

        applyFrame(1f);

        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        assertNull(root.getClipBounds());
        assertTrue(root.getBackground() instanceof android.graphics.drawable.ColorDrawable);
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(true, 0f, false, true));
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(false, 1f, false, true));
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(true, 1f, true, true));
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(true, 1f, false, false));
        assertTrue(AppDrawerController.scrimRunsAroundKeyboard(true, 0.2f, false, true));
    }

    @Test public void settledOpenIsOnlyAFullyOpenUndraggedPlane() {
        assertTrue(AppDrawerController.isSettledOpen(true, false, 1f, 1f));
        assertTrue(AppDrawerController.isSettledOpen(true, false, 1f, 0.9995f));
        assertFalse(AppDrawerController.isSettledOpen(true, false, 1f, 0.5f));
        assertFalse(AppDrawerController.isSettledOpen(true, true, 1f, 1f));
        assertFalse(AppDrawerController.isSettledOpen(false, false, 0f, 1f));
        assertFalse(AppDrawerController.isSettledOpen(true, false, 0f, 1f));
    }

    private static void layOut(FrameLayout root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test public void categoryCardsAreSizedForTheKeyboardDownCard() {
        floating();
        host.paneGapPx = 11f;
        reveal(1f);

        Frame shortened = ReflectionHelpers.callInstanceMethod(controller, "contentRect");
        float sized = controller.categoryMetricsHeightPx(shortened);

        assertEquals(KEYBOARD_TOP - 11f, shortened.bottom, 1e-3f);
        // The full card's height, not the shortened one: no card is squashed by the keyboard.
        assertEquals(HEIGHT - 62f - 125f, sized, 1e-3f);
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
