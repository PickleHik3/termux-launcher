package com.termux.app.chrome.wallpaper;

import androidx.annotation.Nullable;

/**
 * What the activity knows about the live generated background, for {@code GET /v1/wallpaper}. The
 * activity registers one through {@link GeneratedWallpaperApplier#setStatusProvider}; with none
 * registered the route reports not playing, reason {@code inactive}.
 */
public interface AnimatedWallpaperStatus {

    /** Whether frames are being produced right now. */
    boolean playing();

    /** Why not, when {@link #playing()} is false: api, fancier_glass_off, paused, killed or inactive. Null while playing. */
    @Nullable
    String reason();

    /** The self-check's step-down tier, 0 = full rate and resolution. */
    default int tier() {
        return 0;
    }

    /** How many times the renderer was killed by the self-check in this process. */
    default int kills() {
        return 0;
    }
}
