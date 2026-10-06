package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs the Command widget's command through the login shell and remembers the last result.
 *
 * <p>The command runs as {@code $PREFIX/bin/bash -lc <command>} in {@code ~} with the same
 * environment a terminal session gets, stdout and stderr merged, the output capped at
 * {@link #OUTPUT_CAP} bytes and the process killed after {@link #TIMEOUT_MS}. Runs go one at a
 * time on their own thread, not the widgets' shared io thread, so a slow command never holds up a
 * calendar query or a file read. The last result per command is kept in
 * {@code builtin_shell_results} so a restart shows the last output straight away.</p>
 */
public final class ShellWidgetRunner {
    /** What one run left behind. */
    public static final class Result {
        @NonNull public final String command;
        @NonNull public final String output;
        public final int exitCode;
        public final long durationMs;
        public final long finishedAt;
        public final boolean timedOut;
        /** The shell could not be started at all (no bootstrap yet, say). */
        public final boolean failed;

        public Result(@NonNull String command, @NonNull String output, int exitCode, long durationMs,
                      long finishedAt, boolean timedOut, boolean failed) {
            this.command = command; this.output = output; this.exitCode = exitCode;
            this.durationMs = durationMs; this.finishedAt = finishedAt;
            this.timedOut = timedOut; this.failed = failed;
        }

        public boolean succeeded() { return !failed && !timedOut && exitCode == 0; }
    }

    public static final int OUTPUT_CAP = 4096;
    public static final long TIMEOUT_MS = 20_000L;
    static final String PREFERENCES = "builtin_shell_results";
    private static final Pattern ANSI = Pattern.compile("\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-Z\\\\-_])");

    private static final ThreadPoolExecutor RUNS = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "builtin-widget-shell");
            thread.setDaemon(true);
            return thread;
        });
    static { RUNS.allowCoreThreadTimeOut(true); }

    private ShellWidgetRunner() { }

    /** The executor runs go on: one at a time, its thread gone when idle. */
    @NonNull static ExecutorService executor() { return RUNS; }

    /** Runs {@code command} and saves the result; blocks for up to {@link #TIMEOUT_MS} and a little. */
    @WorkerThread @NonNull
    public static Result run(@NonNull Context context, @NonNull String command) {
        long started = SystemClock.elapsedRealtime();
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash", "-lc", command);
            File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
            if (home.isDirectory()) builder.directory(home);
            builder.redirectErrorStream(true);
            HashMap<String, String> environment =
                new TermuxShellEnvironment().getEnvironment(context, false);
            Map<String, String> target = builder.environment();
            target.clear();
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                // The process environment refuses nulls; an unset variable is simply left out.
                if (entry.getKey() != null && entry.getValue() != null) {
                    target.put(entry.getKey(), entry.getValue());
                }
            }
            if (home.isDirectory()) target.put("PWD", home.getAbsolutePath());
            process = builder.start();
        } catch (IOException | RuntimeException e) {
            Result result = new Result(command, "", -1, 0L, System.currentTimeMillis(), false, true);
            save(context, result);
            return result;
        }
        try { process.getOutputStream().close(); } catch (IOException ignored) { }

        Collector collector = new Collector(process.getInputStream(), OUTPUT_CAP);
        Thread reader = new Thread(collector, "builtin-widget-shell-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.destroyForcibly();
            try { process.waitFor(1, TimeUnit.SECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // A background child can hold the pipe open after the shell exits; do not wait on it.
        try { reader.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (reader.isAlive()) {
            try { process.getInputStream().close(); } catch (IOException ignored) { }
        }

        int exit = -1;
        if (finished) {
            try { exit = process.exitValue(); } catch (IllegalThreadStateException ignored) { }
        }
        String output = clean(collector.text());
        Result result = new Result(command, output, exit, SystemClock.elapsedRealtime() - started,
            System.currentTimeMillis(), !finished, false);
        save(context, result);
        return result;
    }

    /** Reads a stream to its end, keeping at most {@code cap} bytes and discarding the rest. */
    private static final class Collector implements Runnable {
        private final InputStream in;
        private final int cap;
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private volatile boolean truncated;

        Collector(@NonNull InputStream in, int cap) { this.in = in; this.cap = cap; }

        @Override public void run() {
            byte[] buffer = new byte[1024];
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (kept) {
                        int room = cap - kept.size();
                        if (room > 0) kept.write(buffer, 0, Math.min(room, read));
                        if (read > room) truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // Closed under us after a timeout; what was read is kept.
            }
        }

        @NonNull String text() {
            byte[] bytes;
            synchronized (kept) { bytes = kept.toByteArray(); }
            return decode(bytes, truncated);
        }
    }

    /** {@code bytes} as text, with "…" on the end when more was cut off; a split character is dropped. */
    @NonNull
    static String decode(@NonNull byte[] bytes, boolean truncated) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!truncated) return text;
        while (text.endsWith("\ufffd")) text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    /**
     * Output as the widget prints it: escape sequences gone, a carriage-return progress line
     * reduced to what it last showed, trailing blank lines dropped.
     */
    @NonNull
    static String clean(@NonNull String raw) {
        String text = ANSI.matcher(raw).replaceAll("");
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            int cr = line.lastIndexOf('\r');
            if (cr >= 0) line = line.substring(cr + 1);
            if (i > 0) out.append('\n');
            out.append(line);
        }
        int end = out.length();
        while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) end--;
        return out.substring(0, end);
    }

    /** The first line with anything on it, for the one-line layouts. */
    @NonNull
    static String firstLine(@NonNull String output) {
        for (String line : output.split("\n")) {
            if (!line.trim().isEmpty()) return line.trim();
        }
        return "";
    }

    // ----- the last result ------------------------------------------------------------------

    @NonNull
    static String key(@NonNull String command) {
        return "cmd_" + Integer.toHexString(command.hashCode());
    }

    /** The last saved result for {@code command}, or null. Reads preferences: not on the main thread. */
    @WorkerThread @Nullable
    public static Result load(@NonNull Context context, @NonNull String command) {
        String stored = preferences(context).getString(key(command), null);
        if (stored == null) return null;
        try {
            JSONObject json = new JSONObject(stored);
            if (!command.equals(json.optString("command"))) return null;
            return new Result(command, json.optString("output"), json.optInt("exit", -1),
                json.optLong("duration"), json.optLong("finished"), json.optBoolean("timedOut"),
                json.optBoolean("failed"));
        } catch (JSONException e) {
            return null;
        }
    }

    private static void save(@NonNull Context context, @NonNull Result result) {
        try {
            JSONObject json = new JSONObject();
            json.put("command", result.command);
            json.put("output", result.output);
            json.put("exit", result.exitCode);
            json.put("duration", result.durationMs);
            json.put("finished", result.finishedAt);
            json.put("timedOut", result.timedOut);
            json.put("failed", result.failed);
            preferences(context).edit().putString(key(result.command), json.toString()).apply();
        } catch (JSONException | RuntimeException ignored) {
            // The result still shows; it just will not survive a restart.
        }
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
