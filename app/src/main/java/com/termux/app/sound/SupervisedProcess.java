package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One process the launcher keeps running — the sound server, the session bus, the media helper.
 * It is the launcher's own child, never a terminal session's. A thread of its own reads the
 * process's output (so the pipe never fills) and then blocks on its exit: nothing polls. On an
 * exit the launcher did not ask for, the {@link Owner} decides what comes next, and a
 * {@link RestartCap} bounds the restarts.
 */
final class SupervisedProcess {

    private static final String LOG_TAG = "LauncherSound";
    /** How much of the process's last output is kept for its owner to read after an exit. */
    private static final int KEPT_LINES = 24;

    /** What happens after an exit nobody asked for. */
    enum AfterExit {
        /** Start it again, counted against the cap. */
        RESTART,
        /** Start it again without counting: the owner changed how it starts (the sink fallback). */
        RESTART_UNCOUNTED,
        /** Leave it down. */
        STOP
    }

    interface Owner {
        /** How to start the process now; null when it cannot be started at all. */
        @Nullable ProcessBuilder build(@NonNull SupervisedProcess process);

        /** The process exited by itself with {@code exitCode}, having last printed {@code output}. */
        @NonNull AfterExit afterExit(@NonNull SupervisedProcess process, int exitCode,
                                     @NonNull List<String> output);

        /** It reached the cap and is left down until {@link #start} is called again. */
        void onGaveUp(@NonNull SupervisedProcess process);
    }

    @NonNull final String name;
    @NonNull private final Owner owner;
    @NonNull private final RestartCap cap = new RestartCap();
    @Nullable private Process process;
    /** Whether the launcher wants it running; an exit while this is false is a stop it asked for. */
    private boolean wanted;
    /** Bumped on every start, so a waiter left from an earlier run acts on nothing. */
    private int generation;
    /** When the running process was started, on the elapsed-realtime clock. */
    private long startedAt;
    /** Set by {@link #restart}: the next exit is one the launcher asked for. */
    private boolean restartRequested;

    SupervisedProcess(@NonNull String name, @NonNull Owner owner) {
        this.name = name;
        this.owner = owner;
    }

    /** Whether it is running or about to be restarted. */
    synchronized boolean isWanted() {
        return wanted;
    }

    /** Start it unless it already runs; a fresh start also forgets earlier exits (the next need). */
    synchronized void start() {
        if (wanted && process != null) return;
        wanted = true;
        restartRequested = false;
        cap.reset();
        spawn();
    }

    /** How long the running process has been up; 0 when none runs. */
    synchronized long uptimeMs() {
        if (process == null) return 0;
        return Math.max(1, android.os.SystemClock.elapsedRealtime() - startedAt);
    }

    /**
     * Kill the running process and start it again, uncounted: the launcher judged it broken, so
     * the exit is not a crash of its own. Nothing happens when nothing runs.
     */
    synchronized void restart() {
        if (process == null) return;
        restartRequested = true;
        process.destroyForcibly();
    }

    /** Stop it now. The exit that follows is not a crash and starts nothing. */
    synchronized void stop() {
        wanted = false;
        generation++;
        Process running = process;
        process = null;
        if (running != null) running.destroy();
    }

    /** Must hold the lock. */
    private void spawn() {
        while (wanted) {
            ProcessBuilder builder = owner.build(this);
            if (builder == null) {
                wanted = false;
                return;
            }
            builder.redirectErrorStream(true);
            try {
                Process started = builder.start();
                process = started;
                startedAt = android.os.SystemClock.elapsedRealtime();
                int run = ++generation;
                Thread waiter = new Thread(() -> watch(started, run), "launcher-sound-" + name);
                waiter.setDaemon(true);
                waiter.start();
                return;
            } catch (IOException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Could not start " + name + ": " + e.getMessage());
                if (!cap.onExit(android.os.SystemClock.elapsedRealtime())) {
                    giveUp();
                    return;
                }
            }
        }
    }

    private void giveUp() {
        wanted = false;
        process = null;
        Logger.logWarn(LOG_TAG, name + " kept stopping; left down until it is next needed");
        owner.onGaveUp(this);
    }

    /** The waiter: drain the output, then block until the process exits, then decide. */
    private void watch(@NonNull Process running, int run) {
        ArrayDeque<String> kept = new ArrayDeque<>(KEPT_LINES);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (kept.size() == KEPT_LINES) kept.removeFirst();
                kept.addLast(line);
            }
        } catch (IOException ignored) {
            // The pipe closes when the process goes; its exit code says the rest.
        }
        int exitCode;
        try {
            exitCode = running.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        List<String> output = new ArrayList<>(kept);
        synchronized (this) {
            if (run != generation || !wanted) return;
            process = null;
            Logger.logInfo(LOG_TAG, name + " exited with " + exitCode
                + (output.isEmpty() ? "" : ": " + output.get(output.size() - 1)));
            AfterExit next;
            if (restartRequested) {
                restartRequested = false;
                next = AfterExit.RESTART_UNCOUNTED;
            } else {
                next = owner.afterExit(this, exitCode, output);
            }
            if (next == AfterExit.STOP) {
                wanted = false;
                return;
            }
            if (next == AfterExit.RESTART && !cap.onExit(android.os.SystemClock.elapsedRealtime())) {
                giveUp();
                return;
            }
            spawn();
        }
    }
}
