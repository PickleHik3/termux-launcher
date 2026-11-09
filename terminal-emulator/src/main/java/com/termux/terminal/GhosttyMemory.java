package com.termux.terminal;

import android.content.ComponentCallbacks2;

import java.util.ArrayList;

/**
 * Every live Ghostty core in the process, so memory pressure reaches all of them at once.
 *
 * <p>The app forwards {@code onTrimMemory} here. From {@code TRIM_MEMORY_BACKGROUND} up every core
 * compresses its whole history in one pass (0.3 ms for 200k lines on pong), and every registered
 * {@link ImageTrim} drops the image bitmaps it keeps. The idle compress after output
 * ({@link GhosttyIdleCompressor}) does the same work a step at a time while the launcher is in use.
 *
 * <p>Main thread only.
 */
public final class GhosttyMemory {

    /** The lowest {@code onTrimMemory} level that compresses every core. */
    static final int TRIM_LEVEL = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;

    /**
     * Something that keeps decoded images for Ghostty panes (the kitty image cache) and can drop
     * them under pressure; they are decoded again when next drawn.
     */
    public interface ImageTrim {
        void trimImages();
    }

    private static final ArrayList<GhosttyCore> sCores = new ArrayList<>();
    private static final ArrayList<ImageTrim> sImageTrims = new ArrayList<>();

    private GhosttyMemory() {}

    static void register(GhosttyCore core) {
        if (!sCores.contains(core)) sCores.add(core);
    }

    /** Forget a released core; returns how many are still alive. */
    static int unregister(GhosttyCore core) {
        sCores.remove(core);
        return sCores.size();
    }

    static int liveCores() {
        return sCores.size();
    }

    /** Image caches a trim reaches now; for tests. */
    static boolean hasImageTrim(ImageTrim trim) {
        return sImageTrims.contains(trim);
    }

    /** Add an image cache to drop on pressure; remove it when its pane closes. */
    public static void addImageTrim(ImageTrim trim) {
        if (trim != null && !sImageTrims.contains(trim)) sImageTrims.add(trim);
    }

    public static void removeImageTrim(ImageTrim trim) {
        sImageTrims.remove(trim);
    }

    /**
     * The app's {@code onTrimMemory}: from {@code TRIM_MEMORY_BACKGROUND} up, compress every core's
     * history fully and drop kept image bitmaps. Returns how many cores were compressed.
     */
    public static int onTrimMemory(int level) {
        if (level < TRIM_LEVEL) return 0;
        int compressed = 0;
        // A copy, in case a failing core falls back to the Java one and leaves the list.
        GhosttyCore[] cores = sCores.toArray(new GhosttyCore[0]);
        for (GhosttyCore core : cores) {
            if (core.compressFully()) compressed++;
        }
        for (int i = sImageTrims.size() - 1; i >= 0; i--) {
            try {
                sImageTrims.get(i).trimImages();
            } catch (RuntimeException ignored) {
                // One cache failing to trim must not stop the rest.
            }
        }
        return compressed;
    }

    /** For tests: forget every core and image cache. */
    static void clearForTests() {
        sCores.clear();
        sImageTrims.clear();
    }
}
