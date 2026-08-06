package com.termux.privileged;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Where privileged backend work runs.
 *
 * <p>Not the common pool: a blocked {@code su} or {@code rish} process holds a
 * ForkJoinPool.commonPool worker, and the pool is sized from the core count, so a couple of hung
 * privileged commands can starve everything else that uses it.
 *
 * <p>Not the backend manager's own single-threaded executor either — {@code cleanup()} shuts that
 * one down, and one wedged stats command there would serialise behind it every other privileged
 * request. Two threads: one for the periodic stats sample, one so an interactive request is not
 * queued behind it.
 */
public final class PrivilegedExecutors {

    private static final Executor COMMANDS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "privileged-command");
        // Daemon: a wedged privileged command must never hold the process open.
        thread.setDaemon(true);
        return thread;
    });

    private PrivilegedExecutors() {}

    public static Executor commands() {
        return COMMANDS;
    }
}
