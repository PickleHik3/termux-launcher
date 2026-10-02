package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ManagedWallpaper;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

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
 */
public final class WallpaperSlots {

    private static final String LOG_TAG = "WallpaperSlots";

    private WallpaperSlots() {}

    /** Which slot a choice goes to. */
    public enum Slot { HOME, LOCK }

    /** What one slot holds: a preshipped background, a photo, or (Lock only) the Home slot's. */
    public static final class Choice {
        /** An {@link AnimatedWallpapers} id, or null. */
        @Nullable public final String animatedId;
        /** A photo the user picked (the managed copy), shown as a still. */
        public final boolean photo;
        /** Lock only: follows the Home slot. */
        public final boolean sameAsHome;

        private Choice(@Nullable String animatedId, boolean photo, boolean sameAsHome) {
            this.animatedId = animatedId;
            this.photo = photo;
            this.sameAsHome = sameAsHome;
        }

        @NonNull public static Choice animated(@NonNull String id) { return new Choice(id, false, false); }
        @NonNull public static Choice photo() { return new Choice(null, true, false); }
        @NonNull public static Choice sameAsHome() { return new Choice(null, false, true); }
    }

    /** Both slots as they are now. */
    public static final class State {
        @NonNull public final Choice home;
        @NonNull public final Choice lock;
        /** The Lock slot's Motion toggle (on by default). */
        public final boolean lockMotion;
        /** Our live wallpaper is the system's lock wallpaper right now. */
        public final boolean lockLiveActive;

        public State(@NonNull Choice home, @NonNull Choice lock, boolean lockMotion, boolean lockLiveActive) {
            this.home = home;
            this.lock = lock;
            this.lockMotion = lockMotion;
            this.lockLiveActive = lockLiveActive;
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
        return stateFrom(homeId, lock, motion, lockLiveActive(app));
    }

    /**
     * Applies {@code choice} to {@code slot}. A photo choice is recorded only: the host runs its photo
     * flow for that slot and calls {@link #notePhotoApplied}. An animated or Same-as-Home Lock choice
     * with Motion on may open Android's live-wallpaper preview the first time.
     */
    public static void apply(@NonNull Activity activity, @NonNull Slot slot, @NonNull Choice choice,
                             @Nullable Callback cb) {
        execute(activity, WallpaperSlotPlan.forApply(slot, choice, inputs(activity)), cb);
    }

    /** The host's photo flow set a photo on {@code slot}. */
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

    // --- package-private helpers ---

    /** {@link #read}'s mapping from stored values, without Android. */
    @NonNull
    static State stateFrom(@Nullable String homeAnimatedId, @Nullable String lockValue, boolean lockMotion,
                           boolean lockLiveActive) {
        return new State(WallpaperSlotPlan.homeChoice(homeAnimatedId),
            WallpaperSlotPlan.lockChoice(TermuxAppSharedPreferences.normaliseWallpaperLockChoice(lockValue)),
            lockMotion, lockLiveActive);
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
            case SET_STILL:
            default: {
                AnimatedWallpaper w = AnimatedWallpapers.byId(plan.stillId);
                if (Build.VERSION.SDK_INT < 34) {
                    GeneratedWallpaperApplier.post(done, false, "api");
                    return;
                }
                if (w == null) {
                    GeneratedWallpaperApplier.post(done, false, "not_found");
                    return;
                }
                int flags = (plan.flags & WallpaperSlotPlan.FLAG_SYSTEM) != 0 ? WallpaperManager.FLAG_SYSTEM : 0;
                if ((plan.flags & WallpaperSlotPlan.FLAG_LOCK) != 0) flags |= WallpaperManager.FLAG_LOCK;
                Logger.logInfo(LOG_TAG, "Still " + w.id() + " to " + ManagedWallpaper.targetName(flags));
                GeneratedWallpaperApplier.applyStill(activity, w, flags, plan.recordHome, plan.recordLock, done);
            }
        }
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
