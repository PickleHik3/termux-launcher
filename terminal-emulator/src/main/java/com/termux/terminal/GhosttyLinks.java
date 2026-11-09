package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Gives Ghostty rows the session's OSC 8 hyperlink ids. The shim reports a row's links as runs of
 * cells sharing a URI (see {@link GhosttyNative#LINK_INTS}); each URI is interned in the emulator's
 * {@link TerminalHyperlinks}, the pool the Java core fills, so the renderer, URL taps and
 * {@link TerminalEmulator#getHyperlinkUriAt} read both cores the same way.
 *
 * <p>The library keeps a link's URI but not its {@code id=} parameter, so two links to the same
 * URI share one id here, as two links without an id do on the Java core.
 *
 * <p>The pool stays bounded as on the Java core: when it is full, ids that no screen row and no
 * history row read so far still carries are given back. A history row read later interns its URI
 * again. Main thread only.
 */
final class GhosttyLinks {

    /** Link runs one sync or one history row read reports at most; more read as plain text. */
    static final int MAX_RUNS = 256;
    /** URI bytes one sync or one history row read reports at most. */
    static final int URI_BYTES = 16 * 1024;

    /** Marks the ids rows outside this class still carry. */
    interface UsedIds {
        void markUsed(boolean[] used);
    }

    private final TerminalHyperlinks mPool;
    private final UsedIds mUsedIds;
    /** Per-column ids of the row being read; reused. */
    private int[] mCells = new int[0];
    /** The URI bytes the last run resolved, and its id, valid until {@link #begin()}. */
    private int mCachedOffset = -1, mCachedLength, mCachedId;

    GhosttyLinks(TerminalHyperlinks pool, UsedIds usedIds) {
        mPool = pool;
        mUsedIds = usedIds;
    }

    /** The shim rewrote the runs: ids cached from the last report no longer apply. */
    void begin() {
        mCachedOffset = -1;
    }

    /**
     * The link id of each of the first {@code columns} cells of row {@code y}, from the runs the
     * shim wrote, or null when none of the row's cells is a link. The array is reused.
     */
    int[] cellIds(int[] runs, byte[] uris, int y, int columns) {
        int count = Math.min(runs[0], (runs.length - 1) / GhosttyNative.LINK_INTS);
        if (count <= 0) return null;
        boolean any = false;
        for (int i = 0; i < count; i++) {
            int base = 1 + i * GhosttyNative.LINK_INTS;
            if (runs[base + GhosttyNative.LINK_Y] != y) continue;
            if (!any) {
                if (mCells.length < columns) mCells = new int[columns];
                Arrays.fill(mCells, 0, columns, TerminalHyperlinks.NO_LINK);
                any = true;
            }
            int id = idFor(uris, runs[base + GhosttyNative.LINK_URI_OFFSET], runs[base + GhosttyNative.LINK_URI_LENGTH]);
            if (id == TerminalHyperlinks.NO_LINK) continue;
            int end = Math.min(columns, runs[base + GhosttyNative.LINK_X1]);
            for (int x = Math.max(0, runs[base + GhosttyNative.LINK_X0]); x < end; x++) mCells[x] = id;
        }
        return any ? mCells : null;
    }

    private int idFor(byte[] uris, int offset, int length) {
        if (offset == mCachedOffset && length == mCachedLength) return mCachedId;
        if (offset < 0 || length <= 0 || offset > uris.length - length) return TerminalHyperlinks.NO_LINK;
        String uri = new String(uris, offset, length, StandardCharsets.UTF_8);
        int id = intern(uri);
        mCachedOffset = offset;
        mCachedLength = length;
        mCachedId = id;
        return id;
    }

    /** Intern like the Java core's OSC 8 handler: no id parameter, and one sweep when the pool is full. */
    private int intern(String uri) {
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c < ' ' || c == 0x7f) return TerminalHyperlinks.NO_LINK;
        }
        int id = mPool.intern("", uri);
        if (id == TerminalHyperlinks.NO_LINK && mPool.isFull()) {
            boolean[] used = new boolean[TerminalHyperlinks.MAX_LINKS + 1];
            mUsedIds.markUsed(used);
            // The row being read has not been written yet; its earlier runs' ids are only here.
            for (int linkId : mCells)
                if (linkId > TerminalHyperlinks.NO_LINK && linkId < used.length) used[linkId] = true;
            if (mPool.reclaimUnused(used) > 0) id = mPool.intern("", uri);
            mCachedOffset = -1;
        }
        return id;
    }
}
