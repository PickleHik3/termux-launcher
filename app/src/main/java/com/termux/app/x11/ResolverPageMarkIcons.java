package com.termux.app.x11;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.pageswitch.PageMarkIcons;
import com.termux.app.wayland.LinuxWindowIcons;

import java.util.function.Supplier;

/**
 * {@link PageMarkIcons} over what the window chips already use: the icon resolver (backed by the
 * bounded launcher icon store) and the bundled symbols face. It keeps no bitmaps of its own; the
 * drawable it hands out is a thin wrapper over the resolver's bitmap, so a view asks once per
 * model it sets, not per frame.
 *
 * <p>A key is a Linux window's ({@link LinuxWindowIcons#KEY_PREFIX}: it names the window and
 * changes with its icon) or a bound Display page's app id. Both answer in the app's own
 * colours, not the single-colour silhouette a window chip wears. A window's key is answered by the
 * window icon owner ({@link WindowIcons}, {@code LinuxWindowsController#windowIcon}), the same
 * method the window chip asks, so the chip and the mark never disagree.
 */
public final class ResolverPageMarkIcons implements PageMarkIcons {

    @NonNull private final Resources mResources;
    @NonNull private final Supplier<X11WindowIconResolver> mResolver;
    @NonNull private final Supplier<Typeface> mTypeface;
    @Nullable private final WindowIcons mWindowIcons;

    /** The one owner of a Linux window's icon. */
    public interface WindowIcons {
        /** Linux window {@code id}'s icon in its own colours (run-time, else its desktop entry's); null for none. */
        @Nullable Bitmap windowIcon(long id);
    }

    public ResolverPageMarkIcons(@NonNull Resources resources,
                                 @NonNull Supplier<X11WindowIconResolver> resolver,
                                 @NonNull Supplier<Typeface> typeface) {
        this(resources, resolver, typeface, null);
    }

    public ResolverPageMarkIcons(@NonNull Resources resources,
                                 @NonNull Supplier<X11WindowIconResolver> resolver,
                                 @NonNull Supplier<Typeface> typeface,
                                 @Nullable WindowIcons windowIcons) {
        mResources = resources;
        mResolver = resolver;
        mTypeface = typeface;
        mWindowIcons = windowIcons;
    }

    @Nullable @Override public Drawable icon(@NonNull String iconKey) {
        long window = LinuxWindowIcons.windowOfKey(iconKey);
        Bitmap bitmap;
        if (window != 0L) {
            bitmap = mWindowIcons == null ? null : mWindowIcons.windowIcon(window);
        } else {
            X11WindowIconResolver resolver = mResolver.get();
            bitmap = resolver == null ? null : resolver.colourFor(iconKey);
        }
        return bitmap == null ? null : new BitmapDrawable(mResources, bitmap);
    }

    @Nullable @Override public Typeface glyphTypeface() {
        return mTypeface.get();
    }
}
