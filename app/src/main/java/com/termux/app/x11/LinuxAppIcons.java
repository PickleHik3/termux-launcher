package com.termux.app.x11;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Where a Linux app's icon lives — in the prefix or inside a distro container — and how it is
 * loaded small enough to sit in the launcher's budgeted icon store. Theme lookup is the
 * freedesktop one cut to what matters: the hicolor theme's PNG sizes, largest useful first, then
 * {@code scalable}'s SVG, then the pixmaps directory — a real raster size always wins over a
 * rendered one, since a hand-made 256px PNG beats a vector redrawn at the same budget.
 */
public final class LinuxAppIcons {

    /** The most any app icon is decoded to: the drawer never draws one larger. */
    static final int MAX_EDGE_PX = 192;

    private static final String[] HICOLOR_SIZES = {
        "256x256", "192x192", "128x128", "96x96", "72x72", "64x64", "48x48", "32x32", "scalable"
    };

    /** Checked in this order at every candidate location: a PNG at a size beats an SVG at it. */
    private static final String[] ICON_EXTENSIONS = {".png", ".svg"};

    private LinuxAppIcons() {}

    /**
     * The icon file for an app, wherever the app lives. A container names its paths in its own
     * world and keeps its icons under its {@code /usr}, so both are resolved against its rootfs —
     * which is inside the launcher's own data directory, and so an ordinary readable file.
     */
    @Nullable
    public static File find(@NonNull LinuxAppCatalog.LinuxApp app) {
        return find(app.icon, app.container);
    }

    /** The icon file for an {@code Icon=} value belonging to {@code container}, or null. */
    @Nullable
    public static File find(@NonNull String iconName, @NonNull ProotDistro.Container container) {
        if (!iconName.isEmpty() && iconName.startsWith("/")) {
            File file = container.inside(iconName);
            return file.isFile() && isSupportedIcon(file) ? file : null;
        }
        return find(iconName, container.iconPrefix());
    }

    /**
     * The icon file for an {@code Icon=} value under {@code prefix}, or null. Absolute paths are
     * taken as they are; names are looked up in hicolor (PNG sizes, then the scalable SVG) and
     * pixmaps.
     */
    @Nullable
    public static File find(@NonNull String iconName, @NonNull File prefix) {
        if (iconName.isEmpty()) return null;
        if (iconName.startsWith("/")) {
            File file = new File(iconName);
            return file.isFile() && isSupportedIcon(file) ? file : null;
        }
        String name = stripKnownExtension(iconName);
        for (String size : HICOLOR_SIZES) {
            for (String ext : ICON_EXTENSIONS) {
                File file = new File(prefix, "share/icons/hicolor/" + size + "/apps/" + name + ext);
                if (file.isFile()) return file;
            }
        }
        for (String ext : ICON_EXTENSIONS) {
            File pixmap = new File(prefix, "share/pixmaps/" + name + ext);
            if (pixmap.isFile()) return pixmap;
        }
        return null;
    }

    @NonNull
    private static String stripKnownExtension(@NonNull String iconName) {
        for (String ext : ICON_EXTENSIONS) {
            if (iconName.endsWith(ext)) return iconName.substring(0, iconName.length() - ext.length());
        }
        return iconName;
    }

    private static boolean isSupportedIcon(@NonNull File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".svg");
    }

    private static boolean isSvg(@NonNull File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    /**
     * Decode or render {@code file} to at most {@link #MAX_EDGE_PX} on a side; null when it will
     * not decode/render. Never throws — a malformed icon shipped by a distro app must not take the
     * drawer down.
     */
    @Nullable
    public static Drawable load(@NonNull Resources resources, @NonNull File file) {
        return isSvg(file) ? loadSvg(resources, file) : loadPng(resources, file);
    }

    @Nullable
    private static Drawable loadPng(@NonNull Resources resources, @NonNull File file) {
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

    @Nullable
    private static Drawable loadSvg(@NonNull Resources resources, @NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            SVG svg = SVG.getFromInputStream(in);
            float[] size = documentSizePx(svg);
            float scale = MAX_EDGE_PX / Math.max(size[0], size[1]);
            int width = Math.max(1, Math.round(size[0] * scale));
            int height = Math.max(1, Math.round(size[1] * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new Canvas(bitmap));
            return new BitmapDrawable(resources, bitmap);
        } catch (SVGParseException | IOException | RuntimeException | OutOfMemoryError e) {
            // A malformed or unsupported SVG (missing viewBox, unknown element, a truncated
            // file) must fall back to null exactly like an undecodable PNG does, not throw.
            return null;
        }
    }

    /** The SVG's own width/height in pixels, falling back to its viewBox, then a square budget. */
    @NonNull
    private static float[] documentSizePx(@NonNull SVG svg) {
        float width = svg.getDocumentWidth();
        float height = svg.getDocumentHeight();
        if (width > 0 && height > 0) return new float[] {width, height};
        RectF viewBox = svg.getDocumentViewBox();
        if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
            return new float[] {viewBox.width(), viewBox.height()};
        }
        return new float[] {MAX_EDGE_PX, MAX_EDGE_PX};
    }
}
