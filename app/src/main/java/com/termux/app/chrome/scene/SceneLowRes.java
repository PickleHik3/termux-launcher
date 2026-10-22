package com.termux.app.chrome.scene;

import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The scene rendered a second time at {@link #DIVISOR} of the screen per axis into an offscreen
 * target, for the glass surfaces to sample. The target is an {@link ImageReader} surface drawn by
 * a {@link HardwareRenderer}: the same Contour program on a RenderNode, so the GPU cost is the
 * real cost of a low-resolution second pass at the scene's rate.
 *
 * <p>Prototype status: the result is <b>not</b> wired to the glass. Every glass surface samples
 * the blur cache's still frame through a per-surface {@code BitmapShader} (see
 * {@code SharedFrameDrawable}); the images produced here are acquired and closed so the producer
 * never stalls on a full queue, and {@link #framesRendered} counts them. What wiring it would
 * take is in the commit message and the report.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class SceneLowRes {

    /** Pixels per axis at which the glass reads the scene: one eighth. */
    static final int DIVISOR = 8;

    @NonNull private final ContourProgram mProgram = new ContourProgram(1f / DIVISOR);
    @NonNull private final Paint mPaint = new Paint();
    @NonNull private final RenderNode mNode = new RenderNode("SceneLowRes");
    @NonNull private final HardwareRenderer mRenderer = new HardwareRenderer();
    @NonNull private final ImageReader mReader;
    private final int mWidth, mHeight;
    private long mFrames;
    private boolean mClosed;
    private boolean mEventsSet;

    SceneLowRes(int screenWidth, int screenHeight) {
        mWidth = Math.max(1, screenWidth / DIVISOR);
        mHeight = Math.max(1, screenHeight / DIVISOR);
        mReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 3,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        mReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) image.close();
        }, new Handler(Looper.getMainLooper()));
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
        Trace.beginSection("Scene.contour.lowres");
        try {
            mProgram.setSize(mWidth, mHeight, density);
            mProgram.setTime(timeMs);
            if (eventsStale || !mEventsSet) {
                mProgram.setEvents(events, nowMs, density);
                mEventsSet = true;
            }
            Canvas c = mNode.beginRecording(mWidth, mHeight);
            c.drawRect(0f, 0f, mWidth, mHeight, mPaint);
            mNode.endRecording();
            mRenderer.createRenderRequest().syncAndDraw();
            mFrames++;
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
        mRenderer.destroy();
        mReader.close();
        mNode.discardDisplayList();
    }
}
