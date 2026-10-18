package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * The controller's jobs on the wall's row (ADR 0008): which pages exist (Home always, the
 * Workspace while there is a window, the display window while a display is up), the seam both
 * ways, where the home press and a cold start land, and keeping the row position across an
 * activity recreate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneWallControllerTest {

    /** A host with a number of Home pages and windows, each with one in front. */
    private static final class Host implements PaneWallController.Host {
        boolean reduced = true;
        boolean hasWindow = true;
        boolean display = true;
        int homePages = 3;
        int homePage;
        int windows = 3;
        int current;
        Boolean terminalOffScreen;
        int stepBegun;
        int stepped;
        float steppedFrom;
        float homeShift;

        @Override public boolean reducedMotion() { return reduced; }
        @Override public float innerShiftFraction(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homeShift : 0f;
        }
        @Override public boolean isPlankTiltEnabled() { return !reduced; }
        @Override public boolean hasWindow() { return hasWindow; }
        @Override public boolean isDisplayUp() { return display; }
        @Override public int innerCount(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homePages
                : page == PaneWallPage.TERMINAL ? (hasWindow ? windows : 0) : 1;
        }
        @Override public int innerIndex(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homePage
                : page == PaneWallPage.TERMINAL ? current : 0;
        }
        @Override public void showInnerPage(PaneWallPage page, int index) {
            if (page == PaneWallPage.WIDGETS) homePage = index;
            if (page == PaneWallPage.TERMINAL) current = index;
        }
        @Override public void onInnerStepBegin(PaneWallPage page, int steps) { stepBegun = steps; }
        @Override public void onInnerStep(PaneWallPage page, int steps, float fromPx) {
            stepped = steps;
            steppedFrom = fromPx;
            current += steps;
        }
        @Override public void onTerminalOffScreenChanged(boolean offScreen) {
            terminalOffScreen = offScreen;
        }
    }

    private Activity activity;
    private PaneWallLayout wall;
    private Host host;
    private PaneWallController controller;

    @Before public void build() {
        // A recycled velocity tracker can carry an earlier test's movements under Robolectric.
        FreshVelocityTrackers.drainPool();
        activity =Robolectric.buildActivity(Activity.class).setup().get();
        host = new Host();
        controller = newController(host);
        wall = controller.wall();
    }

    private PaneWallController newController(Host withHost) {
        PaneWallLayout layout = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        View widgets = new FrameLayout(activity);
        layout.addView(widgets);
        layout.addView(terminal);
        PaneWallController made = new PaneWallController(layout, withHost);
        made.attachTerminalPage(terminal);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 1800);
        return made;
    }

    @Test public void theWallsTerminalOffScreenSignalReachesTheHost() {
        assertEquals("the first layout reports the terminal on screen", Boolean.FALSE,
            host.terminalOffScreen);
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(Boolean.TRUE, host.terminalOffScreen);
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(Boolean.FALSE, host.terminalOffScreen);
    }

    @Test public void theRowIsHomeThenTheWorkspaceThenTheDisplay() {
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            controller.pages());

        host.display = false;
        controller.refreshPages();
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL), controller.pages());

        // No window: the Workspace leaves the row, and Home is all there is.
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals(Collections.singletonList(PaneWallPage.WIDGETS), controller.pages());
    }

    @Test public void thePagesAreALineNotARing() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertTrue(controller.goTo("left"));
        assertEquals("nothing is left of Home page 1", PaneWallPage.WIDGETS,
            controller.currentPage());
        assertTrue(controller.goTo("display"));
        assertTrue(controller.goTo("right"));
        assertEquals("nothing is right of the Display page", PaneWallPage.DISPLAY,
            controller.currentPage());
        assertTrue(controller.goTo("left"));
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertTrue(controller.goTo("home"));
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertFalse(controller.goTo("nowhere"));
        controller.returnToTerminal(false);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void theDisplayGoingDownLandsOnTheLastWindow() {
        host.current = 0;
        assertTrue(controller.goTo(PaneWallPage.DISPLAY, false));
        host.display = false;
        controller.refreshPages();
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertEquals("the window next to where the display stood", 2, host.current);
    }

    @Test public void theDisplayGoingDownWithNoWindowLandsOnTheLastHomePage() {
        host.hasWindow = false;
        controller.refreshPages();
        assertTrue(controller.goTo(PaneWallPage.DISPLAY, false));
        host.homePage = 0;
        host.display = false;
        controller.refreshPages();
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(2, host.homePage);
    }

    @Test public void theLastWindowClosingLandsOnTheLastHomePage() {
        host.display = false;
        controller.refreshPages();
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.homePage = 0;
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(2, host.homePage);
        assertFalse(controller.isTerminalShowing());
    }

    @Test public void aPageLeavingElsewhereLeavesTheWallWhereItIs() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        host.display = false;
        controller.refreshPages();
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertEquals(1, host.current);
    }

    @Test public void theSeamLeavesHomeOnlyWhenTheRowGoesOn() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertTrue(controller.homeSeam().canLeaveForward());

        host.hasWindow = false;
        controller.refreshPages();
        assertTrue("the Display page still follows Home", controller.homeSeam().canLeaveForward());

        host.display = false;
        controller.refreshPages();
        assertFalse("no window and no display: the swipe stops on the last Home page",
            controller.homeSeam().canLeaveForward());
    }

    @Test public void theSeamFromHomeReachesTheFirstWindow() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.current = 2;
        controller.homeSeam().begin();
        assertEquals("the Workspace turns to window 1 before it slides in", 0, host.current);
        controller.homeSeam().drag(-900f);
        controller.homeSeam().end(-3000f);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void aDragOffTheDisplayPageReachesTheLastWindow() {
        controller.goTo(PaneWallPage.DISPLAY, false);
        host.current = 0;
        wall.beginPlainDrag();
        assertEquals(2, host.current);
        wall.dragTo(900f);
        wall.endDrag(3000f);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void aBorderDragBetweenWindowsStepsInsideTheWorkspace() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        wall.beginPlainDrag();
        wall.dragTo(-600f);
        wall.endDrag(-3000f);
        assertEquals("the wall stays on the Workspace", PaneWallPage.TERMINAL,
            controller.currentPage());
        assertEquals(1, host.stepBegun);
        assertEquals(1, host.stepped);
        assertEquals("the next window comes in from where the drag left it", 480f,
            host.steppedFrom, 0.5f);
        assertEquals(2, host.current);
        assertEquals("and the wall is at rest", 0f, wall.offsetPx(), 0f);
    }

    @Test public void aBorderDragOffTheFirstWindowReachesHome() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 0;
        wall.beginPlainDrag();
        wall.dragTo(700f);
        wall.endDrag(3000f);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.stepped);
    }

    @Test public void aHomePageHasNoBorderToPageBy() {
        assertFalse(controller.isBorderPagingEnabled(PaneWallPage.WIDGETS));
        assertTrue(controller.isBorderPagingEnabled(PaneWallPage.TERMINAL));
        assertTrue(controller.isBorderPagingEnabled(PaneWallPage.DISPLAY));
        assertFalse("Home's pages page inside the grid, never by the wall",
            controller.hasInnerPage(PaneWallPage.WIDGETS, 1));
    }

    @Test public void theHomePressLandsOnHomePageOne() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.homePage = 2;
        controller.goHome(false);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.homePage);
    }

    @Test public void theLastWindowClosingShowsTheLastHomePage() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.homePage = 0;
        controller.goToLastHomePage(false);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(2, host.homePage);
    }

    @Test public void aColdStartFollowsStartIn() {
        controller.goTo(PaneWallPage.DISPLAY, false);
        host.homePage = 1;
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.homePage);

        controller.landOnColdStart(WallRow.START_IN_WORKSPACE);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());

        controller.landOnColdStart("nonsense");
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
    }

    @Test public void aWorkspaceStartWaitsForItsWindow() {
        Host empty = new Host();
        empty.hasWindow = false;
        PaneWallController cold = newController(empty);
        cold.landOnColdStart(WallRow.START_IN_WORKSPACE);
        assertEquals(PaneWallPage.WIDGETS, cold.currentPage());

        empty.hasWindow = true;
        cold.refreshPages();
        assertEquals("the fresh window arrives and the wall goes to it", PaneWallPage.TERMINAL,
            cold.currentPage());
    }

    @Test public void theRowPositionSurvivesARecreate() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.homePage = 1;
        Bundle state = new Bundle();
        controller.onSaveInstanceState(state);
        assertEquals(WallRow.HALF_HOME, state.getString(PaneWallController.ARG_HALF));
        assertEquals(1, state.getInt(PaneWallController.ARG_INDEX));

        Host again = new Host();
        PaneWallController recreated = newController(again);
        recreated.goTo(PaneWallPage.TERMINAL, false);
        assertTrue(recreated.restoreInstanceState(state));
        assertEquals(PaneWallPage.WIDGETS, recreated.currentPage());
        assertEquals(1, again.homePage);

        controller.goTo(PaneWallPage.DISPLAY, false);
        Bundle display = new Bundle();
        controller.onSaveInstanceState(display);
        assertEquals(WallRow.HALF_WORKSPACE, display.getString(PaneWallController.ARG_HALF));
        assertTrue(recreated.restoreInstanceState(display));
        assertEquals(PaneWallPage.DISPLAY, recreated.currentPage());
    }

    @Test public void restoreToleratesNothingAndNonsense() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertFalse(controller.restoreInstanceState(null));
        assertFalse(controller.restoreInstanceState(new Bundle()));
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());

        // The old place names are not read; an unknown half falls back to Home page 1.
        Bundle stale = new Bundle();
        stale.putString(PaneWallController.ARG_HALF, "TERMINAL");
        host.homePage = 2;
        assertTrue(controller.restoreInstanceState(stale));
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.homePage);
    }

    @Test public void theSidePagesAreBuiltOnlyWhenAskedForAndOnlyOnce() {
        PaneWallLayout bare = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        bare.addView(terminal);
        PaneWallController built = new PaneWallController(bare, new Host());
        built.attachTerminalPage(terminal);
        assertFalse(built.hasWidgetsPage());
        assertNull(built.displayPage());
        assertNull("registered on the wall only once built", bare.pageView(PaneWallPage.WIDGETS));

        LayoutInflater inflater = LayoutInflater.from(activity);
        built.attachWidgetsPage(inflater);
        assertTrue(built.hasWidgetsPage());
        View widgets = bare.pageView(PaneWallPage.WIDGETS);
        assertNotNull(widgets);
        assertEquals("added as the leftmost child", widgets, bare.getChildAt(0));

        built.attachWidgetsPage(inflater);
        assertSame("a second call does not rebuild the grid", widgets,
            bare.pageView(PaneWallPage.WIDGETS));
        assertEquals(2, bare.getChildCount());
    }

    @Test public void aTerminalLeftOnScreenByACutSlideCountsAsShowingWhateverTheRecordSays() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        wall.setReducedMotion(false);
        assertTrue(controller.goTo(PaneWallPage.WIDGETS, true));
        wall.onDetachedFromWindow();
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertFalse(wall.isMoving());

        assertTrue(controller.isTerminalShowing());
        assertTrue(controller.isTerminalOnScreen());
        assertTrue(controller.describeState().contains("page=WIDGETS"));

        wall.requestLayout();
        wall.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, 1080, 1800);
        assertFalse(controller.isTerminalShowing());
        assertFalse(controller.isTerminalOnScreen());
    }

    /**
     * The drag back from the first window to Home, by touch through the wall as the phone sends
     * it (#77, emulator 10-16): Home left through the seam, then a sideways slide in the air
     * above the window, let go with no speed, lands on the last Home page. At the phone's
     * density, on a wall below the status bar, with Home's real frame on a window and the
     * slide's own spring running.
     */
    @Config(qualifiers = "xxhdpi")
    @Test public void aTopAirSlideOffTheFirstWindowReachesTheLastHomePage() {
        Host moving = new Host();
        moving.reduced = false;
        moving.display = false;
        moving.windows = 1;
        PaneWallController row = laidOutOnAWindow(moving);
        PaneWallLayout layout = row.wall();
        View terminal = layout.pageView(PaneWallPage.TERMINAL);
        enterTheFirstWindowBySeam(row, moving);

        // In the air between the bar and the window's top, sideways, and let go still.
        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 150f, 12f, 0L));
        for (int i = 1; i <= 20; i++) {
            layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 150f + i * 40f,
                12f, i * 17L));
        }
        assertTrue("the slide off the top border is the wall's", layout.isDragging());
        assertTrue(terminal.getTranslationX() > 0f);
        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 950f, 12f, 360L));
        assertEquals(PaneWallPage.WIDGETS, row.currentPage());
        idle();
        assertEquals(PaneWallPage.WIDGETS, row.currentPage());
        assertEquals(0f, layout.offsetPx(), 0f);
        assertEquals("Home arrives on its last page, the one beside the seam", 2,
            moving.homePage);
    }

    /** The same from the left border: held still past the hold, then drawn to the right. */
    @Config(qualifiers = "xxhdpi")
    @Test public void aHeldLeftBorderDragOffTheFirstWindowReachesTheLastHomePage() {
        Host moving = new Host();
        moving.reduced = false;
        moving.display = false;
        moving.windows = 2;
        PaneWallController row = laidOutOnAWindow(moving);
        PaneWallLayout layout = row.wall();
        enterTheFirstWindowBySeam(row, moving);

        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 6f, 700f, 0L));
        letTheHoldElapse();
        assertTrue("the hold claims the left border", layout.isDragging());
        for (int i = 1; i <= 20; i++) {
            layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 6f + i * 45f,
                700f, 1000L + i * 17L));
        }
        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 906f, 700f, 1360L));
        idle();
        assertEquals(PaneWallPage.WIDGETS, row.currentPage());
        assertEquals("no step inside the Workspace", 0, moving.stepped);
        assertEquals(2, moving.homePage);
    }

    /**
     * The swipe back to Home, as a person makes it and as {@code adb shell input swipe} sends it:
     * a plain sideways drag off the first window's left border with no hold, let go with no
     * speed past the middle, lands Home on its last page.
     */
    @Config(qualifiers = "xxhdpi")
    @Test public void aSideBorderSwipeWithNoHoldReachesTheLastHomePage() {
        Host moving = new Host();
        moving.reduced = false;
        moving.display = false;
        moving.windows = 1;
        PaneWallController row = laidOutOnAWindow(moving);
        PaneWallLayout layout = row.wall();
        enterTheFirstWindowBySeam(row, moving);

        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_DOWN, 6f, 700f, 0L));
        for (int i = 1; i <= 20; i++) {
            layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_MOVE, 6f + i * 45f,
                700f, i * 70L));
        }
        assertTrue("the side border's slide is the wall's, no hold", layout.isDragging());
        layout.dispatchTouchEvent(touch(android.view.MotionEvent.ACTION_UP, 906f, 700f, 1400L));
        idle();
        assertEquals(PaneWallPage.WIDGETS, row.currentPage());
        assertEquals("no step inside the Workspace", 0, moving.stepped);
        assertEquals("Home arrives on its last page, the one beside the seam", 2,
            moving.homePage);
    }

    /**
     * A wall the size of the phone's below its status bar, the window standing in from it as the
     * frame puts it, Home's real frame on the wall, attached to a window and laid out.
     */
    private PaneWallController laidOutOnAWindow(Host withHost) {
        PaneWallLayout layout = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        layout.addView(terminal);
        PaneWallController made = new PaneWallController(layout, withHost);
        made.attachTerminalPage(terminal);
        made.attachWidgetsPage(LayoutInflater.from(activity));
        android.view.ViewGroup.MarginLayoutParams margins =
            (android.view.ViewGroup.MarginLayoutParams) terminal.getLayoutParams();
        margins.setMargins(22, 20, 22, 300);
        terminal.setLayoutParams(margins);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 1500);
        made.refreshPages();
        idle();
        return made;
    }

    /** Home's last page, then its swipe on through the seam to the first window. */
    private static void enterTheFirstWindowBySeam(PaneWallController row, Host withHost) {
        row.goHome(false);
        withHost.homePage = 2;
        assertTrue(row.homeSeam().canLeaveForward());
        row.homeSeam().begin();
        row.homeSeam().drag(-800f);
        row.homeSeam().end(0f);
        idle();
        assertEquals(PaneWallPage.TERMINAL, row.currentPage());
        assertEquals(0, withHost.current);
        // Away from Home, a Home that did not wait on its last page could be on any of them.
        withHost.homePage = 0;
    }

    private static android.view.MotionEvent touch(int action, float x, float y, long timeMs) {
        return android.view.MotionEvent.obtain(0L, timeMs, action, x, y, 0);
    }

    // ---- The row's live position, for the wallpaper's pan (slice 3) ---------------------------

    private static final float ROW_EPS = 0.01f;

    @Test public void eachPageRestsAtItsPlaceInTheRow() {
        // Three Home pages, three windows, the Display page: seven pages.
        assertEquals(7, controller.rowCount());
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.homePage = 0;
        assertEquals(0f, controller.rowPosition(), ROW_EPS);
        host.homePage = 2;
        assertEquals(2f, controller.rowPosition(), ROW_EPS);
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        assertEquals("window 2 comes after the three Home pages", 4f, controller.rowPosition(),
            ROW_EPS);
        controller.goTo(PaneWallPage.DISPLAY, false);
        assertEquals("the Display page is the row's last", 6f, controller.rowPosition(), ROW_EPS);
    }

    @Test public void anEmptyWorkspaceLeavesHomeAndTheDisplayPage() {
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals(4, controller.rowCount());
        controller.goTo(PaneWallPage.DISPLAY, false);
        assertEquals(3f, controller.rowPosition(), ROW_EPS);
        host.display = false;
        controller.refreshPages();
        assertEquals("Home pages only", 3, controller.rowCount());
    }

    @Test public void aWorkspaceWhoseWindowsAreNotKnownYetIsOnePage() {
        host.windows = 0;
        controller.refreshPages();
        assertEquals(5, controller.rowCount());
    }

    @Test public void homesOwnSwipeMovesTheRowBetweenHomePages() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.homePage = 1;
        // Home page 2 dragged 0.3 widths left: Home page 3 is coming in.
        host.homeShift = -0.3f;
        assertEquals(1.3f, controller.rowPosition(), ROW_EPS);
        // Committed: Home page 3 in front, entering from 0.7 widths to the right.
        host.homePage = 2;
        host.homeShift = 0.7f;
        assertEquals(1.3f, controller.rowPosition(), ROW_EPS);
    }

    @Test public void theSeamDragRunsFromTheLastHomePageIntoWindowOne() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.homePage = 2;
        host.current = 2;
        controller.homeSeam().begin();
        controller.homeSeam().drag(-432f);
        assertEquals("0.4 of the way from Home page 3 to window 1", 2.4f,
            controller.rowPosition(), ROW_EPS);
        controller.homeSeam().end(-3000f);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertEquals(3f, controller.rowPosition(), ROW_EPS);
    }

    @Test public void aBorderDragBetweenWindowsMovesTheRowByOneWindow() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        wall.beginPlainDrag();
        wall.dragTo(-324f);
        assertEquals(4.3f, controller.rowPosition(), ROW_EPS);
        wall.dragTo(324f);
        assertEquals(3.7f, controller.rowPosition(), ROW_EPS);
        wall.cancelDrag();
    }

    @Test public void aSlideAcrossTheRowStartsWhereTheWallLeftAndEndsWhereItLands() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 2;
        host.reduced = false;
        controller.refreshPages();
        // The home press from window 3: the wall slides one view, the row runs from row 5 to 0.
        controller.goHome(true);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(5f, controller.rowPosition(), 0.05f);
        idle();
        assertEquals(0f, controller.rowPosition(), ROW_EPS);
    }

    @Test public void aWindowArrivalPansFromTheWindowItLeft() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 2;
        // The bar jumped from window 1 to window 3: the arrival starts a whole width out.
        controller.nudgeTerminalPage(1080f, 0, 300L, null, null);
        assertEquals(3f, controller.rowPosition(), ROW_EPS);
        idle();
        assertEquals(5f, controller.rowPosition(), ROW_EPS);
        // A step back to window 2, arriving from the left with no departure named, starts from
        // window 3 next door.
        host.current = 1;
        controller.nudgeTerminalPage(-540f, WallParallax.ADJACENT, 300L, null, null);
        assertEquals(4.5f, controller.rowPosition(), ROW_EPS);
        idle();
    }

    /**
     * A two-window jump from the bar (window 1 to window 3, row 3 to row 5) pans over both steps:
     * wherever the arriving window stands on its way in, the row stands as far along from the
     * departure, through window 2 at half way, never at the arrival before the window is there
     * (slice 3 pass, finding D). Each nudge is started at that point of the way in rather than
     * stepped frame by frame: under Robolectric the nudge's animator ends on its first frame.
     */
    @Test public void aTwoWindowJumpPansFromTheDepartureAcrossWindowTwoToTheArrival() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 2;
        float[] leftToTravel = {1f, 0.75f, 0.5f, 0.25f};
        float[] rowAt = {3f, 3.5f, 4f, 4.5f};
        for (int i = 0; i < leftToTravel.length; i++) {
            controller.nudgeTerminalPage(1080f * leftToTravel[i], 0, 300L, null, null);
            assertEquals("a window " + leftToTravel[i] + " of a width out", rowAt[i],
                controller.rowPosition(), ROW_EPS);
        }
        idle();
        assertEquals(5f, controller.rowPosition(), ROW_EPS);
    }

    private static void letTheHoldElapse() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(com.termux.view.HoldTiming.holdTimeoutMs() + 20L));
    }

    /** Lets every spring and slide run out. */
    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(3000L));
    }
}
