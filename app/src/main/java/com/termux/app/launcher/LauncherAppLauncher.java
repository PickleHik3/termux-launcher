package com.termux.app.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LauncherAppLauncher {

    private LauncherAppLauncher() {
    }

    /** Where a profile launch reports back; always called on the main thread. */
    public interface ProfileLaunchResult {
        /**
         * @param launched whether the app was started
         * @param later    true when the answer came after {@code am} ran in the background, so the
         *                 caller's view may have gone meanwhile
         */
        void onProfileLaunchResult(boolean launched, boolean later);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /**
     * Runs the {@code am start --user} fallback, which waits up to 5 s for a process: never on
     * main. One thread, gone when idle; a launch is one tap, so nothing queues behind it for long.
     */
    private static final ExecutorService AM_EXECUTOR = newAmExecutor();
    /** {@code UserHandle.of(int)}, looked up once; it is hidden API reached by reflection. */
    @Nullable private static volatile Method userHandleOf;

    @NonNull
    private static ExecutorService newAmExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 15L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "ProfileLaunchAm"));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** How {@link #startProfileWithLauncherApps} ended. */
    private enum ProfileStart {
        /** Started. */
        LAUNCHED,
        /** Not a profile launch this can attempt (no user, no activity, no LauncherApps). */
        NOT_ATTEMPTED,
        /** LauncherApps refused it; {@code am start --user} is the remaining way. */
        NEEDS_AM
    }

    /** Runs a Linux app entry on the display; installed by the activity that owns the display. */
    public interface LinuxAppRunner {
        boolean run(@NonNull LauncherAppEntry entry);
    }

    /**
     * Runs a Linux app entry that wants a terminal rather than the display (D5,
     * {@code Terminal=true}). {@link #handles} decides ahead of {@link #run} so the launcher can
     * fall back to {@link LinuxAppRunner} for every entry this one does not claim, without either
     * runner needing to know about the other.
     */
    public interface TerminalAppRunner {
        boolean handles(@NonNull LauncherAppEntry entry);
        boolean run(@NonNull LauncherAppEntry entry);
    }

    /**
     * Process-wide, because the drawer and the launcherctl API both arrive here. Two instances of
     * the owning activity can be alive at once - a home relaunch out of a plain task, an adb
     * start - and the first one's onDestroy runs after the second one's onCreate, so an instance
     * may only ever take out the runner it put in ({@link #clearLinuxAppRunner}); one that set
     * null on the way out left every Linux app in the drawer dead until the launcher restarted.
     */
    @Nullable private static volatile LinuxAppRunner linuxAppRunner;
    /** Same lifecycle rules as {@link #linuxAppRunner}, for {@link TerminalAppRunner}. */
    @Nullable private static volatile TerminalAppRunner terminalAppRunner;

    public static void setLinuxAppRunner(@NonNull LinuxAppRunner runner) {
        linuxAppRunner = runner;
    }

    /** Take {@code runner} out, if it is still the one installed; another instance's stays. */
    public static void clearLinuxAppRunner(@NonNull LinuxAppRunner runner) {
        if (linuxAppRunner == runner) linuxAppRunner = null;
    }

    @Nullable
    static LinuxAppRunner linuxAppRunner() {
        return linuxAppRunner;
    }

    public static void setTerminalAppRunner(@NonNull TerminalAppRunner runner) {
        terminalAppRunner = runner;
    }

    /** Take {@code runner} out, if it is still the one installed; another instance's stays. */
    public static void clearTerminalAppRunner(@NonNull TerminalAppRunner runner) {
        if (terminalAppRunner == runner) terminalAppRunner = null;
    }

    @Nullable
    static TerminalAppRunner terminalAppRunner() {
        return terminalAppRunner;
    }

    public static boolean launchEntry(@NonNull Context context, @NonNull LauncherAppEntry entry) {
        if (entry.appRef.packageName.startsWith("injected.test")) {
            return false;
        }
        if (com.termux.app.x11.X11Apps.isLinuxApp(entry.appRef)) {
            // Not an Android component: a Terminal=true entry (D5) gets a pane; every other Linux
            // app still goes to the display's runner, or nothing does.
            TerminalAppRunner terminal = terminalAppRunner;
            if (terminal != null && terminal.handles(entry)) {
                return terminal.run(entry);
            }
            LinuxAppRunner runner = linuxAppRunner;
            return runner != null && runner.run(entry);
        }
        if (entry.appRef.clonedProfile) {
            ProfileStart start = startProfileWithLauncherApps(context, entry, null);
            if (start == ProfileStart.LAUNCHED) return true;
            if (start == ProfileStart.NEEDS_AM) {
                // The am fallback blocks for a process, so it runs off main; if it fails, the
                // current-profile launch runs then, as it did straight after it before.
                final Context appContext = context.getApplicationContext();
                final int userId = entry.appRef.userId;
                final String packageName = entry.appRef.packageName;
                final String activityName = profileActivityName(entry);
                AM_EXECUTOR.execute(() -> {
                    if (tryStartProfileWithAm(userId, packageName, activityName)) return;
                    MAIN.post(() -> launchInCurrentProfile(liveContext(context, appContext), entry));
                });
                return true;
            }
        }
        return launchInCurrentProfile(context, entry);
    }

    /** {@code context} while it is a live activity (or not an activity), else the application. */
    @NonNull
    private static Context liveContext(@NonNull Context context, @NonNull Context appContext) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) return appContext;
        }
        return context;
    }

    private static boolean launchInCurrentProfile(@NonNull Context context,
                                                  @NonNull LauncherAppEntry entry) {
        PackageManager packageManager = context.getPackageManager();
        String activityName = entry.appRef.activityName;
        if (!TextUtils.isEmpty(activityName) && activityName.startsWith(".")) {
            activityName = entry.appRef.packageName + activityName;
        }

        Intent explicit = null;
        Intent explicitNoCategory = null;
        if (!TextUtils.isEmpty(activityName)) {
            explicit = new Intent(Intent.ACTION_MAIN);
            explicit.addCategory(Intent.CATEGORY_LAUNCHER);
            explicit.setComponent(new ComponentName(entry.appRef.packageName, activityName));

            explicitNoCategory = new Intent(Intent.ACTION_MAIN);
            explicitNoCategory.setComponent(new ComponentName(entry.appRef.packageName, activityName));
        }

        // The catalogue already names the component: start it as it is and ask the package
        // manager for the package's own launch intent only when that fails, rather than resolving
        // it on every tap. A Launcher3-style launch intent is exactly this explicit one.
        if (tryStartActivity(context, explicit)) {
            return true;
        }
        Intent packageDefault = packageManager.getLaunchIntentForPackage(entry.appRef.packageName);
        if (tryStartActivity(context, packageDefault)) {
            return true;
        }

        Intent resolveFallback = new Intent(Intent.ACTION_MAIN);
        resolveFallback.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveFallback.setPackage(entry.appRef.packageName);
        ComponentName resolved = resolveFallback.resolveActivity(packageManager);
        if (resolved != null) {
            resolveFallback.setComponent(resolved);
        }

        if (tryStartActivity(context, explicitNoCategory)) {
            return true;
        }
        if (resolved != null && tryStartActivity(context, resolveFallback)) {
            return true;
        }
        if (tryStartMainActivity(context, explicit != null ? explicit.getComponent() : null)) {
            return true;
        }
        if (tryStartMainActivity(context, packageDefault != null ? packageDefault.getComponent() : null)) {
            return true;
        }
        if (tryStartMainActivity(context, resolved)) {
            return true;
        }

        Intent packageMain = new Intent(Intent.ACTION_MAIN);
        packageMain.addCategory(Intent.CATEGORY_LAUNCHER);
        packageMain.setPackage(entry.appRef.packageName);
        List<ResolveInfo> matches = packageManager.queryIntentActivities(packageMain, 0);
        for (ResolveInfo match : matches) {
            if (match == null || match.activityInfo == null) continue;
            String pkg = match.activityInfo.packageName;
            String cls = match.activityInfo.name;
            if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(cls)) continue;
            Intent fallbackExplicit = new Intent(Intent.ACTION_MAIN);
            fallbackExplicit.addCategory(Intent.CATEGORY_LAUNCHER);
            fallbackExplicit.setComponent(new ComponentName(pkg, cls));
            if (tryStartActivity(context, fallbackExplicit)
                || tryStartMainActivity(context, fallbackExplicit.getComponent())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Starts a cloned or work-profile entry in its own profile. LauncherApps is tried on the
     * calling (main) thread and answers at once; when it refuses, {@code am start --user} runs on
     * a background thread and the answer is posted to main ({@code later} set).
     */
    public static void startProfileMainActivity(@NonNull Context context,
                                                @NonNull LauncherAppEntry entry,
                                                @Nullable Bundle options,
                                                @NonNull ProfileLaunchResult result) {
        ProfileStart start = startProfileWithLauncherApps(context, entry, options);
        if (start != ProfileStart.NEEDS_AM) {
            result.onProfileLaunchResult(start == ProfileStart.LAUNCHED, false);
            return;
        }
        final int userId = entry.appRef.userId;
        final String packageName = entry.appRef.packageName;
        final String activityName = profileActivityName(entry);
        AM_EXECUTOR.execute(() -> {
            boolean launched = tryStartProfileWithAm(userId, packageName, activityName);
            MAIN.post(() -> result.onProfileLaunchResult(launched, true));
        });
    }

    /** The entry's activity with a leading-dot name expanded, or "" when it has none. */
    @NonNull
    private static String profileActivityName(@NonNull LauncherAppEntry entry) {
        String activityName = entry.appRef.activityName;
        if (!TextUtils.isEmpty(activityName) && activityName.startsWith(".")) {
            activityName = entry.appRef.packageName + activityName;
        }
        return activityName == null ? "" : activityName;
    }

    @NonNull
    private static ProfileStart startProfileWithLauncherApps(@NonNull Context context,
                                                             @NonNull LauncherAppEntry entry,
                                                             @Nullable Bundle options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || entry.appRef.userId < 0) {
            return ProfileStart.NOT_ATTEMPTED;
        }
        String activityName = profileActivityName(entry);
        if (TextUtils.isEmpty(activityName)) {
            return ProfileStart.NOT_ATTEMPTED;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return ProfileStart.NOT_ATTEMPTED;
            }
            launcherApps.startMainActivity(
                new ComponentName(entry.appRef.packageName, activityName),
                userHandleFor(entry.appRef.userId),
                null,
                options
            );
            return ProfileStart.LAUNCHED;
        } catch (Throwable ignored) {
            return ProfileStart.NEEDS_AM;
        }
    }

    @NonNull
    private static UserHandle userHandleFor(int userId) throws Exception {
        Method method = userHandleOf;
        if (method == null) {
            method = UserHandle.class.getDeclaredMethod("of", int.class);
            method.setAccessible(true);
            userHandleOf = method;
        }
        Object value = method.invoke(null, userId);
        if (value instanceof UserHandle) {
            return (UserHandle) value;
        }
        throw new IllegalStateException("UserHandle.of did not return a handle");
    }

    private static boolean tryStartProfileWithAm(int userId, @NonNull String packageName, @NonNull String activityName) {
        if (userId < 0) {
            return false;
        }
        try {
            String component = packageName + "/" + activityName;
            java.lang.Process process = new ProcessBuilder("am", "start", "--user",
                String.valueOf(userId), "-n", component)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryStartMainActivity(@NonNull Context context, @Nullable ComponentName componentName) {
        if (componentName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return false;
            }
            launcherApps.startMainActivity(componentName, Process.myUserHandle(), null, null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryStartActivity(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (context instanceof Activity) {
                ((Activity) context).startActivity(intent);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
