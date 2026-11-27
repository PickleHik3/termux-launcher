package com.termux.app.x11;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.launcher.model.AppRef;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app icon and name the display window's chip wears: the window's {@code WM_CLASS} traced back
 * to an installed app's desktop file, its {@code Name} read as the drawer lists it, its PNG loaded
 * from the prefix or from the distro container the app lives in, and the artwork reduced to a
 * single-colour silhouette small enough to sit behind a chip's label.
 *
 * <p>A window says which app drew it through the class part of its {@code WM_CLASS}; a desktop
 * file says which class its app will set through {@code StartupWMClass}, and where it does not,
 * its own name or the binary it runs is the same word in practice. So a class is matched against,
 * in order, the desktop-file name, {@code StartupWMClass}, the {@code Exec} basename and the last
 * segment of a reverse-DNS desktop-file name — all case-insensitively, because the two sides
 * disagree about capitals constantly (Firefox sets {@code firefox}, {@code Navigator} sets
 * {@code Firefox}).
 *
 * <p>Matching and the silhouette are pure and tested; the file reading and decoding they need
 * happen on a background thread, and a resolved icon and name reach the bar through a callback on
 * the main thread. Results are cached per class — misses included, so a window from something that is
 * not installed is not looked up again every time the bar syncs.
 */
public final class X11WindowIconResolver {

    /**
     * The silhouette's edge, in pixels. A chip draws it around 16 dp, so this covers the densest
     * phone without scaling up, and one costs 16 KB — the cache below is what keeps that bounded.
     */
    public static final int SILHOUETTE_PX = 64;

    /**
     * How much of a fully transparent-to-opaque pixel's own coverage survives at zero luminance.
     * Luminance alone would erase a dark icon on a dark chip, which is the common case for the
     * terminal's palettes; keeping a floor means the artwork's shape always reads and its bright
     * parts read stronger.
     */
    @VisibleForTesting static final float SHAPE_FLOOR = 0.45f;

    /** How many silhouettes are kept. A display with more open apps than this re-resolves. */
    private static final int MAX_CACHED = 32;

    /**
     * Told, on the main thread, what a class resolved to: its app's icon and name, either null
     * when the class matches no installed app or the app has no icon.
     */
    public interface Callback {
        void onWindowAppResolved(@NonNull String wmClass, @Nullable Bitmap icon,
                                 @Nullable String name);
    }

    /**
     * Where an installed Linux app's icon in its own colours lives: the launcher's bounded icon
     * store, which the drawer draws the same app from. Called off the main thread to warm it and
     * on the main thread to read; a miss is null.
     */
    public interface ColourIcons {
        @Nullable Drawable of(@NonNull AppRef ref);
    }

    /**
     * One class's answer: the matched app's silhouette and name, either possibly missing, and for
     * an app whose icon file exists the catalogue ref its colour icon is read from the store by
     * ({@code ref}), or for a theme name the colour pixels themselves, at most {@link #COLOUR_PX}.
     */
    private static final class Resolved {
        @Nullable final Bitmap icon;
        @Nullable final String name;
        @Nullable final AppRef ref;
        @Nullable final Bitmap colour;
        /** The matched app's id, the key of its per-app settings; null for a theme name or none. */
        @Nullable final String appId;

        Resolved(@Nullable Bitmap icon, @Nullable String name) {
            this(icon, name, null, null, null);
        }

        Resolved(@Nullable Bitmap icon, @Nullable String name, @Nullable AppRef ref, @Nullable Bitmap colour) {
            this(icon, name, ref, colour, null);
        }

        Resolved(@Nullable Bitmap icon, @Nullable String name, @Nullable AppRef ref,
                 @Nullable Bitmap colour, @Nullable String appId) {
            this.appId = appId;
            this.icon = icon;
            this.name = name;
            this.ref = ref;
            this.colour = colour;
        }
    }

    private static final Resolved NOTHING = new Resolved(null, null);

    /** The most a theme-named icon keeps in colour on a side (36 KiB). */
    private static final int COLOUR_PX = 96;
    /** How many theme-named icons are kept, each at most a silhouette and a colour icon (52 KiB). */
    private static final int MAX_NAMED = 8;
    @Nullable private volatile ColourIcons colourIcons;

    @NonNull private final Resources resources;
    @NonNull private final Callback callback;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** Class (lower-cased) to what it resolved to; {@link #NOTHING} for a class matching no app. */
    @NonNull private final Map<String, Resolved> cache = new HashMap<>();
    @NonNull private final Set<String> pending = new HashSet<>();

    public X11WindowIconResolver(@NonNull Resources resources, @NonNull Callback callback) {
        this.resources = resources;
        this.callback = callback;
    }

    /**
     * The silhouette for {@code wmClass} when it is already known, else null — and, the first time
     * a class is asked about, a background resolution whose result reaches {@link Callback}. Call
     * it from the main thread while building the bar's items.
     */
    @Nullable
    public Bitmap iconFor(@NonNull String wmClass) {
        Resolved resolved = resolved(wmClass);
        return resolved == null ? null : resolved.icon;
    }

    /** Where the colour icons of installed apps come from; see {@link ColourIcons}. */
    public void setColourIcons(@Nullable ColourIcons colourIcons) {
        this.colourIcons = colourIcons;
    }

    /**
     * {@link #iconFor} in the app's own colours, for the page marks: read from the launcher's icon
     * store, which the drawer draws the app from; null while unknown, for an app with no icon file,
     * or when the store has none.
     */
    @Nullable
    public Bitmap colourFor(@NonNull String wmClass) {
        Resolved resolved = resolved(wmClass);
        return resolved == null ? null : colourOf(resolved);
    }

    @Nullable
    private Bitmap colourOf(@NonNull Resolved resolved) {
        if (resolved.colour != null) return resolved.colour;
        ColourIcons source = colourIcons;
        if (resolved.ref == null || source == null) return null;
        try {
            Drawable drawable = source.of(resolved.ref);
            return drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The {@code Name} of the installed app {@code wmClass} belongs to when it is already known,
     * else null — resolved alongside the icon, so asking for either starts both.
     */
    @Nullable
    public String nameFor(@NonNull String wmClass) {
        Resolved resolved = resolved(wmClass);
        return resolved == null ? null : resolved.name;
    }

    /**
     * The id of the installed app {@code wmClass} belongs to when it is already known, else null
     * (and, the first time, a background resolution starts). The key of the app's own settings.
     */
    @Nullable
    public String appIdFor(@NonNull String wmClass) {
        Resolved resolved = resolved(wmClass);
        return resolved == null ? null : resolved.appId;
    }

    /**
     * The silhouette of the theme icon {@code name} (an app's {@code xdg_toplevel_icon} name) when
     * it is already known, else null, and, the first time, a background resolution whose result
     * reaches {@link Callback}. The name is searched in the prefix and every container, like an
     * entry's {@code Icon=}.
     */
    @Nullable
    public Bitmap iconNamed(@NonNull String name) {
        Resolved resolved = resolved(NAMED_PREFIX + name.trim(), () -> resolveNamed(name.trim()));
        return resolved == null ? null : resolved.icon;
    }

    /** Keys of {@link #iconNamed} start with a mark no window class has. */
    private static final String NAMED_PREFIX = "~icon:";

    /** {@link #iconNamed} in colour, at most {@link #COLOUR_PX} on a side; null while unknown. */
    @Nullable
    public Bitmap colourNamed(@NonNull String name) {
        Resolved resolved = resolved(NAMED_PREFIX + name.trim(), () -> resolveNamed(name.trim()));
        return resolved == null ? null : resolved.colour;
    }

    @NonNull
    private Resolved resolveNamed(@NonNull String name) {
        if (name.isEmpty()) return NOTHING;
        try {
            for (LinuxAppCatalog.Root root : LinuxAppCatalog.roots()) {
                File file = LinuxAppIcons.find(name, root.container);
                if (file == null) continue;
                Drawable drawable = LinuxAppIcons.load(resources, file);
                Bitmap source = drawable instanceof BitmapDrawable
                    ? ((BitmapDrawable) drawable).getBitmap() : null;
                if (source != null) {
                    return new Resolved(silhouette(source, SILHOUETTE_PX), null, null, fitColour(source));
                }
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            // Decoration: a missing or malformed icon is a chip without it.
        }
        return NOTHING;
    }

    /** {@code source} cut to {@link #COLOUR_PX} on its longer side. */
    @NonNull
    private static Bitmap fitColour(@NonNull Bitmap source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= COLOUR_PX) return source;
        float scale = COLOUR_PX / (float) longest;
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
            Math.max(1, Math.round(source.getHeight() * scale)), true);
    }

    @Nullable
    private Resolved resolved(@NonNull String wmClass) {
        return resolved(wmClass, () -> resolve(wmClass));
    }

    @Nullable
    private Resolved resolved(@NonNull String wmClass, @NonNull java.util.function.Supplier<Resolved> work) {
        String key = key(wmClass);
        if (key.isEmpty()) return null;
        Resolved known = cache.get(key);
        if (known != null) return known;
        if (!pending.add(key)) return null;
        worker.execute(() -> {
            Resolved resolved = work.get();
            main.post(() -> {
                pending.remove(key);
                if (cache.size() >= MAX_CACHED) cache.clear();
                if (key.startsWith(NAMED_PREFIX)) dropNamedIfFull();
                cache.put(key, resolved);
                callback.onWindowAppResolved(wmClass, resolved.icon, resolved.name);
            });
        });
        return null;
    }

    /** Theme-named icons hold colour pixels, so only {@link #MAX_NAMED} are kept. */
    private void dropNamedIfFull() {
        int named = 0;
        for (String k : cache.keySet()) if (k.startsWith(NAMED_PREFIX)) named++;
        if (named < MAX_NAMED) return;
        cache.keySet().removeIf(k -> k.startsWith(NAMED_PREFIX));
    }

    /** Forget every resolution, so a newly installed app's icon is picked up. */
    public void clear() {
        cache.clear();
    }

    /** Stop resolving; the resolver is unusable afterwards. */
    public void shutdown() {
        worker.shutdownNow();
    }

    /** The whole background half: catalogue scan, the app's name, icon file, decode, silhouette. */
    @NonNull
    private Resolved resolve(@NonNull String wmClass) {
        LinuxAppCatalog.LinuxApp app;
        try {
            app = match(LinuxAppCatalog.scan(LinuxAppCatalog.roots()), wmClass);
        } catch (RuntimeException e) {
            return NOTHING;
        }
        if (app == null) return NOTHING;
        String name = app.name.trim().isEmpty() ? null : app.name.trim();
        try {
            File file = LinuxAppIcons.find(app);
            if (file == null) return new Resolved(null, name, null, null, app.id);
            Drawable drawable = LinuxAppIcons.load(resources, file);
            Bitmap source = drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap() : null;
            // The colour icon is the drawer's: read through the launcher's icon store, warmed here
            // so the main thread finds it held.
            AppRef ref = source == null ? null : X11Apps.ref(app.id);
            ColourIcons colours = colourIcons;
            if (ref != null && colours != null) {
                try {
                    colours.of(ref);
                } catch (RuntimeException e) {
                    // Warming only: the main thread asks again.
                }
            }
            return new Resolved(source == null ? null : silhouette(source, SILHOUETTE_PX), name, ref, null, app.id);
        } catch (RuntimeException | OutOfMemoryError e) {
            // An icon is decoration: a malformed PNG or a tight moment must not take the bar down,
            // nor cost the chip the app's name.
            return new Resolved(null, name, null, null, app.id);
        }
    }

    /**
     * The installed app a window's class belongs to, or null. Keys are tried in order — the
     * desktop-file name, {@code StartupWMClass}, the {@code Exec} basename, the last segment of a
     * reverse-DNS desktop-file name — and every app is tested against a key before the next key is
     * tried, so an exact name match always beats another app's looser one.
     *
     * <p>The keys are the desktop file's own, not the container-qualified id: a window says
     * {@code firefox} whichever distro drew it. Where two containers both have that app, the first
     * in the list wins — the prefix, then containers by name — and the two icons are usually the
     * same artwork anyway.
     */
    @Nullable
    public static LinuxAppCatalog.LinuxApp match(
            @NonNull List<LinuxAppCatalog.LinuxApp> apps, @NonNull String wmClass) {
        String key = key(wmClass);
        if (key.isEmpty()) return null;
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(key(app.desktopFile))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(key(app.startupWmClass))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(execBasename(app.exec))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(tail(key(app.desktopFile)))) return app;
        }
        return null;
    }

    /**
     * The command an {@code Exec} line runs, without its path or arguments, lower-cased. An
     * interpreter prefix is not unwrapped: {@code env FOO=1 kate} matches nothing, which is
     * correct — the window will be {@code kate}'s and that desktop file will be found by name.
     */
    @NonNull
    @VisibleForTesting
    static String execBasename(@NonNull String exec) {
        String first = exec.trim();
        int space = first.indexOf(' ');
        if (space > 0) first = first.substring(0, space);
        int slash = first.lastIndexOf('/');
        if (slash >= 0) first = first.substring(slash + 1);
        return key(first);
    }

    /** The part of a reverse-DNS name that names the app: {@code org.kde.kate} is {@code kate}. */
    @NonNull
    private static String tail(@NonNull String id) {
        int dot = id.lastIndexOf('.');
        return dot >= 0 && dot + 1 < id.length() ? id.substring(dot + 1) : id;
    }

    @NonNull
    private static String key(@NonNull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code source} as a square silhouette {@code sizePx} on a side: white throughout, carrying
     * the artwork's shape and brightness in its alpha, for a caller that tints it. Scaling keeps
     * the aspect ratio and centres what it produces, so a wide icon is not stretched, and it
     * averages over the source area a destination pixel covers rather than picking one pixel —
     * an icon is being shrunk by three or four times here, and dropping pixels would drop the
     * thin strokes that make it recognisable.
     *
     * <p>Alpha is the pixel's own coverage scaled by its luminance, floored at
     * {@link #SHAPE_FLOOR} so dark artwork still reads as a shape rather than disappearing.
     *
     * <p>Pure arithmetic over pixel arrays, with no drawing: what it produces is exactly the same
     * on every device and in a test.
     */
    @NonNull
    public static Bitmap silhouette(@NonNull Bitmap source, int sizePx) {
        int size = Math.max(1, sizePx);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int width = source.getWidth(), height = source.getHeight();
        if (width <= 0 || height <= 0) return out;

        int[] src = new int[width * height];
        source.getPixels(src, 0, width, 0, 0, width, height);
        float scale = Math.min(size / (float) width, size / (float) height);
        int drawWidth = Math.min(size, Math.max(1, Math.round(width * scale)));
        int drawHeight = Math.min(size, Math.max(1, Math.round(height * scale)));
        int left = (size - drawWidth) / 2, top = (size - drawHeight) / 2;

        int[] pixels = new int[size * size];
        for (int y = 0; y < drawHeight; y++) {
            int sourceTop = y * height / drawHeight;
            int sourceBottom = Math.max(sourceTop + 1, (y + 1) * height / drawHeight);
            for (int x = 0; x < drawWidth; x++) {
                int sourceLeft = x * width / drawWidth;
                int sourceRight = Math.max(sourceLeft + 1, (x + 1) * width / drawWidth);
                int sum = 0, count = 0;
                for (int sy = sourceTop; sy < sourceBottom; sy++) {
                    for (int sx = sourceLeft; sx < sourceRight; sx++) {
                        sum += Color.alpha(whiteWithAlpha(src[sy * width + sx]));
                        count++;
                    }
                }
                int alpha = count == 0 ? 0 : sum / count;
                pixels[(top + y) * size + left + x] =
                    alpha == 0 ? Color.TRANSPARENT : Color.argb(alpha, 255, 255, 255);
            }
        }
        out.setPixels(pixels, 0, size, 0, 0, size, size);
        return out;
    }

    /** One pixel's contribution to the silhouette: white, alpha from coverage and luminance. */
    @VisibleForTesting
    static int whiteWithAlpha(int pixel) {
        int alpha = Color.alpha(pixel);
        if (alpha == 0) return Color.TRANSPARENT;
        // Rec. 601 luma, which is what the eye reads as "how bright is this bit of the icon".
        float luminance = (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel)
            + 0.114f * Color.blue(pixel)) / 255f;
        float coverage = SHAPE_FLOOR + (1f - SHAPE_FLOOR) * luminance;
        int out = Math.round(alpha * coverage);
        return Color.argb(Math.max(0, Math.min(255, out)), 255, 255, 255);
    }
}
