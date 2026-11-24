package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * What restoring a saved pane does, as pure policy: where the shell opens, which program it starts
 * and the one line it prints when something saved is gone. Save keeps each pane's program (a fresh
 * read of its foreground process, {@code WindowForegroundResolver.captureCommand}); restore always
 * starts it. The activity only creates the shell this answer describes.
 *
 * <p>Loading a saved session and the autosave's rebuild after a kill both come through
 * {@link #plan}, so they follow the same rules. A resume-by-id for agents hooks in here: it
 * rewrites {@link PanePlan#command} before the script is built.
 */
public final class WorkspaceRestore {

    private WorkspaceRestore() {}

    /** The user-facing lines, filled in by the activity from string resources. */
    public interface Notices {
        /** The program is not on the shell's PATH. */
        @NonNull String programMissing(@NonNull String program);

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

        PanePlan(@NonNull String cwd, @NonNull List<String> command, @Nullable String note) {
            this.cwd = cwd;
            this.command = command;
            this.note = note;
        }
    }

    @NonNull
    public static PanePlan plan(@NonNull TerminalWorkspace.Pane pane, @NonNull FolderCheck folders,
                                @NonNull String defaultCwd, @NonNull Notices notices) {
        String saved = pane.cwd;
        if (saved == null || saved.isEmpty())
            return new PanePlan(defaultCwd, pane.command, null);
        if (folders.exists(saved)) return new PanePlan(saved, pane.command, null);
        // A program started in the wrong folder would not be the one that was saved.
        String program = pane.command.isEmpty() ? null : pane.command.get(0);
        return new PanePlan(defaultCwd, Collections.<String>emptyList(),
            notices.folderMissing(saved, program));
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
            String run = commandLine(plan.command, fishStyle);
            script.append(check);
            script.append(fishStyle ? run + "; else; " + missing + "; end"
                : "then " + run + "; else " + missing + "; fi");
        } else {
            return null;
        }
        return script.append("; exec ").append(shellQuote(shellPath, fishStyle)).append(" -l")
            .toString();
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
