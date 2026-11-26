package com.termux.app.wall.pageswitch;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.X11WindowIconResolver;

import java.util.function.Supplier;

/**
 * {@link PageMarkIcons} over what the window chips already use: the icon resolver (backed by the
 * bounded launcher icon store) and the bundled symbols face. It keeps no bitmaps of its own; the
 * drawable it hands out is a thin wrapper over the resolver's bitmap, so a view asks once per
 * model it sets, not per frame.
 */
public final class ResolverPageMarkIcons implements PageMarkIcons {

    @NonNull private final Resources mResources;
    @NonNull private final Supplier<X11WindowIconResolver> mResolver;
    @NonNull private final Supplier<Typeface> mTypeface;

    public ResolverPageMarkIcons(@NonNull Resources resources,
                                 @NonNull Supplier<X11WindowIconResolver> resolver,
                                 @NonNull Supplier<Typeface> typeface) {
        mResources = resources;
        mResolver = resolver;
        mTypeface = typeface;
    }

    @Nullable @Override public Drawable icon(@NonNull String iconKey) {
        X11WindowIconResolver resolver = mResolver.get();
        Bitmap bitmap = resolver == null ? null : resolver.iconFor(iconKey);
        return bitmap == null ? null : new BitmapDrawable(mResources, bitmap);
    }

    @Nullable @Override public Typeface glyphTypeface() {
        return mTypeface.get();
    }
}
