package com.termux.terminal;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tears down a shell and everything it started.
 *
 * <p>Two earlier teardowns each left work behind. Stock termux-app sent one SIGKILL to the shell's
 * own pid, so {@code sleep 300 &} was reparented to init and kept running. The next version hung up
 * and then killed the shell's process group, {@code kill(-shellPid)} — but an interactive shell with
 * job control (fish, bash) puts every job, foreground or background, into a process group of its
 * own, precisely so it can stop and interrupt them one at a time. The group signal therefore never
 * reached a single job, and curses programs that swallow the write error on a dead pty (tty-clock,
 * cbonsai, lazygit, sigye) lived on until the phone rebooted.
 *
 * <p>What does identify the whole tree is the session. The native child calls {@code setsid()}
 * before opening the slave pty, so its session id is its own pid and every descendant inherits it
 * unless it starts a session of its own — a real daemon, which is then deliberately left alone. The
 * teardown hangs up every process group the session holds, waits, and kills whatever groups the
 * session still holds, whether or not the shell itself survived the hangup.
 *
 * <p>No Android imports, so the logic runs under the module's plain-JUnit suite.
 */
public final class ShellTerminator {

    /** Grace between the hangup and the kill. Long enough for a shell to run its EXIT trap. */
    public static final long ESCALATION_DELAY_MS = 150L;

    /** Sends {@code signal} to {@code pid}; a negative pid means "this process group". */
    public interface SignalSender {
        boolean send(int pid, int signal);
    }

    /** Somewhere to run the escalation after a delay — in practice a main-looper Handler. */
    public interface Scheduler {
        void postDelayed(Runnable runnable, long delayMs);
    }

    /** The shell's pid right now, or -1 once it has been reaped. */
    public interface LivePid {
        int get();
    }

    /** Answers which process groups a session currently holds. */
    public interface ProcessTable {
        /**
         * The distinct process-group ids of every live process whose session id is {@code sid},
         * or null when the table cannot be read at all. An empty array means the session is gone.
         */
        int[] processGroupsInSession(int sid);
    }

    private ShellTerminator() {}

    /**
     * Hangs up every process group in {@code shellPid}'s session, then after
     * {@link #ESCALATION_DELAY_MS} kills every group the session still holds.
     *
     * <p>The second pass matches on the session id rather than on the shell being alive, because
     * the common case is the opposite: the shell exits on the hangup and a job that ignores it
     * stays. A brand-new pane whose shell was handed this exact pid within the grace period would
     * be caught too; pids are handed out in sequence and wrap only after tens of thousands of
     * processes, so that is theoretical.
     *
     * <p>When the table is unreadable the leader's own group is signalled alone, guarded on the
     * leader still being alive, which is the previous behaviour. If even the group form of the
     * hangup is rejected, the single pid is signalled and the escalation is skipped, since a group
     * kill would be no more valid than the group hangup was.
     */
    public static void terminate(int shellPid, int sighup, int sigkill, SignalSender sender,
                                 Scheduler scheduler, ProcessTable table, LivePid livePid) {
        if (shellPid <= 0) return;
        int[] found = table.processGroupsInSession(shellPid);
        Set<Integer> groups = new LinkedHashSet<>();
        groups.add(shellPid);   // the leader's own group, even when the table has nothing to say
        if (found != null) for (int g : found) groups.add(g);

        boolean anyAccepted = false;
        for (int g : groups) anyAccepted |= sender.send(-g, sighup);
        if (!anyAccepted) {
            sender.send(shellPid, sighup);
            return;
        }

        scheduler.postDelayed(() -> {
            int[] left = table.processGroupsInSession(shellPid);
            if (left == null) {
                if (livePid.get() == shellPid) sender.send(-shellPid, sigkill);
                return;
            }
            for (int g : left) sender.send(-g, sigkill);
        }, ESCALATION_DELAY_MS);
    }
}
