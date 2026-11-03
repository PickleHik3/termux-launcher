package com.termux.app.surfaces;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The ink of the page label under the editor's frame (editor-redesign §8, "Text over the wallpaper
 * follows the wallpaper"). The label sits on the wallpaper the frame paints, which stays light
 * under a dark theme, so the ink follows that picture's luminance, never the theme.
 *
 * <pre>
 *   int ink = EditorLabelInk.pick(sampledBackdrop, darkInk, paleInk);
 * </pre>
 *
 * Pure: held by JVM tests.
 */
final class EditorLabelInk {
    private EditorLabelInk() {}

    /**
     * Whichever of the two inks contrasts more with {@code backdrop} (opaque): the launcher's one
     * rule for text on the wallpaper ({@link com.termux.app.chrome.WallpaperInk#bareInk}).
     */
    @ColorInt
    static int pick(@ColorInt int backdrop, @ColorInt int darkInk, @ColorInt int paleInk) {
        return com.termux.app.chrome.WallpaperInk.bareInk(backdrop, darkInk, paleInk);
    }

    /**
     * The colour the label stands on: the sampled picture's rows at {@code fraction}
     * ({@link #band}) when a picture was read, else the system's description of its wallpaper
     * ({@code hint}, opaque), else transparent: nothing is known and the label keeps the theme's
     * ink. The launcher cannot read the system's wallpaper without storage permission (a fresh
     * install), so there the hint is all there is.
     */
    @ColorInt
    static int backdrop(@Nullable int[] picture, int width, int height, float fraction,
                        @ColorInt int hint) {
        if (picture != null)
            return band(picture, width, height, fraction);
        return isKnown(hint) ? hint | 0xFF000000 : 0;
    }

    /** Whether {@code backdrop} says anything: {@link #backdrop}'s transparent says nothing. */
    static boolean isKnown(@ColorInt int backdrop) {
        return (backdrop >>> 24) != 0;
    }

    /** The sampled picture's width in pixels; its height follows the decor's shape. */
    static final int SAMPLE_WIDTH = 16;

    /** The sampled picture's height: {@link #SAMPLE_WIDTH} in the decor's proportions. */
    static int sampleHeight(int decorWidth, int decorHeight) {
        if (decorWidth <= 0 || decorHeight <= 0)
            return SAMPLE_WIDTH;
        int h = Math.round(SAMPLE_WIDTH * (float) decorHeight / decorWidth);
        return Math.max(4, Math.min(SAMPLE_WIDTH * 4, h));
    }

    /**
     * Where a picture of {@code srcWidth} x {@code srcHeight} is drawn into a {@code width} x
     * {@code height} sample so it is centre-cropped as the editor's frame and the screen show it:
     * scaled to cover, the overflow split evenly. A picture with no size of its own is stretched.
     *
     * @return left, top, right, bottom
     */
    @NonNull
    static int[] cropBounds(int srcWidth, int srcHeight, int width, int height) {
        if (srcWidth <= 0 || srcHeight <= 0)
            return new int[] {0, 0, width, height};
        float scale = Math.max(width / (float) srcWidth, height / (float) srcHeight);
        int w = Math.round(srcWidth * scale);
        int h = Math.round(srcHeight * scale);
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        return new int[] {left, top, left + w, top + h};
    }

    /**
     * The colour of the sampled picture's rows at {@code fraction} of its height (the label's
     * centre on the screen), averaged over the two rows nearest it; opaque.
     */
    @ColorInt
    static int band(@NonNull int[] argb, int width, int height, float fraction) {
        if (width <= 0 || height <= 0 || argb.length < width * height)
            return average(argb, argb.length, 0);
        float f = Float.isNaN(fraction) ? 1f : Math.max(0f, Math.min(1f, fraction));
        int row = Math.max(0, Math.min(height - 2, Math.round(f * height) - 1));
        int rows = Math.min(2, height - row);
        int[] band = new int[width * rows];
        System.arraycopy(argb, row * width, band, 0, band.length);
        return average(band, band.length, 0);
    }

    /** The average of {@code argb} pixels, opaque, then {@code dim} laid over it. */
    @ColorInt
    static int average(int[] argb, int count, @ColorInt int dim) {
        long r = 0, g = 0, b = 0;
        int n = Math.max(1, Math.min(count, argb.length));
        for (int i = 0; i < n; i++) {
            r += (argb[i] >> 16) & 0xFF;
            g += (argb[i] >> 8) & 0xFF;
            b += argb[i] & 0xFF;
        }
        int ar = (int) (r / n), ag = (int) (g / n), ab = (int) (b / n);
        int a = (dim >>> 24) & 0xFF;
        ar = (ar * (255 - a) + ((dim >> 16) & 0xFF) * a) / 255;
        ag = (ag * (255 - a) + ((dim >> 8) & 0xFF) * a) / 255;
        ab = (ab * (255 - a) + (dim & 0xFF) * a) / 255;
        return 0xFF000000 | (ar << 16) | (ag << 8) | ab;
    }
}
