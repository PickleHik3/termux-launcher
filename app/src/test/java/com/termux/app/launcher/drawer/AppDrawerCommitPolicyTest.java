package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Decision;
import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Direction;

import org.junit.Test;

/** What a lifted finger means, in both transition directions. */
public class AppDrawerCommitPolicyTest {

    @Test
    public void pastHalfwayAPlainReleaseCommits() {
        assertEquals(Decision.COMMIT_OPEN,
            AppDrawerCommitPolicy.decide(0.5f, 0f, Direction.OPENING));
        assertEquals(Decision.COMMIT_OPEN,
            AppDrawerCommitPolicy.decide(0.83f, 40f, Direction.OPENING));
    }

    @Test
    public void slowReleaseBelowHalfwayCancels() {
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.49f, 0f, Direction.OPENING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.30f, 120f, Direction.OPENING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.11f, 899f, Direction.OPENING));
    }

    @Test
    public void downwardFlingCommitsFromABarelyStartedDrag() {
        assertEquals(Decision.COMMIT_OPEN,
            AppDrawerCommitPolicy.decide(0.12f, 900f, Direction.OPENING));
        assertEquals(Decision.COMMIT_OPEN,
            AppDrawerCommitPolicy.decide(0.15f, 2400f, Direction.OPENING));
        // Below the travel floor a fling is a stray flick, not an instruction.
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.11f, 2400f, Direction.OPENING));
    }

    @Test
    public void upwardFlingCancelsRegardlessOfProgress() {
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.95f, -900f, Direction.OPENING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.60f, -3000f, Direction.OPENING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.05f, -3000f, Direction.OPENING));
    }

    @Test
    public void closingDirectionMirrors() {
        // Half the drawer's worth of travel back down commits the close…
        assertEquals(Decision.COMMIT_CLOSE,
            AppDrawerCommitPolicy.decide(0.5f, 0f, Direction.CLOSING));
        assertEquals(Decision.COMMIT_CLOSE,
            AppDrawerCommitPolicy.decide(0.2f, 0f, Direction.CLOSING));
        // …while barely nudging it leaves the drawer open.
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.51f, 0f, Direction.CLOSING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.95f, 120f, Direction.CLOSING));
        // A dismissing fling needs the same 12% of travel behind it.
        assertEquals(Decision.COMMIT_CLOSE,
            AppDrawerCommitPolicy.decide(0.88f, 900f, Direction.CLOSING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.89f, 2400f, Direction.CLOSING));
        // And pulling it back up still means "abort", whatever the progress.
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.05f, -900f, Direction.CLOSING));
    }

    @Test
    public void progressOutsideTheUnitRangeIsClamped() {
        assertEquals(Decision.COMMIT_OPEN,
            AppDrawerCommitPolicy.decide(1.4f, 0f, Direction.OPENING));
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(-0.3f, 0f, Direction.OPENING));
    }
}
