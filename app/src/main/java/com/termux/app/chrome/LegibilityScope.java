package com.termux.app.chrome;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * The one owner of "which rectangle is measured" for legibility (#297). By default every glass
 * item reads the one measurement of the whole wallpaper picture; with Adaptive legibility on, each
 * item reads the wallpaper behind itself. Pure: views and caches apply the answer.
 */
public final class LegibilityScope {

    /** The grid the whole picture is read on: it spans far more pixels than one item does. */
    public static final int WHOLE_PICTURE_GRID = 32;

    private LegibilityScope() {}

    /**
     * The rect to read: {@code itemRect} when adaptive, else {@code wholePicture}. An empty whole
     * picture means nothing can be read yet; it is returned as it is, empty.
     */
    @NonNull
    public static Rect measuredRect(boolean adaptive, @NonNull Rect itemRect,
                                    @NonNull Rect wholePicture) {
        return adaptive ? itemRect : wholePicture;
    }

    /**
     * Maps {@code rootRect} (root space, the space {@code frameRect} was captured in) into a
     * bitmap of {@code bitmapWidth} x {@code bitmapHeight} that shows all of {@code frameRect}.
     * The caller has checked {@code frameRect} is not empty.
     */
    public static void frameRegion(@NonNull Rect rootRect, @NonNull Rect frameRect,
                                   int bitmapWidth, int bitmapHeight, @NonNull Rect out) {
        float scaleX = bitmapWidth / (float) frameRect.width();
        float scaleY = bitmapHeight / (float) frameRect.height();
        out.set(
            Math.round((rootRect.left - frameRect.left) * scaleX),
            Math.round((rootRect.top - frameRect.top) * scaleY),
            Math.round((rootRect.right - frameRect.left) * scaleX),
            Math.round((rootRect.bottom - frameRect.top) * scaleY));
    }

    /** The read grid for {@code rootRect}: {@link #WHOLE_PICTURE_GRID} for the whole picture. */
    @VisibleForTesting
    public static int gridFor(@NonNull Rect rootRect, @NonNull Rect wholePicture) {
        return rootRect.equals(wholePicture) ? WHOLE_PICTURE_GRID
            : GlassBackdropCache.SAMPLE_GRID;
    }
}
