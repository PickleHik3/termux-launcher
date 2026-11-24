package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a live picture needs, on top of its blur, to come out as the stored frame does at rest, so
 * the hand-off from one to the other (and back) never changes the glass's look.
 *
 * <p>The stored frame is the wallpaper alone, blurred. A live recording is what the window draws
 * there, and the launcher-drawn wallpaper ({@link WallpaperBackdropView}) draws the wall's dim
 * over itself, which the stored frame never carries. So the live picture takes the dim back out
 * ({@code in = (out - d a) / (1 - a)}, exact for a dim drawn source-over an opaque wallpaper).
 * That is all: the glass's colour (Vibrancy, the frame tint) is the program's, applied to
 * whichever picture it is handed ({@link GlassRefraction.Look#withColour}), and live pictures
 * exist only while the program draws (#321). Pure floats; a picture builds its filter from this
 * only when the dim moves, never per draw.</p>
 */
public final class LiveStoredParity {

    private LiveStoredParity() {}

    /**
     * The dim the parity will undo at most: past it the dim leaves too little of the wallpaper to
     * lift back, and the picture keeps whatever dim is beyond.
     */
    static final float MAX_UNDIM_ALPHA = 0.9f;

    /**
     * The 4x5 matrix that takes a live recording made under {@code dimColor}, drawn source-over,
     * back to the stored frame's colours; null for no dim.
     */
    @Nullable
    public static float[] matrix(int dimColor) {
        int alpha = dimColor >>> 24;
        if (alpha <= 0) return null;
        float a = Math.min(MAX_UNDIM_ALPHA, alpha / 255f);
        float gain = 1f / (1f - a);
        float[] m = new float[20];
        m[0] = gain;
        m[6] = gain;
        m[12] = gain;
        m[18] = 1f;
        m[4] = -((dimColor >> 16) & 0xFF) * a * gain;
        m[9] = -((dimColor >> 8) & 0xFF) * a * gain;
        m[14] = -(dimColor & 0xFF) * a * gain;
        return m;
    }

    /** {@code rgb} (0..255 each) through {@code m}, unclamped; what a test models. */
    @NonNull
    static float[] apply(@NonNull float[] m, float r, float g, float b) {
        float[] in = {r, g, b, 255f};
        float[] out = new float[3];
        for (int row = 0; row < 3; row++) {
            float sum = m[row * 5 + 4];
            for (int k = 0; k < 4; k++) sum += m[row * 5 + k] * in[k];
            out[row] = sum;
        }
        return out;
    }
}
