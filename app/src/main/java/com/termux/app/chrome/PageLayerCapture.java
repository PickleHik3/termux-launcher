package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One picture of a live surface's page layer, rendered offscreen on the GPU as the window draws
 * it ({@link LiveBackdrop}, {@link BackdropLayers.Mode#FROZEN}). Started in a pre-draw and collected by a later
 * one: the render is not waited for, and until it lands the surface keeps its one-layer picture.
 * Once collected only the bitmap is held (its buffer, a quarter-scale layer's bytes); the renderer
 * and the reader are gone.
 *
 * <p>The reader is only ever touched on one background thread ({@link #readerThread}): taking the
 * image waits on the render's GPU fence, which once held the main thread 1.1 s mid-swipe on pong.
 * The image is taken there as soon as it is queued and handed over through a {@link Handoff} that
 * the main thread's poll reads without waiting. A capture that has not landed in
 * {@link #MAX_WAIT_FRAMES} polls is dropped as before, whatever that thread is doing.</p>
 */
@RequiresApi(Build.VERSION_CODES.Q)
final class PageLayerCapture {

    /** Frames a capture may take to land before the swipe goes on without it. */
    static final int MAX_WAIT_FRAMES = 4;

    @Nullable private static Handler sReaderHandler;

    @Nullable private HardwareRenderer mRenderer;
    @Nullable private ImageReader mReader;
    @Nullable private Handoff mHandoff;
    @Nullable private RenderNode mRoot;
    private int mWaited;

    /** Main thread. The one thread every capture's reader is read and closed on; started once. */
    @NonNull
    private static Handler readerThread() {
        if (sReaderHandler == null) {
            HandlerThread thread = new HandlerThread("PageLayerCapture");
            thread.start();
            sReaderHandler = new Handler(thread.getLooper());
        }
        return sReaderHandler;
    }

    /**
     * Renders {@code root} (already recorded, {@code width} x {@code height}) offscreen. False,
     * with nothing held, when it cannot.
     */
    boolean start(@NonNull RenderNode root, int width, int height) {
        cancel();
        ImageReader reader = null;
        HardwareRenderer renderer = null;
        Handoff handoff = new Handoff();
        try {
            Handler readerThread = readerThread();
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            reader.setOnImageAvailableListener(r -> collect(r, handoff), readerThread);
            renderer = new HardwareRenderer();
            renderer.setOpaque(false);
            renderer.setContentRoot(root);
            renderer.setSurface(reader.getSurface());
            renderer.createRenderRequest().syncAndDraw();
        } catch (RuntimeException e) {
            if (renderer != null) renderer.destroy();
            if (reader != null) close(reader, handoff);
            root.discardDisplayList();
            return false;
        }
        mReader = reader;
        mHandoff = handoff;
        mRenderer = renderer;
        mRoot = root;
        mWaited = 0;
        return true;
    }

    /** Whether a capture is under way. */
    boolean pending() {
        return mReader != null;
    }

    /**
     * The captured picture once it has landed, and then nothing is held here any more; null while
     * it is still rendering. A capture that has not landed in {@link #MAX_WAIT_FRAMES} calls, or
     * fails, is dropped: {@link #pending} turns false with null returned. Never waits.
     */
    @Nullable
    Bitmap poll() {
        Handoff handoff = mHandoff;
        if (mReader == null || handoff == null) return null;
        Bitmap bitmap = handoff.take();
        if (bitmap == null) {
            if (handoff.failed() || ++mWaited > MAX_WAIT_FRAMES) cancel();
            return null;
        }
        cancel();
        return bitmap;
    }

    /** Drops a capture under way, and everything it holds. */
    void cancel() {
        if (mRenderer != null) mRenderer.destroy();
        if (mReader != null && mHandoff != null) close(mReader, mHandoff);
        if (mRoot != null) mRoot.discardDisplayList();
        mRenderer = null;
        mReader = null;
        mHandoff = null;
        mRoot = null;
        mWaited = 0;
    }

    /** Reader thread: the queued image, wrapped and handed to the main thread's next poll. */
    private static void collect(@NonNull ImageReader reader, @NonNull Handoff handoff) {
        if (handoff.dropped()) return;
        Image image = null;
        Bitmap bitmap = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            HardwareBuffer buffer = image.getHardwareBuffer();
            if (buffer != null) {
                // The bitmap holds its own reference to the buffer; the reader can go.
                bitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                buffer.close();
            }
        } catch (RuntimeException e) {
            bitmap = null;
        } finally {
            if (image != null) image.close();
        }
        if (bitmap == null) {
            handoff.fail();
        } else if (!handoff.land(bitmap)) {
            bitmap.recycle();
        }
    }

    /**
     * Main thread. Closes {@code reader} on the reader thread, after any collect still running
     * there; a picture that landed and was never taken is released.
     */
    private static void close(@NonNull ImageReader reader, @NonNull Handoff handoff) {
        Bitmap unclaimed = handoff.drop();
        if (unclaimed != null) unclaimed.recycle();
        boolean posted;
        try {
            posted = readerThread().post(() -> {
                Bitmap late = handoff.drop();
                if (late != null) late.recycle();
                reader.close();
            });
        } catch (RuntimeException e) {
            posted = false;
        }
        if (!posted) reader.close();
    }

    /**
     * The one picture a capture yields, passed from the reader thread to the main thread: taken
     * once by the main thread or, once dropped, by nobody. A picture landing after the drop is
     * refused, and whoever made it releases it. Thread-safe.
     */
    static final class Handoff {
        @NonNull private final AtomicReference<Bitmap> mLanded = new AtomicReference<>();
        private volatile boolean mDropped;
        private volatile boolean mFailed;

        /** Reader thread. False when the capture was dropped first: the caller releases it. */
        boolean land(@NonNull Bitmap bitmap) {
            if (mDropped) return false;
            mLanded.set(bitmap);
            // A drop between the check and the set: take it back unless the drop already did.
            return !(mDropped && mLanded.compareAndSet(bitmap, null));
        }

        /** Reader thread: the render came back with nothing to show. */
        void fail() {
            mFailed = true;
        }

        boolean failed() {
            return mFailed;
        }

        boolean dropped() {
            return mDropped;
        }

        /** Main thread: the landed picture, once; null while nothing has landed. */
        @Nullable
        Bitmap take() {
            return mLanded.getAndSet(null);
        }

        /** No one will take it from now on: what had landed and was never taken, to release. */
        @Nullable
        Bitmap drop() {
            mDropped = true;
            return mLanded.getAndSet(null);
        }
    }
}
