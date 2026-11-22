package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The still route's picture: a {@link SharedFrameDrawable} over a frame of the blur cache. */
public final class StoredPicture implements GlassPicture {

    @NonNull private final SharedFrameDrawable mDrawable;

    public StoredPicture(@NonNull Bitmap frame, @NonNull Rect frameRect,
                         @Nullable WallpaperParallax parallax, @NonNull GlassAnchor.Origin anchor) {
        mDrawable = new SharedFrameDrawable(frame, frameRect, parallax, anchor);
    }

    @NonNull
    @Override
    public Drawable drawable() {
        return mDrawable;
    }

    @Override
    public void setFrame(@NonNull Bitmap frame, @NonNull Rect frameRect, boolean crossfade) {
        mDrawable.setFrame(frame, frameRect, crossfade);
    }
}
