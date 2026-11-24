package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.WindowForegroundResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The agents that resume a conversation by session id: how each is recognised in a saved pane and
 * the command that reopens its conversation. How the id reaches the launcher is the agent's own
 * hook or plugin ({@code ClaudeHooksInstaller}); restore only reads this table.
 */
public final class AgentResume {

    private AgentResume() {}

    /** One agent: its name as {@link AgentStatus} spells it, and the words before the id. */
    public static final class Row {
        @NonNull public final String agent;
        @NonNull public final String displayName;
        @NonNull private final List<String> resumeWords;

        private Row(@NonNull String agent, @NonNull String displayName, @NonNull String... words) {
            this.agent = agent;
            this.displayName = displayName;
            this.resumeWords = Collections.unmodifiableList(Arrays.asList(words));
        }

        /** The command that reopens conversation {@code id}. */
        @NonNull
        public List<String> resume(@NonNull String id) {
            List<String> command = new ArrayList<>(resumeWords.size() + 2);
            command.add(agent);
            command.addAll(resumeWords);
            command.add(id);
            return command;
        }
    }

    private static final Row[] ROWS = {
        new Row(AgentStatus.AGENT_CLAUDE, "Claude", "--resume"),
        new Row(AgentStatus.AGENT_CODEX, "Codex", "resume"),
        new Row(AgentStatus.AGENT_OPENCODE, "OpenCode", "--session"),
    };

    /** Whether the agent's hook or plugin has been installed, asked per agent name. */
    public interface Installed {
        boolean has(@NonNull String agent);
    }

    @Nullable
    public static Row forAgent(@Nullable String agent) {
        if (agent == null) return null;
        for (Row row : ROWS) if (row.agent.equals(agent)) return row;
        return null;
    }

    /** The row a captured command runs, by its unwrapped program name, or null. */
    @Nullable
    public static Row forCommand(@NonNull List<String> command) {
        if (command.isEmpty()) return null;
        return forAgent(WindowForegroundResolver.processName(command.toArray(new String[0])));
    }

    /**
     * Ids reach a shell command line and a file, so only the characters real ids use pass: an id
     * starting with a dash would be read as an option.
     */
    public static boolean isValidSessionId(@Nullable String id) {
        if (id == null || id.isEmpty() || id.length() > 128) return false;
        if (id.charAt(0) == '-') return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == ':';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * The id a pane's save keeps: the one its agent reported, while the program saved with it is
     * still that agent. After the agent quits and something else runs, the id is dropped.
     */
    @Nullable
    public static String sessionToSave(@NonNull List<String> command, @Nullable String agent,
                                       @Nullable String id) {
        Row row = forCommand(command);
        if (row == null || !row.agent.equals(agent) || !isValidSessionId(id)) return null;
        return id;
    }

    /**
     * The agents running in {@code commands} that the user has not set up for resume, in order of
     * first appearance. Empty means the Save dialog says nothing.
     */
    @NonNull
    public static List<String> agentsNeedingSetup(@NonNull List<List<String>> commands,
                                                  @NonNull Installed installed) {
        List<String> missing = new ArrayList<>();
        for (List<String> command : commands) {
            Row row = forCommand(command);
            if (row == null || missing.contains(row.agent) || installed.has(row.agent)) continue;
            missing.add(row.agent);
        }
        return missing;
    }
}
