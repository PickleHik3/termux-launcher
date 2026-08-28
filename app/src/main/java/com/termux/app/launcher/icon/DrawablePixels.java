package com.termux.app.launcher.icon;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pixel accounting for held drawables: how big the bitmaps inside one really are, what they cost,
 * and rasterising one down when it is larger than anything will ever draw it. Shared by every
 * bounded store that keeps artwork, so they all measure the same way.
 */
public final class DrawablePixels {

    /** Deep enough for an adaptive icon of wrapped layer lists; a guard against a cyclic drawable. */
    private static final int MAX_CONTAINER_DEPTH = 6;

    private DrawablePixels() {
    }

    /**
     * Rasterise artwork that is both larger than {@code maxExtentPx} and actually made of pixels.
     * A vector costs a few kilobytes and scales for free, so flattening one to a bitmap would spend
     * memory rather than save it; only a drawable already carrying bitmaps is worth redrawing.
     */
    @Nullable
    public static Drawable shrink(@NonNull Resources resources, @Nullable Drawable source,
                                  int maxExtentPx) {
        if (source == null) return null;
        // How big the pixels actually are, which is the only thing that costs anything. A drawable
        // is asked what size it is, but not believed on its own: an adaptive icon declares the
        // nominal 72dp while holding the 108dp rasterisation, so trusting the declaration keeps
        // 323 KB per app in order to draw 143 KB of it. A container that will not answer at all is
        // measured entirely by what it holds.
        int largestBitmap = largestBitmapExtent(source, 0);
        if (largestBitmap <= 0) return source;
        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) width = height = largestBitmap;
        int longestEdge = Math.max(width, height);
        int targetEdge = Math.min(maxExtentPx, longestEdge);
        // Nothing to gain once the pixels are no larger than the size they would be redrawn at.
        if (largestBitmap <= targetEdge) return source;
        try {
            float scale = targetEdge / (float) longestEdge;
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));
            Bitmap flattened = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Rect previousBounds = new Rect(source.getBounds());
            source.setBounds(0, 0, targetWidth, targetHeight);
            source.draw(new Canvas(flattened));
            source.setBounds(previousBounds);
            return new BitmapDrawable(resources, flattened);
        } catch (RuntimeException | OutOfMemoryError e) {
            // Keeping the original costs memory; failing to produce artwork costs the user an icon.
            return source;
        }
    }

    /**
     * Byte cost of one held drawable. Anything without a bitmap still costs 1, so occupancy tracks
     * entries and evicting everything returns to zero.
     */
    public static int heldBytes(@Nullable Drawable value) {
        if (!(value instanceof BitmapDrawable)) return 1;
        Bitmap bitmap = ((BitmapDrawable) value).getBitmap();
        if (bitmap == null) return 1;
        long bytes = bitmap.getAllocationByteCount();
        if (bytes < 1L) return 1;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /**
     * The largest bitmap edge anywhere inside this drawable, or 0 when it holds none.
     *
     * <p>A container is worth whatever it contains, and launcher artwork is almost never a bare
     * bitmap: the framework hands back adaptive icons whose layers are themselves wrapped in
     * insets, scales and rotations, and layer lists of the same — and some of those containers
     * report no intrinsic size at all. Looking only one level down, or believing a -1, keeps every
     * one of them whole, which is the entire cost this class exists to remove. So the walk goes all
     * the way down.</p>
     */
    private static int largestBitmapExtent(@Nullable Drawable drawable, int depth) {
        if (drawable == null || depth > MAX_CONTAINER_DEPTH) return 0;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            return bitmap == null ? 0 : Math.max(bitmap.getWidth(), bitmap.getHeight());
        }
        // Adaptive icons are checked before layer lists: they are not one, and they expose their
        // two layers by name rather than by index.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            return Math.max(largestBitmapExtent(adaptive.getBackground(), depth + 1),
                largestBitmapExtent(adaptive.getForeground(), depth + 1));
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            int largest = 0;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                largest = Math.max(largest, largestBitmapExtent(layers.getDrawable(i), depth + 1));
            }
            return largest;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && drawable instanceof DrawableWrapper) {
            return largestBitmapExtent(((DrawableWrapper) drawable).getDrawable(), depth + 1);
        }
        return 0;
    }
}
