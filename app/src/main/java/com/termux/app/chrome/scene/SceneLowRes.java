package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.terminal.FrameProbe;

/**
 * The scene rendered a second time at {@link #DIVISOR} of the screen per axis into an offscreen
 * target, blurred, and published as {@link LiveSceneFrame} for the glass surfaces to sample. The
 * target is an {@link ImageReader} surface drawn by a {@link HardwareRenderer}: the same Contour
 * program on a RenderNode. Scene thread only (its handler is the scene thread's).
 *
 * <p>Blur: a {@link RenderEffect} blur on the node, so it runs on the GPU at 1/8 size, keyed to the
 * radius glass currently asks the blur cache for ({@link LiveSceneFrame#blurRadiusDp()}): sigma is
 * half the radius in pixels of this pass, which reads close to the stock blur's frost (eyeballed on
 * paper, not measured). The 8x bilinear upscale in the glass draw softens it further.</p>
 *
 * <p>Image lifetime: each new image is acquired ({@code acquireLatestImage}, older queued ones are
 * dropped), its {@link HardwareBuffer} wrapped by {@link Bitmap#wrapHardwareBuffer} (no pixel
 * copy; the bitmap holds its own reference to the buffer), and the {@link Image} is <i>kept open</i>
 * so the queue cannot hand that buffer back to the producer while glass may still sample it. A
 * {@link SceneImageLedger} closes the oldest only after {@link #MIN_NEWER} newer frames and
 * {@link #MIN_AGE_MS} since they replaced it (draws recorded for the old frame have long been
 * on the GPU by then), and a pass is skipped, not queued, while {@link #HELD} images are held. The
 * bitmaps are never recycled: dropping the reference is enough, and a glass surface that still
 * names one only ever reads {@link LiveSceneFrame#current()}.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class SceneLowRes {

    /** Pixels per axis at which the glass reads the scene: one eighth. */
    static final int DIVISOR = 8;
    /** Images held open at most: the frame on screen and those not yet safe to give back. */
    static final int HELD = 4;
    /** One more than {@link #HELD}, so the producer always has a buffer to draw into. */
    private static final int MAX_IMAGES = HELD + 1;
    static final int MIN_NEWER = 2;
    static final long MIN_AGE_MS = 100L;

    private static final FrameProbe.Source GLASS = FrameProbe.source("sceneGlass");

    @NonNull private final ContourProgram mProgram = new ContourProgram(1f / DIVISOR);
    @NonNull private final Paint mPaint = new Paint();
    @NonNull private final RenderNode mNode = new RenderNode("SceneLowRes");
    @NonNull private final HardwareRenderer mRenderer = new HardwareRenderer();
    @NonNull private final ImageReader mReader;
    @NonNull private final SceneImageLedger<Image> mLedger =
        new SceneImageLedger<>(HELD, MIN_NEWER, MIN_AGE_MS, Image::close);
    private final int mWidth, mHeight;
    private long mFrames;
    private int mBlurDp = -1;
    private boolean mClosed;
    private boolean mEventsSet;

    SceneLowRes(int screenWidth, int screenHeight, @NonNull Handler sceneHandler) {
        mWidth = Math.max(1, screenWidth / DIVISOR);
        mHeight = Math.max(1, screenHeight / DIVISOR);
        mReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, MAX_IMAGES,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        mReader.setOnImageAvailableListener(this::onImage, sceneHandler);
        mPaint.setShader(mProgram.shader);
        mNode.setPosition(0, 0, mWidth, mHeight);
        mRenderer.setName("SceneLowRes");
        mRenderer.setOpaque(true);
        mRenderer.setContentRoot(mNode);
        mRenderer.setSurface(mReader.getSurface());
    }

    /** Renders one frame; the caller has set nothing, the program is brought up to date here. */
    void render(@NonNull SceneEvents events, long nowMs, long timeMs, float density,
                boolean eventsStale) {
        if (mClosed) return;
        mLedger.reap(SystemClock.uptimeMillis());
        if (!mLedger.canProduce()) return;
        Trace.beginSection("Scene.contour.lowres");
        try {
            mProgram.setSize(mWidth, mHeight, density);
            mProgram.setTime(timeMs);
            if (eventsStale || !mEventsSet) {
                mProgram.setEvents(events, nowMs, density);
                mEventsSet = true;
            }
            syncBlur(density);
            Canvas c = mNode.beginRecording(mWidth, mHeight);
            c.drawRect(0f, 0f, mWidth, mHeight, mPaint);
            mNode.endRecording();
            mRenderer.createRenderRequest().syncAndDraw();
            mFrames++;
        } finally {
            Trace.endSection();
        }
    }

    /** The node's blur follows the radius glass asks for; changed only when that moves. */
    private void syncBlur(float density) {
        int dp = LiveSceneFrame.blurRadiusDp();
        if (dp == mBlurDp) return;
        mBlurDp = dp;
        float sigma = dp * density / DIVISOR / 2f;
        mNode.setRenderEffect(sigma < 0.3f ? null
            : RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP));
    }

    /** Scene thread: a frame landed. Wrap its buffer, keep the image open, publish. */
    private void onImage(@NonNull ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        if (mClosed || !mLedger.canProduce()) {
            image.close();
            return;
        }
        Trace.beginSection("Scene.glass.publish");
        try {
            HardwareBuffer buffer = image.getHardwareBuffer();
            Bitmap bitmap = buffer == null ? null : Bitmap.wrapHardwareBuffer(buffer, null);
            // The bitmap holds its own reference to the buffer; this handle is done with.
            if (buffer != null) buffer.close();
            if (bitmap == null) {
                image.close();
                return;
            }
            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
            shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            mLedger.publish(image, SystemClock.uptimeMillis());
            LiveSceneFrame.publish(bitmap, shader);
            GLASS.tick();
        } finally {
            Trace.endSection();
        }
    }

    long framesRendered() {
        return mFrames;
    }

    void close() {
        if (mClosed) return;
        mClosed = true;
        LiveSceneFrame.retire();
        mRenderer.destroy();
        mLedger.closeAll();
        mReader.close();
        mNode.discardDisplayList();
    }
}
