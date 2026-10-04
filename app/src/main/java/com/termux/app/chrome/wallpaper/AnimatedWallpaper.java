package com.termux.app.chrome.wallpaper;

/**
 * One animated background: a living still, a photo played by an AGSL program. It names no Android graphics type, so the
 * registry and its tests run on a plain JVM; {@link WallpaperUniforms} is what compiles the
 * program (API 33+) and binds the frame's uniforms.
 *
 * <p>Every program declares the same uniform contract (see {@link MomentAgsl#HEAD}), is
 * independent of {@code uTime} at {@code uEnergy == 0} and {@code uPhase == 0} (its rest pose,
 * the photo itself), and loops seamlessly every {@link #periodSeconds()} of either clock.</p>
 */
public interface AnimatedWallpaper {

    /** Stable id stored in preferences and used by {@code launcherctl}: {@code living:<hash>}. */
    String id();

    /** Name shown on the picker tile. */
    String label();

    /** Length of the motion loop, at least 60 s. */
    float periodSeconds();

    /** The four ARGB colours the background is drawn with. */
    int[] ownPalette();

    /** The AGSL source, with the shared moment code ({@link MomentAgsl#HEAD}) already included. */
    String agsl();
}
