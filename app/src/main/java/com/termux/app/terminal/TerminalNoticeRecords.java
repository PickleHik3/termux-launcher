package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.ProgramStatus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What each window currently has up in the shade: one record per window, so a later notice updates
 * it in place and a cancel knows what to take down. Also remembers which windows have already rung
 * the bell since their pane was last seen. Main thread only. Both maps are bounded by the number of
 * windows, and an entry goes when its pane does.
 */
public final class TerminalNoticeRecords {

    private static final TerminalNoticeRecords SHARED = new TerminalNoticeRecords();

    /** The one the app uses; tests build their own. */
    @NonNull
    public static TerminalNoticeRecords shared() {
        return SHARED;
    }

    /** One window's notice. The pane is held weakly so a closed session is not kept alive. */
    public static final class Record {
        public final TerminalNoticeKind kind;
        private final WeakReference<Object> mPane;
        public final String windowKey;
        /** The OSC 99 id, empty when the program gave none. */
        public final String messageId;
        /** The OSC 7501 id this record reports on, else null. */
        @Nullable public final String statusId;
        /** The state that record was in when posted, else null. */
        @Nullable public final ProgramStatus.State statusState;
        /** Increases on every put, so a late name lookup can tell it has been superseded. */
        public final int generation;

        Record(TerminalNoticeKind kind, Object pane, String windowKey, String messageId,
               @Nullable String statusId, @Nullable ProgramStatus.State statusState, int generation) {
            this.kind = kind;
            this.mPane = new WeakReference<>(pane);
            this.windowKey = windowKey;
            this.messageId = messageId;
            this.statusId = statusId;
            this.statusState = statusState;
            this.generation = generation;
        }

        /** The pane that raised it, null once collected. */
        @Nullable
        public Object pane() {
            return mPane.get();
        }
    }

    private final Map<String, Record> mRecords = new HashMap<>();
    private final Map<String, WeakReference<Object>> mBells = new HashMap<>();
    private int mGeneration;

    public TerminalNoticeRecords() {
    }

    @Nullable
    public Record get(@NonNull String windowKey) {
        return mRecords.get(windowKey);
    }

    /** Replaces the window's record and returns the new one. */
    @NonNull
    public Record put(@NonNull TerminalNoticeKind kind, @NonNull Object pane, @NonNull String windowKey,
                      @NonNull String messageId, @Nullable String statusId,
                      @Nullable ProgramStatus.State statusState) {
        Record record = new Record(kind, pane, windowKey, messageId, statusId, statusState, ++mGeneration);
        mRecords.put(windowKey, record);
        return record;
    }

    @Nullable
    public Record remove(@NonNull String windowKey) {
        return mRecords.remove(windowKey);
    }

    /** Drops the records raised by {@code pane}, and any whose pane is gone; returns them. */
    @NonNull
    public List<Record> removeForPane(@Nullable Object pane) {
        List<Record> removed = new ArrayList<>();
        for (Iterator<Record> it = mRecords.values().iterator(); it.hasNext(); ) {
            Record record = it.next();
            Object owner = record.pane();
            if (owner == null || owner == pane) {
                removed.add(record);
                it.remove();
            }
        }
        return removed;
    }

    public int size() {
        return mRecords.size();
    }

    /** Whether the window's bell has already notified since its pane was last seen. */
    public boolean bellSpent(@NonNull String windowKey) {
        WeakReference<Object> mark = mBells.get(windowKey);
        if (mark == null) return false;
        if (mark.get() == null) {
            mBells.remove(windowKey);
            return false;
        }
        return true;
    }

    public void markBellSpent(@NonNull String windowKey, @NonNull Object pane) {
        mBells.put(windowKey, new WeakReference<>(pane));
    }

    /** The pane was seen or closed: its bell may notify again. Also drops marks of collected panes. */
    /** The window was seen: its next bell may notify again. */
    public void clearBell(@NonNull String windowKey) {
        mBells.remove(windowKey);
    }

    public void clearBellFor(@Nullable Object pane) {
        for (Iterator<WeakReference<Object>> it = mBells.values().iterator(); it.hasNext(); ) {
            Object owner = it.next().get();
            if (owner == null || owner == pane) it.remove();
        }
    }
}
