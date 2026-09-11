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
 * The app icon a Display-place window chip wears: the window's {@code WM_CLASS} traced back to an
 * installed app's desktop file, its PNG loaded from the prefix, and the artwork reduced to a
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
 * happen on a background thread, and a resolved icon reaches the bar through a callback on the
 * main thread. Results are cached per class — misses included, so a window from something that is
 * not installed is not looked up again every time the bar syncs.
 */
public final class X11WindowIconResolver {

    /**
     * The silhouette's edge, in pixels. A chip draws it around 16 dp, so this covers the densest
     * phone without scaling up, and one costs 16 KB — the cache below is what keeps that bounded.
     */
    @VisibleForTesting static final int SILHOUETTE_PX = 64;

    /**
     * How much of a fully transparent-to-opaque pixel's own coverage survives at zero luminance.
     * Luminance alone would erase a dark icon on a dark chip, which is the common case for the
     * terminal's palettes; keeping a floor means the artwork's shape always reads and its bright
     * parts read stronger.
     */
    @VisibleForTesting static final float SHAPE_FLOOR = 0.45f;

    /** How many silhouettes are kept. A display with more open apps than this re-resolves. */
    private static final int MAX_CACHED = 32;

    /** Told, on the main thread, that a class now has an icon — or is known not to have one. */
    public interface Callback {
        void onWindowIconResolved(@NonNull String wmClass, @Nullable Bitmap icon);
    }

    @NonNull private final Resources resources;
    @NonNull private final Callback callback;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** Class (lower-cased) to its silhouette, or null for a class known to have none. */
    @NonNull private final Map<String, Bitmap> cache = new HashMap<>();
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
        String key = key(wmClass);
        if (key.isEmpty()) return null;
        if (cache.containsKey(key)) return cache.get(key);
        if (!pending.add(key)) return null;
        worker.execute(() -> {
            Bitmap icon = resolve(wmClass);
            main.post(() -> {
                pending.remove(key);
                if (cache.size() >= MAX_CACHED) cache.clear();
                cache.put(key, icon);
                callback.onWindowIconResolved(wmClass, icon);
            });
        });
        return null;
    }

    /** Forget every resolution, so a newly installed app's icon is picked up. */
    public void clear() {
        cache.clear();
    }

    /** Stop resolving; the resolver is unusable afterwards. */
    public void shutdown() {
        worker.shutdownNow();
    }

    /** The whole background half: catalogue scan, icon file, decode, silhouette. */
    @Nullable
    private Bitmap resolve(@NonNull String wmClass) {
        try {
            LinuxAppCatalog.LinuxApp app = match(
                LinuxAppCatalog.scan(LinuxAppCatalog.applicationDirs()), wmClass);
            if (app == null) return null;
            File file = LinuxAppIcons.find(app.icon, LinuxAppIcons.prefix());
            if (file == null) return null;
            Drawable drawable = LinuxAppIcons.load(resources, file);
            Bitmap source = drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap() : null;
            return source == null ? null : silhouette(source, SILHOUETTE_PX);
        } catch (RuntimeException | OutOfMemoryError e) {
            // An icon is decoration: a malformed PNG or a tight moment must not take the bar down.
            return null;
        }
    }

    /**
     * The installed app a window's class belongs to, or null. Keys are tried in order — the
     * desktop-file name, {@code StartupWMClass}, the {@code Exec} basename, the last segment of a
     * reverse-DNS desktop-file name — and every app is tested against a key before the next key is
     * tried, so an exact name match always beats another app's looser one.
     */
    @Nullable
    public static LinuxAppCatalog.LinuxApp match(
            @NonNull List<LinuxAppCatalog.LinuxApp> apps, @NonNull String wmClass) {
        String key = key(wmClass);
        if (key.isEmpty()) return null;
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(key(app.id))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(key(app.startupWmClass))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(execBasename(app.exec))) return app;
        }
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (key.equals(tail(key(app.id)))) return app;
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
