package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small rotating log of what the on-device AI did, {@code files/tai/events.log}: loads,
 * unloads, evictions, idle exits, recovered crashes, API errors, benchmark runs and the memory
 * guard. One line per event, e.g.
 * {@code 2026-10-01T09:30:12.345Z load_ok model=qwen3-0.6b backend=mnn_llm accel=gpu ctx=4096 ms=2210 mem=512MB reason="..."}.
 *
 * <p>Never prompts, replies or other user text: callers pass identifiers, numbers and a short
 * reason, and {@link #formatLine} flattens and truncates the reason so even an error message that
 * quoted something stays one bounded line.
 *
 * <p>Bounded: once {@code events.log} passes {@link #MAX_BYTES} it is renamed to
 * {@code events.log.1}, replacing the older one, and a fresh file starts, so at most two files of
 * about 256 KB each exist.
 *
 * <p>Cross-process design: both the app process and the {@code :tai_runtime} process write. Every
 * process appends the file itself (no forwarding, so an event survives the other process dying)
 * under an exclusive {@link FileLock} on the sidecar {@code events.log.lock}, which makes the
 * size check, the rotation and the append one step across processes; a static monitor serialises
 * threads of one process, because the OS lock is per process and a second in-process lock on the
 * same file throws. Calls through {@link #log} only queue the line to one daemon thread, so the
 * token path never waits on disk. Any I/O failure is swallowed: logging must never break a load.
 */
public final class TaiEventLog {
    public static final String FILE_NAME = "events.log";
    public static final String ROTATED_NAME = "events.log.1";
    private static final String LOCK_NAME = "events.log.lock";
    /** The live file rotates once it is larger than this. */
    static final long MAX_BYTES = 256L * 1024L;
    private static final int MAX_REASON_CHARS = 160;
    public static final int DEFAULT_TAIL_LINES = 100;

    public static final String LOAD_START = "load_start";
    public static final String LOAD_OK = "load_ok";
    public static final String LOAD_FAIL = "load_fail";
    public static final String UNLOAD = "unload";
    public static final String EVICT = "evict";
    public static final String IDLE_EXIT = "idle_exit";
    public static final String CRASH_RECOVERED = "crash_recovered";
    public static final String API_ERROR = "api_error";
    public static final String BENCH_START = "bench_start";
    public static final String BENCH_DONE = "bench_done";
    public static final String OOM_GUARD = "oom_guard";
    /** A load went ahead on a different accelerator than the model's first choice; the reason says why. */
    public static final String ACCEL_FALLBACK = "accel_fallback";

    /** Serialises the threads of this process; the file lock covers the other process. */
    private static final Object PROCESS_MONITOR = new Object();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-event-log");
        thread.setDaemon(true);
        return thread;
    });

    @NonNull private final File directory;
    private final long maxBytes;

    TaiEventLog(@NonNull File directory, long maxBytes) {
        this.directory = directory;
        this.maxBytes = maxBytes;
    }

    /** The log under {@code filesDir/tai/}. */
    @NonNull
    public static TaiEventLog in(@NonNull File filesDir) {
        return new TaiEventLog(new File(filesDir, "tai"), MAX_BYTES);
    }

    /** Queues one event; returns at once. Unknown figures are {@code null} / {@code <= 0}. */
    public static void log(
        @Nullable Context context,
        @NonNull String event,
        @Nullable String modelId,
        @Nullable String backend,
        @Nullable String accelerator,
        int contextWindow,
        long ms,
        long memBytes,
        @Nullable String reason
    ) {
        if (context == null) return;
        final String line = formatLine(System.currentTimeMillis(), event, modelId, backend, accelerator,
            contextWindow, ms, memBytes, reason);
        final TaiEventLog target = in(context.getApplicationContext().getFilesDir());
        try {
            WRITER.execute(() -> target.append(line));
        } catch (RuntimeException ignored) {
            // Executor rejected (process shutting down): drop the line.
        }
    }

    /** An event with only a reason, for the callers that know no model. */
    public static void log(@Nullable Context context, @NonNull String event, @Nullable String reason) {
        log(context, event, null, null, null, 0, 0L, 0L, reason);
    }

    @NonNull
    static String formatLine(
        long timeMs,
        @NonNull String event,
        @Nullable String modelId,
        @Nullable String backend,
        @Nullable String accelerator,
        int contextWindow,
        long ms,
        long memBytes,
        @Nullable String reason
    ) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder line = new StringBuilder(160).append(format.format(new Date(timeMs))).append(' ').append(event);
        appendField(line, "model", modelId);
        appendField(line, "backend", backend);
        appendField(line, "accel", accelerator);
        if (contextWindow > 0) line.append(" ctx=").append(contextWindow);
        if (ms > 0L) line.append(" ms=").append(ms);
        if (memBytes > 0L) line.append(" mem=").append(memBytes / (1024L * 1024L)).append("MB");
        String text = flatten(reason);
        if (!text.isEmpty()) line.append(" reason=\"").append(text).append('"');
        return line.toString();
    }

    private static void appendField(@NonNull StringBuilder line, @NonNull String name, @Nullable String value) {
        String text = flatten(value);
        if (!text.isEmpty()) line.append(' ').append(name).append('=').append(text.replace(' ', '_'));
    }

    /** One line, no quotes, at most {@link #MAX_REASON_CHARS} characters. */
    @NonNull
    static String flatten(@Nullable String value) {
        if (value == null) return "";
        String text = value.replaceAll("[\\r\\n\\t]+", " ").replace('"', '\'').trim();
        if (text.length() > MAX_REASON_CHARS) text = text.substring(0, MAX_REASON_CHARS) + "...";
        return text;
    }

    /** Appends one line now, rotating first when the file is over the limit. Never throws. */
    void append(@NonNull String line) {
        synchronized (PROCESS_MONITOR) {
            try {
                if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return;
                try (RandomAccessFile lockFile = new RandomAccessFile(new File(directory, LOCK_NAME), "rw");
                     FileChannel channel = lockFile.getChannel();
                     FileLock ignored = channel.lock()) {
                    File live = new File(directory, FILE_NAME);
                    if (live.length() > maxBytes) {
                        File rotated = new File(directory, ROTATED_NAME);
                        //noinspection ResultOfMethodCallIgnored
                        rotated.delete();
                        if (!live.renameTo(rotated)) {
                            //noinspection ResultOfMethodCallIgnored
                            live.delete();
                        }
                    }
                    try (FileOutputStream output = new FileOutputStream(live, true)) {
                        output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Diagnostics only; a full disk or a denied lock must not touch the caller.
            }
        }
    }

    /** The last {@code count} lines across {@code events.log.1} then {@code events.log}, oldest first. */
    @NonNull
    public List<String> tail(int count) {
        int wanted = Math.max(1, count);
        Deque<String> lines = new ArrayDeque<>();
        synchronized (PROCESS_MONITOR) {
            for (String name : new String[]{ROTATED_NAME, FILE_NAME}) {
                for (String line : readLines(new File(directory, name))) {
                    lines.addLast(line);
                    if (lines.size() > wanted) lines.removeFirst();
                }
            }
        }
        return new ArrayList<>(lines);
    }

    /** The whole text of both files, oldest first, for the diagnostics share. */
    @NonNull
    public String readAll() {
        StringBuilder text = new StringBuilder();
        synchronized (PROCESS_MONITOR) {
            for (String name : new String[]{ROTATED_NAME, FILE_NAME}) {
                for (String line : readLines(new File(directory, name))) text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    /** Deletes both files; returns whether anything was there. */
    public boolean clear() {
        boolean any = false;
        synchronized (PROCESS_MONITOR) {
            for (String name : new String[]{FILE_NAME, ROTATED_NAME}) {
                File file = new File(directory, name);
                if (file.isFile()) {
                    any = true;
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        return any;
    }

    @NonNull
    private static List<String> readLines(@NonNull File file) {
        List<String> lines = new ArrayList<>();
        if (!file.isFile()) return lines;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (IOException | RuntimeException ignored) {
            // Unreadable: show nothing for this file.
        }
        return lines;
    }
}
