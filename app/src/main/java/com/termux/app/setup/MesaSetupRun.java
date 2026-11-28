package com.termux.app.setup;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.AppEdition;
import com.termux.app.wayland.MesaWaylandOverlay;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.TermuxShellUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link MesaFirstSetup} on the phone: after the bootstrap settles (every start, cheap when
 * done), off the main thread, it checks the add-on and, when due, installs Mesa and the add-on in a
 * shell of the prefix's own, with a quiet progress notification. Offline it waits for the network
 * to come back; a failed try rests {@link MesaFirstSetup#RETRY_AFTER_MS}. The Setup card reads only
 * what the last check stored, never the package database, so the sheet never waits on a file.
 */
public final class MesaSetupRun {

    private static final String LOG_TAG = "MesaSetupRun";

    /** The overlay's state at the last check ({@link MesaWaylandOverlay.State} name). */
    static final String KEY_OVERLAY = "terl_mesa_setup_overlay";
    /** When the last try failed, or absent. */
    static final String KEY_FAILED_AT = "terl_mesa_setup_failed_at";

    private static final String CHANNEL_ID = "terl_setup";
    private static final int NOTIFICATION_ID = 1440;
    /** The longest a try may run: Mesa and its libraries are large on a slow line. */
    private static final long TIMEOUT_MS = 45L * 60L * 1000L;
    private static final String SCRIPT_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/terl-mesa-setup.sh";
    private static final String LOG_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/terl-mesa-setup.log";

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terl-mesa-setup");
        thread.setDaemon(true);
        return thread;
    });

    private MesaSetupRun() {}

    /** Checks, and installs when due; safe to call on every start. Never blocks the caller. */
    public static void ensure(@NonNull Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (AppEdition.of(app.getPackageName()) != AppEdition.TERMUX) return;
        if (RUNNING.get()) return;
        EXECUTOR.execute(() -> check(app));
    }

    /** What the Setup card says now, from the last check; cheap enough for the main thread. */
    @NonNull
    public static MesaFirstSetup.CardState cardState(@NonNull Context context) {
        AppEdition edition = AppEdition.of(context.getPackageName());
        SharedPreferences store = store(context);
        MesaWaylandOverlay.State overlay = overlayOf(store == null ? null
            : store.getString(KEY_OVERLAY, null));
        long failedAt = store == null ? 0L : store.getLong(KEY_FAILED_AT, 0L);
        // Online and the prefix are taken as ready: the card says "waits" either way when not done.
        MesaFirstSetup.Step step = MesaFirstSetup.next(edition, true, overlay, true,
            System.currentTimeMillis(), failedAt);
        return MesaFirstSetup.card(step, RUNNING.get(), overlay);
    }

    private static void check(@NonNull Context app) {
        MesaWaylandOverlay.Answer answer = MesaWaylandOverlay.check();
        SharedPreferences store = store(app);
        if (store != null) store.edit().putString(KEY_OVERLAY, answer.state.name()).apply();
        long failedAt = store == null ? 0L : store.getLong(KEY_FAILED_AT, 0L);
        boolean online = isOnline(app);
        MesaFirstSetup.Step step = MesaFirstSetup.next(AppEdition.of(app.getPackageName()),
            prefixReady(), answer.state, online, System.currentTimeMillis(), failedAt);
        if (step == MesaFirstSetup.Step.WAIT && !online) watchNetwork(app);
        if (step != MesaFirstSetup.Step.INSTALL || !RUNNING.compareAndSet(false, true)) return;
        try {
            showProgress(app);
            boolean ok = run(app);
            MesaWaylandOverlay.Answer after = MesaWaylandOverlay.check();
            if (store != null) {
                SharedPreferences.Editor edit = store.edit().putString(KEY_OVERLAY, after.state.name());
                // A try that left versions apart rests too: tlstore's next add-on may match.
                if (ok && after.state == MesaWaylandOverlay.State.USE) edit.remove(KEY_FAILED_AT);
                else edit.putLong(KEY_FAILED_AT, System.currentTimeMillis());
                edit.apply();
            }
            Logger.logInfo(LOG_TAG, "Mesa setup " + (ok ? "ran" : "failed") + "; add-on " + after.state);
            if (!ok && !isOnline(app)) watchNetwork(app);
        } finally {
            RUNNING.set(false);
            hideProgress(app);
        }
    }

    /** The prefix's package manager can run: the bootstrap is in. */
    private static boolean prefixReady() {
        File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        return new File(bin, "pkg").canExecute() || new File(bin, "pacman").canExecute();
    }

    /** Runs {@link MesaFirstSetup#SCRIPT}; true when it ended well. */
    private static boolean run(@NonNull Context app) {
        Process process = null;
        try {
            File script = new File(SCRIPT_PATH);
            File parent = script.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            try (FileOutputStream out = new FileOutputStream(script)) {
                out.write(("#!" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh\n"
                    + MesaFirstSetup.SCRIPT).getBytes(StandardCharsets.UTF_8));
            }
            if (!script.setExecutable(true, true)) return false;
            String[] argv = TermuxShellUtils.setupShellCommandArguments(SCRIPT_PATH, null);
            HashMap<String, String> env = new TermuxShellEnvironment().getEnvironment(app, false);
            env.put(UnixShellEnvironment.ENV_PWD, TermuxConstants.TERMUX_HOME_DIR_PATH);
            ShellEnvironmentUtils.createHomeDir(env);
            ProcessBuilder builder = new ProcessBuilder(argv)
                .directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH))
                .redirectErrorStream(true)
                .redirectOutput(new File(LOG_PATH));
            builder.environment().clear();
            builder.environment().putAll(env);
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false;
            return process.exitValue() == 0;
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Mesa setup could not run: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroy();
        }
    }

    private static boolean isOnline(@NonNull Context app) {
        ConnectivityManager connectivity = app.getSystemService(ConnectivityManager.class);
        if (connectivity == null) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            android.net.NetworkInfo info = connectivity.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Tries again once a network comes back; one watch at a time, dropped when it fires. */
    private static void watchNetwork(@NonNull Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        ConnectivityManager connectivity = app.getSystemService(ConnectivityManager.class);
        if (connectivity == null || !WATCHING.compareAndSet(false, true)) return;
        try {
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) {
                    try {
                        connectivity.unregisterNetworkCallback(this);
                    } catch (RuntimeException ignored) {
                        // Already gone.
                    }
                    WATCHING.set(false);
                    ensure(app);
                }
            });
        } catch (RuntimeException e) {
            WATCHING.set(false);
        }
    }

    private static void showProgress(@NonNull Context app) {
        NotificationManager manager = NotificationUtils.getNotificationManager(app);
        if (manager == null) return;
        try {
            NotificationUtils.setupNotificationChannel(app, CHANNEL_ID,
                app.getString(R.string.setup_graphics_channel), NotificationManager.IMPORTANCE_LOW);
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(app, CHANNEL_ID) : new Notification.Builder(app);
            builder.setSmallIcon(R.drawable.ic_service_notification)
                .setContentTitle(app.getString(R.string.setup_graphics_progress_title))
                .setContentText(app.getString(R.string.setup_graphics_progress_text))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException e) {
            // A notice that cannot show must not stop the install.
        }
    }

    private static void hideProgress(@NonNull Context app) {
        NotificationManager manager = NotificationUtils.getNotificationManager(app);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    @Nullable
    private static SharedPreferences store(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    /** A stored state name; never checked means missing (the card says it waits). */
    @NonNull
    static MesaWaylandOverlay.State overlayOf(@Nullable String name) {
        if (name != null) {
            for (MesaWaylandOverlay.State state : MesaWaylandOverlay.State.values())
                if (state.name().equals(name)) return state;
        }
        return MesaWaylandOverlay.State.MISSING;
    }
}
