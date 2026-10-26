package com.termux.app.surfaces;

import androidx.annotation.ColorInt;

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
