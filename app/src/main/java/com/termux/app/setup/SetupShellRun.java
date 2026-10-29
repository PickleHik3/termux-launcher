package com.termux.app.setup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.logger.Logger;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

/**
 * The one setup shell run the wizard starts, owned by the process rather than by the page: a
 * script run in a headless {@link TerminalSession}, the way the graphics benchmark is
 * ({@code X11GpuBenchmarkRun}). It is not one of the user's sessions: it never reaches the
 * service's shell manager, so it shows as no pane or tab. A page attaches while shown and the run
 * carries on without it; the transcript stays until {@link #dismiss()} or the next run.
 *
 * <p>The run takes no input. A command that asks a question waits for ever; the caller must only
 * hand it commands that do not.
 *
 * <p>Main thread only, apart from {@link #prepare}.
 */
final class SetupShellRun {
    enum State { IDLE, RUNNING, FINISHED, FAILED }

    /** The page's side: the transcript or the state changed. */
    interface Listener {
        void onChanged();
    }

    /** What starting needs, built off the main thread. */
    static final class Launch {
        @NonNull final String executable;
        @NonNull final String[] arguments;
        @NonNull final String[] environment;

        Launch(@NonNull String executable, @NonNull String[] arguments, @NonNull String[] environment) {
            this.executable = executable;
            this.arguments = arguments;
            this.environment = environment;
        }
    }

    private static final int TRANSCRIPT_ROWS = 1000;
    private static final int START_COLUMNS = 80;
    private static final int START_ROWS = 24;
    private static final int START_CELL_WIDTH = 10;
    private static final int START_CELL_HEIGHT = 20;
    /** How often, at most, a listener hears of new output. */
    private static final long TEXT_NOTIFY_MS = 300L;
    private static final String LOG_TAG = "SetupShellRun";
    /** Where the command is written, in the prefix's tmp. */
    static final String SCRIPT_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/setup-display-run.sh";

    @SuppressLint("StaticFieldLeak") // the application context only
    @Nullable private static SetupShellRun instance;

    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private TerminalSession session;
    @NonNull private State state = State.IDLE;
    private int exitStatus = -1;
    @Nullable private Listener listener;
    private boolean notifyPending;

    private SetupShellRun() {}

    @MainThread
    @NonNull
    static SetupShellRun get() {
        if (instance == null) instance = new SetupShellRun();
        return instance;
    }

    @NonNull State state() { return state; }
    boolean isActive() { return state == State.RUNNING; }
    /** The exit status of the finished run; -1 before one ended. */
    int exitStatus() { return exitStatus; }

    /** Writes {@code command} to the script file and resolves how to run it. File work. */
    @WorkerThread
    @NonNull
    static Launch prepare(@NonNull Context context, @NonNull String command) throws IOException {
        File file = new File(SCRIPT_PATH);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        Files.write(file.toPath(), DisplayCardLogic.script(command).getBytes(StandardCharsets.UTF_8));
        String[] argv = TermuxShellUtils.setupShellCommandArguments(SCRIPT_PATH, new String[0]);
        String[] arguments = argv.clone();
        arguments[0] = ShellUtils.getExecutableBasename(argv[0]);
        HashMap<String, String> env = new TermuxShellEnvironment().getEnvironment(context, false);
        env.put(UnixShellEnvironment.ENV_PWD, TermuxConstants.TERMUX_HOME_DIR_PATH);
        ShellEnvironmentUtils.createHomeDir(env);
        List<String> environ = ShellEnvironmentUtils.convertEnvironmentToEnviron(env);
        return new Launch(argv[0], arguments, environ.toArray(new String[0]));
    }

    /** Starts the script in a session of its own; false when a run is going or it would not start. */
    @MainThread
    boolean start(@NonNull Launch launch) {
        if (isActive()) return false;
        release();
        TerminalSession created = new TerminalSession(launch.executable,
            TermuxConstants.TERMUX_HOME_DIR_PATH, launch.arguments, launch.environment,
            TRANSCRIPT_ROWS, new SessionClient());
        // The page says how the run ended; the session takes no Enter.
        created.mShowsExitLine = false;
        session = created;
        state = State.RUNNING;
        exitStatus = -1;
        try {
            created.updateSize(START_COLUMNS, START_ROWS, START_CELL_WIDTH, START_CELL_HEIGHT);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The setup could not be started", e);
            session = null;
            state = State.FAILED;
            changed();
            return false;
        }
        changed();
        return true;
    }

    /** Puts a finished run away: its transcript goes. */
    @MainThread
    void dismiss() {
        if (isActive()) return;
        release();
        state = State.IDLE;
        exitStatus = -1;
        changed();
    }

    @MainThread
    void attach(@NonNull Listener page) {
        listener = page;
    }

    @MainThread
    void detach(@NonNull Listener page) {
        if (listener == page) listener = null;
    }

    /** Everything the run has printed that the transcript still holds, screen included. */
    @NonNull
    String transcript() {
        TerminalSession s = session;
        TerminalEmulator emulator = s == null ? null : s.getEmulator();
        if (emulator == null) return "";
        String text = emulator.getScreen().getTranscriptText();
        return text == null ? "" : text;
    }

    private void release() {
        TerminalSession old = session;
        session = null;
        if (old != null && old.isRunning()) old.finishIfRunning();
    }

    private void changed() {
        Listener l = listener;
        if (l != null) l.onChanged();
    }

    private final class SessionClient extends TermuxTerminalSessionClientBase {
        @Override
        public void onTextChanged(@NonNull TerminalSession changed) {
            if (changed != session || notifyPending) return;
            notifyPending = true;
            handler.postDelayed(() -> {
                notifyPending = false;
                SetupShellRun.this.changed();
            }, TEXT_NOTIFY_MS);
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finished) {
            if (finished != session) return;
            handler.removeCallbacksAndMessages(null);
            notifyPending = false;
            exitStatus = finished.getExitStatus();
            state = exitStatus == 0 ? State.FINISHED : State.FAILED;
            SetupShellRun.this.changed();
        }
    }
}
