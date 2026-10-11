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

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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

    /**
     * The default Margin, 12 dp at the test's 1x density: every page sits this far inside the wall
     * on all four sides, as the activity lays it out, and the borders live in that air.
     */
    private static final int MARGIN = 12;

    /**
     * A point along the top and bottom edges clear of the boxes by the grabber pills, which are
     * centred on the page and {@link BorderDrag#PILL_SWIPE_WIDTH_DP} wide (px here), and clear of
     * the corner squares: where the page's own edge rows are the content's alone.
     */
    private static final float OFF_PILL_X = WIDTH / 2f + 200f;

    /** {@link BorderDrag#SIDE_REACH_DP} at the test's 1x density: px = dp. */
    private static final int SIDE_REACH = Math.round(BorderDrag.SIDE_REACH_DP);

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void theSideBordersKeepAStretchFromTheBackSwipe() {
        buildWithContent();
        // The side zones are the Margin of air beside the page and the side reach over its edge,
        // less the corner squares; the back swipe leaves 144dp of each to the wall, centred along
        // it (mdpi here: px = dp).
        int centre = HEIGHT / 2;
        List<android.graphics.Rect> expected = Arrays.asList(
            new android.graphics.Rect(0, centre - 72, MARGIN + SIDE_REACH, centre + 72),
            new android.graphics.Rect(WIDTH - MARGIN - SIDE_REACH, centre - 72, WIDTH,
                centre + 72));
        assertEquals(expected, wall.getSystemGestureExclusionRects());

        // With its gestures off the wall gives the whole edge back, and takes it again after.
        wall.setGesturesEnabled(false);
        assertTrue(wall.getSystemGestureExclusionRects().isEmpty());
        wall.setGesturesEnabled(true);
        assertEquals(expected, wall.getSystemGestureExclusionRects());

        // The stretches follow the page that is up. With the terminal's own frame air on, the
        // other places stand that much nearer the wall, so Display's zone is MARGIN - AIR wide;
        // and a page change re-lays nothing, so the change itself has to republish.
        final int air = 4;
        wall.setTerminalFrameAirPx(air);
        relayout();
        assertEquals(expected, wall.getSystemGestureExclusionRects());
        wall.goTo(PaneWallPage.DISPLAY, false);
        relayout();
        List<android.graphics.Rect> onDisplay = Arrays.asList(
            new android.graphics.Rect(0, centre - 72, MARGIN - air + SIDE_REACH, centre + 72),
            new android.graphics.Rect(WIDTH - MARGIN + air - SIDE_REACH, centre - 72, WIDTH,
                centre + 72));
        assertEquals(onDisplay, wall.getSystemGestureExclusionRects());
        // Back to the terminal: no layout runs, and the stretches still come back to its zone.
        wall.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(expected, wall.getSystemGestureExclusionRects());

        // A wall of one place has nowhere to page to, so the back swipe keeps the edge.
        wall.setPages(Collections.singletonList(PaneWallPage.TERMINAL));
        assertTrue(wall.getSystemGestureExclusionRects().isEmpty());
    }

    /** Lays the wall out again at its size, as the next frame would. */
    private void relayout() {
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    /** {@link #buildWithContent(int)} at the default {@link #MARGIN}. */
    private void buildWithContent() {
        buildWithContent(MARGIN);
    }

    /**
     * A wall whose terminal page holds content that takes every touch, like a terminal does, and
     * stands {@code marginPx} inside the wall on every side. At 0 the page is flush with the wall
     * and the border's minimum band reaches over the page's edge, where the content hears the DOWN.
     */
    private void buildWithContent(int marginPx) {
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
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        margins.setMargins(marginPx, marginPx, marginPx, marginPx);
        terminal.setLayoutParams(margins);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        assertEquals(marginPx, terminal.getTop());
        assertEquals(HEIGHT - marginPx, terminal.getBottom());
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
    public void aHeldBorderInTheGapDragsTheWallAndTheContentNeverHearsIt() {
        buildWithContent();
        // In the air above the page, away from the corners: the wall's own, and armed.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            MARGIN / 2f, 0L));
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 600f,
            40f, 400L));
        assertEquals(-600f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 600f,
            40f, 420L));
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertTrue("the page's content never heard the finger", content.actions.isEmpty());
    }

    /**
     * The page's edges are the content's at the default Margin, on every side and with every
     * border gesture on: a tap and a long press on the outermost pixel at the top and bottom, and
     * on the first pixel past the side reach on the left and right, reach the content whole, and,
     * away from the grabber pills, the wall never arms.
     */
    @Test
    public void aTapAndALongPressJustPastEachZoneReachTheContent() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        float density = wall.getResources().getDisplayMetrics().density;
        assertTrue("the default Margin leaves more air than the minimum band",
            MARGIN >= BorderDrag.MIN_BAND_DP * density);
        float[][] edges = {
            {OFF_PILL_X, terminal.getTop()}, {OFF_PILL_X, terminal.getBottom() - 1f},
            {terminal.getLeft() + SIDE_REACH, HEIGHT / 2f},
            {terminal.getRight() - SIDE_REACH - 1f, HEIGHT / 2f},
        };
        long t = 0L;
        for (float[] p : edges) {
            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, p[0], p[1], t));
            assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, p[0], p[1], t + 50L));
            assertEquals("a tap at " + p[0] + "," + p[1], Arrays.asList(
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP),
                content.actions);

            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, p[0], p[1],
                t + 1000L));
            letTheHoldElapse();
            assertFalse(wall.isDragging());
            assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, p[0], p[1],
                t + 2000L));
            assertEquals("a long press at " + p[0] + "," + p[1], Arrays.asList(
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP),
                content.actions);
            t += 5000L;
        }
        assertTrue(keyboardSwipes.isEmpty());
        assertTrue(statusSwipes.isEmpty());
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    /**
     * The side borders reach 2dp over the page's edge, into the clearance before its text: a hold
     * there takes the finger from the content and pages; 3dp in stays the content's.
     */
    @Test
    public void aHoldTwoDpInsideASideEdgePagesAndThreeDpInDoesNot() {
        buildWithContent();
        float[] within = {terminal.getLeft() + SIDE_REACH - 1f, terminal.getRight() - SIDE_REACH};
        long t = 0L;
        for (float x : within) {
            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, HEIGHT / 2f, t));
            assertEquals("the content hears the press first",
                Collections.singletonList(android.view.MotionEvent.ACTION_DOWN), content.actions);
            assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
            letTheHoldElapse();
            assertTrue("the hold at " + x + " pages", wall.isDragging());
            assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_CANCEL), content.actions);
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, HEIGHT / 2f,
                t + 2000L));
            assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
            t += 5000L;
        }
        float[] past = {terminal.getLeft() + 3f, terminal.getRight() - 4f};
        for (float x : past) {
            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, HEIGHT / 2f, t));
            assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
            letTheHoldElapse();
            assertFalse(wall.isDragging());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, HEIGHT / 2f,
                t + 2000L));
            assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_UP), content.actions);
            t += 5000L;
        }
    }

    @Test
    public void aHeldBorderTakesTheFingerFromTheContentAndDragsTheWall() {
        // Margin 0: the border's minimum band reaches over the page's edge.
        buildWithContent(0);
        // Down on the top border, away from the corners: the content gets it, as for a tap.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        // Margin 0, where the band reaches over the content's first row.
        buildWithContent(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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

        // A corner square, in the air beyond the page's corner: the corner tab's.
        content.actions.clear();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 1000L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, 4f, 1400L));
        assertFalse(wall.isDragging());
        // And at Margin 0, the page's own corner, under the band: the content's.
        buildWithContent(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 2000L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        letTheHoldElapse();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, 4f, 2400L));
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
        assertTrue("and reaches no content", content.actions.isEmpty());
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

    // ---- The slide off the top and bottom borders ---------------------------------------------

    @Test
    public void aSlideOffTheBottomAirPagesAtOnceAndTheContentNeverHearsIt() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        float y = HEIGHT - MARGIN / 2f;
        float x = WIDTH / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, 0L));
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        // Sideways past the slop, long before the hold: the wall's at once.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 100f, y, 50L));
        assertEquals(BorderDrag.Claim.PAGING, wall.borderDragClaim());
        assertTrue(wall.isDragging());
        assertEquals("dragged from where it was claimed", 0f, terminal.getTranslationX(), EPS);
        // From there the wall follows the finger across, whatever it does up or down.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 700f, y - 300f,
            100L));
        assertEquals(-600f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x - 700f, y - 300f,
            120L));
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertFalse(wall.isDragging());
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        assertTrue("the page's content never heard the finger", content.actions.isEmpty());
        assertTrue("nor the keyboard", keyboardSwipes.isEmpty());
        assertTrue("nor the bar", foldBegins.isEmpty() && statusSwipes.isEmpty());
        letTheHoldElapse();
        assertFalse("the hold's timer went with the claim", wall.isDragging());
    }

    @Test
    public void aSlideOffTheTopAirPagesTheOtherWayAndAShortOneSpringsBack() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        float y = MARGIN / 2f;
        float x = WIDTH / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x + 100f, y, 50L));
        assertEquals(BorderDrag.Claim.PAGING, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x + 700f, y, 100L));
        assertEquals(600f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x + 700f, y, 120L));
        assertEquals("pulled right: the place on the left", PaneWallPage.WIDGETS,
            wall.currentPage());

        // Short and slow, by the same rules a held drag lands by: back where it was.
        wall.goTo(PaneWallPage.TERMINAL, false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, 1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 100f, y, 1050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 150f, y, 2000L));
        assertEquals(-50f, terminal.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 150f, y, 3000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x - 150f, y, 4000L));
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertTrue(statusSwipes.isEmpty());
    }

    @Test
    public void aSidewaysStartFromASideBorderStillWaitsForTheHold() {
        buildWithContent();
        float[] xs = {4f, WIDTH - 4f};
        long t = 0L;
        for (float x : xs) {
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, HEIGHT / 2f, t));
            assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
            float to = x < WIDTH / 2f ? x + 200f : x - 200f;
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, to, HEIGHT / 2f,
                t + 50L));
            assertEquals("the back swipe's edge: given up", BorderDrag.Claim.ABANDONED,
                wall.borderDragClaim());
            assertFalse(wall.isDragging());
            letTheHoldElapse();
            assertFalse(wall.isDragging());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, to, HEIGHT / 2f,
                t + 1000L));
            assertEquals(0f, terminal.getTranslationX(), EPS);
            t += 5000L;
        }
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void aWallOfOnePlaceNeverSlidesButStillSwipes() {
        buildWithContent();
        wall.setPages(Collections.singletonList(PaneWallPage.TERMINAL));
        listenForKeyboardSwipes();
        float y = HEIGHT - MARGIN / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 200f, y,
            50L));
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        assertFalse(wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 200f, y,
            80L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, y - 200f,
            1050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y - 200f,
            1080L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
    }

    @Test
    public void aSlideTipsThePlankAndSinksThePageAsAHoldDoes() {
        buildWithContent();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        float y = HEIGHT - MARGIN / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        assertNull(wall.sinkPage());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 100f, y,
            50L));
        assertTrue(wall.isDragging());
        assertTrue(wall.tiltPages().contains(terminal));
        assertEquals(terminal, wall.sinkPage());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 100f, y,
            2000L));
        letTheMotionSettle();
        assertNull("the lift springs it back", wall.sinkPage());
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        // Margin 0: the band reaches over the last row, so the content heard the DOWN.
        buildWithContent(0);
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
        // Margin 0, where the band reaches over the content's last row.
        buildWithContent(0);
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
    public void aVerticalSwipeFromThePagesLastRowStaysTheContents() {
        buildWithContent();
        listenForKeyboardSwipes();
        // The page's own last pixel row, right against the border's air, away from the pill.
        float y = terminal.getBottom() - 1f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X, y - 200f,
            50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X, y - 200f,
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
        // Margin 0, where the band reaches over the content's last row.
        buildWithContent(0);
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f,
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
        // The last row's text, on the page's own last pixel row, away from the pill.
        float frame = HEIGHT - AIR_BELOW;
        float y = frame - 1f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y, 0L));
        letTheHoldElapse();
        assertFalse("the hold never pages from the text", wall.isDragging());
        assertEquals("nothing arms on the page", BorderDrag.Claim.NONE, wall.borderDragClaim());
        // The long press goes on to a selection drag, the content's to the lift.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X - 200f,
            y - 200f, 500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X - 200f,
            y - 200f, 520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());

        // A swipe up from the same row is the content's scroll, never the keyboard's.
        content.actions.clear();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y,
            1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X, y - 200f,
            1050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X, y - 200f,
            1080L));
        assertTrue(keyboardSwipes.isEmpty());
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        // One pixel lower, on the border's air, it is.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, frame,
            2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X,
            frame - 200f, 2050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X,
            frame - 200f, 2080L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
    }

    @Test
    public void aHoldDeepInTheAirBelowTheFramePages() {
        buildWithAirBelow();
        listenForKeyboardSwipes();
        float density = wall.getResources().getDisplayMetrics().density;
        float y = HEIGHT - 4f;
        assertTrue("the press is far below the line",
            y - (HEIGHT - AIR_BELOW) > 10f * BorderDrag.MIN_BAND_DP * density);
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
    public void theLineInTheTerminalsOwnAirIsTheBorderAndThePageIsNot() {
        // Margin 40, and the terminal's own margins carry its border's 3 of air on top: the line
        // is drawn 40 in from the wall, in the air outside the terminal page.
        buildWithContent();
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        margins.setMargins(43, 43, 43, 43);
        terminal.setLayoutParams(margins);
        wall.setTerminalFrameAirPx(3);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);

        // The page's last pixel row: the content's.
        float lastRow = terminal.getBottom() - 1f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, lastRow,
            0L));
        letTheHoldElapse();
        assertFalse("a hold on the last row is the content's", wall.isDragging());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, lastRow,
            520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_UP), content.actions);

        // On the drawn line the hold pages.
        float line = HEIGHT - 40f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, line,
            1000L));
        letTheHoldElapse();
        assertTrue("a hold on the line pages", wall.isDragging());
    }

    @Test
    public void withGesturesOffTheBottomBorderIsTheContents() {
        // Margin 0, where the band would reach over the content's last row.
        buildWithContent(0);
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f,
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
    public void aHoldOnTheFirstTextRowIsTheContentsLongPress() {
        buildWithContent();
        listenForStatusSwipes(0);
        // The first row's text, on the page's own first pixel row, away from the pill.
        float y = terminal.getTop();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y, 0L));
        letTheHoldElapse();
        assertFalse("the hold never pages from the text", wall.isDragging());
        assertEquals("nothing arms on the page", BorderDrag.Claim.NONE, wall.borderDragClaim());
        // The long press goes on to a selection drag, the content's to the lift.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X - 200f,
            y + 200f, 500L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X - 200f,
            y + 200f, 520L));
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        assertTrue(statusSwipes.isEmpty());
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());

        // A swipe down from the same row is the content's scroll, never the status bar's.
        content.actions.clear();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y,
            1000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X, y + 200f,
            1050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X, y + 200f,
            1080L));
        assertTrue(statusSwipes.isEmpty());
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
            content.actions);
        // One pixel higher, on the border's air, it is.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, OFF_PILL_X, y - 1f,
            2000L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, OFF_PILL_X, y + 200f,
            2050L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, OFF_PILL_X, y + 200f,
            2080L));
        assertEquals(Collections.singletonList(true), statusSwipes);
    }

    @Test
    public void aDownSwipeOffTheTopBorderUnfoldsTheBarAndCancelsTheContent() {
        // Margin 0: the band reaches over the first row, so the content heard the DOWN.
        buildWithContent(0);
        listenForStatusSwipes(0);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 204f,
            50L));
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, 204f, 80L));

        // With it on, a hold on the top border is still the wall's.
        listenForStatusSwipes(FOLD);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f,
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, 4f + 120f,
            50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_CANCEL, WIDTH / 2f,
            4f + 120f, 60L));
        assertEquals(Collections.singletonList(false), foldEnds);
        assertFalse(wall.isStatusFoldEngaged());

        // The wall moved by something else takes the fold back as well.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f,
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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

    // ---- The swipes by the grabber pills, a row inside the page ------------------------------

    /** How far into the page the box by each pill reaches, in px. */
    private float pillReach() {
        return BorderDrag.PILL_SWIPE_REACH_DP * wall.getResources().getDisplayMetrics().density;
    }

    /**
     * By either pill, inside the page: a tap, a long press that goes on to a selection drag, and
     * a sideways drag all reach the content whole, and nothing pages or swipes.
     */
    @Test
    public void aTapALongPressAndASidewaysDragByThePillsReachTheContent() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        float reach = pillReach();
        float x = WIDTH / 2f;
        float[] ys = {terminal.getBottom() - 1f, terminal.getBottom() - reach, terminal.getTop(),
            terminal.getTop() + reach - 1f};
        long t = 0L;
        for (float y : ys) {
            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t));
            assertEquals("armed for the swipe at " + y, BorderDrag.Claim.PENDING,
                wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, y, t + 50L));
            assertEquals("a tap at " + y, Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_UP), content.actions);

            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t + 1000L));
            letTheHoldElapse();
            assertFalse("the hold never pages from the page", wall.isDragging());
            assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x, y - 200f,
                t + 1500L));
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, y - 200f,
                t + 1520L));
            assertEquals("a long press and its drag at " + y, Arrays.asList(
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE,
                android.view.MotionEvent.ACTION_UP), content.actions);

            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t + 3000L));
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x + 100f, y,
                t + 3050L));
            assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x + 150f,
                y - 200f, t + 3070L));
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x + 150f, y - 200f,
                t + 3090L));
            assertEquals("a sideways drag at " + y, Arrays.asList(
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE,
                android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
                content.actions);
            t += 5000L;
        }
        assertTrue(keyboardSwipes.isEmpty());
        assertTrue(statusSwipes.isEmpty());
        assertTrue(foldBegins.isEmpty());
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void anUpSwipeByTheBottomPillOpensTheKeyboardAndCancelsTheContent() {
        buildWithContent();
        listenForKeyboardSwipes();
        float y = terminal.getBottom() - pillReach() / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        assertEquals("the content hears the press", Collections.singletonList(
            android.view.MotionEvent.ACTION_DOWN), content.actions);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, y - 200f,
            50L));
        assertEquals(BorderDrag.Claim.KEYBOARD, wall.borderDragClaim());
        assertEquals("claimed at once: the content forgets the touch", Arrays.asList(
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_CANCEL),
            content.actions);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y - 200f,
            80L));
        assertEquals(Collections.singletonList(true), keyboardSwipes);
        assertEquals("and hears nothing after", 2, content.actions.size());
        letTheHoldElapse();
        assertFalse(wall.isDragging());
    }

    @Test
    public void aDownSwipeByTheTopPillUnfoldsTheBarAndCancelsTheContent() {
        buildWithContent();
        listenForStatusSwipes(0);
        float y = terminal.getTop() + pillReach() / 2f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f, y + 200f,
            50L));
        assertEquals(BorderDrag.Claim.STATUS, wall.borderDragClaim());
        assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_CANCEL), content.actions);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y + 200f,
            80L));
        assertEquals(Collections.singletonList(true), statusSwipes);
        assertTrue("never the keyboard's", keyboardSwipes.isEmpty());
    }

    @Test
    public void justOutsideThePillsBoxAVerticalSwipeStaysTheContents() {
        buildWithContent();
        listenForKeyboardSwipes();
        float density = wall.getResources().getDisplayMetrics().density;
        float halfWidth = BorderDrag.PILL_SWIPE_WIDTH_DP * density / 2f;
        float[][] presses = {
            {WIDTH / 2f + halfWidth, terminal.getBottom() - 1f},
            {WIDTH / 2f - halfWidth - 1f, terminal.getBottom() - 1f},
            {WIDTH / 2f, terminal.getBottom() - pillReach() - 1f},
        };
        long t = 0L;
        for (float[] p : presses) {
            content.actions.clear();
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, p[0], p[1], t));
            assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, p[0], p[1] - 200f,
                t + 50L));
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, p[0], p[1] - 200f,
                t + 80L));
            assertEquals(Arrays.asList(android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP),
                content.actions);
            t += 1000L;
        }
        assertTrue(keyboardSwipes.isEmpty());
    }

    @Test
    public void withTheSwipesOffThePillsBoxesAreTheContents() {
        buildWithContent();
        // A listener with neither swipe: the wall still pages, but has no pills.
        wall.setListener(new PaneWallLayout.Listener() { });
        float y = terminal.getBottom() - 1f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, y, 0L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f, y, 50L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            terminal.getTop(), 1000L));
        assertEquals(BorderDrag.Claim.NONE, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            terminal.getTop(), 1050L));
    }

    @Test
    public void aFingerByAPillLightsItsGrabberUntilTheHoldGivesItUp() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForStatusSwipes(FOLD);
        float reach = pillReach();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            terminal.getBottom() - reach / 2f, 0L));
        idle(200L);
        assertEquals(1f, wall.grabber().emphasis(), EPS);
        assertEquals(0f, wall.statusGrabber().emphasis(), EPS);
        // Held still through the hold: the content's long press, and the grabber goes back.
        letTheHoldElapse();
        idle(300L);
        assertEquals(0f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            terminal.getBottom() - reach / 2f, 1000L));

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            terminal.getTop() + reach / 2f, 2000L));
        idle(200L);
        assertEquals(1f, wall.statusGrabber().emphasis(), EPS);
        assertEquals(0f, wall.grabber().emphasis(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            terminal.getTop() + reach / 2f, 2100L));
        idle(300L);
        assertEquals(0f, wall.statusGrabber().emphasis(), EPS);
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 2f, 0L));
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

    // ---- The hold's glow -----------------------------------------------------------------------

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }

    @Test
    public void aHoldThatCanPageLightsItsBorderAtOnceAndFillsUntilItTakes() {
        buildWithContent();
        wall.setReducedMotion(false);
        BorderGlow glow = wall.borderGlow();
        assertFalse(glow.isShowing());
        // In the air beside the page: the left border, whose hold pages.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        assertTrue(glow.isHeld());
        assertEquals(BorderDrag.Border.LEFT, glow.side());
        float first = glow.level(now());
        assertTrue("lit at once", first > 0f && first < 1f);
        idle(com.termux.view.HoldTiming.holdTimeoutMs() / 2);
        assertTrue("brighter as the hold runs", glow.level(now()) > first);
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        assertEquals("full at the claim", 1f, glow.level(now()), EPS);
        assertFalse("and still", glow.needsFrame(now()));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 204f, HEIGHT / 2f,
            900L));
        assertEquals("full through the drag", 1f, glow.level(now()), EPS);
        // The drag ends: it fades, and asks for nothing once it has.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 204f, HEIGHT / 2f,
            950L));
        assertFalse(glow.isHeld());
        idle(BorderGlow.FADE_MS + 20L);
        assertEquals(0f, glow.level(now()), EPS);
        assertFalse(glow.needsFrame(now()));
    }

    @Test
    public void theTopAndBottomAirLightFullAtTheTouchAndASwipeHandsTheGlowToItsGrabber() {
        buildWithContent();
        wall.setReducedMotion(false);
        listenForStatusSwipes(0);
        BorderGlow glow = wall.borderGlow();
        float[][] presses = {{WIDTH / 2f, HEIGHT - MARGIN / 2f}, {WIDTH / 2f, MARGIN / 2f}};
        BorderDrag.Border[] sides = {BorderDrag.Border.BOTTOM, BorderDrag.Border.TOP};
        long t = 0L;
        for (int i = 0; i < presses.length; i++) {
            float x = presses[i][0];
            float y = presses[i][1];
            // Held still: full from the touch, with no rise, and the hold still pages.
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t));
            assertTrue(glow.isHeld());
            assertEquals(sides[i], glow.side());
            assertEquals("full at once", 1f, glow.level(now()), EPS);
            assertFalse(glow.needsFrame(now()));
            letTheHoldElapse();
            assertTrue("the hold still pages", wall.isDragging());
            assertEquals(1f, glow.level(now()), EPS);
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, y, t + 1000L));
            assertFalse(glow.isHeld());
            letTheMotionSettle();

            // Slid sideways: still full through the drag.
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t + 3000L));
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x - 100f, y,
                t + 3050L));
            assertTrue(wall.isDragging());
            assertTrue(glow.isHeld());
            assertEquals(1f, glow.level(now()), EPS);
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x - 100f, y,
                t + 4000L));
            letTheMotionSettle();

            // Up or down: the swipe's grabber takes over and the glow fades.
            float toward = sides[i] == BorderDrag.Border.BOTTOM ? y - 200f : y + 200f;
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, y, t + 6000L));
            assertTrue(glow.isHeld());
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x, toward,
                t + 6050L));
            assertEquals(sides[i] == BorderDrag.Border.BOTTOM ? BorderDrag.Claim.KEYBOARD
                : BorderDrag.Claim.STATUS, wall.borderDragClaim());
            assertFalse(glow.isHeld());
            idle(BorderGlow.FADE_MS + 20L);
            assertEquals(0f, glow.level(now()), EPS);
            wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, toward,
                t + 6100L));
            letTheMotionSettle();
            t += 10000L;
        }
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test
    public void theBandInsideThePageStillRisesOverTheHold() {
        // Margin 0: the band reaches over the content's first and last rows.
        buildWithContent(0);
        wall.setReducedMotion(false);
        BorderGlow glow = wall.borderGlow();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 2f, 0L));
        assertTrue(glow.isHeld());
        float first = glow.level(now());
        assertTrue("lit, but not full", first > 0f && first < 1f);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 2f, 50L));
    }

    @Test
    public void aPressGivenUpBeforeTheHoldFadesItsGlowOut() {
        buildWithContent();
        wall.setReducedMotion(false);
        BorderGlow glow = wall.borderGlow();
        // Set off sideways past the slop from a side border, where only the hold pages.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        assertTrue(glow.isHeld());
        assertEquals(BorderDrag.Border.LEFT, glow.side());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 204f, HEIGHT / 2f,
            50L));
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        assertFalse(glow.isHeld());
        idle(BorderGlow.FADE_MS + 20L);
        assertEquals(0f, glow.level(now()), EPS);
        assertFalse(glow.needsFrame(now()));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 204f, HEIGHT / 2f,
            400L));

        // Lifted before the hold.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f,
            1000L));
        assertTrue(glow.isHeld());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, HEIGHT / 2f, 1050L));
        assertFalse(glow.isHeld());
        idle(BorderGlow.FADE_MS + 20L);
        assertEquals(0f, glow.level(now()), EPS);

        // Inside the side reach, set off into the content: a scroll the content keeps.
        float x = terminal.getLeft() + SIDE_REACH - 1f;
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, x, HEIGHT / 2f, 2000L));
        assertTrue(glow.isHeld());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, x, HEIGHT / 2f + 200f,
            2050L));
        assertFalse(glow.isHeld());
        idle(BorderGlow.FADE_MS + 20L);
        assertEquals(0f, glow.level(now()), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, x, HEIGHT / 2f + 200f,
            2100L));
    }

    @Test
    public void aWallMovedFromUnderAHeldBorderPutsItsGlowOut() {
        buildWithContent();
        BorderGlow glow = wall.borderGlow();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        assertTrue(glow.isHeld());
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertFalse(glow.isHeld());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, HEIGHT / 2f, 400L));
    }

    @Test
    public void aPressThatCanOnlySwipeHasNoGlow() {
        buildWithContent();
        listenForStatusSwipes(FOLD);
        BorderGlow glow = wall.borderGlow();
        // By the keyboard's pill, a row into the page: its swipe only.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            terminal.getBottom() - 1f, 0L));
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        assertFalse(glow.isShowing());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            terminal.getBottom() - 1f, 50L));

        // A wall of one place: the bottom border carries the keyboard's swipe and nothing else.
        wall.setPages(Collections.singletonList(PaneWallPage.TERMINAL));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 1000L));
        assertEquals(BorderDrag.Claim.PENDING, wall.borderDragClaim());
        assertFalse(glow.isShowing());
        letTheHoldElapse();
        assertFalse(glow.isShowing());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f,
            HEIGHT - 4f, 2000L));
    }

    @Test
    public void underReducedMotionTheGlowIsFullAtOnceAndGoneAtOnce() {
        buildWithContent();
        BorderGlow glow = wall.borderGlow();
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 4f, HEIGHT / 2f, 0L));
        assertEquals("no ramp", 1f, glow.level(now()), EPS);
        assertFalse(glow.needsFrame(now()));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 4f, HEIGHT / 2f + 200f,
            50L));
        assertFalse("no fade", glow.isShowing());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 4f, HEIGHT / 2f + 200f,
            100L));
    }
}
