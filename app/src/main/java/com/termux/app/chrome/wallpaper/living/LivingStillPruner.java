package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Deletes orphaned living-still folders ({@code files/wallpaper/living/<hash16>/}). A folder is
 * kept when its hash is in the keep set, when its {@code recipe.json} is missing (a build writes it
 * last, so the folder may be in progress), or when anything in it changed within
 * {@link #GRACE_MS}. Only direct children named like a 16-hex hash are ever considered, and
 * symbolic links are never followed. Pure Java over {@link File}s, blocking: worker threads only.
 */
public final class LivingStillPruner {

    /** A folder touched this recently is left alone. */
    public static final long GRACE_MS = 10L * 60L * 1000L;

    private static final String RECIPE = "recipe.json";

    private LivingStillPruner() {}

    /**
     * @param livingRoot the {@code living} directory
     * @param keepHashes hashes (first 16 hex of SHA-256) whose folders stay
     * @return how many folders were deleted
     */
    public static int prune(@NonNull File livingRoot, @NonNull Set<String> keepHashes) {
        return prune(livingRoot, keepHashes, System.currentTimeMillis());
    }

    static int prune(@NonNull File livingRoot, @NonNull Set<String> keepHashes, long nowMs) {
        File[] children = livingRoot.listFiles();
        if (children == null) return 0;
        int deleted = 0;
        for (File dir : children) {
            String name = dir.getName();
            if (!name.matches("[0-9a-f]{16}") || !dir.isDirectory() || isLink(dir)) continue;
            if (keepHashes.contains(name)) continue;
            if (!new File(dir, RECIPE).isFile()) continue;
            if (nowMs - newest(dir) < GRACE_MS) continue;
            if (deleteTree(dir, livingRoot)) deleted++;
        }
        return deleted;
    }

    /** The latest modification time of the folder or anything directly inside it. */
    private static long newest(@NonNull File dir) {
        long t = dir.lastModified();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) t = Math.max(t, f.lastModified());
        return t;
    }

    private static boolean isLink(@NonNull File f) {
        try {
            return !f.getCanonicalFile().equals(new File(f.getParentFile().getCanonicalFile(), f.getName()));
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean deleteTree(@NonNull File dir, @NonNull File root) {
        try {
            if (!dir.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) return false;
        } catch (IOException e) {
            return false;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !isLink(f)) {
                    deleteTree(f, dir);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        return dir.delete();
    }
}
