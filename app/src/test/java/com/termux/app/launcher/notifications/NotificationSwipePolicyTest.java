package com.termux.app.launcher.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Pull;
import com.termux.app.launcher.notifications.NotificationSwipePolicy.Swipe;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/**
 * The quick reply on a badged pinned icon: which way it runs, when it commits, and when it gives
 * the drag up to the drawer that wants the same axis.
 */
public class NotificationSwipePolicyTest {

    /** Phone of record: density 2.75, so slop is around 24px and the flick is 66px. */
    private static final float SLOP = 24f;
    private static final float FLICK = NotificationSwipePolicy.flickPx(2.75f);
    /** The drawer's own travel span on that phone, portrait: the screen's height. */
    private static final float SPAN = 2400f;
    private static final long TIMEOUT = 500L;

    @Test public void theSwipeAlwaysRunsTowardsTheMiddleOfTheScreen() {
        assertEquals(Swipe.UP, NotificationSwipePolicy.swipeFor(Edge.BOTTOM));
        assertEquals(Swipe.DOWN, NotificationSwipePolicy.swipeFor(Edge.TOP));
        assertEquals(Swipe.RIGHT, NotificationSwipePolicy.swipeFor(Edge.LEFT));
        assertEquals(Swipe.LEFT, NotificationSwipePolicy.swipeFor(Edge.RIGHT));
    }

    @Test public void onlyTheBottomDockPointsTheOtherWayFromTheDrawer() {
        assertFalse(NotificationSwipePolicy.contested(Edge.BOTTOM));
        assertTrue(NotificationSwipePolicy.commitsOnMove(Edge.BOTTOM));
        for (Edge edge : new Edge[] {Edge.TOP, Edge.LEFT, Edge.RIGHT}) {
            assertTrue(edge + " shares the drawer's axis", NotificationSwipePolicy.contested(edge));
            assertFalse(edge + " waits for the release",
                NotificationSwipePolicy.commitsOnMove(edge));
        }
    }

    // ------------------------------------------------------------------ the bottom dock, as it is

    @Test public void aBadgedSwipeUpOnTheDockCommitsOnTheMoveItArmsOn() {
        // Today's rule, kept whole: 1.8 slops up with the up dominating the sideways drift.
        assertTrue(NotificationSwipePolicy.armed(Edge.BOTTOM, 0f, -SLOP * 1.9f, SLOP));
        assertTrue(NotificationSwipePolicy.commitsOnMove(Edge.BOTTOM));
        // Short of the slop, or dragged sideways instead, it is not a quick reply.
        assertFalse(NotificationSwipePolicy.armed(Edge.BOTTOM, 0f, -SLOP * 1.5f, SLOP));
        assertFalse(NotificationSwipePolicy.armed(Edge.BOTTOM, 200f, -SLOP * 1.9f, SLOP));
        // And a drag down off the dock — the drawer's own pull — can never arm it.
        assertFalse(NotificationSwipePolicy.armed(Edge.BOTTOM, 0f, SLOP * 8f, SLOP));
    }

    @Test public void theDockNeverHandsOffBecauseNothingElseWantsTheWayItRuns() {
        assertFalse(NotificationSwipePolicy.handsOff(Edge.BOTTOM, 0f, -SPAN, SPAN));
    }

    // ------------------------------------------------------------------- the three shared edges

    @Test public void aShortFlickTowardsTheMiddleCommitsOnRelease() {
        assertTrue(NotificationSwipePolicy.armed(Edge.TOP, 0f, SLOP * 1.9f, SLOP));
        assertTrue(NotificationSwipePolicy.commitsOnRelease(Edge.TOP, 0f, FLICK, FLICK,
            120L, TIMEOUT));
        assertTrue(NotificationSwipePolicy.commitsOnRelease(Edge.LEFT, FLICK, 0f, FLICK,
            120L, TIMEOUT));
        assertTrue(NotificationSwipePolicy.commitsOnRelease(Edge.RIGHT, -FLICK, 0f, FLICK,
            120L, TIMEOUT));
        // Short of the flick, and a finger that stopped flicking and lingered, both do nothing.
        assertFalse(NotificationSwipePolicy.commitsOnRelease(Edge.TOP, 0f, FLICK - 1f, FLICK,
            120L, TIMEOUT));
        assertFalse(NotificationSwipePolicy.commitsOnRelease(Edge.TOP, 0f, FLICK, FLICK,
            TIMEOUT + 1L, TIMEOUT));
    }

    @Test public void aLongDragHandsTheStreamToTheDrawer() {
        float past = SPAN * NotificationSwipePolicy.HANDOFF_TRAVEL_FRACTION + 1f;
        float shortOf = SPAN * NotificationSwipePolicy.HANDOFF_TRAVEL_FRACTION - 1f;
        assertFalse(NotificationSwipePolicy.handsOff(Edge.TOP, 0f, shortOf, SPAN));
        assertTrue(NotificationSwipePolicy.handsOff(Edge.TOP, 0f, past, SPAN));
        assertTrue(NotificationSwipePolicy.handsOff(Edge.LEFT, past, 0f, SPAN));
        assertTrue(NotificationSwipePolicy.handsOff(Edge.RIGHT, -past, 0f, SPAN));
        // The rail's host knows only the pull it was handed, and gets the same answer.
        assertTrue(NotificationSwipePolicy.handsOffAlongPull(Pull.RIGHT, past, 0f, SPAN));
        assertFalse(NotificationSwipePolicy.handsOffAlongPull(Pull.RIGHT, shortOf, 0f, SPAN));
    }

    @Test public void theReplyHoldsOnlyTheAxisThatLeansTowardsTheMiddle() {
        // Held from the first move that leans inward — the drawer's own claim comes sooner than
        // the reply's arming travel, so a weaker test is what gives the reply a chance at all.
        assertTrue(NotificationSwipePolicy.holdsAxis(Edge.TOP, 0f, 6f));
        // A drag along the bar is the page swipe's, and one back towards the edge is nobody's.
        assertFalse(NotificationSwipePolicy.holdsAxis(Edge.TOP, 200f, 6f));
        assertFalse(NotificationSwipePolicy.holdsAxis(Edge.TOP, 0f, -200f));
        assertTrue(NotificationSwipePolicy.holdsAxis(Edge.LEFT, 6f, 0f));
        assertFalse(NotificationSwipePolicy.holdsAxis(Edge.LEFT, 6f, 200f));
    }

    @Test public void travelIsSignedTowardsTheMiddleOnEveryEdge() {
        assertEquals(50f, NotificationSwipePolicy.travelPx(Edge.BOTTOM, 0f, -50f), 0.001f);
        assertEquals(50f, NotificationSwipePolicy.travelPx(Edge.TOP, 0f, 50f), 0.001f);
        assertEquals(50f, NotificationSwipePolicy.travelPx(Edge.LEFT, 50f, 0f), 0.001f);
        assertEquals(50f, NotificationSwipePolicy.travelPx(Edge.RIGHT, -50f, 0f), 0.001f);
        assertEquals(30f, NotificationSwipePolicy.acrossPx(Edge.TOP, -30f, 50f), 0.001f);
        assertEquals(30f, NotificationSwipePolicy.acrossPx(Edge.LEFT, 50f, -30f), 0.001f);
    }
}
