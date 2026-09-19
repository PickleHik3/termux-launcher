package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalActionDispatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Runs a {@code Terminal=true} Linux app (D5) in a terminal pane instead of on the display: the
 * same in-app pane route {@code launcherctl pane open}/{@code pane focus} already drive through
 * {@link TerminalActionDispatcher}, not a second one. Never touches the embedded X server or
 * {@link X11LinuxAppRunner} — a container app's whole {@code proot-distro login …} line runs
 * inside the pane's shell exactly as {@link LinuxAppCatalog.LinuxApp#command()} built it.
 *
 * <p>A tap on an app whose pane is already running focuses that pane instead of opening a
 * duplicate: every pane opened here is tagged with the app's id, {@code pane.list} reports the
 * tag back, and a running match is focused rather than piling up a second pane for the same
 * program. "Running" only means the pane's shell is still alive — once the app's own command
 * exits, the shell it started in stays behind (like a restored workspace pane), so a later tap
 * focuses that idle shell rather than relaunching; nothing in the pane API exposes whether the
 * original foreground command is still the one running, so this is the closest available signal.
 */
public final class LinuxTerminalAppRunner {

    private static final String TAG_PREFIX = "linux-terminal-app:";

    private LinuxTerminalAppRunner() {}

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
     * The id of a still-running pane already tagged for {@code tag} inside a {@code pane.list}
     * result, or null when there is none. Pure: the same shape {@code LauncherCtlApiServer}
     * answers a client with.
     */
    @Nullable
    static String findRunningPaneId(@NonNull JSONObject paneListResult, @NonNull String tag) {
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
                if (agent != null && tag.equals(agent.optString("tag", ""))) {
                    return pane.optString("id", null);
                }
            }
        }
        return null;
    }

    /**
     * Runs {@code app} in a pane: focuses its already-running pane when there is one, opens a
     * fresh one otherwise. False when there is no live host to ask (the launcher is not running
     * at all) or the pane request itself failed (no active session, terminal limit reached).
     */
    public static boolean run(@NonNull LinuxAppCatalog.LinuxApp app) {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        if (!dispatcher.isAttached()) return false;
        String tag = tagFor(app);
        JSONObject list = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_LIST, new JSONObject());
        String existing = list.optBoolean("ok", false) ? findRunningPaneId(list, tag) : null;
        if (existing != null) {
            try {
                JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                    new JSONObject().put("id", existing));
                return focus.optBoolean("ok", false);
            } catch (JSONException e) {
                return false;
            }
        }
        JSONObject opened = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_OPEN, openArguments(app));
        return opened.optBoolean("ok", false);
    }
}
