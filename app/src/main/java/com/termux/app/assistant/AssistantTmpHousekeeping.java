package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.shared.file.OlderThanSweeper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The tmp offer's two jobs, both for a background thread: how big the folder is, and clearing what
 * has sat untouched for a day. Neither follows a symlink out of the folder.
 */
final class AssistantTmpHousekeeping {

    /** What clears old entries; {@code TermuxShellUtils::clearTermuxTMPDIROlderThan} in the app. */
    interface Clearer {
        @NonNull OlderThanSweeper.Result clear(long ageMillis);
    }

    private AssistantTmpHousekeeping() {}

    /** Bytes of the regular files under {@code root}; 0 when it is missing or itself a link. */
    static long sizeOf(@NonNull File root) {
        try {
            if (Files.isSymbolicLink(root.toPath())) return 0L;
        } catch (RuntimeException | LinkageError ignored) {
            return 0L;
        }
        return walk(root);
    }

    private static long walk(@NonNull File dir) {
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            try {
                BasicFileAttributes a = Files.readAttributes(child.toPath(), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if (a.isRegularFile()) total += a.size();
                else if (a.isDirectory()) total += walk(child);
            } catch (Exception | LinkageError ignored) {
                // Gone mid-walk or unreadable: not counted.
            }
        }
        return total;
    }

    /** Clears entries untouched for a day and returns the bytes that freed; 0 when the clear fails. */
    static long clearOld(@NonNull Clearer clearer) {
        try {
            return Math.max(0L, clearer.clear(AssistantOffersPolicy.CLEAR_AGE_MS).bytesFreed);
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
