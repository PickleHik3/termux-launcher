package com.termux.app.chrome.wallpaper;

/**
 * One generated background: an original AGSL generator. It names no Android graphics type, so the
 * registry and its tests run on a plain JVM; {@link WallpaperUniforms} is what compiles the
 * program (API 33+) and binds the frame's uniforms.
 *
 * <p>Every program declares the same uniform contract (see {@link MomentAgsl#HEAD}), is
 * independent of {@code uTime} at {@code uEnergy == 0} and {@code uPhase == 0} (its rest pose),
 * and loops seamlessly every {@link #periodSeconds()} of either clock.</p>
 */
public interface AnimatedWallpaper {

    /** Stable id stored in preferences and used by {@code launcherctl}: aurora, mesh, tide, rain. */
    String id();

    /** Name shown on the picker tile. */
    String label();

    /** Length of the motion loop, at least 60 s. */
    float periodSeconds();

    /** The four ARGB colours the background ships with, used in "own" palette mode. */
    int[] ownPalette();

    /** The AGSL source, with the shared moment code already included. */
    String agsl();
}
