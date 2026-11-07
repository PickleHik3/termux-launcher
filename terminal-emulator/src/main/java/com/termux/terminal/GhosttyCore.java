package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * One session's libghostty-vt terminal, the experimental core behind
 * {@code adb shell setprop debug.tl.terminal_core ghostty}. {@link TerminalEmulator} hands it the
 * shell's output and copies the result back into its own buffers, so everything that reads the
 * emulator keeps working unchanged.
 *
 * <p>Main thread only, like the emulator. Replies and clipboard text that arrive during a write are
 * held here and handed on after it, so nothing reaches back into the terminal mid-write.
 */
final class GhosttyCore implements GhosttyNative.Sink, GhosttyMirror.HistorySource {

    /** The system property that picks the core for new sessions; "ghostty" picks this one. */
    static final String CORE_PROPERTY = "debug.tl.terminal_core";
    static final String CORE_GHOSTTY = "ghostty";

    /**
     * Bytes written between two syncs. The library's history only has to hold the rows one slice
     * can scroll off, since the Java transcript keeps the scrollback; a byte scrolls at most one row.
     */
    static final int WRITE_SLICE_BYTES = 1024;
    /** Rows of history the library keeps: a slice's worth with room to spare. */
    static final int HISTORY_ROWS = 2 * WRITE_SLICE_BYTES;
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

    /**
     * A terminal of this size with {@code colors} (the 259 0xAARRGGBB colours of
     * {@link TerminalColors}) as its defaults, or null when the library is missing or refuses.
     */
    static GhosttyCore create(int columns, int rows, int cellWidthPixels, int cellHeightPixels, int[] colors) {
        if (!GhosttyNative.isAvailable()) return null;
        GhosttyCore core = new GhosttyCore();
        long handle = GhosttyNative.nativeCreate(columns, rows, cellWidthPixels, cellHeightPixels, HISTORY_ROWS, core);
        if (handle == 0) return null;
        core.mHandle = handle;
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

    void write(byte[] data, int offset, int length) {
        if (mHandle != 0) GhosttyNative.nativeWrite(mHandle, data, offset, length);
    }

    void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mHandle == 0) return;
        GhosttyNative.nativeResize(mHandle, columns, rows, cellWidthPixels, cellHeightPixels);
        if (columns != mColumns || rows != mRows) allocate(columns, rows);
    }

    void reset() {
        if (mHandle != 0) GhosttyNative.nativeReset(mHandle);
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

    /** Resident, virtual and compressed bytes of the primary screen, or null. For measuring. */
    long[] memoryUsage() {
        return mHandle == 0 ? null : GhosttyNative.nativeMemoryUsage(mHandle);
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
