package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.terminal.FrameProbe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The scene at 1/{@code divisor} of the screen per axis, blurred, and published as
 * {@link LiveSceneFrame} for the glass surfaces to sample. The target is an {@link ImageReader}
 * whose surface is an EGL window surface of the scene's own GL context: {@link SceneGl} blurs the
 * frame's texture horizontally into a low-res texture, then vertically onto this surface, and the
 * swap queues the image. Scene thread only (its handler is the scene thread's).
 *
 * <p>Blur: separable Gaussian on the GPU at 1/divisor size, keyed to the radius glass currently
 * asks the blur cache for ({@link LiveSceneFrame#blurRadiusDp()}): sigma is half the radius in
 * pixels of this pass, the same number the {@code RenderEffect} blur of the AGSL scenes used. The
 * 8x bilinear upscale in the glass draw softens it further.</p>
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
@RequiresApi(Build.VERSION_CODES.Q)
final class SceneLowRes {

    /** The Look's default glass blur ({@code DEFAULT_SURFACE_BASE_BLUR}), used while no glass has asked. */
    static final int FALLBACK_BLUR_DP = TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR;
    /** Images held open at most: the frame on screen and those not yet safe to give back. */
    static final int HELD = 4;
    /** One more than {@link #HELD}, so the producer always has a buffer to draw into. */
    private static final int MAX_IMAGES = HELD + 1;
    static final int MIN_NEWER = 2;
    static final long MIN_AGE_MS = 100L;

    /** Pooled software bitmaps the soft path rotates through. */
    static final int POOL = 3;
    private static final String TAG = "SceneLowRes";

    private static final FrameProbe.Source GLASS = FrameProbe.source("sceneGlass");

    @NonNull private final SceneEgl mEgl;
    @NonNull private final ImageReader mReader;
    @Nullable private final EGLSurface mSurface;
    @NonNull private final SceneImageLedger<Image> mLedger =
        new SceneImageLedger<>(HELD, MIN_NEWER, MIN_AGE_MS, Image::close);
    private final int mWidth, mHeight;
    /** Screen pixels per low-res pixel, per axis: 2, 4 or 8 ({@code debug.tl.scene_glass_div}). */
    private final int mDivisor;
    /** Soft path: the reader's images are CPU readable, so no GPU readback is needed. */
    private boolean mCpuReadable;
    @NonNull private final SceneSoftPool mPool = new SceneSoftPool(POOL);
    @NonNull private final Bitmap[] mPoolBitmaps = new Bitmap[POOL];
    @NonNull private final BitmapShader[] mPoolShaders = new BitmapShader[POOL];
    @Nullable private ByteBuffer mStaging;
    private long mFrames;
    private boolean mClosed;

    SceneLowRes(int screenWidth, int screenHeight, int divisor, @NonNull Handler sceneHandler,
                @NonNull SceneEgl egl) {
        mEgl = egl;
        mDivisor = divisor;
        mWidth = Math.max(1, screenWidth / divisor);
        mHeight = Math.max(1, screenHeight / divisor);
        ImageReader reader = null;
        if ("soft".equals(COPY.get(SystemClock.uptimeMillis()))) {
            try {
                // The listener fires once the producer's fence has signalled, so a delivered
                // image is rendered and can be read on the CPU without waiting for the GPU.
                reader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888,
                    MAX_IMAGES, HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                        | HardwareBuffer.USAGE_CPU_READ_OFTEN);
                mCpuReadable = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "CPU-readable scene reader refused, glass falls back to Bitmap.copy", e);
            }
        }
        if (reader == null) {
            reader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, MAX_IMAGES,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        }
        mReader = reader;
        mReader.setOnImageAvailableListener(this::onImage, sceneHandler);
        mSurface = egl.windowSurface(mReader.getSurface());
        if (mSurface == null) Log.w(TAG, "EGL refused the glass reader's surface; glass keeps the still");
    }

    /**
     * Renders one glass frame from the scene texture {@code gl} has just drawn. The context may end
     * current on this pass's surface; the caller makes its own current again.
     */
    void render(@NonNull SceneGl gl, float density) {
        if (mClosed || mSurface == null) return;
        mLedger.reap(SystemClock.uptimeMillis());
        if (!mLedger.canProduce()) return;
        Trace.beginSection("Scene.glass.render");
        try {
            float sigma = sigmaPx(LiveSceneFrame.blurRadiusDp(), density, mDivisor);
            gl.blurSceneHorizontally(mWidth, mHeight, sigma);
            if (!mEgl.makeCurrent(mSurface)) return;
            gl.blurLowVertically(mWidth, mHeight, sigma);
            mEgl.swap(mSurface);
            mFrames++;
        } finally {
            Trace.endSection();
        }
    }

    /** The blur's sigma in pixels of this pass for a glass radius of {@code dp} (0: the Look's default). */
    static float sigmaPx(int dp, float density, int divisor) {
        // Glass has not asked for a radius yet: blur at the Look's default, never hand it raw texels.
        if (dp <= 0) dp = FALLBACK_BLUR_DP;
        return dp * density / divisor / 2f;
    }

    /**
     * {@code debug.tl.scene_glass_copy}: {@code soft} (default) reads each rendered frame on the
     * CPU (the reader's buffers are CPU readable, so no GPU wait) into one of {@link #POOL}
     * reused software bitmaps; {@code copy} wraps the hardware buffer and {@code Bitmap.copy}s it
     * (a blocking GPU readback, a new bitmap per frame); {@code hw} hands glass the hardware
     * buffer itself. The reader's usage is chosen from the value when the pass is created.
     * A hardware bitmap on the window's glass kept the UI thread waiting for the whole render on
     * every frame (latency study, "Glass reading the scene"); the still frame, a software bitmap,
     * does not.
     */
    private static final com.termux.terminal.DebugChoice COPY = new com.termux.terminal.DebugChoice(
        "debug.tl.scene_glass_copy", "soft", com.termux.terminal.DebugSwitch.SYSTEM_PROPERTIES);

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
            if (mCpuReadable && "soft".equals(COPY.get(SystemClock.uptimeMillis()))) {
                if (publishPooled(image)) return;
                // Unreadable plane: this device cannot do it; copy as before from now on.
                mCpuReadable = false;
                return;
            }
            HardwareBuffer buffer = image.getHardwareBuffer();
            Bitmap bitmap = buffer == null ? null : Bitmap.wrapHardwareBuffer(buffer, null);
            // The bitmap holds its own reference to the buffer; this handle is done with.
            if (buffer != null) buffer.close();
            if (bitmap == null) {
                image.close();
                return;
            }
            if (!"hw".equals(COPY.get(SystemClock.uptimeMillis()))) {
                // A software copy: the image goes back to the reader at once, nothing to track.
                Bitmap soft = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                image.close();
                if (soft == null) return;
                BitmapShader softShader = new BitmapShader(soft, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
                softShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                LiveSceneFrame.publish(soft, softShader);
                GLASS.tick();
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

    /**
     * Soft path: the rendered image's pixels into the next pooled bitmap, the image closed at once.
     * False only when the plane cannot be read (the frame is dropped, the caller switches to the
     * copy path for the next ones); a frame with no safe slot is dropped and returns true. The
     * image is closed on every path.
     */
    private boolean publishPooled(@NonNull Image image) {
        int slot = mPool.acquire();
        if (slot < 0) {
            image.close();
            return true;
        }
        try {
            Trace.beginSection("Scene.glass.copy");
            try {
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer src = plane.getBuffer();
                int rowStride = plane.getRowStride();
                int pixelStride = plane.getPixelStride();
                Bitmap bitmap = mPoolBitmaps[slot];
                if (bitmap == null) {
                    // Once per slot: the bitmap and the shader over it live as long as the pass.
                    bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                    BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                        Shader.TileMode.CLAMP);
                    shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                    mPoolBitmaps[slot] = bitmap;
                    mPoolShaders[slot] = shader;
                }
                if (pixelStride == 4 && rowStride == mWidth * 4) {
                    src.rewind();
                    bitmap.copyPixelsFromBuffer(src);
                } else {
                    if (mStaging == null) {
                        mStaging = ByteBuffer.allocateDirect(mWidth * mHeight * 4)
                            .order(ByteOrder.nativeOrder());
                    }
                    SceneSoftPool.copyPacked(src, rowStride, pixelStride, mWidth, mHeight, mStaging);
                    bitmap.copyPixelsFromBuffer(mStaging);
                }
            } finally {
                Trace.endSection();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "reading the scene image on the CPU failed, falling back to Bitmap.copy", e);
            image.close();
            return false;
        }
        image.close();
        mPool.published(slot);
        LiveSceneFrame.publish(mPoolBitmaps[slot], mPoolShaders[slot]);
        GLASS.tick();
        return true;
    }

    int divisor() {
        return mDivisor;
    }

    long framesRendered() {
        return mFrames;
    }

    /** Lets go of the EGL surface, the held images and the reader; the context stays. */
    void close() {
        if (mClosed) return;
        mClosed = true;
        LiveSceneFrame.retire();
        // The producer goes first, so the reader is never closed under a connected EGL surface.
        mEgl.destroySurface(mSurface);
        mLedger.closeAll();
        mReader.close();
    }
}
