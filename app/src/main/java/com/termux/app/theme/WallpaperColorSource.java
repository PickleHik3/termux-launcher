package com.termux.app.theme;

import androidx.annotation.NonNull;

/**
 * Where "the wallpaper's colours" come from: Android's wallpaper, or the launcher's app-only
 * picture (ADR 0009) through the seed captured from it.
 *
 * <p>Pure, so the rule is pinned by tests: the activity and settings themes, the terminal's
 * Wallpaper colours and the exported palettes all ask {@link AppOnlyWallpaperColors}, which reads
 * the facts and asks here.
 */
public final class WallpaperColorSource {

    /** The stored seed when none could be taken from the picture; never a real seed (alpha 0). */
    public static final int NO_SEED = 0;

    /** The answer. */
    public enum Kind {
        /** Android's wallpaper colours, exactly as without an app-only picture. */
        SYSTEM,
        /** The app-only picture's stored seed. */
        APP_ONLY,
        /**
         * An app-only picture is on screen but the stored seed describes another picture (or none
         * was ever taken: a picture set before seeds were kept, a restore). Android's colours are
         * worn until the seed is taken off the main thread, then the activity re-themes.
         */
        NEEDS_SEED
    }

    private WallpaperColorSource() {}

    /**
     * Which colours the launcher wears.
     *
     * @param dynamicColorSupported the theme takes its roles from the wallpaper at all (Android
     *     12+); below that nothing follows a wallpaper, app-only or not
     * @param schemeChromeActive the chrome is on the terminal's colour scheme, which no wallpaper
     *     drives
     * @param appOnlyOnScreen the app-only mark holds and its copy is on disk
     * @param pictureStamp {@link #pictureStamp} of the copy on disk now
     * @param storedStamp the stamp stored with the seed (0 when none)
     * @param storedSeed the stored seed, or {@link #NO_SEED}
     */
    @NonNull
    public static Kind choose(boolean dynamicColorSupported, boolean schemeChromeActive,
                              boolean appOnlyOnScreen, long pictureStamp, long storedStamp,
                              int storedSeed) {
        if (!dynamicColorSupported || schemeChromeActive || !appOnlyOnScreen) return Kind.SYSTEM;
        if (pictureStamp == 0L || storedStamp != pictureStamp) return Kind.NEEDS_SEED;
        // A picture no seed could be taken from keeps Android's colours rather than retrying on
        // every arrival.
        return storedSeed == NO_SEED ? Kind.SYSTEM : Kind.APP_ONLY;
    }

    /**
     * The identity a seed is stored against: the exact copy's {@code lastModified()} and
     * {@code length()}, the same two facts {@code WallpaperSourceFacts} keeps. 0 for no file.
     */
    public static long pictureStamp(long lastModified, long length) {
        if (lastModified == 0L && length == 0L) return 0L;
        long stamp = lastModified * 31L + length;
        // 0 means "no seed stored"; a real picture never stamps as that.
        return stamp == 0L ? 1L : stamp;
    }
}
