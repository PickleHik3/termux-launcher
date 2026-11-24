package com.termux.shared.file;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Deletes the entries under a directory that have not been modified for an age, and keeps newer
 * entries together with the directories that hold them. The directory itself is never deleted.
 *
 * Symlinks are judged by their own modification time and deleted as links; they are never
 * followed, so a target outside the directory is never touched. Safe to call off the main thread.
 */
public final class OlderThanSweeper {

    /** What a sweep removed. */
    public static final class Result {
        /** Bytes of the regular files that were deleted. */
        public long bytesFreed;
        /** Files, links and directories that were deleted. */
        public int entriesDeleted;
        /** Entries that were old enough but could not be deleted. */
        public int entriesFailed;
    }

    private OlderThanSweeper() {}

    /** Sweep {@code root} of entries last modified more than {@code ageMillis} before {@code nowMillis}. */
    @NonNull
    public static Result sweep(@NonNull File root, long ageMillis, long nowMillis) {
        Result result = new Result();
        if (!isRealDirectory(root)) return result;
        File[] children = root.listFiles();
        if (children == null) return result;
        long cutoff = nowMillis - ageMillis;
        for (File child : children) sweepEntry(child, cutoff, result);
        return result;
    }

    /** A symlinked root is a user's opt-out of clearing, so only a real directory is swept. */
    private static boolean isRealDirectory(File root) {
        try {
            return Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS);
        } catch (LinkageError e) {
            long[] s = lstat(root.getPath());
            return s != null && s[2] == 1;
        }
    }

    /** Returns true when the entry is gone afterwards. */
    private static boolean sweepEntry(File entry, long cutoff, Result result) {
        long[] attrs = attributes(entry);
        if (attrs == null) return false;
        long mtime = attrs[0];
        long size = attrs[1];
        boolean isDirectory = attrs[2] == 1;
        boolean old = mtime < cutoff;
        if (isDirectory) {
            boolean allGone = true;
            File[] children = entry.listFiles();
            if (children != null) {
                for (File c : children) allGone &= sweepEntry(c, cutoff, result);
            }
            if (!allGone || !old) return false;
            return deleteOne(entry, 0, result);
        }
        if (!old) return false;
        return deleteOne(entry, size, result);
    }

    private static boolean deleteOne(File entry, long size, Result result) {
        if (entry.delete()) {
            result.entriesDeleted++;
            result.bytesFreed += size;
            return true;
        }
        result.entriesFailed++;
        return false;
    }

    /**
     * Bytes of the regular files under {@code root}, never following a symlink; 0 when the root is
     * missing, unreadable or itself a link. Safe to call off the main thread.
     */
    public static long sizeOf(@NonNull File root) {
        if (!isRealDirectory(root)) return 0L;
        return sizeOfChildren(root);
    }

    private static long sizeOfChildren(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            long[] a = attributes(child);
            if (a == null) continue; // Gone mid-walk or unreadable: not counted.
            total += a[1];
            if (a[2] == 1) total += sizeOfChildren(child);
        }
        return total;
    }

    /** {mtimeMillis, size if regular else 0, 1 if a real directory} of the entry itself, or null. */
    private static long[] attributes(File entry) {
        try {
            BasicFileAttributes a = Files.readAttributes(entry.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new long[]{a.lastModifiedTime().toMillis(), a.isRegularFile() ? a.size() : 0, a.isDirectory() ? 1 : 0};
        } catch (LinkageError e) {
            // Android before 8 has no java.nio.file.
            return lstat(entry.getPath());
        } catch (Exception e) {
            return null;
        }
    }

    /** The pre-Android-8 path of {@link #attributes}. */
    private static long[] lstat(String path) {
        try {
            StructStat st = Os.lstat(path);
            long size = OsConstants.S_ISREG(st.st_mode) ? st.st_size : 0;
            return new long[]{st.st_mtime * 1000L, size, OsConstants.S_ISDIR(st.st_mode) ? 1 : 0};
        } catch (ErrnoException e) {
            return null;
        }
    }
}
