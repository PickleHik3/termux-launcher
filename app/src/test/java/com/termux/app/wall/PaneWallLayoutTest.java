package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals(View.INVISIBLE, widgets.getVisibility());
        assertEquals(View.INVISIBLE, display.getVisibility());
    }

    @Test
    public void everyPageIsLaidOutAtTheHostsSize() {
        build(Robolectric.buildActivity(Activity.class).setup().get(), true, true);
        for (View page : new View[]{widgets, terminal, display}) {
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

        for (View page : new View[]{widgets, terminal, display}) {
            assertEquals(24, page.getLeft());
            assertEquals(10, page.getTop());
            assertEquals(WIDTH - 24, page.getRight());
            assertEquals(HEIGHT - 30, page.getBottom());
        }
        // The pages still sit one full wall width apart, frame and all.
        assertEquals(-WIDTH, widgets.getTranslationX(), EPS);
        assertEquals(WIDTH, display.getTranslationX(), EPS);
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
}
