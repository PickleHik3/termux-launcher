package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * The window manager the launcher starts with the server. Without one, X clients open where they
 * map and have no way to be raised or closed; with a small one and a rule that maximises every
 * window, the Display place shows one app at a time, full size — the shape the launcher recommends
 * over a desktop. openbox gets the launcher's own configuration file for that rule, so no dotfile
 * of the user's is touched; any other command is run as written.
 */
public final class X11WindowManager {

    private X11WindowManager() {}

    /**
     * The {@code -xstartup} command for the configured window manager, or null when none is
     * configured or its binary is not in the prefix (the display still starts; windows just
     * come up undecorated and unmanaged).
     */
    @Nullable
    public static String xstartup(@NonNull String configured) {
        String command = configured.trim();
        if (command.isEmpty()) return null;
        String binary = command.split("\\s+")[0];
        File executable = binary.contains("/") ? new File(binary)
            : new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, binary);
        if (!executable.canExecute()) return null;
        if ("openbox".equals(binary) && !command.contains("--config-file")) {
            return command + " --config-file " + X11CliInstaller.OPENBOX_RC_PATH;
        }
        return command;
    }
}
