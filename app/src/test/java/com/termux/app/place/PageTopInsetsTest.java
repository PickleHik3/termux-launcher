package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * Each page of the wall keeps its own top across the seam between Home and the Workspace: the
 * terminal never moves down at settle as the bar takes its band, and Home never moves up as it
 * gives it back.
 */
public class PageTopInsetsTest {

    private static final PaneWallPage HOME = PaneWallPage.WIDGETS;
    private static final PaneWallPage WINDOW = PaneWallPage.TERMINAL;

    private static final int BAR = 120;
    private static final int COMPACT_BAR = 72;
    private static final int AZ_ROW = 40;

    private static EdgeStackPolicy.Metrics metrics(int bar) {
        return EdgeStackPolicy.Metrics.builder()
            .status(bar, 200).apps(150, 90).az(AZ_ROW, 60).extraKeys(80, 100).build();
    }

    private static PlaceLayout layout(Edge status, Edge azEdge) {
        return new PlaceLayout(status, RowPlacement.BOTTOM, true, azEdge, RowPlacement.HIDDEN,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    /** The Workspace's layout: the bar along the top, the dock along the bottom. */
    private static final PlaceLayout WORKSPACE = layout(Edge.TOP, Edge.BOTTOM);
    /** A Home layout that puts the bar away. */
    private static final PlaceLayout HOME_LAYOUT = WORKSPACE.withSlot(Element.STATUS,
        Slot.hiddenFrom(Edge.TOP, 0));
    /** Home with the Workspace's bar brought over it for a slide toward the Workspace. */
    private static final PlaceLayout HOME_WITH_BAR = SeamTravel.withStatusOf(HOME_LAYOUT,
        WORKSPACE);

    /**
     * Where {@code page} starts on screen, from the top of the top edge's stack: the stack's
     * thickness, less the canvas's reach up under it, plus the page's own inset. The model of the
     * content column the activity lays out (EdgeStackView.overlayFollowing, PaneWallLayout).
     */
    private static int pageTopPx(PaneWallPage page, PaneWallPage committed, boolean wallAtRest,
                                 PlaceLayout onScreen, PlaceLayout home,
                                 EdgeStackPolicy.Metrics metrics) {
        boolean reaches = PageTopInsets.canvasReachesUnderBar(committed, wallAtRest);
        int reach = PageTopInsets.canvasReachPx(reaches, true, onScreen, metrics);
        int canvasTop = EdgeStackPolicy.stackThicknessPx(onScreen, Edge.TOP, metrics) - reach;
        return canvasTop + PageTopInsets.pageTopInsetPx(page,
            PageTopInsets.workspaceTopInsetPx(reaches, true, onScreen, WORKSPACE, metrics),
            PageTopInsets.homeTopInsetPx(reaches, true, home, metrics));
    }

    /** {@link #pageTopPx}, Home on a Home layout that puts the bar away. */
    private static int pageTopPx(PaneWallPage page, PaneWallPage committed, boolean wallAtRest,
                                 PlaceLayout onScreen, EdgeStackPolicy.Metrics metrics) {
        return pageTopPx(page, committed, wallAtRest, onScreen, HOME_LAYOUT, metrics);
    }

    @Test
    public void theCanvasStandsBelowTheBarOnlyWhileTheWallRestsOnTheWorkspace() {
        assertFalse(PageTopInsets.canvasReachesUnderBar(WINDOW, true));
        assertFalse(PageTopInsets.canvasReachesUnderBar(PaneWallPage.DISPLAY, true));
        assertTrue("a slide from the Workspace", PageTopInsets.canvasReachesUnderBar(WINDOW,
            false));
        assertTrue("resting on Home", PageTopInsets.canvasReachesUnderBar(HOME, true));
        assertTrue(PageTopInsets.canvasReachesUnderBar(HOME, false));
    }

    @Test
    public void aHomePageStartsAtItsOwnInsetAndAWindowAtTheWorkspaces() {
        assertEquals(0, PageTopInsets.pageTopInsetPx(HOME, BAR, 0));
        assertEquals(BAR, PageTopInsets.pageTopInsetPx(HOME, 0, BAR));
        assertEquals(BAR, PageTopInsets.pageTopInsetPx(WINDOW, BAR, 0));
        assertEquals("the display window's frame is a Workspace page", BAR,
            PageTopInsets.pageTopInsetPx(null, BAR, 0));
        assertEquals(0, PageTopInsets.pageTopInsetPx(WINDOW, -5, 0));
        assertEquals(0, PageTopInsets.pageTopInsetPx(HOME, 0, -5));
    }

    /**
     * One bar across the wall (#90): a Home that wears the Workspace's bar starts below it as a
     * window's page does, at rest and on every frame of the swipe both ways, so the bar and the
     * two pages line up across the seam.
     */
    @Test
    public void aHomeWearingTheBarStartsBelowItLikeAWindowAtEveryStep() {
        for (int bar : new int[] {BAR, COMPACT_BAR}) {
            EdgeStackPolicy.Metrics m = metrics(bar);
            int window = pageTopPx(WINDOW, WINDOW, true, WORKSPACE, WORKSPACE, m);
            assertEquals("below the bar", bar, window);
            assertEquals(window, pageTopPx(HOME, HOME, true, WORKSPACE, WORKSPACE, m));
            assertEquals(window, pageTopPx(WINDOW, HOME, true, WORKSPACE, WORKSPACE, m));
            for (PaneWallPage committed : new PaneWallPage[] {WINDOW, HOME}) {
                assertEquals(committed.name(), window,
                    pageTopPx(HOME, committed, false, WORKSPACE, WORKSPACE, m));
                assertEquals(committed.name(), window,
                    pageTopPx(WINDOW, committed, false, WORKSPACE, WORKSPACE, m));
            }
        }
    }

    @Test
    public void homesInsetIsItsOwnBarsBandOnlyWhileTheCanvasReaches() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        assertEquals(BAR, PageTopInsets.homeTopInsetPx(true, true, WORKSPACE, m));
        assertEquals("a Home layout without the bar", 0,
            PageTopInsets.homeTopInsetPx(true, true, HOME_LAYOUT, m));
        assertEquals("a bar on the bottom edge", 0, PageTopInsets.homeTopInsetPx(true, true,
            layout(Edge.BOTTOM, Edge.BOTTOM), m));
        assertEquals("below the bar there is nothing to inset", 0,
            PageTopInsets.homeTopInsetPx(false, true, WORKSPACE, m));
        assertEquals("compatibility mode shows no bar", 0,
            PageTopInsets.homeTopInsetPx(true, false, WORKSPACE, m));
    }

    /** The slide from the terminal to Home: every frame, then the settle, puts each page where it was. */
    @Test
    public void slidingFromTheTerminalToHomeMovesNeitherPageAtAnyStep() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        int terminalAtRest = pageTopPx(WINDOW, WINDOW, true, WORKSPACE, m);
        assertEquals("below the bar, as it always stood", BAR, terminalAtRest);
        // The wall leaves its rest: the canvas reaches up, the terminal keeps its place.
        assertEquals(terminalAtRest, pageTopPx(WINDOW, WINDOW, false, WORKSPACE, m));
        int homeSliding = pageTopPx(HOME, WINDOW, false, WORKSPACE, m);
        assertEquals("Home stands at its own top from the first frame", 0, homeSliding);
        // Settled on Home: Home's layout, no bar. Neither page moves.
        assertEquals(homeSliding, pageTopPx(HOME, HOME, true, HOME_LAYOUT, m));
        assertEquals(terminalAtRest, pageTopPx(WINDOW, HOME, true, HOME_LAYOUT, m));
    }

    /** And back: from Home, with the bar brought over Home for the slide, to the terminal. */
    @Test
    public void slidingFromHomeToTheTerminalMovesNeitherPageAtAnyStep() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        int homeAtRest = pageTopPx(HOME, HOME, true, HOME_LAYOUT, m);
        int terminalWaiting = pageTopPx(WINDOW, HOME, true, HOME_LAYOUT, m);
        assertEquals(0, homeAtRest);
        assertEquals("the terminal waits beside Home at its Workspace place", BAR,
            terminalWaiting);
        // The slide lays the Workspace's bar out over Home.
        assertEquals(homeAtRest, pageTopPx(HOME, HOME, false, HOME_WITH_BAR, m));
        assertEquals(terminalWaiting, pageTopPx(WINDOW, HOME, false, HOME_WITH_BAR, m));
        // Settled on the terminal: the Workspace's layout, the canvas back below the bar.
        assertEquals(terminalWaiting, pageTopPx(WINDOW, WINDOW, true, WORKSPACE, m));
    }

    /** The terminal's height never changes either: its top and the canvas's bottom both hold. */
    @Test
    public void theTerminalKeepsItsHeightWhereverTheCanvasStands() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        int screenPx = 2000;
        int restHeight = screenPx - pageTopPx(WINDOW, WINDOW, true, WORKSPACE, m);
        assertEquals(restHeight, screenPx - pageTopPx(WINDOW, WINDOW, false, WORKSPACE, m));
        assertEquals(restHeight, screenPx - pageTopPx(WINDOW, HOME, true, HOME_LAYOUT, m));
        assertEquals(restHeight, screenPx - pageTopPx(WINDOW, HOME, false, HOME_WITH_BAR, m));
    }

    /** A compact bar is a thinner band, and the terminal waiting beside Home follows it. */
    @Test
    public void theCompactBarsBandIsTheInset() {
        EdgeStackPolicy.Metrics m = metrics(COMPACT_BAR);
        assertEquals(COMPACT_BAR, pageTopPx(WINDOW, WINDOW, true, WORKSPACE, m));
        assertEquals(COMPACT_BAR, pageTopPx(WINDOW, HOME, true, HOME_LAYOUT, m));
        assertEquals(COMPACT_BAR, pageTopPx(WINDOW, WINDOW, false, WORKSPACE, m));
    }

    /** Other bands on the top edge keep their room on the canvas; only the bar's is reached under. */
    @Test
    public void anotherTopBandKeepsItsRoomBesideTheBar() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        PlaceLayout workspace = layout(Edge.TOP, Edge.TOP);
        assertEquals(BAR, PageTopInsets.canvasReachPx(true, true, workspace, m));
        int atRest = EdgeStackPolicy.stackThicknessPx(workspace, Edge.TOP, m);
        int sliding = atRest - PageTopInsets.canvasReachPx(true, true, workspace, m)
            + PageTopInsets.workspaceTopInsetPx(true, true, workspace, workspace, m);
        assertEquals(BAR + AZ_ROW, atRest);
        assertEquals(atRest, sliding);
    }

    @Test
    public void aBarElsewhereOrPutAwayClaimsNoBand() {
        EdgeStackPolicy.Metrics m = metrics(BAR);
        PlaceLayout bottomBar = layout(Edge.BOTTOM, Edge.BOTTOM);
        assertEquals(0, PageTopInsets.canvasReachPx(true, true, bottomBar, m));
        assertEquals(0, PageTopInsets.workspaceTopInsetPx(true, true, HOME_LAYOUT, bottomBar, m));
        assertEquals("compatibility mode shows no bar", 0,
            PageTopInsets.canvasReachPx(true, false, WORKSPACE, m));
        assertEquals(0, PageTopInsets.workspaceTopInsetPx(true, false, HOME_LAYOUT, WORKSPACE, m));
        assertEquals("below the bar there is nothing to inset", 0,
            PageTopInsets.workspaceTopInsetPx(false, true, WORKSPACE, WORKSPACE, m));
    }

    /** The flat body surface keeps the top the canvas used to have: below the bar on a window. */
    @Test
    public void theBodySurfaceStaysBelowTheBarOnAWindow() {
        assertEquals(BAR, PageTopInsets.bodySurfaceTopPx(WINDOW, BAR));
        assertEquals(0, PageTopInsets.bodySurfaceTopPx(WINDOW, 0));
        assertEquals(0, PageTopInsets.bodySurfaceTopPx(HOME, BAR));
    }
}
