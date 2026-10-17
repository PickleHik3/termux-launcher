package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * Vibrancy: the saturation of the blurred frame every glass surface samples. The Tint amount's
 * frame tint ({@link GlassFrameTint}) is baked in the same pass, after it.
 * It is baked into each frame once, when {@link WallpaperBlurCache} blurs it, so it costs nothing
 * per frame; a change of it re-bakes the frames as a wallpaper change does. Radius 0, the
 * wallpaper as the wall shows it, never takes it: the bare wallpaper is the user's picture.
 *
 * <p>It brightens what the gathered light then multiplies, and that light is still held under
 * the headroom the brightened sample has left ({@link GlassRefraction#heldK}), so a vivid frame's
 * edge cannot clip either. The ink check samples the same baked frame, so what it measures is
 * what the glass shows.</p>
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
     * {@code frame} at {@code percent}'s saturation and then {@code frameTint}'s hue shift
     * ({@link GlassFrameTint}), in one pass, as a new bitmap; {@code frame} is recycled. Null when
     * the copy cannot be made, which the cache treats as a frame that failed to blur.
     */
    @Nullable
    static Bitmap baked(@NonNull Bitmap frame, int percent, int frameTint) {
        Bitmap out = null;
        try {
            out = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(saturation(percent));
            if (frameTint != GlassFrameTint.NONE)
                matrix.postConcat(new ColorMatrix(GlassFrameTint.matrix(frameTint)));
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
