package com.termux.app.launcher.icon;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

/**
 * Bounded ownership of raw launcher artwork — the picture an app ships, before the dock's glass
 * treatment is applied to it.
 *
 * <p>Every catalogue entry used to carry its own {@link Drawable} for the life of the process. That
 * is one icon per installed app and nothing ever gave one back: 115 apps held 36.4 MB here, and the
 * cost is linear in how many apps someone has, so a 300-app device pays around 90 MB for artwork
 * that is almost entirely off screen. The model objects now carry identity, and the pixels live
 * here under a budget.</p>
 *
 * <p>Two things make a budget small enough to work. Artwork is rasterised down to
 * {@link #MAX_RETAINED_PX} on the way in — the framework hands back the system-density
 * rasterisation, 284x284 on this display, where the drawer's grid caps its cells at 48dp and the
 * dock rail asks for 38dp, so most of those pixels could never be shown. And this sits behind
 * {@link DockIconCache}, which caches the rendered result: raw artwork is only wanted when that
 * one misses, or by the few surfaces that draw an app's own icon untreated.</p>
 *
 * <p>An entry that carries artwork of its own — a folder, a per-app icon override, a test stub —
 * keeps it and is never looked up here. Those are bespoke and there is nothing to load them from.</p>
 */
public final class LauncherIconStore {

    /**
     * The largest artwork worth keeping. The drawer grid clamps its icons to
     * {@code AppDrawerGridMetrics.MAX_ICON_DP} (48dp) and the dock rail asks for 38dp, so on any
     * ordinary density this is comfortably above every size anything renders at; a request beyond
     * it upscales slightly rather than failing. At 284x284 an {@code ARGB_8888} icon costs 323 KB
     * and at 192x192 it costs 147 KB, which is the difference between a budget that holds a
     * catalogue and one that thrashes through it.
     */
    @VisibleForTesting
    static final int MAX_RETAINED_PX = 192;

    /** Smallest artwork budget we will hand out, even on a memory-starved device. */
    private static final int MIN_BYTES = 4 * 1024 * 1024;
    /** Ceiling regardless of how generous the heap is: ~110 icons at the retained size. */
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    /** Fraction of the per-app heap raw artwork may hold (1/16th). */
    private static final int HEAP_DIVISOR = 16;

    /** Produces an app's raw artwork when it is not held. Runs on whichever thread asked. */
    public interface ArtworkLoader {
        @Nullable Drawable load(@NonNull AppRef ref);
    }

    @NonNull private final Resources resources;
    @NonNull private final ArtworkLoader loader;
    @NonNull private final LruCache<String, Drawable> cache;

    public LauncherIconStore(@NonNull Resources resources, int memoryClassMb,
                             @NonNull ArtworkLoader loader) {
        this.resources = resources;
        this.loader = loader;
        this.cache = new LruCache<String, Drawable>(resolveBudgetBytes(memoryClassMb)) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return entrySize(value);
            }
        };
    }

    /** One sixteenth of the per-app heap, clamped into [4MB, 16MB]. */
    public static int resolveBudgetBytes(int memoryClassMb) {
        long heapBytes = (long) Math.max(0, memoryClassMb) * 1024L * 1024L;
        long budget = heapBytes / HEAP_DIVISOR;
        if (budget < MIN_BYTES) budget = MIN_BYTES;
        if (budget > MAX_BYTES) budget = MAX_BYTES;
        return (int) budget;
    }

    /**
     * The raw artwork for {@code entry}: its own if it carries one, otherwise the held or freshly
     * loaded artwork for its app. Null when nothing could be produced, which callers already treat
     * as "draw the default".
     */
    @Nullable
    public Drawable artwork(@Nullable LauncherAppEntry entry) {
        if (entry == null) return null;
        if (entry.icon != null) return entry.icon;
        return artwork(entry.appRef);
    }

    /** The held or freshly loaded artwork for one app. */
    @Nullable
    public Drawable artwork(@Nullable AppRef ref) {
        if (ref == null) return null;
        String key = ref.stableId();
        Drawable held = cache.get(key);
        if (held != null) return held;
        Drawable loaded = shrink(loader.load(ref));
        if (loaded != null) cache.put(key, loaded);
        return loaded;
    }

    /**
     * Hand the store artwork that has just been resolved anyway, so the first paint does not have
     * to load it again. The catalogue load already resolves every app on its worker thread; this is
     * what keeps that work useful without keeping every drawable alive forever.
     */
    public void prime(@Nullable AppRef ref, @Nullable Drawable artwork) {
        if (ref == null || artwork == null) return;
        Drawable retained = shrink(artwork);
        if (retained != null) cache.put(ref.stableId(), retained);
    }

    /** Drops every held drawable; the next read reloads at the current icon-pack treatment. */
    public void invalidateAll() {
        cache.evictAll();
    }

    /** Bytes currently held. */
    @VisibleForTesting
    public int sizeBytes() {
        return cache.size();
    }

    /** Read-only budget. */
    public int budgetBytes() {
        return cache.maxSize();
    }

    /**
     * Rasterise artwork that is both larger than we will ever draw it and actually made of pixels.
     * A vector costs a few kilobytes and scales for free, so flattening one to a bitmap would spend
     * memory rather than save it; only a drawable already carrying bitmaps is worth redrawing.
     */
    @Nullable
    @VisibleForTesting
    Drawable shrink(@Nullable Drawable source) {
        if (source == null) return null;
        // How big the pixels actually are, which is the only thing that costs anything. A drawable
        // is asked what size it is, but not believed on its own: an adaptive icon declares the
        // nominal 72dp while holding the 108dp rasterisation, so trusting the declaration keeps
        // 323 KB per app in order to draw 143 KB of it. A container that will not answer at all is
        // measured entirely by what it holds.
        int largestBitmap = largestBitmapExtent(source, 0);
        // No pixels: a vector costs a few kilobytes and scales for free, so flattening one would
        // spend memory rather than save it.
        if (largestBitmap <= 0) return source;
        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) width = height = largestBitmap;
        int longestEdge = Math.max(width, height);
        int targetEdge = Math.min(MAX_RETAINED_PX, longestEdge);
        // Nothing to gain once the pixels are no larger than the size they would be redrawn at.
        if (largestBitmap <= targetEdge) return source;
        try {
            float scale = targetEdge / (float) longestEdge;
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));
            Bitmap flattened = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Rect previousBounds = new Rect(source.getBounds());
            source.setBounds(0, 0, targetWidth, targetHeight);
            source.draw(new Canvas(flattened));
            source.setBounds(previousBounds);
            return new BitmapDrawable(resources, flattened);
        } catch (RuntimeException | OutOfMemoryError e) {
            // Keeping the original costs memory; failing to produce artwork costs the user an icon.
            return source;
        }
    }

    /**
     * The largest bitmap edge anywhere inside this drawable, or 0 when it holds none.
     *
     * <p>A container is worth whatever it contains, and launcher artwork is almost never a bare
     * bitmap: the framework hands back adaptive icons whose layers are themselves wrapped in
     * insets, scales and rotations, and layer lists of the same — and some of those containers
     * report no intrinsic size at all. Looking only one level down, or believing a -1, keeps every
     * one of them whole, which is the entire cost this class exists to remove. So the walk goes all
     * the way down.</p>
     */
    private static int largestBitmapExtent(@Nullable Drawable drawable, int depth) {
        if (drawable == null || depth > MAX_CONTAINER_DEPTH) return 0;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            return bitmap == null ? 0 : Math.max(bitmap.getWidth(), bitmap.getHeight());
        }
        // Adaptive icons are checked before layer lists: they are not one, and they expose their
        // two layers by name rather than by index.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            return Math.max(largestBitmapExtent(adaptive.getBackground(), depth + 1),
                largestBitmapExtent(adaptive.getForeground(), depth + 1));
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            int largest = 0;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                largest = Math.max(largest, largestBitmapExtent(layers.getDrawable(i), depth + 1));
            }
            return largest;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && drawable instanceof DrawableWrapper) {
            return largestBitmapExtent(((DrawableWrapper) drawable).getDrawable(), depth + 1);
        }
        return 0;
    }

    /** Deep enough for an adaptive icon of wrapped layer lists; a guard against a cyclic drawable. */
    private static final int MAX_CONTAINER_DEPTH = 6;

    /**
     * Byte cost of one held drawable. Anything without a bitmap still costs 1, so occupancy tracks
     * entries and {@link #invalidateAll()} returns to zero.
     */
    @VisibleForTesting
    static int entrySize(@Nullable Drawable value) {
        if (!(value instanceof BitmapDrawable)) return 1;
        Bitmap bitmap = ((BitmapDrawable) value).getBitmap();
        if (bitmap == null) return 1;
        long bytes = bitmap.getAllocationByteCount();
        if (bytes < 1L) return 1;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }
}
