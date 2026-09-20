package com.termux.terminal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JUnit with fakes for every interface: no Android types are involved in the escalation. */
public class ShellTerminatorTest {

    private static final int SIGHUP = 1;
    private static final int SIGKILL = 9;

    @Test
    public void hangsUpEveryGroupInTheSession() {
        // fish gives `tty-clock` (foreground) and `sleep 300 &` (background) groups of their own; a
        // signal to the shell's group 4321 alone reached neither.
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTable table = new FakeTable(4321, 5000, 5100);

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, table, () -> 4321);

        assertEquals(Arrays.asList("-4321:1", "-5000:1", "-5100:1"), sender.sent);
    }

    @Test
    public void killsWhatTheSessionStillHoldsAfterTheShellExitedOnTheHangup() {
        // The common case on the phone: the shell honours the hangup and exits, the curses program in
        // its own group swallows it. The old guard "leader still alive" skipped exactly this kill.
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTable table = new FakeTable(4321, 5000);
        int[] livePid = {4321};

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, table, () -> livePid[0]);
        assertEquals(Arrays.asList((long) ShellTerminator.ESCALATION_DELAY_MS), scheduler.delays);
        livePid[0] = -1;              // cleanupResources ran
        table.groups = new int[]{5000};
        scheduler.runAll();

        assertEquals(Arrays.asList("-4321:1", "-5000:1", "-5000:9"), sender.sent);
    }

    @Test
    public void sendsNoKillWhenTheHangupEmptiedTheSession() {
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTable table = new FakeTable(4321, 5000);

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, table, () -> -1);
        table.groups = new int[0];
        scheduler.runAll();

        assertEquals(Arrays.asList("-4321:1", "-5000:1"), sender.sent);
    }

    @Test
    public void fallsBackToTheLeaderGroupWhenTheTableIsUnreadable() {
        // Without /proc this is the previous behaviour: the leader's group, escalation guarded on
        // the leader still being alive.
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTable table = new FakeTable((int[]) null);
        int[] livePid = {4321};

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, table, () -> livePid[0]);
        scheduler.runAll();
        assertEquals(Arrays.asList("-4321:1", "-4321:9"), sender.sent);

        sender.sent.clear();
        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, table, () -> livePid[0]);
        livePid[0] = -1;
        scheduler.runAll();
        assertEquals(Arrays.asList("-4321:1"), sender.sent);
    }

    @Test
    public void fallsBackToTheSinglePidWhenTheGroupSignalIsRejected() {
        // A group kill would be no more valid than the group hangup was, so no escalation either.
        Sender sender = new Sender();
        sender.rejectNegative = true;
        FakeScheduler scheduler = new FakeScheduler();

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, new FakeTable(4321), () -> 4321);

        assertEquals(Arrays.asList("-4321:1", "4321:1"), sender.sent);
        assertTrue(scheduler.delays.isEmpty());
    }

    @Test
    public void ignoresAShellThatWasNeverRunning() {
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        FakeTable table = new FakeTable(4321);

        ShellTerminator.terminate(0, SIGHUP, SIGKILL, sender, scheduler, table, () -> 0);
        ShellTerminator.terminate(-1, SIGHUP, SIGKILL, sender, scheduler, table, () -> -1);

        assertTrue(sender.sent.isEmpty());
        assertTrue(scheduler.delays.isEmpty());
    }

    private static final class Sender implements ShellTerminator.SignalSender {
        final List<String> sent = new ArrayList<>();
        boolean rejectNegative;

        @Override public boolean send(int pid, int signal) {
            sent.add(pid + ":" + signal);
            return !(rejectNegative && pid < 0);
        }
    }

    private static final class FakeTable implements ShellTerminator.ProcessTable {
        int[] groups;

        FakeTable(int... groups) { this.groups = groups; }

        @Override public int[] processGroupsInSession(int sid) { return groups; }
    }

    private static final class FakeScheduler implements ShellTerminator.Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override public void postDelayed(Runnable runnable, long delayMs) {
            tasks.add(runnable);
            delays.add(delayMs);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : pending) task.run();
        }
    }
}
