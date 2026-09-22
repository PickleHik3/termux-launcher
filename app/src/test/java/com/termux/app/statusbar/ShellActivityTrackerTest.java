package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShellActivityTrackerTest {

    private static final long DECAY = ShellActivityTracker.DECAY_MS;

    @Test
    public void activityDecaysAfterTheDecayWindow() {
        ShellActivityTracker tracker = new ShellActivityTracker();

        tracker.noteActivity(101, 1_000L);

        assertTrue(tracker.isActive(101, 1_000L));
        assertTrue(tracker.isActive(101, 1_000L + DECAY - 1));
        assertFalse(tracker.isActive(101, 1_000L + DECAY));
        assertFalse(tracker.isActive(999, 1_000L));
    }

    @Test
    public void aFurtherNoteExtendsTheWindowRatherThanRestartingTheClock() {
        // Long-running output arrives in bursts; each burst has to hold the indication up.
        ShellActivityTracker tracker = new ShellActivityTracker();
        tracker.noteActivity(101, 1_000L);

        tracker.noteActivity(101, 1_000L + DECAY - 10);

        assertTrue(tracker.isActive(101, 1_000L + DECAY + 10));
        assertFalse(tracker.isActive(101, 1_000L + 2 * DECAY));
    }

    @Test
    public void nextExpiryIsTheSoonestStillActiveShell() {
        ShellActivityTracker tracker = new ShellActivityTracker();
        tracker.noteActivity(101, 1_000L);
        tracker.noteActivity(102, 1_300L);

        assertEquals(1_000L + DECAY, tracker.nextExpiryMs(1_400L));
        // Once the first has expired the second becomes the next thing to wake for.
        assertEquals(1_300L + DECAY, tracker.nextExpiryMs(1_000L + DECAY));
        // Nothing active: nothing to schedule, so the host stops posting entirely.
        assertEquals(-1L, tracker.nextExpiryMs(1_300L + DECAY));
    }

    @Test
    public void pruneDropsStalePidsSoDeadShellsCannotPileUp() {
        ShellActivityTracker tracker = new ShellActivityTracker();
        tracker.noteActivity(101, 1_000L);
        tracker.noteActivity(102, 5_000L);

        tracker.pruneBefore(4_000L);

        assertFalse(tracker.isActive(101, 1_000L));
        assertEquals(5_000L + DECAY, tracker.nextExpiryMs(5_000L));
    }

    @Test
    public void aNonRunningShellIsNeverRecorded() {
        ShellActivityTracker tracker = new ShellActivityTracker();

        tracker.noteActivity(-1, 1_000L);
        tracker.noteActivity(0, 1_000L);

        assertEquals(-1L, tracker.nextExpiryMs(1_000L));
    }

    @Test
    public void aShortBurstIsActivityButNotYetWork() {
        // One keystroke echoed back is a screen update. It must not read as a command working.
        ShellActivityTracker tracker = new ShellActivityTracker();

        tracker.noteActivity(101, 1_000L);

        assertTrue(tracker.isActive(101, 1_000L));
        assertFalse(tracker.isWorking(101, 1_000L));
    }

    @Test
    public void aBurstBecomesWorkOnceItIsLongEnoughAndBusyEnough() {
        ShellActivityTracker tracker = new ShellActivityTracker();
        long start = 1_000L;
        for (int i = 0; i < ShellActivityTracker.SUSTAIN_UPDATES - 1; i++) {
            tracker.noteActivity(101, start + i * 100L);
        }
        long last = start + ShellActivityTracker.SUSTAIN_MS;

        // Neither long enough nor busy enough yet; one more update satisfies both.
        assertFalse(tracker.isWorking(101, start + 300L));
        tracker.noteActivity(101, last);

        assertTrue(tracker.isWorking(101, last));
        assertFalse("work ends with the output", tracker.isWorking(101, last + DECAY));
    }

    @Test
    public void updatesSpreadTooThinlyNeverAddUpToWork() {
        // A TUI that repaints once every couple of seconds leaves a gap wider than the decay window,
        // so each repaint starts a fresh burst instead of accumulating credit forever.
        ShellActivityTracker tracker = new ShellActivityTracker();
        long at = 1_000L;
        for (int i = 0; i < 20; i++) {
            tracker.noteActivity(101, at);
            assertFalse("repaint " + i, tracker.isWorking(101, at));
            at += DECAY + 1;
        }
    }

    @Test
    public void forgetAndClearDropTrackedShells() {
        ShellActivityTracker tracker = new ShellActivityTracker();
        tracker.noteActivity(101, 1_000L);
        tracker.noteActivity(102, 1_000L);

        tracker.forget(101);
        assertFalse(tracker.isActive(101, 1_000L));
        assertTrue(tracker.isActive(102, 1_000L));

        tracker.clear();
        assertFalse(tracker.isActive(102, 1_000L));
    }


    @Test
    public void outputOnTheHeelsOfAKeystrokeIsEchoAndEndsTheBurst() {
        // A remote shell echoes every keystroke back in several pieces; ten of those over two
        // seconds pass the burst rule, and the pause after typing used to light the ring.
        ShellActivityTracker tracker = new ShellActivityTracker();
        long echo = ShellActivityTracker.INPUT_ECHO_MS;
        long t = 1_000L;
        for (int i = 0; i < 10; i++) {
            long keystroke = t + i * 200L;
            tracker.noteActivity(101, keystroke + 40L, keystroke);
        }
        long lastKeystroke = t + 9 * 200L;

        assertFalse("echo is not activity", tracker.isActive(101, lastKeystroke + 100L));
        assertFalse("and never adds up to work", tracker.isWorking(101, lastKeystroke + echo + 100L));
        assertEquals(-1L, tracker.nextExpiryMs(lastKeystroke + 100L));
    }

    @Test
    public void aKeystrokeInTheMiddleOfABurstDropsItRatherThanLeavingItToBeJudged() {
        ShellActivityTracker tracker = new ShellActivityTracker();
        for (int i = 0; i < 5; i++) tracker.noteActivity(101, 1_000L + i * 150L, -1L);
        assertTrue(tracker.isWorking(101, 1_700L));

        // The user types; the redraw that follows is echo, and the earlier burst is gone with it.
        tracker.noteActivity(101, 1_900L, 1_850L);

        assertFalse(tracker.isWorking(101, 1_900L));
        assertFalse(tracker.isActive(101, 1_900L));
    }

    @Test
    public void outputPastTheEchoWindowAfterEnterIsTheCommandsOwn() {
        // Enter is a keystroke too; what a command prints after the window is work as before.
        ShellActivityTracker tracker = new ShellActivityTracker();
        long enter = 1_000L;
        long start = enter + ShellActivityTracker.INPUT_ECHO_MS;
        for (int i = 0; i < 5; i++) tracker.noteActivity(101, start + i * 150L, enter);

        assertTrue(tracker.isWorking(101, start + 700L));
    }
}
