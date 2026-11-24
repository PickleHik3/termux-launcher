package com.termux.app.surfaces;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The editor bar's wallpaper button picture: the wallpaper drawn straight into a small square at
 * the button's own pixel size, centre-cropped and round-cornered. Nothing of the full-size picture
 * is kept; the bitmap is a few KB. Called off the main thread.
 */
final class WallpaperThumb {

    private WallpaperThumb() {}

    /** A {@code sizePx} square of {@code picture}, or null when it cannot be drawn. */
    @Nullable
    static Bitmap render(@NonNull Drawable picture, int sizePx) {
        if (sizePx <= 0)
            return null;
        try {
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Path clip = new Path();
            float radius = sizePx * 0.28f;
            clip.addRoundRect(0, 0, sizePx, sizePx, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            Rect old = picture.copyBounds();
            int[] b = EditorLabelInk.cropBounds(picture.getIntrinsicWidth(),
                picture.getIntrinsicHeight(), sizePx, sizePx);
            picture.setBounds(b[0], b[1], b[2], b[3]);
            picture.draw(canvas);
            picture.setBounds(old);
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
