package com.termux.app.x11;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The one graphics benchmark run, owned by the process rather than by the page that started it.
 *
 * <p>The run is a real terminal session, so the script's package-manager progress bars and
 * carriage returns read as they do in a terminal, but it is not one of the user's sessions: it is
 * created here, never handed to {@code TermuxService}'s shell manager, and so never appears as a
 * pane, a tab or in the session manager. The Graphics acceleration page attaches a read-only
 * {@code TerminalView} to {@link #session()} while it is shown and detaches when it goes; the run
 * carries on without it, with its progress in a notification that opens the page again. The
 * finished transcript stays until the page's Close or the next run, bounded by
 * {@link #TRANSCRIPT_ROWS}.
 *
 * <p>Stop interrupts the script the way Ctrl-C would: SIGINT to the session's process group, and
 * nothing harder. The script traps it and puts back what it installed, ignoring further signals
 * while it does, so a kill that followed it (as closing a terminal pane does after 150 ms) would
 * cut a package removal in half.
 *
 * <p>Main thread only, apart from {@link #prepare} and {@link Estimate}.
 */
public final class X11GpuBenchmarkRun {
    public enum State { IDLE, RUNNING, STOPPING, FINISHED, STOPPED, FAILED }

    /** The page's side of the run: the screen changed, or the state or step did. */
    public interface Listener {
        void onScreenUpdated();
        void onRunChanged();
    }

    /** The script's output is a few hundred lines; this keeps all of it, and no more. */
    static final int TRANSCRIPT_ROWS = 600;
    /** The grid the run starts on before any page has measured one. */
    private static final int START_COLUMNS = 80;
    private static final int START_ROWS = 24;
    /** A nominal cell in pixels, for the same start; the page's view gives the real one. */
    private static final int START_CELL_WIDTH = 10;
    private static final int START_CELL_HEIGHT = 20;
    /** How often, at most, the transcript is read for the step line while output flows. */
    private static final long STEP_READ_MS = 500L;
    /** How long the size estimate may take; it may refresh the package lists first. */
    public static final long ESTIMATE_TIMEOUT_MS = 90_000L;
    private static final int ESTIMATE_MAX_BYTES = 64 * 1024;
    private static final int NOTIFICATION_ID = 24130;
    private static final String LOG_TAG = "X11GpuBenchmarkRun";

    @SuppressLint("StaticFieldLeak") // the application context only
    @Nullable private static X11GpuBenchmarkRun instance;

    @NonNull private final Context app;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private TerminalSession session;
    @NonNull private State state = State.IDLE;
    @Nullable private X11GpuBenchmarkStep step;
    @Nullable private Intent pageIntent;
    @Nullable private Listener listener;
    private boolean stepReadPending;

    private X11GpuBenchmarkRun(@NonNull Context app) {
        this.app = app;
    }

    @MainThread
    @NonNull
    public static X11GpuBenchmarkRun get(@NonNull Context context) {
        if (instance == null) instance = new X11GpuBenchmarkRun(context.getApplicationContext());
        return instance;
    }

    @NonNull public State state() { return state; }
    @Nullable public X11GpuBenchmarkStep step() { return step; }
    /** The run's terminal session, for a view to show; null while idle. */
    @Nullable public TerminalSession session() { return session; }
    public boolean isActive() { return state == State.RUNNING || state == State.STOPPING; }

    /** What starting the script needs, built off the main thread: its argv and environment. */
    public static final class Launch {
        @NonNull final String executable;
        @NonNull final String[] arguments;
        @NonNull final String[] environment;

        Launch(@NonNull String executable, @NonNull String[] arguments, @NonNull String[] environment) {
            this.executable = executable;
            this.arguments = arguments;
            this.environment = environment;
        }
    }

    /** The Termux environment a session shell gets, working in the home directory. File work. */
    @WorkerThread
    @NonNull
    static HashMap<String, String> environment(@NonNull Context context) {
        HashMap<String, String> env = new TermuxShellEnvironment().getEnvironment(context, false);
        env.put(UnixShellEnvironment.ENV_PWD, TermuxConstants.TERMUX_HOME_DIR_PATH);
        ShellEnvironmentUtils.createHomeDir(env);
        return env;
    }

    /** Resolves the script's interpreter and the environment, as a terminal session would. File work. */
    @WorkerThread
    @NonNull
    public static Launch prepare(@NonNull Context context, @NonNull String script, @NonNull String... args) {
        String[] argv = TermuxShellUtils.setupShellCommandArguments(script, args);
        String[] arguments = argv.clone();
        arguments[0] = ShellUtils.getExecutableBasename(argv[0]);
        List<String> environ = ShellEnvironmentUtils.convertEnvironmentToEnviron(environment(context));
        return new Launch(argv[0], arguments, environ.toArray(new String[0]));
    }

    /**
     * Starts the script in a session of its own. {@code page} is what the notification opens.
     *
     * @return false when a run is already going, or the session could not be started
     */
    @MainThread
    public boolean start(@NonNull Launch launch, @NonNull Intent page) {
        if (isActive()) return false;
        release();
        pageIntent = page;
        TerminalSession created = new TerminalSession(launch.executable,
            TermuxConstants.TERMUX_HOME_DIR_PATH, launch.arguments, launch.environment,
            TRANSCRIPT_ROWS, new SessionClient());
        created.mSessionName = app.getString(R.string.settings_x11_gpu_run_label);
        // The panel's step line says how the run ended; it takes no Enter.
        created.mShowsExitLine = false;
        session = created;
        state = State.RUNNING;
        step = null;
        try {
            // A session's process starts when it is first given a size; no view has one yet.
            created.updateSize(START_COLUMNS, START_ROWS, START_CELL_WIDTH, START_CELL_HEIGHT);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The benchmark could not be started", e);
            session = null;
            state = State.IDLE;
            changed();
            return false;
        }
        changed();
        return true;
    }

    /** Interrupts the run, which then puts back what it installed and ends on its own. */
    @MainThread
    public void stop() {
        TerminalSession running = session;
        if (state != State.RUNNING || running == null) return;
        int pid = running.getPid();
        if (pid <= 0) return;
        state = State.STOPPING;
        // The script is the session leader, so its pid is also its process group; everything it
        // runs in the foreground is in that group, as Ctrl-C at its terminal would reach it.
        if (!signal(-pid) && !signal(pid)) state = State.RUNNING;
        changed();
    }

    private static boolean signal(int pid) {
        try {
            Os.kill(pid, OsConstants.SIGINT);
            return true;
        } catch (ErrnoException e) {
            Logger.logWarn(LOG_TAG, "Could not interrupt " + pid + ": " + e.getMessage());
            return false;
        }
    }

    /** Puts a finished run away: its transcript goes, and the page shows Run again. */
    @MainThread
    public void dismiss() {
        if (isActive()) return;
        release();
        state = State.IDLE;
        step = null;
        cancelNotification();
        changed();
    }

    /** One page at a time shows the run; attaching also takes down a finished run's notification. */
    @MainThread
    public void attach(@NonNull Listener page) {
        listener = page;
        if (!isActive()) cancelNotification();
    }

    @MainThread
    public void detach(@NonNull Listener page) {
        if (listener == page) listener = null;
    }

    private void release() {
        TerminalSession old = session;
        session = null;
        // A finished session holds no process; this only makes sure of it.
        if (old != null && old.isRunning()) old.finishIfRunning();
    }

    private void changed() {
        updateNotification();
        Listener l = listener;
        if (l != null) l.onRunChanged();
    }

    private void readStep() {
        stepReadPending = false;
        TerminalSession s = session;
        TerminalEmulator emulator = s == null ? null : s.getEmulator();
        if (emulator == null) return;
        X11GpuBenchmarkStep found = X11GpuBenchmarkStep.lastIn(emulator.getScreen().getTranscriptText());
        // A step whose line has scrolled out of the transcript stays the step.
        if (found == null || found.sameAs(step)) return;
        if (step != null && found.number < step.number) return;
        step = found;
        changed();
    }

    private final class SessionClient extends TermuxTerminalSessionClientBase {
        @Override
        public void onTextChanged(@NonNull TerminalSession changed) {
            if (changed != session) return;
            Listener l = listener;
            if (l != null) l.onScreenUpdated();
            if (!stepReadPending) {
                stepReadPending = true;
                handler.postDelayed(X11GpuBenchmarkRun.this::readStep, STEP_READ_MS);
            }
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finished) {
            if (finished != session) return;
            handler.removeCallbacksAndMessages(null);
            readStep();
            int exit = finished.getExitStatus();
            state = state == State.STOPPING ? State.STOPPED : exit == 0 ? State.FINISHED : State.FAILED;
            changed();
            Listener l = listener;
            if (l != null) l.onScreenUpdated();
        }
    }

    // ---- notification ----

    private void updateNotification() {
        NotificationManager manager = NotificationUtils.getNotificationManager(app);
        if (manager == null) return;
        // A page on show says all of it; a run nobody is watching says where it is.
        boolean watched = listener != null;
        if (state == State.IDLE || (watched && !isActive())) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }
        NotificationUtils.setupNotificationChannel(app, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(app.getString(R.string.settings_x11_gpu_run_label))
            .setContentText(statusText(app, state, step))
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        Intent page = pageIntent;
        if (page != null) {
            builder.setContentIntent(PendingIntent.getActivity(app, NOTIFICATION_ID, page,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        if (isActive()) {
            X11GpuBenchmarkStep s = step;
            builder.setOngoing(true).setAutoCancel(false);
            if (s == null) builder.setProgress(0, 0, true);
            else builder.setProgress(s.total, s.number, false);
        } else {
            builder.setOngoing(false).setAutoCancel(true);
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Could not show the benchmark's progress: " + e.getMessage());
        }
    }

    private void cancelNotification() {
        NotificationManager manager = NotificationUtils.getNotificationManager(app);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    /** The one line that says where a run is: its step while it runs, how it ended after. */
    @NonNull
    public static String statusText(@NonNull Context context, @NonNull State state,
                                    @Nullable X11GpuBenchmarkStep step) {
        switch (state) {
            case STOPPING: return context.getString(R.string.settings_x11_gpu_run_stopping);
            case FINISHED: return context.getString(R.string.settings_x11_gpu_run_finished);
            case STOPPED: return context.getString(R.string.settings_x11_gpu_run_stopped);
            case FAILED: return context.getString(R.string.settings_x11_gpu_run_failed);
            default:
                return step == null ? context.getString(R.string.settings_x11_gpu_run_starting)
                    : context.getString(R.string.settings_x11_gpu_run_step, step.number, step.total, step.text);
        }
    }

    // ---- the size estimate ----

    /**
     * {@code termux-x11-gpu-setup --estimate}, run in the Termux environment and bounded by
     * {@link #ESTIMATE_TIMEOUT_MS}. One per confirmation; {@link #cancel} ends it early.
     */
    public static final class Estimate {
        @Nullable private volatile Process process;
        private volatile boolean cancelled;

        @WorkerThread
        @NonNull
        public X11GpuBenchmarkEstimate run(@NonNull Context context, @NonNull String script,
                                           @NonNull String tablePath) {
            if (cancelled) return X11GpuBenchmarkEstimate.NONE;
            Process started = null;
            try {
                String[] argv = TermuxShellUtils.setupShellCommandArguments(script,
                    new String[] {"--estimate", "--profiles=" + tablePath});
                ProcessBuilder builder = new ProcessBuilder(argv)
                    .directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH))
                    .redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                builder.environment().clear();
                builder.environment().putAll(environment(context));
                started = builder.start();
                process = started;
                if (cancelled) return X11GpuBenchmarkEstimate.NONE;
                started.getOutputStream().close();
                Reader reader = new Reader(started.getInputStream());
                reader.start();
                if (!started.waitFor(ESTIMATE_TIMEOUT_MS, TimeUnit.MILLISECONDS) || cancelled) {
                    return X11GpuBenchmarkEstimate.NONE;
                }
                reader.join(2_000L);
                return started.exitValue() == 0 ? X11GpuBenchmarkEstimate.parse(reader.text())
                    : X11GpuBenchmarkEstimate.NONE;
            } catch (IOException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Could not work out the benchmark's sizes: " + e.getMessage());
                return X11GpuBenchmarkEstimate.NONE;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return X11GpuBenchmarkEstimate.NONE;
            } finally {
                if (started != null && started.isAlive()) started.destroy();
                process = null;
            }
        }

        /** Ends a running estimate; its answer is then nothing known. */
        public void cancel() {
            cancelled = true;
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    /** Drains the estimate's output so a chatty script cannot fill the pipe and stall. */
    private static final class Reader extends Thread {
        @NonNull private final InputStream in;
        @NonNull private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Reader(@NonNull InputStream in) {
            super("x11-gpu-estimate");
            setDaemon(true);
            this.in = in;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (out) {
                        if (out.size() < ESTIMATE_MAX_BYTES) out.write(buffer, 0, Math.min(read, ESTIMATE_MAX_BYTES - out.size()));
                    }
                }
            } catch (IOException ignored) {
                // The process was ended.
            }
        }

        @NonNull
        String text() {
            synchronized (out) {
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
