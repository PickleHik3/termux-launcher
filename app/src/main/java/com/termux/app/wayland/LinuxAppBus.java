package com.termux.app.wayland;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.sound.LauncherSound;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The session bus the host gives the Linux apps it runs ({@link LinuxAppChildren}), in place of
 * the launcher's own (LauncherSound's), which is a child of the launcher's process and dies with
 * it: a GTK app's session bus connection ends the app when the bus goes (GLib's exit-on-close),
 * so an app on the launcher's bus died with the launcher even though it ran in the host. This one
 * is the host's child, like the apps, and ends with the host.
 *
 * <p>Started at the first app and kept until the host stops; one that exits is started again by
 * the next app. An app is started on it only once it answers (it prints its address); when
 * {@code dbus-daemon} is missing or the bus does not come up, the app keeps the environment the
 * launcher gave it. A whole desktop drops whatever bus it is given and starts its own.
 *
 * <p>Main thread only, apart from the thread that reads the bus's address and waits on it.
 */
final class LinuxAppBus {

    private static final String LOG_TAG = "TLWayland";

    /** The socket's name, beside the X sockets (a distro sees that directory as {@code /tmp/.X11-unix}). */
    static final String NAME = "termux-launcher-apps-bus";
    static final String SOCKET = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/.X11-unix/" + NAME;
    static final String ADDRESS = "unix:path=" + SOCKET;
    /** The same socket as a distro's login reaches it ({@link LauncherSound#DISTRO_BUS_VARIABLE}). */
    static final String DISTRO_ADDRESS = "unix:path=/tmp/.X11-unix/" + NAME;
    static final String DAEMON = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dbus-daemon";
    static final String CONFIG = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        + "/var/lib/termux-launcher/linux-apps-bus.conf";
    static final String LOG = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/linux-apps-bus.log";
    /** How long the bus has to answer before the apps waiting on it run without it. */
    static final long START_TIMEOUT_MS = 3_000L;

    /** The variables that name a session bus, all replaced by this one's. */
    private static final String[] BUS_VARIABLES = {"DBUS_SESSION_BUS_ADDRESS", "DBUS_SESSION_BUS_PID",
        "DBUS_SESSION_BUS_WINDOWID", LauncherSound.DISTRO_BUS_VARIABLE};

    /** Told on the main thread with the environment to start the app with. */
    interface Ready {
        void run(@Nullable String[] env);
    }

    @NonNull private final Handler main;
    @NonNull private final List<Runnable> waiting = new ArrayList<>();
    @Nullable private Process process;
    private boolean up;
    /** Bumped at each start and stop, so a stale thread's report is ignored. */
    private int generation;

    LinuxAppBus(@NonNull Handler main) {
        this.main = main;
    }

    /** Calls {@code ready} with {@code env} pointed at this bus once it answers, or as given without one. */
    void ensure(@Nullable String[] env, @NonNull Ready ready) {
        if (up) {
            ready.run(withBus(env));
            return;
        }
        if (process == null && !start(env)) {
            ready.run(env);
            return;
        }
        waiting.add(() -> ready.run(up ? withBus(env) : env));
    }

    /** Ends the bus: the host is stopping, after its apps were asked to end. */
    void stop() {
        generation++;
        Process p = process;
        process = null;
        up = false;
        if (p != null) p.destroy();
        runWaiting();
    }

    private boolean start(@Nullable String[] env) {
        if (!new File(DAEMON).canExecute()) return false;
        try {
            File config = new File(CONFIG);
            File dir = config.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("no " + dir);
            java.nio.file.Files.write(config.toPath(),
                LauncherSound.sessionBusConfig(SOCKET).getBytes(StandardCharsets.UTF_8));
            File socket = new File(SOCKET);
            File sockets = socket.getParentFile();
            if (sockets != null && !sockets.isDirectory() && !sockets.mkdirs()) {
                throw new IOException("no " + sockets);
            }
            // A host that was killed leaves its socket; nobody answers on it.
            if (socket.exists() && !socket.delete()) throw new IOException("stale " + socket);
            ProcessBuilder builder = new ProcessBuilder(argv(DAEMON, CONFIG));
            builder.environment().clear();
            for (String entry : withoutBus(env)) {
                int eq = entry.indexOf('=');
                if (eq > 0) builder.environment().put(entry.substring(0, eq), entry.substring(eq + 1));
            }
            builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            builder.redirectError(ProcessBuilder.Redirect.to(new File(LOG)));
            process = builder.start();
        } catch (IOException | RuntimeException e) {
            Log.w(LOG_TAG, "no session bus for Linux apps: " + e);
            process = null;
            return false;
        }
        int started = ++generation;
        Process p = process;
        Runnable giveUp = () -> {
            if (generation != started || up) return;
            Log.w(LOG_TAG, "the Linux apps' session bus did not answer; apps start without it");
            runWaiting();
        };
        main.postDelayed(giveUp, START_TIMEOUT_MS);
        Thread reader = new Thread(() -> {
            String address = null;
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                address = out.readLine();
                main.post(() -> {
                    if (generation != started) return;
                    main.removeCallbacks(giveUp);
                    up = true;
                    runWaiting();
                });
                while (out.readLine() != null) {
                    // It prints its address once; anything more is drained.
                }
            } catch (IOException ignored) {
                // Gone; the wait below says so.
            }
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            boolean answered = address != null;
            main.post(() -> {
                if (generation != started) return;
                Log.w(LOG_TAG, "the Linux apps' session bus ended" + (answered ? "" : " before answering"));
                process = null;
                up = false;
                runWaiting();
            });
        }, "tl-linux-apps-bus");
        reader.setDaemon(true);
        reader.start();
        return true;
    }

    private void runWaiting() {
        if (waiting.isEmpty()) return;
        List<Runnable> now = new ArrayList<>(waiting);
        waiting.clear();
        for (Runnable r : now) r.run();
    }

    /** The bus's command line: in the foreground, so the host waits on it, printing its address once it listens. */
    @NonNull
    static List<String> argv(@NonNull String daemon, @NonNull String config) {
        return Arrays.asList(daemon, "--config-file=" + config, "--nofork", "--nopidfile",
            "--print-address=1");
    }

    /** {@code env} without any session bus variable. Pure. */
    @NonNull
    static List<String> withoutBus(@Nullable String[] env) {
        List<String> out = new ArrayList<>();
        if (env == null) return out;
        for (String entry : env) {
            if (entry == null || isBusVariable(entry)) continue;
            out.add(entry);
        }
        return out;
    }

    /** {@code env} with this bus as the session bus, for the prefix and for a distro's login. Pure. */
    @NonNull
    static String[] withBus(@Nullable String[] env) {
        List<String> out = withoutBus(env);
        out.add("DBUS_SESSION_BUS_ADDRESS=" + ADDRESS);
        out.add(LauncherSound.DISTRO_BUS_VARIABLE + "=" + DISTRO_ADDRESS);
        return out.toArray(new String[0]);
    }

    private static boolean isBusVariable(@NonNull String entry) {
        for (String name : BUS_VARIABLES) {
            if (entry.startsWith(name + "=")) return true;
        }
        return false;
    }
}
