package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ordered, PID-keyed lifecycle model for commands running outside the focused session. */
public final class BackgroundProcessModel {

    public static final class Snapshot {
        /** Which tmux-style session owns the shell. Rows are only raised for other sessions. */
        public final long sessionId;
        public final int shellPid;
        public final int foregroundPid;
        @Nullable public final String processName;
        @Nullable public final String nativeTitle;
        public final boolean commandRunning;

        public Snapshot(long sessionId, int shellPid, int foregroundPid,
                        @Nullable String processName, @Nullable String nativeTitle,
                        boolean commandRunning) {
            this.sessionId = sessionId;
            this.shellPid = shellPid;
            this.foregroundPid = foregroundPid;
            this.processName = processName;
            this.nativeTitle = nativeTitle;
            this.commandRunning = commandRunning;
        }
    }

    public static final class Entry {
        public final long sessionId;
        public final int shellPid;
        public final int foregroundPid;
        public final long startedAtMs;
        /** Stable identity of the shell/foreground pair, so the view can reuse a row across binds. */
        public final long key;
        @Nullable public String processName;
        @Nullable public String nativeTitle;

        private Entry(@NonNull Snapshot snapshot, long startedAtMs) {
            sessionId = snapshot.sessionId;
            shellPid = snapshot.shellPid;
            foregroundPid = snapshot.foregroundPid;
            key = key(snapshot.shellPid, snapshot.foregroundPid);
            this.startedAtMs = startedAtMs;
            update(snapshot);
        }

        private void update(@NonNull Snapshot snapshot) {
            processName = snapshot.processName;
            nativeTitle = snapshot.nativeTitle;
        }

        @NonNull public String displayText() {
            // OSC/native title is deliberately not normalized: Codex attention text must survive.
            if (nativeTitle != null && !nativeTitle.isEmpty()) return nativeTitle;
            return processName == null ? "process" : processName;
        }
    }

    /** Linked order breaks equal-start-time ties by the resolver's pane traversal order. */
    private final Map<Long, Entry> mEntries = new LinkedHashMap<>();

    /** Foreground state is authoritative: absent/idle PID pairs are removed immediately. */
    public void update(@NonNull List<Snapshot> snapshots, long nowMs) {
        Set<Long> live = new HashSet<>();
        for (Snapshot snapshot : snapshots) {
            if (!snapshot.commandRunning || snapshot.shellPid < 1 || snapshot.foregroundPid < 1)
                continue;
            long key = key(snapshot.shellPid, snapshot.foregroundPid);
            live.add(key);
            Entry entry = mEntries.get(key);
            if (entry == null) mEntries.put(key, new Entry(snapshot, nowMs));
            else entry.update(snapshot);
        }
        mEntries.keySet().retainAll(live);
    }

    /**
     * How long a foreground process has to survive before it earns a row.
     *
     * <p>Entering a session runs the rc files of every shell it starts, and anything they spawn — a
     * motd, a fetch tool, a subshell — is a non-idle foreground with no shell-integration mark yet,
     * so it reads exactly like a background command. One resolver poll interval is the shortest delay
     * that means "still there next time we looked" rather than "seen once".
     */
    public static final long SHOW_DELAY_MS = 1500L;

    /**
     * Rows for commands running outside {@code focusedSessionId}.
     *
     * <p>Scoped to the session, not the pane: switching window or pane inside a session is navigation
     * the user just performed, and the window pill's own rim already says which of its windows is
     * working. Only leaving the session entirely takes those pills off screen, and that is what this
     * corner exists to replace.
     */
    @NonNull
    public List<Entry> visibleEntries(long focusedSessionId, long nowMs) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : mEntries.values()) {
            if (entry.sessionId == focusedSessionId) continue;
            if (nowMs - entry.startedAtMs < SHOW_DELAY_MS) continue;
            result.add(entry);
        }
        Collections.sort(result, Comparator.comparingLong(value -> value.startedAtMs));
        return result;
    }

    /**
     * Milliseconds until the next entry held back by {@link #SHOW_DELAY_MS} becomes visible, or -1
     * when none is waiting. Callers re-sync then: a quiet command produces no further resolver change
     * and no title output, so nothing else would come along to reveal it.
     */
    public long msUntilNextVisible(long focusedSessionId, long nowMs) {
        long soonest = -1L;
        for (Entry entry : mEntries.values()) {
            if (entry.sessionId == focusedSessionId) continue;
            long remaining = SHOW_DELAY_MS - (nowMs - entry.startedAtMs);
            if (remaining <= 0L) continue;
            if (soonest < 0L || remaining < soonest) soonest = remaining;
        }
        return soonest;
    }

    private static long key(int shellPid, int foregroundPid) {
        return ((long) shellPid << 32) ^ (foregroundPid & 0xffffffffL);
    }
}
