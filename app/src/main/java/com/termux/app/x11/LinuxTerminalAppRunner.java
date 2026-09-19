package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalActionDispatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Runs a {@code Terminal=true} Linux app (D5) in a terminal pane instead of on the display: the
 * same in-app pane route {@code launcherctl pane open}/{@code pane focus}/{@code pane write}
 * already drive through {@link TerminalActionDispatcher}, not a second one. Never touches the
 * embedded X server or {@link X11LinuxAppRunner} — a container app's whole
 * {@code proot-distro login …} line runs inside the pane's shell exactly as
 * {@link LinuxAppCatalog.LinuxApp#command()} built it.
 *
 * <p>Every pane opened here is tagged with the app's id, and {@code pane.list} reports the tag
 * back, so a second tap can find the pane it opened last time rather than piling up a duplicate.
 * What it does with that pane depends on whether the app is confirmed still running in it
 * ({@link ForegroundState}, {@link #actionFor}):
 * <ul>
 *   <li><b>Confirmed still running</b> ({@code idle == false}) — focus it. Its input is never
 *       touched: typing into a pane that might be a live program (a TUI, an editor) rather than
 *       an idle shell is destructive, not merely wrong.
 *   <li><b>Confirmed idle</b> ({@code idle == true}) — the app already finished and this is now
 *       an ordinary shell sitting at a prompt; safe to re-run the command in it (write it in, as
 *       a user would type it, and focus).
 *   <li><b>Unknown</b> ({@code idle == null}) — never confirmed either way, so neither of the
 *       above is safe: open a fresh pane instead. A duplicate pane is untidy; a keystroke landing
 *       in whatever the existing one turns out to be running is not a trade worth making for
 *       tidiness.
 *   <li><b>No live pane at all</b> (never opened, or its shell has since exited) — open a fresh
 *       one; there is nothing to be unsure about.
 * </ul>
 *
 * <p><b>Why "confirmed" and not "assume running":</b> {@code pane.open}'s own wrapper (see
 * {@code TermuxActivity#createCommandShell}) runs the app then {@code exec}s into a login shell
 * that stays behind — same pid throughout, since {@code exec} replaces the process image in
 * place — so a pane's own liveness ({@code pane.list}'s {@code running}) cannot tell "the app is
 * still running" from "the app finished and this is now an idle shell".
 *
 * <p><b>Where the confirmed answer comes from:</b> {@link ProcfsForegroundState} reads
 * {@code /proc/<pid>/stat}'s {@code tpgid} directly — no privileged backend, and deliberately
 * <em>not</em> {@link com.termux.app.statusbar.WindowForegroundResolver}, which reads the same
 * field but for a different reason needs one. That resolver's privileged read is for scanning
 * every numbered entry under {@code /proc} system-wide (summing a foreground group's CPU across
 * every other process's stat file too), which {@code hidepid} hides from an unprivileged app; a
 * pane's own shell is this app's own child, same uid, and {@code /proc/<pid>/stat} of the launcher's own
 * processes is already read unprivileged elsewhere in this codebase
 * ({@code ProcSessionTable}, used to sweep a killed pane's process group — see its class doc:
 * "under Android's hidepid mount only this uid's processes are listed, which is exactly the set a
 * pane can have started"). Verified by reading both classes rather than assumed.
 *
 * <p>{@code tpgid} identifies the pane's foreground process <em>group</em>, not a program by
 * name — for a container app that group's own leader is {@code proot-distro} (or an intermediate
 * {@code sh}), never the app inside it. This class never reads or matches a process name; it only
 * compares {@code tpgid} to the shell's own pid, so the wrapper-vs-real-command trap the window
 * chips hit once already does not apply here.
 */
public final class LinuxTerminalAppRunner {

    private static final String TAG_PREFIX = "linux-terminal-app:";

    private LinuxTerminalAppRunner() {}

    /**
     * Whether a pane's own foreground process group is idle (only the shell, nothing running in
     * it), when that is knowable at all. Null when unknown — the stat file could not be read or
     * parsed — never guessed.
     */
    public interface ForegroundState {
        @Nullable Boolean isIdle(int shellPid);
    }

    /**
     * The real, procfs-based {@link ForegroundState}: reads {@code /proc/<pid>/stat} directly,
     * unprivileged (see the class doc for why that is safe for this app's own pane shells).
     */
    public static final class ProcfsForegroundState implements ForegroundState {
        @Override @Nullable public Boolean isIdle(int shellPid) {
            return readIsIdle(new File("/proc/" + shellPid + "/stat"), shellPid);
        }
    }

    /** A pane found already tagged for this app: its id, and the pid its foreground is read by. */
    static final class ExistingPane {
        @NonNull final String id;
        final int pid;

        ExistingPane(@NonNull String id, int pid) {
            this.id = id;
            this.pid = pid;
        }
    }

    /** What to do with an existing tagged pane, or that there is none to consider at all. */
    enum Action { FOCUS, RERUN_IN_PLACE, OPEN_FRESH }

    /** The tag a pane running {@code app} is opened with; stable across taps. */
    @NonNull
    static String tagFor(@NonNull LinuxAppCatalog.LinuxApp app) {
        return TAG_PREFIX + app.id;
    }

    /**
     * {@code pane.open}'s arguments for {@code app}: its own command as the pane's command line,
     * its own name — what the drawer tile already shows — as the pane's title.
     */
    @NonNull
    static JSONObject openArguments(@NonNull LinuxAppCatalog.LinuxApp app) {
        try {
            return new JSONObject()
                .put("command", app.command())
                .put("title", app.name)
                .put("tag", tagFor(app))
                .put("focus", true);
        } catch (JSONException e) {
            // put() only throws for a non-finite double; nothing here is one.
            throw new AssertionError(e);
        }
    }

    /**
     * The still-alive pane already tagged for {@code tag} inside a {@code pane.list} result, or
     * null when there is none. "Alive" here only means the pane's own shell has not exited
     * ({@code running}); it says nothing about what is currently running inside it — that is
     * {@link ForegroundState}'s job. Pure: the same shape {@code LauncherCtlApiServer} answers a
     * client with.
     */
    @Nullable
    static ExistingPane findExistingPane(@NonNull JSONObject paneListResult, @NonNull String tag) {
        JSONArray windows = paneListResult.optJSONArray("windows");
        if (windows == null) return null;
        for (int w = 0; w < windows.length(); w++) {
            JSONObject window = windows.optJSONObject(w);
            JSONArray panes = window == null ? null : window.optJSONArray("panes");
            if (panes == null) continue;
            for (int p = 0; p < panes.length(); p++) {
                JSONObject pane = panes.optJSONObject(p);
                if (pane == null || !pane.optBoolean("running", false)) continue;
                JSONObject agent = pane.optJSONObject("agent");
                if (agent == null || !tag.equals(agent.optString("tag", ""))) continue;
                String id = pane.optString("id", null);
                if (id == null) continue;
                return new ExistingPane(id, pane.optInt("pid", -1));
            }
        }
        return null;
    }

    /**
     * What to do with an existing tagged pane given what is known about it: focus only on a
     * confirmed {@code false} (something other than the shell owns the foreground right now);
     * re-run in place only on a confirmed {@code true} (confirmed idle); anything unknown opens a
     * fresh pane instead of guessing, since a wrong guess here is either useless (focusing a dead
     * prompt) or destructive (typing into a live program).
     */
    @NonNull
    static Action actionFor(@Nullable Boolean idle) {
        if (idle == null) return Action.OPEN_FRESH;
        return idle ? Action.RERUN_IN_PLACE : Action.FOCUS;
    }

    /**
     * {@code /proc/<pid>/stat}'s {@code tpgid} (the 8th field; comm is skipped over since it can
     * itself contain spaces and parentheses), compared against {@code pid} itself: equal means
     * the shell is its own foreground — idle. Null when the file cannot be read or parsed (the
     * process has already gone, or the line is not in the expected shape) — same parsing style as
     * {@code ProcSessionTable.readPgrpAndSid}, which this mirrors.
     */
    @Nullable
    static Boolean readIsIdle(@NonNull File stat, int pid) {
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(stat))) {
            line = reader.readLine();
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (line == null) return null;
        int close = line.lastIndexOf(')');
        if (close < 0) return null;
        String[] fields = line.substring(close + 1).trim().split(" ");
        if (fields.length < 6) return null;
        try {
            int tpgid = Integer.parseInt(fields[5]);
            return tpgid == pid || tpgid < 1;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Runs {@code app} in a pane, reading the real {@link ProcfsForegroundState} for what an
     * existing tagged pane is doing. The production entry point.
     */
    public static boolean run(@NonNull LinuxAppCatalog.LinuxApp app) {
        return run(app, new ProcfsForegroundState());
    }

    /**
     * Runs {@code app} in a pane. {@code foreground}, when given, is asked whether the app's
     * existing pane (if any) is confirmed still running before deciding what to do with it — see
     * {@link #actionFor}. Exposed for tests to inject a fixed answer instead of reading
     * {@code /proc}.
     */
    public static boolean run(@NonNull LinuxAppCatalog.LinuxApp app, @Nullable ForegroundState foreground) {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        if (!dispatcher.isAttached()) return false;
        String tag = tagFor(app);
        JSONObject list = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_LIST, new JSONObject());
        ExistingPane existing = list.optBoolean("ok", false) ? findExistingPane(list, tag) : null;
        if (existing == null) {
            return openFresh(dispatcher, app);
        }
        Boolean idle = foreground == null ? null : foreground.isIdle(existing.pid);
        try {
            switch (actionFor(idle)) {
                case FOCUS: {
                    JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                        new JSONObject().put("id", existing.id));
                    return focus.optBoolean("ok", false);
                }
                case RERUN_IN_PLACE: {
                    JSONObject write = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_WRITE,
                        new JSONObject().put("id", existing.id).put("text", app.command())
                            .put("enter", true));
                    if (!write.optBoolean("ok", false)) {
                        // The pane's shell exited between the list above and this write — fall
                        // back to a fresh one rather than leaving the tap dead.
                        return openFresh(dispatcher, app);
                    }
                    JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                        new JSONObject().put("id", existing.id));
                    return focus.optBoolean("ok", false);
                }
                default:
                    return openFresh(dispatcher, app);
            }
        } catch (JSONException e) {
            return false;
        }
    }

    private static boolean openFresh(@NonNull TerminalActionDispatcher dispatcher,
                                     @NonNull LinuxAppCatalog.LinuxApp app) {
        JSONObject opened = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_OPEN, openArguments(app));
        return opened.optBoolean("ok", false);
    }
}
