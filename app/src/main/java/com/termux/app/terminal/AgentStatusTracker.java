package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One {@link AgentStatus} per pane, keyed by the pane's shell pid. Two sources write into it and
 * the tracker is what keeps them in order:
 *
 * <ul>
 *   <li>{@link #report} — the agent's own hook, through {@code launcherctl agent}. Authoritative,
 *       and it holds until the next report, until the agent clears it, or until the agent process
 *       leaves the foreground.</li>
 *   <li>{@link #observe} — the screen rules, for agents that report nothing. Ignored for a pane a
 *       hook is speaking for.</li>
 * </ul>
 *
 * <p>Reading the screen is the expensive half, so it is gated twice: the caller's supplier is only
 * asked after {@link #SCREEN_INTERVAL_MS}, and the regexes only run when the text that came back
 * differs from last time, compared by length and hash rather than kept around. A pane whose
 * foreground is not a known agent costs one map lookup and nothing else.
 *
 * <p>Not thread-safe: everything here runs on the main thread, like the window bar it feeds.
 */
public final class AgentStatusTracker {

    /** Floor between two screen readings of the same pane. */
    public static final long SCREEN_INTERVAL_MS = 750L;

    /** What the caller hands over when the tracker decides it wants to look at a pane. */
    public interface ScreenSource {
        /** The last {@link AgentScreenRules#TAIL_ROWS} rows of the pane, or null when unreadable. */
        @Nullable String screenTail();
    }

    private static final class Entry {
        @Nullable AgentStatus status;
        /** Set while a hook is speaking for this pane; screen readings are ignored until cleared. */
        boolean hooked;
        long lastScreenAtMs;
        int lastScreenHash;
        int lastScreenLength = -1;
    }

    private final Map<Integer, Entry> mEntries = new HashMap<>();

    /** The pane's status, or null when nothing there is an agent. */
    @Nullable
    public AgentStatus get(int pid) {
        Entry entry = mEntries.get(pid);
        return entry == null ? null : entry.status;
    }

    /** Whether a hook is currently speaking for this pane. */
    public boolean isHooked(int pid) {
        Entry entry = mEntries.get(pid);
        return entry != null && entry.hooked;
    }

    /**
     * An agent's own report. {@code state} null clears the pane, which is what {@code SessionEnd}
     * and {@code launcherctl agent clear} send. Returns true when the pane's shown state changed.
     */
    public boolean report(int pid, @Nullable String agent, @Nullable AgentStatus.State state,
                          long nowMs) {
        if (pid < 1) return false;
        if (state == null) {
            Entry entry = mEntries.remove(pid);
            return entry != null && entry.status != null;
        }
        Entry entry = entryFor(pid);
        AgentStatus previous = entry.status;
        String name = agent == null || agent.trim().isEmpty()
            ? (previous == null ? AgentStatus.AGENT_CLAUDE : previous.agent) : agent.trim();
        entry.hooked = true;
        entry.status = new AgentStatus(name, state, AgentStatus.Source.HOOK, nowMs);
        return !entry.status.equals(previous);
    }

    /**
     * The screen-rule pass for one pane. {@code agent} null — the foreground is not an agent — drops
     * whatever the pane had, hook report included: the agent it was speaking for is gone.
     *
     * <p>{@code screenSource} is only asked when a reading is actually due, so a caller can build
     * the screen text lazily. Returns true when the pane's shown state changed.
     */
    public boolean observe(int pid, @Nullable String agent, boolean cpuWorking,
                           @NonNull ScreenSource screenSource, long nowMs) {
        if (pid < 1) return false;
        if (agent == null) {
            Entry gone = mEntries.remove(pid);
            return gone != null && gone.status != null;
        }
        Entry entry = entryFor(pid);
        // A hook report stands until the agent replaces or clears it. Nothing is read while it does.
        if (entry.hooked) {
            if (entry.status != null && !entry.status.agent.equals(agent)) {
                // A different agent took the foreground; the old report no longer describes it.
                entry.hooked = false;
            } else {
                return false;
            }
        }
        if (entry.status != null && nowMs - entry.lastScreenAtMs < SCREEN_INTERVAL_MS) return false;
        entry.lastScreenAtMs = nowMs;
        AgentStatus.State state;
        if (AgentStatus.hasScreenRules(agent)) {
            String screen = screenSource.screenTail();
            if (screen == null) return false;
            int length = screen.length();
            int hash = screen.hashCode();
            if (length == entry.lastScreenLength && hash == entry.lastScreenHash
                && entry.status != null) return false;
            entry.lastScreenLength = length;
            entry.lastScreenHash = hash;
            state = AgentScreenRules.classify(agent, screen, cpuWorking);
            // No rule matched: keep the last reading rather than inventing one.
            if (state == null) state = entry.status == null ? AgentScreenRules.classifyGeneric(cpuWorking)
                : entry.status.state;
        } else {
            state = AgentScreenRules.classifyGeneric(cpuWorking);
        }
        AgentStatus previous = entry.status;
        entry.status = new AgentStatus(agent, state, AgentStatus.Source.SCREEN, nowMs);
        return !entry.status.equals(previous);
    }

    /** Forget panes whose shells are gone, so a reused pid never inherits a dead agent's state. */
    public void retain(@NonNull Set<Integer> livePids) {
        mEntries.keySet().retainAll(livePids);
    }

    public void clear() {
        mEntries.clear();
    }

    @NonNull
    private Entry entryFor(int pid) {
        Entry entry = mEntries.get(pid);
        if (entry == null) {
            entry = new Entry();
            mEntries.put(pid, entry);
        }
        return entry;
    }

    /** The wire form of a state, as the hook route and the shell wrapper spell it. */
    @Nullable
    public static AgentStatus.State parseState(@Nullable String value) {
        if (value == null) return null;
        switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "working": return AgentStatus.State.WORKING;
            case "blocked": return AgentStatus.State.BLOCKED;
            case "idle": return AgentStatus.State.IDLE;
            default: return null;
        }
    }
}
