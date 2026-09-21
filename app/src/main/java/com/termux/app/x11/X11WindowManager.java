package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;

/**
 * The window manager the launcher starts with the server. Without one, X clients open where they
 * map and have no way to be raised or closed; with a small one and a rule that maximises every
 * window, the Display place shows one app at a time, full size — the shape the launcher recommends
 * over a desktop. openbox gets the launcher's own configuration file for that rule, so no dotfile
 * of the user's is touched; any other command is run as written.
 *
 * <p>Two things are true only on the nix edition, and between them they decide the shape of this
 * class. The server execs {@code -xstartup} from Android's side, where a nix binary and its
 * {@code PATH} do not exist, so it has to go through {@code $PREFIX/bin/login}; and the server
 * refuses any single argument longer than about 128 characters ("Command line argument number 7
 * is too long — X server aborted because of unsafe environment"), which the login line with a
 * configuration path in it is well past. So on nix the command is baked into the small wrapper
 * {@link X11CliInstaller#WM_SCRIPT_PATH} and the argument is that one short path.
 */
public final class X11WindowManager {

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
     * none to start. On nix that is the wrapper's path and nothing else; everywhere else it is
     * {@link #command} as written.
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
        if (!NixProfile.isNix(prefixDir)) return command;
        // No wrapper written means nothing to start: the display comes up without a manager
        // rather than handing the server a nix command line it cannot exec.
        return wrapper.canExecute() ? wrapper.getPath() : null;
    }
}
