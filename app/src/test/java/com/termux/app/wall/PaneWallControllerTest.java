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

        @Override public boolean reducedMotion() { return true; }
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
        activity = Robolectric.buildActivity(Activity.class).setup().get();
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
}
