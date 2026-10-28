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
 * Workspace while its row has a window, the display window's included), the seam both ways,
 * where the home press and a cold start land, keeping the row position across an activity
 * recreate, and the display window standing as the Workspace's page while it is in front.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneWallControllerTest {

    /** A host with a number of Home pages and windows, each with one in front. */
    private static final class Host implements PaneWallController.Host {
        boolean reduced = true;
        boolean hasWindow = true;
        int homePages = 3;
        int homePage;
        int windows = 3;
        int current;
        Boolean terminalOffScreen;
        int stepBegun;
        int stepped;
        float steppedFrom;
        float homeShift;
        boolean canStart;
        int shellsStarted;
        UseAs useAs = UseAs.HOME;
        WallPages pages;
        int boundDisplay = -2;
        int landedDisplay = -1;
        final java.util.List<PaneWallPage> changes = new java.util.ArrayList<>();

        @Override public boolean reducedMotion() { return reduced; }
        int homePress;
        @Override public int homePressPage() { return homePress; }
        @Override public WallPages wallPages() { return pages; }
        @Override public void bindDisplayPage(int pageId) { boundDisplay = pageId; }
        @Override public void onDisplayPageLanded(int pageId) { landedDisplay = pageId; }
        @Override public void onWallPageChanged(PaneWallPage page) { changes.add(page); }
        @Override public UseAs useAs() { return useAs; }
        @Override public boolean canStartWorkspace() { return canStart; }
        @Override public boolean startWorkspaceShell() {
            shellsStarted++;
            // The shell's window arrives with it, as the session client adds it.
            hasWindow = true;
            windows = 1;
            current = 0;
            return true;
        }
        @Override public float innerShiftFraction(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homeShift : 0f;
        }
        @Override public boolean isPlankTiltEnabled() { return !reduced; }
        @Override public boolean hasWindow() { return hasWindow; }
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

    @Test public void theEditorsSlideToAnEmptyWorkspaceStartsItsFirstShellAndLandsThere() {
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();
        controller.goHome(false);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());

        assertTrue(controller.goToWorkspace(false));

        assertEquals(1, host.shellsStarted);
        assertEquals("the wall stands on the new window", PaneWallPage.TERMINAL,
            wall.currentPage());
    }

    @Test public void theEditorsSlideToAWorkspaceWithAWindowStartsNothing() {
        controller.goHome(false);
        assertTrue(controller.goToWorkspace(false));
        assertEquals(0, host.shellsStarted);
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test public void noWorkspaceToShowLeavesTheWallOnHome() {
        host.hasWindow = false;
        host.canStart = false;
        controller.refreshPages();
        controller.goHome(false);
        assertFalse(controller.goToWorkspace(false));
        assertEquals(0, host.shellsStarted);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
    }

    @Test public void theWallsTerminalOffScreenSignalReachesTheHost() {
        assertEquals("the first layout reports the terminal on screen", Boolean.FALSE,
            host.terminalOffScreen);
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertEquals(Boolean.TRUE, host.terminalOffScreen);
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(Boolean.FALSE, host.terminalOffScreen);
    }

    @Test public void theRowIsHomeThenTheWorkspace() {
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL), controller.pages());

        // No window: the Workspace leaves the row, and Home is all there is.
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals(Collections.singletonList(PaneWallPage.WIDGETS), controller.pages());
    }

    @Test public void terminalOnlyTakesHomeOffTheRowAndMovesTheWallOffIt() {
        controller.goHome(false);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        host.current = 1;

        host.useAs = UseAs.TERMINAL;
        controller.refreshPages();

        assertEquals("the window in front stays in front", 1, host.current);
        assertEquals(Collections.singletonList(PaneWallPage.TERMINAL), controller.pages());
        assertEquals("the wall left the Home page it stood on", PaneWallPage.TERMINAL,
            controller.currentPage());
        assertFalse("launcherctl's wall.go home is refused", controller.goTo("home"));
        assertFalse(controller.goTo("widgets"));
    }

    @Test public void terminalOnlyLandsTheHomePressAndAColdStartOnTheWorkspace() {
        host.useAs = UseAs.TERMINAL;
        controller.refreshPages();
        controller.goHome(false);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        controller.goToLastHomePage(false);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void terminalOnlyKeepsAnEmptyWorkspaceOnTheRowAndTheHomePressStartsAShell() {
        host.useAs = UseAs.TERMINAL;
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();
        assertEquals("never a Home page to fall back on",
            Collections.singletonList(PaneWallPage.TERMINAL), controller.pages());

        controller.goHome(false);

        assertEquals(1, host.shellsStarted);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void backToHomeScreenPutsHomeBackOnTheRow() {
        host.useAs = UseAs.TERMINAL;
        controller.refreshPages();
        host.useAs = UseAs.HOME;
        controller.refreshPages();
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL), controller.pages());
        controller.goHome(false);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
    }

    @Test public void thePagesAreALineNotARing() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertTrue(controller.goTo("left"));
        assertEquals("nothing is left of Home page 1", PaneWallPage.WIDGETS,
            controller.currentPage());
        assertFalse("display names a window, not a page", controller.goTo("display"));
        assertTrue(controller.goTo("right"));
        assertTrue(controller.goTo("right"));
        assertEquals("nothing is right of the Workspace", PaneWallPage.TERMINAL,
            controller.currentPage());
        assertTrue(controller.goTo("home"));
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertFalse(controller.goTo("nowhere"));
        controller.returnToTerminal(false);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void theLastWindowClosingLandsOnTheLastHomePage() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.homePage = 0;
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(2, host.homePage);
        assertFalse(controller.isTerminalShowing());
    }

    @Test public void aWindowLeavingElsewhereLeavesTheWallWhereItIs() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        host.windows = 2;
        controller.refreshPages();
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertEquals(1, host.current);
    }

    @Test public void theSeamLeavesHomeOnlyWhenTheRowGoesOn() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        assertTrue(controller.homeSeam().canLeaveForward());

        host.hasWindow = false;
        controller.refreshPages();
        assertFalse("no window and no shell to start: the swipe stops",
            controller.homeSeam().canLeaveForward());
        host.canStart = true;
        assertTrue("an empty Workspace that can start still takes the swipe",
            controller.homeSeam().canLeaveForward());
    }

    @Test public void theSeamIntoAnEmptyWorkspaceStartsItsShellOnlyWhenItCommits() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();
        assertEquals(Collections.singletonList(PaneWallPage.WIDGETS), controller.pages());

        controller.homeSeam().begin();
        assertEquals("the empty Workspace's page joins the row for the swipe",
            Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL), controller.pages());
        assertEquals("nothing is started at touch-down", 0, host.shellsStarted);
        controller.homeSeam().drag(-900f);
        assertEquals("nor while the finger drives it", 0, host.shellsStarted);
        controller.homeSeam().end(-3000f);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertEquals("the release committed: one shell", 1, host.shellsStarted);
        controller.refreshPages();
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void aCrossingThatWavers_beforeItsWindowArrives_startsOneShell() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();

        controller.homeSeam().begin();
        controller.homeSeam().drag(-900f);
        controller.homeSeam().end(-3000f);
        assertEquals(1, host.shellsStarted);
        // The page flips back and forth before the shell's window has come.
        controller.onWallPageChanged(PaneWallPage.WIDGETS);
        controller.onWallPageChanged(PaneWallPage.TERMINAL);
        assertEquals("one crossing, one shell", 1, host.shellsStarted);
    }

    @Test public void aSwipeIntoAnEmptyWorkspaceThatSpringsBackLeavesNothing() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();

        controller.homeSeam().begin();
        controller.homeSeam().drag(-40f);
        controller.homeSeam().end(0f);
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.shellsStarted);
        assertEquals("the empty page leaves the row again",
            Collections.singletonList(PaneWallPage.WIDGETS), controller.pages());

        controller.homeSeam().begin();
        controller.homeSeam().drag(-300f);
        controller.homeSeam().cancel();
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertEquals(0, host.shellsStarted);
        assertEquals(Collections.singletonList(PaneWallPage.WIDGETS), controller.pages());
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
        controller.goTo(PaneWallPage.TERMINAL, false);
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

        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 2;
        Bundle workspace = new Bundle();
        controller.onSaveInstanceState(workspace);
        assertEquals(WallRow.HALF_WORKSPACE, workspace.getString(PaneWallController.ARG_HALF));
        assertEquals(2, workspace.getInt(PaneWallController.ARG_INDEX));
        recreated.goTo(PaneWallPage.WIDGETS, false);
        assertTrue(recreated.restoreInstanceState(workspace));
        assertEquals(PaneWallPage.TERMINAL, recreated.currentPage());
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
        assertNull(built.displayWindow());
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
        // Three Home pages and three windows, the display window counted among them if it is up.
        assertEquals(6, controller.rowCount());
        controller.goTo(PaneWallPage.WIDGETS, false);
        host.homePage = 0;
        assertEquals(0f, controller.rowPosition(), ROW_EPS);
        host.homePage = 2;
        assertEquals(2f, controller.rowPosition(), ROW_EPS);
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.current = 1;
        assertEquals("window 2 comes after the three Home pages", 4f, controller.rowPosition(),
            ROW_EPS);
    }

    @Test public void anEmptyWorkspaceLeavesHomeAlone() {
        host.hasWindow = false;
        controller.refreshPages();
        assertEquals("Home pages only", 3, controller.rowCount());
    }

    @Test public void aWorkspaceWhoseWindowsAreNotKnownYetIsOnePage() {
        host.windows = 0;
        controller.refreshPages();
        assertEquals(4, controller.rowCount());
    }

    // ---- Display pages as wall slots (ADR 0015, A3b) --------------------------------------------

    /** [Home 1][Home 2][Terminal 3][Display 4], with the display's frame as the Display page. */
    private void displayPages() {
        host.pages = WallPagesMigration.seed("home", 0, "home", 2, true);
        host.homePages = 2;
        View frame = new FrameLayout(activity);
        wall.addView(frame);
        wall.setPageView(PaneWallPage.DISPLAY, frame);
        controller.refreshPages();
    }

    @Test public void aDisplayPageIsAPageOfTheWallBesideTheTerminal() {
        displayPages();
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL,
            PaneWallPage.DISPLAY), wall.pages());
        assertEquals("the display view is bound to the Display page", 4, host.boundDisplay);
        assertEquals("the Terminal page is the pane host alone", 3, host.innerCount(
            PaneWallPage.TERMINAL));
        assertTrue(controller.goTo(PaneWallPage.DISPLAY, false));
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());
        assertEquals("landing tells the host which page", 4, host.landedDisplay);
        assertFalse("the Terminal page is not showing", controller.isTerminalShowing());
    }

    @Test public void theHomePressLandsOnADisplayHome() {
        displayPages();
        WallPages.Result home = host.pages.setHome(4);
        assertTrue(home.ok());
        host.pages = home.pages;
        controller.goTo(PaneWallPage.WIDGETS, false);
        controller.goHome(false);
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());
        assertEquals(4, controller.boundDisplayPage());
    }

    @Test public void theHomePressToAFarDisplayHomeNeitherPassesTheTerminalNorStartsAShell() {
        // [HomeA][D1][HomeB][T empty][D2 home], pressed from HomeA (W3).
        host.pages = new WallPages(Arrays.asList(
            new WallPages.Page(1, WallPages.PageKind.HOME, null, null),
            new WallPages.Page(2, WallPages.PageKind.DISPLAY, null, null),
            new WallPages.Page(3, WallPages.PageKind.HOME, null, null),
            new WallPages.Page(4, WallPages.PageKind.TERMINAL, null, null),
            new WallPages.Page(5, WallPages.PageKind.DISPLAY, null, null)),
            Collections.<Integer>emptyList(), Collections.<WallPages.Page>emptyList(), 1, 6, false);
        host.homePages = 2;
        host.hasWindow = false;
        host.canStart = true;
        View frame = new FrameLayout(activity);
        wall.addView(frame);
        wall.setPageView(PaneWallPage.DISPLAY, frame);
        controller.refreshPages();
        controller.goHome(false);
        assertEquals(1, controller.currentPageId());
        host.pages = host.pages.setHome(5).pages;
        host.changes.clear();
        controller.goHome(false);
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());
        assertEquals(5, controller.boundDisplayPage());
        assertEquals("no Workspace shell starts on the way", 0, host.shellsStarted);
        assertFalse("the wall is never handed to the Terminal page",
            host.changes.contains(PaneWallPage.TERMINAL));
    }

    @Test public void theHomePressToAHomePageOfAnotherRunSlidesThere() {
        // [Home1][T2][Home3], the wall on Home 3; the press goes to Home 1 (W9).
        host.pages = new WallPages(Arrays.asList(
            new WallPages.Page(1, WallPages.PageKind.HOME, null, null),
            new WallPages.Page(2, WallPages.PageKind.TERMINAL, null, null),
            new WallPages.Page(3, WallPages.PageKind.HOME, null, null)),
            Collections.<Integer>emptyList(), Collections.<WallPages.Page>emptyList(), 1, 4, false);
        host.homePages = 2;
        controller.refreshPages();
        host.homePress = 1;
        controller.goHome(false);
        assertEquals(3, controller.currentPageId());
        host.homePress = 0;
        host.reduced = false;
        controller.refreshPages();
        controller.goHome(true);
        assertEquals(1, controller.currentPageId());
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        assertTrue("the run arrives with a slide", wall.isMoving());
        assertTrue("from the side it stands on", wall.offsetPx() < 0f);
        idle();
        assertEquals(0f, wall.offsetPx(), 0f);
    }

    @Test public void terminalOnlyKeepsTheDisplayPagesBesideTheWorkspace() {
        displayPages();
        host.useAs = UseAs.TERMINAL;
        controller.refreshPages();
        assertEquals(Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY), wall.pages());
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

    /**
     * A one-window jump from the bar, either way, in the order showWindowFromBar takes: the
     * departure is current, the arrival is made current, then the arrival is nudged in from a
     * width out naming the departure. The row runs from the departure to the arrival and never
     * steps outside them, so the wallpaper never starts the wrong way (slice 3 re-check).
     */
    @Test public void aOneWindowJumpEitherWayStaysBetweenTheDepartureAndTheArrival() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        int[][] jumps = {{1, 2}, {2, 1}};
        float[] leftToTravel = {1f, 0.5f, 0f};
        for (int[] jump : jumps) {
            int departure = jump[0];
            int arrival = jump[1];
            host.current = departure;
            assertEquals(3f + departure, controller.rowPosition(), ROW_EPS);
            host.current = arrival;
            float sign = arrival > departure ? 1f : -1f;
            float departureAt = 3f + departure;
            float arrivalAt = 3f + arrival;
            for (float left : leftToTravel) {
                controller.nudgeTerminalPage(sign * 1080f * left, departure, 300L, null, null);
                float at = controller.rowPosition();
                assertEquals(departure + " to " + arrival + ", " + left + " of a width out",
                    arrivalAt + (departureAt - arrivalAt) * left, at, ROW_EPS);
                assertTrue("never past the departure", (at - departureAt) * sign >= -ROW_EPS);
                assertTrue("never past the arrival", (arrivalAt - at) * sign >= -ROW_EPS);
            }
            idle();
            assertEquals(arrivalAt, controller.rowPosition(), ROW_EPS);
        }
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
