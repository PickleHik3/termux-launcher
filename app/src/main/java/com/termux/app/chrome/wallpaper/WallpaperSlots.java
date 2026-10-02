package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Home and Lock wallpaper slots (project-docs/active/animated-wallpaper/lock-live-wallpaper.md):
 * the one seam between the wallpaper picker and what each slot holds. The picker reads and applies
 * through here only; WallpaperManager, the lock live wallpaper and the slot preferences stay behind
 * it.
 */
public final class WallpaperSlots {

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
        throw new UnsupportedOperationException("WallpaperSlots.read: implemented by the lock live wallpaper work");
    }

    /**
     * Applies {@code choice} to {@code slot}. A photo choice is recorded only: the host runs its photo
     * flow for that slot and calls {@link #notePhotoApplied}. An animated or Same-as-Home Lock choice
     * with Motion on may open Android's live-wallpaper preview the first time.
     */
    public static void apply(@NonNull Activity activity, @NonNull Slot slot, @NonNull Choice choice,
                             @Nullable Callback cb) {
        throw new UnsupportedOperationException("WallpaperSlots.apply: implemented by the lock live wallpaper work");
    }

    /** The host's photo flow set a photo on {@code slot}. */
    public static void notePhotoApplied(@NonNull Context ctx, @NonNull Slot slot) {
        throw new UnsupportedOperationException("WallpaperSlots.notePhotoApplied: implemented by the lock live wallpaper work");
    }

    /** The Lock slot's Motion toggle. */
    public static void setLockMotion(@NonNull Activity activity, boolean on, @Nullable Callback cb) {
        throw new UnsupportedOperationException("WallpaperSlots.setLockMotion: implemented by the lock live wallpaper work");
    }
}
