package com.termux.terminal;

/**
 * Tears down a shell and everything it started.
 *
 * <p>The old teardown sent one SIGKILL to the shell's own pid, which is why {@code sleep 300 &}
 * outlived the pane that spawned it: the shell died, its children were reparented to init, and the
 * pane's work kept running with nothing on screen to stop it.
 *
 * <p>The native child calls {@code setsid()} before opening the slave pty, so its pid is its own
 * process group leader and every descendant inherits that group. Signalling the group therefore
 * reaches the whole job — which is also strictly more than the kernel's pty hangup would.
 *
 * <p>No Android imports, so the escalation logic runs under the module's plain-JUnit suite.
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

    private ShellTerminator() {}

    /**
     * Hangs up {@code shellPid}'s process group, then kills it if the shell is still alive after
     * {@link #ESCALATION_DELAY_MS}.
     *
     * <p>The escalation is guarded on the leader still being alive, which is what makes it
     * pid-reuse-safe: while the leader lives, the process group {@code shellPid} is unambiguously
     * this job's, so a negative-pid SIGKILL cannot land on a stranger. It also implements the chosen
     * contract — a shell that exited on the hangup is simply left alone.
     *
     * <p>If the group form of the signal is rejected, the single pid is signalled instead and the
     * escalation is skipped, since a group kill would be no more valid than the group hangup was.
     */
    public static void terminate(int shellPid, int sighup, int sigkill,
                                 SignalSender sender, Scheduler scheduler, LivePid livePid) {
        if (shellPid <= 0) return;
        if (!sender.send(-shellPid, sighup)) {
            sender.send(shellPid, sighup);
            return;
        }
        scheduler.postDelayed(() -> {
            if (livePid.get() != shellPid) return;   // already exited on the hangup
            sender.send(-shellPid, sigkill);
        }, ESCALATION_DELAY_MS);
    }
}
