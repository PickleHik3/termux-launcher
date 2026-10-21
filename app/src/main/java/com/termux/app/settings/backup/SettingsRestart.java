package com.termux.app.settings.backup;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import androidx.annotation.NonNull;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.termux.shared.logger.Logger;

import java.util.List;

/**
 * Restarts the launcher after a restore, so every store and cache that lives for the whole process
 * (the app catalogue, the widget wall, termux.properties) starts again from what was written.
 *
 * <p>The app's other processes ({@code :tai_runtime}) are stopped first, so none of them writes a
 * store back from its stale copy. {@link ProcessPhoenix} then ends this process from its own
 * {@code :phoenix} process and starts the launcher again: through the home intent while this
 * launcher is the default home, so it comes back as the home screen, and through its launcher
 * entry otherwise.
 */
public final class SettingsRestart {

    private static final String LOG_TAG = "SettingsRestart";

    private SettingsRestart() {}

    /** Call on the main thread; this process ends shortly after. */
    public static void restart(@NonNull Activity activity) {
        Context app = activity.getApplicationContext();
        Intent next = restartIntent(app);
        stopOtherProcesses(app);
        activity.finish();
        ProcessPhoenix.triggerRebirth(app, next);
    }

    @NonNull
    static Intent restartIntent(@NonNull Context context) {
        String packageName = context.getPackageName();
        PackageManager packageManager = context.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo defaultHome = null;
        try {
            defaultHome = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Resolving the home screen failed: " + e.getMessage());
        }
        if (defaultHome != null && defaultHome.activityInfo != null
            && packageName.equals(defaultHome.activityInfo.packageName)) {
            return home.setPackage(packageName);
        }
        Intent launch = packageManager.getLaunchIntentForPackage(packageName);
        return launch != null ? launch : home.setPackage(packageName);
    }

    /** This app's own named processes other than this one ({@code <package>:<name>}). */
    private static void stopOtherProcesses(@NonNull Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        String prefix = context.getPackageName() + ":";
        try {
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes == null) return;
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.pid == Process.myPid() || process.processName == null) continue;
                if (process.uid != Process.myUid() || !process.processName.startsWith(prefix)) continue;
                Process.killProcess(process.pid);
            }
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Stopping the app's other processes failed: " + e.getMessage());
        }
    }
}
