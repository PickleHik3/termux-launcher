package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which panes were opened through the local API (by an agent or a script) rather than by the
 * user, keyed by the shell's {@link com.termux.terminal.TerminalSession#mHandle}.
 *
 * <p>This is the boundary the pane API enforces: a caller may list and focus any pane, but it may
 * only type into, read from and close panes it opened itself. The user's own shells stay out of
 * reach of anything holding the token. Records are dropped lazily, whenever the caller prunes
 * against the shells that still exist, so a pane the user closed by hand does not linger here.
 */
public final class AgentPaneRegistry {

    /** What is remembered about an API-opened pane. */
    public static final class Record {
        @NonNull public final String handle;
        /** Free-form label the opener chose (its agent name, a task id); may be empty. */
        @NonNull public final String tag;
        /** The argv the pane was opened with; empty for a plain shell. */
        @NonNull public final List<String> command;
        public final long openedAtEpochMs;

        Record(@NonNull String handle, @NonNull String tag, @NonNull List<String> command,
               long openedAtEpochMs) {
            this.handle = handle;
            this.tag = tag;
            this.command = Collections.unmodifiableList(new ArrayList<>(command));
            this.openedAtEpochMs = openedAtEpochMs;
        }
    }

    private static final AgentPaneRegistry INSTANCE = new AgentPaneRegistry();

    private final Map<String, Record> records = new LinkedHashMap<>();

    private AgentPaneRegistry() {}

    @NonNull
    public static AgentPaneRegistry getInstance() {
        return INSTANCE;
    }

    /** Remember a pane the API just opened. */
    @NonNull
    public synchronized Record register(@NonNull String handle, @Nullable String tag,
                                        @Nullable List<String> command) {
        Record record = new Record(handle, tag == null ? "" : tag,
            command == null ? Collections.<String>emptyList() : command, System.currentTimeMillis());
        records.put(handle, record);
        return record;
    }

    @Nullable
    public synchronized Record get(@Nullable String handle) {
        return handle == null ? null : records.get(handle);
    }

    /** True when the API opened this pane, so the API may also drive and close it. */
    public synchronized boolean isOwned(@Nullable String handle) {
        return handle != null && records.containsKey(handle);
    }

    public synchronized void forget(@Nullable String handle) {
        if (handle != null) records.remove(handle);
    }

    /** Drop every record whose shell is no longer among {@code liveHandles}. */
    public synchronized void prune(@NonNull Collection<String> liveHandles) {
        Set<String> live = new HashSet<>(liveHandles);
        records.keySet().retainAll(live);
    }

    public synchronized int size() {
        return records.size();
    }

    /** Test hook: forget everything. */
    public synchronized void clear() {
        records.clear();
    }
}
