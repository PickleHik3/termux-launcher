package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalActionDispatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
 * ({@link ForegroundState}):
 * <ul>
 *   <li><b>Confirmed still running</b> (the pane's own foreground process group is not idle) —
 *       focus it, exactly as before.
 *   <li><b>Not confirmed</b> — including "unknown", which is the common case — re-run the app's
 *       command in that pane (write it in, as a user would type it, and focus) rather than
 *       guessing. A live pane that is merely sitting at a prompt is reused rather than
 *       abandoned for a fresh one.
 *   <li><b>No live pane at all</b> (never opened, or its shell has since exited) — open a fresh
 *       one.
 * </ul>
 *
 * <p><b>Why "confirmed" and not "assume running": {@code pane.open}'s own wrapper (see
 * {@code TermuxActivity#createCommandShell}) runs the app then {@code exec}s into a login shell
 * that stays behind — same pid throughout, since {@code exec} replaces the process image in
 * place — so a pane's own liveness ({@code pane.list}'s {@code running}) cannot tell "the app is
 * still running" from "the app finished and this is now an idle shell". The one thing in this
 * codebase that can tell the difference is {@link com.termux.app.statusbar.WindowForegroundResolver}
 * (reads {@code /proc/<pid>/stat}'s {@code tpgid} to see whether anything other than the shell
 * itself currently owns the pane's foreground). It has three problems for this exact call site,
 * which is why it is consulted rather than relied on:
 * <ol>
 *   <li>It requires the launcher's privileged backend (Shizuku or {@code su}/{@code rish}) —
 *       optional, and commonly not set up at all, in which case it never answers anything.
 *   <li>It is asynchronous and throttled, polled only while the terminal window bar itself is
 *       being refreshed (see {@code TermuxActivity#scheduleWindowLabelPoll}) — there is no
 *       synchronous "ask and wait" entry point, so a tap that lands while the terminal place has
 *       not been on screen recently finds nothing cached.
 *   <li>Its {@code processName} is whatever owns the pane's foreground process <em>group</em> —
 *       for a container app that is {@code proot-distro} (or an intermediate {@code sh}), not
 *       the app inside it, the exact wrapper-vs-real-command trap the window chips hit once
 *       already. Matching on a process <em>name</em> here would misidentify a container app as
 *       "not running" or "running" almost arbitrarily.
 * </ol>
 * <p>None of that applies to the one bit this class actually reads: {@code idle} — whether
 * <em>anything at all</em> is running in the pane's foreground besides the shell. That question
 * needs no process identity, so the wrapper-name trap does not apply to it, and reading only that
 * bit means an unavailable or stale resolver degrades to "unknown" (never a wrong "confirmed"),
 * which the rule above treats as "re-run", not "assume running". No refresh is requested here —
 * this only reads whatever the window bar has already cached — so this call never itself demands
 * privileged access; it simply benefits when that data happens to already be fresh.
 */
public final class LinuxTerminalAppRunner {

    private static final String TAG_PREFIX = "linux-terminal-app:";

    private LinuxTerminalAppRunner() {}

    /**
     * Whether a pane's own foreground process group is idle (only the shell, nothing running in
     * it), when that is knowable at all. Null when unknown — no privileged backend, no reading
     * yet, or the pid is not one being watched — never guessed.
     */
    public interface ForegroundState {
        @Nullable Boolean isIdle(int shellPid);
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
     * Whether an existing tagged pane should be focused as-is rather than re-run: only when
     * {@code idle} is a confirmed {@code false} (something other than the shell is in the
     * pane's foreground right now). Null (unknown) and {@code true} (confirmed idle) both mean
     * "re-run" — see the class doc for why "unknown" is not treated as "running".
     */
    static boolean shouldFocusRatherThanRerun(@Nullable Boolean idle) {
        return idle != null && !idle;
    }

    /**
     * Runs {@code app} in a pane. {@code foreground}, when given, is asked whether the app's
     * existing pane (if any) is confirmed still running before deciding to focus it outright
     * instead of re-running the command in it; null (or an unknown answer from it) means "not
     * confirmed", not "not running" — see the class doc.
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
            if (shouldFocusRatherThanRerun(idle)) {
                JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                    new JSONObject().put("id", existing.id));
                return focus.optBoolean("ok", false);
            }
            JSONObject write = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_WRITE,
                new JSONObject().put("id", existing.id).put("text", app.command()).put("enter", true));
            if (!write.optBoolean("ok", false)) {
                // The pane's shell exited between the list above and this write (or was never
                // truly reusable) — fall back to a fresh one rather than leaving the tap dead.
                return openFresh(dispatcher, app);
            }
            JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                new JSONObject().put("id", existing.id));
            return focus.optBoolean("ok", false);
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
