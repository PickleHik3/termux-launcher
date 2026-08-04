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

/** Ordered, PID-keyed lifecycle model for commands outside the focused pane. */
public final class BackgroundProcessModel {

    public static final class Snapshot {
        public final int shellPid;
        public final int foregroundPid;
        @Nullable public final String processName;
        @Nullable public final String nativeTitle;
        public final boolean commandRunning;

        public Snapshot(int shellPid, int foregroundPid, @Nullable String processName,
                        @Nullable String nativeTitle, boolean commandRunning) {
            this.shellPid = shellPid;
            this.foregroundPid = foregroundPid;
            this.processName = processName;
            this.nativeTitle = nativeTitle;
            this.commandRunning = commandRunning;
        }
    }

    public static final class Entry {
        public final int shellPid;
        public final int foregroundPid;
        public final long startedAtMs;
        @Nullable public String processName;
        @Nullable public String nativeTitle;

        private Entry(@NonNull Snapshot snapshot, long startedAtMs) {
            shellPid = snapshot.shellPid;
            foregroundPid = snapshot.foregroundPid;
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

    @NonNull
    public List<Entry> visibleEntries(int focusedShellPid) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : mEntries.values()) {
            if (entry.shellPid != focusedShellPid) result.add(entry);
        }
        Collections.sort(result, Comparator.comparingLong(value -> value.startedAtMs));
        return result;
    }

    public void clearShell(int shellPid) {
        mEntries.entrySet().removeIf(entry -> entry.getValue().shellPid == shellPid);
    }

    private static long key(int shellPid, int foregroundPid) {
        return ((long) shellPid << 32) ^ (foregroundPid & 0xffffffffL);
    }
}
