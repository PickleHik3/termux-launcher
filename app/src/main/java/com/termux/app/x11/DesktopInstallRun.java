package com.termux.app.x11;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.app.fragments.settings.termux.WatchOnlyTerminal;
import com.termux.app.x11.DesktopsPage.RunPhase;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The one desktop install run, owned by the process rather than by the Desktops page: the install
 * script ({@link DesktopInstallScript}) in a headless {@link TerminalSession}, the way the
 * graphics benchmark ({@link X11GpuBenchmarkRun}) and the Setup card's install run. It is not one
 * of the user's sessions: it never reaches the service's shell manager, so it shows as no pane or
 * tab. The page attaches while shown and the run carries on without it; the transcript stays until
 * {@link #dismiss()} or the next run.
 *
 * <p>The run takes no input. Main thread only, apart from the script write in {@link #start}'s
 * worker.
 */
public final class DesktopInstallRun {
    /** The page's side: the screen changed, or the phase did. */
    public interface Listener {
        void onScreenUpdated();
        void onRunChanged();
    }

    private static final int START_COLUMNS = 80;
    private static final int START_ROWS = 24;
    private static final int START_CELL_WIDTH = 10;
    private static final int START_CELL_HEIGHT = 20;
    private static final String LOG_TAG = "DesktopInstallRun";

    @SuppressLint("StaticFieldLeak") // the application context only
    @Nullable private static DesktopInstallRun instance;

    @NonNull private final Context app;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private DesktopCatalogue.Desktop desktop;
    @Nullable private TerminalSession session;
    @NonNull private RunPhase phase = RunPhase.IDLE;
    @Nullable private Listener listener;

    private DesktopInstallRun(@NonNull Context app) {
        this.app = app;
    }

    @MainThread
    @NonNull
    public static DesktopInstallRun get(@NonNull Context context) {
        if (instance == null) instance = new DesktopInstallRun(context.getApplicationContext());
        return instance;
    }

    @NonNull public RunPhase phase() { return phase; }
    public boolean isActive() { return phase == RunPhase.RUNNING; }
    /** The id of the desktop the run is, or was last, installing; null when idle. */
    @Nullable public String desktopId() { return desktop == null ? null : desktop.id; }
    /** The run's terminal session, for a view to show; null while idle or before it started. */
    @Nullable public TerminalSession session() { return session; }

    /** Where the script is written, in the prefix's tmp. */
    @NonNull
    static String scriptPath(@NonNull DesktopCatalogue.Desktop desktop) {
        return TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/desktop-install-" + desktop.id + ".sh";
    }

    /**
     * Starts installing {@code desktop}: false when a run is going. The script is written off the
     * main thread; the phase is Running from this call on, so a second tap finds the page busy.
     */
    @MainThread
    public boolean start(@NonNull DesktopCatalogue.Desktop desktop) {
        if (isActive()) return false;
        release();
        this.desktop = desktop;
        phase = RunPhase.RUNNING;
        changed();
        new Thread(() -> {
            X11GpuBenchmarkRun.Launch launch = null;
            try {
                launch = prepare(desktop);
            } catch (IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The install could not be prepared", e);
            }
            X11GpuBenchmarkRun.Launch prepared = launch;
            handler.post(() -> begin(desktop, prepared));
        }, "desktop-install-prepare").start();
        return true;
    }

    /** Writes the script and resolves the shell that runs it, as a terminal session would. File work. */
    @WorkerThread
    @NonNull
    private X11GpuBenchmarkRun.Launch prepare(@NonNull DesktopCatalogue.Desktop desktop) throws IOException {
        String path = scriptPath(desktop);
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        Files.write(file.toPath(), DesktopInstallScript.script(desktop).getBytes(StandardCharsets.UTF_8));
        return X11GpuBenchmarkRun.prepare(app, path);
    }

    @MainThread
    private void begin(@NonNull DesktopCatalogue.Desktop started, @Nullable X11GpuBenchmarkRun.Launch launch) {
        if (desktop != started || phase != RunPhase.RUNNING) return;
        if (launch == null) {
            phase = RunPhase.FAILED;
            changed();
            return;
        }
        TerminalSession created = new TerminalSession(launch.executable,
            TermuxConstants.TERMUX_HOME_DIR_PATH, launch.arguments, launch.environment,
            WatchOnlyTerminal.TRANSCRIPT_ROWS, new SessionClient());
        // The row says how the run ended; the session takes no Enter.
        created.mShowsExitLine = false;
        session = created;
        try {
            // A session's process starts when it is first given a size; no view has one yet.
            created.updateSize(START_COLUMNS, START_ROWS, START_CELL_WIDTH, START_CELL_HEIGHT);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The install could not be started", e);
            session = null;
            phase = RunPhase.FAILED;
        }
        changed();
    }

    /** Puts a finished run away: its transcript goes. */
    @MainThread
    public void dismiss() {
        if (isActive()) return;
        release();
        phase = RunPhase.IDLE;
        desktop = null;
        changed();
    }

    @MainThread
    public void attach(@NonNull Listener page) {
        listener = page;
    }

    @MainThread
    public void detach(@NonNull Listener page) {
        if (listener == page) listener = null;
    }

    private void release() {
        TerminalSession old = session;
        session = null;
        if (old != null && old.isRunning()) old.finishIfRunning();
        if (desktop != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(scriptPath(desktop)).delete();
        }
    }

    private void changed() {
        Listener l = listener;
        if (l != null) l.onRunChanged();
    }

    private final class SessionClient extends TermuxTerminalSessionClientBase {
        @Override
        public void onTextChanged(@NonNull TerminalSession changed) {
            Listener l = listener;
            if (changed == session && l != null) l.onScreenUpdated();
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finished) {
            DesktopCatalogue.Desktop done = desktop;
            if (finished != session || done == null) return;
            boolean present = done.state(
                file -> new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, file).exists(), file -> false)
                == DesktopCatalogue.State.INSTALLED;
            phase = DesktopInstallScript.succeeded(finished.getExitStatus(), present)
                ? RunPhase.FINISHED : RunPhase.FAILED;
            //noinspection ResultOfMethodCallIgnored
            new File(scriptPath(done)).delete();
            DesktopInstallRun.this.changed();
        }
    }
}
