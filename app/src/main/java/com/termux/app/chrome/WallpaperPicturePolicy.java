package com.termux.app.chrome;

import androidx.annotation.NonNull;

/** The one rule that turns three facts about the system wallpaper into a {@link WallpaperPicture}. */
public final class WallpaperPicturePolicy {

    private WallpaperPicturePolicy() {
    }

    /**
     * @param serviceRunning            a wallpaper service is running, as read by
     *                                  {@link WallpaperPictureReader}
     * @param launcherSetCurrentWallpaper the system wallpaper id equals the id the in-app picker
     *                                  stored when it set the picture; Android issues a new id
     *                                  whenever the picture or the service changes
     * @param stillFileExists           {@code WallpaperManager.getWallpaperFile(FLAG_SYSTEM)} is
     *                                  non-null; setting a live wallpaper clears that file
     */
    @NonNull
    public static WallpaperPicture resolve(boolean serviceRunning, boolean launcherSetCurrentWallpaper,
                                           boolean stillFileExists) {
        if (!serviceRunning || launcherSetCurrentWallpaper) {
            return WallpaperPicture.MATCHES_SCREEN;
        }
        return stillFileExists ? WallpaperPicture.BEST_EFFORT : WallpaperPicture.NO_STILL;
    }
}
