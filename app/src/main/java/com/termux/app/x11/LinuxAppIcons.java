package com.termux.app.x11;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Where a Linux app's icon lives in the prefix, and how it is loaded small enough to sit in the
 * launcher's budgeted icon store. Theme lookup is the freedesktop one cut to what matters: the
 * hicolor theme's PNG sizes, largest useful first, then the pixmaps directory. SVG is not
 * rendered — the drawer falls back to its generic mark for those.
 */
public final class LinuxAppIcons {

    /** The most any app icon is decoded to: the drawer never draws one larger. */
    static final int MAX_EDGE_PX = 192;

    private static final String[] HICOLOR_SIZES = {
        "256x256", "192x192", "128x128", "96x96", "72x72", "64x64", "48x48", "32x32", "scalable"
    };

    private LinuxAppIcons() {}

    /**
     * The icon file for an {@code Icon=} value under {@code prefix}, or null. Absolute paths are
     * taken as they are; names are looked up in hicolor and pixmaps, PNG only.
     */
    @Nullable
    public static File find(@NonNull String iconName, @NonNull File prefix) {
        if (iconName.isEmpty()) return null;
        if (iconName.startsWith("/")) {
            File file = new File(iconName);
            return file.isFile() && isPng(file) ? file : null;
        }
        String name = iconName.endsWith(".png") ? iconName.substring(0, iconName.length() - 4) : iconName;
        for (String size : HICOLOR_SIZES) {
            File file = new File(prefix, "share/icons/hicolor/" + size + "/apps/" + name + ".png");
            if (file.isFile()) return file;
        }
        File pixmap = new File(prefix, "share/pixmaps/" + name + ".png");
        return pixmap.isFile() ? pixmap : null;
    }

    private static boolean isPng(@NonNull File file) {
        return file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png");
    }

    /** Decode {@code file} to at most {@link #MAX_EDGE_PX} on a side; null when it will not decode. */
    @Nullable
    public static Drawable load(@NonNull Resources resources, @NonNull File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int edge = Math.max(bounds.outWidth, bounds.outHeight);
        while (edge / options.inSampleSize > MAX_EDGE_PX) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        return bitmap == null ? null : new BitmapDrawable(resources, bitmap);
    }

    /** The prefix icons are searched under, for production callers. */
    @NonNull
    public static File prefix() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
    }
}
