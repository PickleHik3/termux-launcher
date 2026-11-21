package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the platform and the disk said about the wallpaper the last time anyone asked: the system
 * wallpaper's id, whether the launcher's managed copy is on disk, with its identity and its
 * pixel size, and whether that copy is a picture of the launcher's own that Android never got.
 *
 * <p>Every glass surface asks these on every pass — the blur cache once per surface per obtain —
 * and each answer is a binder call or a file stat. The activity reads them once into one of
 * these and takes a fresh one where the wallpaper can have changed (an arrival, the colours
 * listener, the picker's apply, the managed picture landing), so a pass costs field reads and every
 * surface in it sees the same answer.</p>
 */
public final class WallpaperSourceFacts {

    /** Nothing known yet: no system id, no managed picture. */
    @NonNull public static final WallpaperSourceFacts NONE =
        new WallpaperSourceFacts(0, -1, false, 0L, 0L, 0, 0, false);

    /**
     * The id the in-app picker had stored when these were read. The picker writes it after the
     * picture is on disk, so a stored id that has moved since is the cue to read again — that is
     * how a set from anywhere (the picker, a command) is picked up without each writer telling us.
     */
    public final int storedWallpaperId;
    /** The system wallpaper's id, or -1 when it could not be read. */
    public final int systemWallpaperId;
    /** True when the managed exact copy is a file on disk. */
    public final boolean managedFilePresent;
    /** The managed file's {@code lastModified()}, as the file reported it (0 when absent). */
    public final long managedLastModified;
    /** The managed file's {@code length()}, as the file reported it (0 when absent). */
    public final long managedLength;
    /** The managed picture's pixel size from its header; 0s when unknown. */
    public final int managedWidth, managedHeight;
    /**
     * The picker stored the managed copy for the launcher alone ({@code wallpaper_app_only}): the
     * launcher wears it whatever Android shows, so no wallpaper id has to agree. Read with the
     * stored id, and a cue to read again in the same way.
     */
    public final boolean appOnly;

    private WallpaperSourceFacts(int storedWallpaperId, int systemWallpaperId,
                                 boolean managedFilePresent, long managedLastModified,
                                 long managedLength, int managedWidth, int managedHeight,
                                 boolean appOnly) {
        this.storedWallpaperId = storedWallpaperId;
        this.systemWallpaperId = systemWallpaperId;
        this.managedFilePresent = managedFilePresent;
        this.managedLastModified = managedLastModified;
        this.managedLength = managedLength;
        boolean sized = managedFilePresent && managedWidth > 0 && managedHeight > 0;
        this.managedWidth = sized ? managedWidth : 0;
        this.managedHeight = sized ? managedHeight : 0;
        this.appOnly = appOnly;
    }

    /** Facts for a managed copy that is (or was) also the phone's wallpaper. */
    @NonNull
    public static WallpaperSourceFacts of(int storedWallpaperId, int systemWallpaperId,
                                          boolean managedFilePresent, long managedLastModified,
                                          long managedLength, int managedWidth, int managedHeight) {
        return of(storedWallpaperId, systemWallpaperId, managedFilePresent, managedLastModified,
            managedLength, managedWidth, managedHeight, false);
    }

    @NonNull
    public static WallpaperSourceFacts of(int storedWallpaperId, int systemWallpaperId,
                                          boolean managedFilePresent, long managedLastModified,
                                          long managedLength, int managedWidth, int managedHeight,
                                          boolean appOnly) {
        return new WallpaperSourceFacts(storedWallpaperId, systemWallpaperId, managedFilePresent,
            managedLastModified, managedLength, managedWidth, managedHeight, appOnly);
    }

    /** True when the picker has stored another id since these were read: read them again. */
    public boolean isStaleFor(int currentStoredWallpaperId) {
        return currentStoredWallpaperId != storedWallpaperId;
    }

    /** {@link #isStaleFor(int)}, and also when the app-only mark has moved since. */
    public boolean isStaleFor(int currentStoredWallpaperId, boolean currentAppOnly) {
        return isStaleFor(currentStoredWallpaperId) || currentAppOnly != appOnly;
    }

    /**
     * Whether the wallpaper on screen is the launcher's own, with its exact copy still on disk:
     * either the id the picker stored is the system's current one, or the copy is the launcher's
     * app-only picture, which the launcher draws itself whatever Android shows.
     */
    public boolean managedOnScreen() {
        if (!managedFilePresent) return false;
        return appOnly || (storedWallpaperId > 0 && storedWallpaperId == systemWallpaperId);
    }

    /**
     * Whether the launcher wears its app-only picture: marked so and on disk. A mark whose copy
     * is gone reads as no wallpaper of the launcher's own, and the phone's shows again.
     */
    public boolean appOnlyOnScreen() {
        return appOnly && managedFilePresent;
    }

    /** True when the managed picture's header gave a usable pixel size. */
    public boolean hasManagedSize() {
        return managedWidth > 0 && managedHeight > 0;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof WallpaperSourceFacts)) return false;
        WallpaperSourceFacts that = (WallpaperSourceFacts) other;
        return storedWallpaperId == that.storedWallpaperId
            && systemWallpaperId == that.systemWallpaperId
            && managedFilePresent == that.managedFilePresent
            && managedLastModified == that.managedLastModified
            && managedLength == that.managedLength
            && managedWidth == that.managedWidth
            && managedHeight == that.managedHeight
            && appOnly == that.appOnly;
    }

    @Override
    public int hashCode() {
        int result = storedWallpaperId;
        result = 31 * result + systemWallpaperId;
        result = 31 * result + (managedFilePresent ? 1 : 0);
        result = 31 * result + Long.hashCode(managedLastModified);
        result = 31 * result + Long.hashCode(managedLength);
        result = 31 * result + managedWidth;
        result = 31 * result + managedHeight;
        result = 31 * result + (appOnly ? 1 : 0);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "WallpaperSourceFacts{stored=" + storedWallpaperId + ", id=" + systemWallpaperId + ", managed=" + managedFilePresent
            + ", " + managedWidth + "x" + managedHeight + (appOnly ? ", appOnly" : "") + "}";
    }
}
