package com.termux.launcherctl;

import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Helpers for runtime state stored under {@code ~/.launcherctl}.
 *
 * <p>All paths are kept under the Termux home directory so that shell tooling
 * can read and write them without special permissions. A test override is
 * provided so unit tests can use a temporary directory instead of the real
 * {@code /data/data/com.termux/files/home} path.
 */
public final class LauncherCtlStorage {
    private static final String LOG_TAG = "LauncherCtlStorage";

    private static final String LAUNCHERCTL_DIR_NAME = ".launcherctl";

    public static final String DB_FILE_NAME = "launcher.db";
    public static final String NOTIFICATIONS_JSONL_NAME = "notifications.jsonl";

    private static File sTestBaseDir = null;

    private LauncherCtlStorage() {
    }

    /**
     * Override the base directory used for all LauncherCtl paths. Intended for
     * unit tests only.
     */
    public static synchronized void setBaseDirForTesting(File baseDir) {
        sTestBaseDir = baseDir;
    }

    public static synchronized void clearTestBaseDir() {
        sTestBaseDir = null;
    }

    /**
     * Returns the base directory that contains {@code .launcherctl}. For real
     * runtime this is {@link TermuxConstants#TERMUX_HOME_DIR_PATH}; tests may
     * override it with {@link #setBaseDirForTesting(File)}.
     */
    public static File getHomeDir() {
        File override;
        synchronized (LauncherCtlStorage.class) {
            override = sTestBaseDir;
        }
        return override != null ? override : TermuxConstants.TERMUX_HOME_DIR;
    }

    /** Returns {@code ~/.launcherctl} (or the test override equivalent). */
    public static File getLauncherCtlDir() {
        return new File(getHomeDir(), LAUNCHERCTL_DIR_NAME);
    }

    /**
     * Creates the LauncherCtl directory if it does not already exist. Logs a
     * warning on failure but does not throw.
     */
    public static File ensureLauncherCtlDir() {
        File dir = getLauncherCtlDir();
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(LOG_TAG, "Failed to create launcherctl directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static File getDatabaseFile() {
        return new File(ensureLauncherCtlDir(), DB_FILE_NAME);
    }

    public static File getNotificationsJsonlFile() {
        return new File(ensureLauncherCtlDir(), NOTIFICATIONS_JSONL_NAME);
    }

}
