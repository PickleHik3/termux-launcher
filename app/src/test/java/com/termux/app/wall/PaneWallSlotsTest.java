package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * The built window (ADR 0015, A2a): the wall lays out in the pages' order, one Home frame bound
 * to one Home run. [Home 1][Terminal][Home 2] pages both ways from the Terminal page, a reversal
 * re-binds the frame, the second run leaves backward, and a saved second-run page comes back
 * there. The default order builds the row it always had.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneWallSlotsTest {

    private static final class Host implements PaneWallController.Host {
        WallPages pages = mid();
        boolean hasWindow = true;
        int homePages = 2;
        int homePage;
        int windows = 1;
        int current;
        int boundFirst = 0;
        int boundLast = -1;
        int bindCalls;

        @Override public boolean reducedMotion() { return true; }
        @Override public boolean hasWindow() { return hasWindow; }
        @Override public WallPages wallPages() { return pages; }
        @Override public void bindHomeRun(int first, int last) {
            boundFirst = first;
            boundLast = last;
            bindCalls++;
        }
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
    }

    /** [Home 1][Terminal][Home 2]: ids 1, 3, 2. */
    private static WallPages mid() {
        return WallPagesMigration.seed("home", 0, "home", 2).move(3, 1).pages;
    }

    private static final List<PaneWallPage> HOME_LEFT =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
    private static final List<PaneWallPage> HOME_RIGHT =
        Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS);

    private Activity activity;
    private Host host;
    private PaneWallController controller;
    private PaneWallLayout wall;

    @Before public void build() {
        FreshVelocityTrackers.drainPool();
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

    private void swipe(float dxPx) {
        wall.beginPlainDrag();
        wall.dragTo(dxPx);
        wall.endDrag(dxPx > 0 ? 3000f : -3000f);
        idle();
    }

    // ---- pure ----------------------------------------------------------------------------

    @Test public void theDefaultOrderBuildsTheRowItAlwaysHad() {
        WallPages d = WallPagesMigration.seed("home", 0, "home", 2);
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(d, true);
        assertTrue(PaneWallPolicy.isDefaultOrder(slots));
        assertEquals(PaneWallPolicy.rowPages(true), PaneWallPolicy.builtWindow(slots, 0, 0));
        assertEquals(PaneWallPolicy.rowPages(true), PaneWallPolicy.builtWindow(slots, 1, 0));
        List<WallPages.Slot> none = PaneWallPolicy.wallSlots(d, false);
        assertEquals(PaneWallPolicy.rowPages(false), PaneWallPolicy.builtWindow(none, 0, 0));
    }

    @Test public void onTheTerminalPageTheFrameStandsForOneSide() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(mid(), true);
        assertFalse(PaneWallPolicy.isDefaultOrder(slots));
        assertEquals(HOME_LEFT, PaneWallPolicy.builtWindow(slots, 1, 0));
        assertEquals(HOME_RIGHT, PaneWallPolicy.builtWindow(slots, 1, 2));
        assertEquals(HOME_RIGHT, PaneWallPolicy.builtWindow(slots, 2, 2));
        // The bound side is kept while it lies next door; unknown binds the left.
        assertEquals(2, PaneWallPolicy.boundHomeSlot(slots, 1, 2));
        assertEquals(0, PaneWallPolicy.boundHomeSlot(slots, 1, -1));
        assertEquals(2, PaneWallPolicy.boundHomeSlot(slots, 2, 0));
        assertEquals(1, PaneWallPolicy.runStart(slots, 2));
        assertEquals(2, PaneWallPolicy.homeSlotAt(slots, 1));
    }

    // ---- the controller ------------------------------------------------------------------

    @Test public void theDefaultOrderNeverBindsARun() {
        host.pages = WallPagesMigration.seed("home", 0, "home", 2);
        controller.refreshPages();
        controller.goTo(PaneWallPage.TERMINAL, false);
        swipe(600f);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(HOME_LEFT, controller.pages());
        assertEquals("the run stays every page", -1, host.boundLast);
    }

    @Test public void fromTheTerminalPageTheWallSlidesToEachSide() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(HOME_LEFT, controller.pages());

        swipe(-600f);
        assertEquals("forward lands on Home 2", PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(HOME_RIGHT, controller.pages());
        assertEquals("Home 2's first page", 1, host.homePage);
        assertEquals(1, host.boundFirst);
        assertEquals(1, host.boundLast);
        assertEquals(0f, wall.offsetPx(), 0.01f);
        assertEquals(3, controller.rowCount());
        assertEquals(2f, controller.rowPosition(), 0.001f);

        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(1f, controller.rowPosition(), 0.001f);
        swipe(600f);
        assertEquals("backward lands on Home 1", PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(HOME_LEFT, controller.pages());
        assertEquals(0, host.homePage);
        assertEquals(0, host.boundFirst);
        assertEquals(0f, controller.rowPosition(), 0.001f);
    }

    @Test public void aReversalOnTheTerminalPageRebindsTheFrame() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        wall.beginPlainDrag();
        wall.dragTo(-300f);
        assertEquals(HOME_RIGHT, controller.pages());
        assertEquals(1, host.homePage);
        wall.dragTo(300f);
        assertEquals(HOME_LEFT, controller.pages());
        assertEquals(0, host.homePage);
        wall.cancelDrag();
        idle();
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test public void theSecondRunLeavesBackward() {
        controller.goTo(PaneWallPage.TERMINAL, false);
        swipe(-600f);
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        com.termux.app.launcher.widget.WidgetPaneView.Seam seam = controller.homeSeam();
        assertFalse("nothing lies after Home 2", seam.canLeaveForward());
        assertTrue(seam.canLeaveBackward());
        seam.begin();
        seam.drag(600f);
        seam.end(3000f);
        idle();
        assertEquals(PaneWallPage.TERMINAL, wall.currentPage());
    }

    @Test public void theFirstRunInTheDefaultOrderNeverLeavesBackward() {
        host.pages = WallPagesMigration.seed("home", 0, "home", 2);
        controller.refreshPages();
        controller.goHome(false);
        assertFalse(controller.homeSeam().canLeaveBackward());
        assertTrue(controller.homeSeam().canLeaveForward());
    }

    @Test public void aSavedSecondRunPageComesBackThere() {
        Bundle state = new Bundle();
        state.putString(PaneWallController.ARG_HALF, WallRow.HALF_HOME);
        state.putInt(PaneWallController.ARG_INDEX, 1);
        assertTrue(controller.restoreInstanceState(state));
        assertEquals(PaneWallPage.WIDGETS, wall.currentPage());
        assertEquals(1, host.homePage);
        assertEquals(HOME_RIGHT, controller.pages());
        assertEquals(1, host.boundFirst);

        Bundle saved = new Bundle();
        controller.onSaveInstanceState(saved);
        assertEquals(WallRow.HALF_HOME, saved.getString(PaneWallController.ARG_HALF));
        assertEquals(1, saved.getInt(PaneWallController.ARG_INDEX));
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(3000L));
    }
}
