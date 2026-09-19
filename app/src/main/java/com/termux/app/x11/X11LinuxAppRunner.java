package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs a Linux app from the app drawer on the display, starting the display first when none is
 * up. This is the on-demand model: nothing runs until an app is asked for, and the app is run
 * with the GPU environment the probe recommends for what is actually installed.
 */
public final class X11LinuxAppRunner {

    /**
     * What a toolkit needs before a finger on the display reaches it as a touch rather than as a
     * mouse. Firefox reads its X11 input through XInput2 only when asked to, and without that a
     * page cannot be scrolled or pinched with a finger at all.
     */
    static final List<String> TOUCH_ENV = java.util.Collections.singletonList("MOZ_USE_XINPUT2=1");

    /** How long a display gets to come up before the tap is given up on. */
    private static final long START_TIMEOUT_MS = 15_000L;
    /**
     * How long a plainly-started container app gets before an exit with no window is read as the
     * Electron sandbox crash (D7) rather than a slow start. Reproduced on a phone, the crash is
     * immediate — a fork failing inside the sandbox setup, not a toolkit taking its time — so this
     * only needs headroom for {@code proot-distro login} and a shell to fork the app at all, not
     * for the app itself to finish starting. A false "quick failure" here just costs one needless
     * retry with a flag the app did not need; a threshold too short would instead call a slow but
     * healthy start a crash.
     */
    @VisibleForTesting static final long QUICK_FAIL_MS = 3_000L;
    private static final String LOG_TAG = "X11LinuxAppRunner";

    /** What the runner needs from the activity. */
    public interface Host {
        boolean isDisplayEnabled();
        void turnOnDisplay();
        boolean isDisplayRunning();
        void startDisplay();
        /**
         * Run a shell script as a background task in the prefix. Returns whether the task actually
         * started; when it did, {@code onExit} is called exactly once, later, on the main thread,
         * with the process's exit code — pushed across from wherever the host already learns it
         * with a genuine happens-before edge over the thread that set it, never left to be polled
         * from a bare field afterwards.
         */
        boolean runScript(@NonNull String script, @NonNull ScriptExitListener onExit);
        void showDisplayPlace();
        void showNotice(@NonNull String message);
    }

    /** Told a background script task's exit code, on the main thread, once it has one. */
    public interface ScriptExitListener {
        void onExit(int exitCode);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-linux-app");
        thread.setDaemon(true);
        return thread;
    });

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @NonNull private final X11ElectronSandboxStore sandboxStore;
    @Nullable private LinuxAppCatalog.LinuxApp pending;
    private final Runnable giveUp = this::giveUp;

    private void giveUp() {
        if (pending == null) return;
        pending = null;
        host.showNotice(context.getString(com.termux.R.string.termux_x11_app_display_failed));
    }

    public X11LinuxAppRunner(@NonNull Context context, @NonNull Host host) {
        this.context = context.getApplicationContext();
        this.host = host;
        this.sandboxStore = new X11ElectronSandboxStore(this.context);
    }

    /**
     * The drawer tapped {@code app}, or launcherctl asked for it. Always continues on the main
     * thread: the host shows the Display place and reads the controller, both of which are the
     * activity's, and the API server calls in from a worker.
     */
    public void run(@NonNull LinuxAppCatalog.LinuxApp app) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> run(app));
            return;
        }
        if (!host.isDisplayEnabled()) host.turnOnDisplay();
        host.showDisplayPlace();
        if (host.isDisplayRunning()) {
            launch(app);
            return;
        }
        // The server needs a moment; the app runs when the controller says the display is up.
        pending = app;
        handler.removeCallbacks(giveUp);
        handler.postDelayed(giveUp, START_TIMEOUT_MS);
        host.startDisplay();
    }

    /** The controller's running state changed. */
    public void onDisplayRunningChanged(boolean running) {
        if (!running || pending == null) return;
        LinuxAppCatalog.LinuxApp app = pending;
        pending = null;
        handler.removeCallbacks(giveUp);
        launch(app);
    }

    public void destroy() {
        pending = null;
        // Cancels giveUp and any quick-fail watch left over from a launch this runner will never
        // hear the result of.
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Builds and runs the app's script. Decides from the remembered store (off the main thread,
     * alongside the GPU probe, so a remembered app costs no more than an ordinary one) whether the
     * first attempt already needs the flag.
     */
    private void launch(@NonNull LinuxAppCatalog.LinuxApp app) {
        // The probe may build a GL context the first time; off the main thread, then back.
        EXECUTOR.execute(() -> {
            boolean noSandbox = shouldStartWithNoSandbox(app);
            String display = X11DisplayHostController.displayName();
            List<String> env = installedEnv(X11GpuProbe.probe(context));
            String script = script(app, display, env, noSandbox);
            handler.post(() -> {
                Logger.logInfo(LOG_TAG, "Running " + app.id + " on the display"
                    + (noSandbox ? " with --no-sandbox" : ""));
                runAndWatch(app, script, noSandbox, display, env);
            });
        });
    }

    /** Whether a first attempt at {@code app} should start with the flag already, from D7's memory. */
    @VisibleForTesting
    boolean shouldStartWithNoSandbox(@NonNull LinuxAppCatalog.LinuxApp app) {
        return sandboxStore.needsNoSandbox(app.id);
    }

    /**
     * Runs {@code script} and watches for D7's quick-fail window. Nothing here holds up the app
     * itself: {@link Host#runScript} starts the task asynchronously and returns immediately, the
     * same as it always did, and the script's own last line simply {@code exec}s into the app —
     * this only reacts, afterwards, to whichever of two things happens first, both on the main
     * thread so a plain flag on {@link Watch} is all that is needed to let only one of them act:
     * the process exits (pushed straight from {@link ScriptExitListener}, never read back off a
     * field), or the window elapses with no word of that, which is read as success.
     */
    @VisibleForTesting
    void runAndWatch(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String script, boolean noSandbox,
                      @NonNull String display, @NonNull List<String> env) {
        new Watch(app, display, env).start(script, noSandbox);
    }

    /**
     * One launch's watch. A fresh instance is also what a retry starts, since it is really a new
     * attempt with its own window, not a continuation of the one that just failed.
     */
    private final class Watch {
        private final LinuxAppCatalog.LinuxApp app;
        private final String display;
        private final List<String> env;
        private final Runnable timeout = () -> resolve(false);
        private boolean noSandbox;
        private boolean decided;

        Watch(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display, @NonNull List<String> env) {
            this.app = app;
            this.display = display;
            this.env = env;
        }

        void start(@NonNull String script, boolean noSandbox) {
            this.noSandbox = noSandbox;
            boolean started = host.runScript(script, exitCode -> resolve(isQuickFailure(exitCode)));
            if (started) handler.postDelayed(timeout, QUICK_FAIL_MS);
        }

        /**
         * {@code quickFailure} is what actually happened, decided by whichever of the exit callback
         * or {@link #timeout} got here first — the other is cancelled, or simply finds
         * {@link #decided} already set and does nothing. Both read {@link #noSandbox} as it was set
         * by {@link #start}, before either could possibly fire.
         * <ul>
         *   <li>not a quick failure: if this attempt was flagged, remember it (harmless to repeat if
         *       it already was).</li>
         *   <li>quick failure, plain: one retry with the flag, from the same {@code display}/
         *       {@code env} — nothing about the app or the device changed in the last few seconds,
         *       so there is nothing left to probe again.</li>
         *   <li>quick failure, flagged: the flag is not helping (or never did for a remembered app),
         *       so forget it and tell the user, the same way a display that never came up does.</li>
         * </ul>
         */
        private void resolve(boolean quickFailure) {
            if (decided) return;
            decided = true;
            handler.removeCallbacks(timeout);
            if (!quickFailure) {
                if (noSandbox) sandboxStore.remember(app.id);
                return;
            }
            if (noSandbox) {
                sandboxStore.forget(app.id);
                host.showNotice(context.getString(com.termux.R.string.termux_x11_app_launch_failed));
                return;
            }
            Logger.logInfo(LOG_TAG, "Retrying " + app.id + " on the display with --no-sandbox");
            new Watch(app, display, env).start(script(app, display, env, true), true);
        }
    }

    /** A process that has already exited with a non-zero code within the quick-fail window. */
    static boolean isQuickFailure(int exitCode) {
        return exitCode != 0;
    }

    /** The best profile whose packages are actually installed; nothing when none is. */
    @NonNull
    static List<String> installedEnv(@NonNull X11GpuProbe.Result gpu) {
        for (X11GpuProbe.Recommendation r : gpu.ranked) {
            if (r.installed && r.profile != X11GpuProbe.Profile.SOFTWARE) return r.env;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * The shell line that runs the app: the display, the GPU environment, the home directory,
     * then the app's command — its own, or a {@code proot-distro login} around it when the app is
     * installed in a container. The touch environment goes in first so a GPU profile still has the
     * last word over anything it sets. Pure, so the composition is tested.
     */
    @NonNull
    static String script(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display,
                         @NonNull List<String> env) {
        return script(app, display, env, false);
    }

    /**
     * {@link #script(LinuxAppCatalog.LinuxApp, String, List)} with {@code noSandbox} choosing
     * between the app's own command and D7's retry, {@code app.commandWith("--no-sandbox")}.
     */
    @NonNull
    static String script(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display,
                         @NonNull List<String> env, boolean noSandbox) {
        StringBuilder script = new StringBuilder();
        script.append("export DISPLAY=").append(display).append('\n');
        for (String line : TOUCH_ENV) script.append("export ").append(line).append('\n');
        for (String line : env) script.append("export ").append(line).append('\n');
        script.append("cd \"$HOME\"\n");
        script.append("exec ").append(noSandbox ? app.commandWith("--no-sandbox") : app.command())
            .append('\n');
        return script.toString();
    }
}
