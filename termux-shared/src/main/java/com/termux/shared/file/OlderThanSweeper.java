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
        long mtime;
        long size;
        boolean isDirectory;
        try {
            BasicFileAttributes a = Files.readAttributes(entry.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            mtime = a.lastModifiedTime().toMillis();
            size = a.isRegularFile() ? a.size() : 0;
            isDirectory = a.isDirectory();
        } catch (LinkageError e) {
            // Android before 8 has no java.nio.file.
            long[] s = lstat(entry.getPath());
            if (s == null) return false;
            mtime = s[0];
            size = s[1];
            isDirectory = s[2] == 1;
        } catch (Exception e) {
            return false;
        }
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

    /** {mtimeMillis, size if regular else 0, 1 if a real directory}, or null. */
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
