package com.termux.app.chrome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A frost crop cut with the parallax's spare width to its right, drawn shifted by the wallpaper's
 * live offset so the crop follows the wallpaper without ever being re-cut.
 *
 * <p>The dock, the keyboard and the top bars hold a crop of the shared pre-blurred frame in an
 * {@code ImageView} (or a background layer), and a whole chain of the chrome reasons about that
 * crop as a {@link BitmapDrawable}: the in-use scan that keeps the cache from recycling a bitmap
 * still on screen, the height check that stops a keyboard-era crop being stretched into the dock,
 * the crossfade that composites two same-sized crops on a wallpaper change. Keeping the crop a
 * {@code BitmapDrawable} keeps all of that true; this only widens what is cut and moves where it
 * is drawn. The intrinsic size is the visible window, not the bitmap, so an {@code ImageView}
 * fitting the drawable to its bounds scales the window and never squashes the spare into it.</p>
 *
 * <p>A crop cut with no spare and drawn at offset 0 paints exactly as a plain
 * {@code BitmapDrawable} would, which is what every surface still gets while nothing pans.</p>
 */
public final class ParallaxFrostDrawable extends BitmapDrawable {

    @NonNull private final WallpaperParallax mParallax;
    /** The crop's width less the spare cut beyond it: what a surface at rest shows. */
    private final int mWindowWidth;

    /**
     * @param windowWidth the width of the crop that is the surface's own; the rest of the bitmap,
     *                    to its right, is the spare the offset pans across
     */
    public ParallaxFrostDrawable(@NonNull Resources resources, @NonNull Bitmap crop, int windowWidth,
                                 @NonNull WallpaperParallax parallax) {
        super(resources, crop);
        mParallax = parallax;
        mWindowWidth = Math.max(1, Math.min(windowWidth, crop.getWidth()));
    }

    /** The parallax this crop follows, for a swap that has to keep following the same one. */
    @NonNull
    public WallpaperParallax parallax() {
        return mParallax;
    }

    public int windowWidth() {
        return mWindowWidth;
    }

    @Override
    public int getIntrinsicWidth() {
        return mWindowWidth;
    }

    @Override
    public int getMinimumWidth() {
        return mWindowWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap bitmap = getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        // Whoever set the bounds meant them for the window: an ImageView fits the intrinsic size to
        // its own, a layer takes its host's. The window is scaled into them; the spare beyond it
        // is drawn at the same scale and clipped away, and the offset slides both left together.
        float scaleX = bounds.width() / (float) mWindowWidth;
        float scaleY = bounds.height() / (float) Math.max(1, bitmap.getHeight());
        int save = canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.left - mParallax.offsetPx() * scaleX, bounds.top);
        canvas.scale(scaleX, scaleY);
        canvas.drawBitmap(bitmap, 0f, 0f, getPaint());
        canvas.restoreToCount(save);
    }

    /**
     * A crop cut for the whole of the parallax's travel: {@code targetRect} widened by
     * {@code sparePx} to the right, in the frame's own coordinates at offset 0, so that every
     * offset in the travel finds its pixels inside the one bitmap.
     */
    @NonNull
    public static Rect overscan(@NonNull Rect targetRect, int sparePx) {
        Rect wide = new Rect(targetRect);
        wide.right += Math.max(0, sparePx);
        return wide;
    }

    /** The bitmap a drawable of either kind holds, or null. */
    @Nullable
    public static Bitmap bitmapOf(@Nullable android.graphics.drawable.Drawable drawable) {
        return drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
    }
}
