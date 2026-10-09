package com.termux.app.chrome;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;

/**
 * The one place in the app that asks Android about the system wallpaper.
 *
 * <p>Three facts decide which {@link WallpaperPicture} the glass is looking at, and each of them is
 * a platform call that can throw or lie on its own: whether a wallpaper <em>service</em> is running
 * ({@code getWallpaperInfo()}), whether the picture on screen is the one the in-app picker set (the
 * system wallpaper id against the id the picker stored), and whether Android holds a still file at
 * all ({@code getWallpaperFile}). Reading them in one place is what keeps the activity and the
 * settings page from drifting apart — issue #37 was two readers disagreeing about the same phone.
 *
 * <p>Every call opens a file descriptor, so callers cache the answer and refresh it when the
 * wallpaper changes rather than asking per surface.
 */
public final class WallpaperPictureReader {

    private static final String LOG_TAG = "WallpaperPictureReader";
    /** Not a wallpaper id Android issues: "ask for it". */
    private static final int UNKNOWN_WALLPAPER_ID = Integer.MIN_VALUE;

    private WallpaperPictureReader() {
    }

    /**
     * @param preferences the app preferences, for the id the in-app picker stored; null (the
     *                    settings page before its preferences are built) simply means the launcher
     *                    cannot claim the picture as its own.
     */
    @NonNull
    public static WallpaperPicture read(@NonNull Context context,
                                        @Nullable TermuxAppSharedPreferences preferences) {
        return read(context, preferences, UNKNOWN_WALLPAPER_ID);
    }

    /**
     * {@link #read(Context, TermuxAppSharedPreferences)} for a caller that has just read the system
     * wallpaper id itself, so the same answer is not asked of Android twice in one breath.
     *
     * @param currentSystemWallpaperId the id as the caller read it, -1 when that read failed
     */
    @NonNull
    public static WallpaperPicture read(@NonNull Context context,
                                        @Nullable TermuxAppSharedPreferences preferences,
                                        int currentSystemWallpaperId) {
        WallpaperManager manager;
        try {
            manager = WallpaperManager.getInstance(context);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot reach the wallpaper service", e);
            return WallpaperPicture.MATCHES_SCREEN;
        }
        boolean serviceRunning = serviceRunning(manager);
        int storedId = preferences == null ? 0 : preferences.getManagedWallpaperSystemId();
        boolean launcherSet = serviceRunning && storedId > 0
            && storedId == (currentSystemWallpaperId == UNKNOWN_WALLPAPER_ID
                ? currentWallpaperId(manager) : currentSystemWallpaperId);
        boolean stillFileExists = serviceRunning && !launcherSet && stillFileExists(manager);
        return WallpaperPicturePolicy.resolve(serviceRunning, launcherSet, stillFileExists);
    }

    /**
     * The exact copy of the picture the in-app picker last set, under the app's own files. This
     * is the file the glass reads when the wallpaper is the launcher's own; whether it is on
     * screen is {@link #managedPictureOnScreen}'s question.
     */
    @NonNull
    public static File managedWallpaperExactFile(@NonNull Context context) {
        File directory = new File(context.getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-exact.png");
    }

    /**
     * Whether the wallpaper on screen is one set from inside the launcher: the system's wallpaper
     * id is the one the picker stored when it set the picture, and the picture's exact copy is
     * still on disk. The same rule the activity's blur source uses, so the settings page and the
     * chrome agree on which wallpapers Fancier Glass can work over.
     */
    public static boolean managedPictureOnScreen(@NonNull Context context,
                                                 @Nullable TermuxAppSharedPreferences preferences) {
        if (preferences == null) return false;
        int storedId = preferences.getManagedWallpaperSystemId();
        if (storedId <= 0) return false;
        WallpaperManager manager;
        try {
            manager = WallpaperManager.getInstance(context);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot reach the wallpaper service", e);
            return false;
        }
        return storedId == currentWallpaperId(manager) && managedWallpaperExactFile(context).isFile();
    }

    /** The only {@code getWallpaperInfo()} call in the app; everything else goes through here. */
    private static boolean serviceRunning(@NonNull WallpaperManager manager) {
        try {
            return manager.getWallpaperInfo() != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot tell whether a wallpaper service is running", e);
            return false;
        }
    }

    /** Android issues a new id whenever the picture or the service behind it changes. */
    private static int currentWallpaperId(@NonNull WallpaperManager manager) {
        try {
            return manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot resolve the current system wallpaper id", e);
            return -1;
        }
    }

    /**
     * Whether Android holds a still image for the system wallpaper. False under a freshly set live
     * wallpaper, and false when the read is refused — in which case no blur could be drawn anyway.
     */
    private static boolean stillFileExists(@NonNull WallpaperManager manager) {
        try (ParcelFileDescriptor fd = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
            return fd != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot tell whether a system wallpaper still exists", e);
            return false;
        }
    }
}
