package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Paint;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws a living still's effects map ({@link LivingStill#effectsAgsl()}) off the UI thread, at
 * {@link LivingStill#EFFECTS_DIVISOR} of the frame per side, into a ring of {@link LiveRadii#RING}
 * RGBA_8888 hardware buffers that the composite program then samples as a child. Shared by the home
 * {@link LiveWallpaperRenderer} and the lock engine, so the two split the render the same way.
 *
 * <p>Two ways to use it. The renderer calls {@link #draw} for a slot and waits for {@code done}
 * (the buffer written, fence waited) before it draws anything that samples the map, so its glass
 * sources see the very tick's map. The lock engine calls {@link #advance} each frame and
 * {@link #bindLatest}: it draws the composite with the newest map that has finished, one tick
 * behind, and never waits on the GPU on the main thread (the map is smooth and slow, so a tick of
 * lag does not show). The ring is three deep so the slot being written was last read two ticks
 * ago.</p>
 *
 * <p>All methods run on the main thread; the {@code done} callback of {@link #draw} runs on this
 * class's fence thread. Nothing is allocated per frame.</p>
 */
@RequiresApi(34)
final class LivingEffectsRenderer {

    /** Called on the fence thread when the slot's buffer is written (true) or the draw failed. */
    interface Done {
        void onDone(boolean ok);
    }

    @NonNull private final RuntimeShader mShader;
    @NonNull private final Paint mPaint = new Paint();
    @NonNull private final RenderNode mNode = new RenderNode("LivingEffects");
    private final HardwareBuffer[] mBuffers = new HardwareBuffer[LiveRadii.RING];
    private final Bitmap[] mBitmaps = new Bitmap[LiveRadii.RING];
    private final BitmapShader[] mShaders = new BitmapShader[LiveRadii.RING];
    private final HardwareBufferRenderer[] mRenderers = new HardwareBufferRenderer[LiveRadii.RING];
    @Nullable private ExecutorService mExecutor;
    private int mFrameW;
    private int mFrameH;
    private int mWidth;
    private int mHeight;
    private volatile boolean mBusy;
    private volatile boolean mReleased;
    /** The newest slot whose buffer is fully written, for {@link #bindLatest}; -1 for none. */
    private volatile int mLatest = -1;

    /** @param shader this user's own effects shader ({@link WallpaperUniforms#newEffectsShader}) */
    LivingEffectsRenderer(@NonNull RuntimeShader shader) {
        mShader = shader;
        mPaint.setShader(shader);
    }

    /** True while a draw has not finished. Resizing or releasing waits for it. */
    boolean busy() {
        return mBusy;
    }

    int width() {
        return mWidth;
    }

    int height() {
        return mHeight;
    }

    /**
     * Makes the ring match a {@code frameW} x {@code frameH} frame, rebuilding it when the size
     * changed. Call it only while not {@link #busy}.
     */
    void ensure(int frameW, int frameH) {
        if (mReleased || frameW <= 0 || frameH <= 0) return;
        if (frameW == mFrameW && frameH == mFrameH && mBuffers[0] != null) return;
        free();
        mFrameW = frameW;
        mFrameH = frameH;
        mWidth = size(frameW);
        mHeight = size(frameH);
        mNode.setPosition(0, 0, mWidth, mHeight);
        ColorSpace srgb = ColorSpace.get(ColorSpace.Named.SRGB);
        long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        try {
            for (int i = 0; i < LiveRadii.RING; i++) {
                mBuffers[i] = HardwareBuffer.create(mWidth, mHeight, HardwareBuffer.RGBA_8888, 1, usage);
                mBitmaps[i] = Bitmap.wrapHardwareBuffer(mBuffers[i], srgb);
                mShaders[i] = WallpaperUniforms.linearShader(mBitmaps[i]);
                mRenderers[i] = new HardwareBufferRenderer(mBuffers[i]);
                mRenderers[i].setContentRoot(mNode);
            }
        } catch (Throwable t) {
            free();
            throw t;
        }
    }

    private static int size(int frameSize) {
        return Math.max(1, (frameSize + LivingStill.EFFECTS_DIVISOR - 1) / LivingStill.EFFECTS_DIVISOR);
    }

    /**
     * Draws {@code f}'s map into {@code slot}; {@code done} follows on the fence thread once the
     * buffer is written. Throws when the draw cannot be started.
     */
    void draw(@NonNull WallpaperDirector.Frame f, int slot, @NonNull Done done) {
        if (mReleased || mBusy || mBuffers[slot] == null) {
            throw new IllegalStateException("The effects ring is not ready");
        }
        WallpaperUniforms.applyEffects(mShader, f, mFrameW, mFrameH);
        Canvas canvas = mNode.beginRecording(mWidth, mHeight);
        canvas.scale(mWidth / (float) mFrameW, mHeight / (float) mFrameH);
        canvas.drawRect(0f, 0f, mFrameW, mFrameH, mPaint);
        mNode.endRecording();
        ExecutorService executor = mExecutor;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "LivingEffects.fence"));
            mExecutor = executor;
        }
        mBusy = true;
        try {
            mRenderers[slot].obtainRenderRequest()
                .setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                .draw(executor, result -> onDrawn(result, done));
        } catch (Throwable t) {
            mBusy = false;
            throw t;
        }
    }

    /** Fence thread: wait for the buffer, then report. */
    private void onDrawn(@NonNull HardwareBufferRenderer.RenderResult result, @NonNull Done done) {
        boolean ok;
        try {
            ok = result.getStatus() == HardwareBufferRenderer.RenderResult.SUCCESS;
            SyncFence fence = result.getFence();
            try {
                if (fence.isValid()) fence.awaitForever();
            } finally {
                fence.close();
            }
        } catch (Throwable t) {
            ok = false;
        }
        mBusy = false;
        if (mReleased) {
            free();
            return;
        }
        done.onDone(ok);
    }

    /** Binds {@code slot}'s map to {@code composite}'s {@code uEffects}. */
    void bind(@NonNull RuntimeShader composite, int slot) {
        BitmapShader map = mShaders[slot];
        if (map == null) {
            WallpaperUniforms.setEffects(composite, null, 1, 1);
        } else {
            WallpaperUniforms.setEffects(composite, map, mWidth, mHeight);
        }
    }

    // --- the lock engine's way: one tick behind, no waiting ---

    /**
     * Starts drawing {@code f}'s map unless the previous one is still being drawn. The slot it
     * writes is the one after the newest finished, so {@link #bindLatest} never reads a buffer
     * under way.
     */
    void advance(@NonNull WallpaperDirector.Frame f, int frameW, int frameH) {
        if (mReleased || mBusy) return;
        if (frameW != mFrameW || frameH != mFrameH || mBuffers[0] == null) {
            mLatest = -1;
            ensure(frameW, frameH);
        }
        final int slot = (mLatest + 1) % LiveRadii.RING;
        draw(f, slot, ok -> {
            if (ok) mLatest = slot;
        });
    }

    /** Binds the newest finished map to {@code composite}, the neutral one when none has. */
    void bindLatest(@NonNull RuntimeShader composite) {
        int slot = mLatest;
        if (slot < 0 || mBuffers[slot] == null) {
            WallpaperUniforms.setEffects(composite, null, 1, 1);
        } else {
            bind(composite, slot);
        }
    }

    /** Lets go of the ring; deferred to the end of a draw still under way. */
    void release() {
        if (mReleased) return;
        mReleased = true;
        ExecutorService executor = mExecutor;
        mExecutor = null;
        if (!mBusy) free();
        if (executor != null) executor.shutdown();
    }

    private void free() {
        mLatest = -1;
        for (int i = 0; i < LiveRadii.RING; i++) {
            mShaders[i] = null;
            if (mRenderers[i] != null) {
                try { mRenderers[i].close(); } catch (Throwable ignored) { }
                mRenderers[i] = null;
            }
            if (mBitmaps[i] != null) {
                mBitmaps[i].recycle();
                mBitmaps[i] = null;
            }
            if (mBuffers[i] != null) {
                try { mBuffers[i].close(); } catch (Throwable ignored) { }
                mBuffers[i] = null;
            }
        }
        mFrameW = 0;
        mFrameH = 0;
    }
}
