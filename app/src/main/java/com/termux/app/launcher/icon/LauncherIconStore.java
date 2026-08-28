package com.termux.app.launcher.icon;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
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
                return DrawablePixels.heldBytes(value);
            }
        };
    }

    /** One sixteenth of the per-app heap, clamped into [4MB, 16MB]. */
    public static int resolveBudgetBytes(int memoryClassMb) {
        return HeapBudget.of(memoryClassMb, HEAP_DIVISOR, MIN_BYTES, MAX_BYTES);
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

    /** Artwork is kept no larger than {@link #MAX_RETAINED_PX}, measured by the pixels it holds. */
    @Nullable
    private Drawable shrink(@Nullable Drawable source) {
        return DrawablePixels.shrink(resources, source, MAX_RETAINED_PX);
    }
}
