package com.termux.app.launcher.icon;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.launcher.model.LauncherAppEntry;

/**
 * The rendered-icon subsystem behind the dock and the app drawer: it harmonizes raw launcher
 * artwork into one glass treatment and hands out the <em>same drawable instance</em> for the same
 * (app, pixel size) pair.
 *
 * <p>Sharing one cache across surfaces is the point. Keys carry the pixel size, so a drawer cell
 * and a dock icon of the same size are literally the same object: no size jump between resting and
 * swipe-preview rendering, and a fling does not rebuild bitmaps per frame.
 *
 * <p>Budgeted in bytes rather than entries, because the dock's 48dp icons and the drawer's grid
 * icons live in the cache simultaneously at several sizes; a count-based cap would admit wildly
 * different amounts of pixel data depending on which sizes happen to be live.
 *
 * <p>Hot path: {@link #icon} allocates nothing on a hit beyond the key string.
 */
public final class DockIconCache {

    /**
     * Bumped whenever the render pipeline changes shape, so stale keys (and stale render
     * signatures upstream) never resurrect artwork from an older treatment.
     */
    public static final int RENDER_PIPELINE_VERSION = 2;

    private static final int SHADOW_COLOR = 0x47000000;
    /** Smallest icon budget we will hand out, even on a memory-starved device. */
    private static final int MIN_BYTES = 8 * 1024 * 1024;
    /**
     * Ceiling regardless of how generous the heap is. Sized against a real catalogue rather than a
     * round number: at xxhdpi a 48dp entry costs ~166KB (display plus retained clean artwork), so
     * the old 16MB ceiling held ~96 entries — fewer apps than one drawer screenful cycle, which
     * turned every fling into a continuous re-render (4 bitmaps and a mask blur per cell, on the
     * UI thread). 32MB holds ~190 entries, enough that a scroll revisits before eviction.
     */
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    /** Fraction of the per-app heap the rendered-icon cache may hold (1/8th). */
    private static final int HEAP_DIVISOR = 8;

    /** Supplies the stand-in artwork for an entry that carries no icon of its own. */
    public interface DefaultIconSource {
        @Nullable Drawable defaultIcon();
    }

    /**
     * Where an entry's raw artwork comes from. Catalogue entries carry identity rather than pixels,
     * so the artwork is fetched at render time — see {@link LauncherIconStore}.
     */
    public interface ArtworkSource {
        @Nullable Drawable artwork(@NonNull LauncherAppEntry entry);
    }

    @NonNull private final Resources resources;
    @NonNull private final DefaultIconSource defaultIconSource;
    @Nullable private final ArtworkSource artworkSource;
    @NonNull private final LruCache<String, Drawable> cache;

    public DockIconCache(@NonNull Resources resources, int memoryClassMb,
                         @NonNull DefaultIconSource defaultIconSource) {
        this(resources, memoryClassMb, defaultIconSource, null);
    }

    public DockIconCache(@NonNull Resources resources, int memoryClassMb,
                         @NonNull DefaultIconSource defaultIconSource,
                         @Nullable ArtworkSource artworkSource) {
        this.resources = resources;
        this.defaultIconSource = defaultIconSource;
        this.artworkSource = artworkSource;
        this.cache = new LruCache<String, Drawable>(resolveBudgetBytes(memoryClassMb)) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return entrySize(value);
            }
        };
    }

    /** Per-app heap ceiling in MB, or a conservative stand-in when the service is unavailable. */
    public static int memoryClassMb(@Nullable Context context) {
        try {
            ActivityManager activityManager = context == null
                ? null : (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                return activityManager.getMemoryClass();
            }
        } catch (Throwable ignored) {
            // Fall through to the floor below; an unusable service is not a reason to render nothing.
        }
        return 0;
    }

    /** One eighth of the per-app heap, clamped into [8MB, 32MB]. */
    public static int resolveBudgetBytes(int memoryClassMb) {
        return HeapBudget.of(memoryClassMb, HEAP_DIVISOR, MIN_BYTES, MAX_BYTES);
    }

    /**
     * Byte cost of one cached icon. A {@link RenderedIconDrawable} retains its clean pre-shadow
     * artwork at the same dimensions as the display bitmap, so it is charged twice. Anything without
     * a bitmap (a placeholder, a vector) still costs 1 so that occupancy tracks entries and
     * {@link #invalidateAll()} returns to zero.
     */
    public static int entrySize(@Nullable Drawable value) {
        if (!(value instanceof BitmapDrawable)) return 1;
        Bitmap bitmap = ((BitmapDrawable) value).getBitmap();
        if (bitmap == null) return 1;
        boolean hasCleanArtwork = value instanceof RenderedIconDrawable;
        long bytes = (long) bitmap.getAllocationByteCount() * (1 + (hasCleanArtwork ? 1 : 0));
        if (bytes < 1L) return 1;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /** Read-only budget shared by dock and drawer rendered icons. */
    public int budgetBytes() {
        return cache.maxSize();
    }

    /**
     * The harmonized icon for {@code entry} at {@code sizePx}, from cache when possible.
     *
     * <p>Returns the raw artwork untouched when the size is not yet known ({@code sizePx <= 0}) or
     * when rendering could not produce a bitmap, so a caller always has something to draw.
     */
    @Nullable
    public Drawable icon(@NonNull LauncherAppEntry entry, int sizePx) {
        Drawable raw = rawArtwork(entry);
        if (sizePx <= 0) {
            return raw;
        }
        Badge badge = entry.appRef.clonedProfile ? Badge.CLONE
            : com.termux.app.x11.X11Apps.isLinuxApp(entry.appRef) ? Badge.LINUX : Badge.NONE;
        String key = "glass" + RENDER_PIPELINE_VERSION
            + (badge == Badge.CLONE ? "c" : badge == Badge.LINUX ? "x" : "")
            + (entry.iconPackArtwork ? "p" : "")
            + entry.appRef.stableId() + "@" + sizePx;
        Drawable cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Drawable built = normalize(raw, sizePx, !entry.iconPackArtwork, badge);
        if (built != null) {
            cache.put(key, built);
        }
        return built != null ? built : raw;
    }

    /**
     * The untreated artwork for an entry: its own if it carries one, then the store's, then the
     * system default — so a caller always has something to draw.
     */
    @Nullable
    public Drawable rawArtwork(@NonNull LauncherAppEntry entry) {
        if (entry.icon != null) return entry.icon;
        Drawable stored = artworkSource == null ? null : artworkSource.artwork(entry);
        return stored != null ? stored : defaultIconSource.defaultIcon();
    }

    /** Drops every rendered icon; the next bind re-renders at the current treatment. */
    public void invalidateAll() {
        cache.evictAll();
    }

    /** Bytes currently held. */
    @VisibleForTesting
    public int sizeBytes() {
        return cache.size();
    }

    @VisibleForTesting
    public int evictionCount() {
        return cache.evictionCount();
    }

    /**
     * Renders {@code src} into the launcher's glass treatment at {@code sizePx}: a fixed inset
     * footprint, an optional saturation nudge, an optional clone badge, and a silhouette contact
     * shadow. The result retains its clean pre-shadow artwork for contour extraction.
     */
    @Nullable
    public Drawable normalize(@Nullable Drawable src, int sizePx, boolean tuneSaturation,
                              boolean cloneBadge) {
        return normalize(src, sizePx, tuneSaturation, cloneBadge ? Badge.CLONE : Badge.NONE);
    }

    /** The small mark in an icon's corner that says what kind of app this is. */
    public enum Badge {
        NONE,
        /** A work or clone profile's copy of an app. */
        CLONE,
        /** A Linux app that runs on the display. */
        LINUX
    }

    @Nullable
    public Drawable normalize(@Nullable Drawable src, int sizePx, boolean tuneSaturation,
                              @NonNull Badge badge) {
        if (src == null || sizePx <= 0) {
            return src;
        }
        Bitmap cleanArtwork = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas artworkCanvas = new Canvas(cleanArtwork);

        // Keep the prior unification footprint as the normalization baseline for every source,
        // including icon-pack artwork. The native silhouette is never reshaped.
        float inset = sizePx * 0.035f;
        int left = Math.round(inset);
        int top = Math.round(inset);
        int right = Math.round(sizePx - inset);
        int bottom = Math.round(sizePx - inset);
        Rect iconRect = new Rect(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));

        // Render the source at the footprint size so we can derive a silhouette shadow that follows
        // its native shape (adaptive icons draw their own masked bg+fg here, so shape is preserved).
        Bitmap iconBmp = Bitmap.createBitmap(iconRect.width(), iconRect.height(), Bitmap.Config.ARGB_8888);
        Canvas iconCanvas = new Canvas(iconBmp);
        Rect oldBounds = new Rect(src.getBounds());
        src.setBounds(0, 0, iconBmp.getWidth(), iconBmp.getHeight());
        src.draw(iconCanvas);
        src.setBounds(oldBounds);

        // Saturation nudge toward the glass vibrancy (match, not grey), then draw the icon.
        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (tuneSaturation) {
            ColorMatrix saturate = new ColorMatrix();
            saturate.setSaturation(0.92f);
            iconPaint.setColorFilter(new ColorMatrixColorFilter(saturate));
        }
        artworkCanvas.drawBitmap(iconBmp, null, iconRect, iconPaint);
        iconBmp.recycle();
        if (badge == Badge.CLONE) drawCloneBadge(artworkCanvas, sizePx);
        else if (badge == Badge.LINUX) drawLinuxBadge(artworkCanvas, sizePx);

        Bitmap display = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas displayCanvas = new Canvas(display);
        drawIconShadow(displayCanvas, cleanArtwork);
        displayCanvas.drawBitmap(cleanArtwork, 0f, 0f,
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return new RenderedIconDrawable(resources, display, cleanArtwork);
    }

    /** Small, neutral double-tile badge that remains legible over both bright and dark icons. */
    private void drawCloneBadge(@NonNull Canvas canvas, int sizePx) {
        float radius = Math.max(dp(6f), sizePx * 0.19f);
        float cx = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        float cy = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xF2FFFFFF);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1f), sizePx * 0.025f));
        paint.setColor(0x70000000);
        canvas.drawCircle(cx, cy, radius - (paint.getStrokeWidth() * 0.5f), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD9202020);
        float tile = radius * 0.82f;
        float corner = tile * 0.22f;
        canvas.drawRoundRect(cx - tile * 0.58f, cy - tile * 0.58f,
            cx + tile * 0.22f, cy + tile * 0.22f, corner, corner, paint);
        paint.setColor(0xFF5B6CFF);
        canvas.drawRoundRect(cx - tile * 0.20f, cy - tile * 0.20f,
            cx + tile * 0.60f, cy + tile * 0.60f, corner, corner, paint);
    }

    /**
     * The same disc as the clone badge carrying a prompt — a chevron and a cursor — so a Linux
     * app reads as one at a glance, in the drawer, the dock and the suggestions alike.
     */
    private void drawLinuxBadge(@NonNull Canvas canvas, int sizePx) {
        float radius = Math.max(dp(6f), sizePx * 0.19f);
        float cx = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        float cy = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xF2FFFFFF);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1f), sizePx * 0.025f));
        paint.setColor(0x70000000);
        canvas.drawCircle(cx, cy, radius - (paint.getStrokeWidth() * 0.5f), paint);

        // The chevron: two strokes meeting at the right, set left of centre.
        float stroke = Math.max(dp(1.25f), radius * 0.2f);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xD9202020);
        float half = radius * 0.34f;
        float chevronLeft = cx - radius * 0.52f;
        float chevronRight = cx - radius * 0.06f;
        Path chevron = new Path();
        chevron.moveTo(chevronLeft, cy - half);
        chevron.lineTo(chevronRight, cy);
        chevron.lineTo(chevronLeft, cy + half);
        canvas.drawPath(chevron, paint);
        // The cursor: a short bar on the baseline, in the badge accent.
        paint.setColor(0xFF5B6CFF);
        canvas.drawLine(cx + radius * 0.14f, cy + half, cx + radius * 0.56f, cy + half, paint);
    }

    /** Subtle silhouette contact shadow: 3dp feather, 1dp down, 28% black. */
    private void drawIconShadow(@NonNull Canvas canvas, @NonNull Bitmap cleanArtwork) {
        Bitmap alpha = cleanArtwork.extractAlpha();
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(SHADOW_COLOR);
        shadowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, dp(3f)), BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(alpha, 0f, dp(1f), shadowPaint);
        alpha.recycle();
    }

    private float dp(float value) {
        return value * resources.getDisplayMetrics().density;
    }
}
