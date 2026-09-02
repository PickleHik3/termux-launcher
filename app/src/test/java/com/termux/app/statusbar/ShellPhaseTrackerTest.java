package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ShellPhaseTrackerTest {

    private static final long Q = ShellPhaseTracker.QUIET_MS;
    private static final long W = ShellPhaseTracker.MIN_WORK_MS;

    private final ShellPhaseTracker tracker = new ShellPhaseTracker();

    private ShellPhaseTracker.Phase observe(long now, boolean working, boolean shellForeground,
                                            boolean question, boolean seen) {
        return tracker.observe(7, now, working, true, shellForeground, () -> question, seen);
    }

    @Test
    public void anUnseenCommandThatWorksThenAsksGetsTheBell() {
        assertEquals(ShellPhaseTracker.Phase.WORKING, observe(0, true, false, false, false));
        assertEquals(ShellPhaseTracker.Phase.WORKING, observe(W + 100, true, false, false, false));
        // Quiet, but not for long enough yet: the ring holds through the gap.
        assertEquals(ShellPhaseTracker.Phase.WORKING, observe(W + 600, false, false, true, false));
        assertEquals(ShellPhaseTracker.Phase.ATTENTION, observe(W + 100 + Q, false, false, true, false));
        // Held until seen.
        assertEquals(ShellPhaseTracker.Phase.ATTENTION, observe(W + 100 + Q + 5000, false, false, true, false));
        assertEquals(ShellPhaseTracker.Phase.NONE, observe(W + 100 + Q + 6000, false, false, true, true));
    }

    @Test
    public void anUnseenCommandThatWorksThenGoesQuietIsDone() {
        observe(0, true, false, false, false);
        observe(W + 100, true, false, false, false);
        assertEquals(ShellPhaseTracker.Phase.DONE, observe(W + 100 + Q, false, false, false, false));
    }

    @Test
    public void handingTheTerminalBackToTheShellIsDoneAtOnce() {
        observe(0, true, false, false, false);
        observe(W + 100, true, false, false, false);
        // No quiet wait: the command exited, and a question on a shell prompt is not a question.
        assertEquals(ShellPhaseTracker.Phase.DONE, observe(W + 200, false, true, true, false));
    }

    @Test
    public void shortWorkAndSeenWindowsEarnNoBadge() {
        observe(0, true, false, false, false);
        observe(200, true, false, false, false);
        assertEquals(ShellPhaseTracker.Phase.NONE, observe(200 + Q, false, false, true, false));

        observe(10_000, true, false, false, true);
        observe(10_000 + W + 100, true, false, false, true);
        assertEquals(ShellPhaseTracker.Phase.NONE, observe(10_000 + W + 100 + Q, false, false, true, true));
    }

    @Test
    public void workResumingClearsABadgeAndTheJudgementIsScheduledOnce() {
        observe(0, true, false, false, false);
        observe(W + 100, true, false, false, false);
        assertEquals(W + 100 + Q, tracker.nextJudgementMs(W + 200));
        assertEquals(ShellPhaseTracker.Phase.DONE, observe(W + 100 + Q, false, false, false, false));
        assertEquals(-1L, tracker.nextJudgementMs(W + 100 + Q));
        assertEquals(ShellPhaseTracker.Phase.WORKING, observe(W + 100 + Q + 50, true, false, false, false));
    }

    @Test
    public void markSeenClearsBadgesButNotWork() {
        observe(0, true, false, false, false);
        observe(W + 100, true, false, false, false);
        observe(W + 100 + Q, false, false, true, false);
        assertEquals(ShellPhaseTracker.Phase.ATTENTION, tracker.phaseOf(7));
        tracker.markSeen(7);
        assertEquals(ShellPhaseTracker.Phase.NONE, tracker.phaseOf(7));
        observe(20_000, true, false, false, true);
        tracker.markSeen(7);
        assertEquals(ShellPhaseTracker.Phase.WORKING, tracker.phaseOf(7));
        tracker.retain(Collections.emptySet());
        assertEquals(ShellPhaseTracker.Phase.NONE, tracker.phaseOf(7));
    }
}
