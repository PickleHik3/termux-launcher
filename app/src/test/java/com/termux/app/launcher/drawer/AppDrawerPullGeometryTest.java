package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Pull;
import com.termux.app.launcher.drawer.AppDrawerPullGeometry.Seed;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/**
 * The drawer's direction, per edge: which way it is pulled, and which rectangle it grows out of.
 *
 * <p>Pure — no view, no Robolectric. Both answers are one function of the pinned apps row's edge,
 * which is what keeps the gesture and the animation on the same axis.
 */
public class AppDrawerPullGeometryTest {

    @Test public void everyEdgePullsTowardsTheMiddle_exceptTheDockItShipsOn() {
        assertEquals(Pull.RIGHT, AppDrawerPullGeometry.pullFor(Edge.LEFT));
        assertEquals(Pull.LEFT, AppDrawerPullGeometry.pullFor(Edge.RIGHT));
        assertEquals(Pull.DOWN, AppDrawerPullGeometry.pullFor(Edge.TOP));
        // The one that runs off the edge rather than towards the middle, and stays that way: it is
        // the gesture every install already has.
        assertEquals(Pull.DOWN, AppDrawerPullGeometry.pullFor(Edge.BOTTOM));
    }

    @Test public void thePlaneGrowsOutOfTheRowTheFingerIsOn() {
        assertEquals(Seed.RAIL, AppDrawerPullGeometry.seedFor(Edge.LEFT));
        assertEquals(Seed.RAIL, AppDrawerPullGeometry.seedFor(Edge.RIGHT));
        assertEquals(Seed.PLANK, AppDrawerPullGeometry.seedFor(Edge.TOP));
        assertEquals(Seed.DOCK, AppDrawerPullGeometry.seedFor(Edge.BOTTOM));
    }

    @Test public void aRailsTravelIsTheScreensWidth_andARowsItsHeight() {
        assertTrue(AppDrawerPullGeometry.isHorizontal(Pull.RIGHT));
        assertTrue(AppDrawerPullGeometry.isHorizontal(Pull.LEFT));
        assertFalse(AppDrawerPullGeometry.isHorizontal(Pull.DOWN));

        assertEquals(1080f, AppDrawerPullGeometry.travelSpanPx(Pull.RIGHT, 1080f, 2400f), 0f);
        assertEquals(1080f, AppDrawerPullGeometry.travelSpanPx(Pull.LEFT, 1080f, 2400f), 0f);
        assertEquals(2400f, AppDrawerPullGeometry.travelSpanPx(Pull.DOWN, 1080f, 2400f), 0f);
        assertEquals(2400f, AppDrawerPullGeometry.travelSpanPx(Pull.NONE, 1080f, 2400f), 0f);
    }

    @Test public void onlyTheDockHops() {
        // The hop is a vertical lift written to the dock's glass and its rows; a plane seeded from
        // a column would be hopping a rectangle the finger is not on.
        assertEquals(24f, AppDrawerPullGeometry.liftPxFor(Pull.DOWN, 24f), 0f);
        assertEquals(0f, AppDrawerPullGeometry.liftPxFor(Pull.RIGHT, 24f), 0f);
        assertEquals(0f, AppDrawerPullGeometry.liftPxFor(Pull.LEFT, 24f), 0f);
    }
}
