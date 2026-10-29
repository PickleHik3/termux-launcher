package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The wall lays out one cell per page (ADR 0016): every page of the list, in order, by page id;
 * each kind's one view live at the page it is bound to and a card still at every other cell of
 * that kind; a landing binds the view; neighbours are cell neighbours, whatever route brought the
 * wall where it is; a far jump by name slides one width from a stand-off (W3). The tests read
 * what the user sees: the page landed on, the cells in order, which page of each kind is live.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneWallCellsTest {

    private static final int WIDTH = 1080;

    private static final class Host implements PaneWallController.Host {
        WallPages pages;
        boolean reduced = true;
        boolean hasWindow = true;
        int homePage;
        int windows = 1;
        int current;
        int homePress;
        boolean canStart;
        int shellsStarted;
        int boundDisplay = -1;
        int landedDisplay = -1;
        final List<PaneWallPage> changes = new ArrayList<>();

        @Override public boolean reducedMotion() { return reduced; }
        @Override public boolean canStartWorkspace() { return canStart; }
        @Override public boolean startWorkspaceShell() {
            shellsStarted++;
            hasWindow = true;
            windows = 1;
            current = 0;
            return true;
        }
        @Override public boolean hasWindow() { return hasWindow; }
        @Override public WallPages wallPages() { return pages; }
        @Override public int homePressPage() { return homePress; }
        @Override public void bindDisplayPage(int pageId) { boundDisplay = pageId; }
        @Override public void onDisplayPageLanded(int pageId) { landedDisplay = pageId; }
        @Override public void onWallPageChanged(PaneWallPage page) { changes.add(page); }
        @Override public int innerCount(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? Math.max(1, pages.homePages().size())
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

    private Activity activity;
    private Host host;
    private PaneWallController controller;
    private PaneWallLayout wall;
    private View widgets;
    private View terminal;
    private View display;

    @Before public void build() {
        FreshVelocityTrackers.drainPool();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new Host();
    }

    /** [Home 1][Terminal]: ids 1, 2. */
    private static WallPages homeTerminal() {
        return WallPagesMigration.seed("home", 0, "home", 1);
    }

    /** [Home 1][Terminal][Display]: ids 1, 2, 3; the sweep's wall. */
    private static WallPages homeTerminalDisplay() {
        return homeTerminal().add(WallPages.PageKind.DISPLAY, 2, null).pages;
    }

    private void start(WallPages pages) {
        host.pages = pages;
        PaneWallLayout layout = new PaneWallLayout(activity);
        terminal = new FrameLayout(activity);
        widgets = new FrameLayout(activity);
        display = new FrameLayout(activity);
        layout.addView(widgets);
        layout.addView(terminal);
        layout.addView(display);
        controller = new PaneWallController(layout, host);
        controller.attachTerminalPage(terminal);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        layout.setPageView(PaneWallPage.DISPLAY, display);
        layout.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, WIDTH, 1800);
        wall = controller.wall();
    }

    private List<Integer> cellIds() {
        List<Integer> out = new ArrayList<>();
        for (PaneWallLayout.Cell cell : wall.cells()) out.add(cell.pageId);
        return out;
    }

    private int on() {
        return controller.currentPageId();
    }

    /** A finger's swipe: positive brings in the page on the left. */
    private void swipe(float dxPx) {
        wall.beginPlainDrag();
        wall.dragTo(dxPx);
        wall.endDrag(dxPx > 0 ? 3000f : -3000f);
        idle();
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(3000L));
    }

    // ---- cold start ------------------------------------------------------------------------

    @Test public void aColdStartOnHomeLandsOnTheHomePageWithEveryPageACell() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(Arrays.asList(1, 2, 3), cellIds());
        assertEquals(1, on());
        assertTrue(wall.isLive(1));
    }

    @Test public void aColdStartOnTheTerminalLandsThere() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_WORKSPACE);
        assertEquals(2, on());
        assertTrue(wall.isLive(2));
    }

    @Test public void aColdStartOnAHomeDisplayPageLandsThereWithTheDisplayBound() {
        WallPages pages = homeTerminalDisplay();
        start(pages.setHome(3).pages);
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(3, on());
        assertEquals(3, host.boundDisplay);
        assertTrue(wall.isLive(3));
    }

    // ---- the sweep of 2026-10-29 -----------------------------------------------------------

    @Test public void aNamedJumpToDisplayThenADragTowardHomeFindsTheArrangedNeighbours() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertTrue(controller.goTo("display"));
        assertEquals(3, on());
        assertEquals(3, host.landedDisplay);
        swipe(600f);
        assertEquals(2, on());
        swipe(600f);
        assertEquals(1, on());
        assertEquals(Arrays.asList(1, 2, 3), cellIds());
    }

    @Test public void afterTheDisplayKeyAndTheHomeKeyASwipeFromHomeLandsOnTheTerminal() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        controller.goTo("display");
        assertTrue(controller.goTo("home"));
        assertEquals(1, on());
        swipe(-600f);
        assertEquals(2, on());
    }

    @Test public void theTerminalKeyThenAFlingRightReachesHome() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        controller.goTo("display");
        assertTrue(controller.goTo("terminal"));
        assertEquals(2, on());
        swipe(600f);
        assertEquals(1, on());
    }

    @Test public void aHomePressOnADisplayPageReachedByKeyLandsOnTheHomePage() {
        start(homeTerminalDisplay());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        controller.goTo("display");
        controller.goHome(true);
        idle();
        assertEquals(1, on());
        assertTrue(wall.isLive(1));
    }

    // ---- orders ------------------------------------------------------------------------------

    @Test public void homeDisplayTerminalPagesInItsOrderBothWays() {
        // [Home 1][Display][Terminal]: ids 1, 3, 2.
        start(homeTerminalDisplay().move(3, 1).pages);
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(Arrays.asList(1, 3, 2), cellIds());
        swipe(-600f);
        assertEquals(3, on());
        assertTrue(wall.isLive(3));
        swipe(-600f);
        assertEquals(2, on());
        swipe(600f);
        assertEquals(3, on());
        swipe(600f);
        assertEquals(1, on());
    }

    @Test public void aFarJumpBetweenTwoHomeRunsNeverStartsThePageBetween() {
        // [Home 1][Terminal][Home 2]: ids 1, 3, 2; the Terminal page empty, a stop.
        start(WallPagesMigration.seed("home", 0, "home", 2).move(3, 1).pages);
        host.hasWindow = false;
        host.canStart = true;
        controller.refreshPages();
        assertEquals(Arrays.asList(1, 3, 2), cellIds());
        host.homePress = 1;
        controller.goHome(false);
        assertEquals(2, on());
        host.changes.clear();
        host.homePress = 0;
        controller.goHome(true);
        idle();
        assertEquals(1, on());
        assertTrue(wall.isLive(1));
        assertFalse(wall.isLive(2));
        assertEquals(0, host.shellsStarted);
        assertFalse(host.changes.contains(PaneWallPage.TERMINAL));
    }

    @Test public void twoDisplayPagesSideBySideEachBindTheDisplayOnLanding() {
        // [Home 1][Terminal][Display 3][Display 4].
        start(homeTerminalDisplay().add(WallPages.PageKind.DISPLAY, 3, null).pages);
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(Arrays.asList(1, 2, 3, 4), cellIds());
        controller.goTo("display");
        assertEquals(3, on());
        assertTrue(wall.isLive(3));
        assertFalse(wall.isLive(4));
        swipe(-600f);
        assertEquals(4, on());
        assertEquals(4, host.boundDisplay);
        assertTrue(wall.isLive(4));
        assertFalse(wall.isLive(3));
        assertNotNull(wall.cardOf(3));
    }

    @Test public void removingTheNeighbourOfThePageArrivingMidSlideKeepsTheArrival() {
        start(homeTerminalDisplay().add(WallPages.PageKind.DISPLAY, 3, null).pages);
        controller.goTo(PaneWallPage.TERMINAL, false);
        host.reduced = false;
        controller.refreshPages();
        assertTrue(controller.goTo("right"));
        assertTrue(wall.isMoving());
        host.pages = host.pages.remove(4).pages;
        controller.refreshPages();
        idle();
        assertEquals(3, on());
        assertEquals(Arrays.asList(1, 2, 3), cellIds());
        assertTrue(wall.isLive(3));
    }

    // ---- the layout ------------------------------------------------------------------------

    @Test public void theLayoutHasACellPerPageAndEachViewAtItsBoundCell() {
        start(homeTerminalDisplay().add(WallPages.PageKind.DISPLAY, 3, null).pages);
        controller.goTo(PaneWallPage.TERMINAL, false);
        assertEquals(4, wall.cells().size());
        assertEquals(2, on());
        // Home 1 one cell left, Display 3 one right; Display 4 a card two cells right.
        assertEquals(-WIDTH, widgets.getTranslationX(), 0.5f);
        assertEquals(WIDTH, display.getTranslationX(), 0.5f);
        assertEquals(3, wall.boundPageId(PaneWallPage.DISPLAY));
        PageCardView card = wall.cardOf(4);
        assertNotNull(card);
        assertEquals(2f * WIDTH, card.getTranslationX(), 0.5f);
        assertFalse(wall.isLive(4));
    }

    @Test public void aFarJumpPosesOneWidthFromAStandOff() {
        start(homeTerminalDisplay().add(WallPages.PageKind.DISPLAY, 3, null).pages);
        controller.landOnColdStart(WallRow.START_IN_HOME);
        wall.setReducedMotion(false);
        assertTrue(wall.goToCell(4, true));
        // One width, not three: the arriving page stands one width off, the page left where it
        // was, and the pages between out of the slide.
        assertEquals(WIDTH, wall.offsetPx(), 0.5f);
        assertEquals(0f, widgets.getTranslationX(), 0.5f);
        assertTrue(terminal.getAlpha() == 0f || Math.abs(terminal.getTranslationX()) >= WIDTH);
        idle();
        assertEquals(4, wall.currentPageId());
        assertEquals(0f, wall.offsetPx(), 0.5f);
    }
}
