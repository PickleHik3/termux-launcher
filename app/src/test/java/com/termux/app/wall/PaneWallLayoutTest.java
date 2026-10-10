package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Page positions. Every place is laid out at the host's size and only ever moved, so a page
 * change and a whole drag cost no layout work — and the terminal page in the middle keeps the
 * exact bounds it had before the wall existed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneWallLayoutTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;
    private static final float EPS = 0.01f;

    private PaneWallLayout wall;
    private View widgets;
    private View terminal;
    private View display;

    private void build(Activity activity, boolean widgetsPage, boolean displayPage) {
        wall = new PaneWallLayout(activity);
        widgets = new FrameLayout(activity);
        terminal = new FrameLayout(activity);
        display = new FrameLayout(activity);
        wall.addView(widgets);
        wall.addView(terminal);
        wall.addView(display);
        wall.setReducedMotion(true); // no spring in a unit test: page changes land immediately
        wall.setPages(PaneWallPolicy.availablePages(!widgetsPage, widgetsPage, displayPage));
        // Every page view is registered whether or not the install has that place: a view whose
        // page is switched off must go away, not sit on top of the terminal.
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        wall.setPageView(PaneWallPage.WIDGETS, widgets);
        wall.setPageView(PaneWallPage.DISPLAY, display);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test
    public void aJumpAcrossTheRingBringsThePageInFromTheSideItSitsOn() {
        // Widgets, Terminal, Display make a ring. From Display, Widgets is the place to the
        // right, so a jump straight to it starts with the wall a whole width to the right - the
        // page slides in from the right edge; from Widgets, Display is the place to the left and
        // comes in from the left. The same sides a swipe reaches them from.
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.goTo(PaneWallPage.DISPLAY, false);
        assertEquals(0f, display.getTranslationX(), EPS);

        wall.goTo(PaneWallPage.WIDGETS, true);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals("Widgets starts a width to the right and slides in from there",
            WIDTH, wall.offsetPx(), EPS);
        wall.goTo(PaneWallPage.DISPLAY, false);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());

        wall.goTo(PaneWallPage.WIDGETS, false);
        wall.goTo(PaneWallPage.DISPLAY, true);
        assertEquals("Display starts a width to the left and slides in from there",
            -WIDTH, wall.offsetPx(), EPS);

        // Between neighbours the side is the plain one: from the terminal, Widgets is to the left
        // and Display to the right.
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.goTo(PaneWallPage.WIDGETS, true);
        assertEquals(-WIDTH, wall.offsetPx(), EPS);
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.goTo(PaneWallPage.DISPLAY, true);
        assertEquals(WIDTH, wall.offsetPx(), EPS);
    }

    @Test
    public void theTerminalRestsOnScreenAndItsNeighboursRestOffIt() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(-WIDTH, widgets.getTranslationX(), EPS);
        assertEquals(WIDTH, display.getTranslationX(), EPS);
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(1f, terminal.getAlpha(), EPS);
        assertEquals(View.INVISIBLE, widgets.getVisibility());
        assertEquals(View.INVISIBLE, display.getVisibility());
    }

    /**
     * Unlike the other places, the Terminal page never goes INVISIBLE off screen: it stays
     * VISIBLE and is faded to alpha 0 instead, so its display lists (kitty animation frames, the
     * padding band, every pane's rows) survive the trip away and the frame that brings it back
     * repaints them rather than re-recording from nothing (see PaneWallLayout#applyPagePositions
     * and 707920f7, which is where an off-screen page first stopped drawing at all).
     */
    @Test
    public void theTerminalPageStaysVisibleAndFadesInsteadOfGoingInvisible() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(0f, terminal.getAlpha(), EPS);
        // The place actually on screen is unaffected: it is fully opaque, same as before.
        assertEquals(View.VISIBLE, widgets.getVisibility());
        assertEquals(1f, widgets.getAlpha(), EPS);

        wall.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(1f, terminal.getAlpha(), EPS);
    }

    /**
     * The Terminal page's own visibility no longer says when it left or came back, since it is
     * never INVISIBLE any more — so the wall says so directly, once per actual crossing.
     */
    @Test
    public void onTerminalOffScreenChangedFiresOnceASideOfEachCrossing() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        List<Boolean> reports = new ArrayList<>();
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onTerminalOffScreenChanged(boolean offScreen) {
                reports.add(offScreen);
            }
        });
        // Registering a listener does not itself replay the current state; it hears the terminal
        // leave for the first time when the wall actually moves off it.
        assertTrue(reports.isEmpty());

        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(Collections.singletonList(true), reports);

        // Moving between the two pages that are not the Terminal reports nothing new.
        wall.goTo(PaneWallPage.DISPLAY, false);
        assertEquals(Collections.singletonList(true), reports);

        wall.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(Arrays.asList(true, false), reports);
    }

    /** Brings {@code page} on screen and runs the layout pass that follows. */
    private void showAndLayOut(PaneWallPage page) {
        wall.goTo(page, false);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test
    public void everyPageIsLaidOutAtTheHostsSize() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        // Pages parked off screen skip layout; each is laid out once it is on screen.
        PaneWallPage[] pages = {PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY};
        View[] views = {widgets, terminal, display};
        for (int i = 0; i < pages.length; i++) {
            showAndLayOut(pages[i]);
            View page = views[i];
            assertEquals(0, page.getLeft());
            assertEquals(0, page.getTop());
            assertEquals(WIDTH, page.getWidth());
            assertEquals(HEIGHT, page.getHeight());
        }
    }

    @Test
    public void theTerminalPagesMarginsAreEveryPagesMargins() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        // The activity writes the surface editor's frame insets into the pane host's margins,
        // and checks they are margin params first — a plain ViewGroup's are not.
        android.view.ViewGroup.LayoutParams params = terminal.getLayoutParams();
        assertTrue("the wall hands out margin params",
            params instanceof android.view.ViewGroup.MarginLayoutParams);
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) params;
        margins.setMargins(24, 10, 24, 30);
        terminal.setLayoutParams(margins);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);

        // The pages still sit one full wall width apart, frame and all.
        assertEquals(-WIDTH, widgets.getTranslationX(), EPS);
        assertEquals(WIDTH, display.getTranslationX(), EPS);
        PaneWallPage[] pages = {PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY};
        View[] views = {widgets, terminal, display};
        for (int i = 0; i < pages.length; i++) {
            showAndLayOut(pages[i]);
            View page = views[i];
            assertEquals(24, page.getLeft());
            assertEquals(10, page.getTop());
            assertEquals(WIDTH - 24, page.getRight());
            assertEquals(HEIGHT - 30, page.getBottom());
        }
    }

    @Test
    public void theOtherPagesStandAtTheFrameWhileTheTerminalKeepsItsBordersAir() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        // The frame is the model's opening (24, 10); the terminal's own margins carry 3 more.
        margins.setMargins(27, 13, 27, 33);
        terminal.setLayoutParams(margins);
        wall.setTerminalFrameAirPx(3);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);

        showAndLayOut(PaneWallPage.TERMINAL);
        assertEquals(27, terminal.getLeft());
        assertEquals(13, terminal.getTop());
        showAndLayOut(PaneWallPage.WIDGETS);
        assertEquals(24, widgets.getLeft());
        assertEquals(10, widgets.getTop());
        assertEquals(WIDTH - 24, widgets.getRight());
        assertEquals(HEIGHT - 30, widgets.getBottom());
        showAndLayOut(PaneWallPage.DISPLAY);
        assertEquals(24, display.getLeft());
        assertEquals(WIDTH - 24, display.getRight());
    }

    @Test
    public void aDragMovesTheWholeWallOneToOne() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-200f);
        assertEquals(-200f, terminal.getTranslationX(), EPS);
        assertEquals(WIDTH - 200f, display.getTranslationX(), EPS);
        assertTrue("a page sliding in has to be drawing", display.getVisibility() == View.VISIBLE);
    }

    /**
     * A slide between two places does not draw the third. It used to be kept drawing for the whole
     * motion, so every page change paid for the widget grid and the display beside the two pages
     * actually on screen (Pong, 2026-09-23).
     */
    @Test
    public void aDragBetweenTwoPlacesLeavesTheThirdUndrawn() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-200f);
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(View.VISIBLE, display.getVisibility());
        assertEquals("the page behind the terminal is a full width off screen",
            View.INVISIBLE, widgets.getVisibility());

        // The finger turns back and the Widgets page slides in: it draws again as it arrives.
        wall.dragTo(200f);
        assertEquals(View.VISIBLE, widgets.getVisibility());
        assertEquals(View.INVISIBLE, display.getVisibility());
    }

    @Test
    public void aShortDragLeavesThePageAlone() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-100f);
        wall.endDrag(0f);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
        assertEquals(0f, terminal.getTranslationX(), EPS);
    }

    @Test
    public void aCommittedDragLandsOnTheNextPage() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-WIDTH * 0.5f);
        wall.endDrag(0f);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertEquals(0f, display.getTranslationX(), EPS);
        assertEquals(-WIDTH, terminal.getTranslationX(), EPS);
    }

    @Test
    public void threePagesWrapSoTheOuterPageHasANeighbourOnBothSides() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.goTo(PaneWallPage.DISPLAY, false);
        // From the rightmost place the Widgets page waits on the right, the shorter way round.
        assertEquals(WIDTH, widgets.getTranslationX(), EPS);
        assertEquals(-WIDTH, terminal.getTranslationX(), EPS);
        wall.beginDrag();
        wall.dragTo(-200f);
        assertEquals("the wrapped page slides in one to one, no rubber band",
            WIDTH - 200f, widgets.getTranslationX(), EPS);
        assertEquals(View.VISIBLE, widgets.getVisibility());
        wall.endDrag(-10_000f);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(0f, widgets.getTranslationX(), EPS);
    }

    @Test
    public void aPageChangeAcrossTheRingSlidesTheShortWayRound() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(0f, widgets.getTranslationX(), EPS);

        // Widgets -> Display: one step to the left on the ring, so the Display page starts one
        // width to the left and the Widgets page slides off to the right.
        wall.goTo(PaneWallPage.DISPLAY, true);
        assertTrue(wall.isMoving());
        assertEquals(-WIDTH, display.getTranslationX(), EPS);
        assertEquals(0f, widgets.getTranslationX(), EPS);
    }

    @Test
    public void aTwoPageWallStillRubberBandsAtItsEnd() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), false, true);
        wall.goTo(PaneWallPage.DISPLAY, false);
        wall.beginDrag();
        wall.dragTo(-WIDTH);
        wall.endDrag(-10_000f);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertEquals(0f, display.getTranslationX(), EPS);
    }

    @Test
    public void aPageChangeUnderALiveDragEndsTheDrag() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-200f);

        // A tile tap, wall.go or Home lands mid-drag.
        wall.goTo(PaneWallPage.DISPLAY, false);

        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertEquals(0f, display.getTranslationX(), EPS);
        // The rest of that finger is nobody's: the wall neither moves for it nor settles on it.
        wall.dragTo(300f);
        assertEquals(0f, display.getTranslationX(), EPS);
        wall.endDrag(-10_000f);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
    }

    @Test
    public void switchingGesturesOffUnderALiveDragInterruptsIt() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-200f);

        wall.setGesturesEnabled(false);

        assertFalse(wall.isMoving());
    }

    @Test
    public void aMissingPageIsSkippedRatherThanLeftAsADeadSwipe() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), false, true);
        assertEquals(View.GONE, widgets.getVisibility());
        wall.beginDrag();
        wall.dragTo(WIDTH * 0.5f);
        wall.endDrag(0f);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void aPageThatGoesAwayHandsTheWallBackToTheTerminal() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        wall.setPages(PaneWallPolicy.availablePages(true, false, false));
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
        assertEquals(0f, terminal.getTranslationX(), EPS);
    }

    @Test
    public void holdingTheGesturesStillEndsALiveDrag() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.beginDrag();
        wall.dragTo(-300f);
        wall.setGesturesEnabled(false);
        assertEquals(0f, terminal.getTranslationX(), EPS);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertEquals("a held wall must not move at all", 0f, terminal.getTranslationX(), EPS);
    }

    // ---- A slide cut short -----------------------------------------------------------------

    /** Ask for Widgets with a spring, then cut the spring before its first frame. */
    private void cutASlideToWidgets() {
        wall.setReducedMotion(false);
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, true));
        assertTrue(wall.isMoving());
        wall.onDetachedFromWindow();
    }

    @Test
    public void aCutSlideLeavesTheRecordAheadOfThePixelsAndThePixelsAnswer() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        cutASlideToWidgets();
        // At rest, the record names Widgets, but the terminal page never moved.
        assertFalse(wall.isMoving());
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertTrue(wall.isRestingOffPage());
        assertTrue(wall.isPageOnScreen(PaneWallPage.TERMINAL));
        assertFalse(wall.isPageOnScreen(PaneWallPage.WIDGETS));
    }

    @Test
    public void theNextLayoutFinishesACutSlideAndSaysSo() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        final PaneWallPage[] settled = {null};
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onWallPageSettled(PaneWallPage page) { settled[0] = page; }
        });
        cutASlideToWidgets();
        assertEquals(null, settled[0]);

        // A real pass: the keyboard or a bar asked for layout, so onLayout runs again.
        wall.requestLayout();
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);

        assertFalse(wall.isRestingOffPage());
        assertEquals(0f, wall.offsetPx(), EPS);
        assertEquals(PaneWallPage.WIDGETS, settled[0]);
        assertEquals(WIDTH, terminal.getTranslationX(), EPS);
        // The Terminal page stays VISIBLE off screen and is faded instead, so its display lists
        // survive the trip away (see PaneWallLayout#applyPagePositions and 707920f7).
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(0f, terminal.getAlpha(), EPS);
        assertEquals(0f, widgets.getTranslationX(), EPS);
        assertTrue(wall.isPageOnScreen(PaneWallPage.WIDGETS));
        assertFalse(wall.isPageOnScreen(PaneWallPage.TERMINAL));
    }

    @Test
    public void comingBackToTheWindowFinishesACutSlide() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        cutASlideToWidgets();
        wall.onAttachedToWindow();
        assertFalse(wall.isRestingOffPage());
        assertEquals(WIDTH, terminal.getTranslationX(), EPS);
        assertEquals(0f, widgets.getTranslationX(), EPS);
    }

    // ---- A page nudged in from the side ----------------------------------------------------

    @Test
    public void aNudgeMovesThePageOnTopOfTheWallsOwnPositionAndADragDropsIt() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        final int[] ended = {0};
        wall.setReducedMotion(false);
        wall.nudgePage(PaneWallPage.TERMINAL, 300f, 380L, null, () -> ended[0]++);
        // The first frame has the page beside its place and the neighbours where they were.
        assertEquals(300f, terminal.getTranslationX(), EPS);
        assertEquals(-WIDTH, widgets.getTranslationX(), EPS);
        assertEquals(0, ended[0]);

        wall.beginDrag();
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(1, ended[0]);
    }

    @Test
    public void aNudgeOnAPageTheWallHasPutAwayLeavesItAway() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, false));
        final int[] ended = {0};
        wall.nudgePage(PaneWallPage.TERMINAL, -WIDTH, 380L, null, () -> ended[0]++);
        assertEquals(1, ended[0]);
        assertEquals(WIDTH, terminal.getTranslationX(), EPS);
        assertEquals(View.VISIBLE, terminal.getVisibility());
        assertEquals(0f, terminal.getAlpha(), EPS);
    }

    @Test
    public void aNudgeOnAPageTheWallDoesNotHaveJustRunsItsEnding() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), false, true);
        final int[] ended = {0};
        wall.nudgePage(PaneWallPage.WIDGETS, 300f, 380L, null, () -> ended[0]++);
        assertEquals(1, ended[0]);
        assertEquals(0f, terminal.getTranslationX(), EPS);
    }

    /**
     * The planks (PlankTilt): a held border's drag tips the page the wall rests on when the
     * listener allows it, by the angle its position and the finger's weight say, on a hardware
     * layer for the length of the motion; the settle lays it flat and drops the layer. A flat
     * drag with no press (the test harness's) slides flat.
     *
     * <p>Changed with the planks' lean (2026-09-28): the page used to lead with the side it moved
     * toward; it now dips toward the finger, and a finger on its centre line dips it toward the
     * page arriving beside it — here the right edge, for a drag to the left.
     */
    @Test
    public void aDragTipsThePlankAndTheSettleLaysItFlat() {
        buildWithContent();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // A flat drag with no press: the same leave, but nothing tips.
        wall.beginDrag();
        assertTrue(wall.tiltPages().isEmpty());
        assertTrue(wall.goTo(PaneWallPage.TERMINAL, false));

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        assertTrue(wall.tiltPages().contains(terminal));
        assertEquals(View.LAYER_TYPE_HARDWARE, terminal.getLayerType());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            40f, 400L));
        assertEquals(PlankTilt.angleDeg(-300f, WIDTH, PlankTilt.lean(-300f, -300f, WIDTH)),
            terminal.getRotationY(), EPS);
        assertTrue("pressed on its centre line and dragged left, it dips toward the page arriving "
            + "on its right", terminal.getRotationY() > 0f);
        // A plain Display page here, parked the whole drag: it has no stand-in to tip.
        assertEquals("the Display page never tips", 0f, display.getRotationY(), EPS);
        // A jump lands the wall at rest: flat, and the layer gone.
        assertTrue(wall.goTo(PaneWallPage.DISPLAY, false));
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    /** Lays every page out once, as the pages a finger has visited are: a plank needs a size. */
    private void layOutEveryPage() {
        showAndLayOut(PaneWallPage.WIDGETS);
        showAndLayOut(PaneWallPage.DISPLAY);
        showAndLayOut(PaneWallPage.TERMINAL);
    }

    @Test
    public void bothPagesTipTowardTheFingerAndTheReleaseCarriesThemFlat() {
        buildWithContent();
        layOutEveryPage();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // Held by the left border and pulled right: the Widgets page comes in from the left, and
        // the finger stands on the seam between the two.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 304f, HEIGHT / 2f,
            3000L));
        assertEquals(300f, terminal.getTranslationX(), EPS);
        assertEquals(300f - WIDTH, widgets.getTranslationX(), EPS);
        assertTrue(wall.tiltPages().contains(terminal));
        assertTrue(wall.tiltPages().contains(widgets));
        assertTrue("the page leaving dips its left edge, at the finger",
            terminal.getRotationY() < -1f);
        assertTrue("the page arriving dips its right edge, at the finger",
            widgets.getRotationY() > 1f);
        assertEquals("one motion: the two tip alike", Math.abs(terminal.getRotationY()),
            Math.abs(widgets.getRotationY()), EPS);
        assertEquals(View.LAYER_TYPE_HARDWARE, widgets.getLayerType());

        // Flung on to the Widgets page: the planks are not laid flat at the lift, but carried on
        // the settle's spring and flat once it lands.
        float leavingAtLift = terminal.getRotationY();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 404f, HEIGHT / 2f,
            3016L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 404f, HEIGHT / 2f,
            3032L));
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertTrue(wall.isMoving());
        assertTrue("still tipped as the settle starts", Math.abs(terminal.getRotationY()) > 1f);
        assertTrue(Math.signum(terminal.getRotationY()) == Math.signum(leavingAtLift));
        letTheMotionSettle();
        assertFalse(wall.isMoving());
        assertEquals(0f, widgets.getTranslationX(), EPS);
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(0f, widgets.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
        assertEquals(View.LAYER_TYPE_NONE, widgets.getLayerType());
    }

    /**
     * Changed with the Display's stand-in (2026-09-29): the listener no longer refuses the Display
     * a tip, the wall decides. A Display page that cannot stand a copy in for its surface — a
     * plain view here, as a page that is not a {@link SurfacePage} — keeps what a SurfaceView
     * allows: no tilt, no layer. The stand-in's own tests are further down.
     */
    @Test
    public void theDisplayPageNeverTipsNorTakesALayerEvenWithLeave() {
        buildWithContent();
        layOutEveryPage();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // Held by the right border and pulled left: the Display page comes in from the right.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH - 4f,
            HEIGHT / 2f, 0L));
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 304f,
            HEIGHT / 2f, 400L));
        assertTrue(terminal.getRotationY() > 0f);
        assertFalse(wall.tiltPages().contains(display));
        assertEquals(0f, display.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
    }

    @Test
    public void theSlideCarriesOnFromTheReleaseAndNeverPassesItsRest() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.beginDrag();
        wall.dragTo(-300f);
        wall.endDrag(-3000f);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        // The commit moved the record a width on; the pixels are where the finger left them.
        assertEquals(WIDTH - 300f, wall.offsetPx(), EPS);
        assertEquals(-300f, terminal.getTranslationX(), EPS);
        float previous = wall.offsetPx();
        for (int frame = 0; frame < 120 && wall.isMoving(); frame++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(16L));
            float offset = wall.offsetPx();
            assertTrue("never past its rest: " + offset, offset >= -EPS);
            assertTrue("never back the way it came: " + offset, offset <= previous + EPS);
            previous = offset;
        }
        letTheMotionSettle();
        assertFalse(wall.isMoving());
        assertEquals(0f, wall.offsetPx(), EPS);
        assertEquals(0f, display.getTranslationX(), EPS);
    }

    @Test
    public void thePlankNeedsTheListenersLeaveAndThePhoneAnimating() {
        // The default listener gives no leave: the drag moves the page and nothing tips.
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(-300f, terminal.getTranslationX(), EPS);
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
        assertTrue(wall.goTo(PaneWallPage.TERMINAL, false));

        // With leave but the phone told to hold still, the same: reduced motion tips nothing.
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        wall.setReducedMotion(true);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        wall.cancelDrag();
        assertEquals(0f, terminal.getTranslationX(), EPS);
    }

    // ---- The border drag ---------------------------------------------------------------------

    /** The content under a page: takes every touch and remembers what it was sent. */
    private static final class Content extends View {
        final List<Integer> actions = new ArrayList<>();

        Content(android.content.Context context) {
            super(context);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }
    }

    private Content content;

    /** A wall whose terminal page holds content that takes every touch, like a terminal does. */
    private void buildWithContent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        widgets = new FrameLayout(activity);
        terminal = new FrameLayout(activity);
        content = new Content(activity);
        ((FrameLayout) terminal).addView(content, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        display = new FrameLayout(activity);
        wall.addView(widgets);
        wall.addView(terminal);
        wall.addView(display);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(false, true, true));
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        wall.setPageView(PaneWallPage.WIDGETS, widgets);
        wall.setPageView(PaneWallPage.DISPLAY, display);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    private static android.view.MotionEvent touch(int action, float x, float y, long timeMs) {
        return android.view.MotionEvent.obtain(0L, timeMs, action, x, y, 0);
    }

    /** Lets the hold's timer run out. */
    private static void letTheHoldElapse() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(com.termux.view.HoldTiming.holdTimeoutMs() + 20L));
    }

    @Test
    public void aHeldBorderTakesTheFingerFromTheContentAndDragsTheWall() {
        buildWithContent();
        // Down on the top border, away from the corners: the content gets it, as for a tap.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        assertEquals(Collections.singletonList(android.view.MotionEvent.ACTION_DOWN),
            content.actions);
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        assertFalse(wall.isDragging());

        letTheHoldElapse();
        // The hold claimed it: the content was told to forget the touch, and the wall is dragging.
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_CANCEL), content.actions);
        assertEquals(BorderDrag.Claim.PAGING, wall.borderDragClaim());
        assertTrue(wall.isDragging());

        // From here the finger's sideways travel is the wall's, and the content hears nothing.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 600f,
            40f, 400L));
        assertEquals(-600f, terminal.getTranslationX(), EPS);
        assertEquals(WIDTH - 600f, display.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 600f,
            40f, 420L));
        assertEquals(2, content.actions.size());
        assertEquals("past the commit distance: the place on the right", PaneWallPage.DISPLAY,
            wall.currentPage());
        assertFalse(wall.isDragging());
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
    }

    @Test
    public void aFingerThatMovesBeforeTheHoldStaysTheContents() {
        buildWithContent();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        // A swipe from the border without a hold: a scroll, a selection, a TUI's drag.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 100f,
            4f, 50L));
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 400f,
            4f, 500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 400f,
            4f, 520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_UP), content.actions);
        assertEquals("the wall never moved", 0f, terminal.getTranslationX(), EPS);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void aPressOffTheBorderOrInACornerNeverArms() {
        buildWithContent();
        // The middle of the page.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT / 2f, 0L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT / 2f, 400L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_UP), content.actions);
        assertFalse(wall.isDragging());

        // A corner square: the corner tab's.
        content.actions.clear();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 1000L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, 4f, 1400L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_UP), content.actions);
        assertFalse(wall.isDragging());
    }

    @Test
    public void aWallMovedFromUnderAHeldBorderSwallowsTheRestOfTheFinger() {
        buildWithContent();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 1f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        // A key or wall.go lands mid-drag.
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            HEIGHT - 1f, 400L));
        assertEquals("the rest of the finger moves nothing", 0f, widgets.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 300f,
            HEIGHT - 1f, 420L));
        assertEquals("and reaches no content", Arrays.asList(
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_CANCEL),
            content.actions);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
    }

    @Test
    public void aSystemCancelUnderAHeldBorderSpringsTheWallBack() {
        buildWithContent();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 204f, HEIGHT / 2f,
            400L));
        assertEquals(200f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, 204f, HEIGHT / 2f,
            420L));
        assertFalse(wall.isDragging());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void theBorderDragEngagesThePlankAndAFlatDragDoesNot() {
        buildWithContent();
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // A flat drag with no press: the page slides flat.
        wall.beginDrag();
        wall.dragTo(-300f);
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        wall.cancelDrag();
        assertEquals(0f, terminal.getTranslationX(), EPS);

        // A held border: the page tips under the finger.
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        assertTrue(wall.tiltPages().contains(terminal));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            4f, 400L));
        assertEquals(PlankTilt.angleDeg(-300f, WIDTH, PlankTilt.lean(-300f, -300f, WIDTH)),
            terminal.getRotationY(), EPS);
    }

    // ---- The sink ------------------------------------------------------------------------------

    /** Lets every spring and slide run out. */
    private static void letTheMotionSettle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(2000L));
    }

    @Test
    public void aHeldBorderSinksThePageInEveryModeAndTheLiftSpringsItBack() {
        buildWithContent();
        wall.setReducedMotion(false);
        final List<Float> frameScales = new ArrayList<>();
        // No plank: the sink is not a Fancier Glass motion.
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onPageSinkChanged(PaneWallPage page, float scale) {
                if (page == PaneWallPage.TERMINAL) frameScales.add(scale);
            }
        });
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        assertNull("not before the hold", wall.sinkPage());
        letTheHoldElapse();
        assertEquals(terminal, wall.sinkPage());
        letTheMotionSettle();
        assertEquals(1f, wall.sink(), EPS);
        assertEquals(PageSink.SCALE, terminal.getScaleX(), EPS);
        assertEquals(PageSink.SCALE, terminal.getScaleY(), EPS);
        assertEquals("dimmed on a layer", View.LAYER_TYPE_HARDWARE, terminal.getLayerType());
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        // Stays down through the drag.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 100f,
            4f, 3000L));
        assertEquals(PageSink.SCALE, terminal.getScaleX(), EPS);
        // Let go short of the commit: back up, full size, the layer given back.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 100f,
            4f, 3020L));
        letTheMotionSettle();
        assertNull(wall.sinkPage());
        assertEquals(1f, terminal.getScaleX(), EPS);
        assertEquals(1f, terminal.getScaleY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
        // The terminal's frame line was told every step, and told it was back at rest.
        assertTrue(frameScales.contains(PageSink.SCALE));
        assertEquals(1f, frameScales.get(frameScales.size() - 1), EPS);
    }

    @Test
    public void underFancierGlassAHeldSideBorderPushesThatSideIn() {
        buildWithContent();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // The right border, half way down.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH - 4f,
            HEIGHT / 2f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        assertTrue(wall.tiltPages().contains(terminal));
        assertEquals(PlankTilt.HOLD_TILT_DEG, terminal.getRotationY(), EPS);
        assertTrue("the right edge goes in", terminal.getRotationY() > 0f);
        // Dragged, the travel's tip takes over, deeper than the old twelve degrees half way out,
        // and still toward the finger on the right edge.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 4f - WIDTH / 2f,
            HEIGHT / 2f, 3000L));
        assertEquals(PlankTilt.angleDeg(-WIDTH / 2f, WIDTH, 1f, 1, 1f), terminal.getRotationY(),
            EPS);
        assertTrue(terminal.getRotationY() > 12f);
        // Pulled back to rest and let go: flat, full size, the layer gone once the spring lands.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 4f,
            HEIGHT / 2f, 3100L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH - 4f,
            HEIGHT / 2f, 3400L));
        letTheMotionSettle();
        assertNull(wall.sinkPage());
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(1f, terminal.getScaleX(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    /**
     * A Display page with no stand-in (a plain view, not a {@link SurfacePage}) sinks by scale
     * alone, as it did before there was one: its picture would be a SurfaceView a layer strands.
     * With a stand-in up it takes the layer and the dim (see
     * {@link #theDisplaysStandInSinksOnALayerAndTheRestGivesTheSurfaceBack}).
     */
    @Test
    public void theDisplayPageSinksWithoutALayer() {
        buildWithContent();
        // Parked off screen at the first layout, the Display page is laid out once it is shown.
        showAndLayOut(PaneWallPage.DISPLAY);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        assertEquals(display, wall.sinkPage());
        assertEquals(PageSink.SCALE, display.getScaleX(), EPS);
        assertEquals("its picture is a SurfaceView a layer would strand", View.LAYER_TYPE_NONE,
            display.getLayerType());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f, 4f,
            3000L));
        letTheMotionSettle();
        assertNull(wall.sinkPage());
        assertEquals(1f, display.getScaleX(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
    }

    @Test
    public void reducedMotionSinksNothing() {
        buildWithContent();
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // buildWithContent leaves motion reduced.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH - 4f,
            HEIGHT / 2f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        assertNull(wall.sinkPage());
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(1f, terminal.getScaleX(), EPS);
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    @Test
    public void aJumpUnderASunkPageLandsItAtRestAtOnce() {
        buildWithContent();
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        assertEquals(terminal, wall.sinkPage());
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, false));
        assertNull(wall.sinkPage());
        assertEquals(1f, terminal.getScaleX(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    // ---- The keyboard swipe off the bottom border --------------------------------------------

    private final List<Boolean> keyboardSwipes = new ArrayList<>();

    /** A listener that wants the keyboard swipe, as the wall's controller does. */
    private void listenForKeyboardSwipes() {
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isBorderKeyboardSwipeEnabled() { return true; }
            @Override public void onBorderKeyboardSwipe(boolean open) { keyboardSwipes.add(open); }
        });
    }

    @Test
    public void anUpSwipeOffTheBottomBorderOpensTheKeyboardAndCancelsTheContent() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        assertEquals(BorderDrag.Claim.KEYBOARD, wall.borderDragClaim());
        assertEquals("claimed at once: the content forgets the touch", Arrays.asList(
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_CANCEL),
            content.actions);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 304f, 70L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 304f, 90L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
        assertEquals("and hears nothing after", 2, content.actions.size());
        assertFalse(wall.isDragging());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        // The hold's timer was put away with the claim: nothing pages later.
        letTheHoldElapse();
        assertFalse(wall.isDragging());
    }

    @Test
    public void aDownSwipeOffTheBottomBorderClosesIt() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        // Down past the wall's own edge, over the keyboard: the stream is still the wall's.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT + 150f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT + 150f, 80L));
        assertEquals(Collections.singletonList(false), keyboardSwipes);
    }

    @Test
    public void aCancelledKeyboardSwipeAsksForNothing() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
    }

    @Test
    public void aSidewaysSwipeOffTheBottomBorderStaysTheContents() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 100f,
            HEIGHT - 10f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 150f,
            HEIGHT - 200f, 70L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 150f,
            HEIGHT - 200f, 90L));
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_MOVE,
            android.view.MotionEvent.ACTION_UP), content.actions);
    }

    @Test
    public void aVerticalSwipeHigherUpThanTheReachStaysTheContents() {
        buildWithContent();
        listenForKeyboardSwipes();
        float density = wall.getResources().getDisplayMetrics().density;
        float y = HEIGHT - BorderDrag.KEYBOARD_REACH_DP * density - 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, y - 200f,
            50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y - 200f,
            80L));
        assertTrue("a scroll from the last rows is the terminal's", keyboardSwipes.isEmpty());
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
    }

    @Test
    public void aHoldOnTheBottomBorderStillPagesWithTheKeyboardSwipeOn() {
        buildWithContent();
        listenForKeyboardSwipes();
        // On the line, within the hold's reach of it.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 1f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        // Held first, even a vertical move is the drag's, and the wall follows the sideways part.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 600f,
            HEIGHT - 300f, 400L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 600f,
            HEIGHT - 300f, 420L));
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
    }

    @Test
    public void aWallOfOnePlaceStillHasTheKeyboardSwipeAndLeavesHoldsToTheContent() {
        buildWithContent();
        wall.setPages(Collections.singletonList(PaneWallPage.TERMINAL));
        listenForKeyboardSwipes();
        // The swipe.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);

        // A hold on the bottom border has no page to go to: the content keeps it.
        content.actions.clear();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 1000L));
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 1500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 1520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        assertEquals("a selection drag after the hold is not the keyboard's", 1,
            keyboardSwipes.size());
        assertFalse(wall.isDragging());

        // The top border arms nothing at all.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f,
            2000L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 4f, 2020L));
    }

    /** The air between the terminal's frame and whatever stands below the wall, in px. */
    private static final int AIR_BELOW = 150;

    /** {@link #buildWithContent}, with the terminal page's frame standing {@link #AIR_BELOW} up. */
    private void buildWithAirBelow() {
        buildWithContent();
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        margins.setMargins(0, 0, 0, AIR_BELOW);
        terminal.setLayoutParams(margins);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        assertEquals(HEIGHT - AIR_BELOW, terminal.getBottom());
    }

    @Test
    public void aHoldOnTheLastTextRowIsTheContentsLongPress() {
        buildWithAirBelow();
        listenForKeyboardSwipes();
        float density = wall.getResources().getDisplayMetrics().density;
        // Inside the old band and the keyboard's reach, past the hold's: the last row's text.
        float frame = HEIGHT - AIR_BELOW;
        float y = frame - (BorderDrag.BOTTOM_HOLD_REACH_DP + BorderDrag.KEYBOARD_REACH_DP) / 2f
            * density;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        letTheHoldElapse();
        assertFalse("the hold never pages from the text", wall.isDragging());
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        // The long press goes on to a selection drag, the content's to the lift.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 200f,
            y - 200f, 500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 200f,
            y - 200f, 520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());

        // The same row still starts the keyboard swipe: it claims a moving finger.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y,
            1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, y - 200f,
            1050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y - 200f,
            1080L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
    }

    @Test
    public void aHoldInTheAirBelowTheFramePagesFarPastTheBand() {
        buildWithAirBelow();
        listenForKeyboardSwipes();
        float density = wall.getResources().getDisplayMetrics().density;
        float y = HEIGHT - 4f;
        assertTrue("the press is further out than the band",
            y - (HEIGHT - AIR_BELOW) > BorderDrag.BAND_DP * density);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        assertTrue("nothing under the finger: the content never hears it", content.actions.isEmpty());
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 600f, y,
            400L));
        assertEquals(-600f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 600f, y,
            420L));
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertTrue(keyboardSwipes.isEmpty());
    }

    @Test
    public void theBottomLineIsTheOpeningsNotThePageInsetAgain() {
        // Floating: the opening is 40 in from the wall, and the terminal's own margins carry its
        // border's 3 of air on top. The line a hold finds is the opening's, where it is drawn.
        buildWithContent();
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        margins.setMargins(43, 43, 43, 43);
        terminal.setLayoutParams(margins);
        wall.setTerminalFrameAirPx(3);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public int[] borderInsetsPx() { return new int[] {40, 40, 40, 40}; }
        });

        // The page's last row, 10 inside its foot: inset twice, the line stood 40 above it.
        float lastRow = terminal.getBottom() - 10f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, lastRow,
            0L));
        letTheHoldElapse();
        assertFalse("a hold on the last row is the content's", wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, lastRow,
            520L));

        // On the drawn line the hold pages.
        float line = HEIGHT - 40f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, line,
            1000L));
        letTheHoldElapse();
        assertTrue("a hold on the line pages", wall.isDragging());
    }

    @Test
    public void withGesturesOffTheBottomBorderIsTheContents() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.setGesturesEnabled(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(3, content.actions.size());
    }

    // ---- The keyboard under the finger -------------------------------------------------------

    private static final int KEYBOARD = 900;
    private final List<Boolean> revealBegins = new ArrayList<>();
    private final List<Float> reveals = new ArrayList<>();
    private final List<Boolean> revealEnds = new ArrayList<>();

    /** A listener whose keyboard can follow the finger, as the activity's docked one can. */
    private void listenForKeyboardReveals(final int travelPx) {
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isBorderKeyboardSwipeEnabled() { return true; }
            @Override public void onBorderKeyboardSwipe(boolean open) { keyboardSwipes.add(open); }
            @Override public int onKeyboardRevealBegin(boolean opening) {
                revealBegins.add(opening);
                return travelPx;
            }
            @Override public void onKeyboardRevealProgress(float reveal) { reveals.add(reveal); }
            @Override public void onKeyboardRevealEnd(boolean open) { revealEnds.add(open); }
        });
    }

    private float lastReveal() {
        return reveals.get(reveals.size() - 1);
    }

    @Test
    public void theKeyboardRisesUnderTheFingerAndAFlingOnOpensIt() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 300f, 50L));
        assertEquals(BorderDrag.Claim.KEYBOARD, wall.borderDragClaim());
        assertEquals("asked once, going up", Collections.singletonList(true), revealBegins);
        assertTrue(wall.isKeyboardRevealEngaged());
        // One to one over the keyboard's height, from where the finger landed.
        assertEquals(300f / KEYBOARD, lastReveal(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 450f, 70L));
        assertEquals(0.5f, lastReveal(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f - 450f, 90L));
        assertTrue("the release-only path is not asked", keyboardSwipes.isEmpty());
        assertTrue("nothing lands at the lift: it settles", revealEnds.isEmpty());
        letTheMotionSettle();
        assertEquals(Collections.singletonList(true), revealEnds);
        assertEquals(1f, lastReveal(), EPS);
        assertFalse(wall.isKeyboardRevealEngaged());
        // Up all the way, without a step back on the way.
        for (int i = 1; i < reveals.size(); i++) {
            assertTrue(reveals.get(i) >= reveals.get(i - 1) - EPS);
        }
    }

    @Test
    public void aShortSlowSwipeDownIsTakenBack() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f + 100f, 1000L));
        assertEquals("asked once, going down", Collections.singletonList(false), revealBegins);
        assertEquals(1f - 100f / KEYBOARD, lastReveal(), EPS);
        // Held still, then let go: short of a third and not flung, it goes back up.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f + 100f, 2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f + 100f, 3000L));
        letTheMotionSettle();
        assertEquals(Collections.singletonList(true), revealEnds);
        assertEquals(1f, lastReveal(), EPS);
    }

    @Test
    public void aSwipeDownPastAThirdClosesItWithoutAFullSwipe() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f + 320f, 1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f + 320f, 2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f + 320f, 3000L));
        letTheMotionSettle();
        assertEquals(Collections.singletonList(false), revealEnds);
        assertEquals(0f, lastReveal(), EPS);
    }

    @Test
    public void aCancelledSwipeTakesTheKeyboardBackToHowItWas() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 600f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f,
            HEIGHT - 4f - 600f, 60L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        letTheMotionSettle();
        assertEquals("far past a third, a cancel still goes back", Collections.singletonList(false),
            revealEnds);
        assertEquals(0f, lastReveal(), EPS);
        assertTrue(keyboardSwipes.isEmpty());
    }

    @Test
    public void aKeyboardThatCannotFollowIsAskedAtTheRelease() {
        buildWithContent();
        wall.setReducedMotion(false);
        // Floating, the phone's own, switched off: the listener answers 0.
        listenForKeyboardReveals(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        assertEquals(Collections.singletonList(true), revealBegins);
        assertFalse(wall.isKeyboardRevealEngaged());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
        assertTrue(reveals.isEmpty());
        assertTrue(revealEnds.isEmpty());
    }

    @Test
    public void reducedMotionNeverTiesTheKeyboardToTheFinger() {
        buildWithContent();
        listenForKeyboardReveals(KEYBOARD);
        // buildWithContent leaves motion reduced.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        assertTrue(revealBegins.isEmpty());
        assertEquals(Collections.singletonList(true), keyboardSwipes);
    }

    @Test
    public void theWallMovingLandsAKeyboardStillSettling() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 600f, 1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 600f, 2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f - 600f, 3000L));
        assertTrue(wall.isKeyboardRevealEngaged());
        // A tile or wall.go before the settle lands: the keyboard lands first, where it was going.
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(Collections.singletonList(true), revealEnds);
        assertEquals(1f, lastReveal(), EPS);
        assertFalse(wall.isKeyboardRevealEngaged());
    }

    @Test
    public void gesturesTakenAwayMidSwipePutTheKeyboardBack() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardReveals(KEYBOARD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 4f - 600f, 50L));
        wall.setGesturesEnabled(false);
        assertEquals(Collections.singletonList(false), revealEnds);
        assertEquals(0f, lastReveal(), EPS);
        // The rest of that finger asks for nothing.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f - 600f, 80L));
        assertEquals(1, revealEnds.size());
        assertTrue(keyboardSwipes.isEmpty());
    }

    // ---- The grabber on the bottom border ----------------------------------------------------

    @Test
    public void theGrabberIsDrawnOnlyWhileTheKeyboardSwipeIsOn() {
        buildWithContent();
        assertFalse("the default listener has no keyboard swipe", wall.isGrabberShown());
        listenForKeyboardSwipes();
        assertTrue(wall.isGrabberShown());
        wall.setGesturesEnabled(false);
        assertFalse(wall.isGrabberShown());
        wall.setGesturesEnabled(true);
        assertTrue(wall.isGrabberShown());
    }

    @Test
    public void theGrabberLightsUnderAFingerOnTheBandAndRestsAfter() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        // Inside the hold time: the finger is still only on the band.
        idle(200L);
        assertEquals(1f, wall.grabber().emphasis(), EPS);
        // The swipe draws it a little way after the finger.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        assertTrue(wall.grabber().trackOffsetPx() < 0f);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
        idle(300L);
        assertEquals(0f, wall.grabber().emphasis(), EPS);
        assertEquals(0f, wall.grabber().trackOffsetPx(), EPS);

        // A finger on the top border is not on the band.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f,
            1000L));
        idle(100L);
        assertEquals(0f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 4f, 1100L));
    }

    @Test
    public void underReducedMotionTheGrabberStaysStill() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
        idle(200L);
        assertEquals(0f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f,
            HEIGHT - 204f, 50L));
        assertEquals(0f, wall.grabber().trackOffsetPx(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 204f, 80L));
    }

    private static void idle(long ms) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(ms));
    }

    // ---- The status bar's swipe off the top border -------------------------------------------

    private static final int FOLD = 150;
    private final List<Boolean> statusSwipes = new ArrayList<>();
    private final List<Boolean> foldBegins = new ArrayList<>();
    private final List<Float> folds = new ArrayList<>();
    private final List<Boolean> foldEnds = new ArrayList<>();

    /**
     * A listener that wants both border swipes, as the wall's controller does with the bar along
     * the top; its fold follows the finger over {@code travelPx}, or not at all for 0.
     */
    private void listenForStatusSwipes(final int travelPx) {
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isBorderKeyboardSwipeEnabled() { return true; }
            @Override public void onBorderKeyboardSwipe(boolean open) { keyboardSwipes.add(open); }
            @Override public boolean isBorderStatusSwipeEnabled() { return true; }
            @Override public void onBorderStatusSwipe(boolean expand) { statusSwipes.add(expand); }
            @Override public int onStatusFoldBegin(boolean expanding) {
                foldBegins.add(expanding);
                return travelPx;
            }
            @Override public void onStatusFoldProgress(float towardOpenPx) {
                folds.add(towardOpenPx);
            }
            @Override public void onStatusFoldEnd(boolean expanded, float velocity) {
                foldEnds.add(expanded);
            }
        });
    }

    private float lastFold() {
        return folds.get(folds.size() - 1);
    }

    @Test
    public void aDownSwipeOffTheTopBorderUnfoldsTheBarAndCancelsTheContent() {
        buildWithContent();
        listenForStatusSwipes(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 204f,
            50L));
        assertEquals(BorderDrag.Claim.STATUS, wall.borderDragClaim());
        assertEquals("claimed at once: the content forgets the touch", Arrays.asList(
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_CANCEL),
            content.actions);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 204f, 80L));
        assertEquals(Collections.singletonList(true), statusSwipes);
        assertTrue("never the keyboard's", keyboardSwipes.isEmpty());
        assertEquals(2, content.actions.size());
        letTheHoldElapse();
        assertFalse("the hold's timer went with the claim", wall.isDragging());
    }

    @Test
    public void anUpSwipeOffTheTopBorderFoldsIt() {
        buildWithContent();
        listenForStatusSwipes(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, -150f,
            50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, -150f,
            80L));
        assertEquals(Collections.singletonList(false), statusSwipes);
    }

    @Test
    public void withoutTheStatusSwipeTheTopBorderStaysTheContentsAndAHoldStillPages() {
        buildWithContent();
        listenForKeyboardSwipes();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 204f,
            50L));
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 204f, 80L));

        // With it on, a hold on the top border is still the wall's.
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f,
            1000L));
        letTheHoldElapse();
        assertEquals(BorderDrag.Claim.PAGING, wall.borderDragClaim());
        assertTrue(wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 304f,
            1500L));
        assertTrue("a held drag down the page moves no fold", foldBegins.isEmpty());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 304f,
            1520L));
        assertTrue(statusSwipes.isEmpty());
    }

    @Test
    public void theTopCornersStayTheCornerTabs() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 4f, 204f, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, 204f, 80L));
        assertTrue(foldBegins.isEmpty());
        assertTrue(statusSwipes.isEmpty());
    }

    @Test
    public void theFoldFollowsTheFingerAndPastAThirdItUnfolds() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 30f,
            1000L));
        assertEquals("asked once, going down", Collections.singletonList(true), foldBegins);
        assertTrue(wall.isStatusFoldEngaged());
        assertEquals("from where the finger landed", 30f, lastFold(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 60f,
            1100L));
        assertEquals(60f / FOLD, wall.statusFold(), EPS);
        // Past the bar's whole way the fold is held there.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 400f,
            1200L));
        assertEquals((float) FOLD, lastFold(), EPS);
        // Back to 60 px of 150, held still, let go: past a third, it unfolds.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 60f,
            2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 60f,
            3000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 4f + 60f,
            4000L));
        assertEquals(Collections.singletonList(true), foldEnds);
        assertTrue("the release-only path is not asked", statusSwipes.isEmpty());
        assertFalse(wall.isStatusFoldEngaged());
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
    }

    @Test
    public void aShortSlowSwipeUpIsTakenBack() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f - 30f,
            1000L));
        assertEquals("asked once, going up", Collections.singletonList(false), foldBegins);
        assertEquals(-30f, lastFold(), EPS);
        assertEquals(1f - 30f / FOLD, wall.statusFold(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f - 30f,
            2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 4f - 30f,
            3000L));
        assertEquals("short of a third and not flung: it stays open",
            Collections.singletonList(true), foldEnds);
    }

    @Test
    public void aCancelledOrInterruptedFoldGoesBackToTheFormTheBarHad() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 120f,
            50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f,
            4f + 120f, 60L));
        assertEquals(Collections.singletonList(false), foldEnds);
        assertFalse(wall.isStatusFoldEngaged());

        // The wall moved by something else takes the fold back as well.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f,
            1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 120f,
            1050L));
        assertTrue(wall.goTo(PaneWallPage.DISPLAY, false));
        assertEquals(Arrays.asList(false, false), foldEnds);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 4f + 120f,
            1100L));
        assertEquals("the rest of that finger is swallowed", 2, foldEnds.size());
        assertTrue(statusSwipes.isEmpty());
    }

    @Test
    public void underReducedMotionTheFoldAnswersTheReleaseAlone() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 204f,
            50L));
        assertTrue("nothing follows the finger", foldBegins.isEmpty());
        assertFalse(wall.isStatusFoldEngaged());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 204f, 80L));
        assertEquals(Collections.singletonList(true), statusSwipes);
        assertTrue(foldEnds.isEmpty());
    }

    @Test
    public void theTopGrabberIsDrawnOnlyWhileTheStatusSwipeIsOnAndLightsForItsOwnBand() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForKeyboardSwipes();
        assertFalse(wall.isStatusGrabberShown());
        listenForStatusSwipes(FOLD);
        assertTrue(wall.isStatusGrabberShown());
        assertTrue(wall.statusGrabber().isTop());
        assertFalse(wall.grabber().isTop());
        wall.setGesturesEnabled(false);
        assertFalse(wall.isStatusGrabberShown());
        wall.setGesturesEnabled(true);

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        idle(200L);
        assertEquals(1f, wall.statusGrabber().emphasis(), EPS);
        assertEquals("the bottom one stays at rest", 0f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 104f,
            250L));
        assertTrue("drawn down after the finger", wall.statusGrabber().trackOffsetPx() > 0f);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 104f,
            280L));
        idle(300L);
        assertEquals(0f, wall.statusGrabber().emphasis(), EPS);
        assertEquals(0f, wall.statusGrabber().trackOffsetPx(), EPS);

        // A finger on the bottom band lights the keyboard's, not this one.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 1000L));
        idle(100L);
        assertEquals(0f, wall.statusGrabber().emphasis(), EPS);
        assertEquals(1f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f, 1100L));
    }

    @Test
    public void aPageChangeWithoutAFingerNeverTips() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // A tile, a key or wall.go slides the wall with no finger on it: no plank.
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, true));
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    // ---- The Display's stand-in (SurfacePage) ----------------------------------------------------

    /**
     * A Display page as the wall sees it: a page that can stand a still copy in for its surface.
     * The copy lands at once, or when the test says ({@link #land}); PixelCopy itself cannot run
     * on the JVM, and its lifecycle is {@link SurfaceStandInTest}'s.
     */
    private static final class FakeSurfacePage extends FrameLayout implements SurfacePage {
        boolean landsAtOnce = true;
        boolean still;
        @androidx.annotation.Nullable Runnable pending;
        int holds;
        int releases;
        int drops;

        FakeSurfacePage(android.content.Context context) {
            super(context);
        }

        @Override public void holdStill(Runnable onReady) {
            holds++;
            if (still || landsAtOnce) {
                still = true;
                onReady.run();
            } else {
                pending = onReady;
            }
        }

        /** The copy lands mid-motion. */
        void land() {
            still = true;
            Runnable ready = pending;
            pending = null;
            if (ready != null) ready.run();
        }

        @Override public boolean isStill() { return still; }

        @Override public void releaseStill() {
            releases++;
            still = false;
            pending = null;
        }

        @Override public void dropStill() {
            drops++;
            still = false;
            pending = null;
        }
    }

    /** {@link #buildWithContent}, with a Display page that can stand a copy in. */
    private FakeSurfacePage buildWithSurfaceDisplay() {
        buildWithContent();
        FakeSurfacePage page = new FakeSurfacePage(wall.getContext());
        wall.removeView(display);
        wall.addView(page);
        wall.setPageView(PaneWallPage.DISPLAY, page);
        display = page;
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        return page;
    }

    @Test
    public void theDisplaysStandInSinksOnALayerAndTheRestGivesTheSurfaceBack() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        showAndLayOut(PaneWallPage.DISPLAY);
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        assertTrue("the hold asked for the stand-in", page.holds > 0);
        assertEquals(display, wall.sinkPage());
        assertEquals(PageSink.SCALE, display.getScaleX(), EPS);
        assertEquals("the stand-in is a plain view: dimmed on a layer like any page",
            View.LAYER_TYPE_HARDWARE, display.getLayerType());
        assertEquals(0, page.releases);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f, 4f,
            3000L));
        letTheMotionSettle();
        assertNull(wall.sinkPage());
        assertEquals(1f, display.getScaleX(), EPS);
        assertEquals("the layer goes before the surface comes back", View.LAYER_TYPE_NONE,
            display.getLayerType());
        assertEquals("at rest, once", 1, page.releases);
        assertTrue(wall.stillPlaces().isEmpty());
    }

    @Test
    public void aCopyLandingMidSinkTakesTheLayerAndTheDimFromThere() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        page.landsAtOnce = false;
        showAndLayOut(PaneWallPage.DISPLAY);
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        letTheMotionSettle();
        // The copy is on its way: today's sink, a scale on the live surface and no layer.
        assertEquals(PageSink.SCALE, display.getScaleX(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
        page.land();
        assertEquals("snapped in where the sink stands", View.LAYER_TYPE_HARDWARE,
            display.getLayerType());
        assertEquals(PageSink.SCALE, display.getScaleX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f, 4f,
            3000L));
        letTheMotionSettle();
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
        assertEquals(1, page.releases);
    }

    @Test
    public void aCopyThatNeverLandsLeavesTheDisplaysMotionAsItWas() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        page.landsAtOnce = false;
        showAndLayOut(PaneWallPage.DISPLAY);
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage place) { return true; }
        });
        // The left border, pulled right: the held Display page would tip if it could.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 304f, HEIGHT / 2f,
            400L));
        assertFalse(wall.tiltPages().contains(display));
        assertEquals(0f, display.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
        assertTrue("the page arriving tips as ever", wall.tiltPages().contains(terminal));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 4f, HEIGHT / 2f,
            500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, HEIGHT / 2f,
            900L));
        letTheMotionSettle();
        assertEquals("rest still tells it, so a copy landing late is only kept", 1,
            page.releases);
    }

    @Test
    public void theDisplayArrivingOnItsKeptCopyTipsWithThePageLeaving() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        layOutEveryPage();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage place) { return true; }
        });
        // Held by the right border and pulled left: the Display page comes in from the right.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH - 4f,
            HEIGHT / 2f, 0L));
        letTheHoldElapse();
        assertTrue("the drag asked the page beside it too", page.holds > 0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 304f,
            HEIGHT / 2f, 400L));
        assertTrue(wall.tiltPages().contains(display));
        assertEquals(View.LAYER_TYPE_HARDWARE, display.getLayerType());
        assertTrue("it dips its left edge, at the finger", display.getRotationY() < -1f);
        assertEquals("one motion: the two tip alike", Math.abs(terminal.getRotationY()),
            Math.abs(display.getRotationY()), EPS);
        // Let go short: both lie flat, and the Display is told the wall is at rest.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 4f,
            HEIGHT / 2f, 500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH - 4f,
            HEIGHT / 2f, 900L));
        letTheMotionSettle();
        assertTrue(wall.tiltPages().isEmpty());
        assertEquals(0f, display.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
        assertEquals(1, page.releases);
    }

    @Test
    public void theDisplayArrivingWithNoCopyToHandSlidesInFlat() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        page.landsAtOnce = false;
        layOutEveryPage();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage place) { return true; }
        });
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH - 4f,
            HEIGHT / 2f, 0L));
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 304f,
            HEIGHT / 2f, 400L));
        assertTrue(terminal.getRotationY() > 0f);
        assertFalse(wall.tiltPages().contains(display));
        assertEquals(0f, display.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
    }

    @Test
    public void aSlideAsksOnlyForTheDisplayItCarries() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        wall.setReducedMotion(false);
        // Terminal to Widgets: the Display stays parked the whole way.
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, true));
        assertEquals(0, page.holds);
        letTheMotionSettle();
        assertEquals(0, page.releases);
        // Widgets to Display: it lands on the Display, which is asked, and told at rest.
        assertTrue(wall.goTo(PaneWallPage.DISPLAY, true));
        assertEquals(1, page.holds);
        letTheMotionSettle();
        assertEquals(1, page.releases);
        // And off it again: the Display leaving is on screen as the slide starts.
        assertTrue(wall.goTo(PaneWallPage.TERMINAL, true));
        assertEquals(2, page.holds);
        letTheMotionSettle();
        assertEquals(2, page.releases);
    }

    @Test
    public void reducedMotionAsksForNoStandIn() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        showAndLayOut(PaneWallPage.DISPLAY);
        // buildWithContent leaves motion reduced.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f, 4f,
            3000L));
        assertTrue(wall.goTo(PaneWallPage.TERMINAL, true));
        assertEquals(0, page.holds);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
    }

    @Test
    public void theDisplayLeavingTheWallDropsItsStandIn() {
        FakeSurfacePage page = buildWithSurfaceDisplay();
        wall.setPageView(PaneWallPage.DISPLAY, null);
        assertEquals(1, page.drops);
        assertTrue(wall.stillPlaces().isEmpty());
    }
}
