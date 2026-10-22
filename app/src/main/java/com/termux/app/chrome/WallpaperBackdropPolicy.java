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
 * nothing left to correct — which is why {@link #renderZoomPercent} pins it to 100 there. A picture
 * we cannot be sure of (see {@link WallpaperPicture}) or a wallpaper we are not allowed to read
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
     * @param picture                 which picture the glass can read, and how sure we are it is the
     *                                one on screen. Only {@link WallpaperPicture#MATCHES_SCREEN} may
     *                                be painted by us: anything else would put a picture behind
     *                                everything that the user is not actually looking at.
     * @param wallpaperReadable       the wallpaper can still be captured (no read was refused)
     */
    @NonNull
    public static Mode mode(boolean wallpaperFeatureEnabled, @NonNull WallpaperPicture picture,
                            boolean wallpaperReadable) {
        return mode(wallpaperFeatureEnabled, picture, wallpaperReadable, false);
    }

    /**
     * {@link #mode(boolean, WallpaperPicture, boolean)} with the launcher's app-only picture.
     *
     * @param appOnlyPicture the launcher wears a picture of its own that Android never got (see
     *                       {@link WallpaperSourceFacts#appOnlyOnScreen}). Only the launcher can
     *                       show it, so it is always self-drawn while wallpaper mode is on; it is
     *                       the app's own file, so a refused read of Android's wallpaper does not
     *                       stand in its way.
     */
    @NonNull
    public static Mode mode(boolean wallpaperFeatureEnabled, @NonNull WallpaperPicture picture,
                            boolean wallpaperReadable, boolean appOnlyPicture) {
        if (!wallpaperFeatureEnabled) return Mode.PASSTHROUGH;
        if (appOnlyPicture) return Mode.SELF_DRAWN;
        return picture.allowsSelfDrawnBackdrop() && wallpaperReadable
            ? Mode.SELF_DRAWN : Mode.PASSTHROUGH;
    }

    /**
     * Whether the window still asks Android to composite the phone's wallpaper behind it.
     *
     * <p>Only an app-only picture turns it off: every other self-drawn frame is the phone's own
     * picture, so the system's showing for the frame or two before the capture lands (a cold
     * start, a rotation) is the same picture. Under an app-only picture it is a different one,
     * and would flash. With wallpaper mode off the theme has no wallpaper to begin with.</p>
     */
    public static boolean showsSystemWallpaper(@NonNull Mode mode, boolean appOnlyPicture) {
        return !(mode == Mode.SELF_DRAWN && appOnlyPicture);
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
