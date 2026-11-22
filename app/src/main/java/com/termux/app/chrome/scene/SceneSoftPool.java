package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

/**
 * The pure parts of the soft path: which pooled bitmap the next frame is written into, and the
 * copy of a possibly row-padded RGBA plane into a packed buffer. No Android class, so it is tested
 * on the JVM.
 *
 * <p>Rotation: slots are taken round-robin, but never the one glass currently draws ({@code
 * current}) nor the one before it ({@code previous}): a draw recorded for the previous frame may
 * still be on the render thread. With three slots exactly one slot is left, so the guard never
 * fires; it returns -1 only if the pool is smaller than that.</p>
 */
final class SceneSoftPool {

    private final int mSize;
    private int mNext;
    private int mCurrent = -1;
    private int mPrevious = -1;

    SceneSoftPool(int size) {
        mSize = size;
    }

    /** The slot to write next, or -1 if none is safe (skip this frame). Does not publish it. */
    int acquire() {
        for (int i = 0; i < mSize; i++) {
            int slot = (mNext + i) % mSize;
            if (slot != mCurrent && slot != mPrevious) {
                mNext = (slot + 1) % mSize;
                return slot;
            }
        }
        return -1;
    }

    /** The slot just acquired was written and handed to glass. */
    void published(int slot) {
        mPrevious = mCurrent;
        mCurrent = slot;
    }

    int current() {
        return mCurrent;
    }

    /**
     * Copies a {@code width x height} RGBA_8888 plane into {@code dst} packed (stride width * 4),
     * byte order unchanged: an Android ARGB_8888 bitmap is R,G,B,A in memory, the same as the
     * RGBA_8888 plane. {@code src} is read by absolute index and left untouched; {@code dst} is
     * rewritten from 0 and left with position 0, limit width * height * 4.
     */
    static void copyPacked(@NonNull ByteBuffer src, int rowStride, int pixelStride, int width,
                           int height, @NonNull ByteBuffer dst) {
        int row = width * 4;
        dst.clear();
        if (pixelStride == 4) {
            ByteBuffer s = src.duplicate();
            for (int y = 0; y < height; y++) {
                int from = y * rowStride;
                s.limit(from + row).position(from);
                dst.put(s);
            }
        } else {
            for (int y = 0; y < height; y++) {
                int from = y * rowStride;
                for (int x = 0; x < width; x++) {
                    int p = from + x * pixelStride;
                    dst.put(src.get(p)).put(src.get(p + 1)).put(src.get(p + 2)).put(src.get(p + 3));
                }
            }
        }
        dst.flip();
    }
}
