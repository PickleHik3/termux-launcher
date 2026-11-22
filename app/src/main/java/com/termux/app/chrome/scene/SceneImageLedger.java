package com.termux.app.chrome.scene;

/**
 * Which low-resolution images are still held open, and when the oldest may go back to the
 * producer. A published frame is a hardware bitmap wrapping the image's buffer, which glass draws
 * from on the render thread for a while after the main thread has moved on to a newer frame; the
 * queue may only reuse a buffer once no recorded draw can still read it. So an image is closed
 * only after at least {@code minNewer} frames were published after it <b>and</b> {@code minAgeMs}
 * have passed since the next one replaced it, and the producer is asked to skip a frame
 * ({@link #canProduce}) rather than run the queue dry. Scene thread only; no locks.
 *
 * @param <T> the held thing (an {@code android.media.Image}); {@link Closer} gives it back
 */
final class SceneImageLedger<T> {

    interface Closer<T> {
        void close(T item);
    }

    private final Object[] mItems;
    private final long[] mPublishedAtMs;
    private final int mCapacity;
    private final int mMinNewer;
    private final long mMinAgeMs;
    private final Closer<T> mCloser;
    /** Held items, oldest at index 0; the newest is the one on screen. */
    private int mCount;

    SceneImageLedger(int capacity, int minNewer, long minAgeMs, Closer<T> closer) {
        mCapacity = capacity;
        mItems = new Object[capacity];
        mPublishedAtMs = new long[capacity];
        mMinNewer = minNewer;
        mMinAgeMs = minAgeMs;
        mCloser = closer;
    }

    /** True while one more image can be held; otherwise the next frame is not worth rendering. */
    boolean canProduce() {
        return mCount < mCapacity;
    }

    int held() {
        return mCount;
    }

    /** Takes {@code item} as the frame on screen; the caller checked {@link #canProduce}. */
    @SuppressWarnings("unchecked")
    void publish(T item, long nowMs) {
        if (mCount == mCapacity) {
            // Not meant to happen: give the oldest up rather than overrun.
            mCloser.close((T) mItems[0]);
            shiftLeft();
        }
        mItems[mCount] = item;
        mPublishedAtMs[mCount] = nowMs;
        mCount++;
        reap(nowMs);
    }

    /** Closes the held images that no draw can still be reading. */
    @SuppressWarnings("unchecked")
    void reap(long nowMs) {
        while (mCount > 1 && mCount - 1 >= mMinNewer
            && nowMs - mPublishedAtMs[1] >= mMinAgeMs) {
            mCloser.close((T) mItems[0]);
            shiftLeft();
        }
    }

    /** Surface gone or scene off: everything goes back at once. */
    @SuppressWarnings("unchecked")
    void closeAll() {
        for (int i = 0; i < mCount; i++) {
            mCloser.close((T) mItems[i]);
            mItems[i] = null;
        }
        mCount = 0;
    }

    private void shiftLeft() {
        for (int i = 1; i < mCount; i++) {
            mItems[i - 1] = mItems[i];
            mPublishedAtMs[i - 1] = mPublishedAtMs[i];
        }
        mCount--;
        mItems[mCount] = null;
    }
}
