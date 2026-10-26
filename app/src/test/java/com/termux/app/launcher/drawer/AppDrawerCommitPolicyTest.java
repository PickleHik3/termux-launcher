package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Decision;
import com.termux.app.launcher.drawer.AppDrawerCommitPolicy.Direction;

import org.junit.Test;

/**
 * What a lifted finger means, in both transition directions: the one page rule (PagePolicy) read
 * over the drawer's travel. Speeds are written in dp and handed over in px at a phone's density.
 */
public class AppDrawerCommitPolicyTest {

    private static final float DENSITY = 2.75f;

    private static float dp(float dpPerSec) {
        return dpPerSec * DENSITY;
    }

    private static Decision open(float progress, float dpPerSec) {
        return AppDrawerCommitPolicy.decide(progress, dp(dpPerSec), Direction.OPENING, DENSITY);
    }

    private static Decision close(float progress, float dpPerSec) {
        return AppDrawerCommitPolicy.decide(progress, dp(dpPerSec), Direction.CLOSING, DENSITY);
    }

    @Test
    public void pastFourTenthsAPlainReleaseCommits() {
        assertEquals(Decision.COMMIT_OPEN, open(0.4f, 0f));
        assertEquals(Decision.COMMIT_OPEN, open(0.83f, 40f));
    }

    @Test
    public void slowReleaseShortOfFourTenthsCancels() {
        // Was 0.5: 0.49 cancelled, and still would at 0.39.
        assertEquals(Decision.CANCEL, open(0.39f, 0f));
        assertEquals(Decision.CANCEL, open(0.30f, 120f));
        assertEquals(Decision.CANCEL, open(0.11f, 499f));
    }

    @Test
    public void downwardFlingCommitsFromABarelyStartedDrag() {
        assertEquals(Decision.COMMIT_OPEN, open(0.12f, 500f));
        assertEquals(Decision.COMMIT_OPEN, open(0.15f, 2400f));
        assertEquals(Decision.CANCEL, open(0.12f, 499f));
        // Below the travel floor a fling is a stray flick, not an instruction.
        assertEquals(Decision.CANCEL, open(0.11f, 2400f));
    }

    @Test
    public void pastAThirdAnEasyFlickCommits() {
        assertEquals(Decision.COMMIT_OPEN, open(0.34f, 400f));
        assertEquals(Decision.CANCEL, open(0.32f, 400f));
    }

    @Test
    public void upwardFlingCancelsRegardlessOfProgress() {
        assertEquals(Decision.CANCEL, open(0.95f, -250f));
        assertEquals(Decision.CANCEL, open(0.60f, -3000f));
        assertEquals(Decision.CANCEL, open(0.05f, -3000f));
        // Slower than a reversal, position decides.
        assertEquals(Decision.COMMIT_OPEN, open(0.95f, -249f));
    }

    @Test
    public void closingDirectionMirrors() {
        // Four tenths of the drawer's worth of travel back down commits the close…
        assertEquals(Decision.COMMIT_CLOSE, close(0.59f, 0f));
        assertEquals(Decision.COMMIT_CLOSE, close(0.2f, 0f));
        // …while barely nudging it leaves the drawer open.
        assertEquals(Decision.CANCEL, close(0.61f, 0f));
        assertEquals(Decision.CANCEL, close(0.95f, 120f));
        // A dismissing fling needs the same 12% of travel behind it.
        assertEquals(Decision.COMMIT_CLOSE, close(0.88f, 500f));
        assertEquals(Decision.CANCEL, close(0.89f, 2400f));
        // And pulling it back up still means "abort", whatever the progress.
        assertEquals(Decision.CANCEL, close(0.05f, -900f));
    }

    @Test
    public void theFlingIsInDpSoItDoesNotVaryWithDensity() {
        // The old 900 px/s: a fling at 1x, 257 dp/s and no fling at 3.5x.
        assertEquals(Decision.CANCEL,
            AppDrawerCommitPolicy.decide(0.15f, 900f, Direction.OPENING, 3.5f));
        for (float density : new float[] {2.6f, 3.5f}) {
            assertEquals(Decision.COMMIT_OPEN, AppDrawerCommitPolicy.decide(0.15f,
                500f * density, Direction.OPENING, density));
            assertEquals(Decision.CANCEL, AppDrawerCommitPolicy.decide(0.15f,
                499f * density, Direction.OPENING, density));
        }
    }

    @Test
    public void progressOutsideTheUnitRangeIsClamped() {
        assertEquals(Decision.COMMIT_OPEN, open(1.4f, 0f));
        assertEquals(Decision.CANCEL, open(-0.3f, 0f));
    }
}
