package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders the animated wallpaper into one blurred hardware-buffer frame per live radius
 * (animated-wallpaper SPEC §3.3, §3.6). API 34+: {@link HardwareBufferRenderer} draws a
 * {@link RenderNode} straight into a {@link HardwareBuffer} we own.
 *
 * <p>The graph: a <b>source node</b> per downsample (÷4 and ÷2 per side) whose canvas is scaled so
 * the wallpaper shader keeps drawing in the full frame rect's pixels and is re-recorded each
 * render (the uniforms changed); a <b>blur node</b> per live radius with a blur
 * {@link RenderEffect} that draws its source node, recorded once; and a ring of
 * {@link LiveRadii#RING} buffers per radius, each wrapped once with
 * {@link Bitmap#wrapHardwareBuffer} and each with its own renderer rooted at the radius' blur node.
 * A frame allocates nothing.</p>
 *
 * <p>All public methods run on the main thread. {@link #render} skips (returns false) while the
 * previous render has not landed. The callback runs on the main thread after every radius' buffer
 * has finished drawing: the executor thread waits each result's fence first, so a slot is never
 * published half written whatever the queue ordering is.</p>
 *
 * <p>Self-check (SPEC §9.4): the wall time of a render, start to landed, is sampled after a
 * warm-up ({@link RenderBudget#WARMUP_MS}); when the p90 of {@link RenderBudget#WINDOW} of them
 * exceeds {@link RenderBudget#P90_LIMIT_MS}, {@link #slow()} turns true and the renderer stops
 * rendering: the owner steps down a tier and builds a new renderer. A render that fails or throws
 * turns {@link #healthy()} false and the owner shows the still.</p>
 */
@RequiresApi(34)
public final class LiveWallpaperRenderer {

    private static final String TAG = "LiveWallpaperRenderer";

    /** Receives a landed render on the main thread. The arrays are the renderer's; copy, don't keep. */
    public interface Callback {
        /**
         * @param ok      false when a draw failed; the frames are then not to be shown
         * @param radiiDp the live radii of this render, {@code count} of them
         * @param frames  the ring slot just written, per radius
         */
        void onRendered(boolean ok, @NonNull float[] radiiDp, @NonNull Bitmap[] frames, int count);
    }

    /** One live radius: its blur node and its ring. */
    private static final class Ring {
        final float radiusDp;
        final int divisor;
        final RenderNode blurNode;
        final HardwareBuffer[] buffers = new HardwareBuffer[LiveRadii.RING];
        final Bitmap[] bitmaps = new Bitmap[LiveRadii.RING];
        final HardwareBufferRenderer[] renderers = new HardwareBufferRenderer[LiveRadii.RING];

        Ring(float radiusDp, int divisor, RenderNode blurNode) {
            this.radiusDp = radiusDp;
            this.divisor = divisor;
            this.blurNode = blurNode;
        }

        void free() {
            for (int i = 0; i < LiveRadii.RING; i++) {
                if (renderers[i] != null) {
                    try { renderers[i].close(); } catch (Throwable ignored) { }
                    renderers[i] = null;
                }
                if (bitmaps[i] != null) {
                    bitmaps[i].recycle();
                    bitmaps[i] = null;
                }
                if (buffers[i] != null) {
                    try { buffers[i].close(); } catch (Throwable ignored) { }
                    buffers[i] = null;
                }
            }
            blurNode.discardDisplayList();
        }
    }

    @NonNull private final Handler mMain = new Handler(Looper.getMainLooper());
    private final float mDensity;
    @NonNull private final RuntimeShader mShader;
    @NonNull private final Paint mPaint = new Paint();
    /** Source nodes by divisor: index 0 is ÷2, index 1 is ÷4. Created on use. */
    private final RenderNode[] mSources = new RenderNode[2];
    private final Ring[] mRings = new Ring[LiveRadii.MAX_LIVE_RADII];
    private int mRingCount;
    private final float[] mRadiiOut = new float[LiveRadii.MAX_LIVE_RADII];
    private final Bitmap[] mBitmapsOut = new Bitmap[LiveRadii.MAX_LIVE_RADII];
    private float[] mLastRequest = new float[0];
    private int mLastRequestCount = -1;
    private final RenderBudget mBudget = new RenderBudget();
    private final int mTier;
    /** The cheaper source resolution (÷4 whatever the radius) is in force. */
    private final boolean mLowRes;
    @NonNull private final LiveWallpaperFrames mFrames;

    @Nullable private ExecutorService mExecutor;
    private int mFrameW;
    private int mFrameH;
    /** The rings no longer match the frame size or the wallpaper; rebuilt at the next render. */
    private boolean mStale = true;
    private int mSlot;
    private boolean mInFlight;
    private boolean mReleased;
    private boolean mReleaseDeferred;
    private boolean mHealthy = true;
    private boolean mSlow;
    private int mToken;
    private long mStartNanos;
    /** Set on the fence thread when the last radius' buffer is written; ends the timed span. */
    private volatile long mEndNanos;
    @Nullable private Callback mCallback;
    private final AtomicInteger mPending = new AtomicInteger();
    private volatile boolean mFailed;

    /**
     * @param density the screen density, which turns a live radius in dp into blur pixels
     * @param shader  this renderer's own wallpaper shader ({@code WallpaperUniforms.newShader});
     *                the backdrop draws its own, so no uniform is ever written under a draw
     * @param frames  where the rings' bitmaps are published; cleared before any ring is freed
     * @param tier    the step-down tier this renderer runs at, for the log
     * @param lowRes  draw every ring at the ÷4 source resolution
     */
    public LiveWallpaperRenderer(float density, @NonNull RuntimeShader shader,
                                 @NonNull LiveWallpaperFrames frames, int tier, boolean lowRes) {
        mTier = tier;
        mLowRes = lowRes;
        mBudget.restartWarmup(SystemClock.uptimeMillis());
        mFrames = frames;
        mDensity = density;
        mShader = shader;
        mPaint.setShader(shader);
    }

    /** False for good once a render failed or threw. */
    public boolean healthy() {
        return mHealthy;
    }

    /** True once a budget window failed: this renderer renders no more; the owner steps down. */
    public boolean slow() {
        return mSlow;
    }

    /** The screen came on or the device unlocked: ignore renders for the warm-up again. */
    public void restartWarmup() {
        mBudget.restartWarmup(SystemClock.uptimeMillis());
    }

    /** True while a render has not landed yet. */
    public boolean busy() {
        return mInFlight;
    }

    /**
     * The frame rect changed (rotation, resize): the rings are rebuilt at the new size by the
     * next {@link #render}. The owner clears the published frames itself.
     */
    public void rebuild(int frameW, int frameH) {
        if (frameW == mFrameW && frameH == mFrameH && !mStale) return;
        mFrameW = frameW;
        mFrameH = frameH;
        mStale = true;
    }

    /**
     * Draws {@code f} once per live radius. Records the sources, starts the draws and returns; the
     * callback lands later on the main thread.
     *
     * @param radiiDp what the visible surfaces use, in dp, duplicates welcome; merged by
     *                {@link LiveRadii#merge}
     * @return false when nothing was started: the previous render has not landed, the renderer is
     *         released or unhealthy, or there is no radius to draw
     */
    public boolean render(@NonNull WallpaperDirector.Frame f, @NonNull float[] radiiDp,
                          @NonNull Callback done) {
        if (mReleased || !mHealthy || mSlow || mInFlight || mFrameW <= 0 || mFrameH <= 0) return false;
        Trace.beginSection("LiveWallpaper.render");
        try {
            configure(radiiDp);
            if (mRingCount == 0) return false;
            WallpaperUniforms.apply(mShader, f, mFrameW, mFrameH);
            for (int i = 0; i < mRingCount; i++) recordSource(mRings[i].divisor);
            int slot = LiveRadii.nextSlot(mSlot);
            mSlot = slot;
            mCallback = done;
            mFailed = false;
            mPending.set(mRingCount);
            mInFlight = true;
            mStartNanos = System.nanoTime();
            final int token = ++mToken;
            ExecutorService executor = executor();
            ColorSpace srgb = ColorSpace.get(ColorSpace.Named.SRGB);
            for (int i = 0; i < mRingCount; i++) {
                mRings[i].renderers[slot].obtainRenderRequest()
                    .setColorSpace(srgb)
                    .draw(executor, result -> onDrawn(result, token, slot));
            }
            return true;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(TAG, "Live wallpaper render failed", t);
            mInFlight = false;
            mHealthy = false;
            return false;
        } finally {
            Trace.endSection();
        }
    }

    /** Executor thread: wait for the buffer to be written, then count this radius in. */
    private void onDrawn(@NonNull HardwareBufferRenderer.RenderResult result, int token, int slot) {
        try {
            if (result.getStatus() != HardwareBufferRenderer.RenderResult.SUCCESS) {
                mFailed = true;
            }
            SyncFence fence = result.getFence();
            try {
                if (fence.isValid()) fence.awaitForever();
            } finally {
                fence.close();
            }
        } catch (Throwable t) {
            mFailed = true;
        }
        if (mPending.decrementAndGet() == 0) {
            mEndNanos = System.nanoTime();
            mMain.post(() -> landed(token, slot));
        }
    }

    /** Main thread: every radius' buffer for this render has been written. */
    private void landed(int token, int slot) {
        if (token != mToken) return;
        mInFlight = false;
        if (mReleaseDeferred) {
            doRelease();
            return;
        }
        if (mReleased) return;
        boolean ok = !mFailed;
        if (!ok) {
            mHealthy = false;
            Logger.logWarn(TAG, "Live wallpaper render failed (tier " + mTier
                + "); showing the still");
        } else {
            // The render work only (submit to the last fence), not the hop to the main thread.
            boolean within = mBudget.add((mEndNanos - mStartNanos) / 1_000_000f,
                SystemClock.uptimeMillis());
            if (mBudget.windowClosed()) {
                Logger.logInfo(TAG, "Render window closed: p50 " + mBudget.lastP50Ms() + " ms, p90 "
                    + mBudget.lastP90Ms() + " ms, tier " + mTier + ", warm-up skipped "
                    + mBudget.warmupSkipped() + " renders, " + (within ? "within" : "over")
                    + " the " + RenderBudget.P90_LIMIT_MS + " ms limit");
            }
            if (!within) mSlow = true;
        }
        Callback callback = mCallback;
        mCallback = null;
        if (callback == null) return;
        for (int i = 0; i < mRingCount; i++) {
            mRadiiOut[i] = mRings[i].radiusDp;
            mBitmapsOut[i] = mRings[i].bitmaps[slot];
        }
        callback.onRendered(ok, mRadiiOut, mBitmapsOut, mRingCount);
    }

    /** Lets go of every buffer and node. Safe to call twice; defers while a render is in flight. */
    public void release() {
        if (mReleased) return;
        mReleased = true;
        mCallback = null;
        if (mInFlight) {
            mReleaseDeferred = true;
        } else {
            doRelease();
        }
    }

    private void doRelease() {
        mReleaseDeferred = false;
        freeRings();
        for (int i = 0; i < mSources.length; i++) {
            if (mSources[i] != null) {
                mSources[i].discardDisplayList();
                mSources[i] = null;
            }
        }
        ExecutorService executor = mExecutor;
        mExecutor = null;
        if (executor != null) executor.shutdown();
    }

    private ExecutorService executor() {
        ExecutorService executor = mExecutor;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "LiveWallpaper.fence"));
            mExecutor = executor;
        }
        return executor;
    }

    // ------------------------------------------------------------------------------ the graph

    /** Brings the rings in line with the radii asked for, reusing those that already match. */
    private void configure(@NonNull float[] radiiDp) {
        int n = radiiDp.length;
        if (mStale) {
            freeRings();
            for (int i = 0; i < mSources.length; i++) {
                if (mSources[i] != null) {
                    mSources[i].discardDisplayList();
                    mSources[i] = null;
                }
            }
            mStale = false;
            mLastRequestCount = -1;
        }
        if (n == mLastRequestCount && Arrays.equals(radiiDp, mLastRequest)) return;
        mLastRequest = radiiDp.clone();
        mLastRequestCount = n;
        float[] wanted = new float[LiveRadii.MAX_LIVE_RADII];
        int count = LiveRadii.merge(radiiDp, n, wanted);
        Ring[] next = new Ring[LiveRadii.MAX_LIVE_RADII];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < mRingCount; j++) {
                if (mRings[j] != null && mRings[j].radiusDp == wanted[i]) {
                    next[i] = mRings[j];
                    mRings[j] = null;
                    break;
                }
            }
        }
        boolean dropped = false;
        for (int j = 0; j < mRingCount; j++) {
            if (mRings[j] != null) dropped = true;
        }
        // A dropped ring's bitmaps are still published; unpublish before they are recycled.
        if (dropped) mFrames.clear();
        for (int j = 0; j < mRingCount; j++) {
            if (mRings[j] != null) mRings[j].free();
        }
        java.util.Arrays.fill(mRings, null);
        for (int i = 0; i < count; i++) {
            mRings[i] = next[i] != null ? next[i] : buildRing(wanted[i]);
        }
        mRingCount = count;
    }

    private void freeRings() {
        if (mRingCount > 0) mFrames.clear();
        for (int i = 0; i < mRings.length; i++) {
            if (mRings[i] != null) {
                mRings[i].free();
                mRings[i] = null;
            }
        }
        mRingCount = 0;
        mLastRequestCount = -1;
    }

    @NonNull
    private Ring buildRing(float radiusDp) {
        int divisor = LiveRadii.divisor(radiusDp);
        if (mLowRes) divisor = Math.max(divisor, 4);
        int bw = bufferSize(mFrameW, divisor);
        int bh = bufferSize(mFrameH, divisor);
        RenderNode source = source(divisor);
        RenderNode blur = new RenderNode("LiveWallpaper.blur." + radiusDp);
        blur.setPosition(0, 0, bw, bh);
        float sigma = Math.max(0.1f, radiusDp * mDensity / divisor);
        blur.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP));
        Canvas canvas = blur.beginRecording(bw, bh);
        canvas.drawRenderNode(source);
        blur.endRecording();
        Ring ring = new Ring(radiusDp, divisor, blur);
        ColorSpace srgb = ColorSpace.get(ColorSpace.Named.SRGB);
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try {
            for (int i = 0; i < LiveRadii.RING; i++) {
                HardwareBuffer buffer = HardwareBuffer.create(bw, bh, HardwareBuffer.RGBA_8888, 1, usage);
                ring.buffers[i] = buffer;
                ring.bitmaps[i] = Bitmap.wrapHardwareBuffer(buffer, srgb);
                HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
                renderer.setContentRoot(blur);
                ring.renderers[i] = renderer;
            }
        } catch (Throwable t) {
            ring.free();
            throw t;
        }
        return ring;
    }

    @NonNull
    private RenderNode source(int divisor) {
        int index = divisor == 2 ? 0 : 1;
        RenderNode node = mSources[index];
        if (node == null) {
            node = new RenderNode("LiveWallpaper.source.x" + divisor);
            node.setPosition(0, 0, bufferSize(mFrameW, divisor), bufferSize(mFrameH, divisor));
            mSources[index] = node;
        }
        return node;
    }

    /** The wallpaper shader over the whole frame rect, scaled down to the buffer. */
    private void recordSource(int divisor) {
        RenderNode node = source(divisor);
        int bw = bufferSize(mFrameW, divisor);
        int bh = bufferSize(mFrameH, divisor);
        Canvas canvas = node.beginRecording(bw, bh);
        canvas.scale(bw / (float) mFrameW, bh / (float) mFrameH);
        canvas.drawRect(0f, 0f, mFrameW, mFrameH, mPaint);
        node.endRecording();
    }

    private static int bufferSize(int frameSize, int divisor) {
        return Math.max(1, (frameSize + divisor - 1) / divisor);
    }
}
