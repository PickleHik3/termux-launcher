package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * What an AI coding agent running in one pane is doing right now: working on a turn, blocked
 * waiting for the user to answer something, or idle at its prompt with nothing to do.
 *
 * <p>Two sources feed it, and {@link Source#HOOK} always wins while it is fresh: the agent itself
 * reporting through {@code launcherctl agent <state>}, or — for agents that report nothing — the
 * screen rules in {@link AgentScreenRules} matched against the bottom of the pane. Immutable, so a
 * status can be held and compared without copying.
 */
public final class AgentStatus {

    /** Ordered by precedence: a blocked pane outranks a working one outranks an idle one. */
    public enum State { IDLE, WORKING, BLOCKED }

    /** Where the reading came from. A hook report is authoritative; a screen reading is a guess. */
    public enum Source { HOOK, SCREEN }

    public static final String AGENT_CLAUDE = "claude";
    public static final String AGENT_CODEX = "codex";

    /**
     * The agent binaries whose panes carry a status. Anything here but Claude and Codex only ever
     * gets the generic CPU fallback, which can never say blocked.
     */
    private static final String[] KNOWN_AGENTS = {
        AGENT_CLAUDE, AGENT_CODEX, "opencode", "gemini", "aider", "cursor-agent", "copilot",
    };

    /**
     * Interpreters that launch an agent from a script, so the process name is the runtime and the
     * agent is somewhere in the argv.
     */
    private static final String[] INTERPRETERS = {
        "node", "nodejs", "bun", "deno", "python", "python3", "npx", "uvx",
    };

    /** The agent this pane is running, lowercase and never empty. */
    @NonNull public final String agent;
    @NonNull public final State state;
    @NonNull public final Source source;
    /** {@code SystemClock.uptimeMillis()} when this reading was taken. */
    public final long atUptimeMs;

    public AgentStatus(@NonNull String agent, @NonNull State state, @NonNull Source source,
                       long atUptimeMs) {
        this.agent = agent;
        this.state = state;
        this.source = source;
        this.atUptimeMs = atUptimeMs;
    }

    /**
     * The agent a pane's foreground process is, or null when it is not one. {@code processName} is
     * the basename procfs reported; {@code command} its full argv, which is what identifies an
     * agent launched through node, bun or npx, where the process name is only the runtime.
     */
    @Nullable
    public static String kindFor(@Nullable String processName, @Nullable List<String> command) {
        String process = normalise(processName);
        String direct = matchAgent(process);
        if (direct != null) return direct;
        if (!isInterpreter(process)) return null;
        if (command == null) return null;
        // Only the script itself — the first argument that is not a flag. Scanning the whole argv
        // instead would read "node server.js --agent claude" as an agent pane.
        for (int i = 1; i < command.size(); i++) {
            String argument = command.get(i);
            if (argument == null || argument.isEmpty()) continue;
            if (argument.startsWith("-")) continue;
            return matchAgent(normalise(argument));
        }
        return null;
    }

    /** Whether {@code agent} has screen rules of its own, rather than only the CPU fallback. */
    public static boolean hasScreenRules(@Nullable String agent) {
        return AGENT_CLAUDE.equals(agent) || AGENT_CODEX.equals(agent);
    }

    /**
     * The state a window or session shows for the panes under it: blocked outranks working outranks
     * idle, and null — no agent anywhere — outranked by all three.
     */
    @Nullable
    public static State rollUp(@Nullable State left, @Nullable State right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    /** The rolled-up state of a group of readings, or null when none of them has an agent. */
    @Nullable
    public static State rollUp(@Nullable Iterable<AgentStatus> statuses) {
        if (statuses == null) return null;
        State folded = null;
        for (AgentStatus status : statuses) {
            if (status != null) folded = rollUp(folded, status.state);
        }
        return folded;
    }

    @NonNull
    public AgentStatus withState(@NonNull State state, @NonNull Source source, long atUptimeMs) {
        return state == this.state && source == this.source ? this
            : new AgentStatus(agent, state, source, atUptimeMs);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof AgentStatus)) return false;
        AgentStatus that = (AgentStatus) other;
        return state == that.state && source == that.source && agent.equals(that.agent);
    }

    @Override
    public int hashCode() {
        return (agent.hashCode() * 31 + state.hashCode()) * 31 + source.hashCode();
    }

    @Override
    public String toString() {
        return agent + ":" + state + "(" + source + ")";
    }

    @Nullable
    private static String matchAgent(@NonNull String value) {
        if (value.isEmpty()) return null;
        // The npm package name, which is what an npx-launched Claude Code shows as its script.
        if (value.equals("claude-code")) return AGENT_CLAUDE;
        for (String known : KNOWN_AGENTS) {
            if (value.equals(known)) return known;
        }
        return null;
    }

    private static boolean isInterpreter(@NonNull String value) {
        for (String interpreter : INTERPRETERS) {
            if (value.equals(interpreter)) return true;
        }
        return false;
    }

    /** Basename, lowercased, with a trailing {@code .js}/{@code .py} and any leading dash dropped. */
    @NonNull
    private static String normalise(@Nullable String value) {
        if (value == null) return "";
        String name = value.trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        // A login shell is exec'd as "-bash"; the same convention can reach an argv entry.
        if (name.startsWith("-")) name = name.substring(1);
        name = name.toLowerCase(Locale.ROOT);
        if (name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".py")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }
}
