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

    // ---- Home's swipe up: the plane's top edge follows the finger 1:1 ------------------------

    /** A 2400 px host with the dock at 2100-2280 and the open plane from 0: a 2100 px crossing. */
    private static final AppDrawerTransitionGeometry.Frame DOCK =
        new AppDrawerTransitionGeometry.Frame(24f, 2100f, 1056f, 2280f);
    private static final AppDrawerTransitionGeometry.Frame OPEN =
        new AppDrawerTransitionGeometry.Frame(24f, 0f, 1056f, 2400f);
    /** The row pull's own travel on that host (0.3 of it, inside the 120-260 dp bounds at 2.75). */
    private static final float ROW_TRAVEL = 715f;
    private static final float MIN_TRAVEL = 330f;
    private static final float CLAIM_RAW_Y = 1500f;

    /**
     * The plane's top for a finger at {@code rawY}, composed the way the controller does it: the
     * pull axis is mirrored (up is positive), anchored at the claim, no slop, no hop.
     */
    private static float followTop(float rawY) {
        float travel = AppDrawerPullGeometry.followTravelPx(DOCK.top, OPEN.top, MIN_TRAVEL,
            ROW_TRAVEL);
        float p = AppDrawerTransitionGeometry.progressForDrag(-rawY, -CLAIM_RAW_Y, 0f, travel);
        float lift = -AppDrawerPullGeometry.FOLLOW_LIFT_PX
            * AppDrawerTransitionGeometry.dockLiftFraction(p);
        return AppDrawerTransitionGeometry.resolvePlaneFrame(DOCK, OPEN, p, lift).top;
    }

    @Test public void aFingerMovedUpNPixelsMovesTheDrawerTopNPixels() {
        assertEquals(DOCK.top, followTop(CLAIM_RAW_Y), 0f);
        for (float n : new float[] {1f, 7f, 40f, 100f, 333f, 800f, 1499f}) {
            assertEquals("finger up " + n, n, DOCK.top - followTop(CLAIM_RAW_Y - n), 0.01f);
        }
    }

    @Test public void aFingerComingBackDownTakesTheDrawerTopBackDownWithIt() {
        float up = 900f;
        float topAtPeak = followTop(CLAIM_RAW_Y - up);
        for (float n : new float[] {1f, 25f, 300f, 899f}) {
            float rawY = CLAIM_RAW_Y - up + n;
            assertEquals("finger down " + n, n, followTop(rawY) - topAtPeak, 0.01f);
        }
        // Below the claim point the drawer stays where it started rather than sinking under it.
        assertEquals(DOCK.top, followTop(CLAIM_RAW_Y + 200f), 0f);
    }

    @Test public void theFollowedEdgeStopsAtTheOpenTop() {
        assertEquals(OPEN.top, followTop(CLAIM_RAW_Y - DOCK.top - 400f), 0f);
    }

    @Test public void theRowPullsTravelWouldOutrunTheFinger() {
        // The mapping the follow replaces: the row pull's travel and hop under a Home finger. At
        // 100 px of finger the top had already moved about three times as far.
        float p = AppDrawerTransitionGeometry.progressForDrag(100f, 0f, 0f, ROW_TRAVEL);
        float lift = -24f * AppDrawerTransitionGeometry.dockLiftFraction(p);
        float moved = DOCK.top
            - AppDrawerTransitionGeometry.resolvePlaneFrame(DOCK, OPEN, p, lift).top;
        assertTrue("moved " + moved, moved > 250f);
    }

    @Test public void aFollowReleasesAtTheSameFingerDistanceAsARowPull() {
        float follow = AppDrawerPullGeometry.followTravelPx(DOCK.top, OPEN.top, MIN_TRAVEL,
            ROW_TRAVEL);
        for (float finger : new float[] {30f, 100f, 286f, 500f}) {
            float rowP = AppDrawerTransitionGeometry.progressForDrag(finger, 0f, 0f, ROW_TRAVEL);
            float followP = AppDrawerTransitionGeometry.progressForDrag(finger, 0f, 0f, follow);
            assertEquals("open " + finger, rowP,
                AppDrawerPullGeometry.releaseProgress(followP, 0f, follow, ROW_TRAVEL), 1e-4f);
            // A close drag on the plane a follow opened keeps the follow's travel.
            assertEquals("close " + finger, 1f - rowP,
                AppDrawerPullGeometry.releaseProgress(1f - followP, 1f, follow, ROW_TRAVEL), 1e-4f);
        }
        // A row pull reads its own progress unchanged.
        assertEquals(0.37f, AppDrawerPullGeometry.releaseProgress(0.37f, 0f, ROW_TRAVEL, ROW_TRAVEL),
            0f);
    }

    @Test public void aSeedWithLittleToCrossKeepsTheRowTravel() {
        assertEquals(2100f, AppDrawerPullGeometry.followTravelPx(2100f, 0f, MIN_TRAVEL, ROW_TRAVEL),
            0f);
        // A top row: its top is barely below the open plane's, so a full open would take a few
        // pixels of finger.
        assertEquals(ROW_TRAVEL, AppDrawerPullGeometry.followTravelPx(60f, 0f, MIN_TRAVEL,
            ROW_TRAVEL), 0f);
        assertEquals(ROW_TRAVEL, AppDrawerPullGeometry.followTravelPx(-10f, 0f, MIN_TRAVEL,
            ROW_TRAVEL), 0f);
    }
}
