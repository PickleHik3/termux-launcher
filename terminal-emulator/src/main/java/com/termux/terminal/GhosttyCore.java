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
final class GhosttyCore implements GhosttyNative.Sink, GhosttyMirror.HistorySource {

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
            TerminalEmulator.xtVersionName().getBytes(StandardCharsets.UTF_8));
        if (handle == 0) return null;
        core.mHandle = handle;
        core.allocate(columns, rows);
        core.mHistoryRowLimit = historyRows;
        core.applyHistoryByteLimit();
        GhosttyMemory.register(core);
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
        if (handle == 0) return;
        GhosttyNative.nativeFree(handle);
        GhosttyCrashGuard.onCoreReleased(GhosttyMemory.unregister(this));
    }

    /** Parse the bytes. The Java buffers see the result only after the next {@link #sync()}. */
    void write(byte[] data, int offset, int length) {
        if (mHandle == 0) return;
        GhosttyCrashGuard.beforeWrite();
        GhosttyNative.nativeWrite(mHandle, data, offset, length);
    }

    void setHistoryLimit(int rows) {
        if (mHandle == 0) return;
        GhosttyNative.nativeSetHistoryLimit(mHandle, rows);
        mHistoryRowLimit = rows;
        applyHistoryByteLimit();
    }

    void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mHandle == 0) return;
        GhosttyNative.nativeResize(mHandle, columns, rows, cellWidthPixels, cellHeightPixels);
        if (columns != mColumns || rows != mRows) allocate(columns, rows);
        applyHistoryByteLimit();
    }

    void reset() {
        if (mHandle != 0) GhosttyNative.nativeReset(mHandle);
    }

    /**
     * Read the terminal's state and changed rows; false once released. A live terminal that cannot
     * be read is a Ghostty error and throws, so the emulator falls back to the Java core.
     */
    boolean sync() {
        if (mHandle == 0) return false;
        RuntimeException injected = mFailNextSync;
        if (injected != null) {
            mFailNextSync = null;
            throw injected;
        }
        if (!GhosttyNative.nativeSync(mHandle, mState, mScreen, mDirty, mExtra))
            throw new IllegalStateException("ghostty: the render state could not be read");
        return true;
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

    // Fail-safes and memory (wave 3 slice 6).

    /**
     * Bytes of history per row Ghostty may keep, as {@link #pageRowsBound} counts them; with the
     * row limit (transcript plus one page) this caps a styled flood at what plain rows would take.
     */
    static long historyByteLimit(int historyRows, int columns) {
        return (long) Math.max(1, historyRows) * (ROW_HEADER_BYTES + (long) CELL_BYTES * Math.max(1, columns));
    }

    /** The row limit last given, which the byte limit follows. */
    private int mHistoryRowLimit;
    /** The compression activity token when the last idle pass completed; -1 for none yet. */
    private long mCompressedActivity = -1;
    /** Set when the library says compressing cannot free memory here; nothing is tried again. */
    private boolean mCompressionUnsupported;
    /** For tests: the next {@link #sync()} throws this, as a Ghostty error mid-stream would. */
    private RuntimeException mFailNextSync;

    private void applyHistoryByteLimit() {
        if (mHandle != 0 && mHistoryRowLimit > 0)
            GhosttyNative.nativeSetHistoryByteLimit(mHandle, historyByteLimit(mHistoryRowLimit, mColumns));
    }

    /**
     * The screen and up to {@code historyRows} history rows as VT for a Java emulator to replay
     * (see {@link GhosttyNative#nativeFormatReplay}), or null when the formatter fails.
     */
    byte[] formatReplay(int historyRows) {
        if (mHandle == 0 || mFormatterBrokenForTests) return null;
        return GhosttyNative.nativeFormatReplay(mHandle, historyRows);
    }

    /**
     * One incremental compression step for an idle pane. A pass that completed is not repeated
     * until the library's activity token moves, so an idle edge after typing costs one native call.
     */
    int compressIdleStep() {
        if (mHandle == 0) return -1;
        if (mCompressionUnsupported) return GhosttyNative.COMPRESS_UNSUPPORTED;
        long activity = GhosttyNative.nativeCompressionActivity(mHandle);
        if (activity >= 0 && activity == mCompressedActivity) return GhosttyNative.COMPRESS_COMPLETE;
        int result = GhosttyNative.nativeCompress(mHandle, false);
        if (result == GhosttyNative.COMPRESS_COMPLETE) mCompressedActivity = GhosttyNative.nativeCompressionActivity(mHandle);
        else if (result == GhosttyNative.COMPRESS_UNSUPPORTED) mCompressionUnsupported = true;
        return result;
    }

    /**
     * Compress every eligible page now, for memory pressure. True when a pass ran; compression
     * never changes what the terminal holds, so a failed pass leaves the pane as it was.
     */
    boolean compressFully() {
        if (mHandle == 0 || mCompressionUnsupported) return false;
        int result = GhosttyNative.nativeCompress(mHandle, true);
        if (result == GhosttyNative.COMPRESS_UNSUPPORTED) mCompressionUnsupported = true;
        if (result == GhosttyNative.COMPRESS_COMPLETE) mCompressedActivity = GhosttyNative.nativeCompressionActivity(mHandle);
        return result == GhosttyNative.COMPRESS_COMPLETE || result == GhosttyNative.COMPRESS_PENDING;
    }

    /** For tests: make the next sync fail as a Ghostty error would. */
    void failNextSyncForTests(RuntimeException failure) {
        mFailNextSync = failure;
    }

    /** For tests: the formatter fails too. */
    private boolean mFormatterBrokenForTests;

    void breakFormatterForTests() {
        mFormatterBrokenForTests = true;
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
}
