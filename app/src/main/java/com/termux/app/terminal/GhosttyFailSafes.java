package com.termux.app.terminal;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;
import com.termux.terminal.GhosttyCrashGuard;
import com.termux.terminal.GhosttyMemory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The app's side of the Ghostty core's fail-safes: the crash guard's files and exit records, and
 * memory pressure forwarded to every Ghostty pane. Installed once from
 * {@code TermuxApplication.onCreate} in the main process, before any session is created.
 */
public final class GhosttyFailSafes {

    private static final String LOG_TAG = "GhosttyFailSafes";
    /** Present while a Ghostty pane may be writing; see {@link GhosttyCrashGuard}. */
    static final String MARKER_FILE = "ghostty-core.marker";
    /** Present while a crash keeps the Ghostty core off for this build. */
    static final String DISABLED_FILE = "ghostty-core.off";

    private GhosttyFailSafes() {}

    public static void install(@NonNull Application app) {
        try {
            File dir = app.getNoBackupFilesDir();
            GhosttyCrashGuard.install(new FileStore(dir),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? exitReasons(app) : null,
                Process.myPid(), buildStamp(app), System::currentTimeMillis,
                message -> Logger.logWarn(LOG_TAG, message));
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Ghostty crash guard not installed", e);
        }
        app.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {
                GhosttyMemory.onTrimMemory(level);
            }

            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {}

            @Override
            public void onLowMemory() {
                GhosttyMemory.onTrimMemory(TRIM_MEMORY_COMPLETE);
            }
        });
    }

    /** Changes with every install of the app, an update or a reinstall of the same version alike. */
    private static long buildStamp(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static GhosttyCrashGuard.ExitReasons exitReasons(Context context) {
        return (pid, sinceMillis) -> {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return GhosttyCrashGuard.REASON_UNKNOWN;
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(context.getPackageName(), pid, 1);
            if (exits == null || exits.isEmpty()) return GhosttyCrashGuard.REASON_UNKNOWN;
            ApplicationExitInfo exit = exits.get(0);
            if (exit.getPid() != pid || exit.getTimestamp() < sinceMillis) return GhosttyCrashGuard.REASON_UNKNOWN;
            return exit.getReason();
        };
    }

    /** The marker and the off record as small files in no-backup storage. */
    static final class FileStore implements GhosttyCrashGuard.Store {
        private final File mMarker, mDisabled;

        FileStore(File dir) {
            mMarker = new File(dir, MARKER_FILE);
            mDisabled = new File(dir, DISABLED_FILE);
        }

        @Nullable @Override public String readMarker() { return read(mMarker); }

        @Override public void writeMarker(String marker) { write(mMarker, marker); }

        @Override public void clearMarker() { delete(mMarker); }

        @Nullable @Override public String readDisabled() { return read(mDisabled); }

        @Override public void writeDisabled(String record) { write(mDisabled, record); }

        @Override public void clearDisabled() { delete(mDisabled); }

        @Nullable
        private static String read(File file) {
            if (!file.isFile()) return null;
            try {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private static void write(File file, String text) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                // No fsync: a process that crashes leaves its written pages to the kernel, and this
                // runs on the main thread before the first write.
                out.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void delete(File file) {
            if (file.exists() && !file.delete()) Logger.logWarn(LOG_TAG, "Could not delete " + file.getName());
        }
    }
}
