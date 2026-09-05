package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    /** How long a display gets to come up before the tap is given up on. */
    private static final long START_TIMEOUT_MS = 15_000L;
    private static final String LOG_TAG = "X11LinuxAppRunner";

    /** What the runner needs from the activity. */
    public interface Host {
        boolean isDisplayEnabled();
        void turnOnDisplay();
        boolean isDisplayRunning();
        void startDisplay();
        /** Run a shell script as a background task in the prefix. */
        void runScript(@NonNull String script);
        void showDisplayPlace();
        void showNotice(@NonNull String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-linux-app");
        thread.setDaemon(true);
        return thread;
    });

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
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
        handler.removeCallbacks(giveUp);
    }

    private void launch(@NonNull LinuxAppCatalog.LinuxApp app) {
        // The probe may build a GL context the first time; off the main thread, then back.
        EXECUTOR.execute(() -> {
            X11GpuProbe.Result gpu = X11GpuProbe.probe(context);
            String script = script(app, X11DisplayHostController.displayName(), installedEnv(gpu));
            handler.post(() -> {
                Logger.logInfo(LOG_TAG, "Running " + app.id + " on the display");
                host.runScript(script);
            });
        });
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
     * then the desktop file's own command. Pure, so the composition is tested.
     */
    @NonNull
    static String script(@NonNull LinuxAppCatalog.LinuxApp app, @NonNull String display,
                         @NonNull List<String> env) {
        StringBuilder script = new StringBuilder();
        script.append("export DISPLAY=").append(display).append('\n');
        for (String line : env) script.append("export ").append(line).append('\n');
        script.append("cd \"$HOME\"\n");
        script.append("exec ").append(app.exec).append('\n');
        return script.toString();
    }
}
