package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the platform and the disk said about the wallpaper the last time anyone asked: the system
 * wallpaper's id, and whether the launcher's managed copy is on disk, with its identity and its
 * pixel size.
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
        new WallpaperSourceFacts(0, -1, false, 0L, 0L, 0, 0);

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

    private WallpaperSourceFacts(int storedWallpaperId, int systemWallpaperId,
                                 boolean managedFilePresent, long managedLastModified,
                                 long managedLength, int managedWidth, int managedHeight) {
        this.storedWallpaperId = storedWallpaperId;
        this.systemWallpaperId = systemWallpaperId;
        this.managedFilePresent = managedFilePresent;
        this.managedLastModified = managedLastModified;
        this.managedLength = managedLength;
        boolean sized = managedFilePresent && managedWidth > 0 && managedHeight > 0;
        this.managedWidth = sized ? managedWidth : 0;
        this.managedHeight = sized ? managedHeight : 0;
    }

    @NonNull
    public static WallpaperSourceFacts of(int storedWallpaperId, int systemWallpaperId,
                                          boolean managedFilePresent, long managedLastModified,
                                          long managedLength, int managedWidth, int managedHeight) {
        return new WallpaperSourceFacts(storedWallpaperId, systemWallpaperId, managedFilePresent,
            managedLastModified, managedLength, managedWidth, managedHeight);
    }

    /** True when the picker has stored another id since these were read: read them again. */
    public boolean isStaleFor(int currentStoredWallpaperId) {
        return currentStoredWallpaperId != storedWallpaperId;
    }

    /**
     * Whether the wallpaper on screen is the launcher's own: the id the picker stored is the
     * system's current one, and the exact copy is still on disk.
     */
    public boolean managedOnScreen() {
        return storedWallpaperId > 0 && storedWallpaperId == systemWallpaperId && managedFilePresent;
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
            && managedHeight == that.managedHeight;
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
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "WallpaperSourceFacts{stored=" + storedWallpaperId + ", id=" + systemWallpaperId + ", managed=" + managedFilePresent
            + ", " + managedWidth + "x" + managedHeight + "}";
    }
}
