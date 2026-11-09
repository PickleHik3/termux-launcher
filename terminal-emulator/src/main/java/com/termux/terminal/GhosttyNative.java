package com.termux.terminal;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * The JNI surface of {@code libtermux-ghostty.so}: libghostty-vt linked into a small shim
 * ({@code terminal-emulator/ghostty/ghostty_jni.c}). The library is only in builds made with
 * {@code -Pghostty=true}; everywhere else {@link #isAvailable()} is false and nothing here runs.
 *
 * <p>Every call takes a handle from {@link #nativeCreate} and is made on the session's main thread.
 * A zero or freed handle does nothing.
 */
final class GhosttyNative {

    /** Ints per cell in the row arrays the shim fills; the layout matches ghostty_jni.c. */
    static final int CELL_INTS = 5;
    static final int CELL_CODEPOINT = 0;
    /** Style flags in bits 0-15, the cell's width kind in 16-17, its extra code points from 20. */
    static final int CELL_ATTRS = 1;
    static final int CELL_FG = 2;
    static final int CELL_BG = 3;
    static final int CELL_UNDERLINE = 4;

    /** Width kinds in {@link #CELL_ATTRS}. */
    static final int WIDE_NARROW = 0;
    static final int WIDE_WIDE = 1;
    static final int WIDE_SPACER_TAIL = 2;
    static final int WIDE_SPACER_HEAD = 3;

    /** Set in a row result when the row soft-wraps into the next; the low bits count its extras. */
    static final int ROW_WRAPPED = 1 << 24;
    static final int ROW_EXTRA_MASK = ROW_WRAPPED - 1;
    /** Set in a row result when OSC 133 marked the row as the first line of a shell prompt. */
    static final int ROW_PROMPT = 1 << 25;

    /**
     * The link runs a sync or row read writes: element 0 is the run count, then per run of
     * {@link #LINK_INTS} its row, first column, end column (exclusive), and the offset and length
     * of its UTF-8 URI in the byte array passed beside it.
     */
    static final int LINK_Y = 0;
    static final int LINK_X0 = 1;
    static final int LINK_X1 = 2;
    static final int LINK_URI_OFFSET = 3;
    static final int LINK_URI_LENGTH = 4;
    static final int LINK_INTS = 5;

    /** Extra code points (combining marks and the like) one sync or row read carries at most. */
    static final int EXTRA_CAPACITY = 8192;

    /** Slots of the state block {@link #nativeSync} fills. */
    static final int STATE_COLS = 0;
    static final int STATE_ROWS = 1;
    static final int STATE_CURSOR_X = 2;
    static final int STATE_CURSOR_Y = 3;
    static final int STATE_FLAGS = 4;
    static final int STATE_SCROLLED = 5;
    static final int STATE_HISTORY = 6;
    static final int STATE_MODES = 7;
    static final int STATE_KITTY_FLAGS = 8;
    static final int STATE_CURSOR_STYLE = 9;
    /** The pointer shape a program asked for with OSC 22, as a {@link #mouseShapeName} index. */
    static final int STATE_MOUSE_SHAPE = 10;
    static final int STATE_COUNT = 11;

    /** Bits of {@link #STATE_FLAGS}. */
    static final int FLAG_CURSOR_VISIBLE = 1;
    static final int FLAG_ALT_SCREEN = 1 << 1;
    static final int FLAG_CURSOR_BLINK = 1 << 2;
    static final int FLAG_PALETTE_CHANGED = 1 << 3;
    static final int FLAG_TITLE_CHANGED = 1 << 4;
    static final int FLAG_PWD_CHANGED = 1 << 5;
    static final int FLAG_BELL = 1 << 6;
    static final int FLAG_DIRTY_FULL = 1 << 7;

    /** Bits of {@link #STATE_MODES}, one per DEC private mode. */
    static final int MODE_APPLICATION_CURSOR_KEYS = 1;       // 1
    static final int MODE_REVERSE_VIDEO = 1 << 1;            // 5
    static final int MODE_ORIGIN = 1 << 2;                   // 6
    static final int MODE_AUTOWRAP = 1 << 3;                 // 7
    static final int MODE_CURSOR_VISIBLE = 1 << 4;           // 25
    static final int MODE_APPLICATION_KEYPAD = 1 << 5;       // 66
    static final int MODE_MOUSE_PRESS_RELEASE = 1 << 6;      // 1000
    static final int MODE_MOUSE_BUTTON_EVENT = 1 << 7;       // 1002
    static final int MODE_MOUSE_ANY_EVENT = 1 << 8;          // 1003
    static final int MODE_FOCUS_EVENTS = 1 << 9;             // 1004
    static final int MODE_MOUSE_SGR = 1 << 10;               // 1006
    static final int MODE_MOUSE_SGR_PIXELS = 1 << 11;        // 1016
    static final int MODE_BRACKETED_PASTE = 1 << 12;         // 2004
    static final int MODE_SYNCHRONIZED_OUTPUT = 1 << 13;     // 2026
    static final int MODE_PASTE_EVENTS = 1 << 14;            // 5522
    static final int MODE_MOUSE_X10 = 1 << 15;               // 9

    /** Cursor shapes in {@link #STATE_CURSOR_STYLE}. */
    static final int CURSOR_BAR = 0;
    static final int CURSOR_BLOCK = 1;
    static final int CURSOR_UNDERLINE = 2;
    static final int CURSOR_BLOCK_HOLLOW = 3;

    // Kinds of Sink.onEvent.

    /** OSC 133: {@code a} is a {@code SEMANTIC_*} step, {@code b} the exit code or {@link #NO_EXIT_CODE}. */
    static final int EVENT_PROMPT = 1;
    /** OSC 9;4: {@code a} is the state (0-4, as the Java core numbers them), {@code b} the percent or -1. */
    static final int EVENT_PROGRESS = 2;
    /** OSC 9 or OSC 777: {@code first} is the title (empty when the protocol has none), {@code second} the body. */
    static final int EVENT_NOTIFICATION = 3;
    /** OSC 99: {@code first} is everything after {@code 99;}. */
    static final int EVENT_KITTY_NOTIFICATION = 4;

    /** OSC 133 steps in {@link #EVENT_PROMPT}. */
    static final int SEMANTIC_PROMPT_START = 1;
    static final int SEMANTIC_INPUT_START = 2;
    static final int SEMANTIC_OUTPUT_START = 3;
    static final int SEMANTIC_COMMAND_END = 4;
    /** {@link #EVENT_PROMPT}'s exit code when the shell reported none. */
    static final int NO_EXIT_CODE = Integer.MIN_VALUE;

    /** Where native calls come back to during a write. No call may touch the terminal. */
    interface Sink {
        /** Bytes the terminal answers the program with (device reports and the like). */
        void onPtyReply(byte[] data);

        /** UTF-8 text a program put on the clipboard (OSC 52). */
        void onClipboardWrite(byte[] data);

        /** Something a program asked for (see EVENT_*); strings are UTF-8 or null. */
        void onEvent(int kind, int a, int b, byte[] first, byte[] second);
    }

    /**
     * OSC 22 pointer shapes by the library's number, as the CSS names the Java core keeps
     * ({@code TerminalEmulator.getPointerShape}).
     */
    private static final String[] MOUSE_SHAPES = {
        "default", "context-menu", "help", "pointer", "progress", "wait", "cell", "crosshair", "text",
        "vertical-text", "alias", "copy", "move", "no-drop", "not-allowed", "grab", "grabbing", "all-scroll",
        "col-resize", "row-resize", "n-resize", "e-resize", "s-resize", "w-resize", "ne-resize", "nw-resize",
        "se-resize", "sw-resize", "ew-resize", "ns-resize", "nesw-resize", "nwse-resize", "zoom-in", "zoom-out",
    };

    /** The CSS name of a library pointer shape, or null for one this build does not know. */
    static String mouseShapeName(int shape) {
        return shape >= 0 && shape < MOUSE_SHAPES.length ? MOUSE_SHAPES[shape] : null;
    }

    /**
     * System property naming a directory holding the library, for JVM tests on the host and for
     * {@link TerminalParseBench}, which runs outside the app.
     */
    static final String TEST_LIBRARY_DIR_PROPERTY = "termux.ghostty.libDir";

    private static final String LIBRARY = "termux-ghostty";
    private static final Object LOAD_LOCK = new Object();
    private static int sLoadState; // 0 not tried, 1 loaded, -1 failed
    private static Throwable sLoadError;

    private GhosttyNative() {}

    /** Load the library once. False, for good, when it is not in this build or will not load. */
    static boolean isAvailable() {
        synchronized (LOAD_LOCK) {
            if (sLoadState == 0) {
                try {
                    String dir = System.getProperty(TEST_LIBRARY_DIR_PROPERTY);
                    if (dir != null && !dir.isEmpty())
                        System.load(new File(dir, "lib" + LIBRARY + ".so").getAbsolutePath());
                    else
                        System.loadLibrary(LIBRARY);
                    sLoadState = 1;
                } catch (Throwable t) {
                    sLoadError = t;
                    sLoadState = -1;
                }
            }
            return sLoadState == 1;
        }
    }

    /** Why {@link #isAvailable()} said no, or null. */
    static Throwable loadError() {
        synchronized (LOAD_LOCK) {
            return sLoadError;
        }
    }

    /** Called from native code to decode a borrowed string; invalid UTF-8 becomes U+FFFD. */
    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static native long nativeCreate(int columns, int rows, int cellWidthPixels, int cellHeightPixels,
                                    int historyRows, Sink sink, byte[] xtVersion);

    static native void nativeFree(long handle);

    /** 259 colours, 0xRRGGBB: the palette, then foreground, background and cursor. */
    static native void nativeSetColors(long handle, int[] colors);

    /** Parse {@code length} bytes from {@code offset} in one go. */
    static native void nativeWrite(long handle, byte[] data, int offset, int length);

    /** The most rows of history the primary screen keeps; Ghostty prunes in whole pages. */
    static native void nativeSetHistoryLimit(long handle, int rows);

    static native void nativeResize(long handle, int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    static native void nativeReset(long handle);

    /**
     * Fill {@code state} (see STATE_*), and for each row changed since the last sync its cells in
     * {@code screen} at {@code y * columns * CELL_INTS} and its extras in {@code extra}, in row
     * order; {@code dirty[y]} is the row result (extras | {@link #ROW_WRAPPED} | {@link #ROW_PROMPT})
     * or -1 if unchanged. The changed rows' OSC 8 links go to {@code links} and {@code uris} (see
     * {@link #LINK_INTS}).
     */
    static native boolean nativeSync(long handle, int[] state, int[] screen, int[] dirty, int[] extra,
                                     int[] links, byte[] uris);

    /**
     * Read history row {@code y} (0 is the oldest kept), its links into {@code links} and {@code uris}
     * as row 0. Valid between a sync and the next write.
     */
    static native int nativeReadHistoryRow(long handle, int y, int[] cells, int[] extra, int[] links, byte[] uris);

    /**
     * The first history row from {@code from} towards {@code to} (both inclusive, 0 is the oldest
     * kept) that starts a shell prompt, or -1. Reads only the rows' prompt flags.
     */
    static native int nativeFindPromptRow(long handle, int from, int to);

    /** The current 259 colours as 0xAARRGGBB. */
    static native boolean nativeGetColors(long handle, int[] out);

    static native String nativeGetTitle(long handle);

    static native String nativeGetPwd(long handle);

    /** Slots of {@link #nativeMemoryUsage}, all for the primary screen; -1 when unavailable. */
    static final int MEM_RESIDENT_BYTES = 0;
    static final int MEM_VIRTUAL_BYTES = 1;
    static final int MEM_COMPRESSED_BYTES = 2;
    static final int MEM_PAGES = 3;
    static final int MEM_COMPRESSED_PAGES = 4;
    /** 1 when compressing history can free memory on this device, 0 when not. */
    static final int MEM_COMPRESSION_SUPPORTED = 5;

    /** The primary screen's memory (see MEM_*), or null. Looks at every page: for measuring only. */
    static native long[] nativeMemoryUsage(long handle);

    /** {@link #nativeCompress} results. */
    static final int COMPRESS_UNSUPPORTED = 0;
    static final int COMPRESS_PENDING = 1;
    static final int COMPRESS_COMPLETE = 2;

    /**
     * Compress history: one bounded step, or every eligible page when {@code full}. Returns a
     * COMPRESS_* result, or -1 on error. For measuring only.
     */
    static native int nativeCompress(long handle, boolean full);
}
