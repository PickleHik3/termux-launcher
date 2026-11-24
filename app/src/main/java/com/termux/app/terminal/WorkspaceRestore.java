package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What restoring a saved pane does, as pure policy: where the shell opens, which program it starts
 * and the one line it prints when something saved is gone. Save keeps each pane's program (a fresh
 * read of its foreground process, {@code WindowForegroundResolver.captureCommand}); restore always
 * starts it. The activity only creates the shell this answer describes.
 *
 * <p>Loading a saved session and the autosave's rebuild after a kill both come through
 * {@link #plan}, so they follow the same rules. A pane that ran an agent in {@link AgentResume}
 * comes back resumed by the id it saved, or started fresh with one line saying so.
 */
public final class WorkspaceRestore {

    /**
     * A resume that fails inside this many seconds is a conversation that is gone (all three
     * agents exit 1 at once), so the pane starts the agent fresh; one that ran longer was a
     * session the user ended.
     */
    public static final int RESUME_FAILS_WITHIN_S = 10;

    private WorkspaceRestore() {}

    /** The user-facing lines, filled in by the activity from string resources. */
    public interface Notices {
        /** The program is not on the shell's PATH. */
        @NonNull String programMissing(@NonNull String program);

        /** An agent came back without its conversation: "Claude started a new conversation". */
        @NonNull String agentStartedFresh(@NonNull String agentName);

        /** The saved folder is gone; {@code program} is what was running there, or null. */
        @NonNull String folderMissing(@NonNull String folder, @Nullable String program);
    }

    /** Whether a saved folder still exists. */
    public interface FolderCheck {
        boolean exists(@NonNull String folder);
    }

    /** How one saved pane is brought back. */
    public static final class PanePlan {
        /** The folder the shell opens in: the saved one, or the default when it is gone. */
        @NonNull public final String cwd;
        /** What to start; empty for a plain shell. */
        @NonNull public final List<String> command;
        /** The one line printed first, or null. */
        @Nullable public final String note;
        /**
         * When {@link #command} is a resume that may find its conversation gone: what to start
         * instead. Empty otherwise.
         */
        @NonNull public final List<String> fallback;
        /** Printed before an agent starts fresh (no id, or the id was stale); null otherwise. */
        @Nullable public final String freshLine;

        PanePlan(@NonNull String cwd, @NonNull List<String> command, @Nullable String note) {
            this(cwd, command, note, Collections.<String>emptyList(), null);
        }

        PanePlan(@NonNull String cwd, @NonNull List<String> command, @Nullable String note,
                 @NonNull List<String> fallback, @Nullable String freshLine) {
            this.cwd = cwd;
            this.command = command;
            this.note = note;
            this.fallback = fallback;
            this.freshLine = freshLine;
        }
    }

    @NonNull
    public static PanePlan plan(@NonNull TerminalWorkspace.Pane pane, @NonNull FolderCheck folders,
                                @NonNull String defaultCwd, @NonNull Notices notices) {
        String saved = pane.cwd;
        if (saved == null || saved.isEmpty())
            return agentPlan(defaultCwd, pane, notices);
        if (folders.exists(saved)) return agentPlan(saved, pane, notices);
        // A program started in the wrong folder would not be the one that was saved.
        String program = pane.command.isEmpty() ? null : pane.command.get(0);
        return new PanePlan(defaultCwd, Collections.<String>emptyList(),
            notices.folderMissing(saved, program));
    }

    /** The plan for a pane whose folder is fine: an agent resumes by its id, else starts fresh. */
    @NonNull
    private static PanePlan agentPlan(@NonNull String cwd, @NonNull TerminalWorkspace.Pane pane,
                                      @NonNull Notices notices) {
        AgentResume.Row row = AgentResume.forCommand(pane.command);
        if (row == null) return new PanePlan(cwd, pane.command, null);
        String line = notices.agentStartedFresh(row.displayName);
        boolean resumable = row.agent.equals(pane.agent)
            && AgentResume.isValidSessionId(pane.agentSessionId);
        if (!resumable) {
            return new PanePlan(cwd, pane.command, null, Collections.<String>emptyList(), line);
        }
        return new PanePlan(cwd, row.resume(pane.agentSessionId), null,
            new ArrayList<>(pane.command), line);
    }

    /**
     * The script the login shell runs for {@code plan}, or null when the pane is a plain shell. It
     * ends by becoming the user's login shell, so a program that quits leaves the pane usable.
     */
    @Nullable
    public static String script(@NonNull PanePlan plan, @NonNull String shellPath, boolean fishStyle,
                                @NonNull Notices notices) {
        StringBuilder script = new StringBuilder();
        if (plan.note != null) {
            script.append(print(plan.note, fishStyle));
        } else if (!plan.command.isEmpty()) {
            String program = plan.command.get(0);
            String missing = print(notices.programMissing(program), fishStyle);
            String check = "if command -v " + shellQuote(program, fishStyle) + " >/dev/null 2>&1; ";
            script.append(check);
            String run = commandLine(plan.command, fishStyle);
            if (!plan.fallback.isEmpty() && plan.freshLine != null) {
                run = resumeOrStartFresh(plan, fishStyle);
            } else if (plan.freshLine != null) {
                run = print(plan.freshLine, fishStyle) + "; " + run;
            }
            script.append(fishStyle ? run + "; else; " + missing + "; end"
                : "then " + run + "; else " + missing + "; fi");
        } else {
            return null;
        }
        return script.append("; exec ").append(shellQuote(shellPath, fishStyle)).append(" -l")
            .toString();
    }

    /**
     * Resume, and when that fails at once start the saved command fresh. The two shell families
     * differ in how they time it and chain a failure, so both spellings are here.
     */
    @NonNull
    private static String resumeOrStartFresh(@NonNull PanePlan plan, boolean fishStyle) {
        String resume = commandLine(plan.command, fishStyle);
        String fresh = print(plan.freshLine, fishStyle) + "; "
            + commandLine(plan.fallback, fishStyle);
        if (fishStyle) {
            return "set -l t (date +%s); " + resume + "; or begin; if test (math (date +%s) - $t) -lt "
                + RESUME_FAILS_WITHIN_S + "; " + fresh + "; end; end";
        }
        return "t=$(date +%s); " + resume + " || { [ $(( $(date +%s) - t )) -lt "
            + RESUME_FAILS_WITHIN_S + " ] && { " + fresh + "; }; }";
    }

    @NonNull
    private static String print(@NonNull String line, boolean fishStyle) {
        return "printf '%s\\n' " + shellQuote(line, fishStyle);
    }

    /**
     * Single-quote an argument so a shell reads it back as the one literal word it was.
     *
     * <p>The two families disagree inside single quotes. POSIX shells take every character
     * literally, so a quote has to end the string, be escaped outside it, and start a new one.
     * Fish instead honours {@code \'} and {@code \\} within the quotes, which means a backslash
     * must be doubled for fish and must not be for anyone else — and an argument ending in a
     * backslash would otherwise escape fish's own closing quote and swallow the rest of the line.
     */
    @NonNull
    public static String shellQuote(@NonNull String argument, boolean fishStyle) {
        if (fishStyle)
            return "'" + argument.replace("\\", "\\\\").replace("'", "\\'") + "'";
        return "'" + argument.replace("'", "'\\''") + "'";
    }

    /** Rebuild a shell command line from captured argv, quoting every word. */
    @NonNull
    public static String commandLine(@NonNull List<String> command, boolean fishStyle) {
        StringBuilder line = new StringBuilder();
        for (String argument : command) {
            if (line.length() > 0) line.append(' ');
            line.append(shellQuote(argument, fishStyle));
        }
        return line.toString();
    }
}
