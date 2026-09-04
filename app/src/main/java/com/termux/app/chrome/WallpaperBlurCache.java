package com.termux.app.chrome;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * The one pre-blurred wallpaper frame every accessory glass surface is cut from.
 *
 * <p>Pre-blurred wallpaper frames shared by dock, keyboard, gesture-nav, and top-pane frost
 * crops — one frame per requested blur radius, LRU-capped. Surfaces are tuned independently
 * (dock and status frost carry their own radius sliders); the previous single-slot cache
 * was invalidated by every radius alternation, re-decoding and re-blurring the wallpaper on
 * the main thread two or three times on every return home (1-3s of dropped frames).</p>
 *
 * <p>Geometry changes only crop this bitmap; they never capture and blur a second, visually
 * different copy.</p>
 */
public final class WallpaperBlurCache {

    /** Everything the cache needs from the outside world: the wallpaper, and who is using a frame. */
    public interface Source {

        /** The wallpaper frame's rect in screen coordinates — the full-frame crop's geometry. */
        @NonNull Rect wallpaperFrameRect();

        /** True while the managed (app-owned) wallpaper file is the blur source. */
        boolean useManagedWallpaperSource();

        /** The system wallpaper's current id; a change to it invalidates every cached frame. */
        int systemWallpaperId();

        /** The managed wallpaper file, consulted for its size/mtime identity. */
        @NonNull File managedWallpaperExactFile();

        /** The configuration orientation the next capture would be taken in. */
        int orientation();

        /** Captures the unblurred wallpaper region for {@code frameRect}. */
        @Nullable Bitmap captureWallpaperFrame(@NonNull Rect frameRect, @NonNull View wallpaperFrame);

        /** Blurs a captured frame; may return {@code sourceBitmap} itself when the radius is 0. */
        @Nullable Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp);

        /**
         * True while some view still draws this exact frame. Recycling a bitmap an ImageView holds
         * crashes on its next draw, so such a frame is dropped from the cache without recycling
         * and left to the collector.
         */
        boolean isFrameInUse(@Nullable Bitmap frame);

        /** Called after the cache is emptied, for the state that shadows it outside the module. */
        void onCacheCleared();
    }

    /** How many independently tuned radii stay resident before the least-recently-used is dropped. */
    public static final int MAX_CACHED_WALLPAPER_BLUR_RADII = 3;
    /**
     * How many bytes of pre-blurred frames stay resident, whatever the radius count. A frame is a
     * full-screen ARGB_8888 bitmap — about 10 MB on a 1080x2400 panel and 18 MB at 1440x3200 — so
     * a count alone let a QHD phone hold 55 MB of wallpaper nobody was looking at. The most recent
     * frame always stays, however large.
     */
    public static final long DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES = 40L * 1024 * 1024;

    @NonNull private final Source mSource;
    @Nullable private final Runnable mOnCleared;
    private final long mMaxBytes;

    @NonNull private final LinkedHashMap<Integer, Bitmap> mByRadius =
        new LinkedHashMap<>(4, 0.75f, true);
    @NonNull private final Rect mFrameRect = new Rect();
    private boolean mManagedSource;
    private int mSystemId = -1;
    private long mManagedLastModified = -1L;
    private long mManagedLength = -1L;
    /**
     * The orientation the cached frames were captured in. The frame rect alone was supposed to
     * carry this, but a rotation delivers {@code onConfigurationChanged} <em>before</em> the window
     * is re-laid out, so a crop taken during that pass records the outgoing orientation's rect and
     * then matches itself forever after. That is what landscape showed: a brighter, mismatched
     * wallpaper region with a hard seam at the pane's left edge, while portrait was correct.
     */
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    public WallpaperBlurCache(@NonNull Source source) {
        this(source, null);
    }

    /** @param onCleared runs after every clear, for the module-side state cut from the frames */
    public WallpaperBlurCache(@NonNull Source source, @Nullable Runnable onCleared) {
        this(source, onCleared, DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES);
    }

    /** @param maxBytes the resident-frame byte budget; see {@link #DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES} */
    public WallpaperBlurCache(@NonNull Source source, @Nullable Runnable onCleared, long maxBytes) {
        mSource = source;
        mOnCleared = onCleared;
        mMaxBytes = maxBytes;
    }

    /** The rect the resident frames were captured for, in screen coordinates. */
    public void copyFrameRect(@NonNull Rect out) {
        out.set(mFrameRect);
    }

    public int frameRectWidth() {
        return mFrameRect.width();
    }

    public int frameRectHeight() {
        return mFrameRect.height();
    }

    public int frameRectLeft() {
        return mFrameRect.left;
    }

    public int frameRectTop() {
        return mFrameRect.top;
    }

    /**
     * The live frame rect, for the per-draw readers (the terminal pane glass, the departure
     * snapshot's ground) that would otherwise allocate a {@link Rect} on every frame. Read only.
     */
    @NonNull
    public Rect frameRectRef() {
        return mFrameRect;
    }

    /** True while {@code frame} is one of the resident pre-blurred frames. */
    public boolean containsFrame(@Nullable Bitmap frame) {
        return frame != null && mByRadius.containsValue(frame);
    }

    /** Visible for tests: how many radii are resident right now. */
    public int residentRadiiCount() {
        return mByRadius.size();
    }

    /** Visible for tests: whether this radius is resident without touching LRU recency order. */
    public boolean hasRadius(int blurRadiusDp) {
        return mByRadius.containsKey(blurRadiusDp);
    }

    /** Bytes the resident frames hold, the figure the byte budget is charged against. */
    public long residentBytes() {
        long total = 0L;
        for (Bitmap frame : mByRadius.values())
            if (frame != null && !frame.isRecycled()) total += frame.getAllocationByteCount();
        return total;
    }

    /**
     * Returns the pre-blurred full wallpaper frame for {@code blurRadiusDp}, capturing and blurring
     * one only when no valid frame is resident.
     */
    @Nullable
    public Bitmap obtain(int blurRadiusDp, @NonNull View wallpaperFrame) {
        Rect frameRect = mSource.wallpaperFrameRect();
        boolean managedSource = mSource.useManagedWallpaperSource();
        int systemWallpaperId = mSource.systemWallpaperId();
        File managedFile = managedSource ? mSource.managedWallpaperExactFile() : null;
        long managedLastModified = managedFile != null ? managedFile.lastModified() : -1L;
        long managedLength = managedFile != null ? managedFile.length() : -1L;
        boolean sourceValid = mManagedSource == managedSource
            && mSystemId == systemWallpaperId
            && mManagedLastModified == managedLastModified
            && mManagedLength == managedLength
            && mOrientation == mSource.orientation()
            && mFrameRect.equals(frameRect);
        if (sourceValid) {
            Bitmap cached = mByRadius.get(blurRadiusDp);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        } else {
            // The wallpaper itself changed; every per-radius frame is stale.
            clear();
        }

        Bitmap wallpaperBitmap = mSource.captureWallpaperFrame(frameRect, wallpaperFrame);
        if (wallpaperBitmap == null) {
            return null;
        }
        Bitmap blurredBitmap = mSource.preBlur(wallpaperBitmap, blurRadiusDp);
        if (blurredBitmap == null) {
            wallpaperBitmap.recycle();
            return null;
        }
        if (blurredBitmap != wallpaperBitmap) {
            wallpaperBitmap.recycle();
        }
        mByRadius.put(blurRadiusDp, blurredBitmap);
        while (mByRadius.size() > MAX_CACHED_WALLPAPER_BLUR_RADII
            || (mByRadius.size() > 1 && residentBytes() > mMaxBytes)) {
            Iterator<Bitmap> eldest = mByRadius.values().iterator();
            Bitmap evicted = eldest.next();
            eldest.remove();
            if (evicted != null && !evicted.isRecycled() && !mSource.isFrameInUse(evicted)) {
                evicted.recycle();
            }
        }
        mFrameRect.set(frameRect);
        mOrientation = mSource.orientation();
        mManagedSource = managedSource;
        mSystemId = systemWallpaperId;
        mManagedLastModified = managedLastModified;
        mManagedLength = managedLength;
        return blurredBitmap;
    }

    /**
     * Crops the shared full-frame blur in screen coordinates, clamping any overscan at its edges.
     *
     * <p>A full-screen surface (the command palette glass, the app drawer plane) asks for exactly
     * the cached frame's rect, and copying it would allocate a second full-screen ARGB_8888 bitmap
     * — ~10MB on a 1080x2400 panel, on the first frame of the open gesture. That request is
     * answered with the cached frame itself; the returned bitmap is then shared, so
     * {@link #clear()} detaches it from the glass frosts before recycling.</p>
     */
    @Nullable
    public Bitmap crop(int blurRadiusDp, @NonNull Rect targetRect, @NonNull View wallpaperFrame) {
        Bitmap fullBlur = obtain(blurRadiusDp, wallpaperFrame);
        if (fullBlur == null) {
            return null;
        }
        if (targetRect.equals(mFrameRect)) {
            return fullBlur;
        }
        int width = Math.max(1, targetRect.width());
        int height = Math.max(1, targetRect.height());
        Bitmap crop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop);
        BitmapShader shader = new BitmapShader(fullBlur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setTranslate(mFrameRect.left - targetRect.left, mFrameRect.top - targetRect.top);
        shader.setLocalMatrix(matrix);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawRect(0f, 0f, width, height, paint);
        return crop;
    }

    /** Empties the cache, recycling every frame nothing is drawing. */
    public void clear() {
        for (Bitmap cached : mByRadius.values()) {
            if (cached != null && !cached.isRecycled() && !mSource.isFrameInUse(cached)) {
                cached.recycle();
            }
        }
        mByRadius.clear();
        mFrameRect.setEmpty();
        mOrientation = Configuration.ORIENTATION_UNDEFINED;
        mManagedSource = false;
        mSystemId = -1;
        mManagedLastModified = -1L;
        mManagedLength = -1L;
        mSource.onCacheCleared();
        if (mOnCleared != null) mOnCleared.run();
    }
}
