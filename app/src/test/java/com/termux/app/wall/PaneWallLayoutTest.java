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
    public void aPageChangeUnderALiveDragEndsTheDragAndSaysSo() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        int[] interrupted = {0};
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onWallDragInterrupted() { interrupted[0]++; }
        });
        wall.beginDrag();
        wall.dragTo(-200f);

        // A tile tap, wall.go or Home lands mid-drag.
        wall.goTo(PaneWallPage.DISPLAY, false);

        assertEquals("the claimant is told once", 1, interrupted[0]);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertEquals(0f, display.getTranslationX(), EPS);
        // The rest of that finger is nobody's: the wall neither moves for it nor settles on it.
        wall.dragTo(300f);
        assertEquals(0f, display.getTranslationX(), EPS);
        wall.endDrag(-10_000f);
        assertEquals(PaneWallPage.DISPLAY, wall.currentPage());
        assertEquals(1, interrupted[0]);
    }

    @Test
    public void aPageChangeWithNoDragUnderWayInterruptsNothing() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        int[] interrupted = {0};
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onWallDragInterrupted() { interrupted[0]++; }
        });

        wall.goTo(PaneWallPage.WIDGETS, false);
        wall.beginDrag();
        wall.endDrag(0f);
        wall.goTo(PaneWallPage.TERMINAL, false);

        assertEquals(0, interrupted[0]);
    }

    @Test
    public void switchingGesturesOffUnderALiveDragInterruptsIt() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        int[] interrupted = {0};
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onWallDragInterrupted() { interrupted[0]++; }
        });
        wall.beginDrag();
        wall.dragTo(-200f);

        wall.setGesturesEnabled(false);

        assertEquals(1, interrupted[0]);
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
     * The plank (PlankTilt): a held border's drag tips the page the wall rests on when the
     * listener allows it, by the angle its position says, on a hardware layer for the length of
     * the motion; the settle lays it flat and drops the layer. The page arriving is never tilted,
     * and a drag taken from outside (the window strip's overswipe) slides flat.
     */
    @Test
    public void aDragTipsThePlankAndTheSettleLaysItFlat() {
        buildWithContent();
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // The window strip's overswipe: the same leave, but nothing tips.
        wall.beginDrag();
        assertNull(wall.tiltPage());
        assertTrue(wall.goTo(PaneWallPage.TERMINAL, false));

        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        assertEquals(terminal, wall.tiltPage());
        assertEquals(View.LAYER_TYPE_HARDWARE, terminal.getLayerType());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            40f, 400L));
        assertEquals(PlankTilt.angleDeg(-300f, WIDTH), terminal.getRotationY(), EPS);
        assertTrue("dragged left, the left edge goes in", terminal.getRotationY() < 0f);
        assertEquals(0f, display.getRotationY(), EPS);
        // A jump lands the wall at rest: flat, and the layer gone.
        assertTrue(wall.goTo(PaneWallPage.DISPLAY, false));
        assertNull(wall.tiltPage());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    @Test
    public void thePlankNeedsTheListenersLeaveAndThePhoneAnimating() {
        // The default listener gives no leave: the drag moves the page and nothing tips.
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertNull(wall.tiltPage());
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
        assertNull(wall.tiltPage());
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
            HEIGHT - 4f, 0L));
        letTheHoldElapse();
        assertTrue(wall.isDragging());
        // A key or wall.go lands mid-drag.
        wall.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(BorderDrag.Claim.ABANDONED, wall.borderDragClaim());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            HEIGHT - 4f, 400L));
        assertEquals("the rest of the finger moves nothing", 0f, widgets.getTranslationX(), EPS);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH / 2f - 300f,
            HEIGHT - 4f, 420L));
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
    public void theBorderDragEngagesThePlankAndAnOutsideDragDoesNot() {
        buildWithContent();
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // The window strip's overswipe: the page slides flat.
        wall.beginDrag();
        wall.dragTo(-300f);
        assertNull(wall.tiltPage());
        assertEquals(0f, terminal.getRotationY(), EPS);
        wall.cancelDrag();
        assertEquals(0f, terminal.getTranslationX(), EPS);

        // A held border: the page tips under the finger.
        wall.setReducedMotion(false);
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f, 4f, 0L));
        letTheHoldElapse();
        assertEquals(terminal, wall.tiltPage());
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH / 2f - 300f,
            4f, 400L));
        assertEquals(PlankTilt.angleDeg(-300f, WIDTH), terminal.getRotationY(), EPS);
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
        assertNull(wall.tiltPage());
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
        assertEquals(terminal, wall.tiltPage());
        assertEquals(PlankTilt.HOLD_TILT_DEG, terminal.getRotationY(), EPS);
        assertTrue("the right edge goes in", terminal.getRotationY() > 0f);
        // Dragged, the travel's tip takes over, deeper than the old twelve degrees half way out.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 4f - WIDTH / 2f,
            HEIGHT / 2f, 3000L));
        assertEquals(PlankTilt.angleDeg(-WIDTH / 2f, WIDTH, 1, 1f), terminal.getRotationY(), EPS);
        assertTrue(Math.abs(terminal.getRotationY()) > 12f);
        // Pulled back to rest and let go: flat, full size, the layer gone once the spring lands.
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, WIDTH - 4f,
            HEIGHT / 2f, 3100L));
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, WIDTH - 4f,
            HEIGHT / 2f, 3400L));
        letTheMotionSettle();
        assertNull(wall.sinkPage());
        assertNull(wall.tiltPage());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(1f, terminal.getScaleX(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }

    @Test
    public void theDisplayPageSinksWithoutALayer() {
        buildWithContent();
        assertTrue(wall.goTo(PaneWallPage.DISPLAY, false));
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
        assertNull(wall.tiltPage());
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
        wall.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, WIDTH / 2f,
            HEIGHT - 4f, 0L));
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

    @Test
    public void aPageChangeWithoutAFingerNeverTips() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        wall.setReducedMotion(false);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean isPlankTiltEnabled(PaneWallPage page) { return true; }
        });
        // A tile, a key or wall.go slides the wall with no finger on it: no plank.
        assertTrue(wall.goTo(PaneWallPage.WIDGETS, true));
        assertNull(wall.tiltPage());
        assertEquals(0f, terminal.getRotationY(), EPS);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
    }
}
