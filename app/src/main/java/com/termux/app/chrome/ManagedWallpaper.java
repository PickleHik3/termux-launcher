package com.termux.app.chrome;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The file and system half of setting the launcher's "managed" wallpaper: hand the picture to
 * Android, keep the launcher's exact-picture copy and the stored wallpaper id in step. Runs off
 * the main thread and touches no view, so both the in-app picker
 * ({@code TermuxActivity.startManagedWallpaperApply}) and {@code POST /v1/wallpaper} share it and
 * a wallpaper set either way is the same wallpaper to the glass.
 */
public final class ManagedWallpaper {

    private static final String LOG_TAG = "ManagedWallpaper";

    /** Home screen, lock screen or both, as {@link WallpaperManager} flags. */
    public static final int FLAGS_HOME = WallpaperManager.FLAG_SYSTEM;
    public static final int FLAGS_LOCK = WallpaperManager.FLAG_LOCK;
    public static final int FLAGS_BOTH = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;

    private ManagedWallpaper() {
    }

    /** {@code home}, {@code lock} or {@code both} to its flags; 0 for anything else. */
    public static int flagsForTarget(@Nullable String target) {
        if (target == null) return 0;
        switch (target.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "home": return FLAGS_HOME;
            case "lock": return FLAGS_LOCK;
            case "both": return FLAGS_BOTH;
            default: return 0;
        }
    }

    /** The name {@link #flagsForTarget} takes for {@code flags}. */
    @NonNull
    public static String targetName(int flags) {
        boolean home = (flags & WallpaperManager.FLAG_SYSTEM) != 0;
        boolean lock = (flags & WallpaperManager.FLAG_LOCK) != 0;
        return home && lock ? "both" : lock ? "lock" : "home";
    }

    /** The screen's real size, portrait way round (width, height), from any Context. */
    @NonNull
    public static int[] portraitSize(@NonNull Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        return new int[] {Math.min(width, height), Math.max(width, height)};
    }

    /**
     * The file and system half of a wallpaper pick; runs off the main thread, touches no view.
     * {@code preferences} may be null, in which case the stored id is not updated.
     */
    public static boolean apply(@NonNull Context context, @NonNull WallpaperManager wallpaperManager,
                                @NonNull Uri source, int wallpaperFlags, int portraitWidth,
                                int portraitHeight, @Nullable TermuxAppSharedPreferences preferences) {
        Rect fullImage = fullImageBounds(context, source);
        Rect centre = systemCentre(fullImage, portraitWidth, portraitHeight);
        try {
            if (!setCentre(context, wallpaperManager, source, fullImage, centre, wallpaperFlags)) {
                return false;
            }
            if ((wallpaperFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                // The exported and exact copies are the home screen's picture; a lock-only set
                // (the Lock slot) leaves them alone.
                exportCopyToBackgroundDirectory(context, source);
                promoteTempFile(context);
                int wallpaperId = currentSystemWallpaperId(context);
                if (preferences != null) {
                    preferences.setManagedWallpaperSystemId(wallpaperId);
                }
            }
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper", e);
            return false;
        }
    }

    /**
     * Puts the home screen's picture (the launcher's exact copy) on the lock screen as well, for
     * a Lock slot that is Same as Home while Home holds a photo. Blocking; call it off the main
     * thread. False when there is no exact copy or Android refused it.
     */
    public static boolean copyHomePictureToLock(@NonNull Context context) {
        File exact = WallpaperPictureReader.managedWallpaperExactFile(context);
        if (!exact.isFile()) {
            Logger.logInfo(LOG_TAG, "No managed home picture to copy to the lock screen");
            return false;
        }
        Uri staged = stageSource(context, exact);
        if (staged == null) return false;
        int[] portrait = portraitSize(context);
        return apply(context, WallpaperManager.getInstance(context), staged, FLAGS_LOCK,
            portrait[0], portrait[1], null);
    }

    /**
     * Copies a picture into the pending file the picker's cropper writes, so {@link #apply} can
     * promote it to the launcher's exact copy exactly as it does after a pick.
     *
     * @return the pending file's URI, or null when the copy failed
     */
    @Nullable
    public static Uri stageSource(@NonNull Context context, @NonNull File source) {
        File pending = tempFile(context);
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(pending, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
            return Uri.fromFile(pending);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to stage the wallpaper picture", e);
            return null;
        }
    }

    /** The picture's pixel bounds, or null when it is not a readable image. */
    @Nullable
    public static Rect fullImageBounds(@NonNull Context context, @NonNull Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream inputStream = openInputStream(context, uri)) {
            if (inputStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(inputStream, null, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            return new Rect(0, 0, options.outWidth, options.outHeight);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read wallpaper crop bounds", e);
            return null;
        }
    }

    /**
     * The screen-sized centre of the picked picture, in its own pixels: as tall as the picture,
     * and as wide as the portrait screen is at that height. The whole picture when it is not
     * wider than that — a picker that could not resize, or an unreadable header.
     */
    @Nullable
    public static Rect systemCentre(@Nullable Rect fullImage, int portraitWidth, int portraitHeight) {
        if (fullImage == null || portraitWidth <= 0 || portraitHeight <= 0) return fullImage;
        int centreWidth = Math.round((float) fullImage.height() * portraitWidth / portraitHeight);
        if (centreWidth <= 0 || centreWidth >= fullImage.width()) return fullImage;
        int left = (fullImage.width() - centreWidth) / 2;
        return new Rect(left, 0, left + centreWidth, fullImage.height());
    }

    /**
     * Hands the system the screen-sized centre of the wide picture — cut here, so Android never
     * sees the wide image and cannot choose to fit it its own way — and falls back to streaming
     * the picture with the centre as its crop hint when the region cannot be decoded. A picture
     * that is not wider than the screen streams whole, exactly as before.
     */
    private static boolean setCentre(@NonNull Context context, @NonNull WallpaperManager wallpaperManager,
                                     @NonNull Uri source, @Nullable Rect fullImage,
                                     @Nullable Rect centre, int wallpaperFlags) {
        if (centre == null || centre.equals(fullImage)) {
            return setStream(context, wallpaperManager, source, fullImage, wallpaperFlags);
        }
        Bitmap region = null;
        try (InputStream inputStream = openInputStream(context, source)) {
            if (inputStream != null) {
                android.graphics.BitmapRegionDecoder decoder =
                    android.graphics.BitmapRegionDecoder.newInstance(inputStream, false);
                if (decoder != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    region = decoder.decodeRegion(centre, options);
                    decoder.recycle();
                }
            }
            if (region != null) {
                wallpaperManager.setBitmap(region, null, true, wallpaperFlags);
                return true;
            }
        } catch (Exception | OutOfMemoryError e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply the managed wallpaper's centre; streaming with a crop hint", e);
        } finally {
            if (region != null) region.recycle();
        }
        return setStream(context, wallpaperManager, source, centre, wallpaperFlags);
    }

    private static boolean setStream(@NonNull Context context, @NonNull WallpaperManager wallpaperManager,
                                     @NonNull Uri source, @Nullable Rect visibleCropHint,
                                     int wallpaperFlags) {
        if (visibleCropHint != null) {
            try (InputStream inputStream = openInputStream(context, source)) {
                if (inputStream == null) {
                    return false;
                }
                wallpaperManager.setStream(inputStream, visibleCropHint, true, wallpaperFlags);
                return true;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper with crop hint; retrying without hint", e);
            }
        }

        try (InputStream inputStream = openInputStream(context, source)) {
            if (inputStream == null) {
                return false;
            }
            wallpaperManager.setStream(inputStream, null, true, wallpaperFlags);
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper without crop hint", e);
            return false;
        }
    }

    private static void exportCopyToBackgroundDirectory(@NonNull Context context, @NonNull Uri source) {
        File backgroundDir = TermuxConstants.TERMUX_BACKGROUND_DIR;
        if (!backgroundDir.exists() && !backgroundDir.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create termux background directory at: " + backgroundDir.getAbsolutePath());
            return;
        }

        File destination = TermuxConstants.TERMUX_BACKGROUND_IMAGE_FILE;
        try (InputStream inputStream = openInputStream(context, source);
             FileOutputStream outputStream = new FileOutputStream(destination, false)) {
            if (inputStream == null) {
                Logger.logError(LOG_TAG, "Failed to export wallpaper copy: could not open source stream");
                return;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to export wallpaper copy to " + destination.getAbsolutePath(), e);
        }
    }

    @Nullable
    private static InputStream openInputStream(@NonNull Context context, @NonNull Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) {
                return new FileInputStream(new File(uri.getPath()));
            }
            return context.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open wallpaper stream", e);
            return null;
        }
    }

    /** Where the picker's cropper writes the picture before it is applied. */
    @NonNull
    public static File tempFile(@NonNull Context context) {
        File directory = new File(context.getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-pending.png");
    }

    private static void promoteTempFile(@NonNull Context context) {
        File tempFile = tempFile(context);
        if (!tempFile.isFile()) {
            return;
        }
        File exactFile = WallpaperPictureReader.managedWallpaperExactFile(context);
        if (exactFile.exists()) {
            exactFile.delete();
        }
        if (!tempFile.renameTo(exactFile)) {
            Logger.logError(LOG_TAG, "Failed to promote managed wallpaper temp file");
        }
    }

    public static int currentSystemWallpaperId(@NonNull Context context) {
        try {
            return WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve current system wallpaper id", e);
            return -1;
        }
    }
}
