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
import com.termux.app.x11.DesktopsPage.RunKind;
import com.termux.app.x11.DesktopsPage.RunPhase;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * The one desktop install or removal run, owned by the process rather than by the Desktops page: the install
 * script ({@link DesktopInstallScript}) in a headless {@link TerminalSession}, the way the
 * graphics benchmark ({@link X11GpuBenchmarkRun}) and the Setup card's install run. It is not one
 * of the user's sessions: it never reaches the service's shell manager, so it shows as no pane or
 * tab. The page attaches while shown and the run carries on without it; the transcript stays until
 * {@link #dismiss()} or the next run. A run is an install ({@link #start}) or a removal
 * ({@link #startRemoval}, {@link DesktopRemoveScript}); only one of either goes at a time.
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
    @NonNull private RunKind kind = RunKind.INSTALL;
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
    /** Whether the run installs or removes. */
    @NonNull public RunKind kind() { return kind; }
    /** The id of the desktop the run is, or was last, installing; null when idle. */
    @Nullable public String desktopId() { return desktop == null ? null : desktop.id; }
    /** The run's terminal session, for a view to show; null while idle or before it started. */
    @Nullable public TerminalSession session() { return session; }

    /** Where the script is written, in the prefix's tmp. */
    @NonNull
    static String scriptPath(@NonNull DesktopCatalogue.Desktop desktop, @NonNull RunKind kind) {
        return TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/desktop-"
            + (kind == RunKind.REMOVE ? "remove-" : "install-") + desktop.id + ".sh";
    }

    /**
     * Starts installing {@code desktop}: false when a run is going. The script is written off the
     * main thread; the phase is Running from this call on, so a second tap finds the page busy.
     */
    @MainThread
    public boolean start(@NonNull DesktopCatalogue.Desktop desktop) {
        return begin(desktop, RunKind.INSTALL, false);
    }

    /**
     * Starts removing {@code desktop}, with its settings folders when {@code alsoSettings}: false
     * when a run is going. The packages to remove are worked out off the main thread.
     */
    @MainThread
    public boolean startRemoval(@NonNull DesktopCatalogue.Desktop desktop, boolean alsoSettings) {
        return begin(desktop, RunKind.REMOVE, alsoSettings);
    }

    @MainThread
    private boolean begin(@NonNull DesktopCatalogue.Desktop desktop, @NonNull RunKind runKind,
                          boolean alsoSettings) {
        if (isActive()) return false;
        release();
        this.desktop = desktop;
        this.kind = runKind;
        phase = RunPhase.RUNNING;
        changed();
        new Thread(() -> {
            X11GpuBenchmarkRun.Launch launch = null;
            try {
                launch = prepare(desktop, runKind, alsoSettings);
            } catch (IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The install could not be prepared", e);
            }
            X11GpuBenchmarkRun.Launch prepared = launch;
            handler.post(() -> launched(desktop, prepared));
        }, "desktop-install-prepare").start();
        return true;
    }

    /** Writes the script and resolves the shell that runs it, as a terminal session would. File work. */
    @WorkerThread
    @NonNull
    private X11GpuBenchmarkRun.Launch prepare(@NonNull DesktopCatalogue.Desktop desktop,
                                              @NonNull RunKind runKind, boolean alsoSettings) throws IOException {
        String path = scriptPath(desktop, runKind);
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        String text = runKind == RunKind.REMOVE
            ? DesktopRemoveScript.script(desktop, removalPlan(desktop), alsoSettings)
            : DesktopInstallScript.script(desktop,
                com.termux.app.wayland.LinuxDisplayScale.current(app));
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return X11GpuBenchmarkRun.prepare(app, path);
    }

    /** The packages {@link DesktopRemoveScript} takes, from the manifests and files on disk. File work. */
    @WorkerThread
    @NonNull
    private static List<String> removalPlan(@NonNull DesktopCatalogue.Desktop desktop) {
        Set<String> claimed = DesktopRemoveScript.claimed(desktop,
            DesktopInstallRun::readManifest,
            other -> other.state(DesktopInstallRun::inPrefix, file -> false)
                == DesktopCatalogue.State.INSTALLED,
            GuiAppsSetup.termuxSetupPackages());
        return DesktopRemoveScript.packagesToRemove(desktop, readManifest(desktop), claimed);
    }

    private static boolean inPrefix(@NonNull String file) {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, file).exists();
    }

    /** The desktop's manifest, or null when no install through the page left one. */
    @Nullable
    private static List<String> readManifest(@NonNull DesktopCatalogue.Desktop desktop) {
        File file = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/" + DesktopCatalogue.MANIFEST_DIR,
            DesktopCatalogue.manifestFileName(desktop));
        if (!file.isFile()) return null;
        try {
            return DesktopRemoveScript.parseManifest(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "The manifest could not be read", e);
            return null;
        }
    }

    @MainThread
    private void launched(@NonNull DesktopCatalogue.Desktop started, @Nullable X11GpuBenchmarkRun.Launch launch) {
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
            new File(scriptPath(desktop, kind)).delete();
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
            boolean present = done.state(DesktopInstallRun::inPrefix, file -> false)
                == DesktopCatalogue.State.INSTALLED;
            int exit = finished.getExitStatus();
            boolean ok = kind == RunKind.REMOVE
                ? DesktopRemoveScript.succeeded(exit, present)
                : DesktopInstallScript.succeeded(exit, present);
            phase = ok ? RunPhase.FINISHED : RunPhase.FAILED;
            //noinspection ResultOfMethodCallIgnored
            new File(scriptPath(done, kind)).delete();
            DesktopInstallRun.this.changed();
        }
    }
}
