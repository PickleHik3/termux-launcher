package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;

/**
 * What {@link WallpaperSlots} does for a slot choice, decided from plain inputs so every case is
 * a unit test. Nothing here touches Android; {@link WallpaperSlots} carries the plan out. Photos
 * work the same on every API level.
 */
final class WallpaperSlotPlan {

    /** WallpaperManager.FLAG_SYSTEM. */
    static final int FLAG_SYSTEM = 1;
    /** WallpaperManager.FLAG_LOCK. */
    static final int FLAG_LOCK = 2;

    enum Kind {
        /** Copy the Home slot's photo (the launcher's exact copy) to the lock screen. */
        COPY_HOME_PHOTO_TO_LOCK,
        /** Set {@link #photo} with {@link #flags}, then record the slots. */
        SET_PHOTO,
        /** Only the stored slot changes; the screens already show it. */
        RECORD_ONLY,
    }

    /** The stored state, as {@link WallpaperSlots#read} has it. */
    static final class Inputs {
        @NonNull final WallpaperSlots.Choice lock;

        Inputs(@NonNull WallpaperSlots.Choice lock) {
            this.lock = lock;
        }
    }

    @NonNull final Kind kind;
    /** {@link #FLAG_SYSTEM} and/or {@link #FLAG_LOCK} for {@link Kind#SET_PHOTO}, else 0. */
    final int flags;
    /** The lock choice to store (a {@code wallpaper_lock_choice} value), or null to keep it. */
    @Nullable final String recordLock;
    /** Whether the Home slot is recorded as a photo. */
    final boolean recordHome;
    /** The picture {@link Kind#SET_PHOTO} sets. */
    @Nullable final File photo;

    private WallpaperSlotPlan(@NonNull Kind kind, int flags, @Nullable String recordLock, boolean recordHome) {
        this(kind, flags, recordLock, recordHome, null);
    }

    private WallpaperSlotPlan(@NonNull Kind kind, int flags, @Nullable String recordLock, boolean recordHome,
                              @Nullable File photo) {
        this.kind = kind;
        this.flags = flags;
        this.recordLock = recordLock;
        this.recordHome = recordHome;
        this.photo = photo;
    }

    // --- choices as stored values ---

    /** The {@code wallpaper_lock_choice} value of a Lock choice. */
    @NonNull
    static String lockValue(@NonNull WallpaperSlots.Choice c) {
        if (c.sameAsHome) return TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
        return TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
    }

    /** A stored {@code wallpaper_lock_choice} value as a Choice; unknown values and junk read as Same as Home. */
    @NonNull
    static WallpaperSlots.Choice lockChoice(@Nullable String value) {
        if (TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(value)) return WallpaperSlots.Choice.photo();
        return WallpaperSlots.Choice.sameAsHome();
    }

    // --- plans ---

    /** What applying {@code choice} to {@code slot} does. */
    @NonNull
    static WallpaperSlotPlan forApply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                      @NonNull Inputs in) {
        if (slot == WallpaperSlots.Slot.HOME) {
            if (choice.photo && choice.photoFile != null) {
                // Same as Home follows a photo on every phone: a still lock screen should match.
                int flags = FLAG_SYSTEM;
                if (in.lock.sameAsHome) flags |= FLAG_LOCK;
                return new WallpaperSlotPlan(Kind.SET_PHOTO, flags, null, true, choice.photoFile);
            }
            // A photo with no picture: the slot is recorded by whoever set it.
            return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, false);
        }

        // Lock.
        if (choice.photo && choice.photoFile != null) {
            return new WallpaperSlotPlan(Kind.SET_PHOTO, FLAG_LOCK,
                TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO, false, choice.photoFile);
        }
        if (choice.photo) {
            return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO, false);
        }
        String record = lockValue(choice);
        if (in.lock.sameAsHome) {
            // Already Same as Home: the photo set put it there with FLAG_LOCK (Apply's lock-follows step).
            return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, record, false);
        }
        return new WallpaperSlotPlan(Kind.COPY_HOME_PHOTO_TO_LOCK, FLAG_LOCK, record, false);
    }
}
