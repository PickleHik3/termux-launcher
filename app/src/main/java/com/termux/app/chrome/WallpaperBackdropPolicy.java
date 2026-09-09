package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * Who draws the wallpaper the glass is cut from — the system, or us.
 *
 * <p>The launcher window is translucent with {@code windowShowWallpaper}, so the ROM composites the
 * wallpaper behind it at a zoom we cannot read: on Nothing OS it measured 1.03 for one wallpaper
 * and 1.084 for another, it ignores {@code setWallpaperZoomOut}, and it moves with every wallpaper.
 * Every glass crop is cut from a frame this app captures itself, so the two pictures can only be
 * lined up by guessing that factor — which is what the Wallpaper alignment slider is.</p>
 *
 * <p>The way out is to stop guessing: when the wallpaper is a still image we can read, the launcher
 * draws it itself, from the very frame the crops come from. Backdrop and glass are then the same
 * pixels at the same offset, so alignment holds by construction on every ROM and the slider has
 * nothing left to correct — which is why {@link #renderZoomPercent} pins it to 100 there. A live
 * wallpaper (a moving picture no capture can match) or a wallpaper we are not allowed to read
 * leaves the system drawing it, and the slider is the only tool that mode has.</p>
 */
public final class WallpaperBackdropPolicy {

    /** Which of the two pictures the user is looking at behind the chrome. */
    public enum Mode {
        /** The launcher paints the captured frame itself, opaque, behind everything. */
        SELF_DRAWN,
        /** The ROM composites the wallpaper behind the translucent window, as it always did. */
        PASSTHROUGH
    }

    private WallpaperBackdropPolicy() {
    }

    /**
     * @param wallpaperFeatureEnabled the {@code use_system_wallpaper} preference
     * @param liveWallpaperActive     a wallpaper service is running, so there is no frame to match
     * @param wallpaperReadable       the wallpaper can still be captured (no read was refused)
     */
    @NonNull
    public static Mode mode(boolean wallpaperFeatureEnabled, boolean liveWallpaperActive,
                            boolean wallpaperReadable) {
        return wallpaperFeatureEnabled && !liveWallpaperActive && wallpaperReadable
            ? Mode.SELF_DRAWN : Mode.PASSTHROUGH;
    }

    /**
     * The zoom every capture in this mode is taken at, as the slider's percent.
     *
     * <p>Self-drawn shares one frame between the backdrop and the glass, so a zoom would move the
     * glass off our own backdrop rather than onto the wallpaper: it is pinned at 1.0. Passthrough
     * is still matching a picture drawn by someone else, and takes the user's number.</p>
     */
    public static int renderZoomPercent(@NonNull Mode mode, int preferencePercent) {
        return mode == Mode.SELF_DRAWN ? 100 : preferencePercent;
    }

    /** Whether the Wallpaper alignment slider does anything in this mode. */
    public static boolean alignmentSliderApplies(@NonNull Mode mode) {
        return mode == Mode.PASSTHROUGH;
    }
}
