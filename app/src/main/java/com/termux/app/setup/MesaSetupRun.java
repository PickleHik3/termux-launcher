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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link MesaFirstSetup} on the phone. Every start reports whether it installed the
 * bootstrap ({@link #start}); only such a start arms the step, and a final phase (done, given up)
 * is never left, so an app update or a later start never installs anything. Off the main thread it
 * checks the add-on and, when due, installs Mesa and the add-on in a bash of the prefix's own, in a
 * process group of its own, with a progress notification naming the download. Offline it waits for
 * a network to come back; while the setup wizard's own install holds the package manager it checks
 * again shortly. The Setup card reads only what is stored, never the package database.
 */
public final class MesaSetupRun {

    private static final String LOG_TAG = "MesaSetupRun";

    /** The step's {@link MesaFirstSetup.Phase} name; absent for an install never armed. */
    static final String KEY_PHASE = "terl_mesa_setup_phase";
    /** The overlay's state at the last check ({@link MesaWaylandOverlay.State} name). */
    static final String KEY_OVERLAY = "terl_mesa_setup_overlay";
    /** When the last try failed, or absent. */
    static final String KEY_FAILED_AT = "terl_mesa_setup_failed_at";
    /** How many tries failed. */
    static final String KEY_TRIES = "terl_mesa_setup_tries";
    /** What the running try downloads, as apt or pacman put it ("123 MB"), or absent. */
    static final String KEY_SIZE = "terl_mesa_setup_size";

    private static final String CHANNEL_ID = "terl_setup";
    private static final int NOTIFICATION_ID = 1440;
    /** The longest a try may run: Mesa and its libraries are large on a slow line. */
    private static final long TIMEOUT_MS = 45L * 60L * 1000L;
    /** How often a running try's output is read for its download size. */
    private static final long POLL_MS = 3000L;
    /** How soon to look again while the wizard's install holds the package manager. */
    private static final long BUSY_RECHECK_MS = 20_000L;
    private static final String TMP = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
    private static final String SCRIPT_PATH = TMP + "/terl-mesa-setup.sh";
    private static final String LOG_PATH = TMP + "/terl-mesa-setup.log";
    private static final String PID_PATH = TMP + "/terl-mesa-setup.pid";
    /** Android's setsid: the run gets a process group of its own, which a timeout ends whole. */
    private static final String SETSID = "/system/bin/setsid";

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean WATCHING = new AtomicBoolean(false);
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terl-mesa-setup");
        thread.setDaemon(true);
        return thread;
    });

    private MesaSetupRun() {}

    /**
     * A launcher start: arms the step when this start installed the bootstrap
     * ({@code installedBootstrapNow}), then checks. Cheap and silent for every install not armed.
     */
    public static void start(@NonNull Context context, boolean installedBootstrapNow) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (AppEdition.of(app.getPackageName()) != AppEdition.TERMUX) return;
        EXECUTOR.execute(() -> {
            SharedPreferences store = store(app);
            if (store == null) return;
            MesaFirstSetup.Phase stored = MesaFirstSetup.phaseOf(store.getString(KEY_PHASE, null));
            MesaFirstSetup.Phase phase = MesaFirstSetup.onStart(stored, installedBootstrapNow);
            if (phase != stored) store.edit().putString(KEY_PHASE, phase.name()).commit();
            if (phase == MesaFirstSetup.Phase.PENDING) check(app);
        });
    }

    /** What the Setup card says now, from what is stored; cheap enough for the main thread. */
    @NonNull
    public static MesaFirstSetup.CardState cardState(@NonNull Context context) {
        SharedPreferences store = store(context);
        return MesaFirstSetup.card(AppEdition.of(context.getPackageName()),
            MesaFirstSetup.phaseOf(store == null ? null : store.getString(KEY_PHASE, null)),
            RUNNING.get(), overlayOf(store == null ? null : store.getString(KEY_OVERLAY, null)));
    }

    /** What the running try downloads ("123 MB"), when its output said; null otherwise. */
    @Nullable
    public static String downloadSize(@NonNull Context context) {
        if (!RUNNING.get()) return null;
        SharedPreferences store = store(context);
        return store == null ? null : store.getString(KEY_SIZE, null);
    }

    private static void recheck(@NonNull Context app) {
        if (!RUNNING.get()) EXECUTOR.execute(() -> check(app));
    }

    private static void check(@NonNull Context app) {
        SharedPreferences store = store(app);
        if (store == null || RUNNING.get()) return;
        MesaFirstSetup.Phase phase = MesaFirstSetup.phaseOf(store.getString(KEY_PHASE, null));
        if (phase != MesaFirstSetup.Phase.PENDING) return;
        MesaWaylandOverlay.Answer answer = MesaWaylandOverlay.check();
        store.edit().putString(KEY_OVERLAY, answer.state.name()).apply();
        boolean online = isOnline(app);
        boolean busy = SetupShellRun.isBusy();
        MesaFirstSetup.Step step = MesaFirstSetup.next(AppEdition.of(app.getPackageName()), phase,
            prefixReady(), busy, answer.state, online, System.currentTimeMillis(),
            store.getLong(KEY_FAILED_AT, 0L));
        switch (step) {
            case DONE:
                store.edit().putString(KEY_PHASE, MesaFirstSetup.Phase.DONE.name()).apply();
                return;
            case WAIT:
                if (!online) watchNetwork(app);
                else if (busy) EXECUTOR.schedule(() -> check(app), BUSY_RECHECK_MS, TimeUnit.MILLISECONDS);
                return;
            case INSTALL:
                break;
            default:
                return;
        }
        if (!RUNNING.compareAndSet(false, true)) return;
        try {
            store.edit().remove(KEY_SIZE).apply();
            showProgress(app, null);
            boolean ok = run(app, store);
            MesaWaylandOverlay.Answer after = MesaWaylandOverlay.check();
            int tries = store.getInt(KEY_TRIES, 0) + (ok && after.state == MesaWaylandOverlay.State.USE ? 0 : 1);
            MesaFirstSetup.Phase next = MesaFirstSetup.afterTry(ok, after.state, tries);
            SharedPreferences.Editor edit = store.edit()
                .putString(KEY_OVERLAY, after.state.name())
                .putString(KEY_PHASE, next.name())
                .putInt(KEY_TRIES, tries)
                .remove(KEY_SIZE);
            // A try that left versions apart rests too: tlstore's next add-on may match.
            if (next == MesaFirstSetup.Phase.PENDING) edit.putLong(KEY_FAILED_AT, System.currentTimeMillis());
            else edit.remove(KEY_FAILED_AT);
            edit.commit();
            Logger.logInfo(LOG_TAG, "Mesa setup " + (ok ? "ran" : "failed") + "; add-on " + after.state
                + "; now " + next);
            if (next == MesaFirstSetup.Phase.PENDING && !isOnline(app)) watchNetwork(app);
        } finally {
            RUNNING.set(false);
            hideProgress(app);
        }
    }

    /** The prefix's package manager can run: the bootstrap is in. */
    private static boolean prefixReady() {
        File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
        return new File(bin, "bash").canExecute()
            && (new File(bin, "pkg").canExecute() || new File(bin, "pacman").canExecute());
    }

    /** Runs {@link MesaFirstSetup#script}; true when it ended well. */
    private static boolean run(@NonNull Context app, @NonNull SharedPreferences store) {
        Process process = null;
        File pidFile = new File(PID_PATH);
        try {
            File script = new File(SCRIPT_PATH);
            File parent = script.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            if (pidFile.exists() && !pidFile.delete()) return false;
            try (FileOutputStream out = new FileOutputStream(script)) {
                out.write(("#!" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash\n"
                    + MesaFirstSetup.script(PID_PATH)).getBytes(StandardCharsets.UTF_8));
            }
            if (!script.setExecutable(true, true)) return false;
            List<String> argv = new ArrayList<>();
            if (new File(SETSID).canExecute()) argv.add(SETSID);
            argv.addAll(Arrays.asList(TermuxShellUtils.setupShellCommandArguments(SCRIPT_PATH, null)));
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
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            String shownSize = null;
            while (!process.waitFor(POLL_MS, TimeUnit.MILLISECONDS)) {
                String size = MesaFirstSetup.downloadSize(tail(new File(LOG_PATH)));
                if (size != null && !size.equals(shownSize)) {
                    shownSize = size;
                    store.edit().putString(KEY_SIZE, size).apply();
                    showProgress(app, size);
                }
                if (System.currentTimeMillis() > deadline) {
                    killGroup(pidFile);
                    return false;
                }
            }
            return process.exitValue() == 0;
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Mesa setup could not run: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killGroup(pidFile);
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroy();
        }
    }

    /** Ends the run's whole process group (apt and dpkg with it), so no lock outlives it. */
    private static void killGroup(@NonNull File pidFile) {
        String text = null;
        try {
            text = new String(Files.readAllBytes(pidFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // No pid file: the destroy in run's finally ends the shell alone.
        }
        int group = MesaFirstSetup.groupToKill(text);
        if (group == 0) return;
        try {
            android.system.Os.kill(group, android.system.OsConstants.SIGKILL);
        } catch (android.system.ErrnoException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Could not end the Mesa setup's processes: " + e.getMessage());
        }
    }

    /** The last few kilobytes of {@code file}, or null. */
    @Nullable
    private static String tail(@NonNull File file) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            int size = (int) Math.min(length, 16 * 1024);
            byte[] bytes = new byte[size];
            in.seek(length - size);
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Some network reaches the internet: any kind, metered or not (the developer's decision). */
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
                    recheck(app);
                }
            });
        } catch (RuntimeException e) {
            WATCHING.set(false);
        }
    }

    private static void showProgress(@NonNull Context app, @Nullable String size) {
        NotificationManager manager = NotificationUtils.getNotificationManager(app);
        if (manager == null) return;
        try {
            NotificationUtils.setupNotificationChannel(app, CHANNEL_ID,
                app.getString(R.string.setup_graphics_channel), NotificationManager.IMPORTANCE_LOW);
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(app, CHANNEL_ID) : new Notification.Builder(app);
            builder.setSmallIcon(R.drawable.ic_service_notification)
                .setContentTitle(app.getString(R.string.setup_graphics_progress_title))
                .setContentText(size == null ? app.getString(R.string.setup_graphics_progress_text)
                    : app.getString(R.string.setup_graphics_progress_text_size, size))
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

    /** A stored state name; never checked means missing. */
    @NonNull
    static MesaWaylandOverlay.State overlayOf(@Nullable String name) {
        if (name != null) {
            for (MesaWaylandOverlay.State state : MesaWaylandOverlay.State.values())
                if (state.name().equals(name)) return state;
        }
        return MesaWaylandOverlay.State.MISSING;
    }
}
