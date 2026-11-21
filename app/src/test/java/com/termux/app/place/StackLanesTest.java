package com.termux.app.place;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.StackLanes.Where;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * The bottom stack's side lanes as pure functions: which lane a row stands in from both layouts
 * at once, the one parent a host has for both, and the band laid out once at the union of both
 * sides' rests.
 */
public class StackLanesTest {

    private static final int S = StackLanes.STACK;
    private static final int H = StackLanes.HOME;
    private static final int W = StackLanes.TERMINAL;

    @Test public void theExtraKeysAreTheTerminalsLaneAndTheSharedAppsRowIsTheStacks() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        assertEquals(S, StackLanes.laneOf(Element.APPS, f.home, f.window));
        assertEquals(W, StackLanes.laneOf(Element.EXTRA_KEYS, f.home, f.window));
        assertEquals(S, StackLanes.laneOf(Element.STATUS, f.home, f.window));
    }

    @Test public void aRowOnlyHomeShowsIsHomesLane() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        PlaceLayout bare = f.window.withSlot(Element.APPS, Slot.hiddenFrom(Edge.BOTTOM, 0));
        assertEquals(H, StackLanes.laneOf(Element.APPS, f.home, bare));
        assertEquals(W, StackLanes.laneOf(Element.APPS, bare, f.home));
    }

    @Test public void aHostHasOneParentForBothLayouts() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        // The extra keys are only the Terminal's: standing in the band over the keyboard for
        // both, whichever layout is in front.
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.EXTRA_KEYS, f.home, f.window));
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.EXTRA_KEYS, f.window, f.home));
        // A shared row stands where both stand it, from either front.
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.APPS, f.home, f.window));
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.APPS, f.window, f.home));
        assertFalse(StackLanes.conflicts(Element.APPS, f.home, f.window));
    }

    @Test public void aRowTheOtherSideStandsOnAnEdgeIsNotPulledOntoTheFrontSide() {
        PlaceLayout home = LayoutFixtures.HOME;
        PlaceLayout rail = home.withSlot(Element.APPS, Slot.on(Edge.LEFT, 0));
        // Home hides nothing; the Terminal puts the apps on the left rail: front Home keeps its
        // own parent, and a front that hides it does not stand the other's rail row.
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.APPS, home, rail));
        PlaceLayout hidden = home.withSlot(Element.APPS, Slot.hiddenFrom(Edge.BOTTOM, 0));
        assertSame(Where.NONE, StackLanes.resolve(Element.APPS, hidden, rail));
        assertTrue(StackLanes.conflicts(Element.APPS, home, rail));
        assertSame("the front layout decides a real conflict", Where.LEFT,
            StackLanes.resolve(Element.APPS, rail, home));
    }

    @Test public void theFixturesWhereAppsStandUnderTheKeyboardOnlyOnTheTerminalKeepTheFrontsParent() {
        LayoutFixtures.Fixture f = LayoutFixtures.PONG;
        assertTrue(StackLanes.conflicts(Element.APPS, f.home, f.window));
        assertSame(Where.BOTTOM_OVER, StackLanes.resolve(Element.APPS, f.home, f.window));
        assertSame(Where.BOTTOM_UNDER, StackLanes.resolve(Element.APPS, f.window, f.home));
    }

    @Test public void theHostsOnTheBandAreTheFrontsOrderPlusTheOtherSidesRows() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        assertEquals(EdgeStackPolicy.overKeyboard(f.window),
            StackLanes.hostsOn(f.window, f.home, Where.BOTTOM_OVER));
        // From Home the Terminal's extra keys join, in the Terminal's order.
        assertEquals(EdgeStackPolicy.overKeyboard(f.window),
            StackLanes.hostsOn(f.home, f.window, Where.BOTTOM_OVER));
        assertEquals(Collections.emptyList(),
            StackLanes.hostsOn(f.home, f.window, Where.BOTTOM_UNDER));
        // Alone, a layout's hosts are its own list, as before.
        assertEquals(EdgeStackPolicy.overKeyboard(f.window),
            StackLanes.hostsOn(f.window, f.window, Where.BOTTOM_OVER));
    }

    @Test public void lanesOnlyStandWhereASideHasRowsOfItsOwn() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        assertTrue(StackLanes.hasLanes(f.home, f.window, Side.HOME));
        assertTrue(StackLanes.hasLanes(f.window, f.home, Side.TERMINAL));
        PlaceLayout noKeys = f.window.withSlot(Element.EXTRA_KEYS,
            Slot.hiddenFrom(Edge.BOTTOM, 2));
        assertFalse(StackLanes.hasLanes(f.home, noKeys, Side.HOME));
    }

    @Test public void theBandIsTheSharedRowsPlusTheTallerLane() {
        // Top to bottom: the shared apps row, the Terminal's extra keys under it.
        int[] height = {100, 98};
        int[] lane = {S, W};
        int[] top = new int[2];
        // Home in front: the apps row stands on the band's floor, the extra keys' room above it.
        assertEquals(198, StackLanes.layoutBand(height, lane, 2, H, top));
        assertArrayEquals(new int[] {98, 100}, top);
        // The Terminal in front: the apps row stands on the extra keys.
        assertEquals(198, StackLanes.layoutBand(height, lane, 2, W, top));
        assertArrayEquals(new int[] {0, 100}, top);
    }

    @Test public void bothLanesStackBottomUpInOneBand() {
        // Top to bottom: Home's two rows, the Terminal's one, a shared row under them.
        int[] height = {40, 30, 90, 50};
        int[] lane = {H, H, W, S};
        int[] top = new int[4];
        int band = StackLanes.layoutBand(height, lane, 4, H, top);
        assertEquals("shared 50 + the taller lane (Home's 70 against the Terminal's 90)", 140, band);
        assertEquals(0, top[2]);
        assertEquals(90, top[3]);
        assertEquals(20, top[0]);
        assertEquals(60, top[1]);
    }

    @Test public void withEveryRowSharedTheBandIsThePlainColumn() {
        int[] height = {10, 20, 30};
        int[] lane = {S, S, S};
        int[] top = new int[3];
        assertEquals(60, StackLanes.layoutBand(height, lane, 3, W, top));
        assertArrayEquals(new int[] {0, 10, 30}, top);
        assertEquals(60, StackLanes.layoutBand(height, lane, 3, H, top));
        assertArrayEquals(new int[] {0, 10, 30}, top);
    }

    @Test public void aGoneRowTakesNoRoom() {
        int[] height = {100, 0, 98};
        int[] lane = {S, W, W};
        int[] top = new int[3];
        assertEquals(198, StackLanes.layoutBand(height, lane, 3, H, top));
        assertEquals(98, top[0]);
    }

    @Test public void slackIsWhatTheOtherSideReachesOverTheFront() {
        assertEquals(98, StackLanes.slackPx(300, 398));
        assertEquals(0, StackLanes.slackPx(398, 300));
        assertEquals(0, StackLanes.slackPx(300, 300));
    }

    @Test public void aCrossingsCompositeAddsItsRowsAndNeverTakesALanesAway() {
        java.util.List<Element> rest = Arrays.asList(Element.APPS, Element.AZ);
        // The composite (the seam's layout while a crossing runs) lacks Home's AZ lane row and
        // has a status bar of its own: the band keeps AZ and gains the bar after the row it follows.
        java.util.List<Element> composite = Arrays.asList(Element.APPS, Element.STATUS);
        assertEquals(Arrays.asList(Element.APPS, Element.STATUS, Element.AZ),
            StackLanes.standWith(rest, composite));
        assertEquals(rest, StackLanes.standWith(rest, Collections.<Element>emptyList()));
        assertEquals(Arrays.asList(Element.STATUS, Element.APPS, Element.AZ),
            StackLanes.standWith(rest, Arrays.asList(Element.STATUS, Element.APPS)));
    }

    @Test public void aLaneSideIsReadFromTheTwoLayoutsAloneAndStaysTheSameFromEitherFront() {
        LayoutFixtures.Fixture f = LayoutFixtures.TL16;
        PlaceLayout bare = f.window.withSlot(Element.APPS, Slot.hiddenFrom(Edge.BOTTOM, 0));
        assertSame(Side.HOME, StackLanes.laneSide(Element.APPS, f.home, bare));
        assertSame(Side.TERMINAL, StackLanes.laneSide(Element.APPS, bare, f.home));
        assertSame(Side.TERMINAL, StackLanes.laneSide(Element.EXTRA_KEYS, f.home, f.window));
        org.junit.Assert.assertNull(StackLanes.laneSide(Element.APPS, f.home, f.window));
        // The seam's composite while Home crosses to the Terminal is Home's layout standing the
        // Terminal's dock: with no apps row on that dock it has no Home lane. Read as the front it
        // lost the lane (the landing then wrote it); the two own layouts keep it.
        assertSame(Side.HOME, StackLanes.laneSide(Element.APPS, f.home, bare));
        org.junit.Assert.assertNull(StackLanes.laneSide(Element.APPS, bare, bare));
    }

    @Test public void theLaneOfASideIsItsOwn() {
        assertEquals(H, StackLanes.laneOf(Side.HOME));
        assertEquals(W, StackLanes.laneOf(Side.TERMINAL));
        assertEquals(Arrays.asList(Element.APPS, Element.AZ, Element.EXTRA_KEYS),
            Arrays.asList(StackLanes.ROWS));
    }
}
