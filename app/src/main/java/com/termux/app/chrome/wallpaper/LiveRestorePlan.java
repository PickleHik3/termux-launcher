package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * What Live remembers of the phone's wallpapers and what it does with that, decided from plain
 * inputs so every case is a unit test. Nothing here touches Android; {@link LiveWallpaperApplier}
 * carries it out.
 *
 * <p>The memory exists exactly while the phone's Home and Lock show one of Live's stills: it is
 * taken before the first still replaces them and dropped when Back to photo puts them back. It
 * records the system's Home and Lock wallpaper ids as they stood right after the still was set, so
 * a wallpaper set by anyone since (the Photo page, another app) is seen: such a memory is stale, a
 * new one is taken before the next still, and Back to photo leaves the phone's wallpaper alone.
 * The pictures live in {@code wallpaper/live-restore/} ({@link #HOME_FILE}, {@link #LOCK_FILE})
 * and the rest in {@link #MARKER_FILE}; nothing there is a {@code living:} or {@code animated:}
 * value, so {@code WallpaperSlots.dropRetiredLiveWallpapers} neither reads nor clears it.</p>
 */
final class LiveRestorePlan {

    /** The folder under {@code files/wallpaper/}. */
    static final String DIR = "live-restore";
    static final String HOME_FILE = "home.png";
    static final String LOCK_FILE = "lock.png";
    /** Written last: a reader that finds it finds the pictures already there. */
    static final String MARKER_FILE = "memory.properties";
    /** The still that was set, kept so a set can be retried; under {@code files/wallpaper/live/}. */
    static final String STILL_DIR = "live";
    static final String STILL_FILE = "still.png";

    /** WallpaperManager.FLAG_SYSTEM and FLAG_LOCK. */
    static final int FLAG_HOME = 1;
    static final int FLAG_LOCK = 2;

    private LiveRestorePlan() {}

    /** What was on the phone before Live's first still. */
    static final class Memory {
        /** {@link #HOME_FILE} holds the Home picture. */
        final boolean hasHome;
        /** That picture was the launcher's own only (terminal-app mode), not the phone's. */
        final boolean homeAppOnly;
        /** The stored lock choice to come back to: {@code same_as_home} or {@code photo}; null when unknown. */
        @Nullable final String lockRecord;
        /** {@link #LOCK_FILE} holds the Lock picture. */
        final boolean hasLockFile;
        /** The system's Home and Lock wallpaper ids right after the still was set; 0 or less when unknown. */
        final int stillHomeId;
        final int stillLockId;

        Memory(boolean hasHome, boolean homeAppOnly, @Nullable String lockRecord, boolean hasLockFile,
               int stillHomeId, int stillLockId) {
            this.hasHome = hasHome;
            this.homeAppOnly = homeAppOnly;
            this.lockRecord = lockRecord;
            this.hasLockFile = hasLockFile;
            this.stillHomeId = stillHomeId;
            this.stillLockId = stillLockId;
        }

        @NonNull
        Memory withStillIds(int homeId, int lockId) {
            return new Memory(hasHome, homeAppOnly, lockRecord, hasLockFile, homeId, lockId);
        }

        /** Whether there is anything to put back. */
        boolean hasAnything() {
            return !steps(this).isEmpty();
        }

        @NonNull
        String serialize() {
            return "home=" + hasHome + "\nhomeAppOnly=" + homeAppOnly + "\nlock=" + (lockRecord == null ? "" : lockRecord)
                + "\nlockFile=" + hasLockFile + "\nstillHomeId=" + stillHomeId + "\nstillLockId=" + stillLockId + "\n";
        }

        /** The memory {@code text} holds; null when it is not one. */
        @Nullable
        static Memory parse(@Nullable String text) {
            if (text == null || text.isEmpty()) return null;
            Properties p = new Properties();
            try {
                p.load(new StringReader(text));
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
            if (p.getProperty("home") == null) return null;
            String lock = p.getProperty("lock", "");
            return new Memory(Boolean.parseBoolean(p.getProperty("home")),
                Boolean.parseBoolean(p.getProperty("homeAppOnly")),
                TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME.equals(lock) || TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(lock)
                    ? lock : null,
                Boolean.parseBoolean(p.getProperty("lockFile")),
                intOr(p.getProperty("stillHomeId"), 0), intOr(p.getProperty("stillLockId"), 0));
        }

        private static int intOr(@Nullable String v, int fallback) {
            if (v == null) return fallback;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    /**
     * The memory of {@code state} (the slots as they are before the still): Home's picture
     * and what the lock screen holds. Ids are filled in once the still is set.
     *
     * @param homeFile  Home's picture, or null when it is unknown
     * @param lockFile  Lock's own picture (also the phone's own one under an app-only Home), or null
     */
    @NonNull
    static Memory memoryOf(@NonNull WallpaperSlots.State state, boolean homeFile, boolean lockFile) {
        String lockRecord;
        if (state.lockUnderAppOnly) lockRecord = TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
        else if (state.lock.sameAsHome) lockRecord = TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
        else lockRecord = TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;
        boolean ownsLockFile = lockFile && (state.lockUnderAppOnly || !state.lock.sameAsHome);
        return new Memory(homeFile, homeFile && state.appOnly, lockRecord, ownsLockFile, 0, 0);
    }

    /** What to do about the memory before a still is set. */
    enum Remember {
        /** The phone's wallpaper is not touched: nothing to remember. */
        NOTHING,
        /** Take a new memory: none exists, or the phone's wallpaper changed since the last still. */
        TAKE,
        /** The phone already shows one of the stills with its memory: keep the memory. */
        KEEP,
    }

    /**
     * @param touchesSystem {@link LiveSceneLogic#setsSystemWallpaper}
     * @param memory        the stored memory, or null
     * @param homeId        the system's Home wallpaper id now
     * @param lockId        the system's Lock wallpaper id now
     */
    @NonNull
    static Remember remember(boolean touchesSystem, @Nullable Memory memory, int homeId, int lockId) {
        if (!touchesSystem) return Remember.NOTHING;
        return stillIsOnPhone(memory, homeId, lockId) ? Remember.KEEP : Remember.TAKE;
    }

    /**
     * Whether the phone still shows the still the memory was taken for: the Home id is known and
     * unchanged, and Lock is as it was. {@code WallpaperManager.getWallpaperId(FLAG_LOCK)} is -1
     * while Lock shares Home's picture, which is how a still set on both stands; a shared Lock then
     * must still be shared, and a Lock of its own must be the same one.
     */
    static boolean stillIsOnPhone(@Nullable Memory memory, int homeId, int lockId) {
        if (memory == null || memory.stillHomeId <= 0 || memory.stillHomeId != homeId) return false;
        return memory.stillLockId <= 0 ? lockId <= 0 : memory.stillLockId == lockId;
    }

    /**
     * Whether Back to photo puts the memory back: it exists, has something to put back, and the
     * phone still shows the still. A stale memory is only dropped.
     */
    static boolean shouldRestore(@Nullable Memory memory, int homeId, int lockId) {
        return memory != null && memory.hasAnything() && stillIsOnPhone(memory, homeId, lockId);
    }

    /**
     * Whether the memory goes when the user applies a photo themselves: it is stale, because the
     * phone's wallpaper is no longer the still. A launcher-only photo leaves the phone showing the
     * still, so the memory (the photos under it) stays for Back to photo.
     */
    static boolean dropOnPhotoChosen(@Nullable Memory memory, int homeId, int lockId) {
        return memory != null && !stillIsOnPhone(memory, homeId, lockId);
    }

    /** One set Back to photo makes. */
    static final class Step {
        enum Kind {
            /** Home's picture to the phone, through the slots' photo set. */
            HOME_PHONE,
            /** Home's picture to the launcher alone. */
            HOME_APP_ONLY,
            /** Lock's picture to the lock screen. */
            LOCK_PHONE,
        }

        @NonNull final Kind kind;
        /** {@link #FLAG_HOME} and/or {@link #FLAG_LOCK}. */
        final int flags;
        /** The lock choice stored by the set, or null to leave it. */
        @Nullable final String recordLock;

        Step(@NonNull Kind kind, int flags, @Nullable String recordLock) {
            this.kind = kind;
            this.flags = flags;
            this.recordLock = recordLock;
        }
    }

    /**
     * The sets that put {@code memory} back, Home first. A Lock that was Same as Home rides along
     * with a phone Home set; a Lock photo is set after it. A Home that was the launcher's own goes
     * back to the launcher alone, and the lock picture beside it to the lock screen. Empty when
     * nothing is known.
     */
    @NonNull
    static List<Step> steps(@NonNull Memory memory) {
        List<Step> out = new ArrayList<>(2);
        boolean sameAsHome = TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME.equals(memory.lockRecord);
        if (memory.hasHome && memory.homeAppOnly) {
            out.add(new Step(Step.Kind.HOME_APP_ONLY, 0, null));
        } else if (memory.hasHome) {
            out.add(new Step(Step.Kind.HOME_PHONE, sameAsHome ? FLAG_HOME | FLAG_LOCK : FLAG_HOME,
                sameAsHome ? TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME : null));
        }
        boolean lockApplied = memory.hasHome && !memory.homeAppOnly && sameAsHome;
        if (memory.hasLockFile && !lockApplied) {
            out.add(new Step(Step.Kind.LOCK_PHONE, FLAG_LOCK,
                memory.lockRecord != null ? memory.lockRecord : TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO));
        }
        return out;
    }
}
