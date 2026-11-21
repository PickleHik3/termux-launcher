package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.launcher.paging.PageTickStrip;
import com.termux.app.page.Side;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/**
 * The dock's apps row in a side's lane stands with the Terminal in front, and through a crossing
 * back to Home, exactly as at its side's rest: which side of the row its ticks take, and so where
 * its icons stand in the band, is read from the row's own side's layout whichever page is in
 * front. Before, the views took it from the front layout, so Home's row slid in one strip band
 * higher and dropped into place at the landing (pong, Terminal to Home, 34 px).
 */
public class AppsRowTicksParityTest {

    private static final boolean[] BOOLS = {false, true};
    private static final PageRef[] PAGES = {PageRef.HOME, PageRef.TERMINAL};

    @NonNull
    private static PlaceLayout front(@NonNull LayoutFixtures.Fixture f, @NonNull PageRef page) {
        return f.layoutOf(page);
    }

    /** The icons' centre in the band the dress gives the row while {@code front} is in front. */
    private static float dressedCentre(@NonNull LayoutFixtures.Fixture f, @NonNull PageRef front,
                                       @NonNull DockLayout rowsLayout) {
        boolean next = DockGeometry.appsRowNextToCanvas(front(f, front), f.home, f.window);
        return rowsLayout.appsIconCentreInBandPx(
            PageTickStrip.ticksLeadRow(PlaceLayout.Edge.BOTTOM, next));
    }

    @Test public void aLaneRowIsOrderedByItsSidesLayoutFromEitherFront() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures()) {
            Side lane = StackLanes.laneSide(Element.APPS, f.home, f.window);
            if (lane == null) continue;
            PlaceLayout own = lane == Side.HOME ? f.home : f.window;
            boolean rest = DockGeometry.appsRowNextToCanvas(own);
            for (PageRef page : PAGES) {
                assertEquals(f.name + " front=" + page, rest,
                    DockGeometry.appsRowNextToCanvas(front(f, page), f.home, f.window));
            }
        }
    }

    @Test public void aTerminalWithoutAnAppsRowLeavesHomesRowInHomesLaneWithItsTicksLeading() {
        // The arrangement the defect was seen on: the Terminal shows no apps row and its own stack
        // ends in its extra keys, Home's in its apps row, so the front layout's answer differed
        // with the Terminal in front.
        LayoutFixtures.Fixture f = LayoutFixtures.DEFAULT_INSTALL;
        assertEquals(Side.HOME, StackLanes.laneSide(Element.APPS, f.home, f.window));
        assertEquals(true, DockGeometry.appsRowNextToCanvas(f.home));
        assertEquals(false, DockGeometry.appsRowNextToCanvas(f.window));
        assertEquals(true, DockGeometry.appsRowNextToCanvas(f.window, f.home, f.window));
    }

    @Test public void theSlidingRowsIconsStandWhereHomesRestAndItsCardPutThem() {
        int checked = 0;
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures()) {
            Side lane = StackLanes.laneSide(Element.APPS, f.home, f.window);
            if (lane != Side.HOME) continue;
            checked++;
            for (boolean floating : BOOLS) {
                // Home's rest as the row is sized and dressed by it while the Terminal is in
                // front (TermuxActivity#noteLanes hands DockRows#setOtherLayout this), and as
                // Home's own pass has it.
                DockLayout parked = DockGeometry.compute(
                    f.snapshot(PageRef.TERMINAL, floating).restOf(PageRef.HOME));
                DockLayout home = DockGeometry.compute(f.snapshot(PageRef.HOME, floating));
                // Where Home's card is cut (RestCards): Home's own layout's answer.
                float card = home.appsIconCentreInBandPx(PageTickStrip.ticksLeadRow(
                    PlaceLayout.Edge.BOTTOM, DockGeometry.appsRowNextToCanvas(f.home)));
                String where = f.name + " floating=" + floating;
                assertEquals(where + ": Home's rest", card,
                    dressedCentre(f, PageRef.HOME, home), 0f);
                assertEquals(where + ": sliding in from the Terminal", card,
                    dressedCentre(f, PageRef.TERMINAL, parked), 0f);
            }
        }
        assertTrue("some fixture stands Home's apps row in Home's lane", checked > 0);
    }
}
