package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs a Linux app from the app drawer on the display, starting the display first when none is
 * up. This is the on-demand model: nothing runs until an app is asked for, and the app is run
 * with the GPU environment the probe recommends for what is actually installed.
 *
 * <p>A whole desktop ({@link LinuxAppCatalog.LinuxApp#session}) is run the same way and differs in
 * four places, all of them because it takes the display rather than opening a window on it: it is
 * given the environment a session expects and, for the desktops known to start none, a session bus
 * (D6); the launcher's own window manager stands down for it and comes back when it ends (D1);
 * only one runs at a time, and a second asks first (D2); and it is never retried with
 * {@code --no-sandbox}, a flag no desktop has heard of.
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

    /**
     * Where a running desktop leaves its process id, so a later script — the only lever the host
     * gives us over something already running — can find it. In the prefix's temporary directory,
     * which the system empties at boot, because a pid outlives nothing else.
     */
    @VisibleForTesting
    static final String SESSION_PID_PATH =
        TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/termux-x11-session.pid";

    /** The directory a session is given as {@code XDG_RUNTIME_DIR}, under its own world's tmp. */
    private static final String RUNTIME_DIR_NAME = "termux-x11-runtime";

    /** What starts a session bus for a desktop that has none, and takes it down again after. */
    private static final String BUS_WRAPPER = "dbus-launch --exit-with-session";

    /**
     * The desktops known to start no session bus of their own, by the program their {@code Exec}
     * names. XFCE's start script adds one only on its Wayland path, so on X11 it comes up without
     * a bus and does not come up at all — which is why termux-x11's own README spells the wrapper
     * out by hand. This is an allowlist and never a blanket: {@code startlxqt} and
     * {@code mate-session} both launch a bus themselves when there is none, and a second one
     * handed to them is a second bus, not a spare.
     */
    private static final List<String> NO_BUS_OF_THEIR_OWN =
        java.util.Collections.unmodifiableList(Arrays.asList("startxfce4", "xfce4-session"));

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
        /**
         * Ask the user {@code message} — one plain sentence, already built — before a second
         * desktop takes the display from the one running. {@code onYes} runs on the main thread,
         * and only if they say yes; saying no is simply the end of it.
         */
        void askBeforeReplacingSession(@NonNull String message, @NonNull Runnable onYes);
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
    /** The desktop holding the display, from the moment its script starts until it exits. */
    @Nullable private LinuxAppCatalog.LinuxApp runningSession;
    /** The desktop waiting for {@link #runningSession} to be stopped so it can have the display. */
    @Nullable private LinuxAppCatalog.LinuxApp takeOver;
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
        // D2: a desktop takes the whole display, so a second one asks first. Two desktops on one
        // display is nothing X or the desktops themselves refuse — it just leaves two panels and
        // two settings daemons fighting over the same screen — so this is the only thing standing
        // between the user and that mess.
        if (app.session && runningSession != null) {
            if (runningSession.id.equals(app.id)) {
                // Already the desktop on the display: the tap only has to show it.
                host.showDisplayPlace();
                return;
            }
            String message = context.getString(com.termux.R.string.termux_x11_session_replace,
                runningSession.name, app.name);
            host.askBeforeReplacingSession(message, () -> replaceSession(app));
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
        // A desktop on the display outlives this runner — the display does too — but nothing here
        // will hear of its exit any more, so the bookkeeping goes rather than growing stale.
        runningSession = null;
        takeOver = null;
        // Cancels giveUp and any quick-fail watch left over from a launch this runner will never
        // hear the result of.
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * D2, after the user said yes: stop the desktop that has the display, and give it to
     * {@code app} once it is actually gone. The stop script does the waiting — it is the one that
     * can see the process — so this simply runs {@code app} when that script comes back.
     *
     * <p>The stopped desktop's own script will report its exit too, whenever the host gets around
     * to it. Either order is safe: {@link #onSessionExited} only ever acts for the desktop that is
     * still the running one, and while a take-over is in flight it leaves the window manager off,
     * because the desktop arriving next wants the display to itself just as much.
     */
    private void replaceSession(@NonNull LinuxAppCatalog.LinuxApp app) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> replaceSession(app));
            return;
        }
        if (runningSession == null) {
            run(app);
            return;
        }
        Logger.logInfo(LOG_TAG, "Stopping " + runningSession.id + " to make room for " + app.id);
        takeOver = app;
        boolean started = host.runScript(stopSessionScript(), exitCode -> {
            LinuxAppCatalog.LinuxApp next = takeOver;
            takeOver = null;
            runningSession = null;
            if (next != null) run(next);
        });
        if (started) return;
        takeOver = null;
        host.showNotice(context.getString(com.termux.R.string.termux_x11_session_failed));
    }

    /**
     * A desktop's script has exited: the display is its own again. Anything but the desktop that
     * is still the running one is stale — a take-over has already moved on from it — and says
     * nothing about what is on the display now.
     */
    private void onSessionExited(@NonNull LinuxAppCatalog.LinuxApp app, int exitCode,
                                 @Nullable String windowManagerRestart) {
        if (runningSession != app) return;
        runningSession = null;
        Logger.logInfo(LOG_TAG, "Desktop " + app.id + " ended with " + exitCode);
        // D1: the manager that stood down comes back — unless another desktop is on its way in,
        // which would only have to stand it down again.
        if (windowManagerRestart == null || takeOver != null) return;
        host.runScript(windowManagerRestart, ignored -> { });
    }

    /**
     * Builds and runs the app's script. Decides from the remembered store (off the main thread,
     * alongside the GPU probe, so a remembered app costs no more than an ordinary one) whether the
     * first attempt already needs the flag.
     */
    private void launch(@NonNull LinuxAppCatalog.LinuxApp app) {
        // The probe may build a GL context the first time; off the main thread, then back.
        EXECUTOR.execute(() -> {
            // A desktop is never the Electron crash D7 remembers, and --no-sandbox is a flag no
            // desktop has ever heard of, so the whole retry stays out of a session's way.
            boolean noSandbox = !app.session && shouldStartWithNoSandbox(app);
            String display = X11DisplayHostController.displayName();
            List<String> env = installedEnv(X11GpuProbe.probe(context));
            // Reading the setting and looking for the binary are both file work, so they happen
            // here beside the probe rather than on the main thread when the desktop ends.
            String configured = app.session ? configuredWindowManager() : "";
            String stopWm = app.session
                ? X11WindowManager.stopCommand(X11WindowManager.command(configured)) : null;
            String startWm = app.session ? X11WindowManager.startCommand(configured) : null;
            String script = app.session ? sessionScript(app, display, env, stopWm)
                : script(app, display, env, noSandbox);
            String restart = startWm == null ? null : windowManagerScript(startWm, display);
            handler.post(() -> {
                Logger.logInfo(LOG_TAG, "Running " + app.id + " on the display"
                    + (app.session ? " as a desktop" : "")
                    + (noSandbox ? " with --no-sandbox" : ""));
                runAndWatch(app, script, noSandbox, display, env, restart);
            });
        });
    }

    /** The window manager the user has configured, or empty when there is none. */
    @NonNull
    private String configuredWindowManager() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences == null ? "" : preferences.getX11WindowManager();
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
        runAndWatch(app, script, noSandbox, display, env, null);
    }

    /**
     * {@link #runAndWatch(LinuxAppCatalog.LinuxApp, String, boolean, String, List)} carrying what
     * starts the window manager again when a desktop ends (D1). It belongs to this one launch and
     * to nothing else, so it rides along with the watch rather than sitting in a field that a
     * later launch would have to remember to clear.
     */
    @VisibleForTesting
    void runAndWatch(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String script, boolean noSandbox,
                      @NonNull String display, @NonNull List<String> env,
                      @Nullable String windowManagerRestart) {
        new Watch(app, display, env, windowManagerRestart).start(script, noSandbox);
    }

    /**
     * One launch's watch. A fresh instance is also what a retry starts, since it is really a new
     * attempt with its own window, not a continuation of the one that just failed.
     */
    private final class Watch {
        private final LinuxAppCatalog.LinuxApp app;
        private final String display;
        private final List<String> env;
        @Nullable private final String windowManagerRestart;
        private final Runnable timeout = () -> resolve(false);
        private boolean noSandbox;
        private boolean decided;

        Watch(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display,
              @NonNull List<String> env, @Nullable String windowManagerRestart) {
            this.app = app;
            this.display = display;
            this.env = env;
            this.windowManagerRestart = windowManagerRestart;
        }

        void start(@NonNull String script, boolean noSandbox) {
            this.noSandbox = noSandbox;
            boolean started = host.runScript(script, exitCode -> {
                // A desktop's exit is news whenever it comes, not only inside the window: it is
                // what says the display is free again and the window manager can come back.
                if (app.session) onSessionExited(app, exitCode, windowManagerRestart);
                resolve(isQuickFailure(exitCode));
            });
            if (started) {
                if (app.session) runningSession = app;
                handler.postDelayed(timeout, QUICK_FAIL_MS);
                return;
            }
            // No shell to run the app in — the service is not bound, or it refused the task. There
            // is nothing to watch and nothing to learn from it, but a tap that opens no app must
            // never simply do nothing: this is the one path that used to end in silence.
            decided = true;
            Logger.logWarn(LOG_TAG, "No shell to run " + app.id + " in; nothing was started");
            host.showNotice(failureNotice(app));
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
            if (app.session) {
                // A desktop that dies at once mostly died because something already holds the
                // display — exactly D7's shape, and nothing D7's flag can help with. The user is
                // told; nothing is retried and nothing is remembered.
                Logger.logWarn(LOG_TAG, "Desktop " + app.id + " did not start");
                host.showNotice(failureNotice(app));
                return;
            }
            if (noSandbox) {
                sandboxStore.forget(app.id);
                host.showNotice(context.getString(com.termux.R.string.termux_x11_app_launch_failed));
                return;
            }
            Logger.logInfo(LOG_TAG, "Retrying " + app.id + " on the display with --no-sandbox");
            new Watch(app, display, env, null).start(script(app, display, env, true), true);
        }
    }

    /** A process that has already exited with a non-zero code within the quick-fail window. */
    static boolean isQuickFailure(int exitCode) {
        return exitCode != 0;
    }

    /** What a tap that opened nothing says, in the words of the thing it was trying to open. */
    @NonNull
    private String failureNotice(@NonNull LinuxAppCatalog.LinuxApp app) {
        return context.getString(app.session ? com.termux.R.string.termux_x11_session_failed
            : com.termux.R.string.termux_x11_app_launch_failed);
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

    /**
     * The shell line that runs a whole desktop. Everything an app's script does, and then what
     * only a desktop needs:
     * <ul>
     *   <li>the window manager the launcher started stands down (D1), identified by the
     *       configuration file the launcher gave it so that nothing else can be mistaken for it.
     *       {@code stopWindowManager} is null when there is none to stop, or none this launcher
     *       can tell from the user's own.</li>
     *   <li>the shell writes down the process id it is about to become. It {@code exec}s from
     *       here on — into the desktop itself in the prefix, into {@code proot-distro login} for a
     *       container — so that one number is the handle {@link #stopSessionScript} needs later,
     *       and the host gives us no other.</li>
     *   <li>the session's own environment, which has to be set where the desktop will read it:
     *       inside the container for a container's desktop, since no host variable crosses a
     *       login that is not on {@code ProotDistro.FORWARDED_ENV}. That is the whole reason
     *       {@link #sessionBody} is a script of its own rather than more lines up here.</li>
     * </ul>
     * Pure, so the composition is tested; nothing about an ordinary app's script changes.
     */
    @NonNull
    static String sessionScript(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display,
                                @NonNull List<String> env, @Nullable String stopWindowManager) {
        StringBuilder script = new StringBuilder();
        script.append("export DISPLAY=").append(display).append('\n');
        for (String line : TOUCH_ENV) script.append("export ").append(line).append('\n');
        for (String line : env) script.append("export ").append(line).append('\n');
        script.append("cd \"$HOME\"\n");
        if (stopWindowManager != null) script.append(stopWindowManager).append('\n');
        script.append("mkdir -p ")
            .append(ProotDistro.singleQuote(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH))
            .append('\n');
        script.append("echo $$ > ").append(ProotDistro.singleQuote(SESSION_PID_PATH)).append('\n');
        String body = sessionBody(app);
        if (app.container.isPrefix()) script.append(body);
        else script.append("exec ").append(ProotDistro.loginCommand(app.container, body)).append('\n');
        return script.toString();
    }

    /**
     * What a desktop is started with, in its own world — the prefix's or a container's. Three
     * things, none of which anything else in Termux sets:
     * <ul>
     *   <li>{@code XDG_RUNTIME_DIR}, made before it is used. Nothing in a Termux prefix or in a
     *       container sets one, and a desktop's own pieces expect it to be there.</li>
     *   <li>{@code XDG_SESSION_TYPE}, and {@code XDG_CURRENT_DESKTOP} from the session file's own
     *       {@code DesktopNames} when it named any — which is what the key is for.</li>
     *   <li>a session bus, but only for a desktop that starts none itself
     *       ({@link #NO_BUS_OF_THEIR_OWN}), only when there is not one already, and only when
     *       {@code dbus-launch} is actually installed: it lives in Termux's main repository, not
     *       in x11, so it can be missing even where the desktop is not.</li>
     * </ul>
     */
    @NonNull
    static String sessionBody(@NonNull LinuxAppCatalog.LinuxApp app) {
        StringBuilder body = new StringBuilder();
        body.append("export XDG_RUNTIME_DIR=\"${TMPDIR:-/tmp}/").append(RUNTIME_DIR_NAME)
            .append("\"\n");
        body.append("mkdir -p \"$XDG_RUNTIME_DIR\" && chmod 700 \"$XDG_RUNTIME_DIR\"\n");
        body.append("export XDG_SESSION_TYPE=x11\n");
        if (!app.desktopNames.isEmpty()) {
            body.append("export XDG_CURRENT_DESKTOP=")
                .append(ProotDistro.singleQuote(app.desktopNames)).append('\n');
        }
        if (needsOwnBus(app)) {
            body.append("if [ -z \"$DBUS_SESSION_BUS_ADDRESS\" ]")
                .append(" && command -v dbus-launch > /dev/null 2>&1; then\n");
            body.append("exec ").append(BUS_WRAPPER).append(' ').append(app.exec).append('\n');
            body.append("fi\n");
        }
        body.append("exec ").append(app.exec).append('\n');
        return body.toString();
    }

    /** Whether this desktop has to be handed a session bus, by the program its {@code Exec} names. */
    @VisibleForTesting
    static boolean needsOwnBus(@NonNull LinuxAppCatalog.LinuxApp app) {
        if (!app.session) return false;
        String binary = LinuxAppCatalog.execBinary(app.exec);
        int slash = binary.lastIndexOf('/');
        return NO_BUS_OF_THEIR_OWN.contains(slash < 0 ? binary : binary.substring(slash + 1));
    }

    /**
     * The shell line that stops the desktop on the display and waits for it to actually be gone,
     * so whatever runs next is not racing its teardown. The pid is the one
     * {@link #sessionScript}'s shell wrote down before it became the desktop, which is the only
     * handle there is — the host runs scripts and hands back exit codes, and keeps nothing to stop.
     *
     * <p>It asks first and insists afterwards: a desktop told to go closes its own clients, which
     * killing it outright would not, and the wait is bounded so this script always ends.
     */
    @NonNull
    static String stopSessionScript() {
        String pidFile = ProotDistro.singleQuote(SESSION_PID_PATH);
        return "pid=$(cat " + pidFile + " 2>/dev/null)\n"
            + "rm -f " + pidFile + "\n"
            + "[ -n \"$pid\" ] || exit 0\n"
            + "kill \"$pid\" 2>/dev/null\n"
            + "n=0\n"
            + "while [ \"$n\" -lt 40 ] && kill -0 \"$pid\" 2>/dev/null; do\n"
            + "sleep 0.25\n"
            + "n=$((n+1))\n"
            + "done\n"
            + "kill -9 \"$pid\" 2>/dev/null\n"
            + "exit 0\n";
    }

    /**
     * The shell line that starts the window manager again on {@code display}, after a desktop has
     * finished with it (D1). It needs the display named the same way everything else on it does;
     * the server hands its own {@code -xstartup} that variable, and nothing hands it to this.
     */
    @NonNull
    static String windowManagerScript(@NonNull String command, @NonNull String display) {
        return "export DISPLAY=" + display + "\nexec " + command + "\n";
    }
}
