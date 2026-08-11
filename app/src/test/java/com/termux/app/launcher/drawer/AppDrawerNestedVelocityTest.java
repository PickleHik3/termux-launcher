package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Decision;
import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Direction;

import org.junit.Test;

/**
 * The one sign conversion between {@code onNestedPreFling} and the drawer's release policy.
 *
 * <p>Two conventions meet here and they disagree. {@code RecyclerView} reports a fling to its parent
 * in scroll units — a finger thrown downwards scrolls the list toward its top and arrives as a
 * <em>negative</em> {@code velocityY} — while the commit policy and the settle spring take velocity
 * positive downwards, with the finger. Getting it backwards is silent: the decision still looks
 * reasonable, and the spring is simply launched away from the target it was just given.
 *
 * <p>Both directions are pinned because the failure is symmetric — a downward throw that refuses to
 * close and an upward one that closes are the same bug, seen from either end.
 */
public class AppDrawerNestedVelocityTest {

    /** dp(28) at 3x. */
    private static final float ARM_OVERPULL = 84f;

    @Test
    public void aDownwardFlingBecomesAPositiveVelocityThatCloses() {
        float velocityY = -2400f;   // the finger went down
        float converted = AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(velocityY);
        assertEquals(2400f, converted, 0f);
        assertEquals(Decision.COMMIT_CLOSE,
            AppDrawerCommitPolicy.decide(0.88f, converted, Direction.CLOSING));
    }

    @Test
    public void anUpwardFlingBecomesANegativeVelocityThatCancels() {
        float velocityY = 2400f;    // the finger went up
        float converted = AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(velocityY);
        assertEquals(-2400f, converted, 0f);
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.2f, converted, Direction.CLOSING));
    }

    @Test
    public void theSameConversionFeedsTheArmingFlingTest() {
        // A fling at the top arms the next pull only when it was thrown downwards, so the arming
        // threshold and the release policy read one number in one direction.
        float threshold = AppDrawerCloseArmingPolicy.ARM_FLING_VELOCITY_PX_PER_SEC;
        assertTrue(armsWith(AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(-threshold)));
        assertFalse(armsWith(AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(threshold)));
    }

    private static boolean armsWith(float velocityPxPerSec) {
        AppDrawerCloseArmingPolicy policy = new AppDrawerCloseArmingPolicy();
        policy.begin(new AppDrawerCloseArmingPolicy.Down(true, true, true), 1000L);
        policy.claimOnPreScroll(-20);
        return policy.end(0f, ARM_OVERPULL, velocityPxPerSec, true, 1000L);
    }
}
