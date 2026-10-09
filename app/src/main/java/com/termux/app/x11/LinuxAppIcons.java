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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a Linux app's icon lives — in the prefix or inside a distro container — and how it is
 * loaded small enough to sit in the launcher's budgeted icon store. Theme lookup is the
 * freedesktop one cut to what matters: the hicolor theme's PNG sizes, largest useful first, then
 * {@code scalable}'s SVG, then the pixmaps directory — a real raster size always wins over a
 * rendered one, since a hand-made 256px PNG beats a vector redrawn at the same budget.
 *
 * <p>A whole desktop is the one entry that usually names no picture at all, so it gets a second
 * try at the desktop environment's own application icon; see {@link #DESKTOP_ICON_NAMES}.
 */
public final class LinuxAppIcons {

    /** The most any app icon is decoded to: the drawer never draws one larger. */
    static final int MAX_EDGE_PX = 192;

    private static final String[] HICOLOR_SIZES = {
        "256x256", "192x192", "128x128", "96x96", "72x72", "64x64", "48x48", "32x32", "scalable"
    };

    /**
     * The icon themes searched, in order. {@code locolor} is the small-screen theme a few old X
     * programs still ship instead of a hicolor one, and nixpkgs carries them as they are.
     */
    private static final String[] THEMES = {"hicolor", "locolor"};

    /** Checked in this order at every candidate location: a PNG at a size beats an SVG at it. */
    private static final String[] ICON_EXTENSIONS = {".png", ".svg"};

    /**
     * The name a desktop environment's own application icon is installed under, for a whole
     * desktop whose session file names no icon that resolves — which is most of them: of the
     * nineteen session files in Termux's x11 repository nine carry no {@code Icon} key at all and
     * four more carry an empty one.
     *
     * <p>The key is matched against the session's {@code DesktopNames} value and against its
     * desktop-file name, both lower-cased; the two agree for every desktop here. Each name was
     * read out of a real package's file list at a path this class already searches —
     * {@code xfce4-logo} from Termux's {@code libxfce4ui} and Debian's {@code libxfce4ui-utils},
     * {@code lxqt} from {@code lxqt-themes} and {@code lxqt-system-theme}, {@code mate-desktop}
     * from {@code mate-desktop} and {@code mate-desktop-common}, {@code openbox} from openbox's
     * own {@code share/pixmaps}, {@code cinnamon} from {@code cinnamon}. A desktop that ships no
     * such icon — i3, IceWM, Plasma, awesome, bspwm, herbstluftwm, Window Maker — finds nothing
     * here and keeps the drawer's own stand-in rather than a name that resolves nowhere.
     */
    private static final Map<String, String> DESKTOP_ICON_NAMES = buildDesktopIconNames();

    @NonNull
    private static Map<String, String> buildDesktopIconNames() {
        Map<String, String> names = new HashMap<>();
        names.put("xfce", "xfce4-logo");
        names.put("lxqt", "lxqt");
        names.put("mate", "mate-desktop");
        names.put("openbox", "openbox");
        names.put("cinnamon", "cinnamon");
        names.put("cinnamon2d", "cinnamon");
        names.put("x-cinnamon", "cinnamon");
        return Collections.unmodifiableMap(names);
    }

    private LinuxAppIcons() {}

    /**
     * The icon file for an app, wherever the app lives. A container names its paths in its own
     * world and keeps its icons under its {@code /usr}, so both are resolved against its rootfs —
     * which is inside the launcher's own data directory, and so an ordinary readable file.
     *
     * <p>A whole desktop ({@link LinuxAppCatalog.LinuxApp#session}) gets one more try: its own
     * {@code Icon} first, exactly like an app, and then the desktop's own application icon by name
     * ({@link #DESKTOP_ICON_NAMES}), because a session file that names a picture is the exception.
     * An application's lookup is untouched — nothing that resolves today stops resolving, and
     * nothing that fails today starts succeeding.
     */
    @Nullable
    public static File find(@NonNull LinuxAppCatalog.LinuxApp app) {
        File own = find(app.icon, app.container);
        if (own != null || !app.session) return own;
        for (String name : desktopIconNames(app)) {
            File file = find(name, app.container);
            if (file != null) return file;
        }
        return null;
    }

    /**
     * The application-icon names to try for a whole desktop, most specific first: what the session
     * calls itself in {@code DesktopNames}, then what its file is called. Empty for an application,
     * and for a desktop no package here ships an icon for.
     */
    @NonNull
    static List<String> desktopIconNames(@NonNull LinuxAppCatalog.LinuxApp app) {
        if (!app.session) return Collections.emptyList();
        List<String> keys = new ArrayList<>(3);
        for (String desktopName : app.desktopNames.split(";")) keys.add(desktopName);
        keys.add(app.desktopFile);
        List<String> names = new ArrayList<>(2);
        for (String key : keys) {
            String name = DESKTOP_ICON_NAMES.get(key.trim().toLowerCase(Locale.ROOT));
            if (name != null && !names.contains(name)) names.add(name);
        }
        return names;
    }

    /** The icon file for an {@code Icon=} value belonging to {@code container}, or null. */
    @Nullable
    public static File find(@NonNull String iconName, @NonNull ProotDistro.Container container) {
        if (!iconName.isEmpty() && iconName.startsWith("/")) {
            File file = container.inside(iconName);
            return file.isFile() && isSupportedIcon(file) ? file : null;
        }
        return find(iconName, container.iconPrefix(), container);
    }

    /**
     * The icon file for an {@code Icon=} value under {@code prefix}, or null. Absolute paths are
     * taken as they are; names are looked up in hicolor (PNG sizes, then the scalable SVG) and
     * pixmaps.
     */
    @Nullable
    public static File find(@NonNull String iconName, @NonNull File prefix) {
        return find(iconName, prefix, null);
    }

    /**
     * The same search, walked the way {@code container} needs its paths walked — plain joining
     * everywhere but a nix profile, where any component of {@code share/icons/…} can itself be a
     * link into the store and has to be followed before the next one means anything.
     */
    @Nullable
    private static File find(@NonNull String iconName, @NonNull File prefix,
                             @Nullable ProotDistro.Container container) {
        if (iconName.isEmpty()) return null;
        if (iconName.startsWith("/")) {
            File file = container == null ? new File(iconName) : container.inside(iconName);
            return file.isFile() && isSupportedIcon(file) ? file : null;
        }
        String name = stripKnownExtension(iconName);
        for (String theme : THEMES) {
            for (String size : HICOLOR_SIZES) {
                for (String ext : ICON_EXTENSIONS) {
                    File file = under(container, prefix,
                        "share/icons/" + theme + "/" + size + "/apps/" + name + ext);
                    if (file.isFile()) return file;
                }
            }
        }
        for (String ext : ICON_EXTENSIONS) {
            File pixmap = under(container, prefix, "share/pixmaps/" + name + ext);
            if (pixmap.isFile()) return pixmap;
        }
        return null;
    }

    @NonNull
    private static File under(@Nullable ProotDistro.Container container, @NonNull File base,
                              @NonNull String relative) {
        return container == null ? new File(base, relative) : container.under(base, relative);
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
