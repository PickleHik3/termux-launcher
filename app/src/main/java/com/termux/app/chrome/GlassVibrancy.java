package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * Vibrancy: the saturation of the blurred backdrop every glass surface shows, with the Tint
 * amount's frame tint ({@link GlassFrameTint}) after it, as one colour matrix ({@link #bakeMatrix}).
 *
 * <p>Where Fancier Glass draws the backdrop, the program applies the matrix to every backdrop
 * sample ({@link GlassRefraction.Look#withColour}), so the stored frames and the live pictures
 * stay the raw blur and match by construction (#321). Where the plain draw shows the frame (no
 * program: Fancier Glass off, or a phone that runs none), the matrix is baked into each frame
 * once, when {@link WallpaperBlurCache} blurs it, and a change re-bakes the frames as a wallpaper
 * change does; that route has no live pictures. Radius 0, the wallpaper as the wall shows it,
 * never takes a bake: the bare wallpaper is the user's picture.</p>
 *
 * <p>The ink check measures the colour the glass shows: the baked frame, or the raw frame through
 * the same matrix ({@link #apply}).</p>
 */
public final class GlassVibrancy {

    private GlassVibrancy() {}

    /** A stored percentage held to the control's range, 70 to 150; 100 is today's frame. */
    public static int clampPercent(int percent) {
        return Math.max(TERMUX_APP.MIN_SURFACE_GLASS_VIBRANCY,
            Math.min(TERMUX_APP.MAX_SURFACE_GLASS_VIBRANCY, percent));
    }

    /** The saturation a stored percentage bakes in: 0.7x to 1.5x, 1 at 100. */
    public static float saturation(int percent) {
        return clampPercent(percent) / 100f;
    }

    /** Whether a frame blurred at {@code blurRadiusDp} under {@code percent} is baked at all. */
    public static boolean bakes(int blurRadiusDp, int percent) {
        return blurRadiusDp > 0 && clampPercent(percent) != 100;
    }

    /**
     * The colour matrix a frame is baked through: {@code percent}'s saturation (as
     * {@code ColorMatrix.setSaturation}), then {@code frameTint}'s hue shift: what the program
     * applies to its backdrop, or the plain draw's frames are baked through. Pure.
     */
    @NonNull
    public static float[] bakeMatrix(int percent, int frameTint) {
        float sat = saturation(percent);
        float inv = 1f - sat;
        float r = 0.213f * inv;
        float g = 0.715f * inv;
        float b = 0.072f * inv;
        float[] m = new float[20];
        m[0] = r + sat; m[1] = g; m[2] = b;
        m[5] = r; m[6] = g + sat; m[7] = b;
        m[10] = r; m[11] = g; m[12] = b + sat;
        m[18] = 1f;
        if (frameTint == GlassFrameTint.NONE) return m;
        return concat(GlassFrameTint.matrix(frameTint), m);
    }

    /**
     * {@code color} through {@code matrix} (a 4x5 {@code ColorMatrix} array), channels held to
     * 0..255, alpha kept: one pixel as a frame baked through it comes out. Allocates nothing.
     */
    @ColorInt
    public static int apply(@NonNull float[] matrix, @ColorInt int color) {
        float r = (color >> 16) & 0xFF;
        float g = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        int out = color & 0xFF000000;
        for (int row = 0; row < 3; row++) {
            float c = matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b
                + matrix[row * 5 + 4];
            out |= Math.round(Math.max(0f, Math.min(255f, c))) << (16 - 8 * row);
        }
        return out;
    }

    /**
     * The filter a still ground drawn beside the glass wears (the departure card's, a switch's
     * gap; {@code TermuxActivity#glassGroundFilter}): the frost at {@code opacityPercent} after
     * the glass's colour where {@code colours}, the frost alone otherwise. A ground has no
     * program, so it takes the colour here: over the scene's live frame, which carries none, and
     * over a raw stored frame while the program owns the colour.
     */
    @NonNull
    public static synchronized ColorFilter groundFilter(int opacityPercent, boolean colours,
                                                        int percent, int frameTint) {
        if (!colours || (clampPercent(percent) == 100 && frameTint == GlassFrameTint.NONE)) {
            return GlassFilters.frost(opacityPercent);
        }
        int frost = GlassFilters.frost(opacityPercent) == GlassFilters.frost() ? 1 : 0;
        ColorFilter last = sGround;
        if (last != null && sGroundFrost == frost && sGroundPercent == clampPercent(percent)
            && sGroundTint == frameTint) return last;
        sGround = new ColorMatrixColorFilter(groundMatrix(opacityPercent, percent, frameTint));
        sGroundFrost = frost;
        sGroundPercent = clampPercent(percent);
        sGroundTint = frameTint;
        return sGround;
    }

    /** {@link #groundFilter}'s last answer and what it was built for: one per colour, not per call. */
    @Nullable private static ColorFilter sGround;
    private static int sGroundFrost = -1;
    private static int sGroundPercent;
    private static int sGroundTint;

    /** {@link #groundFilter}'s matrix: the colour first, then the frost. Pure. */
    @NonNull
    static float[] groundMatrix(int opacityPercent, int percent, int frameTint) {
        return concat(GlassFilters.frostMatrix(opacityPercent).getArray(),
            bakeMatrix(percent, frameTint));
    }

    /**
     * {@code outer} after {@code inner}, as {@code ColorMatrix.setConcat(outer, inner)}: a colour
     * goes through {@code inner} first.
     */
    @NonNull
    static float[] concat(@NonNull float[] outer, @NonNull float[] inner) {
        float[] out = new float[20];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float sum = col == 4 ? outer[row * 5 + 4] : 0f;
                for (int k = 0; k < 4; k++) sum += outer[row * 5 + k] * inner[k * 5 + col];
                out[row * 5 + col] = sum;
            }
        }
        return out;
    }

    /**
     * {@code frame} at {@code percent}'s saturation and then {@code frameTint}'s hue shift
     * ({@link GlassFrameTint}), in one pass, as a new bitmap; {@code frame} is recycled. Null when
     * the copy cannot be made, which the cache treats as a frame that failed to blur.
     */
    @Nullable
    static Bitmap baked(@NonNull Bitmap frame, int percent, int frameTint) {
        Bitmap out = null;
        try {
            out = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
            ColorMatrix matrix = new ColorMatrix(bakeMatrix(percent, frameTint));
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            new Canvas(out).drawBitmap(frame, 0f, 0f, paint);
            return out;
        } catch (Throwable t) {
            if (out != null) out.recycle();
            return null;
        } finally {
            frame.recycle();
        }
    }
}
