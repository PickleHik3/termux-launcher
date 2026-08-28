package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCloseArmingPolicy.Decision;
import com.termux.app.launcher.drawer.AppDrawerCloseArmingPolicy.Down;

import org.junit.Test;

/**
 * Scroll versus close, on the grid — the standard launcher model.
 *
 * <p>The failure these guard is one gesture doing two things: a flick that scrolls the list to its
 * top and, in the same stream, keeps going and throws the drawer away. Hence the cases that pin the
 * snapshot and the one-way scroll latch — a stream that began mid-list, or that ever scrolled,
 * never closes however far it is pulled. A fresh pull that begins at the top closes in that one
 * gesture; the release-time commit policy (travel or fling), not a second swipe, is what separates
 * a close from an accident.
 *
 * <p>{@code dy} is in scroll units throughout: negative is a downward finger.
 */
public class AppDrawerCloseArmingPolicyTest {

    private static final Down ON_CHROME = new Down(false, true, true);
    private static final Down AT_TOP = new Down(true, true, true);
    private static final Down MID_LIST = new Down(true, false, true);
    private static final Down UNSCROLLABLE = new Down(true, true, false);

    @Test
    public void aPullThatBeginsAtTheTopClosesInOneGesture() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, 1000L);
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        assertTrue(policy.isClosing());
        // Latched: the rest of the stream drives the close, so the grid never scrolls.
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-200));
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(60));
    }

    @Test
    public void aPullThatBeganMidListNeverClosesEvenAfterReachingTheTop() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(MID_LIST, 1100L);
        // The grid reaches its top part way through — the snapshot does not care, and that is the
        // whole point: the drag that carried you to the top is not the drag that closes.
        for (int i = 0; i < 40; i++) {
            assertEquals("delta " + i, Decision.SCROLL, policy.claimOnPreScroll(-60));
        }
        assertFalse(policy.isClosing());
    }

    @Test
    public void aStreamThatScrolledUpwardCanNeverCloseOnTheWayBack() {
        // At the top, the finger first moves up: the list leaves its top, so the snapshot's atTop
        // is stale. Coming back down inside the same stream must scroll the list back, not throw
        // the drawer away from the middle of it.
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, 1100L);
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(40));
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(-40));
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(-400));
        assertFalse(policy.isClosing());
    }

    @Test
    public void theNextGestureAtTheTopClosesWithoutAnyArming() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        // First gesture scrolls (began mid-list) and ends.
        policy.begin(MID_LIST, 1000L);
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(-60));
        assertFalse(policy.end(0f, 0f, 0f, true, 1000L));
        // The next pull, now at the top, closes on its first delta — one swipe, no arming window.
        policy.begin(AT_TOP, 5_000_000L);
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
    }

    @Test
    public void aGridThatCannotScrollClosesOnTheFirstPull() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(UNSCROLLABLE, 1000L);
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
    }

    @Test
    public void chromeAlwaysCloses() {
        for (long now : new long[] {1000L, 9_000_000L}) {
            AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
            policy.begin(ON_CHROME, now);
            assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        }
    }

    @Test
    public void endResetsTheStreamAndNeverArms() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, 1000L);
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        assertFalse(policy.end(500f, 0f, 9000f, true, 1000L));
        assertFalse(policy.isArmed());
        assertFalse(policy.isClosing());
        // A stray delta outside any gesture can only scroll, never close.
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(-20));
    }
}
