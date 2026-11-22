package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    // ------------------------------------------------------------------ the plane's bottom

    @Test public void dockedPlaneIsACardMarginInFromTheEdgesAndAboveTheKeyboard() {
        docked();
        host.paneGapPx = 11f;
        host.paneCornerRadiusPx = 55f;
        host.marginPx = 21f;
        reveal(1f);

        applyFrame(1f);

        Frame frame = plane.getFrame();
        // Margin's air at both sides, the top and above the keyboard card: the wallpaper shows.
        assertEquals(21f, frame.left, 1e-3f);
        assertEquals(WIDTH - 21f, frame.right, 1e-3f);
        assertEquals(21f, frame.top, 1e-3f);
        assertEquals(KEYBOARD_TOP - 21f, frame.bottom, 1e-3f);
        // The pane's corners, all four showing.
        assertEquals(55f, (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
        // The content ends where the card does, so nothing is drawn behind the keyboard or in the
        // gap, and starts where it does.
        Frame content = ReflectionHelpers.callInstanceMethod(controller, "contentRect");
        assertEquals(frame.bottom, content.bottom, 1e-3f);
        assertEquals(frame.top, content.top, 1e-3f);
    }

    /**
     * Pull from Home and every close (pong, card3/open f018–f024, card3/midclose): while p moved,
     * Docked's plane lerped from the full-width dock to the card, so the drag and the close showed
     * a full-width sheet with square corners running to the screen's bottom, which snapped to the
     * card at the release. In every frame of the motion it is now the rest card's sides, corners
     * and bottom, its top rising from the dock's.
     */
    @Test public void dockedPullAndCloseFramesAreTheRestCard() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.marginPx = 21f;
        host.paneCornerRadiusPx = 55f;
        host.sheetsBandPx = 90;
        ReflectionHelpers.setField(controller, "mSeedRadiusPx", 0f);
        Frame rest = ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect");
        ReflectionHelpers.setField(controller, "mOpenRect", rest);
        reveal(0f);

        float prevTop = Float.MAX_VALUE;
        for (float p : new float[] {0.02f, 0.2f, 0.5f, 0.8f, 1f}) {
            applyFrame(p);
            Frame f = plane.getFrame();
            assertEquals("left at p " + p, rest.left, f.left, 1e-3f);
            assertEquals("right at p " + p, rest.right, f.right, 1e-3f);
            assertEquals("bottom at p " + p, rest.bottom, f.bottom, 1e-3f);
            assertTrue("top rises at p " + p, f.top <= prevTop && f.top >= rest.top);
            assertEquals("the card's corners at p " + p, 55f,
                (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
            prevTop = f.top;
        }
        assertEquals(rest.top, plane.getFrame().top, 1e-3f);
    }

    /**
     * One owner for the card: the content rect the keyboard decision lays the grid and the A–Z
     * strip out in is the rectangle the plane comes to rest on, keyboard down and keyboard up. Before
     * the fix the content was laid out for the bezel-to-bezel sheet (keyboard down) and at the
     * sheet's sides (keyboard up), wider than the card, so the card clipped the right column and the
     * strip.
     */
    @Test public void dockedPlaneAndContentRestOnTheSameCard() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.marginPx = 21f;
        host.paneCornerRadiusPx = 55f;
        host.sheetsBandPx = 90;
        ReflectionHelpers.setField(controller, "mOpenRect",
            ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect"));

        for (float k : new float[] {0f, 1f}) {
            reveal(k);
            applyFrame(1f);

            Frame frame = plane.getFrame();
            Frame content = ReflectionHelpers.callInstanceMethod(controller, "contentRect");
            assertEquals("left at k " + k, 21f, frame.left, 1e-3f);
            assertEquals("right at k " + k, WIDTH - 21f, frame.right, 1e-3f);
            assertEquals("top at k " + k, 21f, frame.top, 1e-3f);
            assertEquals("bottom at k " + k, k == 0f ? HEIGHT - 90f - 21f : KEYBOARD_TOP - 21f,
                frame.bottom, 1e-3f);
            assertEquals("content left at k " + k, frame.left, content.left, 1e-3f);
            assertEquals("content right at k " + k, frame.right, content.right, 1e-3f);
            assertEquals("content top at k " + k, frame.top, content.top, 1e-3f);
            assertEquals("content bottom at k " + k, frame.bottom, content.bottom, 1e-3f);
            assertEquals("the pane's corners at k " + k, 55f,
                (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
        }
    }

    /**
     * The ghost: the plane's Snapshot was posed with the keyboard up, and when the keyboard went
     * the card's bottom came down over a picture of it. The keyboard-gone retake poses the open
     * scene with the keyboard all the way down, then puts the real frame back.
     */
    @Test public void theKeyboardGoneRetakePosesTheCardAtItsKeyboardDownBottom() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.marginPx = 21f;
        ReflectionHelpers.setField(controller, "mOpenRect",
            ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect"));
        ReflectionHelpers.setField(controller, "mEngaged", true);
        ReflectionHelpers.setField(ReflectionHelpers.getField(controller, "mProgress"), "value", 1f);
        reveal(1f);
        applyFrame(1f);
        com.termux.app.chrome.PanelBackdrop.Scene scene =
            ReflectionHelpers.getField(controller, "mSettledScene");

        ReflectionHelpers.setField(controller, "mPosingKeyboardGone", true);
        scene.beforeSnapshot();
        assertEquals("posed keyboard down", HEIGHT - 21f, plane.getFrame().bottom, 1e-3f);
        assertTrue((Boolean) ReflectionHelpers.getField(controller, "mBackdropKeyboardGone"));
        scene.afterSnapshot();
        ReflectionHelpers.setField(controller, "mPosingKeyboardGone", false);
        assertEquals("the real frame back", KEYBOARD_TOP - 21f, plane.getFrame().bottom, 1e-3f);

        // A Snapshot posed at the target with the keyboard up shows it again.
        scene.beforeSnapshot();
        scene.afterSnapshot();
        assertFalse((Boolean) ReflectionHelpers.getField(controller, "mBackdropKeyboardGone"));
    }

    @Test public void dockedCardMovesWithTheRevealWithNoStep() {
        docked();
        host.paneCornerRadiusPx = 55f;
        host.marginPx = 21f;
        Frame prev = null;
        float prevRadius = 40f;
        for (int i = 0; i <= 20; i++) {
            reveal(i / 20f);
            applyFrame(1f);
            Frame f = plane.getFrame();
            float r = ReflectionHelpers.getField(controller, "mCurrentRadiusPx");
            if (prev != null) {
                assertTrue("left " + i, f.left >= prev.left && f.left - prev.left <= 1.06f);
                assertTrue("right " + i, f.right <= prev.right && prev.right - f.right <= 1.06f);
                assertTrue("top " + i, f.top >= prev.top && f.top - prev.top <= 3.06f);
                assertTrue("radius " + i, Math.abs(r - prevRadius) <= 0.76f);
            }
            prev = f;
            prevRadius = r;
        }
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
     * The old live blur's capture cost ~13 ms of every open and close frame on pong. The plane's
     * picture is told it moves on every frame that is not settled open, so a Snapshot is never
     * retaken mid-motion, and that it rests exactly when the plane settles open.
     */
    @Test public void thePictureIsFrozenWhileThePlaneMoves() {
        com.termux.app.chrome.PanelBackdrop backdrop = new com.termux.app.chrome.PanelBackdrop(
            new android.widget.ImageView(plane.getContext()), plane, "drawer");
        ReflectionHelpers.setField(controller, "mBackdrop", backdrop);
        Object progress = ReflectionHelpers.getField(controller, "mProgress");
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(progress, "target", 1f);

        for (float p : new float[] {0.03f, 0.26f, 0.5f, 0.9f, 0.998f}) {
            ReflectionHelpers.setField(progress, "value", p);
            controller.applyBackdropRest();
            assertTrue("open p=" + p, (boolean) ReflectionHelpers.getField(backdrop, "mMoving"));
        }

        ReflectionHelpers.setField(progress, "value", 1f);
        controller.applyBackdropRest();
        assertFalse((boolean) ReflectionHelpers.getField(backdrop, "mMoving"));

        ReflectionHelpers.setField(controller, "mOpen", false);
        ReflectionHelpers.setField(progress, "target", 0f);
        for (float p : new float[] {0.9f, 0.5f, 0.1f}) {
            ReflectionHelpers.setField(progress, "value", p);
            controller.applyBackdropRest();
            assertTrue("close p=" + p, (boolean) ReflectionHelpers.getField(backdrop, "mMoving"));
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

    @Test public void dockedDimRunsRoundTheKeyboardCardsTopCorners() {
        docked();
        host.marginPx = 21f;
        host.keyboardCardRadiusPx = 30f;
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(0f, KEYBOARD_TOP, WIDTH, HEIGHT));
        reveal(1f);

        applyFrame(1f);

        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        // The dim covers the sides, the gap and the pockets beside the keyboard's top corners ...
        assertNull(root.getClipBounds());
        assertTrue(root.getBackground() instanceof AppDrawerScrimDrawable);
        AppDrawerScrimDrawable scrim = (AppDrawerScrimDrawable) root.getBackground();
        // ... with the keyboard cut out, run one radius past the screen so only its top is round.
        assertEquals(new android.graphics.RectF(0f, KEYBOARD_TOP, WIDTH, HEIGHT + 30f),
            scrim.getHole());
        assertEquals(30f, scrim.effectiveRadiusPx(), 1e-3f);
    }

    /** Docked under the system keyboard is the card too, ending its own gap above the keyboard. */
    @Test public void dockedUnderTheSystemKeyboardIsTheCardAboveIt() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.marginPx = 21f;
        host.paneCornerRadiusPx = 55f;
        ReflectionHelpers.setField(controller, "mOpenRect",
            ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect"));
        ReflectionHelpers.setField(controller, "mImePinTopPx", 1500f);
        reveal(1f);

        applyFrame(1f);

        Frame frame = plane.getFrame();
        assertEquals(21f, frame.left, 1e-3f);
        assertEquals(WIDTH - 21f, frame.right, 1e-3f);
        assertEquals(21f, frame.top, 1e-3f);
        assertTrue(frame.bottom < 1500f && frame.bottom > 1450f);
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
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(0f, false, true));
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(1f, true, true));
        assertFalse(AppDrawerController.scrimRunsAroundKeyboard(1f, false, false));
        assertTrue(AppDrawerController.scrimRunsAroundKeyboard(0.2f, false, true));
    }

    /**
     * The brightness jump before settle: the first capture was of the p ~ 0.03 scene, the settled
     * one of p = 1. Every capture is now taken of the settled scene, and the shown frame is put back.
     */
    @Test public void everyCaptureIsOfTheSettledSceneAndTheFrameIsPutBack() {
        floating();
        reveal(0f);
        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        ReflectionHelpers.setField(spring, "target", 1f);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        Object progress = ReflectionHelpers.getField(controller, "mProgress");
        ReflectionHelpers.setField(progress, "value", 0.03f);
        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        applyFrame(0.03f);
        Frame shown = plane.getFrame();
        float shownAlpha = root.getAlpha();

        com.termux.app.chrome.PanelBackdrop.Scene scene =
            ReflectionHelpers.getField(controller, "mSettledScene");
        scene.beforeSnapshot();
        // What the capture sees: the full dim, the host at full opacity, the plane fully open.
        assertEquals(com.termux.app.GlassBackdropTint.colorFor(1f),
            ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor());
        assertEquals(1f, root.getAlpha(), 0f);
        scene.afterSnapshot();

        assertEquals(shown, plane.getFrame());
        assertEquals(shownAlpha, root.getAlpha(), 0f);
        assertEquals(0f, (float) ReflectionHelpers.getField(spring, "value"), 0f);
        assertEquals(com.termux.app.GlassBackdropTint.colorFor(0.03f),
            ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor());
    }

    @Test public void dockedKeyboardUpAtThePullNeitherDipsNorFadesWhileTheRevealCatchesUp() {
        docked();
        host.marginPx = 21f;
        View keyboard = new View(plane.getContext());
        ReflectionHelpers.setField(controller, "mKeyboardView", keyboard);
        ReflectionHelpers.setField(controller, "mKeyboardUpAtPull", true);
        // The open is done and the reveal spring has not caught up yet: the plane alone waits.
        reveal(0f);

        applyFrame(1f);

        assertEquals(0f, keyboard.getTranslationY(), 0f);
        assertEquals(1f, keyboard.getAlpha(), 0f);
    }

    /**
     * The keyboard leaving under the open card. Round 1 on pong: its card faded with the reveal
     * (container alpha = k), so for a few frames its bare keys slid down with the glass thinning
     * out under them. Round 2: put away at once, which opened a gap the card then closed. Now it
     * slides off the bottom of the screen at full opacity, in its card look (the sheet skin and the
     * dim hole round it stay), and the card's bottom rides one gap above its top on the same frame.
     */
    @Test public void aKeyboardTheDrawerSentAwaySlidesDownInItsCardWithTheCardOnIt() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.marginPx = 21f;
        ReflectionHelpers.setField(controller, "mOpenRect",
            ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect"));
        View keyboard = new View(plane.getContext());
        ReflectionHelpers.setField(controller, "mKeyboardView", keyboard);
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(0f, KEYBOARD_TOP, WIDTH, HEIGHT));
        ReflectionHelpers.setField(controller, "mOpen", true);
        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        FrameLayout root = (FrameLayout) ReflectionHelpers.getField(controller, "mHostLayout");

        for (float k : new float[] {0.75f, 0.5f, 0.1f}) {
            ReflectionHelpers.setField(spring, "value", k);
            ReflectionHelpers.setField(spring, "target", 0f);
            applyFrame(1f);

            float slide = (HEIGHT - KEYBOARD_TOP) * (1f - k);
            assertEquals("its card look, never fading, at k " + k, 1f, keyboard.getAlpha(), 0f);
            assertEquals("off the bottom of the screen, at k " + k, slide,
                keyboard.getTranslationY(), 1e-3f);
            assertEquals("the card one gap above its top, at k " + k,
                Math.min(HEIGHT - 21f, KEYBOARD_TOP + slide - 21f), plane.getFrame().bottom, 1e-3f);
            assertTrue("the dim runs round its card, at k " + k,
                root.getBackground() instanceof AppDrawerScrimDrawable);
        }
    }

    @Test public void dockedKeyboardUpAtThePullKeepsTheRevealThroughTheClose() {
        docked();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mKeyboardUpAtPull", true);
        ReflectionHelpers.setField(controller, "mOpen", false);
        reveal(1f);

        ReflectionHelpers.callInstanceMethod(controller, "retargetReveal");

        // The close keeps the card above the keyboard: no full-bleed plane over it.
        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        assertEquals(1f, (float) ReflectionHelpers.getField(spring, "target"), 0f);
    }

    @Test public void dockedKeyboardUpAtThePullIsNotSentAwayByAListDrag() {
        docked();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(content, "mSearchRevealRequested", true);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mKeyboardUpAtPull", true);
        reveal(1f);

        controller.onListDragHidesKeyboard();

        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        assertEquals(1f, (float) ReflectionHelpers.getField(spring, "target"), 0f);
    }

    @Test public void floatingKeyboardUpAtThePullIsNotSentAwayByAListDrag() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(content, "mSearchRevealRequested", true);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mKeyboardUpAtPull", true);
        reveal(1f);

        controller.onListDragHidesKeyboard();

        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        assertEquals(1f, (float) ReflectionHelpers.getField(spring, "target"), 0f);
        assertFalse((boolean) ReflectionHelpers.getField(content, "mKeyboardDismissedByScroll"));
    }

    /**
     * Palette, "App drawer", Docked with the Terminal's keyboard up (pong, 2026-11-20): the plane
     * rose from the bottom over the extra keys and the top rows while the reveal caught up, and the
     * pockets beside the keyboard card's top corners stayed bare. From the first frame the plane is
     * a card above the keyboard card and the dim runs round the keyboard.
     */
    @Test public void dockedKeyboardUpAtThePullOpensAsACardAboveItFromTheFirstFrame() {
        docked();
        host.marginPx = 21f;
        host.keyboardCardRadiusPx = 30f;
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mKeyboardUpAtPull", true);
        ReflectionHelpers.setField(controller, "mKeyboardCardFrame",
            new Frame(0f, KEYBOARD_TOP, WIDTH, HEIGHT));
        // The engage: the reveal comes in at 0 from the last close.
        reveal(0f);
        ReflectionHelpers.callInstanceMethod(controller, "retargetReveal");
        ReflectionHelpers.callInstanceMethod(controller, "startRevealForOpen");

        FrameLayout root = ReflectionHelpers.getField(controller, "mHostLayout");
        for (int i = 0; i <= 20; i++) {
            float p = i / 20f;
            applyFrame(p);
            Frame frame = plane.getFrame();
            assertTrue("bottom at p " + p + ": " + frame.bottom,
                frame.bottom <= KEYBOARD_TOP - 21f + 1e-3f);
            assertTrue("top at p " + p, frame.top <= frame.bottom);
            assertNull("clip at p " + p, root.getClipBounds());
            assertTrue("dim at p " + p, root.getBackground() instanceof AppDrawerScrimDrawable);
            assertNotNull(((AppDrawerScrimDrawable) root.getBackground()).getHole());
        }
    }

    @Test public void revealAtOpenAndASeedBelowTheCard() {
        assertEquals(1f, AppDrawerKeyboardSeparation.revealAtOpen(true, 1f, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.revealAtOpen(false, 1f, 0f), 0f);
        assertEquals(0.4f, AppDrawerKeyboardSeparation.revealAtOpen(false, 1f, 0.4f), 0f);
        // A seed lower than the card's bottom is a card of no height there, never turned over.
        Frame f = AppDrawerKeyboardSeparation.dockedCardFrame(
            new Frame(0f, 1700f, WIDTH, 1760f), WIDTH, 21f, 120f, 1680f, 1f);
        assertEquals(1680f, f.top, 0f);
        assertEquals(1680f, f.bottom, 0f);
    }

    @Test public void keyboardStaysUpPolicy() {
        // Docked, built-in keyboard up at the pull: it stays.
        assertTrue(AppDrawerKeyboardSeparation.keyboardStaysUp(false, false, false, true));
        // Floating, up at the pull: it stays too; the panel rests above it.
        assertTrue(AppDrawerKeyboardSeparation.keyboardStaysUp(true, false, false, true));
        // Raised by the drawer's own search (down at the pull), either style, a system keyboard or
        // a text-field search: the reveal keeps it.
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(false, false, false, false));
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(true, false, false, false));
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(true, true, false, true));
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(false, true, false, true));
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(false, false, true, true));
        assertFalse(AppDrawerKeyboardSeparation.keyboardStaysUp(true, false, true, true));

        // Floating's panel above a keyboard that stays: one gap above its top, never below the
        // panel's own bottom, so the close's shrinking panel is not pulled down over the keys.
        assertEquals(700f, AppDrawerKeyboardSeparation.cardBottomOverKeyboardPx(900f, 724f, 24f), 0f);
        assertEquals(400f, AppDrawerKeyboardSeparation.cardBottomOverKeyboardPx(400f, 724f, 24f), 0f);

        assertEquals(1f, AppDrawerKeyboardSeparation.revealTarget(true, false, true, 0f), 0f);
        assertEquals(1f, AppDrawerKeyboardSeparation.revealTarget(true, true, true, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.revealTarget(false, true, true, 1f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.revealTarget(true, false, false, 1f), 0f);
        assertEquals(1f, AppDrawerKeyboardSeparation.revealTarget(true, true, false, 1f), 0f);

        assertEquals(1f, AppDrawerKeyboardSeparation.bandRevealFraction(true, 0.2f), 0f);
        assertEquals(0.2f, AppDrawerKeyboardSeparation.bandRevealFraction(false, 0.2f), 0f);

        // The keyboard's slide: none at reveal 1, its top at the screen's bottom at 0.
        assertEquals(0f, AppDrawerKeyboardSeparation.keyboardSlidePx(2400f, 1700f, 1f), 0f);
        assertEquals(350f, AppDrawerKeyboardSeparation.keyboardSlidePx(2400f, 1700f, 0.5f), 1e-3f);
        assertEquals(700f, AppDrawerKeyboardSeparation.keyboardSlidePx(2400f, 1700f, 0f), 0f);
        // The card over it: one gap above its live top, never below the rest bottom.
        assertEquals(2029f,
            AppDrawerKeyboardSeparation.cardBottomOverKeyboardPx(2290f, 2050f, 21f), 0f);
        assertEquals(2290f,
            AppDrawerKeyboardSeparation.cardBottomOverKeyboardPx(2290f, 2400f, 21f), 0f);
        // Parity at the ends with the content rect's owner: at reveal 1 the same bottom.
        assertEquals(AppDrawerKeyboardSeparation.cardBottomPx(2290f, 1700f, 21f, 1f),
            AppDrawerKeyboardSeparation.cardBottomOverKeyboardPx(2290f,
                1700f + AppDrawerKeyboardSeparation.keyboardSlidePx(2400f, 1700f, 1f), 21f), 0f);
    }

    @Test public void draggingTheListSendsTheBuiltInKeyboardAwayAndKeepsTheQuery() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(content, "mSearchRevealRequested", true);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        reveal(1f);
        assertEquals(1f, content.getRevealFraction(), 0f);

        controller.onListDragHidesKeyboard();

        // The reveal heads back to the keyboard-down card; the spring animates it there.
        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        assertEquals(0f, (float) ReflectionHelpers.getField(spring, "target"), 0f);
        assertEquals(0f, content.getRevealFraction(), 0f);
        // The search request (and with it any query) is left as it was.
        assertTrue((boolean) ReflectionHelpers.getField(content, "mSearchRevealRequested"));
    }

    @Test public void settlingOnCategoriesSendsTheBuiltInKeyboardAwayAndKeepsTheQuery() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(content, "mSearchRevealRequested", true);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        reveal(1f);

        controller.onSectionChanged(AppDrawerSection.CATEGORIES);

        Object spring = ReflectionHelpers.getField(controller, "mReveal");
        assertEquals(0f, (float) ReflectionHelpers.getField(spring, "target"), 0f);
        assertTrue((boolean) ReflectionHelpers.getField(content, "mSearchRevealRequested"));
    }

    @Test public void settlingOnCategoriesDismissesTheSystemKeyboardEachTime() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mImePinTopPx", 1500f);

        controller.onSectionChanged(AppDrawerSection.CATEGORIES);
        controller.onSectionChanged(AppDrawerSection.CATEGORIES);

        assertEquals(2, host.dismissSearchSystemKeyboardCalls);
    }

    @Test public void settlingOnAppsOrWithNoKeyboardLeavesTheKeyboardAlone() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpen", true);
        ReflectionHelpers.setField(controller, "mImePinTopPx", 1500f);
        controller.onSectionChanged(AppDrawerSection.APPS);
        assertEquals(0, host.dismissSearchSystemKeyboardCalls);

        ReflectionHelpers.setField(controller, "mImePinTopPx", 0f);
        reveal(0f);
        controller.onSectionChanged(AppDrawerSection.CATEGORIES);
        assertEquals(0, host.dismissSearchSystemKeyboardCalls);
    }

    @Test public void aDragWithTheKeyboardDownOrTheDrawerClosedDoesNothing() {
        floating();
        AppDrawerContentView content = new AppDrawerContentView(plane.getContext());
        content.setRevealListener(() -> { });
        ReflectionHelpers.setField(controller, "mContent", content);
        reveal(0f);
        ReflectionHelpers.setField(controller, "mOpen", true);
        controller.onListDragHidesKeyboard();
        assertFalse((boolean) ReflectionHelpers.getField(content, "mKeyboardDismissedByScroll"));

        reveal(1f);
        ReflectionHelpers.setField(controller, "mOpen", false);
        controller.onListDragHidesKeyboard();
        assertFalse((boolean) ReflectionHelpers.getField(content, "mKeyboardDismissedByScroll"));
    }

    /**
     * The scrim's hole moves with the keyboard on every frame of the open. A full-screen
     * anti-aliased even-odd path re-rasterised per frame cost 5-14 ms of RenderThread issue on
     * pong; the scrim is now four exact rects and four small corner wedges, and never a layer.
     */
    @Test public void scrimDrawsRectsAndSmallWedgesNeverAScreenPathOrLayer() {
        AppDrawerScrimDrawable scrim = new AppDrawerScrimDrawable();
        scrim.setBounds(0, 0, WIDTH, HEIGHT);
        scrim.setColor(0x66000000);
        scrim.setHole(new android.graphics.RectF(24f, 1690f, WIDTH - 24f, 2330f), 40f);
        final int[] rects = {0};
        final int[] layers = {0};
        final java.util.List<android.graphics.RectF> paths = new java.util.ArrayList<>();
        android.graphics.Canvas canvas = new android.graphics.Canvas() {
            @Override public void drawRect(float l, float t, float r, float b,
                                           @androidx.annotation.NonNull android.graphics.Paint p) {
                rects[0]++;
            }

            @Override public void drawPath(@androidx.annotation.NonNull android.graphics.Path path,
                                           @androidx.annotation.NonNull android.graphics.Paint p) {
                android.graphics.RectF bounds = new android.graphics.RectF();
                path.computeBounds(bounds, true);
                paths.add(bounds);
            }

            @Override public int saveLayer(@androidx.annotation.Nullable android.graphics.RectF b,
                                           @androidx.annotation.Nullable android.graphics.Paint p) {
                layers[0]++;
                return super.save();
            }

            @Override public int saveLayerAlpha(@androidx.annotation.Nullable android.graphics.RectF b,
                                                int alpha) {
                layers[0]++;
                return super.save();
            }
        };

        scrim.draw(canvas);

        assertEquals(4, rects[0]);
        assertEquals(0, layers[0]);
        assertEquals(4, paths.size());
        for (android.graphics.RectF bounds : paths) {
            // The one cached wedge, a radius square: never a path the size of the screen.
            assertTrue(bounds.width() <= 40.5f && bounds.height() <= 40.5f);
        }
        assertEquals(40f, scrim.effectiveRadiusPx(), 0f);
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

    /** Keyboard down, Docked's plane stays a card: Margin all round, the pane's corners. */
    @Test public void keyboardDownLeavesThePlaneOnItsCard() {
        ReflectionHelpers.setField(controller, "mRoundedStyle", false);
        host.paneGapPx = 26f;
        host.paneCornerRadiusPx = 55f;
        host.marginPx = 21f;
        ReflectionHelpers.setField(controller, "mOpenRect",
            ReflectionHelpers.callInstanceMethod(controller, "resolveOpenRect"));
        reveal(0f);

        applyFrame(1f);

        assertEquals(new Frame(21f, 21f, WIDTH - 21f, HEIGHT - 21f), plane.getFrame());
        assertEquals(55f, (float) ReflectionHelpers.getField(controller, "mCurrentRadiusPx"), 1e-3f);
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

    @Test
    public void dockedCardRestFrameAndBottom() {
        // Margin in from the sides, below the status inset and above the navigation band.
        assertEquals(new Frame(20f, 120f, 980f, 2290f),
            AppDrawerKeyboardSeparation.dockedCardRestFrame(1000f, 2400f, 100f, 90f, 20f));
        // No Margin given: the card is the space between the bars.
        assertEquals(new Frame(0f, 100f, 1000f, 2310f),
            AppDrawerKeyboardSeparation.dockedCardRestFrame(1000f, 2400f, 100f, 90f, -1f));
        // The bottom: the rest bottom at 0, the gap above the keyboard at 1, linear between.
        assertEquals(2290f, AppDrawerKeyboardSeparation.cardBottomPx(2290f, 1700f, 20f, 0f), 0f);
        assertEquals(1680f, AppDrawerKeyboardSeparation.cardBottomPx(2290f, 1700f, 20f, 1f), 0f);
        assertEquals(1985f, AppDrawerKeyboardSeparation.cardBottomPx(2290f, 1700f, 20f, 0.5f),
            1e-3f);
        // The card's corners: the pane's under Docked, the drawer's own under Floating or with none.
        assertEquals(55f, AppDrawerKeyboardSeparation.openRadiusPx(false, 40f, 55f), 0f);
        assertEquals(40f, AppDrawerKeyboardSeparation.openRadiusPx(true, 40f, 55f), 0f);
        assertEquals(40f, AppDrawerKeyboardSeparation.openRadiusPx(false, 40f, -1f), 0f);
    }

    @Test
    public void dockedCardPolicy() {
        // Docked over the built-in keyboard leaves Margin; Floating and a system keyboard their gap.
        assertEquals(21f, AppDrawerKeyboardSeparation.planeGapPx(false, false, 12f, 21f), 0f);
        assertEquals(0f, AppDrawerKeyboardSeparation.planeGapPx(false, false, 12f, -1f), 0f);
        assertEquals(12f, AppDrawerKeyboardSeparation.planeGapPx(true, false, 12f, 21f), 0f);
        assertEquals(12f, AppDrawerKeyboardSeparation.planeGapPx(false, true, 12f, 21f), 0f);
        assertTrue(AppDrawerKeyboardSeparation.dockedCard(false, false));
        assertFalse(AppDrawerKeyboardSeparation.dockedCard(true, false));
        assertFalse(AppDrawerKeyboardSeparation.dockedCard(false, true));

        // The card's top: Margin below the status bar's inset; no inset, Margin from the top.
        assertEquals(120f, AppDrawerKeyboardSeparation.dockedCardTopPx(100f, 20f), 0f);
        assertEquals(20f, AppDrawerKeyboardSeparation.dockedCardTopPx(0f, 20f), 0f);
        assertEquals(100f, AppDrawerKeyboardSeparation.dockedCardTopPx(100f, -1f), 0f);

        Frame sheet = new Frame(0f, -40f, 1000f, 2440f);
        // Reveal 0: the sheet as it was, but for the bottom it is given.
        Frame at0 = AppDrawerKeyboardSeparation.dockedCardFrame(sheet, 1000f, 20f, 120f, 2440f, 0f);
        assertEquals(0f, at0.left, 0f);
        assertEquals(1000f, at0.right, 0f);
        assertEquals(-40f, at0.top, 0f);
        // Halfway, then Margin in at the sides and the top below the status bar.
        Frame half = AppDrawerKeyboardSeparation.dockedCardFrame(sheet, 1000f, 20f, 120f, 2000f,
            0.5f);
        assertEquals(10f, half.left, 1e-4f);
        assertEquals(990f, half.right, 1e-4f);
        assertEquals(40f, half.top, 1e-4f);
        assertEquals(2000f, half.bottom, 0f);
        Frame card = AppDrawerKeyboardSeparation.dockedCardFrame(sheet, 1000f, 20f, 120f, 1700f, 1f);
        assertEquals(20f, card.left, 0f);
        assertEquals(980f, card.right, 0f);
        assertEquals(120f, card.top, 0f);
        // An edge already further in (the seed, early in the open) is never moved out.
        Frame seed = new Frame(0f, 1560f, 1000f, 1720f);
        assertEquals(1560f, AppDrawerKeyboardSeparation.dockedCardFrame(seed, 1000f, 20f, 120f,
            1700f, 1f).top, 0f);
    }
}
