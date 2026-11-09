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
 * <p>Main thread only, like the emulator. Replies, clipboard text and events (prompt marks,
 * progress, notifications) that arrive during a write are held here and handed on after it, in the
 * order they happened, so nothing reaches back into the terminal mid-write.
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
    /** Events since the last delivery, oldest first; at most {@link #MAX_EVENTS}. */
    private final ArrayList<Event> mEvents = new ArrayList<>();
    /** OSC 8 link runs of the last sync or history row read (see {@link GhosttyNative#LINK_INTS}). */
    private final int[] mLinkRuns = new int[1 + GhosttyLinks.MAX_RUNS * GhosttyNative.LINK_INTS];
    private final byte[] mLinkUris = new byte[GhosttyLinks.URI_BYTES];

    /**
     * Events one turn of output holds before later ones are dropped: a turn is at most a few ms of
     * parsing, and a stream of progress reports collapses into its latest (see {@link #onEvent}).
     */
    static final int MAX_EVENTS = 1024;

    /** Something a program asked for during a write, and how many reply bytes came before it. */
    private static final class Event {
        int kind, a, b;
        byte[] first, second;
        int replyMark;
    }

    /** Where {@link #deliver} hands events; strings are decoded, null when absent. */
    interface EventTarget {
        void onGhosttyEvent(int kind, int a, int b, String first, String second);
    }

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

    /** Read the terminal's state and changed rows; false when there is nothing to read. */
    boolean sync() {
        return mHandle != 0 && GhosttyNative.nativeSync(mHandle, mState, mScreen, mDirty, mExtra, mLinkRuns, mLinkUris);
    }

    /** OSC 8 link runs the last sync or history row read wrote. */
    int[] linkRuns() {
        return mLinkRuns;
    }

    /** The UTF-8 URIs {@link #linkRuns()} point into. */
    byte[] linkUris() {
        return mLinkUris;
    }

    /**
     * The first history row from {@code from} towards {@code to} (inclusive, 0 the oldest kept)
     * that starts a shell prompt, or -1.
     */
    int findPromptRow(int from, int to) {
        return mHandle == 0 ? -1 : GhosttyNative.nativeFindPromptRow(mHandle, from, to);
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
        return mHandle == 0 ? -1 : GhosttyNative.nativeReadHistoryRow(mHandle, y, cells, extra, mLinkRuns, mLinkUris);
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

    @Override
    public void onEvent(int kind, int a, int b, byte[] first, byte[] second) {
        int mark = mReplies.size();
        if (kind == GhosttyNative.EVENT_PROGRESS && !mEvents.isEmpty() && progressReplacesEarlier(a, b)) {
            // A report that sets both state and value makes the one just before it moot.
            Event last = mEvents.get(mEvents.size() - 1);
            if (last.kind == GhosttyNative.EVENT_PROGRESS && last.replyMark == mark) {
                last.a = a;
                last.b = b;
                return;
            }
        }
        if (mEvents.size() >= MAX_EVENTS) return;
        Event event = new Event();
        event.kind = kind;
        event.a = a;
        event.b = b;
        event.first = first;
        event.second = second;
        event.replyMark = mark;
        mEvents.add(event);
    }

    /**
     * Whether a progress report leaves nothing of the reports before it ({@code
     * TerminalEmulator.setProgress}): clearing does, and so does a determinate state with a
     * percentage. A report without one keeps the earlier value, so it cannot replace it.
     */
    private static boolean progressReplacesEarlier(int state, int percent) {
        return state == TerminalEmulator.PROGRESS_STATE_NONE
            || (percent >= 0 && (state == TerminalEmulator.PROGRESS_STATE_NORMAL
            || state == TerminalEmulator.PROGRESS_STATE_ERROR || state == TerminalEmulator.PROGRESS_STATE_PAUSED));
    }

    /**
     * Send the replies held since the last call to the program and hand the events to
     * {@code target}, interleaved as they happened: a reply an event's handler writes (a kitty
     * notification query) follows the replies to sequences that came before it.
     */
    void deliver(EventTarget target, TerminalOutput output) {
        if (mEvents.isEmpty()) {
            flushReplies(output);
            return;
        }
        byte[] replies = mReplies.size() == 0 ? null : mReplies.toByteArray();
        mReplies.reset();
        int sent = 0;
        // Handlers run with the list detached, so a handler that reaches back here sees it empty.
        Event[] events = mEvents.toArray(new Event[0]);
        mEvents.clear();
        for (Event event : events) {
            if (replies != null && event.replyMark > sent) {
                output.write(replies, sent, event.replyMark - sent);
                sent = event.replyMark;
            }
            target.onGhosttyEvent(event.kind, event.a, event.b, decode(event.first), decode(event.second));
        }
        if (replies != null && sent < replies.length) output.write(replies, sent, replies.length - sent);
    }

    private static String decode(byte[] utf8) {
        return utf8 == null ? null : new String(utf8, StandardCharsets.UTF_8);
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
