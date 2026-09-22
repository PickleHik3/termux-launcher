package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * The window manager the launcher starts with the server. Without one, X clients open where they
 * map and have no way to be raised or closed; with a small one and a rule that maximises every
 * window, the Display place shows one app at a time, full size — the shape the launcher recommends
 * over a desktop. openbox gets the launcher's own configuration file for that rule, so no dotfile
 * of the user's is touched; any other command is run as written.
 *
 * <p><b>The manager is never the server's own {@code -xstartup} child.</b> The server waits on
 * that child and shuts the whole display down the moment it exits <em>or is signalled</em>
 * (upstream {@code lorie/src/main/cpp/lorie/InitOutput.c}, {@code ddxReadyThread}: a
 * {@code waitpid} loop ending {@code while (!WIFEXITED(status) && !WIFSIGNALED(status))}, then
 * {@code GiveUp(SIGINT)}). A desktop that wants the display to itself has to be able to stop the
 * manager (D1), so what the server is handed is always {@link X11CliInstaller#WM_SCRIPT_PATH}, a
 * wrapper that starts the manager beside itself and then outlives it. Stopping the manager then
 * leaves the server's child exactly where it was.
 *
 * <p>The wrapper records the manager's pid in {@link #WM_PID_PATH}, which is the whole of how
 * "ours and only ours" is decided: nothing but the launcher ever writes that file, so a manager
 * the user started themselves cannot be named by it, whatever it is called or configured with.
 *
 * <p>Two things are true only on the nix edition, and they are why the wrapper existed before any
 * of this. The server execs {@code -xstartup} from Android's side, where a nix binary and its
 * {@code PATH} do not exist, so it has to go through {@code $PREFIX/bin/login}; and the server
 * refuses any single argument longer than about 128 characters ("Command line argument number 7
 * is too long — X server aborted because of unsafe environment"), which the login line with a
 * configuration path in it is well past. The wrapper's own path is short on every edition.
 */
public final class X11WindowManager {

    /**
     * Where the wrapper leaves the pid of the window manager it started, and the only handle the
     * launcher has on it. In the prefix's temporary directory, which the system empties at boot;
     * the wrapper takes the file away again as soon as the manager it names is gone.
     */
    public static final String WM_PID_PATH =
        TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/termux-x11-wm.pid";

    private X11WindowManager() {}

    /**
     * The window-manager command itself, in the world it will run in: the configured command with
     * openbox's configuration file added, or null when none is configured or its binary is not
     * installed (the display still starts; windows just come up undecorated and unmanaged).
     *
     * <p>This is what the nix wrapper carries; on every other edition it is also what the server
     * is handed.
     */
    @Nullable
    public static String command(@NonNull String configured) {
        return command(configured, NixProfile.prefixDir());
    }

    /** {@link #command(String)} against a given prefix, so a fixture tree can stand in for one. */
    @VisibleForTesting
    @Nullable
    static String command(@NonNull String configured, @NonNull File prefixDir) {
        String command = configured.trim();
        if (command.isEmpty()) return null;
        String binary = command.split("\\s+")[0];
        if (!isInstalled(binary, prefixDir)) return null;
        if ("openbox".equals(binary) && !command.contains("--config-file")) {
            return command + " --config-file " + X11CliInstaller.OPENBOX_RC_PATH;
        }
        return command;
    }

    /**
     * The shell line that stops the window manager <em>this launcher</em> started, and nothing
     * else, or null when there is no manager at all. {@code command} is what {@link #command}
     * built, and is read only for that question.
     *
     * <p>It kills the one pid {@link #WM_PID_PATH} names. Nothing but the launcher's own wrapper —
     * and its own restart, {@code X11LinuxAppRunner.windowManagerScript} — ever writes that file,
     * so a manager the user started for themselves can never be the one named, and no pattern has
     * to be guessed at. An absent or empty file means there is nothing of ours running and nothing
     * is killed, which is also what an older display, started before this wrapper existed, looks
     * like: it keeps its manager rather than losing its display.
     *
     * <p>No {@code pkill}: a bare name would not resolve on the nix edition, whose {@code PATH} is
     * the prefix's {@code bin} and holds none, and matching on a command line is the thing that
     * could go wrong — the display server's own command line carries the window manager's command
     * as its {@code -xstartup} argument.
     */
    @Nullable
    public static String stopCommand(@Nullable String command) {
        if (command == null) return null;
        String pidFile = ProotDistro.singleQuote(WM_PID_PATH);
        return "wm=$(cat " + pidFile + " 2>/dev/null)\n"
            + "[ -n \"$wm\" ] && kill \"$wm\" 2>/dev/null\n"
            + "rm -f " + pidFile;
    }

    /**
     * The shell line that starts the configured window manager — what the wrapper runs, and what
     * the launcher runs again after a desktop session has finished with the display. The command
     * itself everywhere but nix, where it has to go through {@code login} to mean anything. Null
     * when there is no manager to start.
     *
     * <p>Not {@link #xstartup}: that is the wrapper, whose job is to be the server's child and
     * stay alive. Running it a second time would leave a second one sleeping forever.
     */
    @Nullable
    public static String startCommand(@NonNull String configured) {
        return startCommand(configured, NixProfile.prefixDir());
    }

    /** {@link #startCommand(String)} against a given prefix, so a fixture tree can stand in. */
    @VisibleForTesting
    @Nullable
    static String startCommand(@NonNull String configured, @NonNull File prefixDir) {
        String command = command(configured, prefixDir);
        if (command == null) return null;
        if (!NixProfile.isNix(prefixDir)) return command;
        return new File(prefixDir, "bin/login").getPath() + " " + command;
    }

    /**
     * Whether {@code binary} is there to be run. A bare name is looked for where the edition
     * keeps its programs — the prefix's {@code bin}, or the nix profile's, which is where a
     * {@code login}'s own {@code PATH} will find it by that same name.
     */
    private static boolean isInstalled(@NonNull String binary, @NonNull File prefixDir) {
        if (binary.contains("/")) return new File(binary).canExecute();
        File profile = NixProfile.profile(prefixDir);
        if (profile != null) {
            return NixProfile.under(prefixDir, profile, "bin/" + binary).canExecute();
        }
        return new File(prefixDir, "bin/" + binary).canExecute();
    }

    /**
     * The {@code -xstartup} argument for the configured window manager, or null when there is
     * none to start. The wrapper's path on every edition: the server ends the display when this
     * child exits or is signalled, so the child must be something that outlives the manager.
     */
    @Nullable
    public static String xstartup(@NonNull String configured) {
        return xstartup(configured, NixProfile.prefixDir(), new File(X11CliInstaller.WM_SCRIPT_PATH));
    }

    /** {@link #xstartup(String)} against a given prefix and wrapper, for the same reason. */
    @VisibleForTesting
    @Nullable
    static String xstartup(@NonNull String configured, @NonNull File prefixDir,
                           @NonNull File wrapper) {
        String command = command(configured, prefixDir);
        if (command == null) return null;
        if (wrapper.canExecute()) return wrapper.getPath();
        // No wrapper written. On nix there is nothing else the server could exec, so the display
        // comes up without a manager rather than with a command line it cannot run. Everywhere
        // else the command itself still works and is what the launcher did before the wrapper: the
        // manager is then the server's own child, which is exactly why nothing may kill it — and
        // nothing will, since it is the wrapper that records the pid a stop would use.
        return NixProfile.isNix(prefixDir) ? null : command;
    }
}
