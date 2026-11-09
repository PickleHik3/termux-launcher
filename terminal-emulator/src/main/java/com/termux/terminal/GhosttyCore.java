package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * One session's libghostty-vt terminal, the experimental core behind
 * {@code adb shell setprop debug.tl.terminal_core ghostty}. {@link TerminalEmulator} hands it the
 * shell's output and copies the changed screen rows into its own buffer; history stays here and is
 * read through {@link GhosttyContent} when a reader asks.
 *
 * <p>Main thread only, like the emulator. Replies and clipboard text that arrive during a write are
 * held here and handed on after it, so nothing reaches back into the terminal mid-write.
 */
final class GhosttyCore implements GhosttyNative.Sink, GhosttyMirror.HistorySource, GhosttyImageCache.Source {

    /** The system property that picks the core for new sessions; "ghostty" picks this one. */
    static final String CORE_PROPERTY = "debug.tl.terminal_core";
    static final String CORE_GHOSTTY = "ghostty";

    /**
     * Bytes one Ghostty page takes at most, to bound how many rows a page holds. Its standard page
     * (215 x 215 cells plus style and grapheme tables) is under this; wider styles trade rows away.
     */
    static final int PAGE_BYTES_BOUND = 512 * 1024;
    /** Bytes a page spends per row: an 8-byte row header and 8 bytes per cell. */
    private static final int ROW_HEADER_BYTES = 8, CELL_BYTES = 8;
    /** Extra code points one history row can carry. */
    static final int HISTORY_EXTRA_CAPACITY = 1024;

    private long mHandle;
    private int mColumns, mRows;
    private final int[] mState = new int[GhosttyNative.STATE_COUNT];
    private int[] mScreen;
    private int[] mDirty;
    private final int[] mExtra = new int[GhosttyNative.EXTRA_CAPACITY];
    private int[] mHistoryCells;
    /** Separate from {@link #mExtra}, which still holds the screen rows' extras while history is read. */
    private final int[] mHistoryExtra = new int[HISTORY_EXTRA_CAPACITY];
    private final ByteArrayOutputStream mReplies = new ByteArrayOutputStream();
    private final ArrayList<String> mClipboard = new ArrayList<>();

    private GhosttyCore() {}

    /**
     * Appended to our XTVERSION name on this core. Programs that match "ghostty" in the answer
     * then pick kitty graphics, which this core draws; DA1 and TERM_PROGRAM stay ours.
     */
    static final String XTVERSION_SUFFIX = " (libghostty-vt 1.0)";

    /** The XTVERSION answer of a Ghostty pane: the Java core's name and {@link #XTVERSION_SUFFIX}. */
    static String xtVersionName() {
        return TerminalEmulator.xtVersionName() + XTVERSION_SUFFIX;
    }

    /** Whether new sessions should use this core, read from {@link #CORE_PROPERTY}. */
    static boolean isRequested(DebugSwitch.Reader reader) {
        return CORE_GHOSTTY.equals(reader.read(CORE_PROPERTY));
    }

    /** The most rows one Ghostty page of {@code columns} columns can hold. */
    static int pageRowsBound(int columns) {
        return PAGE_BYTES_BOUND / (ROW_HEADER_BYTES + CELL_BYTES * Math.max(1, columns)) + 1;
    }

    /**
     * Rows of history the library keeps for a Java transcript of {@code transcriptRows} rows.
     *
     * <p>Readers see at most the rows the Java transcript would hold ({@link GhosttyContent}), so
     * both cores show the same history. Ghostty prunes whole pages once history passes its limit,
     * so after pruning it holds more than the limit minus one page: one page of margin keeps the
     * readable history full instead of dropping by up to a page each time a page goes.
     */
    static int historyLimit(int transcriptRows, int columns) {
        return transcriptRows + pageRowsBound(columns);
    }

    /**
     * A terminal of this size with {@code colors} (the 259 0xAARRGGBB colours of
     * {@link TerminalColors}) as its defaults, keeping {@code historyRows} of history (see
     * {@link #historyLimit}), or null when the library is missing or refuses.
     */
    static GhosttyCore create(int columns, int rows, int cellWidthPixels, int cellHeightPixels, int[] colors,
                              int historyRows) {
        if (!GhosttyNative.isAvailable()) return null;
        GhosttyCore core = new GhosttyCore();
        long handle = GhosttyNative.nativeCreate(columns, rows, cellWidthPixels, cellHeightPixels, historyRows, core,
            xtVersionName().getBytes(StandardCharsets.UTF_8));
        if (handle == 0) return null;
        core.mHandle = handle;
        core.setUpKittyGraphics();
        core.allocate(columns, rows);
        if (colors != null && colors.length >= TextStyle.NUM_INDEXED_COLORS)
            GhosttyNative.nativeSetColors(handle, colors);
        return core;
    }

    private void allocate(int columns, int rows) {
        mColumns = columns;
        mRows = rows;
        mScreen = new int[columns * rows * GhosttyNative.CELL_INTS];
        mDirty = new int[rows];
        mHistoryCells = new int[columns * GhosttyNative.CELL_INTS];
    }

    boolean isAlive() {
        return mHandle != 0;
    }

    /** Free the native terminal. The Java buffers keep the last screen; later calls do nothing. */
    void release() {
        long handle = mHandle;
        mHandle = 0;
        if (handle != 0) GhosttyNative.nativeFree(handle);
    }

    /** Parse the bytes. The Java buffers see the result only after the next {@link #sync()}. */
    void write(byte[] data, int offset, int length) {
        if (mHandle != 0) GhosttyNative.nativeWrite(mHandle, data, offset, length);
    }

    void setHistoryLimit(int rows) {
        if (mHandle != 0) GhosttyNative.nativeSetHistoryLimit(mHandle, rows);
    }

    void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mHandle == 0) return;
        GhosttyNative.nativeResize(mHandle, columns, rows, cellWidthPixels, cellHeightPixels);
        if (columns != mColumns || rows != mRows) allocate(columns, rows);
    }

    void reset() {
        if (mHandle != 0) GhosttyNative.nativeReset(mHandle);
    }

    /** End a render hold (mode 2026) the program has not ended; the next sync takes a fresh frame. */
    void endHold() {
        if (mHandle != 0) GhosttyNative.nativeEndHold(mHandle);
    }

    /** Read the terminal's state and changed rows; false when there is nothing to read. */
    boolean sync() {
        return mHandle != 0 && GhosttyNative.nativeSync(mHandle, mState, mScreen, mDirty, mExtra);
    }

    int[] state() {
        return mState;
    }

    int[] screen() {
        return mScreen;
    }

    int[] dirty() {
        return mDirty;
    }

    int[] extra() {
        return mExtra;
    }

    int[] historyCells() {
        return mHistoryCells;
    }

    int[] historyExtra() {
        return mHistoryExtra;
    }

    int columns() {
        return mColumns;
    }

    int rows() {
        return mRows;
    }

    @Override
    public int readHistoryRow(int y, int[] cells, int[] extra) {
        return mHandle == 0 ? -1 : GhosttyNative.nativeReadHistoryRow(mHandle, y, cells, extra);
    }

    String title() {
        return mHandle == 0 ? null : GhosttyNative.nativeGetTitle(mHandle);
    }

    String workingDirectory() {
        return mHandle == 0 ? null : GhosttyNative.nativeGetPwd(mHandle);
    }

    /** The current colours into {@code out} (259 entries); false when unavailable. */
    boolean colors(int[] out) {
        return mHandle != 0 && GhosttyNative.nativeGetColors(mHandle, out);
    }

    /** The primary screen's memory (see {@link GhosttyNative#MEM_RESIDENT_BYTES}), or null. For measuring. */
    long[] memoryUsage() {
        return mHandle == 0 ? null : GhosttyNative.nativeMemoryUsage(mHandle);
    }

    /** Compress history (see {@link GhosttyNative#nativeCompress}); -1 when released. For measuring. */
    int compress(boolean full) {
        return mHandle == 0 ? -1 : GhosttyNative.nativeCompress(mHandle, full);
    }

    @Override
    public void onPtyReply(byte[] data) {
        if (data != null) mReplies.write(data, 0, data.length);
    }

    @Override
    public void onClipboardWrite(byte[] data) {
        if (data != null) mClipboard.add(new String(data, StandardCharsets.UTF_8));
    }

    /** Send the replies held since the last call to the program. */
    void flushReplies(TerminalOutput output) {
        if (mReplies.size() == 0) return;
        byte[] bytes = mReplies.toByteArray();
        mReplies.reset();
        output.write(bytes, 0, bytes.length);
    }

    /** Hand clipboard text held since the last call to the session. */
    void flushClipboard(TerminalOutput output) {
        if (mClipboard.isEmpty()) return;
        for (int i = 0; i < mClipboard.size(); i++) output.onCopyTextToClipboard(mClipboard.get(i));
        mClipboard.clear();
    }

    // Kitty graphics. Ghostty stores, decodes and places images; GhosttyImages keeps the bitmaps.

    /** Answers kitty queries with the Java core's code, into {@link #mReplies}; made on first use. */
    private KittyGraphicsProtocol mQueryAnswerer;

    /** The replies a query answer writes, kept in order with the replies Ghostty writes. */
    private final TerminalOutput mQueryReplies = new TerminalOutput() {
        @Override
        public void write(byte[] data, int offset, int count) {
            mReplies.write(data, offset, count);
        }

        @Override
        public void titleChanged(String oldTitle, String newTitle) {
        }

        @Override
        public void onCopyTextToClipboard(String text) {
        }

        @Override
        public void onPasteTextFromClipboard() {
        }

        @Override
        public void onBell() {
        }

        @Override
        public void onColorsChanged() {
        }
    };

    /**
     * Turn images on with the Java core's storage limit, so a pane keeps the same image bytes on
     * either core, and its file media rules: file and temp file, no shared memory.
     */
    private void setUpKittyGraphics() {
        byte[] tempDir = protocolTempDir();
        GhosttyNative.nativeKittySetup(mHandle, KittyImageStore.MAX_STORED_BYTES, tempDir);
    }

    /**
     * The directory {@code t=t} files may come from: the shell's {@code $TMPDIR}, else the
     * prefix's tmp beside the app's cache directory (where the shell's {@code $TMPDIR} points), as
     * a canonical path; null when neither exists.
     */
    static byte[] protocolTempDir() {
        try {
            String tmp = System.getenv("TMPDIR");
            java.io.File dir = tmp != null && !tmp.isEmpty() ? new java.io.File(tmp) : null;
            if (dir == null || !dir.isDirectory()) {
                String cache = System.getProperty("java.io.tmpdir");
                java.io.File parent = cache == null ? null : new java.io.File(cache).getParentFile();
                dir = parent == null ? null : new java.io.File(parent, "files/usr/tmp");
            }
            if (dir == null || !dir.isDirectory()) return null;
            return dir.getCanonicalPath().getBytes(StandardCharsets.UTF_8);
        } catch (java.io.IOException | SecurityException e) {
            return null;
        }
    }

    @Override
    public void onKittyQuery(byte[] apc) {
        if (apc == null) return;
        if (mQueryAnswerer == null) mQueryAnswerer = new KittyGraphicsProtocol(null, mQueryReplies);
        mQueryAnswerer.accept(new String(apc, StandardCharsets.ISO_8859_1));
    }

    /** See {@link GhosttyNative#nativeKittyFrame}. */
    boolean kittyFrame(long nowMillis, boolean tick, long[] out) {
        return mHandle != 0 && GhosttyNative.nativeKittyFrame(mHandle, nowMillis, tick, out);
    }

    int kittyPlacements(int[] out) {
        return mHandle == 0 ? 0 : GhosttyNative.nativeKittyPlacements(mHandle, out);
    }

    int kittyVirtualPlacements(int[] out) {
        return mHandle == 0 ? 0 : GhosttyNative.nativeKittyVirtualPlacements(mHandle, out);
    }

    @Override
    public boolean imageInfo(int imageId, long[] out) {
        return mHandle != 0 && GhosttyNative.nativeKittyImageInfo(mHandle, imageId, out);
    }

    @Override
    public int copyImageRows(int imageId, long generation, int firstRow, int rows, int[] argb) {
        return mHandle == 0 ? -1 : GhosttyNative.nativeKittyImageCopy(mHandle, imageId, generation, firstRow, rows, argb);
    }

    @Override
    public long imageGeneration(int imageId) {
        return mHandle == 0 ? 0 : GhosttyNative.nativeKittyImageGeneration(mHandle, imageId);
    }
}
