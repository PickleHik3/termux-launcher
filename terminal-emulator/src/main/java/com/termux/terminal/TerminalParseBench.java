package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Times how fast the terminal cores parse a file, with nothing drawn: the Java emulator alone, the
 * Ghostty core writing only ({@code vt_write}, no render state and no mirror), and the Ghostty core
 * with the mirror exactly as a session runs it (16 KiB reads, one mirror per 4 ms turn). Each run
 * takes fresh headless emulators on a background thread.
 *
 * <p>It runs in its own {@code app_process}, outside the launcher, so it cannot take the home screen
 * down and its numbers are not shared with drawing. As the app user, with the installed APK on the
 * class path:
 *
 * <pre>
 * CLASSPATH=$(pm path com.termux | cut -d: -f2) app_process /system/bin \
 *     com.termux.terminal.TerminalParseBench FILE [--runs N] [--cols C] [--rows R]
 *     [--transcript ROWS] [--modes java,ghostty-write,ghostty-mirror] [--lib-dir DIR] [--raw]
 * </pre>
 *
 * <p>The library is copied out of the APK into {@code --lib-dir} (default $TMPDIR), since
 * {@code app_process} cannot load it from the APK. The file is fed as a pty hands {@code cat}'s
 * output over, every LF as CR LF, unless {@code --raw}.
 *
 * <p>Prints one JSON object per line: a header, then one per run and mode, every run reported.
 * Throughput is in MB of 10^6 bytes. Java allocation comes from ART's cumulative
 * {@code art.gc.bytes-allocated}, which in this single-purpose process is the bench thread's (the
 * per-thread counter needs {@code Debug.startAllocCounting}, which slows every allocation); on a
 * desktop JVM from the thread's own counter.
 */
public final class TerminalParseBench {

    static final String MODE_JAVA = "java";
    static final String MODE_GHOSTTY_WRITE = "ghostty-write";
    static final String MODE_GHOSTTY_MIRROR = "ghostty-mirror";
    static final String[] ALL_MODES = {MODE_JAVA, MODE_GHOSTTY_WRITE, MODE_GHOSTTY_MIRROR};

    private static final int CELL_WIDTH_PIXELS = 10, CELL_HEIGHT_PIXELS = 20;
    private static final String USAGE = "usage: TerminalParseBench FILE [--runs N] [--cols C] [--rows R]"
        + " [--transcript ROWS] [--modes java,ghostty-write,ghostty-mirror] [--lib-dir DIR] [--raw]";

    private TerminalParseBench() {}

    /** What one bench run measures. */
    static final class Options {
        int runs = 3, columns = 52, rows = 45;
        int transcriptRows = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS;
        String[] modes = ALL_MODES;
    }

    public static void main(String[] args) {
        Options options = new Options();
        String file = null, libDir = null;
        boolean raw = false;
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--runs": options.runs = Integer.parseInt(args[++i]); break;
                    case "--cols": options.columns = Integer.parseInt(args[++i]); break;
                    case "--rows": options.rows = Integer.parseInt(args[++i]); break;
                    case "--transcript": options.transcriptRows = Integer.parseInt(args[++i]); break;
                    case "--modes": options.modes = args[++i].split(","); break;
                    case "--lib-dir": libDir = args[++i]; break;
                    case "--raw": raw = true; break;
                    default:
                        if (arg.startsWith("--") || file != null) throw new IllegalArgumentException(arg);
                        file = arg;
                }
            }
            if (file == null || options.runs < 1 || options.columns < 2 || options.rows < 2)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        String library = prepareLibrary(libDir);
        byte[] data;
        long fileBytes;
        try {
            data = readFile(new File(file));
            fileBytes = data.length;
            if (!raw) data = lfToCrLf(data);
        } catch (IOException e) {
            System.err.println("cannot read " + file + ": " + e);
            System.exit(1);
            return;
        }

        String header = new Json()
            .put("bench", "terminal-parse").put("file", file).put("file_bytes", fileBytes)
            .put("parsed_bytes", data.length).put("crlf", !raw)
            .put("cols", options.columns).put("rows", options.rows).put("transcript_rows", options.transcriptRows)
            .put("slice_bytes", TerminalSession.DRAIN_SLICE_BYTES)
            .put("turn_budget_ms", TerminalSession.DRAIN_BUDGET_NANOS / 1e6)
            .put("runtime", isAndroid() ? "android" : "jvm")
            .put("vm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"))
            .put("library", library)
            .put("library_loaded", GhosttyNative.isAvailable())
            .put("alloc_source", isAndroid() ? "art.gc.bytes-allocated (process)" : "thread allocated bytes")
            .toString();
        System.out.println(header);
        System.out.flush();

        final byte[] input = data;
        Thread thread = new Thread(() -> run(input, options, line -> {
            System.out.println(line);
            System.out.flush();
        }), "terminal-parse-bench");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // app_process keeps the VM up for nothing else; leave at once.
        System.exit(0);
    }

    /** Run every mode {@code options.runs} times, in turn, handing each result line to {@code out}. */
    static void run(byte[] data, Options options, Consumer<String> out) {
        for (int run = 1; run <= options.runs; run++) {
            for (String mode : options.modes) {
                out.accept(runOnce(run, mode, data, options).toString());
            }
        }
    }

    private static Json runOnce(int run, String mode, byte[] data, Options options) {
        Json result = new Json().put("run", run).put("mode", mode);
        boolean ghostty = !MODE_JAVA.equals(mode);
        if (ghostty && !GhosttyNative.isAvailable()) {
            Throwable why = GhosttyNative.loadError();
            return result.put("error", "library unavailable" + (why == null ? "" : ": " + why));
        }
        byte[] slice = new byte[TerminalSession.DRAIN_SLICE_BYTES];
        TerminalEmulator emulator = null;
        GhosttyCore core = null;
        try {
            if (MODE_GHOSTTY_WRITE.equals(mode)) {
                core = GhosttyCore.create(options.columns, options.rows, CELL_WIDTH_PIXELS, CELL_HEIGHT_PIXELS, null,
                    GhosttyCore.historyLimit(options.transcriptRows, options.columns));
                if (core == null) return result.put("error", "the Ghostty core did not start");
            } else {
                emulator = new TerminalEmulator(NO_OUTPUT, false, options.columns, options.rows,
                    CELL_WIDTH_PIXELS, CELL_HEIGHT_PIXELS, options.transcriptRows, null);
                if (ghostty) {
                    if (!emulator.useGhosttyCore()) return result.put("error", "the Ghostty core did not start");
                    core = emulator.ghosttyCore();
                }
            }

            int turns = 0;
            long mirrorNanos = 0, mirrorMaxNanos = 0;
            GhosttyMirror.Stats split = MODE_GHOSTTY_MIRROR.equals(mode) ? new GhosttyMirror.Stats() : null;
            GhosttyMirror.stats = split;
            long cpu0 = threadCpuNanos(), alloc0 = allocatedBytes(), gc0 = gcCount();
            long start = System.nanoTime();
            if (MODE_JAVA.equals(mode)) {
                for (int offset = 0; offset < data.length; offset += slice.length) {
                    int length = Math.min(slice.length, data.length - offset);
                    System.arraycopy(data, offset, slice, 0, length);
                    emulator.append(slice, length);
                }
            } else if (MODE_GHOSTTY_WRITE.equals(mode)) {
                for (int offset = 0; offset < data.length; offset += slice.length) {
                    int length = Math.min(slice.length, data.length - offset);
                    System.arraycopy(data, offset, slice, 0, length);
                    core.write(slice, 0, length);
                }
            } else {
                // TerminalSession's turns: 16 KiB reads until the turn's budget is spent, then one mirror.
                long turnStart = System.nanoTime();
                for (int offset = 0; offset < data.length; offset += slice.length) {
                    int length = Math.min(slice.length, data.length - offset);
                    System.arraycopy(data, offset, slice, 0, length);
                    emulator.appendBatched(slice, length);
                    long now = System.nanoTime();
                    if (now - turnStart >= TerminalSession.DRAIN_BUDGET_NANOS || offset + length >= data.length) {
                        emulator.flushBatchedOutput();
                        long mirror = System.nanoTime() - now;
                        mirrorNanos += mirror;
                        mirrorMaxNanos = Math.max(mirrorMaxNanos, mirror);
                        turns++;
                        turnStart = System.nanoTime();
                    }
                }
                if (!emulator.isGhosttyCoreActive()) return result.put("error", "the Ghostty core failed mid-run");
            }
            long wallNanos = System.nanoTime() - start;
            long cpu1 = threadCpuNanos(), alloc1 = allocatedBytes(), gc1 = gcCount();

            result.put("wall_ms", round(wallNanos / 1e6))
                .put("mb_per_s", round(data.length / 1e6 / (wallNanos / 1e9)))
                .put("cpu_ms", cpu0 < 0 || cpu1 < 0 ? -1 : round((cpu1 - cpu0) / 1e6))
                .put("alloc_bytes", alloc0 < 0 || alloc1 < 0 ? -1 : alloc1 - alloc0)
                .put("gc_count", gc0 < 0 || gc1 < 0 ? -1 : gc1 - gc0);
            if (MODE_GHOSTTY_MIRROR.equals(mode)) {
                // How long the mirror holds the thread: in all, and in the longest turn.
                result.put("turns", turns).put("mirror_ms", round(mirrorNanos / 1e6))
                    .put("mirror_max_ms", round(mirrorMaxNanos / 1e6))
                    .put("mirror_sync_ms", round(split.syncNanos / 1e6))
                    .put("mirror_sync_max_ms", round(split.syncMaxNanos / 1e6))
                    .put("mirror_screen_ms", round(split.screenNanos / 1e6))
                    .put("mirror_screen_max_ms", round(split.screenMaxNanos / 1e6))
                    .put("mirror_screen_rows", split.screenRows)
                    .put("mirror_history_ms", round(split.historyNanos / 1e6))
                    .put("mirror_history_max_ms", round(split.historyMaxNanos / 1e6))
                    .put("mirror_history_rows", split.historyRows);
            }
            if (emulator != null) {
                result.put("transcript_rows_used", emulator.getScreen().getActiveTranscriptRows())
                    .put("tail", tail(emulator));
            }
            if (core != null) result.putRaw("ghostty", ghosttyMemory(core));
            return result;
        } catch (RuntimeException e) {
            return result.put("error", e.toString());
        } finally {
            GhosttyMirror.stats = null;
            if (emulator != null) emulator.releaseGhosttyCore();
            else if (core != null) core.release();
        }
    }

    /** Memory after the run, then a full compression pass and memory again. */
    private static String ghosttyMemory(GhosttyCore core) {
        Json json = memory(core.memoryUsage());
        long start = System.nanoTime();
        int result = core.compress(true);
        long nanos = System.nanoTime() - start;
        json.put("compress", result == GhosttyNative.COMPRESS_UNSUPPORTED ? "unsupported"
                : result == GhosttyNative.COMPRESS_PENDING ? "pending"
                : result == GhosttyNative.COMPRESS_COMPLETE ? "complete" : "error")
            .put("compress_ms", round(nanos / 1e6))
            .putRaw("after_compress", memory(core.memoryUsage()).toString());
        return json.toString();
    }

    private static Json memory(long[] usage) {
        Json json = new Json();
        if (usage == null || usage.length <= GhosttyNative.MEM_COMPRESSION_SUPPORTED) return json.put("error", "unavailable");
        return json.put("resident_bytes", usage[GhosttyNative.MEM_RESIDENT_BYTES])
            .put("virtual_bytes", usage[GhosttyNative.MEM_VIRTUAL_BYTES])
            .put("compressed_bytes", usage[GhosttyNative.MEM_COMPRESSED_BYTES])
            .put("pages", usage[GhosttyNative.MEM_PAGES])
            .put("compressed_pages", usage[GhosttyNative.MEM_COMPRESSED_PAGES])
            .put("compression_supported", usage[GhosttyNative.MEM_COMPRESSION_SUPPORTED] == 1);
    }

    /**
     * The last two screen rows with text, a wrapped line joined, so the cores' end states can be
     * compared.
     */
    private static String tail(TerminalEmulator emulator) {
        TerminalBuffer screen = emulator.getScreen();
        for (int y = emulator.mRows - 1; y >= 0; y--) {
            if (screen.getSelectedText(0, y, emulator.mColumns, y).trim().isEmpty()) continue;
            return screen.getSelectedText(0, Math.max(0, y - 1), emulator.mColumns, y).trim();
        }
        return "";
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Every LF preceded by CR, as a pty with onlcr hands {@code cat}'s output to the terminal. */
    static byte[] lfToCrLf(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + data.length / 32);
        for (byte b : data) {
            if (b == '\n') out.write('\r');
            out.write(b);
        }
        return out.toByteArray();
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(0, Math.min(Integer.MAX_VALUE, file.length())));
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    // The platform's counters, through reflection so the same class runs on a desktop JVM.

    static boolean isAndroid() {
        return "Dalvik".equals(System.getProperty("java.vm.name"));
    }

    private static long threadCpuNanos() {
        try {
            if (isAndroid())
                return (Long) Class.forName("android.os.Debug").getMethod("threadCpuTimeNanos").invoke(null);
            Object bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
            return (Long) Class.forName("java.lang.management.ThreadMXBean").getMethod("getCurrentThreadCpuTime").invoke(bean);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static long allocatedBytes() {
        try {
            if (isAndroid()) return androidRuntimeStat("art.gc.bytes-allocated");
            Object bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
            Method method = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
            return (Long) method.invoke(bean, Thread.currentThread().getId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static long gcCount() {
        try {
            return isAndroid() ? androidRuntimeStat("art.gc.gc-count") : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static long androidRuntimeStat(String name) throws ReflectiveOperationException {
        Object value = Class.forName("android.os.Debug").getMethod("getRuntimeStat", String.class).invoke(null, name);
        return value == null ? -1 : Long.parseLong(value.toString());
    }

    /**
     * Point the library loader at a copy of the library taken out of the APK on the class path,
     * unless a directory is already set. Says where the library came from.
     */
    private static String prepareLibrary(String libDir) {
        String dir = System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY);
        if (dir != null && !dir.isEmpty()) return dir;
        if (!isAndroid()) return "java.library.path";
        String apk = null;
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.endsWith(".apk")) {
                apk = entry;
                break;
            }
        }
        if (apk == null) return "no APK on the class path";
        if (libDir == null) libDir = System.getenv("TMPDIR");
        if (libDir == null) libDir = System.getProperty("java.io.tmpdir");
        try {
            String[] abis = (String[]) Class.forName("android.os.Build").getField("SUPPORTED_ABIS").get(null);
            try (ZipFile zip = new ZipFile(apk)) {
                for (String abi : abis) {
                    String name = "lib/" + abi + "/libtermux-ghostty.so";
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null) continue;
                    File target = new File(libDir, "libtermux-ghostty.so");
                    try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                    System.setProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY, libDir);
                    return apk + "!/" + name;
                }
            }
            return "no libtermux-ghostty.so in " + apk;
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            return "could not copy the library: " + e;
        }
    }

    /** Output that goes nowhere: the bench has no program to answer and nobody to notify. */
    private static final TerminalOutput NO_OUTPUT = new TerminalOutput() {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    };

    /** One flat JSON object, written by hand so the bench needs nothing from the platform. */
    static final class Json {
        private final StringBuilder mText = new StringBuilder("{");

        private Json key(String key) {
            if (mText.length() > 1) mText.append(',');
            return string(key).append(":");
        }

        private Json append(String raw) {
            mText.append(raw);
            return this;
        }

        private Json string(String value) {
            mText.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"' || c == '\\') mText.append('\\').append(c);
                else if (c < 0x20) mText.append(String.format(Locale.US, "\\u%04x", (int) c));
                else mText.append(c);
            }
            mText.append('"');
            return this;
        }

        Json put(String key, String value) {
            return key(key).string(value == null ? "" : value);
        }

        Json put(String key, long value) {
            return key(key).append(Long.toString(value));
        }

        Json put(String key, double value) {
            return key(key).append(Double.isFinite(value) ? Double.toString(value) : "null");
        }

        Json put(String key, boolean value) {
            return key(key).append(Boolean.toString(value));
        }

        /** {@code json} is already a JSON value. */
        Json putRaw(String key, String json) {
            return key(key).append(json);
        }

        @Override
        public String toString() {
            return mText + "}";
        }
    }
}
