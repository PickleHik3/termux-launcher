package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.wall.PaneWallPage;

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
    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    private static final PlaceChromeTravel.States STATES = place -> new Rest(false, false,
        place != PaneWallPage.WIDGETS);

    /** The frame with the wall {@code fraction} of the way from Home to the terminal. */
    private static Frame homeToTerminal(float fraction) {
        return PlaceChromeTravel.at(ROW, PaneWallPage.WIDGETS, -fraction * WIDTH, WIDTH, STATES);
    }

    /** The same frame, read from the terminal's side: the commit at release does not move it. */
    private static Frame terminalToHome(float fraction) {
        return PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, (1f - fraction) * WIDTH, WIDTH,
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
        assertTrue(SeamTravel.crosses(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL));
        assertTrue(SeamTravel.crosses(PaneWallPage.DISPLAY, PaneWallPage.WIDGETS));
        assertFalse(SeamTravel.crosses(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        assertFalse(SeamTravel.crosses(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS));
    }

    @Test
    public void progressIntoTheOtherHalfIsOneNumberFromEitherSide() {
        for (float fraction : new float[] {0f, 0.08f, 0.25f, 0.5f, 0.75f, 0.92f, 1f}) {
            float fromHome = SeamTravel.otherHalfProgress(homeToTerminal(fraction),
                PaneWallPage.WIDGETS);
            // Committed to the terminal, the same frame is 1 - that far into the other half.
            float fromTerminal = SeamTravel.otherHalfProgress(terminalToHome(fraction),
                PaneWallPage.TERMINAL);
            assertEquals(PlaceChromeTravel.chromeFraction(fraction), fromHome, EPSILON);
            assertEquals(1f - fromHome, fromTerminal, EPSILON);
        }
    }

    @Test
    public void aFrameOnTheCommittedHalfHasNoOtherSide() {
        Frame windows = PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, -0.4f * WIDTH, WIDTH,
            STATES);
        assertNull(SeamTravel.otherHalfPage(windows, PaneWallPage.TERMINAL));
        assertEquals(0f, SeamTravel.otherHalfProgress(windows, PaneWallPage.TERMINAL), EPSILON);
        assertSame(PaneWallPage.TERMINAL,
            SeamTravel.otherHalfPage(homeToTerminal(0.3f), PaneWallPage.WIDGETS));
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

    @Test
    public void differentRowsOnEachHalfDip() {
        assertSame(DockPlan.DIP, SeamTravel.dockPlan(homeWithDock(), workspaceWithDock()));
        assertSame(DockPlan.DIP, SeamTravel.dockPlan(workspaceWithDock(), homeWithDock()));
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
    public void aDipSwapsTheRowsWhereNeitherShows() {
        assertEquals(1f, SeamTravel.dockReveal(DockPlan.DIP, 0f), EPSILON);
        assertEquals(0.5f, SeamTravel.dockReveal(DockPlan.DIP, 0.25f), EPSILON);
        assertEquals(0f, SeamTravel.dockReveal(DockPlan.DIP, SeamTravel.DIP_SWAP), EPSILON);
        assertEquals(0.5f, SeamTravel.dockReveal(DockPlan.DIP, 0.75f), EPSILON);
        assertEquals(1f, SeamTravel.dockReveal(DockPlan.DIP, 1f), EPSILON);
        assertFalse(SeamTravel.showsOtherDock(DockPlan.DIP, 0.49f));
        assertTrue(SeamTravel.showsOtherDock(DockPlan.DIP, SeamTravel.DIP_SWAP));
        // Just either side of the swap almost nothing shows, so the swap itself is unseen.
        assertTrue(SeamTravel.dockReveal(DockPlan.DIP, 0.499f) < 0.01f);
        assertTrue(SeamTravel.dockReveal(DockPlan.DIP, 0.501f) < 0.01f);
    }

    @Test
    public void theRevealIsContinuousOverAWholeSlideFromEitherEnd() {
        // Read through the real frames, committed at Home: no step between frames jumps.
        DockPlan plan = SeamTravel.dockPlan(homeWithDock(), workspaceWithDock());
        float previous = SeamTravel.dockReveal(plan,
            SeamTravel.otherHalfProgress(homeToTerminal(0f), PaneWallPage.WIDGETS));
        for (int i = 1; i <= 200; i++) {
            float fraction = i / 200f;
            float reveal = SeamTravel.dockReveal(plan,
                SeamTravel.otherHalfProgress(homeToTerminal(fraction), PaneWallPage.WIDGETS));
            assertTrue("jump at " + fraction, Math.abs(reveal - previous) < 0.03f);
            previous = reveal;
        }
        assertEquals(1f, previous, EPSILON);
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
        Frame windows = PlaceChromeTravel.at(ROW, PaneWallPage.TERMINAL, -0.4f * WIDTH, WIDTH,
            STATES);
        assertEquals(0f, SeamTravel.workspaceScreenXPx(windows, WIDTH), EPSILON);
        Frame home = PlaceChromeTravel.rest(PaneWallPage.WIDGETS, STATES.restOf(PaneWallPage.WIDGETS));
        assertEquals(WIDTH, SeamTravel.workspaceScreenXPx(home, WIDTH), EPSILON);
        Frame terminal = PlaceChromeTravel.rest(PaneWallPage.TERMINAL,
            STATES.restOf(PaneWallPage.TERMINAL));
        assertEquals(0f, SeamTravel.workspaceScreenXPx(terminal, WIDTH), EPSILON);
    }
}
