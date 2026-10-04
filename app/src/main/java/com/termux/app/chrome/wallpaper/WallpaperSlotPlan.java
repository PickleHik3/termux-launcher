package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;

/**
 * What {@link WallpaperSlots} does for a choice or a Motion toggle, decided from plain inputs so
 * every case is a unit test (project-docs/active/animated-wallpaper/lock-live-wallpaper.md).
 * Nothing here touches Android; {@link WallpaperSlots} carries the plan out.
 *
 * <p>Photos work on every API level and never need the lock live wallpaper: below API 34 only
 * the animated choices (living stills) answer {@link Kind#UNSUPPORTED}.</p>
 *
 * <p>Ids are living-still ids only. A stored id of a retired pre-made background reads as a photo
 * (Home) or Same as Home (Lock); {@link WallpaperSlots#dropRetiredBackgrounds} then heals the
 * stored values.</p>
 */
final class WallpaperSlotPlan {

    /** WallpaperManager.FLAG_SYSTEM. */
    static final int FLAG_SYSTEM = 1;
    /** WallpaperManager.FLAG_LOCK. */
    static final int FLAG_LOCK = 2;

    enum Kind {
        /** Set {@link #stillId}'s photo (its rest pose) with {@link #flags}. */
        SET_STILL,
        /** Open Android's live-wallpaper preview for our lock service. */
        OPEN_PREVIEW,
        /** Copy the Home slot's photo (the launcher's exact copy) to the lock screen. */
        COPY_HOME_PHOTO_TO_LOCK,
        /** Set {@link #photo} with {@link #flags}, then record the slots. */
        SET_PHOTO,
        /** Only the stored slot changes; the screens already show it. */
        RECORD_ONLY,
        /** The choice needs API 34 (a living still) on a phone below it. */
        UNSUPPORTED,
    }

    /** The stored state and the system's, as {@link WallpaperSlots#read} has it. */
    static final class Inputs {
        final int sdkInt;
        /** The Home slot's living still id, or null for a photo. */
        @Nullable final String homeAnimatedId;
        @NonNull final WallpaperSlots.Choice lock;
        final boolean lockMotion;
        final boolean lockLiveActive;

        Inputs(int sdkInt, @Nullable String homeAnimatedId, @NonNull WallpaperSlots.Choice lock,
               boolean lockMotion, boolean lockLiveActive) {
            this.sdkInt = sdkInt;
            this.homeAnimatedId = homeAnimatedId;
            this.lock = lock;
            this.lockMotion = lockMotion;
            this.lockLiveActive = lockLiveActive;
        }

        /** Motion on and the phone can play the lock live wallpaper. */
        boolean motionPlays() {
            return lockMotion && WallpaperSlots.lockLiveSupported(sdkInt);
        }
    }

    @NonNull final Kind kind;
    /** {@link #FLAG_SYSTEM} and/or {@link #FLAG_LOCK} for {@link Kind#SET_STILL}, else 0. */
    final int flags;
    /** The living still {@link Kind#SET_STILL} sets as the system still. */
    @Nullable final String stillId;
    /** The lock choice to store (a {@code wallpaper_lock_choice} value), or null to keep it. */
    @Nullable final String recordLock;
    /** Whether the Home slot's id is recorded as {@link #stillId} (Home animated) or cleared (Home photo). */
    final boolean recordHome;
    /** The picture {@link Kind#SET_PHOTO} sets. */
    @Nullable final File photo;

    private WallpaperSlotPlan(@NonNull Kind kind, int flags, @Nullable String stillId,
                              @Nullable String recordLock, boolean recordHome) {
        this(kind, flags, stillId, recordLock, recordHome, null);
    }

    private WallpaperSlotPlan(@NonNull Kind kind, int flags, @Nullable String stillId,
                              @Nullable String recordLock, boolean recordHome, @Nullable File photo) {
        this.kind = kind;
        this.flags = flags;
        this.stillId = stillId;
        this.recordLock = recordLock;
        this.recordHome = recordHome;
        this.photo = photo;
    }

    // --- choices as stored values ---

    /** The {@code wallpaper_lock_choice} value of a Lock choice. */
    @NonNull
    static String lockValue(@NonNull WallpaperSlots.Choice c) {
        if (c.sameAsHome) return TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
        if (c.photo || c.animatedId == null) return TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
        return TERMUX_APP.VALUE_WALLPAPER_LOCK_ANIMATED_PREFIX + c.animatedId;
    }

    /** A stored {@code wallpaper_lock_choice} value as a Choice; unknown ids (retired backgrounds too) and junk read as Same as Home. */
    @NonNull
    static WallpaperSlots.Choice lockChoice(@Nullable String value) {
        if (TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(value)) return WallpaperSlots.Choice.photo();
        if (value != null && value.startsWith(TERMUX_APP.VALUE_WALLPAPER_LOCK_ANIMATED_PREFIX)) {
            String id = value.substring(TERMUX_APP.VALUE_WALLPAPER_LOCK_ANIMATED_PREFIX.length());
            if (AnimatedWallpapers.isKnownId(id)) return WallpaperSlots.Choice.animated(id);
        }
        return WallpaperSlots.Choice.sameAsHome();
    }

    /** The Home slot as a Choice: a living still id, else a photo (a retired background's id included). */
    @NonNull
    static WallpaperSlots.Choice homeChoice(@Nullable String homeAnimatedId) {
        return AnimatedWallpapers.isKnownId(homeAnimatedId)
            ? WallpaperSlots.Choice.animated(homeAnimatedId) : WallpaperSlots.Choice.photo();
    }

    /**
     * The living still the lock screen shows for {@code lock}, Same as Home resolved
     * against {@code homeAnimatedId}; null when it shows a photo.
     */
    @Nullable
    static String resolveLockId(@NonNull WallpaperSlots.Choice lock, @Nullable String homeAnimatedId) {
        if (lock.sameAsHome) {
            return AnimatedWallpapers.isKnownId(homeAnimatedId) ? homeAnimatedId : null;
        }
        if (lock.photo) return null;
        return AnimatedWallpapers.isKnownId(lock.animatedId) ? lock.animatedId : null;
    }

    // --- plans ---

    /** What applying {@code choice} to {@code slot} does. */
    @NonNull
    static WallpaperSlotPlan forApply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                      @NonNull Inputs in) {
        if (slot == WallpaperSlots.Slot.HOME) {
            if (choice.photo && choice.photoFile != null) {
                // Any API. Same as Home follows a photo on every phone: the live engine has
                // nothing to draw for one, and a still lock screen should match.
                int flags = FLAG_SYSTEM;
                if (in.lock.sameAsHome) flags |= FLAG_LOCK;
                return new WallpaperSlotPlan(Kind.SET_PHOTO, flags, null, null, true, choice.photoFile);
            }
            if (choice.photo || choice.sameAsHome || choice.animatedId == null) {
                // A photo with no picture: the slot is recorded by whoever set it.
                return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, null, false);
            }
            if (in.sdkInt < 34) return new WallpaperSlotPlan(Kind.UNSUPPORTED, 0, null, null, false);
            int flags = FLAG_SYSTEM;
            // Same as Home follows: the live engine re-reads the slot by itself; a still lock
            // screen (Motion off, or our service not holding it) gets the new still too.
            if (in.lock.sameAsHome && !(in.motionPlays() && in.lockLiveActive)) flags |= FLAG_LOCK;
            return new WallpaperSlotPlan(Kind.SET_STILL, flags, choice.animatedId, null, true);
        }

        // Lock.
        if (choice.photo && choice.photoFile != null) {
            return new WallpaperSlotPlan(Kind.SET_PHOTO, FLAG_LOCK, null,
                TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO, false, choice.photoFile);
        }
        if (choice.photo) {
            return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO, false);
        }
        String record = lockValue(choice);
        String id = resolveLockId(choice, in.homeAnimatedId);
        if (id == null && choice.sameAsHome && in.lock.sameAsHome && !in.lockLiveActive) {
            // Already Same as Home over a home photo, and the lock screen is not our live
            // wallpaper: the photo set put it there with FLAG_LOCK (Apply's lock-follows step).
            return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, record, false);
        }
        return lockShows(id, record, in, true);
    }

    /** What turning the Lock slot's Motion toggle {@code on} or off does; {@code in} has the old toggle. */
    @NonNull
    static WallpaperSlotPlan forMotion(boolean on, @NonNull Inputs in) {
        Inputs next = new Inputs(in.sdkInt, in.homeAnimatedId, in.lock, on, in.lockLiveActive);
        if (in.lock.photo) return new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, null, false);
        return lockShows(resolveLockId(in.lock, in.homeAnimatedId), null, next, false);
    }

    /**
     * The lock screen should show {@code id} (null: the Home photo, for Same as Home).
     * {@code newChoice}: the lock slot itself changed, so whatever it showed before is stale.
     */
    @NonNull
    private static WallpaperSlotPlan lockShows(@Nullable String id, @Nullable String record, @NonNull Inputs in,
                                               boolean newChoice) {
        if (id == null) {
            // Same as Home with a photo at home: put that photo on the lock screen. A Motion
            // toggle alone changes nothing for a photo, unless the live engine holds the lock
            // screen with nothing to draw.
            return newChoice || in.lockLiveActive
                ? new WallpaperSlotPlan(Kind.COPY_HOME_PHOTO_TO_LOCK, FLAG_LOCK, null, record, false)
                : new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, record, false);
        }
        if (in.motionPlays()) {
            return in.lockLiveActive
                ? new WallpaperSlotPlan(Kind.RECORD_ONLY, 0, null, record, false)
                : new WallpaperSlotPlan(Kind.OPEN_PREVIEW, 0, id, record, false);
        }
        if (in.sdkInt < 34) return new WallpaperSlotPlan(Kind.UNSUPPORTED, 0, null, null, false);
        return new WallpaperSlotPlan(Kind.SET_STILL, FLAG_LOCK, id, record, false);
    }
}
