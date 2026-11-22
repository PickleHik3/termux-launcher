package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The picture a glass surface's backdrop shows: what the effect on that view bends and blurs.
 * Today it is always {@link StoredPicture}, the shared pre-blurred wallpaper frame; the seam is
 * here so a live picture can stand in without the effect builder ({@link GlassEffects}) or the
 * install path knowing which one it has.
 */
public interface GlassPicture {

    /** The drawable the backdrop view holds. The same instance for the picture's whole life. */
    @NonNull
    Drawable drawable();

    /**
     * Points the picture at another stored frame, fading from the one it shows when
     * {@code crossfade} and the frame was captured for the same rect.
     */
    void setFrame(@NonNull Bitmap frame, @NonNull Rect frameRect, boolean crossfade);
}
