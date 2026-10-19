package com.termux.app.launcher.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.LockAccessibilityService;
import com.termux.privileged.PrivilegedBackendManager;

/**
 * Runs what a Home gesture is set to ({@link HomeGestures}). The launcher's own moves — the
 * palette, the drawer, the Terminal, the lock — are the host's; the phone's shade and an app are
 * opened here.
 *
 * <p>An app may not pull the system's notification shade or quick settings down, nor lock the
 * phone, by itself at target SDK 28 without a permission it does not hold. The launcher's
 * accessibility service can (global actions), and so can Shizuku ({@code cmd statusbar}), so those
 * actions are offered only while one of them is there.
 */
public final class HomeGestureActions {

    private HomeGestureActions() {}

    /** The launcher's side of the gestures. */
    public interface Host {
        /** The palette with the keyboard, as the swipe up opens it. */
        void openPalette();
        void openDrawer();
        /** Slides the wall to the Terminal. */
        void goToTerminal();
        void lockScreen();
        /** A helper is needed and none is there: say what to turn on. */
        void explainHelperNeeded();
        /** The gesture is set to open an app and none is picked, or it is gone. */
        void explainNoApp();
        /** Shizuku was asked to pull the shade down and said no. Called on the main thread. */
        void explainShadeFailed();
    }

    /** Whether the actions that need a helper can run: the accessibility service, or Shizuku. */
    public static boolean helperAvailable(@NonNull Context context) {
        if (LauncherLockAccessibilityAccess.isEnabled(context)) return true;
        try {
            return PrivilegedBackendManager.getInstance().isPrivilegedAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Runs {@code action}; {@code app} is the flattened component an {@link HomeGestures.Action#APP} opens. */
    public static void run(@NonNull Context context, @NonNull HomeGestures.Action action,
                           @Nullable String app, @NonNull Host host) {
        switch (action) {
            case PALETTE: host.openPalette(); break;
            case DRAWER: host.openDrawer(); break;
            case TERMINAL: host.goToTerminal(); break;
            case LOCK_SCREEN: host.lockScreen(); break;
            case NOTIFICATIONS:
            case QUICK_SETTINGS:
                if (!expandShade(context, action == HomeGestures.Action.QUICK_SETTINGS, host)) {
                    host.explainHelperNeeded();
                }
                break;
            case APP:
                if (!launch(context, app)) host.explainNoApp();
                break;
            case NONE:
            default:
                break;
        }
    }

    /** The shade (or quick settings) down through whichever helper is there; false for none. */
    static boolean expandShade(@NonNull Context context, boolean quickSettings, @NonNull Host host) {
        if (LauncherLockAccessibilityAccess.isEnabled(context)
            && LockAccessibilityService.expandShade(quickSettings)) return true;
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        if (!manager.isPrivilegedAvailable()) return false;
        Handler main = new Handler(Looper.getMainLooper());
        manager.executeCommand(quickSettings ? "cmd statusbar expand-settings"
                : "cmd statusbar expand-notifications")
            .handle((output, error) -> {
                if (error != null || !commandSucceeded(output)) main.post(host::explainShadeFailed);
                return null;
            });
        return true;
    }

    /**
     * Whether a privileged command's output reads as done: empty, or nothing that names an error,
     * a missing permission or no backend (the launcher's lock reads its commands the same way).
     */
    static boolean commandSucceeded(@Nullable String output) {
        if (output == null) return false;
        String lower = output.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.isEmpty()) return true;
        return !lower.startsWith("error") && !lower.contains("permission required")
            && !lower.contains("no privileged backend") && !lower.contains("exception");
    }

    private static boolean launch(@NonNull Context context, @Nullable String app) {
        ComponentName component = app == null ? null : ComponentName.unflattenFromString(app);
        if (component == null) return false;
        Intent intent = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
