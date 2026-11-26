package com.termux.app.wayland;

import android.os.Handler;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.shell.ShellUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * The Linux apps the host runs for the launcher ({@link ILinuxWindows#runApp}), in the host's
 * process ({@code :linuxwindows}). They are its children, so Android ends them with it, and not
 * the launcher's, so they live on when the launcher's process dies (a crash, an install, a kill):
 * Android ends every process a dying app process started, and the host's survives the launcher's.
 *
 * <p>Main thread only, apart from one waiting thread per child that posts its exit back.
 */
final class LinuxAppChildren {

    private static final String LOG_TAG = "TLWayland";

    /** Told on the main thread. */
    interface Listener {
        void started(long token, @NonNull String tag, int pid);

        void exited(long token, @NonNull String tag, int exitCode);
    }

    /** What a live child is, for replay. */
    static final class Child {
        @NonNull final String tag;
        final int pid;

        Child(@NonNull String tag, int pid) {
            this.tag = tag;
            this.pid = pid;
        }
    }

    @NonNull private final Handler main;
    @NonNull private final Listener listener;
    @NonNull private final LongSparseArray<Child> live = new LongSparseArray<>();

    LinuxAppChildren(@NonNull Handler main, @NonNull Listener listener) {
        this.main = main;
        this.listener = listener;
    }

    /** Starts one app; its start and exit go to the listener (an exit of -1 when it cannot start). */
    void run(long token, @Nullable String tag, @Nullable String[] argv, @Nullable String[] env,
             @Nullable String cwd, @Nullable String logPath) {
        String name = tag == null ? "" : tag;
        if (argv == null || argv.length == 0 || live.get(token) != null) {
            listener.exited(token, name, -1);
            return;
        }
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(Arrays.asList(argv));
            builder.environment().clear();
            if (env != null) {
                for (String entry : env) {
                    int eq = entry == null ? -1 : entry.indexOf('=');
                    if (eq > 0) builder.environment().put(entry.substring(0, eq), entry.substring(eq + 1));
                }
            }
            if (cwd != null && new File(cwd).isDirectory()) builder.directory(new File(cwd));
            builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            File log = logPath == null ? new File("/dev/null") : new File(logPath);
            builder.redirectOutput(ProcessBuilder.Redirect.to(log));
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException | RuntimeException e) {
            Log.w(LOG_TAG, "cannot run " + name + ": " + e);
            listener.exited(token, name, -1);
            return;
        }
        int pid = ShellUtils.getPid(process);
        live.put(token, new Child(name, pid));
        listener.started(token, name, pid);
        Thread waiter = new Thread(() -> {
            int code;
            try {
                code = process.waitFor();
            } catch (InterruptedException e) {
                code = -1;
            }
            int exitCode = code;
            main.post(() -> {
                live.remove(token);
                listener.exited(token, name, exitCode);
            });
        }, "tl-linux-app-" + pid);
        waiter.setDaemon(true);
        waiter.start();
    }

    /** The live children, by token, for a new listener. */
    @NonNull
    LongSparseArray<Child> live() {
        return live;
    }

    /**
     * Asks every child to end (SIGTERM): the host is stopping or its compositor ended, and the apps
     * end with it as they did when they lost their display. Their exits follow as usual.
     */
    void endAll() {
        for (int i = 0; i < live.size(); i++) {
            int pid = live.valueAt(i).pid;
            if (pid <= 0) continue;
            try {
                Os.kill(pid, OsConstants.SIGTERM);
            } catch (Exception ignored) {
                // Already gone.
            }
        }
    }
}
