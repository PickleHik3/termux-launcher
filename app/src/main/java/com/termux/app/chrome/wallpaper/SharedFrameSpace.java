package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;

/**
 * Screen pixels to the shared frame's pixels, which is what {@link GeneratedWallpaperHost}'s
 * moment methods take. A surface at screen x samples the frame at x plus the live parallax
 * offset ({@link com.termux.app.chrome.WallpaperParallax}); y has no parallax. The frame rect is
 * the blur cache's capture rect, in screen coordinates. Pure arithmetic, no Android types.
 */
public final class SharedFrameSpace {

    private SharedFrameSpace() {}

    public static float x(float screenX, int frameLeft, float parallaxOffsetPx) {
        return screenX - frameLeft + parallaxOffsetPx;
    }

    public static float y(float screenY, int frameTop) {
        return screenY - frameTop;
    }

    /** Writes {left, top, right, bottom} of the screen rect in frame pixels into {@code out}. */
    public static void rect(float left, float top, float right, float bottom,
                            int frameLeft, int frameTop, float parallaxOffsetPx,
                            @NonNull float[] out) {
        out[0] = x(left, frameLeft, parallaxOffsetPx);
        out[1] = y(top, frameTop);
        out[2] = x(right, frameLeft, parallaxOffsetPx);
        out[3] = y(bottom, frameTop);
    }
}
