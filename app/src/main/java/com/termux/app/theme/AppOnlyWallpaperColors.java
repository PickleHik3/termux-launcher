package com.termux.app.theme;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.material.color.ColorResourcesOverride;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColorUtilitiesHelper;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.SchemeContent;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's Material colours from its app-only picture (ADR 0009) instead of Android's
 * wallpaper, while that picture is on screen.
 *
 * <p>The picture's seed is taken once, off the main thread, when the picture is set
 * ({@link #takeAndStoreSeed}) — from a small downsampled decode, through Material's own
 * content-based extraction — and stored with the stamp of the copy it came from. Activity
 * creation then only reads preferences and stats the copy: the seed becomes a Material content
 * scheme, loaded over the same {@code material_personalized_color_*} resources the terminal-scheme
 * chrome uses ({@link LauncherSchemeTheme#applyPersonalizedColors}). Everything downstream reads
 * Material roles from the theme, so the chrome, Settings, the in-app keyboard and the terminal's
 * Wallpaper colours follow without knowing a seed exists.
 *
 * <p>Which source applies is {@link WallpaperColorSource}'s rule; this class gathers its facts.
 */
public final class AppOnlyWallpaperColors {

    private static final String LOG_TAG = "AppOnlyWallpaperColors";

    /** The decode's longest side is brought down to at least this many pixels, no further. */
    private static final int SEED_DECODE_MIN_SIDE_PX = 128;

    @Nullable private static ExecutorService sWorker;
    /** The picture stamp a background take is running (or ran) for; one take per picture. */
    private static long sTakingStamp;

    /**
     * The last content scheme turned into resource values, which costs a contrast search per
     * role: one per mode, indexed by dark, since the exported palettes build both.
     */
    @SuppressWarnings("unchecked")
    private static final Map<Integer, Integer>[] sCachedValues = new Map[2];
    private static final int[] sCachedSeed = new int[2];
    private static final double[] sCachedContrast = new double[2];

    private AppOnlyWallpaperColors() {}

    /** Whether this device's theme takes its roles from the wallpaper at all. */
    public static boolean isSupported() {
        // Content-based colours are loaded with ResourcesLoader (API 30), but before 31 the theme
        // is the static palette and follows no wallpaper, so there is nothing to replace.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Which source applies now. Cheap: preferences in memory, and three stats of the copy only while the app-only mark holds. */
    @NonNull
    public static WallpaperColorSource.Kind kind(@NonNull Context context,
                                                 @Nullable TermuxAppSharedPreferences preferences) {
        if (preferences == null || !isSupported() || !preferences.isWallpaperAppOnly()) {
            return WallpaperColorSource.Kind.SYSTEM;
        }
        File exact = WallpaperPictureReader.managedWallpaperExactFile(context);
        boolean onDisk = exact.isFile();
        long stamp = onDisk ? WallpaperColorSource.pictureStamp(exact.lastModified(), exact.length()) : 0L;
        return WallpaperColorSource.choose(true, LauncherSchemeTheme.isSchemeChromeActive(context),
            onDisk, stamp, preferences.getWallpaperAppOnlySeedStamp(),
            preferences.getWallpaperAppOnlySeed());
    }

    /** The seed the launcher wears now, or null for Android's wallpaper colours. */
    @Nullable
    public static Integer activeSeed(@NonNull Context context,
                                     @Nullable TermuxAppSharedPreferences preferences) {
        if (kind(context, preferences) != WallpaperColorSource.Kind.APP_ONLY) return null;
        return preferences.getWallpaperAppOnlySeed();
    }

    /** Whether the launcher wears its app-only picture's colours now. */
    public static boolean isActive(@NonNull Context context) {
        if (!isSupported()) return false;
        return activeSeed(context, TermuxAppSharedPreferences.build(context, false)) != null;
    }

    /**
     * Puts {@code seed}'s roles on {@code activity}'s theme. Must be called after {@code setTheme},
     * like {@link LauncherSchemeTheme#apply}.
     */
    public static boolean apply(@NonNull Activity activity, @ColorInt int seed) {
        try {
            if (!LauncherSchemeTheme.applyPersonalizedColors(activity,
                    resourceValues(seed, isDarkTheme(activity), systemContrast(activity)))) {
                Logger.logWarn(LOG_TAG, "App-only wallpaper colours could not be loaded");
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error applying app-only wallpaper colours", e);
            return false;
        }
    }

    /**
     * {@code themed} wearing the app-only picture's roles when they apply, else {@code themed}
     * itself. For the contexts built outside an activity to read the roles the activity would
     * have — the exported dark and light palettes and the background day/night refresh — which
     * otherwise resolve Android's wallpaper colours.
     */
    @NonNull
    @SuppressLint("RestrictedApi")
    public static Context wrap(@NonNull Context themed) {
        try {
            Integer seed = activeSeed(themed, TermuxAppSharedPreferences.build(themed, false));
            if (seed == null) return themed;
            ColorResourcesOverride override = ColorResourcesOverride.getInstance();
            if (override == null) return themed;
            return override.wrapContextIfPossible(themed,
                resourceValues(seed, isDarkTheme(themed), systemContrast(themed)));
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot wrap a context in the app-only colours", e);
            return themed;
        }
    }

    /**
     * Takes the seed for the app-only copy on disk now and stores it with that copy's stamp.
     * Blocking; never on the main thread. A picture no seed can be taken from stores
     * {@link WallpaperColorSource#NO_SEED}, so it is not tried again.
     */
    @WorkerThread
    public static void takeAndStoreSeed(@NonNull Context context,
                                        @NonNull TermuxAppSharedPreferences preferences) {
        File exact = WallpaperPictureReader.managedWallpaperExactFile(context);
        if (!exact.isFile()) return;
        long stamp = WallpaperColorSource.pictureStamp(exact.lastModified(), exact.length());
        int seed = seedOf(exact);
        preferences.setWallpaperAppOnlySeed(seed, stamp);
        Logger.logInfo(LOG_TAG, "App-only wallpaper seed: #" + Integer.toHexString(seed));
    }

    /**
     * {@link #takeAndStoreSeed} on a worker for a picture that has none yet (one set before seeds
     * were kept, or a restore), then {@code done} on the main thread. One take per picture: a
     * second ask for the same copy while or after it runs does nothing.
     */
    public static synchronized void takeSeedInBackground(@NonNull Context context,
                                                         @Nullable Runnable done) {
        Context app = context.getApplicationContext();
        File exact = WallpaperPictureReader.managedWallpaperExactFile(app);
        long stamp = exact.isFile()
            ? WallpaperColorSource.pictureStamp(exact.lastModified(), exact.length()) : 0L;
        if (stamp == 0L || stamp == sTakingStamp) return;
        sTakingStamp = stamp;
        if (sWorker == null) sWorker = Executors.newSingleThreadExecutor();
        sWorker.execute(() -> {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
            if (preferences == null) return;
            takeAndStoreSeed(app, preferences);
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        });
    }

    /** Material's content-based seed of the picture in {@code file}, or {@link WallpaperColorSource#NO_SEED}. */
    @WorkerThread
    @SuppressLint("RestrictedApi")
    private static int seedOf(@NonNull File file) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return WallpaperColorSource.NO_SEED;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return WallpaperColorSource.NO_SEED;
            Integer seed = new DynamicColorsOptions.Builder()
                .setContentBasedSource(bitmap)
                .build()
                .getContentBasedSeedColor();
            return seed == null ? WallpaperColorSource.NO_SEED : seed;
        } catch (Exception | OutOfMemoryError e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot take a seed from " + file.getName(), e);
            return WallpaperColorSource.NO_SEED;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    /** The largest power of two that keeps the longest side at {@link #SEED_DECODE_MIN_SIDE_PX} or more. */
    private static int sampleSize(int width, int height) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / (sample * 2) >= SEED_DECODE_MIN_SIDE_PX) sample *= 2;
        return sample;
    }

    /** The content scheme of {@code seed} as Material's overridable colour resources. */
    @NonNull
    @SuppressLint("RestrictedApi")
    private static synchronized Map<Integer, Integer> resourceValues(@ColorInt int seed,
                                                                     boolean dark,
                                                                     double contrast) {
        int mode = dark ? 1 : 0;
        if (sCachedValues[mode] != null && sCachedSeed[mode] == seed
                && sCachedContrast[mode] == contrast) {
            return sCachedValues[mode];
        }
        Map<Integer, Integer> values = MaterialColorUtilitiesHelper.createColorResourcesIdsToColorValues(
            new SchemeContent(Hct.fromInt(seed), dark, contrast));
        sCachedValues[mode] = values;
        sCachedSeed[mode] = seed;
        sCachedContrast[mode] = contrast;
        return values;
    }

    /** As Material's own content-based path decides it: the theme's {@code isLightTheme}. */
    private static boolean isDarkTheme(@NonNull Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true)
                && value.type == TypedValue.TYPE_INT_BOOLEAN) {
            return value.data == 0;
        }
        return (context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** The system contrast setting, as Material's content-based path reads it. */
    private static double systemContrast(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0d;
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager == null ? 0d : uiModeManager.getContrast();
    }
}
