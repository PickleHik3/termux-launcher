package com.termux.app.theme;

import android.app.Activity;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class TermuxThemeManager {

    private static final String LOG_TAG = "TermuxThemeManager";

    /**
     * The app-only seed each live activity was themed with (absent: Android's wallpaper colours or
     * the scheme), so a moved picture is noticed and a recreate never repeats for one move.
     */
    private static final Map<Activity, Integer> THEMED_SEED = new WeakHashMap<>();

    private TermuxThemeManager() {}

    /**
     * Every activity's last theming step, called straight after {@code setTheme}.
     *
     * <p>Dynamic Material remains the default path. When the user has handed the chrome to the
     * terminal colour scheme, the scheme's palette is layered on top here — after {@code setTheme},
     * because that is what discards previously applied overlays. Otherwise, while the launcher
     * wears an app-only picture (ADR 0011), that picture's colours stand in for Android's
     * wallpaper's the same way.
     */
    public static void applyThemeOverlays(@NonNull Activity activity) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity);
        Integer seed = null;
        if (!LauncherSchemeTheme.apply(activity, preferences)) {
            WallpaperColorSource.Kind kind = AppOnlyWallpaperColors.kind(activity, preferences);
            if (kind == WallpaperColorSource.Kind.APP_ONLY) {
                seed = preferences.getWallpaperAppOnlySeed();
                AppOnlyWallpaperColors.apply(activity, seed);
            } else if (kind == WallpaperColorSource.Kind.NEEDS_SEED) {
                // Never decoded here: Android's colours this time, the seed taken on a worker, and
                // the re-theme asked for when it lands.
                requestSeed(activity);
            }
        }
        // What was decided, not whether the load succeeded: a load that fails must not make every
        // check after it see a move and recreate again.
        synchronized (THEMED_SEED) {
            THEMED_SEED.put(activity, seed);
        }
    }

    /**
     * Recreates {@code activity} when the app-only colours it wears are no longer the ones that
     * apply: the app-only picture changed, the launcher left app-only mode, or the copy is gone.
     * This is how such a move re-themes, as a system wallpaper's colour change re-themes through a
     * configuration change. Cheap enough for every arrival (preferences, and stats of the copy only
     * while the app-only mark holds).
     *
     * @return true when a recreate was asked for
     */
    @MainThread
    public static boolean recreateIfWallpaperColorsMoved(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        Integer themed;
        synchronized (THEMED_SEED) {
            if (!THEMED_SEED.containsKey(activity)) return false;
            themed = THEMED_SEED.get(activity);
        }
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        WallpaperColorSource.Kind kind = AppOnlyWallpaperColors.kind(activity, preferences);
        if (kind == WallpaperColorSource.Kind.NEEDS_SEED) {
            requestSeed(activity);
            return false;
        }
        Integer now = kind == WallpaperColorSource.Kind.APP_ONLY
            ? preferences.getWallpaperAppOnlySeed() : null;
        if (Objects.equals(themed, now)) return false;
        synchronized (THEMED_SEED) {
            // Held as answered, so a second check before the recreate lands asks for no other.
            THEMED_SEED.put(activity, now);
        }
        Logger.logInfo(LOG_TAG, "Wallpaper colours moved to " + (now == null ? "the system's"
            : "#" + Integer.toHexString(now)) + "; recreating " + activity.getClass().getSimpleName());
        activity.recreate();
        return true;
    }

    private static void requestSeed(@NonNull Activity activity) {
        WeakReference<Activity> ref = new WeakReference<>(activity);
        AppOnlyWallpaperColors.takeSeedInBackground(activity, () -> {
            Activity target = ref.get();
            if (target != null) recreateIfWallpaperColorsMoved(target);
        });
    }
}
