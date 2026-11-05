package com.termux.app.chrome.wallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.app.chrome.scene.SceneStill;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * What Use as wallpaper and Back to photo do beyond the scene choice itself: the still on the
 * phone's Home and Lock, and the photos that were there before it. The decisions are
 * {@link LiveSceneLogic} and {@link LiveRestorePlan}; this is their I/O.
 *
 * <p>Both calls write the preferences on the calling (main) thread first, so the running scene
 * starts or stops at once, and then work on the shared apply worker
 * ({@link GeneratedWallpaperApplier}), behind any apply already queued. The still goes to the
 * phone through {@link ManagedWallpaper#apply} like a photo, but is not a recent photo. A GPU
 * failure leaves the scene on in the app and the phone's wallpaper as it was.</p>
 */
public final class LiveWallpaperApplier {

    private static final String LOG_TAG = "LiveWallpaperApplier";

    /** How a call ended; always delivered on the main thread. */
    public enum Result {
        /** The phone's Home and Lock changed. */
        SET,
        /** Done, and the phone's wallpaper was not touched. */
        IN_APP_ONLY,
        /** The still could not be drawn: the scene runs in the app, the phone's wallpaper stays. */
        STILL_FAILED,
        /** The phone refused the picture, or a file could not be written. */
        FAILED,
    }

    public interface Callback {
        void onDone(@NonNull Result result);
    }

    private LiveWallpaperApplier() {}

    // --- Use as wallpaper ---

    /**
     * Starts {@code choice} in the app and, when {@link LiveSceneLogic#setsSystemWallpaper} says so,
     * puts its t = 0 still on Home and Lock, remembering what they showed.
     *
     * @param launcherIsHomeApp the launcher is the phone's home app
     * @param alsoPhone         "Also set as phone wallpaper" (terminal-app mode)
     */
    @MainThread
    public static void useScene(@NonNull Context ctx, @NonNull LiveWallpaperChoice choice,
                                boolean launcherIsHomeApp, boolean alsoPhone, @Nullable Callback cb) {
        final Context app = ctx.getApplicationContext();
        final TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs == null || choice.scene == null) {
            post(cb, Result.FAILED);
            return;
        }
        choice.write(prefs);
        final boolean touches = LiveSceneLogic.setsSystemWallpaper(launcherIsHomeApp, alsoPhone);
        if (!touches) {
            post(cb, Result.IN_APP_ONLY);
            return;
        }
        GeneratedWallpaperApplier.onWorker(() -> post(cb, setStill(app, prefs, choice)));
    }

    @WorkerThread
    @NonNull
    private static Result setStill(@NonNull Context app, @NonNull TermuxAppSharedPreferences prefs,
                                   @NonNull LiveWallpaperChoice choice) {
        try {
            int[] size = ManagedWallpaper.portraitSize(app);
            int[] still = LiveSceneLogic.stillSize(size[0], size[1]);
            Bitmap bitmap;
            try {
                bitmap = SceneStill.render(app, choice.scene, choice, still[0], still[1]);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Drawing the scene still failed", e);
                return Result.STILL_FAILED;
            }
            if (bitmap == null) return Result.STILL_FAILED;
            File stillFile = new File(dir(app, LiveRestorePlan.STILL_DIR), LiveRestorePlan.STILL_FILE);
            try (FileOutputStream out = new FileOutputStream(stillFile, false)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return Result.FAILED;
                out.flush();
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Keeping the scene still failed", e);
                return Result.FAILED;
            } finally {
                bitmap.recycle();
            }

            LiveRestorePlan.Memory stored = readMemory(app);
            int homeId = ManagedWallpaper.currentSystemWallpaperId(app);
            int lockId = ManagedWallpaper.currentLockWallpaperId(app);
            LiveRestorePlan.Remember remember = LiveRestorePlan.remember(true, stored, homeId, lockId);
            LiveRestorePlan.Memory memory = stored;
            if (remember == LiveRestorePlan.Remember.TAKE) memory = takeMemory(app);

            Uri staged = ManagedWallpaper.stageSource(app, stillFile);
            if (staged == null) return Result.FAILED;
            WallpaperManager wm = WallpaperManager.getInstance(app);
            try {
                wm.suggestDesiredDimensions(size[0], size[1]);
            } catch (RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Suggesting wallpaper dimensions failed: " + e.getMessage());
            }
            if (!ManagedWallpaper.apply(app, wm, staged, ManagedWallpaper.FLAGS_BOTH, size[0], size[1], prefs)) {
                // Nothing was set: a memory taken for this set goes again.
                if (remember == LiveRestorePlan.Remember.TAKE) clearMemory(app);
                return Result.FAILED;
            }
            // Both screens show the still now: the lock follows Home, as after any Home set.
            prefs.setWallpaperLockChoice(TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME);
            if (memory != null) {
                writeMemory(app, memory.withStillIds(ManagedWallpaper.currentSystemWallpaperId(app),
                    ManagedWallpaper.currentLockWallpaperId(app)));
            } else {
                clearMemory(app);
            }
            Logger.logInfo(LOG_TAG, "Scene " + choice.scene + " still set on Home and Lock ("
                + remember + (memory == null ? ", nothing to remember" : "") + ")");
            return Result.SET;
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Setting the scene still failed", e);
            return Result.FAILED;
        }
    }

    // --- Back to photo ---

    /**
     * Stops the scene and puts the remembered photos back on Home and Lock. With nothing
     * remembered, or with the phone's wallpaper changed since the still, the phone's wallpaper is
     * left as it is.
     */
    @MainThread
    public static void backToPhoto(@NonNull Context ctx, @Nullable Callback cb) {
        final Context app = ctx.getApplicationContext();
        final TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs == null) {
            post(cb, Result.FAILED);
            return;
        }
        LiveWallpaperChoice.none().write(prefs);
        GeneratedWallpaperApplier.onWorker(() -> post(cb, restore(app)));
    }

    @WorkerThread
    @NonNull
    private static Result restore(@NonNull Context app) {
        try {
            LiveRestorePlan.Memory memory = readMemory(app);
            int homeId = ManagedWallpaper.currentSystemWallpaperId(app);
            int lockId = ManagedWallpaper.currentLockWallpaperId(app);
            if (!LiveRestorePlan.shouldRestore(memory, homeId, lockId)) {
                clearMemory(app);
                deleteStill(app);
                return Result.IN_APP_ONLY;
            }
            File restoreDir = dir(app, LiveRestorePlan.DIR);
            for (LiveRestorePlan.Step step : LiveRestorePlan.steps(memory)) {
                String error;
                switch (step.kind) {
                    case HOME_APP_ONLY:
                        error = WallpaperSlots.applyAppOnlyNow(app, new File(restoreDir, LiveRestorePlan.HOME_FILE));
                        break;
                    case LOCK_PHONE:
                        error = WallpaperSlots.applyPhotoNow(app, new File(restoreDir, LiveRestorePlan.LOCK_FILE),
                            step.flags, false, step.recordLock);
                        break;
                    case HOME_PHONE:
                    default:
                        error = WallpaperSlots.applyPhotoNow(app, new File(restoreDir, LiveRestorePlan.HOME_FILE),
                            step.flags, true, step.recordLock);
                        break;
                }
                if (error != null) {
                    Logger.logError(LOG_TAG, "Putting the photo back failed at " + step.kind + ": " + error);
                    return Result.FAILED;
                }
            }
            clearMemory(app);
            deleteStill(app);
            Logger.logInfo(LOG_TAG, "Photos put back on Home and Lock");
            return Result.SET;
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Putting the photos back failed", e);
            return Result.FAILED;
        }
    }

    // --- a photo chosen on the Photo page ---

    /**
     * The user applied a photo themselves ({@link LiveSceneLogic#photoEndsScene}): the scene ends
     * (so nothing keeps drawing over the photo) and a memory the new wallpaper made stale is
     * dropped ({@link LiveRestorePlan#dropOnPhotoChosen}). Writes nothing while no scene is in use.
     */
    @MainThread
    public static void photoChosen(@NonNull Context ctx) {
        final Context app = ctx.getApplicationContext();
        final TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs != null && LiveWallpaperChoice.read(prefs).scene != null) {
            LiveWallpaperChoice.none().write(prefs);
        }
        GeneratedWallpaperApplier.onWorker(() -> {
            try {
                LiveRestorePlan.Memory memory = readMemory(app);
                if (LiveRestorePlan.dropOnPhotoChosen(memory, ManagedWallpaper.currentSystemWallpaperId(app),
                        ManagedWallpaper.currentLockWallpaperId(app))) {
                    clearMemory(app);
                    deleteStill(app);
                }
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Dropping the photo memory failed", e);
            }
        });
    }

    // --- the memory on disk ---

    @NonNull
    private static File dir(@NonNull Context app, @NonNull String name) {
        File dir = new File(new File(app.getFilesDir(), "wallpaper"), name);
        if (!dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @Nullable
    private static LiveRestorePlan.Memory readMemory(@NonNull Context app) {
        File marker = new File(dir(app, LiveRestorePlan.DIR), LiveRestorePlan.MARKER_FILE);
        if (!marker.isFile()) return null;
        try {
            LiveRestorePlan.Memory memory = LiveRestorePlan.Memory.parse(
                new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
            if (memory == null) return null;
            File dir = marker.getParentFile();
            // A memory whose pictures are gone has nothing to put back.
            if (memory.hasHome && !new File(dir, LiveRestorePlan.HOME_FILE).isFile()) return null;
            if (memory.hasLockFile && !new File(dir, LiveRestorePlan.LOCK_FILE).isFile()) return null;
            return memory;
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Reading the photo memory failed: " + e.getMessage());
            return null;
        }
    }

    private static void writeMemory(@NonNull Context app, @NonNull LiveRestorePlan.Memory memory) {
        File dir = dir(app, LiveRestorePlan.DIR);
        File marker = new File(dir, LiveRestorePlan.MARKER_FILE);
        File staged = new File(dir, LiveRestorePlan.MARKER_FILE + ".tmp");
        try {
            Files.write(staged.toPath(), memory.serialize().getBytes(StandardCharsets.UTF_8));
            if (!staged.renameTo(marker)) Logger.logError(LOG_TAG, "Promoting the photo memory failed");
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Writing the photo memory failed", e);
        }
    }

    /** Forgets the memory: the marker first (the reader's gate), then the pictures. */
    private static void clearMemory(@NonNull Context app) {
        File dir = dir(app, LiveRestorePlan.DIR);
        for (String name : new String[] {LiveRestorePlan.MARKER_FILE, LiveRestorePlan.MARKER_FILE + ".tmp",
            LiveRestorePlan.HOME_FILE, LiveRestorePlan.LOCK_FILE}) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, name).delete();
        }
    }

    /** The kept still: not needed once the photos are back (or were never to come back). */
    private static void deleteStill(@NonNull Context app) {
        //noinspection ResultOfMethodCallIgnored
        new File(dir(app, LiveRestorePlan.STILL_DIR), LiveRestorePlan.STILL_FILE).delete();
    }

    /**
     * Copies what Home and Lock hold now into the memory's folder. The marker is written by the
     * caller once the still is set. Null when there is nothing to put back.
     */
    @WorkerThread
    @Nullable
    private static LiveRestorePlan.Memory takeMemory(@NonNull Context app) {
        clearMemory(app);
        WallpaperSlots.State state = WallpaperSlots.read(app);
        File dir = dir(app, LiveRestorePlan.DIR);
        boolean home = copy(state.home.photoFile, new File(dir, LiveRestorePlan.HOME_FILE));
        boolean ownsLock = state.lockUnderAppOnly || !state.lock.sameAsHome;
        boolean lock = ownsLock && copy(state.lock.photoFile, new File(dir, LiveRestorePlan.LOCK_FILE));
        LiveRestorePlan.Memory memory = LiveRestorePlan.memoryOf(state, home, lock);
        if (!memory.hasAnything()) {
            clearMemory(app);
            return null;
        }
        return memory;
    }

    private static boolean copy(@Nullable File from, @NonNull File to) {
        if (from == null || !from.isFile()) return false;
        try {
            RecentWallpapers.copy(from, to);
            return to.isFile() && to.length() > 0;
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Keeping a photo for later failed", e);
            return false;
        }
    }

    private static void post(@Nullable Callback cb, @NonNull Result result) {
        if (cb == null) return;
        GeneratedWallpaperApplier.post((ok, error) -> cb.onDone(result), true, null);
    }
}
