package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.wall.PageRef;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The chrome across the seam between Home and the Workspace as functions of the wall's frame:
 * which way the dock's rows cross, how much of them shows at every step, where they are swapped,
 * and where the status bar stands while it travels with the Workspace's page.
 */
public class SeamTravelTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;

    /** The row as it stands: Home pages on the left, the Workspace's windows on the right. */
    private static final List<PageKind> ROW =
        Arrays.asList(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY);

    private static final PlaceChromeTravel.States STATES = place -> new Rest(false, false,
        place != PageKind.HOME);

    /** The frame with the wall {@code fraction} of the way from Home to the terminal. */
    private static Frame homeToTerminal(float fraction) {
        return PlaceChromeTravel.at(ROW, PageKind.HOME, -fraction * WIDTH, WIDTH, STATES);
    }

    /** The same frame, read from the terminal's side: the commit at release does not move it. */
    private static Frame terminalToHome(float fraction) {
        return PlaceChromeTravel.at(ROW, PageKind.TERMINAL, (1f - fraction) * WIDTH, WIDTH,
            STATES);
    }

    private static PlaceLayout layout(Map<Element, Slot> slots) {
        Map<Element, Slot> all = new EnumMap<>(Element.class);
        for (Element element : Element.values())
            all.put(element, Slot.on(Edge.BOTTOM, element).withHidden(true));
        all.putAll(slots);
        return new PlaceLayout(all, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** Home as pong has it: the pinned apps and the index, as overlays on the bottom edge. */
    private static PlaceLayout homeWithDock() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        return layout(slots);
    }

    /** The Workspace as pong has it: the status bar along the top, no dock rows at all. */
    private static PlaceLayout workspaceWithoutDock() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        return layout(slots);
    }

    /** The shipped Workspace: the status bar on top, apps, index and extra keys on the bottom. */
    private static PlaceLayout workspaceWithDock() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        return layout(slots);
    }

    // ---- the halves ---------------------------------------------------------------------------

    @Test
    public void onlyAFrameBetweenHomeAndAWindowCrosses() {
        assertTrue(SeamTravel.crosses(PageKind.HOME, PageKind.TERMINAL));
        assertTrue(SeamTravel.crosses(PageKind.DISPLAY, PageKind.HOME));
        assertFalse(SeamTravel.crosses(PageKind.TERMINAL, PageKind.DISPLAY));
        assertFalse(SeamTravel.crosses(PageKind.HOME, PageKind.HOME));
    }

    @Test
    public void progressIntoTheOtherHalfIsOneNumberFromEitherSide() {
        for (float fraction : new float[] {0f, 0.08f, 0.25f, 0.5f, 0.75f, 0.92f, 1f}) {
            float fromHome = SeamTravel.otherHalfProgress(homeToTerminal(fraction),
                PageKind.HOME);
            // Committed to the terminal, the same frame is 1 - that far into the other half.
            float fromTerminal = SeamTravel.otherHalfProgress(terminalToHome(fraction),
                PageKind.TERMINAL);
            assertEquals(StackScene.materialShare(fraction), fromHome, EPSILON);
            assertEquals(1f - fromHome, fromTerminal, EPSILON);
        }
    }

    @Test
    public void aFrameOnTheCommittedHalfHasNoOtherSide() {
        Frame windows = PlaceChromeTravel.at(ROW, PageKind.TERMINAL, -0.4f * WIDTH, WIDTH,
            STATES);
        assertNull(SeamTravel.otherHalfPage(windows, PageKind.TERMINAL));
        assertEquals(0f, SeamTravel.otherHalfProgress(windows, PageKind.TERMINAL), EPSILON);
        assertSame(PageKind.TERMINAL,
            SeamTravel.otherHalfPage(homeToTerminal(0.3f), PageKind.HOME));
    }

    // ---- the dock -----------------------------------------------------------------------------

    @Test
    public void aWorkspaceWithoutADockLetsHomesSlideOutAndIn() {
        // Leaving Home: Home's rows go down with the whole way; arriving there, they rise.
        assertSame(DockPlan.SLIDE_OUT,
            SeamTravel.dockPlan(homeWithDock(), workspaceWithoutDock()));
        assertSame(DockPlan.SLIDE_IN,
            SeamTravel.dockPlan(workspaceWithoutDock(), homeWithDock()));
    }

    /** A Home layout of its own that keeps only the pinned apps. */
    private static PlaceLayout homeAppsOnly() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        return layout(slots);
    }

    /** A window whose bottom stack is, outermost first, {@code outermostFirst}. */
    private static PlaceLayout windowStacking(Element... outermostFirst) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (int order = 0; order < outermostFirst.length; order++) {
            Element element = outermostFirst[order];
            slots.put(element, Slot.on(Edge.BOTTOM, element).withOrder(order));
        }
        return layout(slots);
    }

    // ---- the fold lands on Home's rest (#94: "the dock moves down, then snaps back up") -------

    /** The stack's container bottom on screen, which the margin under it fixes on both halves. */
    private static final int BOTTOM = 2000;
    private static final float DENSITY = 2.75f;
    private static final int APPS_PX = 150;
    private static final int KEYS_PX = 110;

    /** The A-Z row as the real policy lays it out in a stack: its chin only on the dock's rim. */
    private static int azPx(List<Element> outermostFirst) {
        return com.termux.app.terminal.AccessoryStackLayoutPolicy.computeAzRowHeightPx(true,
            com.termux.app.terminal.AccessoryStackLayoutPolicy.rowOverAz(outermostFirst),
            com.termux.app.terminal.AccessoryStackLayoutPolicy.rowUnderAz(outermostFirst),
            DENSITY);
    }

    /** The rows a layout's bottom stack lays out at rest, less the extra keys row itself. */
    private static int restRowsPx(PlaceLayout layout) {
        List<Element> stack = EdgeStackPolicy.stack(layout, Edge.BOTTOM);
        return (stack.contains(Element.APPS) ? APPS_PX : 0)
            + (stack.contains(Element.AZ) ? azPx(stack) : 0);
    }

    @Test
    public void homesRowsRestTallerThanTheWindowsLessTheExtraKeysByTheAzChin() {
        // The shipped order: the extra keys outermost, under the letters. On Home the letters are
        // the rim and carry their chin, so Home's rows are not the window's less the keys.
        List<Element> window = EdgeStackPolicy.stack(workspaceWithDock(), Edge.BOTTOM);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS), window);
        int chin = Math.round(DENSITY * 10f);
        assertEquals(chin, restRowsPx(homeWithDock()) - restRowsPx(workspaceWithDock()));
    }

    @Test
    public void rowsThatDifferButBothHalvesStandStayPutAndSwapAtTheLanding() {
        // Nothing is laid out or slid on the way for rows that differ: the plan is none, the rows
        // stay whole, and the stack's lanes stand both sides' rows (D1-A).
        PlaceLayout window = windowStacking(Element.APPS, Element.EXTRA_KEYS, Element.AZ);
        PlaceLayout[][] pairs = {
            {homeAppsOnly(), workspaceWithDock()}, {workspaceWithDock(), homeAppsOnly()},
            {workspaceWithDock(), homeWithDock()}, {homeWithDock(), workspaceWithDock()},
            {window, homeWithDock()}};
        for (PlaceLayout[] pair : pairs) {
            assertSame(DockPlan.NONE, SeamTravel.dockPlan(pair[0], pair[1]));
        }
        for (float q : new float[] {0f, 0.3f, 0.5f, 0.8f, 1f}) {
            assertEquals(1f, SeamTravel.dockReveal(DockPlan.NONE, q), EPSILON);
        }
        assertFalse(SeamTravel.committedRowsLeave(DockPlan.NONE));
        assertFalse(SeamTravel.showsOtherDock(DockPlan.NONE, 0.5f));
        assertFalse(SeamTravel.showsOtherDock(DockPlan.NONE, 1f));
    }

    @Test
    public void theSameRowsOnBothHalvesStayPut() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        assertSame(DockPlan.NONE, SeamTravel.dockPlan(homeWithDock(), layout(slots)));
        assertEquals(1f, SeamTravel.dockReveal(DockPlan.NONE, 0.5f), EPSILON);
        assertFalse(SeamTravel.showsOtherDock(DockPlan.NONE, 1f));
    }

    @Test
    public void aRowOffTheBottomEdgeKeepsChangingAtSettle() {
        Map<Element, Slot> rail = new EnumMap<>(Element.class);
        rail.put(Element.APPS, Slot.on(Edge.LEFT, Element.APPS));
        assertFalse(SeamTravel.dockExchangeable(layout(rail)));
        assertSame(DockPlan.NONE, SeamTravel.dockPlan(layout(rail), workspaceWithoutDock()));

        Map<Element, Slot> bottomBar = new EnumMap<>(Element.class);
        bottomBar.put(Element.STATUS, Slot.on(Edge.BOTTOM, Element.STATUS));
        bottomBar.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        assertSame(DockPlan.NONE, SeamTravel.dockPlan(homeWithDock(), layout(bottomBar)));

        Map<Element, Slot> under = new EnumMap<>(Element.class);
        under.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS).withUnderKeyboard(true));
        assertFalse(SeamTravel.dockExchangeable(layout(under)));
    }

    @Test
    public void aSlideOutOrInMovesOverTheWholeWay() {
        for (float q : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
            assertEquals(1f - q, SeamTravel.dockReveal(DockPlan.SLIDE_OUT, q), EPSILON);
            assertEquals(q, SeamTravel.dockReveal(DockPlan.SLIDE_IN, q), EPSILON);
        }
        // A slide out never stands the other half's rows before settle; a slide in from the
        // first frame that moves, while they are still all below the screen.
        assertFalse(SeamTravel.showsOtherDock(DockPlan.SLIDE_OUT, 1f));
        assertFalse(SeamTravel.showsOtherDock(DockPlan.SLIDE_IN, 0f));
        assertTrue(SeamTravel.showsOtherDock(DockPlan.SLIDE_IN, 0.01f));
        assertEquals(0.01f, SeamTravel.dockReveal(DockPlan.SLIDE_IN, 0.01f), EPSILON);
    }

    @Test
    public void theExchangedLayoutTakesOnlyTheDockRows() {
        PlaceLayout exchanged = SeamTravel.withDockOf(workspaceWithoutDock(), homeWithDock());
        assertFalse(exchanged.slot(Element.APPS).hidden);
        assertFalse(exchanged.slot(Element.AZ).hidden);
        assertTrue(exchanged.slot(Element.EXTRA_KEYS).hidden);
        // The status bar is still the base's.
        assertEquals(workspaceWithoutDock().slot(Element.STATUS),
            exchanged.slot(Element.STATUS));

        PlaceLayout withBar = SeamTravel.withStatusOf(homeWithDock(), workspaceWithoutDock());
        assertFalse(withBar.slot(Element.STATUS).hidden);
        assertSame(Edge.TOP, withBar.slot(Element.STATUS).edge);
        assertEquals(homeWithDock().slot(Element.APPS), withBar.slot(Element.APPS));
    }

    // ---- the status bar -----------------------------------------------------------------------

    @Test
    public void onlyABarAlongTheTopRides() {
        assertTrue(SeamTravel.statusRides(workspaceWithoutDock()));
        assertFalse(SeamTravel.statusRides(homeWithDock()));
        Map<Element, Slot> side = new EnumMap<>(Element.class);
        side.put(Element.STATUS, Slot.on(Edge.LEFT, Element.STATUS));
        assertFalse(SeamTravel.statusRides(layout(side)));
    }

    @Test
    public void theBarArrivesOverHomeOnlyWhereNothingOfHomesWouldMove() {
        assertTrue(SeamTravel.statusCanArriveOver(homeWithDock()));
        Map<Element, Slot> rail = new EnumMap<>(Element.class);
        rail.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        rail.put(Element.AZ, Slot.on(Edge.RIGHT, Element.AZ));
        assertFalse(SeamTravel.statusCanArriveOver(layout(rail)));
        Map<Element, Slot> top = new EnumMap<>(Element.class);
        top.put(Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        assertFalse(SeamTravel.statusCanArriveOver(layout(top)));
    }

    @Test
    public void theBarStandsWhereTheWorkspacePageStands() {
        for (float fraction : new float[] {0f, 0.1f, 0.5f, 0.9f, 1f}) {
            // The terminal page comes in from the right: it is (1 - f) widths off its rest.
            assertEquals((1f - fraction) * WIDTH,
                SeamTravel.workspaceScreenXPx(homeToTerminal(fraction), WIDTH), 0.5f);
            // Read from the terminal's side the frame is the same, and so is the bar.
            assertEquals((1f - fraction) * WIDTH,
                SeamTravel.workspaceScreenXPx(terminalToHome(fraction), WIDTH), 0.5f);
        }
        // Between two windows the bar stays put; past Home it is off to the right.
        Frame windows = PlaceChromeTravel.at(ROW, PageKind.TERMINAL, -0.4f * WIDTH, WIDTH,
            STATES);
        assertEquals(0f, SeamTravel.workspaceScreenXPx(windows, WIDTH), EPSILON);
        Frame home = PlaceChromeTravel.rest(PageKind.HOME, STATES.restOf(PageKind.HOME));
        assertEquals(WIDTH, SeamTravel.workspaceScreenXPx(home, WIDTH), EPSILON);
        Frame terminal = PlaceChromeTravel.rest(PageKind.TERMINAL,
            STATES.restOf(PageKind.TERMINAL));
        assertEquals(0f, SeamTravel.workspaceScreenXPx(terminal, WIDTH), EPSILON);
    }

    // ---- Between two Home pages (#173 slice E) ----

    private static final PageRef HOME_2 = PageRef.of(PageKind.HOME, 2);
    private static final PageRef HOME_3 = PageRef.of(PageKind.HOME, 3);

    /** Home's pager with page 2 in front, shifted {@code shiftPx}, page 3 coming in. */
    private static Frame pager(float shiftPx) {
        return PlaceChromeTravel.homePages(STATES.restOf(PageKind.HOME), 2, 3, shiftPx,
            WIDTH);
    }

    @Test
    public void twoHomePagesCrossOnlyWhereBothIdsAreSaidAndDiffer() {
        assertTrue(SeamTravel.crosses(HOME_2, HOME_3));
        assertFalse(SeamTravel.crosses(HOME_2, PageRef.of(PageKind.HOME, 2)));
        assertFalse("a Home page with no id said", SeamTravel.crosses(HOME_2, PageRef.HOME));
        assertTrue(SeamTravel.crosses(HOME_2, PageRef.TERMINAL));
        assertFalse("two windows", SeamTravel.crosses(PageRef.TERMINAL,
            PageRef.of(PageKind.DISPLAY)));
    }

    @Test
    public void aPagerFrameReadsTheWayBetweenTwoHomePages() {
        for (float shift : new float[] {-300f, 300f}) {
            Frame frame = pager(shift);
            assertEquals(0.3f, frame.fraction, EPSILON);
            float chrome = StackScene.materialShare(0.3f);
            assertEquals(chrome, SeamTravel.otherEndProgress(frame, HOME_2), EPSILON);
            assertEquals("read from the arriving page", 1f - chrome,
                SeamTravel.otherEndProgress(frame, HOME_3), EPSILON);
            assertEquals(0.3f, SeamTravel.otherEndFraction(frame, HOME_2), EPSILON);
            // By half it never leaves Home: the kinds alone cannot tell the pages apart.
            assertEquals(0f, SeamTravel.otherHalfProgress(frame, PageKind.HOME), EPSILON);
        }
        Frame rest = pager(0f);
        assertEquals(0f, SeamTravel.otherEndProgress(rest, HOME_2), EPSILON);
        assertEquals(0, rest.towardSide);
    }

    @Test
    public void aWallFrameReadsByEndExactlyAsByHalf() {
        for (float fraction = 0f; fraction <= 1f; fraction += 0.05f) {
            Frame out = homeToTerminal(fraction);
            Frame back = terminalToHome(fraction);
            assertEquals(SeamTravel.otherHalfProgress(out, PageKind.HOME),
                SeamTravel.otherEndProgress(out, HOME_2), EPSILON);
            assertEquals(SeamTravel.otherHalfFraction(out, PageKind.HOME),
                SeamTravel.otherEndFraction(out, HOME_2), EPSILON);
            assertEquals(SeamTravel.otherHalfProgress(back, PageKind.TERMINAL),
                SeamTravel.otherEndProgress(back, PageRef.TERMINAL), EPSILON);
        }
    }

    @Test
    public void eachHomePageStandsWhereThePagerDrawsIt() {
        // Shifted left: the page coming in stands on the right.
        Frame left = pager(-300f);
        assertEquals(1, left.towardSide);
        assertEquals(-300f, SeamTravel.endScreenXPx(left, HOME_2, WIDTH), EPSILON);
        assertEquals(700f, SeamTravel.endScreenXPx(left, HOME_3, WIDTH), EPSILON);
        // Shifted right: it comes in from the left.
        Frame right = pager(300f);
        assertEquals(-1, right.towardSide);
        assertEquals(300f, SeamTravel.endScreenXPx(right, HOME_2, WIDTH), EPSILON);
        assertEquals(-700f, SeamTravel.endScreenXPx(right, HOME_3, WIDTH), EPSILON);
        // A page the frame does not show, or the arriving one at rest, is out of sight.
        assertEquals(WIDTH, SeamTravel.endScreenXPx(left, PageRef.of(PageKind.HOME, 7),
            WIDTH), EPSILON);
        assertEquals(WIDTH, SeamTravel.endScreenXPx(pager(0f), HOME_3, WIDTH), EPSILON);
    }

    @Test
    public void theBarCrossesWithTheHomePageThatAloneWearsItAlongTheTop() {
        PlaceLayout bare = homeWithDock();
        PlaceLayout barred = bare.withSlot(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        assertSame(barred, SeamTravel.homeBarWearer(bare, barred));
        assertSame(barred, SeamTravel.homeBarWearer(barred, bare));
        assertNull("worn alike", SeamTravel.homeBarWearer(barred, barred));
        assertNull("put away alike", SeamTravel.homeBarWearer(bare, bare));
        PlaceLayout bottomBar = bare.withSlot(Element.STATUS,
            Slot.on(Edge.BOTTOM, Element.STATUS));
        assertNull("a bar on the bottom edge changes at the landing",
            SeamTravel.homeBarWearer(bare, bottomBar));
        PlaceLayout topRow = bare.withSlot(Element.AZ, Slot.on(Edge.TOP, Element.AZ));
        assertNull("nothing arrives over a page whose top edge it would push in",
            SeamTravel.homeBarWearer(topRow, barred));
    }
}
