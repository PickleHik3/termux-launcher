package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalActionDispatcher;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Where D9's work actually happens: a terminal pane.
 *
 * <h3>Why a pane</h3>
 *
 * The run downloads hundreds of megabytes and spends minutes in {@code apt}, and the user is free
 * to swipe away from the Display place or put the phone in their pocket in the middle of it. Three
 * things in this repo can run a shell command, and only one of them survives that:
 *
 * <ul>
 *   <li><b>A pane.</b> {@code pane.open} puts the command in a {@code TerminalSession} owned by
 *       {@code TermuxService}, which is a foreground service — the session outlives the place the
 *       user was on, outlives the activity going to the background, and keeps its output. This is
 *       what {@link LinuxTerminalAppRunner} already uses for D5's terminal apps, so the route is
 *       the one the launcher already trusts with a long-running program.
 *   <li><b>A background task on the same service.</b> Survives just as well, but the user sees
 *       nothing: no progress, no {@code apt} output, and — worst — no sight of the one line that
 *       says what went wrong. For a job whose most likely failure is "the network went away
 *       eight minutes in", hiding the output is the wrong trade.
 *   <li><b>Anything tied to the activity.</b> Dies with the process the system is free to reclaim
 *       while the user is elsewhere, which is exactly the half-built container D9 must not leave.
 * </ul>
 *
 * <p>A pane also answers "how do I try again" for free: the pane is still there with the failure
 * in it, and the script is written to be safe to run from the top
 * ({@link DistroSetup#script}).
 *
 * <h3>Tapping it twice</h3>
 *
 * Every setup pane is tagged, so a second tap can find the first one. If that pane is
 * <em>confirmed</em> to still be running something, it is focused rather than joined — two
 * {@code apt} runs in one container fight over the same lock and the loser's error message is
 * nonsense. Anything less than confirmed (the pane finished, or its state could not be read)
 * opens a fresh pane instead of typing a multi-line script into a shell that might be an editor.
 * That reuses {@link LinuxTerminalAppRunner}'s reading of {@code /proc} rather than inventing a
 * second one.
 */
public final class DistroSetupRunner {

    /** The tag every setup pane carries, so the next tap can find it. */
    static final String TAG = "distro-setup";

    private DistroSetupRunner() {}

    /** What happened when the run was asked for. */
    public enum Result {
        /** A pane is now running the setup. */
        STARTED,
        /** A pane was already running it, and is now the focused one. */
        ALREADY_RUNNING,
        /** No pane could be opened, so nothing was started. */
        FAILED
    }

    /** Start the setup, or come back to the pane already running it. */
    @NonNull
    public static Result run(@NonNull String script, @NonNull String paneTitle) {
        return run(script, paneTitle, new LinuxTerminalAppRunner.ProcfsForegroundState());
    }

    /**
     * Start the setup, asking {@code foreground} whether an existing setup pane is still busy.
     * Exposed so a test can answer that without a real {@code /proc} entry behind it.
     */
    @NonNull
    static Result run(@NonNull String script, @NonNull String paneTitle,
                      @Nullable LinuxTerminalAppRunner.ForegroundState foreground) {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        if (!dispatcher.isAttached()) return Result.FAILED;
        JSONObject list = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_LIST, new JSONObject());
        LinuxTerminalAppRunner.ExistingPane existing =
            list.optBoolean("ok", false) ? LinuxTerminalAppRunner.findExistingPane(list, TAG) : null;
        if (existing != null && foreground != null && Boolean.FALSE.equals(foreground.isIdle(existing.pid))) {
            try {
                JSONObject focus = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_FOCUS,
                    new JSONObject().put("id", existing.id));
                return focus.optBoolean("ok", false) ? Result.ALREADY_RUNNING : Result.FAILED;
            } catch (JSONException e) {
                return Result.FAILED;
            }
        }
        JSONObject opened = dispatcher.execute(TerminalActionDispatcher.TOOL_PANE_OPEN,
            openArguments(script, paneTitle));
        return opened.optBoolean("ok", false) ? Result.STARTED : Result.FAILED;
    }

    /** {@code pane.open}'s arguments for a setup run. */
    @NonNull
    static JSONObject openArguments(@NonNull String script, @NonNull String paneTitle) {
        try {
            return new JSONObject()
                .put("command", script)
                .put("title", paneTitle)
                .put("tag", TAG)
                .put("focus", true);
        } catch (JSONException e) {
            // put() only throws for a non-finite double; nothing here is one.
            throw new AssertionError(e);
        }
    }
}
