package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The Home and Lock wallpaper slots: the one seam between the wallpaper picker and what each slot
 * holds. The picker reads and applies through here only; WallpaperManager and the slot
 * preferences stay behind it.
 *
 * <p>Storage: the Lock slot is {@code wallpaper_lock_choice} ({@code same_as_home} or
 * {@code photo}); the Home slot is always a photo. What each call does is decided by
 * {@link WallpaperSlotPlan}.</p>
 *
 * <p>The Lock slot also notices a lock wallpaper set by another app: {@code managed_wallpaper_lock_id}
 * is the system's lock id after the launcher's last set; when the system reports another one, the
 * Lock slot shows the system's picture ({@code wallpaper/slots/lock-system.png}) and Home applies
 * leave the lock screen alone. The stored choice changes only when the user applies a Lock choice.</p>
 *
 * <p>Photos, all under the app's private files: a cropped photo waits in
 * {@code wallpaper/pending/<timestamp>.png} until the picker page closes; the Home slot's picture
 * is the managed exact copy ({@code managed-wallpaper/system-wallpaper-exact.png}); the Lock
 * slot's own photo is kept as {@code wallpaper/slots/lock.png}; the last photos applied are
 * {@link RecentWallpapers} in {@code wallpaper/recent/}.</p>
 */
public final class WallpaperSlots {

    private static final String LOG_TAG = "WallpaperSlots";

    private WallpaperSlots() {}

    /** Which slot a choice goes to. */
    public enum Slot { HOME, LOCK }

    /** What one slot holds: a photo, or (Lock only) the Home slot's. */
    public static final class Choice {
        /** A photo the user picked, shown as a still. */
        public final boolean photo;
        /** The photo's picture when known; null for {@link #photo()} ("a photo, picture unknown"). */
        @Nullable public final File photoFile;
        /** Lock only: follows the Home slot. */
        public final boolean sameAsHome;

        private Choice(boolean photo, @Nullable File photoFile, boolean sameAsHome) {
            this.photo = photo;
            this.photoFile = photoFile;
            this.sameAsHome = sameAsHome;
        }

        /** The slot holds a photo whose picture is not known here; applying it records the slot only. */
        @NonNull public static Choice photo() { return new Choice(true, null, false); }
        /** The photo in {@code file}: applying it sets that picture, with no new crop. */
        @NonNull public static Choice photo(@NonNull File file) { return new Choice(true, file, false); }
        @NonNull public static Choice sameAsHome() { return new Choice(false, null, true); }

        @Override @NonNull public String toString() {
            if (sameAsHome) return "same_as_home";
            return photoFile == null ? "photo" : "photo:" + photoFile.getName();
        }
    }

    /** Both slots as they are now. */
    public static final class State {
        @NonNull public final Choice home;
        @NonNull public final Choice lock;
        /** Another app set the lock screen's wallpaper; {@link #lock} then shows the system's picture. */
        public final boolean lockElsewhere;

        public State(@NonNull Choice home, @NonNull Choice lock) {
            this(home, lock, false);
        }

        public State(@NonNull Choice home, @NonNull Choice lock, boolean lockElsewhere) {
            this.home = home;
            this.lock = lock;
            this.lockElsewhere = lockElsewhere;
        }
    }

    /** Called on the main thread. */
    public interface Callback {
        void onDone(boolean ok, @Nullable String error);
    }

    /** Both slots as stored and as the system has them now. The lock screen is checked for an outside change only off the main thread (system calls, a file copy). */
    @WorkerThread
    @NonNull
    public static State read(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        String lock = prefs == null ? TERMUX_APP.DEFAULT_VALUE_WALLPAPER_LOCK_CHOICE : prefs.getWallpaperLockChoice();
        // Home's photo is the managed exact copy while the system still shows it; Lock's is its
        // own kept copy.
        File homePhoto = null;
        if (prefs != null) {
            int stored = prefs.getManagedWallpaperSystemId();
            File exact = WallpaperPictureReader.managedWallpaperExactFile(app);
            if (stored > 0 && stored == ManagedWallpaper.currentSystemWallpaperId(app) && exact.isFile()) {
                homePhoto = exact;
            }
        }
        File lockCopy = lockPhotoFile(app);
        State state = stateFrom(lock, homePhoto, lockCopy.isFile() ? lockCopy : null);
        // A caller on the main thread gets the stored Lock slot only: no lock query or file copy on a frame.
        if (prefs == null || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return state;
        int storedLockId = prefs.getManagedWallpaperLockId();
        int currentLockId = ManagedWallpaper.currentLockWallpaperId(app);
        if (isLockElsewhere(storedLockId, currentLockId)) {
            File outside = systemLockPicture(app);
            Logger.logInfo(LOG_TAG, "Lock screen wallpaper was set elsewhere"
                + (outside == null ? "; its picture could not be read" : ""));
            return new State(state.home, outside != null ? Choice.photo(outside) : Choice.photo(), true);
        }
        if (needsLockBaseline(storedLockId, currentLockId)) prefs.setManagedWallpaperLockId(currentLockId);
        return state;
    }

    /**
     * Whether the system's lock wallpaper is not the one the launcher last saw: a baseline was
     * stored, the current id is readable, and they differ. No baseline (an upgrade) is not
     * elsewhere.
     */
    static boolean isLockElsewhere(int storedId, int currentId) {
        return storedId != 0 && currentId != 0 && storedId != currentId;
    }

    /** Whether the current lock id should be stored as the first baseline. */
    static boolean needsLockBaseline(int storedId, int currentId) {
        return storedId == 0 && currentId != 0;
    }

    /**
     * Copies the system's lock picture (Home's when the lock shares it) to
     * {@code wallpaper/slots/lock-system.png}, or null when Android gives no readable file.
     */
    @WorkerThread
    @Nullable
    private static File systemLockPicture(@NonNull Context app) {
        File target = new File(lockPhotoFile(app).getParentFile(), "lock-system.png");
        File staged = new File(target.getParentFile(), target.getName() + ".tmp");
        WallpaperManager wm = WallpaperManager.getInstance(app);
        for (int which : new int[] {WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM}) {
            try (android.os.ParcelFileDescriptor fd = wm.getWallpaperFile(which)) {
                if (fd == null) continue;
                try (java.io.InputStream in = new java.io.FileInputStream(fd.getFileDescriptor());
                     java.io.FileOutputStream out = new java.io.FileOutputStream(staged, false)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();
                }
                if (staged.length() > 0 && staged.renameTo(target)) return target;
            } catch (IOException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Reading the system lock picture failed: " + e.getMessage());
            }
        }
        //noinspection ResultOfMethodCallIgnored
        staged.delete();
        return null;
    }

    /**
     * Applies {@code choice} to {@code slot}. A photo with its picture ({@link Choice#photo(File)})
     * is set on a worker through {@link #applyPhotoNow}: Home with {@code FLAG_SYSTEM} (plus
     * {@code FLAG_LOCK} when Lock is Same as Home), Lock with {@code FLAG_LOCK}. {@link Choice#photo()}
     * with no picture is recorded only.
     */
    public static void apply(@NonNull Activity activity, @NonNull Slot slot, @NonNull Choice choice,
                             @Nullable Callback cb) {
        Context app = activity.getApplicationContext();
        // The plan needs the Lock slot as the system has it now, which is read off the main thread.
        GeneratedWallpaperApplier.onWorker(() -> {
            WallpaperSlotPlan plan;
            try {
                plan = WallpaperSlotPlan.forApply(slot, choice, inputs(app));
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Planning the wallpaper apply failed", e);
                GeneratedWallpaperApplier.post(cb == null ? null : cb::onDone, false, "wallpaper_failed");
                return;
            }
            execute(app, plan, cb);
        });
    }

    // --- package-private helpers ---

    /** {@link #read}'s mapping from stored values, without Android; each slot's kept picture is for a slot that holds a photo. */
    @NonNull
    static State stateFrom(@Nullable String lockValue, @Nullable File homePhoto, @Nullable File lockPhoto) {
        Choice home = homePhoto != null ? Choice.photo(homePhoto) : Choice.photo();
        Choice lock = WallpaperSlotPlan.lockChoice(TermuxAppSharedPreferences.normaliseWallpaperLockChoice(lockValue));
        if (lock.photo && lockPhoto != null) lock = Choice.photo(lockPhoto);
        return new State(home, lock);
    }

    /** A Lock choice as {@code GET /v1/wallpaper}'s {@code lock_slot}: same_as_home or photo. */
    @NonNull
    public static String lockSlotName(@NonNull Choice lock) {
        return lock.sameAsHome ? TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME : TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
    }

    // --- photos ---

    /**
     * Sets {@code picture} as a photo wallpaper with {@code flags} (WallpaperManager flags) and
     * records it: {@code recordHome} makes it the Home slot; {@code recordLock}, when not null, is
     * the stored Lock choice ({@code photo} also keeps a copy as the Lock slot's picture). The
     * picture joins the recent photos.
     *
     * <p>Blocking ({@code setStream} waits for system_server): call it off the main thread. The
     * picker's Apply and {@code POST /v1/wallpaper} both end here.</p>
     *
     * @return null on success, else {@code not_found}, {@code stage_failed} or {@code wallpaper_failed}
     */
    @WorkerThread
    @Nullable
    public static String applyPhotoNow(@NonNull Context ctx, @NonNull File picture, int flags,
                                       boolean recordHome, @Nullable String recordLock) {
        Context app = ctx.getApplicationContext();
        if (!picture.isFile()) return "not_found";
        boolean home = (flags & WallpaperManager.FLAG_SYSTEM) != 0;
        boolean lock = (flags & WallpaperManager.FLAG_LOCK) != 0;
        boolean keepLockCopy = lock && TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(recordLock);
        // The Lock copy is taken before the set: the picture may be the Lock copy itself.
        File lockCopy = lockPhotoFile(app);
        File lockStaged = new File(lockCopy.getParentFile(), lockCopy.getName() + ".tmp");
        if (keepLockCopy) {
            try {
                RecentWallpapers.copy(picture, lockStaged);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Keeping the lock photo failed", e);
                keepLockCopy = false;
            }
        }
        Uri staged = ManagedWallpaper.stageSource(app, picture);
        if (staged == null) {
            //noinspection ResultOfMethodCallIgnored
            lockStaged.delete();
            return "stage_failed";
        }
        WallpaperManager wm = WallpaperManager.getInstance(app);
        if (home) suggestDimensions(app, wm);
        int[] portrait = ManagedWallpaper.portraitSize(app);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        boolean ok = ManagedWallpaper.apply(app, wm, staged, flags, portrait[0], portrait[1], prefs);
        if (!ok) {
            //noinspection ResultOfMethodCallIgnored
            lockStaged.delete();
            return "wallpaper_failed";
        }
        if (keepLockCopy && !lockStaged.renameTo(lockCopy)) {
            Logger.logError(LOG_TAG, "Promoting the lock photo copy failed");
        }
        if (recordLock != null && prefs != null) prefs.setWallpaperLockChoice(recordLock);
        if (recordHome) Logger.logInfo(LOG_TAG, "Home slot: photo");
        if (recordLock != null) Logger.logInfo(LOG_TAG, "Lock slot: " + recordLock);
        try {
            recents(app).add(picture, System.currentTimeMillis());
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Keeping the recent photo failed", e);
        }
        return null;
    }

    /** The recent photos, newest first (reads a small index). */
    @NonNull
    public static List<File> recentPhotos(@NonNull Context ctx) {
        try {
            return recents(ctx.getApplicationContext()).list();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Listing the recent photos failed", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    static RecentWallpapers recents(@NonNull Context app) {
        return new RecentWallpapers(new File(photoRoot(app), "recent"));
    }

    @NonNull
    private static File photoRoot(@NonNull Context app) {
        return new File(app.getFilesDir(), "wallpaper");
    }

    /** Where the Lock slot's own photo is kept. */
    @NonNull
    static File lockPhotoFile(@NonNull Context app) {
        File dir = new File(photoRoot(app), "slots");
        if (!dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, "lock.png");
    }

    @NonNull
    private static File pendingDir(@NonNull Context app) {
        return new File(photoRoot(app), "pending");
    }

    /**
     * The photo cropper's output ({@link ManagedWallpaper#tempFile}) becomes a pending photo for
     * the picker to preview, out of the way of any other set that writes the cropper's file.
     *
     * @return the pending photo, or null when there is no crop to take
     */
    @Nullable
    public static File adoptCroppedPhoto(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        File crop = ManagedWallpaper.tempFile(app);
        if (!crop.isFile() || crop.length() == 0) return null;
        File dir = pendingDir(app);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        long stamp = System.currentTimeMillis();
        File target = new File(dir, stamp + ".png");
        while (target.exists()) target = new File(dir, (++stamp) + ".png");
        if (crop.renameTo(target)) return target;
        try {
            RecentWallpapers.copy(crop, target);
            //noinspection ResultOfMethodCallIgnored
            crop.delete();
            return target;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Taking the cropped photo failed", e);
            return null;
        }
    }

    /** Deletes {@code file} when it is a pending photo; any other file is left alone. */
    public static void discardPendingPhoto(@NonNull Context ctx, @NonNull File file) {
        File dir = pendingDir(ctx.getApplicationContext());
        File parent = file.getParentFile();
        if (parent == null || !parent.getAbsolutePath().equals(dir.getAbsolutePath())) return;
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Deletes every pending photo: a fresh page has none, so these were left by a lost return. */
    public static void clearPendingPhotos(@NonNull Context ctx) {
        File[] files = pendingDir(ctx.getApplicationContext()).listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void suggestDimensions(@NonNull Context app, @NonNull WallpaperManager wm) {
        try {
            DisplayMetrics real = new DisplayMetrics();
            WindowManager windows = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (windows == null) return;
            windows.getDefaultDisplay().getRealMetrics(real);
            wm.suggestDesiredDimensions(Math.max(1, real.widthPixels), Math.max(1, real.heightPixels));
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Suggesting wallpaper dimensions failed: " + e.getMessage());
        }
    }

    // --- retired live wallpapers ---

    /** Old preference keys and values of the removed live wallpaper; read only by the migration. */
    private static final String RETIRED_HOME_ANIMATED_KEY = "managed_wallpaper_animated";
    private static final String RETIRED_LOCK_MOTION_KEY = "wallpaper_lock_motion";
    private static final String RETIRED_HOME_MOTION_KEY = "wallpaper_home_motion";
    private static final String RETIRED_ANIMATED_DISABLED_KEY = "animated_wallpaper_disabled";
    private static final String[] RETIRED_HOME_ANIMATED_EXTRA_KEYS = {
        "managed_wallpaper_animated_palette", "managed_wallpaper_animated_target", "managed_wallpaper_animated_colors",
    };
    private static final String RETIRED_LOCK_ANIMATED_PREFIX = "animated:";
    private static final String RETIRED_HOME_LIVING_PREFIX = "living:";
    private static final String RETIRED_LOCK_SERVICE = "com.termux.app.chrome.wallpaper.LockLiveWallpaperService";

    private static final java.util.concurrent.atomic.AtomicBoolean sRetiredLiveChecked =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /** What the migration does about the Lock slot. */
    enum RetiredLockAction {
        /** The lock screen needs nothing. */
        NONE,
        /** Set the resolved photo on the lock screen and store {@code photo}. */
        APPLY_PHOTO,
        /** No photo to be found: copy the Home picture to the lock screen, then store {@code same_as_home}. */
        COPY_HOME_AND_STORE_SAME_AS_HOME,
        /** Copy the Home picture to the lock screen; the stored choice stays. */
        COPY_HOME_PICTURE,
    }

    /** What the migration does, decided by {@link #planRetiredLive}. */
    static final class RetiredPlan {
        /** The stored Home id is forgotten (its picture is already the photo). */
        final boolean clearHome;
        /** The exact Home copy is set on the home screen again: our service took it. */
        final boolean reapplyHome;
        @NonNull final RetiredLockAction lock;

        RetiredPlan(boolean clearHome, boolean reapplyHome, @NonNull RetiredLockAction lock) {
            this.clearHome = clearHome;
            this.reapplyHome = reapplyHome;
            this.lock = lock;
        }
    }

    /**
     * What to do about the removed live wallpaper, from what is stored and what is found. Keyed off
     * the stored values as well as the lock service, because Android may already have reset the
     * wallpaper when the service vanished.
     *
     * @param homeId          the raw stored Home id ({@code living:<hash>} or any non-empty id), or null
     * @param lockRaw         the raw stored {@code wallpaper_lock_choice}, or null
     * @param lockServiceHeld the lock (or home) wallpaper is our retired service
     * @param photoFound      the lock choice's photo was found ({@code animated:} resolved, or the kept
     *                        lock photo for a {@code photo} choice)
     * @param systemMoved     the stored system wallpaper id is not the current one
     * @param exactExists     the exact Home copy exists
     */
    @NonNull
    static RetiredPlan planRetiredLive(@Nullable String homeId, @Nullable String lockRaw, boolean lockServiceHeld,
                                       boolean photoFound, boolean systemMoved, boolean exactExists) {
        boolean homeRetired = homeId != null && !homeId.trim().isEmpty();
        boolean reapplyHome = homeRetired && systemMoved && exactExists;
        RetiredLockAction lock = RetiredLockAction.NONE;
        if (isRetiredAnimatedLock(lockRaw)) {
            lock = photoFound ? RetiredLockAction.APPLY_PHOTO : RetiredLockAction.COPY_HOME_AND_STORE_SAME_AS_HOME;
        } else {
            boolean ownPhoto = TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(lockRaw);
            if (lockServiceHeld || (homeRetired && !ownPhoto)) {
                lock = ownPhoto && photoFound ? RetiredLockAction.APPLY_PHOTO : RetiredLockAction.COPY_HOME_PICTURE;
            }
        }
        return new RetiredPlan(homeRetired, reapplyHome, lock);
    }

    /** Whether the raw lock choice is {@code animated:<id>} with a non-empty id. */
    static boolean isRetiredAnimatedLock(@Nullable String lockRaw) {
        return lockRaw != null && lockRaw.trim().startsWith(RETIRED_LOCK_ANIMATED_PREFIX)
            && lockRaw.trim().length() > RETIRED_LOCK_ANIMATED_PREFIX.length();
    }

    /** The hex hash of a stored {@code animated:living:<hash>}, {@code living:<hash>} or bare id, or null when it is not hex. */
    @Nullable
    static String retiredLivingHash(@Nullable String id) {
        if (id == null) return null;
        String t = id.trim();
        if (t.startsWith(RETIRED_LOCK_ANIMATED_PREFIX)) t = t.substring(RETIRED_LOCK_ANIMATED_PREFIX.length());
        if (t.startsWith(RETIRED_HOME_LIVING_PREFIX)) t = t.substring(RETIRED_HOME_LIVING_PREFIX.length());
        return t.matches("[0-9a-fA-F]+") ? t.toLowerCase(java.util.Locale.ROOT) : null;
    }

    /**
     * One-shot (per process) migration for the removed living stills and lock live wallpaper, so
     * nobody keeps a black lock screen or a stored {@code living:<hash>} choice. Call it from the
     * activity's onStart and onResume. The main thread only flips a flag; every preference read,
     * WallpaperManager call and file operation runs on the apply worker. Stored values are cleared
     * only after the apply they need succeeded, so a failed apply is tried again next start.
     */
    public static void dropRetiredLiveWallpapers(@NonNull Context ctx) {
        if (!sRetiredLiveChecked.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        GeneratedWallpaperApplier.onWorker(() -> {
            try {
                dropRetiredLiveWallpapersNow(app);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Migrating the retired live wallpaper failed", e);
            }
        });
    }

    @WorkerThread
    private static void dropRetiredLiveWallpapersNow(@NonNull Context app) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs == null) return;
        android.content.SharedPreferences sp = prefs.getSharedPreferences();
        String homeId = sp.getString(RETIRED_HOME_ANIMATED_KEY, null);
        String lockRaw = sp.getString(TERMUX_APP.KEY_WALLPAPER_LOCK_CHOICE, null);
        File livingDir = new File(photoRoot(app), "living");
        File analysisDir = new File(app.getCacheDir(), "living-analysis");
        boolean leftovers = livingDir.exists() || analysisDir.exists()
            || sp.contains(RETIRED_LOCK_MOTION_KEY) || sp.contains(RETIRED_HOME_MOTION_KEY)
            || sp.contains(RETIRED_ANIMATED_DISABLED_KEY);
        for (String key : RETIRED_HOME_ANIMATED_EXTRA_KEYS) leftovers |= sp.contains(key);
        boolean homeRetired = homeId != null && !homeId.trim().isEmpty();
        boolean serviceHeld = retiredLockServiceHeld(app);
        if (!homeRetired && !isRetiredAnimatedLock(lockRaw) && !serviceHeld && !leftovers) return;

        File photo = null;
        if (isRetiredAnimatedLock(lockRaw)) {
            photo = resolveRetiredLivingPhoto(app, lockRaw);
        } else if (serviceHeld && TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(lockRaw)) {
            File kept = lockPhotoFile(app);
            if (kept.isFile()) photo = kept;
        }
        File exact = WallpaperPictureReader.managedWallpaperExactFile(app);
        boolean systemMoved = homeRetired
            && prefs.getManagedWallpaperSystemId() != ManagedWallpaper.currentSystemWallpaperId(app);
        RetiredPlan plan = planRetiredLive(homeId, lockRaw, serviceHeld, photo != null, systemMoved, exact.isFile());

        boolean ok = true;
        if (plan.reapplyHome) {
            String error = applyPhotoNow(app, exact, WallpaperManager.FLAG_SYSTEM, true, null);
            Logger.logInfo(LOG_TAG, "Retired live wallpaper: Home picture set again"
                + (error == null ? "" : " failed: " + error));
            ok &= error == null;
        }
        switch (plan.lock) {
            case APPLY_PHOTO: {
                String error = photo == null ? "not_found" : applyPhotoNow(app, photo, WallpaperManager.FLAG_LOCK,
                    false, TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO);
                Logger.logInfo(LOG_TAG, "Retired live wallpaper: lock screen set to its photo"
                    + (error == null ? "" : " failed: " + error));
                ok &= error == null;
                break;
            }
            case COPY_HOME_AND_STORE_SAME_AS_HOME: {
                boolean copied = ManagedWallpaper.copyHomePictureToLock(app);
                Logger.logInfo(LOG_TAG, "Retired live wallpaper: no lock photo found, lock follows Home"
                    + (copied ? "" : " failed"));
                if (copied) prefs.setWallpaperLockChoice(TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME);
                ok &= copied;
                break;
            }
            case COPY_HOME_PICTURE: {
                boolean copied = ManagedWallpaper.copyHomePictureToLock(app);
                Logger.logInfo(LOG_TAG, "Retired live wallpaper: Home picture copied to the lock screen"
                    + (copied ? "" : " failed"));
                ok &= copied;
                break;
            }
            case NONE:
            default:
                break;
        }
        if (!ok) {
            Logger.logWarn(LOG_TAG, "Retired live wallpaper: left as it is, will try again next start");
            return;
        }
        android.content.SharedPreferences.Editor editor = sp.edit();
        if (plan.clearHome) {
            Logger.logInfo(LOG_TAG, "Retired live wallpaper: Home id " + homeId + " forgotten; its picture stays as a photo");
        }
        editor.remove(RETIRED_HOME_ANIMATED_KEY).remove(RETIRED_LOCK_MOTION_KEY)
            .remove(RETIRED_HOME_MOTION_KEY).remove(RETIRED_ANIMATED_DISABLED_KEY);
        for (String key : RETIRED_HOME_ANIMATED_EXTRA_KEYS) editor.remove(key);
        editor.apply();
        if (deleteTree(livingDir) | deleteTree(analysisDir)) {
            Logger.logInfo(LOG_TAG, "Retired live wallpaper: living still files deleted");
        }
    }

    /** Whether our retired lock service is the system's lock wallpaper, or the home one when the lock has none of its own. */
    @WorkerThread
    private static boolean retiredLockServiceHeld(@NonNull Context app) {
        if (Build.VERSION.SDK_INT < 34) return false; // getWallpaperInfo(int) is API 34
        try {
            WallpaperManager wm = WallpaperManager.getInstance(app);
            ComponentName ours = new ComponentName(app.getPackageName(), RETIRED_LOCK_SERVICE);
            WallpaperInfo lock = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            if (lock != null) return ours.equals(lock.getComponent());
            if (wm.getWallpaperId(WallpaperManager.FLAG_LOCK) > 0) return false;
            WallpaperInfo home = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
            return home != null && ours.equals(home.getComponent());
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Reading the lock wallpaper failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * The photo a retired {@code animated:living:<hash>} lock choice came from: the living still
     * folder's copy, else a recent photo whose hash starts with it. Null when neither is there.
     */
    @WorkerThread
    @Nullable
    private static File resolveRetiredLivingPhoto(@NonNull Context app, @Nullable String id) {
        String hash = retiredLivingHash(id);
        if (hash == null) return null;
        File image = new File(new File(new File(photoRoot(app), "living"), hash), "image.png");
        if (image.isFile() && image.length() > 0) return image;
        RecentWallpapers recents = recents(app);
        for (RecentWallpapers.Entry e : recents.entries()) {
            if (e.hash.startsWith(hash)) return new File(recents.directory(), e.name);
        }
        return null;
    }

    /** Deletes {@code dir} and everything in it; true when something was deleted. */
    private static boolean deleteTree(@NonNull File dir) {
        if (!dir.exists()) return false;
        File[] children = dir.isDirectory() ? dir.listFiles() : null;
        if (children != null) {
            for (File c : children) deleteTree(c);
        }
        return dir.delete();
    }

    @NonNull
    private static WallpaperSlotPlan.Inputs inputs(@NonNull Context ctx) {
        return new WallpaperSlotPlan.Inputs(read(ctx).lock);
    }

    private static void execute(@NonNull Context app, @NonNull WallpaperSlotPlan plan, @Nullable Callback cb) {
        GeneratedWallpaperApplier.Callback done = cb == null ? null : cb::onDone;
        switch (plan.kind) {
            case RECORD_ONLY:
                recordLock(app, plan.recordLock);
                GeneratedWallpaperApplier.post(done, true, null);
                return;
            case COPY_HOME_PHOTO_TO_LOCK:
                recordLock(app, plan.recordLock);
                GeneratedWallpaperApplier.onWorker(() -> {
                    boolean ok = ManagedWallpaper.copyHomePictureToLock(app);
                    GeneratedWallpaperApplier.post(done, ok, ok ? null : "wallpaper_failed");
                });
                return;
            case SET_PHOTO:
            default: {
                final File photo = plan.photo;
                if (photo == null) {
                    GeneratedWallpaperApplier.post(done, false, "not_found");
                    return;
                }
                int photoFlags = (plan.flags & WallpaperSlotPlan.FLAG_SYSTEM) != 0 ? WallpaperManager.FLAG_SYSTEM : 0;
                if ((plan.flags & WallpaperSlotPlan.FLAG_LOCK) != 0) photoFlags |= WallpaperManager.FLAG_LOCK;
                final int wmFlags = photoFlags;
                Logger.logInfo(LOG_TAG, "Photo " + photo.getName() + " to " + ManagedWallpaper.targetName(wmFlags));
                GeneratedWallpaperApplier.onWorker(() -> {
                    String error = applyPhotoNow(app, photo, wmFlags, plan.recordHome, plan.recordLock);
                    GeneratedWallpaperApplier.post(done, error == null, error);
                });
            }
        }
    }

    private static void recordLock(@NonNull Context app, @Nullable String value) {
        if (value == null) return;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs != null) prefs.setWallpaperLockChoice(value);
    }
}
