package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.app.chrome.wallpaper.living.LivingStills;
import com.termux.app.chrome.wallpaper.living.Manifest;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The Home and Lock wallpaper slots (project-docs/active/animated-wallpaper/lock-live-wallpaper.md):
 * the one seam between the wallpaper picker and what each slot holds. The picker reads and applies
 * through here only; WallpaperManager, the lock live wallpaper and the slot preferences stay behind
 * it.
 *
 * <p>Storage: the Home slot is {@code managed_wallpaper_animated} (an id, or unset for a photo);
 * the Lock slot is {@code wallpaper_lock_choice} ({@code same_as_home}, {@code animated:<id>} or
 * {@code photo}) and {@code wallpaper_lock_motion}. What each call does is decided by
 * {@link WallpaperSlotPlan}.</p>
 *
 * <p>Photos (lock-live-wallpaper.md, "Photos"), all under the app's private files: a cropped
 * photo waits in {@code wallpaper/pending/<timestamp>.png} until the picker page closes; the Home
 * slot's picture is the managed exact copy ({@code managed-wallpaper/system-wallpaper-exact.png});
 * the Lock slot's own photo is kept as {@code wallpaper/slots/lock.png}; the last three applied
 * photos are {@link RecentWallpapers} in {@code wallpaper/recent/}.</p>
 */
public final class WallpaperSlots {

    private static final String LOG_TAG = "WallpaperSlots";

    private WallpaperSlots() {}

    /** Which slot a choice goes to. */
    public enum Slot { HOME, LOCK }

    /** What one slot holds: a living still, a photo, or (Lock only) the Home slot's. */
    public static final class Choice {
        /** A living still's {@link AnimatedWallpapers} id, or null. */
        @Nullable public final String animatedId;
        /** A photo the user picked, shown as a still. */
        public final boolean photo;
        /** The photo's picture when known; null for {@link #photo()} ("a photo, picture unknown"). */
        @Nullable public final File photoFile;
        /** Lock only: follows the Home slot. */
        public final boolean sameAsHome;

        private Choice(@Nullable String animatedId, boolean photo, @Nullable File photoFile, boolean sameAsHome) {
            this.animatedId = animatedId;
            this.photo = photo;
            this.photoFile = photoFile;
            this.sameAsHome = sameAsHome;
        }

        @NonNull public static Choice animated(@NonNull String id) { return new Choice(id, false, null, false); }
        /** The slot holds a photo whose picture is not known here; applying it records the slot only. */
        @NonNull public static Choice photo() { return new Choice(null, true, null, false); }
        /** The photo in {@code file}: applying it sets that picture, with no new crop. */
        @NonNull public static Choice photo(@NonNull File file) { return new Choice(null, true, file, false); }
        @NonNull public static Choice sameAsHome() { return new Choice(null, false, null, true); }

        @Override @NonNull public String toString() {
            if (sameAsHome) return "same_as_home";
            if (photo) return photoFile == null ? "photo" : "photo:" + photoFile.getName();
            return "animated:" + animatedId;
        }
    }

    /** Both slots as they are now. */
    public static final class State {
        @NonNull public final Choice home;
        @NonNull public final Choice lock;
        /** The Lock slot's Motion toggle (on by default). */
        public final boolean lockMotion;
        /** Our live wallpaper is the system's lock wallpaper right now. */
        public final boolean lockLiveActive;
        /** The Home slot's Motion toggle (on by default); read only for a living still. */
        public final boolean homeMotion;

        public State(@NonNull Choice home, @NonNull Choice lock, boolean lockMotion, boolean lockLiveActive) {
            this(home, lock, lockMotion, lockLiveActive, true);
        }

        public State(@NonNull Choice home, @NonNull Choice lock, boolean lockMotion, boolean lockLiveActive,
                     boolean homeMotion) {
            this.home = home;
            this.lock = lock;
            this.lockMotion = lockMotion;
            this.lockLiveActive = lockLiveActive;
            this.homeMotion = homeMotion;
        }
    }

    /** Called on the main thread. */
    public interface Callback {
        void onDone(boolean ok, @Nullable String error);
    }

    /** The lock live wallpaper and the Motion toggle exist from API 34, as the in-app animation. */
    public static boolean lockLiveSupported(int sdkInt) {
        return sdkInt >= 34;
    }

    /** Both slots as stored and as the system has them now. */
    @NonNull
    public static State read(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        String homeId = prefs == null ? null : prefs.getManagedWallpaperAnimatedId();
        String lock = prefs == null ? TERMUX_APP.DEFAULT_VALUE_WALLPAPER_LOCK_CHOICE : prefs.getWallpaperLockChoice();
        boolean motion = prefs == null ? TERMUX_APP.DEFAULT_VALUE_WALLPAPER_LOCK_MOTION
            : prefs.isWallpaperLockMotionEnabled();
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
        State state = stateFrom(homeId, lock, motion, lockLiveActive(app), homePhoto,
            lockCopy.isFile() ? lockCopy : null);
        boolean homeMotion = prefs == null || prefs.isWallpaperHomeMotionEnabled();
        return homeMotion ? state : new State(state.home, state.lock, state.lockMotion, state.lockLiveActive, false);
    }

    /**
     * Applies {@code choice} to {@code slot}. A photo with its picture ({@link Choice#photo(File)})
     * is set on a worker through {@link #applyPhotoNow}: Home with {@code FLAG_SYSTEM} (plus
     * {@code FLAG_LOCK} when Lock is Same as Home), Lock with {@code FLAG_LOCK}. {@link Choice#photo()}
     * with no picture is recorded only. An animated or Same-as-Home Lock choice with Motion on may
     * open Android's live-wallpaper preview the first time.
     */
    public static void apply(@NonNull Activity activity, @NonNull Slot slot, @NonNull Choice choice,
                             @Nullable Callback cb) {
        execute(activity, WallpaperSlotPlan.forApply(slot, choice, inputs(activity)), cb);
    }

    /**
     * A photo was set on {@code slot} some other way. Kept for callers outside the picker; the
     * picker and {@code POST /v1/wallpaper} go through {@link #applyPhotoNow}, which records the
     * slots itself.
     */
    public static void notePhotoApplied(@NonNull Context ctx, @NonNull Slot slot) {
        Context app = ctx.getApplicationContext();
        if (slot == Slot.LOCK) {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
            if (prefs != null) prefs.setWallpaperLockChoice(TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO);
            Logger.logInfo(LOG_TAG, "Lock slot: photo");
            return;
        }
        WallpaperSlotPlan.Inputs in = inputs(app);
        GeneratedWallpaperApplier.clear(app);
        Logger.logInfo(LOG_TAG, "Home slot: photo");
        if (in.lock.sameAsHome && in.lockLiveActive) {
            // Same as Home follows the photo; the live engine has nothing to draw for one.
            GeneratedWallpaperApplier.onWorker(() -> {
                if (!ManagedWallpaper.copyHomePictureToLock(app)) {
                    Logger.logError(LOG_TAG, "Copying the home photo to the lock screen failed");
                }
            });
        }
    }

    /** The Lock slot's Motion toggle. */
    public static void setLockMotion(@NonNull Activity activity, boolean on, @Nullable Callback cb) {
        WallpaperSlotPlan.Inputs in = inputs(activity);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity.getApplicationContext(), false);
        if (prefs != null) prefs.setWallpaperLockMotionEnabled(on);
        Logger.logInfo(LOG_TAG, "Lock motion: " + (on ? "on" : "off"));
        execute(activity, WallpaperSlotPlan.forMotion(on, in), cb);
    }

    /**
     * The Home slot's Motion toggle, for a living still: on plays it in the launcher, off leaves
     * its photo, which is already the system's still. Nothing is re-applied; the live host hears
     * the change and starts or stops the frames.
     */
    public static void setHomeMotion(@NonNull Activity activity, boolean on, @Nullable Callback cb) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity.getApplicationContext(), false);
        if (prefs != null) prefs.setWallpaperHomeMotionEnabled(on);
        Logger.logInfo(LOG_TAG, "Home motion: " + (on ? "on" : "off"));
        GeneratedWallpaperApplier.notifyChanged();
        GeneratedWallpaperApplier.Callback done = cb == null ? null : cb::onDone;
        GeneratedWallpaperApplier.post(done, true, null);
    }

    // --- package-private helpers ---

    /** {@link #read}'s mapping from stored values, without Android. */
    @NonNull
    static State stateFrom(@Nullable String homeAnimatedId, @Nullable String lockValue, boolean lockMotion,
                           boolean lockLiveActive) {
        return stateFrom(homeAnimatedId, lockValue, lockMotion, lockLiveActive, null, null);
    }

    /** As above, with each slot's kept picture for a slot that holds a photo. */
    @NonNull
    static State stateFrom(@Nullable String homeAnimatedId, @Nullable String lockValue, boolean lockMotion,
                           boolean lockLiveActive, @Nullable File homePhoto, @Nullable File lockPhoto) {
        Choice home = WallpaperSlotPlan.homeChoice(homeAnimatedId);
        if (home.photo && homePhoto != null) home = Choice.photo(homePhoto);
        Choice lock = WallpaperSlotPlan.lockChoice(TermuxAppSharedPreferences.normaliseWallpaperLockChoice(lockValue));
        if (lock.photo && lockPhoto != null) lock = Choice.photo(lockPhoto);
        return new State(home, lock, lockMotion, lockLiveActive);
    }

    /** A Lock choice as {@code GET /v1/wallpaper}'s {@code lock_slot}: same_as_home, a background id, or photo. */
    @NonNull
    public static String lockSlotName(@NonNull Choice lock) {
        if (lock.sameAsHome) return TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
        if (lock.photo || lock.animatedId == null) return TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
        return lock.animatedId;
    }

    /**
     * Whether our live wallpaper is the system's lock wallpaper: the lock screen's own wallpaper
     * is our service, or the lock screen has none of its own and the home one is ours.
     */
    static boolean lockLiveActive(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < 34) return false; // lockLiveSupported, spelled out for lint
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            ComponentName ours = LockLiveWallpaperService.component(ctx);
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

    // --- photos ---

    /**
     * Sets {@code picture} as a photo wallpaper with {@code flags} (WallpaperManager flags) and
     * records it: {@code recordHome} makes it the Home slot (any living still is
     * forgotten, and the live host told); {@code recordLock}, when not null, is the stored Lock
     * choice ({@code photo} also keeps a copy as the Lock slot's picture). The picture joins the
     * recent photos. A Home-only set while Lock is Same as Home on our live wallpaper also copies
     * it to the lock screen, which the live engine cannot draw.
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
        return applyPhotoNow(ctx, picture, flags, recordHome, recordLock, null);
    }

    /**
     * As above for a living still ({@code livingId} is {@code living:<hash>}, {@code picture} its
     * manifest's image): the photo itself is the system still, as its rest pose is, and the Home
     * slot records the living id instead of forgetting the living still. The live host
     * then plays the motion over it. With Lock on Same as Home and Motion on, our lock engine keeps
     * the lock screen and plays the same still, so the photo is not copied over it.
     */
    @WorkerThread
    @Nullable
    static String applyPhotoNow(@NonNull Context ctx, @NonNull File picture, int flags,
                                boolean recordHome, @Nullable String recordLock, @Nullable String livingId) {
        Context app = ctx.getApplicationContext();
        if (!picture.isFile()) return "not_found";
        boolean home = (flags & WallpaperManager.FLAG_SYSTEM) != 0;
        boolean lock = (flags & WallpaperManager.FLAG_LOCK) != 0;
        WallpaperSlotPlan.Inputs before = inputs(app);
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
        if (recordHome) {
            boolean lockEngineKeeps = livingId != null && before.lockMotion;
            if (livingId != null) {
                GeneratedWallpaperApplier.recordLiving(app, livingId);
                Logger.logInfo(LOG_TAG, "Home slot: " + livingId);
            } else {
                // A photo replaces any living still; the live host hears it and re-dresses.
                GeneratedWallpaperApplier.clear(app);
                Logger.logInfo(LOG_TAG, "Home slot: photo");
            }
            if (!lock && before.lock.sameAsHome && before.lockLiveActive && !lockEngineKeeps
                && !ManagedWallpaper.copyHomePictureToLock(app)) {
                Logger.logError(LOG_TAG, "Copying the home photo to the lock screen failed");
            }
        }
        if (recordLock != null) Logger.logInfo(LOG_TAG, "Lock slot: " + recordLock);
        try {
            recents(app).add(picture, System.currentTimeMillis(), livingId != null);
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Keeping the recent photo failed", e);
        }
        // Adding a recent is the only moment one can fall out: sweep the folders nothing names now.
        pruneLivingFolders(app);
        return null;
    }

    /**
     * Deletes living-still folders nothing refers to: not a recent photo, the Home or Lock still,
     * a pending crop, the Lock photo or the Home exact copy. Conservative: if anything that could
     * name a folder cannot be read or hashed, nothing is deleted. Hashes photos, so worker threads
     * only.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean sPrunedThisProcess =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    @WorkerThread
    static void pruneLivingFolders(@NonNull Context app) {
        try {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
            if (prefs == null) return;
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (RecentWallpapers.Entry e : recents(app).entries()) {
                if (e.hash.length() < 16) return;
                keep.add(e.hash.substring(0, 16));
            }
            addLivingHash(keep, prefs.getManagedWallpaperAnimatedId());
            String lock = prefs.getWallpaperLockChoice();
            String prefix = TERMUX_APP.VALUE_WALLPAPER_LOCK_ANIMATED_PREFIX;
            if (lock != null && lock.startsWith(prefix)) addLivingHash(keep, lock.substring(prefix.length()));
            File[] pending = pendingDir(app).listFiles();
            if (pending != null) {
                for (File f : pending) {
                    if (f.isFile()) keep.add(LivingStills.hash16(f));
                }
            }
            File lockPhoto = new File(new File(photoRoot(app), "slots"), "lock.png");
            if (lockPhoto.isFile()) keep.add(LivingStills.hash16(lockPhoto));
            File exact = new File(new File(app.getFilesDir(), "managed-wallpaper"), "system-wallpaper-exact.png");
            if (exact.isFile()) keep.add(LivingStills.hash16(exact));
            int n = com.termux.app.chrome.wallpaper.living.LivingStillPruner.prune(LivingStills.root(app), keep);
            if (n > 0) Logger.logInfo(LOG_TAG, "Pruned " + n + " orphaned living still folder(s)");
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Pruning living stills failed; nothing deleted", e);
        }
    }

    /** Adds the hash of a {@code living:<hash>} id (anything else is ignored). */
    private static void addLivingHash(@NonNull java.util.Set<String> keep, @Nullable String id) {
        if (id != null && AnimatedWallpapers.isLivingId(id)) keep.add(id.substring("living:".length()));
    }

    /** Whether the recent photo was last applied as a living still (reads a small index). */
    public static boolean recentIsLiving(@NonNull Context ctx, @NonNull File photo) {
        try {
            return recents(ctx.getApplicationContext()).isLiving(photo);
        } catch (RuntimeException e) {
            return false;
        }
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

    private static volatile boolean sHealingHome;

    /**
     * Android's live-wallpaper preview can offer "Home and lock". Our live wallpaper on the home
     * screen would leave the glass with nothing to blur, so when the launcher comes back and finds it
     * there, the Home slot's still goes back on the home screen alone (the lock screen keeps the
     * live wallpaper). Cheap when nothing is wrong: one WallpaperInfo read.
     */
    public static void keepHomeStill(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < 34 || sHealingHome) return;
        Context app = ctx.getApplicationContext();
        try {
            WallpaperInfo home = WallpaperManager.getInstance(app).getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
            if (home == null || !LockLiveWallpaperService.component(app).equals(home.getComponent())) return;
        } catch (RuntimeException e) {
            return;
        }
        State state = read(app);
        AnimatedWallpaper w = state.home.animatedId == null ? null
            : AnimatedWallpapers.byId(app, state.home.animatedId);
        if (!(w instanceof LivingStill)) {
            Logger.logWarn(LOG_TAG, "Our live wallpaper is on the home screen with no Home living still to put back");
            return;
        }
        Logger.logInfo(LOG_TAG, "Our live wallpaper took the home screen too; putting the Home still back");
        sHealingHome = true;
        // The photo is the still: set it again as it is, and keep the living id recorded.
        final File image = ((LivingStill) w).manifest().image();
        final String id = w.id();
        GeneratedWallpaperApplier.onWorker(() -> {
            String error = applyPhotoNow(app, image, WallpaperManager.FLAG_SYSTEM, true, null, id);
            if (error != null) Logger.logError(LOG_TAG, "Putting the Home photo back failed: " + error);
            sHealingHome = false;
        });
    }

    /** Whether the stored Home id names a background this build no longer has (not null, not a living still). */
    static boolean isRetiredHomeId(@Nullable String homeAnimatedId) {
        return homeAnimatedId != null && !AnimatedWallpapers.isLivingId(homeAnimatedId);
    }

    /**
     * The {@code wallpaper_lock_choice} value to store for a retired lock choice
     * ({@code animated:<id>} with a non-living id), or null when the stored value needs no healing:
     * {@code same_as_home} while our live wallpaper holds the lock screen (the Home picture is then
     * copied over it), else {@code photo}.
     */
    @Nullable
    static String healedLockValue(@Nullable String lockValue, boolean lockLiveActive) {
        String prefix = TERMUX_APP.VALUE_WALLPAPER_LOCK_ANIMATED_PREFIX;
        if (lockValue == null || !lockValue.startsWith(prefix)) return null;
        if (AnimatedWallpapers.isLivingId(lockValue.substring(prefix.length()))) return null;
        return lockLiveActive ? TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME : TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
    }

    /**
     * One-shot migration for the pre-made backgrounds this build dropped. A retired Home id is
     * forgotten (the system still is already that background's picture, which stays as a photo). A
     * retired lock choice becomes {@code photo}, or {@code same_as_home} with the Home picture
     * copied to the lock screen when our live wallpaper holds it, so it never draws black. Cheap
     * when nothing is retired: two preference reads. Call it before anything reads the slots.
     */
    public static void dropRetiredBackgrounds(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs == null) return;
        String homeId = prefs.getManagedWallpaperAnimatedId();
        String lockValue = prefs.getWallpaperLockChoice();
        boolean homeRetired = isRetiredHomeId(homeId);
        boolean lockRetired = healedLockValue(lockValue, false) != null;
        // Startup heal: also sweep orphaned living folders, off the caller's thread. Once per
        // process: this runs on every resume, and the sweep hashes every kept photo.
        if (sPrunedThisProcess.compareAndSet(false, true))
            GeneratedWallpaperApplier.onWorker(() -> pruneLivingFolders(app));
        if (!homeRetired && !lockRetired) return;
        boolean live = lockLiveActive(app);
        boolean copy = false;
        if (homeRetired) {
            Logger.logInfo(LOG_TAG, "Retired Home background " + homeId + " dropped; its picture stays as a photo");
            GeneratedWallpaperApplier.clear(app);
            // A Same as Home lock screen held by our live wallpaper has nothing to draw now.
            copy = live && TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME.equals(lockValue);
        }
        if (lockRetired) {
            String healed = healedLockValue(lockValue, live);
            Logger.logInfo(LOG_TAG, "Retired lock background " + lockValue + " healed to " + healed);
            prefs.setWallpaperLockChoice(healed);
            copy |= TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME.equals(healed);
        }
        if (copy) {
            GeneratedWallpaperApplier.onWorker(() -> {
                if (!ManagedWallpaper.copyHomePictureToLock(app)) {
                    Logger.logError(LOG_TAG, "Copying the home picture to the lock screen failed");
                }
            });
        }
    }

    @NonNull
    private static WallpaperSlotPlan.Inputs inputs(@NonNull Context ctx) {
        State state = read(ctx);
        return new WallpaperSlotPlan.Inputs(Build.VERSION.SDK_INT,
            state.home.animatedId, state.lock, state.lockMotion, state.lockLiveActive);
    }

    private static void execute(@NonNull Activity activity, @NonNull WallpaperSlotPlan plan, @Nullable Callback cb) {
        Context app = activity.getApplicationContext();
        GeneratedWallpaperApplier.Callback done = cb == null ? null : cb::onDone;
        switch (plan.kind) {
            case UNSUPPORTED:
                GeneratedWallpaperApplier.post(done, false, "api");
                return;
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
            case OPEN_PREVIEW:
                // Stored first, so the engine Android binds for the preview already draws it.
                recordLock(app, plan.recordLock);
                if (openPreview(activity)) {
                    Logger.logInfo(LOG_TAG, "preview_shown: lock live wallpaper for " + plan.stillId);
                    GeneratedWallpaperApplier.post(done, true, null);
                } else {
                    GeneratedWallpaperApplier.post(done, false, "preview_unavailable");
                }
                return;
            case SET_PHOTO: {
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
                return;
            }
            case SET_STILL:
            default: {
                if (Build.VERSION.SDK_INT < 34) {
                    GeneratedWallpaperApplier.post(done, false, "api");
                    return;
                }
                if (AnimatedWallpapers.isLivingId(plan.stillId)) {
                    applyLiving(app, plan, done);
                    return;
                }
                // Only living stills exist; anything else (a retired background) is gone.
                GeneratedWallpaperApplier.post(done, false, "not_found");
            }
        }
    }

    /**
     * A living still's apply: its manifest's photo is set as the system still with the plan's
     * flags (no rest-pose render: the photo is the rest pose) and the slots record the living id.
     */
    private static void applyLiving(@NonNull Context app, @NonNull WallpaperSlotPlan plan,
                                    @Nullable GeneratedWallpaperApplier.Callback done) {
        String id = plan.stillId;
        Manifest manifest = id == null ? null
            : LivingStills.findByHash(app, id.substring(LivingStill.ID_PREFIX.length()));
        if (manifest == null) {
            GeneratedWallpaperApplier.post(done, false, "not_found");
            return;
        }
        int flags = (plan.flags & WallpaperSlotPlan.FLAG_SYSTEM) != 0 ? WallpaperManager.FLAG_SYSTEM : 0;
        if ((plan.flags & WallpaperSlotPlan.FLAG_LOCK) != 0) flags |= WallpaperManager.FLAG_LOCK;
        final int wmFlags = flags;
        Logger.logInfo(LOG_TAG, "Living still " + id + " to " + ManagedWallpaper.targetName(wmFlags));
        GeneratedWallpaperApplier.onWorker(() -> {
            // The lock record of a Home apply (Same as Home) is untouched; a Lock apply stores
            // animated:<id>. Home records the id itself (applyPhotoNow's livingId).
            String error = applyPhotoNow(app, manifest.image(), wmFlags, plan.recordHome, plan.recordLock, id);
            GeneratedWallpaperApplier.post(done, error == null, error);
        });
    }

    private static void recordLock(@NonNull Context app, @Nullable String value) {
        if (value == null) return;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs != null) prefs.setWallpaperLockChoice(value);
    }

    /**
     * Android's preview of our live wallpaper, where the user picks "Lock screen". Falls back to
     * the live-wallpaper chooser when a ROM has no direct preview.
     */
    private static boolean openPreview(@NonNull Activity activity) {
        Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, LockLiveWallpaperService.component(activity));
        try {
            activity.startActivity(preview);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Logger.logWarn(LOG_TAG, "No live wallpaper preview: " + e.getMessage());
        }
        try {
            activity.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Logger.logError(LOG_TAG, "No live wallpaper chooser: " + e.getMessage());
            return false;
        }
    }
}
