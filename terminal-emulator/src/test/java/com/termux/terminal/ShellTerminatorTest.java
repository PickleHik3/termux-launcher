package com.termux.terminal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JUnit with fakes for both interfaces: no Android types are involved in the escalation. */
public class ShellTerminatorTest {

    private static final int SIGHUP = 1;
    private static final int SIGKILL = 9;

    @Test
    public void hangsUpTheWholeGroupFirst() {
        // The negative pid is the whole point: a SIGKILL to the shell alone left `sleep 300 &`
        // reparented to init and still running.
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, () -> 4321);

        assertEquals(Arrays.asList("-4321:1"), sender.sent);
    }

    @Test
    public void escalatesToAGroupKillWhenTheShellIgnoresTheHangup() {
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, () -> 4321);
        assertEquals(1, scheduler.delays.size());
        assertEquals(ShellTerminator.ESCALATION_DELAY_MS, (long) scheduler.delays.get(0));
        scheduler.runAll();

        assertEquals(Arrays.asList("-4321:1", "-4321:9"), sender.sent);
    }

    @Test
    public void skipsTheKillOnceTheShellHasBeenReaped() {
        // Guarding on the leader still being alive is also what makes this pid-reuse-safe: while the
        // leader lives, process group 4321 is unambiguously this job's.
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();
        int[] livePid = {4321};

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, () -> livePid[0]);
        livePid[0] = -1;   // cleanupResources ran between the hangup and the escalation
        scheduler.runAll();

        assertEquals(Arrays.asList("-4321:1"), sender.sent);
    }

    @Test
    public void fallsBackToTheSinglePidWhenTheGroupSignalIsRejected() {
        // A group kill would be no more valid than the group hangup was, so no escalation either.
        Sender sender = new Sender();
        sender.rejectNegative = true;
        FakeScheduler scheduler = new FakeScheduler();

        ShellTerminator.terminate(4321, SIGHUP, SIGKILL, sender, scheduler, () -> 4321);

        assertEquals(Arrays.asList("-4321:1", "4321:1"), sender.sent);
        assertTrue(scheduler.delays.isEmpty());
    }

    @Test
    public void ignoresAShellThatWasNeverRunning() {
        Sender sender = new Sender();
        FakeScheduler scheduler = new FakeScheduler();

        ShellTerminator.terminate(0, SIGHUP, SIGKILL, sender, scheduler, () -> 0);
        ShellTerminator.terminate(-1, SIGHUP, SIGKILL, sender, scheduler, () -> -1);

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
