package com.termux.privileged;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One long-lived {@code su} shell that runs commands one at a time, instead of forking {@code su -c}
 * for every command (root managers toast and log each fork).
 *
 * <p>Each command is written as a single line that runs it in a subshell and then prints a unique
 * sentinel to stdout (with the exit code) and to stderr; the caller waits until both are seen.
 * The session starts on the first call and stops {@link #IDLE_STOP_MS} after the last command, so
 * at rest no process and no thread remain. No Android imports: plain JUnit drives it.
 */
public final class RootShellSession {

    public static final long IDLE_STOP_MS = 60_000L;
    public static final int MAX_OUTPUT_CHARS = 8 * 1024 * 1024;
    private static final long STOP_GRACE_MS = 1_000L;
    private static final long JOIN_MS = 500L;

    public interface Starter {
        Process start() throws IOException;
    }

    public interface Clock {
        long nowMs();
    }

    public interface Scheduler {
        Cancellable schedule(Runnable task, long delayMs);
    }

    public interface Cancellable {
        void cancel();
    }

    public enum Failure { TIMEOUT, DIED, START_FAILED, OUTPUT_TOO_LARGE }

    public static final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        /** Null when the command ran to its exit code. */
        public final Failure failure;
        /** Human-readable cause when {@link #failure} is set. */
        public final String reason;

        private Result(int exitCode, String stdout, String stderr, Failure failure, String reason) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.failure = failure;
            this.reason = reason;
        }

        static Result ok(int exitCode, String stdout, String stderr) {
            return new Result(exitCode, stdout, stderr, null, null);
        }

        static Result failed(Failure failure, String reason) {
            return new Result(-1, "", "", failure, reason);
        }
    }

    /** Production scheduler: a pool whose only thread times out, so nothing lives at rest. */
    public static Scheduler defaultScheduler() {
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "root-shell-idle");
            t.setDaemon(true);
            return t;
        });
        executor.setKeepAliveTime(3, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setRemoveOnCancelPolicy(true);
        return (task, delayMs) -> {
            final ScheduledFuture<?> f = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
            return () -> f.cancel(false);
        };
    }

    public static RootShellSession createDefault() {
        return new RootShellSession(() -> new ProcessBuilder("su").start(),
            System::currentTimeMillis, defaultScheduler(), MAX_OUTPUT_CHARS);
    }

    private final Starter starter;
    private final Clock clock;
    private final Scheduler scheduler;
    private final int maxOutputChars;
    /** Fair, so callers queued behind a running command go in arrival order. */
    private final ReentrantLock runLock = new ReentrantLock(true);

    // Guarded by runLock.
    private Conn conn;
    private Cancellable idleStop;
    private long idleGeneration;

    public RootShellSession(Starter starter, Clock clock, Scheduler scheduler) {
        this(starter, clock, scheduler, MAX_OUTPUT_CHARS);
    }

    RootShellSession(Starter starter, Clock clock, Scheduler scheduler, int maxOutputChars) {
        this.starter = starter;
        this.clock = clock;
        this.scheduler = scheduler;
        this.maxOutputChars = maxOutputChars;
    }

    public Result run(String command, long timeoutMs) {
        runLock.lock();
        try {
            cancelIdleStop();
            if (conn == null || conn.dead) {
                try {
                    conn = new Conn(starter.start());
                } catch (IOException | RuntimeException e) {
                    conn = null;
                    return Result.failed(Failure.START_FAILED, String.valueOf(e.getMessage()));
                }
            }
            Result result = execute(conn, command, timeoutMs);
            if (result.failure != null) {
                conn.kill();
                conn = null;
            } else {
                scheduleIdleStop();
            }
            return result;
        } finally {
            runLock.unlock();
        }
    }

    /** Stops the shell at once; the next {@link #run} starts a new one. */
    public void close() {
        runLock.lock();
        try {
            cancelIdleStop();
            stopGracefully();
        } finally {
            runLock.unlock();
        }
    }

    private Result execute(Conn c, String command, long timeoutMs) {
        final String s = UUID.randomUUID().toString().replace("-", "");
        final String outMark = "\n" + s + ":";
        final String errMark = "\n" + s + "\n";
        c.reset();
        String line = "( " + command + " ) </dev/null; __tl_rc=$?; printf '\\n" + s
            + ":%d\\n' \"$__tl_rc\"; printf '\\n" + s + "\\n' >&2\n";
        try {
            c.stdin.write(line.getBytes(StandardCharsets.UTF_8));
            c.stdin.flush();
        } catch (IOException e) {
            return Result.failed(Failure.DIED, "root shell closed: " + e.getMessage());
        }
        final long deadline = clock.nowMs() + timeoutMs;
        synchronized (c.mon) {
            while (true) {
                if (c.out.length() > maxOutputChars || c.err.length() > maxOutputChars) {
                    return Result.failed(Failure.OUTPUT_TOO_LARGE, "Output too large");
                }
                int oi = c.out.indexOf(outMark, c.scanOut);
                int nl = oi < 0 ? -1 : c.out.indexOf("\n", oi + outMark.length());
                int ei = c.err.indexOf(errMark, c.scanErr);
                if (nl >= 0 && ei >= 0) {
                    int rc;
                    try {
                        rc = Integer.parseInt(c.out.substring(oi + outMark.length(), nl).trim());
                    } catch (NumberFormatException e) {
                        return Result.failed(Failure.DIED, "unreadable exit code");
                    }
                    return Result.ok(rc, c.out.substring(0, oi), c.err.substring(0, ei));
                }
                if (c.outEof || c.errEof) {
                    return Result.failed(Failure.DIED, "root shell exited");
                }
                long remaining = deadline - clock.nowMs();
                if (remaining <= 0) {
                    return Result.failed(Failure.TIMEOUT, "Command timed out");
                }
                // Rescan only the tail next time; a sentinel may straddle two reads.
                c.scanOut = oi >= 0 ? oi : Math.max(0, c.out.length() - outMark.length());
                c.scanErr = ei >= 0 ? ei : Math.max(0, c.err.length() - errMark.length());
                try {
                    c.mon.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.failed(Failure.TIMEOUT, "interrupted");
                }
            }
        }
    }

    private void scheduleIdleStop() {
        final long generation = ++idleGeneration;
        idleStop = scheduler.schedule(() -> {
            // A command in flight holds the lock and reschedules when it ends.
            if (!runLock.tryLock()) {
                return;
            }
            try {
                if (generation == idleGeneration) {
                    stopGracefully();
                }
            } finally {
                runLock.unlock();
            }
        }, IDLE_STOP_MS);
    }

    private void cancelIdleStop() {
        idleGeneration++;
        if (idleStop != null) {
            idleStop.cancel();
            idleStop = null;
        }
    }

    private void stopGracefully() {
        Conn c = conn;
        conn = null;
        if (c != null) {
            c.stopGracefully();
        }
    }

    /** One su process with its two reader threads and their buffers. */
    private final class Conn {
        final Process process;
        final OutputStream stdin;
        final Object mon = new Object();
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();
        final Thread outReader;
        final Thread errReader;
        int scanOut;
        int scanErr;
        boolean outEof;
        boolean errEof;
        volatile boolean dead;

        Conn(Process process) {
            this.process = process;
            this.stdin = process.getOutputStream();
            outReader = startReader("root-shell-stdout", process.getInputStream(), true);
            errReader = startReader("root-shell-stderr", process.getErrorStream(), false);
        }

        private Thread startReader(String name, final InputStream in, final boolean isOut) {
            Thread t = new Thread(() -> {
                final StringBuilder sink = isOut ? out : err;
                char[] buf = new char[4096];
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    int n;
                    while ((n = r.read(buf)) > 0) {
                        synchronized (mon) {
                            // Past the cap nothing more is kept; the caller fails the command.
                            if (sink.length() <= maxOutputChars) {
                                sink.append(buf, 0, n);
                            }
                            mon.notifyAll();
                        }
                    }
                } catch (IOException ignored) {
                    // The stream closing is how a dead shell shows up.
                }
                synchronized (mon) {
                    if (isOut) {
                        outEof = true;
                    } else {
                        errEof = true;
                    }
                    mon.notifyAll();
                }
            }, name);
            t.setDaemon(true);
            t.start();
            return t;
        }

        /** Drops anything left over from the previous command. */
        void reset() {
            synchronized (mon) {
                out.setLength(0);
                err.setLength(0);
                scanOut = 0;
                scanErr = 0;
            }
        }

        void kill() {
            dead = true;
            process.destroyForcibly();
            closeQuietly();
            join();
        }

        void stopGracefully() {
            dead = true;
            try {
                stdin.write("exit\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException ignored) {
                // Already gone.
            }
            closeQuietly();
            boolean exited = false;
            try {
                exited = process.waitFor(STOP_GRACE_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!exited) {
                process.destroyForcibly();
            }
            join();
        }

        private void closeQuietly() {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // Nothing to do.
            }
        }

        private void join() {
            try {
                outReader.join(JOIN_MS);
                errReader.join(JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
