package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCloseArmingPolicy.Decision;
import com.termux.app.launcher.drawer.AppDrawerCloseArmingPolicy.Down;

import org.junit.Test;

/**
 * Scroll versus close, on the grid.
 *
 * <p>The failure these guard is one gesture doing two things: a flick that scrolls the list to its
 * top and, in the same stream, keeps going and throws the drawer away. Hence the two cases that pin
 * the snapshot — a stream that began mid-list never closes however far it is pulled, and the first
 * pull at the top only ever overpulls — and hence the window, which is the only thing separating
 * "second deliberate pull" from "an idle drawer closing under a stray touch a minute later".
 *
 * <p>{@code dy} is in scroll units throughout: negative is a downward finger.
 */
public class AppDrawerCloseArmingPolicyTest {

    /** dp(28) at 3x, resolved by the caller since the policy never sees a Context. */
    private static final float ARM_OVERPULL = 84f;

    private static final Down ON_CHROME = new Down(false, true, true);
    private static final Down AT_TOP = new Down(true, true, true);
    private static final Down MID_LIST = new Down(true, false, true);
    private static final Down UNSCROLLABLE = new Down(true, true, false);

    @Test
    public void theFirstPullAtTheTopOverpullsRatherThanClosing() {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, 1000L);
        assertEquals(Decision.OVERPULL, policy.claimOnPreScroll(-20));
        assertEquals(Decision.OVERPULL, policy.claimOnPreScroll(-40));
        assertEquals(Decision.OVERPULL, policy.claimOnPreScroll(-400));
        assertFalse(policy.isClosing());
    }

    @Test
    public void armingNeedsTheOverpullOrTheFling() {
        // Neither: a short, slow pull at the top leaves the drawer exactly as it was.
        assertFalse(end(ARM_OVERPULL - 1f, 0f, true));
        assertFalse(end(0f, AppDrawerCloseArmingPolicy.ARM_FLING_VELOCITY_PX_PER_SEC - 1f, true));
        // Either alone is enough.
        assertTrue(end(ARM_OVERPULL, 0f, true));
        assertTrue(end(0f, AppDrawerCloseArmingPolicy.ARM_FLING_VELOCITY_PX_PER_SEC, true));
        assertTrue(end(0f, 2400f, true));
        // An upward fling is not a downward one, whatever its magnitude.
        assertFalse(end(0f, -2400f, true));
        // And a gesture that ended away from the top arms nothing, however hard it was thrown:
        // it was a scroll, and the next pull has to earn the top again.
        assertFalse(end(ARM_OVERPULL * 4f, 2400f, false));
    }

    @Test
    public void theSecondPullClosesInsideTheWindowAndNotOutsideIt() {
        AppDrawerCloseArmingPolicy inside = armedAt(1000L);
        inside.begin(AT_TOP, 1000L + AppDrawerCloseArmingPolicy.ARM_WINDOW_MS);
        assertTrue(inside.isArmed());
        assertEquals(Decision.CLOSE_DRAG, inside.claimOnPreScroll(-20));
        // Latched: the rest of the stream drives the close, so the grid never scrolls.
        assertEquals(Decision.CLOSE_DRAG, inside.claimOnPreScroll(-200));
        assertEquals(Decision.CLOSE_DRAG, inside.claimOnPreScroll(60));

        AppDrawerCloseArmingPolicy outside = armedAt(1000L);
        outside.begin(AT_TOP, 1000L + AppDrawerCloseArmingPolicy.ARM_WINDOW_MS + 1L);
        assertFalse(outside.isArmed());
        assertEquals(Decision.OVERPULL, outside.claimOnPreScroll(-20));
    }

    @Test
    public void aPullThatBeganMidListNeverClosesEvenAfterReachingTheTop() {
        AppDrawerCloseArmingPolicy policy = armedAt(1000L);
        policy.begin(MID_LIST, 1100L);
        // The grid reaches its top part way through — the snapshot does not care, and that is the
        // whole point: the drag that carried you to the top is not the drag that closes.
        for (int i = 0; i < 40; i++) {
            assertEquals("delta " + i, Decision.SCROLL, policy.claimOnPreScroll(-60));
        }
        assertFalse(policy.isClosing());
    }

    @Test
    public void anUpwardDragDisarms() {
        AppDrawerCloseArmingPolicy policy = armedAt(1000L);
        policy.begin(AT_TOP, 1100L);
        assertEquals(Decision.SCROLL, policy.claimOnPreScroll(40));
        assertFalse(policy.isArmed());
        // Coming back down inside the same stream is now just another first pull.
        assertEquals(Decision.OVERPULL, policy.claimOnPreScroll(-40));
    }

    @Test
    public void aGridThatCannotScrollClosesOnTheFirstPull() {
        // Arming protects a scroll; a two-result filter has none to protect.
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(UNSCROLLABLE, 1000L);
        assertFalse(policy.isArmed());
        assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
    }

    @Test
    public void chromeAlwaysCloses() {
        for (long now : new long[] {1000L, 9_000_000L}) {
            AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
            policy.begin(ON_CHROME, now);
            assertEquals(Decision.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        }
        // The pill must not swallow a vertical drag just because the grid under it can scroll.
        AppDrawerCloseArmingPolicy armed = armedAt(1000L);
        armed.begin(ON_CHROME, 1100L);
        assertEquals(Decision.CLOSE_DRAG, armed.claimOnPreScroll(-20));
    }

    @Test
    public void aTapDisarms() {
        AppDrawerCloseArmingPolicy policy = armedAt(1000L);
        // A tap is a launch, not a dismissal; the content view spends the arming on it.
        policy.disarm();
        policy.begin(AT_TOP, 1100L);
        assertEquals(Decision.OVERPULL, policy.claimOnPreScroll(-20));
    }

    /** @return the armed state after one gesture that ends with the given release. */
    private static boolean end(float overpullPx, float velocityPxPerSec, boolean atTopAtEnd) {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, 1000L);
        policy.claimOnPreScroll(-20);
        return policy.end(overpullPx, ARM_OVERPULL, velocityPxPerSec, atTopAtEnd, 1000L);
    }

    private static AppDrawerCloseArmingPolicy armedAt(long nowMs) {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(AT_TOP, nowMs);
        policy.claimOnPreScroll(-20);
        policy.end(ARM_OVERPULL, ARM_OVERPULL, 0f, true, nowMs);
        assertTrue(policy.isArmed());
        return policy;
    }
}
