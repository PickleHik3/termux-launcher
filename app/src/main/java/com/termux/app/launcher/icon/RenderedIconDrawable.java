package com.termux.app.launcher.icon;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;

/**
 * Display bitmap plus the clean, pre-shadow artwork used for focus contour extraction.
 *
 * <p>Produced by {@link DockIconCache}; the clean artwork is retained so focus outlines and drag
 * silhouettes can be derived from the icon alpha without the contact shadow skewing the contour.
 */
public final class RenderedIconDrawable extends BitmapDrawable {
    @NonNull public final Bitmap cleanArtwork;

    RenderedIconDrawable(@NonNull Resources resources, @NonNull Bitmap display,
                         @NonNull Bitmap cleanArtwork) {
        super(resources, display);
        this.cleanArtwork = cleanArtwork;
    }
}
