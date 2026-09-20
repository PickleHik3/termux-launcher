package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;

/**
 * "Start the display with the launcher": the one place the launcher starts a server on its own,
 * and only because the user asked. Decides from the preferences and the prefix; the caller runs
 * what it hands back.
 */
public final class X11Autostart {

    private static final String LOG_TAG = "X11Autostart";

    private X11Autostart() {}

    /**
     * The command to run at service start, or null when nothing should be: the build has no
     * server, the display or the opt-in is off, the launcher's {@code termux-x11} is not in the
     * prefix yet, or a server already answers on the X socket.
     */
    @Nullable
    public static String[] commandToRun(@NonNull Context context) {
        if (!com.termux.BuildConfig.X11_SERVER) return null;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs == null || !prefs.isX11DisplayEnabled() || !prefs.isX11DisplayAutostartEnabled()) {
            return null;
        }
        String[] argv = X11StartCommand.argv(prefs.getX11DisplayCommand(), prefs.getX11DisplayDpi(),
            prefs.isX11LegacyDrawingEnabled(), prefs.isX11ForceBgraEnabled(),
            X11WindowManager.xstartup(prefs.getX11WindowManager()));
        if (argv.length == 0 || !new File(argv[0]).canExecute()) {
            Logger.logInfo(LOG_TAG, "Not starting the display: its command is not in the prefix yet");
            return null;
        }
        File sockets = new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, ".X11-unix");
        String[] live = sockets.list((dir, name) -> name.startsWith("X"));
        if (live != null && live.length > 0) {
            Logger.logInfo(LOG_TAG, "Not starting the display: one is already running");
            return null;
        }
        return argv;
    }
}
