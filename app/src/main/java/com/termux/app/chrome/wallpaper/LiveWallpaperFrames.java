package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The live blurred frames, one per live radius, that the glass surfaces read at draw time
 * (animated-wallpaper SPEC §3.3). The {@code WallpaperParallax} of time: written on the main
 * thread when a render lands, read by every reader in its own draw, one process-wide instance
 * ({@link #get()}) so nothing has to be handed it.
 *
 * <p>Holds hardware bitmaps wrapping the renderer's ring slots. A reader asks
 * {@link #frame(float)} with its own radius; null means no live frame for it (nothing is playing,
 * the radius has none) and the reader draws its still exactly as before. A reader also checks
 * {@code canvas.isHardwareAccelerated()} first: a software canvas never gets a live frame. Readers
 * keep their own {@link ShaderCache} so each slot's {@link BitmapShader} is built once per surface
 * and only rebound afterwards.</p>
 */
public final class LiveWallpaperFrames {

    private static final LiveWallpaperFrames INSTANCE = new LiveWallpaperFrames();

    /** The process-wide instance every reader and the clock share. */
    @NonNull
    public static LiveWallpaperFrames get() {
        return INSTANCE;
    }

    private final float[] mRadii = new float[LiveRadii.MAX_LIVE_RADII];
    private final Bitmap[] mFrames = new Bitmap[LiveRadii.MAX_LIVE_RADII];
    private int mCount;
    private int mGeneration;
    private int mEpoch;

    /** Public so tests can build their own; production code uses {@link #get()}. */
    public LiveWallpaperFrames() {}

    /**
     * Makes {@code frames[i]} the live frame for {@code radiiDp[i]}, for {@code count} radii.
     * Main thread. The arrays are copied.
     */
    public void publish(@NonNull float[] radiiDp, @NonNull Bitmap[] frames, int count) {
        int n = Math.min(count, LiveRadii.MAX_LIVE_RADII);
        for (int i = 0; i < n; i++) {
            mRadii[i] = radiiDp[i];
            mFrames[i] = frames[i];
        }
        for (int i = n; i < mFrames.length; i++) mFrames[i] = null;
        mCount = n;
        mGeneration++;
    }

    /** Drops every live frame: readers fall back to their stills on their next draw. */
    public void clear() {
        for (int i = 0; i < mFrames.length; i++) mFrames[i] = null;
        mCount = 0;
        mGeneration++;
        mEpoch++;
    }

    /** The live frame that serves {@code radiusDp}, or null when there is none. */
    @Nullable
    public Bitmap frame(float radiusDp) {
        if (mCount == 0) return null;
        int index = LiveRadii.match(mRadii, mCount, radiusDp);
        if (index < 0) return null;
        Bitmap bitmap = mFrames[index];
        return bitmap == null || bitmap.isRecycled() ? null : bitmap;
    }

    /** Bumps on every publish and clear; a reader can compare it instead of the bitmap. */
    public int generation() {
        return mGeneration;
    }

    /** Bumps on {@link #clear}: the bitmaps of an older epoch are gone for good. */
    int epoch() {
        return mEpoch;
    }

    /** True while at least one live frame is published. */
    public boolean isLive() {
        return mCount > 0;
    }

    /**
     * One reader's shaders for the slots it has met: each ring slot's bitmap wrapped once, in
     * this reader's own {@link BitmapShader} (its local matrix is the reader's aim, so shaders
     * are never shared between readers). Main thread only.
     */
    public static final class ShaderCache {
        private static final int CAPACITY = LiveRadii.MAX_LIVE_RADII * LiveRadii.RING;

        @NonNull private final LiveWallpaperFrames mFrames;
        private final Bitmap[] mBitmaps = new Bitmap[CAPACITY];
        private final BitmapShader[] mShaders = new BitmapShader[CAPACITY];
        private int mNext;
        private int mEpoch;

        public ShaderCache() {
            this(INSTANCE);
        }

        public ShaderCache(@NonNull LiveWallpaperFrames frames) {
            mFrames = frames;
            mEpoch = frames.epoch();
        }

        /** This reader's shader for {@code bitmap}, built on first sight. */
        @NonNull
        public BitmapShader shader(@NonNull Bitmap bitmap) {
            if (mEpoch != mFrames.epoch()) {
                for (int i = 0; i < CAPACITY; i++) {
                    mBitmaps[i] = null;
                    mShaders[i] = null;
                }
                mEpoch = mFrames.epoch();
                mNext = 0;
            }
            for (int i = 0; i < CAPACITY; i++) {
                if (mBitmaps[i] == bitmap && mShaders[i] != null) return mShaders[i];
            }
            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            if (Build.VERSION.SDK_INT >= 33) shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            mBitmaps[mNext] = bitmap;
            mShaders[mNext] = shader;
            mNext = (mNext + 1) % CAPACITY;
            return shader;
        }
    }
}
