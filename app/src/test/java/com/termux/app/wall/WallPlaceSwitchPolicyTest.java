package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** The thumb follows the wall: at rest on the current place, between places while it moves. */
public class WallPlaceSwitchPolicyTest {

    private static final float EPS = 0.001f;
    private static final List<PaneWallPage> ALL = PaneWallPolicy.availablePages(false, true, true);

    @Test public void atRestTheThumbIsOnTheCurrentPlace() {
        assertEquals(1f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.TERMINAL, 0f, 1000), EPS);
        assertEquals(2f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.DISPLAY, 0f, 1000), EPS);
    }

    @Test public void aDragTowardsANeighbourMovesTheThumbWithTheFinger() {
        // The wall displaced left by a quarter page: Display is coming in from the right.
        assertEquals(1.25f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.TERMINAL, -250f, 1000), EPS);
        // Displaced right: Widgets is coming in from the left.
        assertEquals(0.5f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.TERMINAL, 500f, 1000), EPS);
    }

    @Test public void aSlideAfterAPageChangeStartsOnTheOldPlaceAndLandsOnTheNew() {
        // goTo(DISPLAY) from the terminal carries the offset: current is Display, displaced a
        // whole page to the right, so the thumb still reads Terminal and then travels.
        assertEquals(1f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.DISPLAY, 1000f, 1000), EPS);
        assertEquals(1.5f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.DISPLAY, 500f, 1000), EPS);
        assertEquals(2f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.DISPLAY, 0f, 1000), EPS);
    }

    @Test public void aWrapCrossesThePillTheLongWay() {
        // Widgets -> Display round the ring: Display slides in from the left, the thumb travels
        // from the first segment to the third.
        assertEquals(1f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.WIDGETS, 500f, 1000), EPS);
    }

    @Test public void aRubberBandAtALinesEndLeavesTheThumbAlone() {
        List<PaneWallPage> line = PaneWallPolicy.availablePages(false, false, true);
        assertEquals(1f, WallPlaceSwitchPolicy.thumbPosition(line, PaneWallPage.DISPLAY, -300f, 1000), EPS);
    }

    @Test public void anOvershootIsClampedToTheNeighbour() {
        assertEquals(2f, WallPlaceSwitchPolicy.thumbPosition(ALL, PaneWallPage.TERMINAL, -1500f, 1000), EPS);
    }
}
