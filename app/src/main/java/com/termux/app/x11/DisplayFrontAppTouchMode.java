package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/**
 * Which touch mode the display uses while a Linux app's window is in front: that app's own
 * override, or none (the display's global mode). Matching a window class to an app needs the app
 * catalogue, which is file work, so it runs on one background thread and its answer is posted
 * back to the main thread; an answer for a window that is no longer in front is dropped.
 *
 * <p>All methods are for the main thread.
 */
final class DisplayFrontAppTouchMode {

    /** Receives the override for the front window; null follows the display's mode. */
    interface Callback {
        void onTouchModeOverride(@Nullable String mode);
    }

    @NonNull private final Context context;
    @NonNull private final Callback callback;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private ExecutorService executor;
    /** The scanned catalogue, kept for the window-to-app match; worker thread only. */
    @Nullable private List<LinuxAppCatalog.LinuxApp> apps;
    @Nullable private X11AppSettingsStore store;
    @Nullable private String frontClass;
    /** Bumped by every request so a late answer can tell it is stale. */
    private int generation;
    private boolean released;

    DisplayFrontAppTouchMode(@NonNull Context context, @NonNull Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    /**
     * The decision: the override for a window of {@code wmClass}, given the catalogue's apps and
     * a lookup of an app's stored override; null for an empty class, an unknown app, or an app
     * with no valid override.
     */
    @Nullable
    static String decide(@NonNull List<LinuxAppCatalog.LinuxApp> apps, @Nullable String wmClass,
                         @NonNull Function<String, String> overrideOf) {
        String appId = X11AppSettingsResolver.appIdForWindowClass(apps, wmClass);
        if (appId == null) return null;
        String mode = overrideOf.apply(appId);
        return X11AppSettings.isTouchMode(mode) ? mode : null;
    }

    /** The window in front changed (or went away: null or empty). An unchanged class is skipped. */
    void onFrontWindowClass(@Nullable String wmClass) {
        String cls = wmClass == null || wmClass.trim().isEmpty() ? null : wmClass;
        if (released) return;
        if (cls == null ? frontClass == null : cls.equals(frontClass)) return;
        frontClass = cls;
        resolve(false);
    }

    /** Preferences changed: the store may differ, and an app may have been installed. */
    void onPreferencesReloaded() {
        if (released) return;
        resolve(true);
    }

    void release() {
        released = true;
        generation++;
        ExecutorService ex = executor;
        executor = null;
        if (ex != null) ex.shutdown();
    }

    private void resolve(boolean rescan) {
        final int mine = ++generation;
        final String cls = frontClass;
        if (cls == null) {
            callback.onTouchModeOverride(null);
            return;
        }
        ExecutorService ex = executor;
        if (ex == null) {
            ex = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "display-front-touch");
                t.setDaemon(true);
                return t;
            });
            executor = ex;
        }
        try {
            ex.execute(() -> {
                String mode;
                try {
                    mode = lookup(cls, rescan);
                } catch (RuntimeException e) {
                    mode = null;
                }
                final String result = mode;
                main.post(() -> {
                    if (released || mine != generation) return;
                    callback.onTouchModeOverride(result);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Released between the check and here.
        }
    }

    /** Worker thread. */
    @Nullable
    private String lookup(@NonNull String cls, boolean rescan) {
        X11AppSettingsStore s = store;
        if (s == null) store = s = new X11AppSettingsStore(context);
        List<LinuxAppCatalog.LinuxApp> list = apps;
        if (list == null || rescan) apps = list = LinuxAppCatalog.scan(LinuxAppCatalog.roots());
        return decide(list, cls, s::touchMode);
    }
}
