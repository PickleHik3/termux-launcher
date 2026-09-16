package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.theme.templates.ThemeTemplates;
import com.termux.shared.errors.Error;
import com.termux.shared.android.ProcessUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.app.notice.AppNotice;
import com.termux.launcherctl.LauncherCtlApiServer;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    /**
     * Runs the day/night re-export below; never the UI thread, and never the thread a
     * configuration change is delivered on.
     */
    private static final ExecutorService NIGHT_MODE_EXPORT_EXECUTOR =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "app-night-mode-theme-export");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });

    /**
     * Set once {@link #onCreate()} has done enough setup that a configuration change is worth
     * acting on. A callback that arrived while this was still false would be racing shell manager
     * and preference setup for no reason: nothing has read a wallpaper or written a session yet.
     */
    private volatile boolean mReady;

    /** {@code UI_MODE_NIGHT_MASK} bits last seen, so only an actual day/night flip does anything. */
    private volatile int mLastNightModeMask;

    public void onCreate() {
        super.onCreate();
        if (ProcessPhoenix.isPhoenixProcess(this)) {
            return;
        }
        Context context = getApplicationContext();
        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);
        // Set log config for the app
        setLogConfig(context);
        if (isTaiRuntimeProcess(context)) {
            Logger.logInfo(LOG_TAG, "Starting TAI runtime process");
            return;
        }
        Logger.logDebug("Starting Application");
        // Every transient message in the app — including the ones raised from termux-shared, which
        // cannot see the app module — lands in the in-app notice chip rather than a stock toast.
        AppNotice.install(this);
        Logger.setNoticePresenter(AppNotice::show);
        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);
        // Terminal name and version reported to applications via XTVERSION ("CSI > 0 q")
        com.termux.terminal.TerminalEmulator.setXtVersion(BuildConfig.VERSION_NAME);
        // Fold the old per-surface glass values into Base plus overrides. Must run before anything
        // reads a surface value, because every one of those reads now resolves through the link.
        TermuxAppSharedPreferences surfacePreferences = TermuxAppSharedPreferences.build(context);
        if (surfacePreferences != null)
            surfacePreferences.migrateSurfaceInheritance();
        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);
        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);
        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());
        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }
            // Setup termux-am-socket server; the nix edition's termux-am/termux-setup-storage
            // clients depend on it, and the run-termux-am-socket-server property defaults true.
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }
        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);
        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
            TermuxShellIntegrationInstaller.ensureInstalled(this);
            TermuxLauncherConfigInstaller.ensureInstalled(this);
        }
        mLastNightModeMask = context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
        mReady = true;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        LauncherCtlApiServer.getInstance().stop();
    }

    /**
     * The one config bit this class cares about: day/night. {@code TermuxActivity} only sees this
     * when it is next created, and while it is merely stopped — the ordinary case, since it is the
     * home screen and outlives being backgrounded — Android defers that recreation until the
     * activity is restarted rather than delivering it immediately (see
     * {@code TermuxTerminalSessionActivityClient.refreshMaterialTerminalColorsIfNeeded}'s javadoc
     * for the foreground half of this same path). Meanwhile any shell already running against
     * {@code ~/.termux/material-colors.sh} and the enabled theme templates — tmux's status line, a
     * live starship or herdr prompt — would keep painting the contrast the wallpaper wore before the
     * flip until the user reopens the launcher. This re-derives the export and reruns the template
     * pass from here instead, off the UI thread, so those tools do not have to wait.
     *
     * <p>Deliberately narrow: it does not touch a live {@code TerminalSession}'s colours, repaint a
     * view, or rebuild the in-app keyboard — all of that needs a live activity and already happens
     * unconditionally the moment {@code TermuxActivity.onCreate} next runs, recreated exactly because
     * this same config bit moved.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mReady) return;
        int nightModeMask = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeMask == mLastNightModeMask) return;
        mLastNightModeMask = nightModeMask;
        refreshThemeTemplatesForNightModeFlip(newConfig);
    }

    /**
     * Only the dynamic-wallpaper palette depends on day/night: the static from-scheme path derives
     * every role from {@code ~/.termux/colors.properties} through {@code LauncherSchemeTheme}, which
     * this deliberately does not touch — mutually exclusive with dynamic colors, and unaffected by
     * this event.
     *
     * <p>Passes are ordered through the same {@link ThemeTemplates} applier the activity schedules
     * through (both resolve to one applier keyed off this application instance), so whichever call
     * lands last — this one or the activity's own, should the two race on resume — wins outright;
     * the other stops between templates rather than redoing finished work.
     */
    private void refreshThemeTemplatesForNightModeFlip(@NonNull Configuration newConfig) {
        Context context = getApplicationContext();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null || !preferences.isTerminalDynamicColorsEnabled()) return;
        TerminalContrastLevel level = preferences.getTerminalContrastLevel();
        // Claimed here, synchronously, so a pass this triggers is ordered by when the flip was
        // actually observed rather than when the executor got around to it.
        long pass = ThemeTemplates.schedulePass(context);
        NIGHT_MODE_EXPORT_EXECUTOR.execute(() -> {
            try {
                Context configuredContext = context.createConfigurationContext(newConfig);
                Context themedContext = new ContextThemeWrapper(configuredContext,
                    R.style.Theme_TermuxActivity_DayNight_NoActionBar);
                Properties terminalColors = MaterialTerminalColorScheme.create(themedContext, level);
                Properties exported = MaterialTerminalColorScheme.createMaterialRoleProperties(
                    themedContext, terminalColors, level);
                MaterialTerminalColorScheme.writeMaterialColorFiles(exported);
                ThemeTemplates.runPass(context, exported, pass);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG,
                    "Error refreshing theme templates for a background day/night flip", e);
            }
        });
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);
        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null)
            return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

    private boolean isTaiRuntimeProcess(Context context) {
        String processName = ProcessUtils.getAppProcessNameForPid(context, android.os.Process.myPid());
        return processName != null && processName.endsWith(":tai_runtime");
    }
}
