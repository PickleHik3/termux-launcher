package com.termux.app.chrome.scene;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The two flags of the live GIF path. A hardware canvas renders asynchronously, so a frame is drawn
 * (posted) when the drawable asked for a repaint ({@code due}) and taken into the texture only once
 * the SurfaceTexture reports a buffer ({@code pending}). Pure state, no framework calls.
 */
final class GifFlow {
    private volatile boolean mDue;
    private boolean mAwaiting;
    private final AtomicInteger mPending = new AtomicInteger();

    /** The drawable wants to be drawn again. */
    void markDue() {
        mDue = true;
    }

    /** The SurfaceTexture has a new buffer. */
    void onFrameAvailable() {
        mPending.incrementAndGet();
    }

    /** True when a repaint is due and no posted frame is still on its way. */
    boolean shouldDraw() {
        return mDue && !mAwaiting;
    }

    /** A frame was posted: the repaint is answered, now wait for its buffer. */
    void posted() {
        mDue = false;
        mAwaiting = true;
    }

    /** The draw threw: nothing is on its way. */
    void drawFailed() {
        mDue = false;
    }

    /** True when a posted frame's buffer has arrived. */
    boolean shouldTake() {
        return mAwaiting && mPending.get() > 0;
    }

    /** The buffer was taken in (or could not be). */
    void taken() {
        mPending.set(0);
        mAwaiting = false;
    }

    /** True when update() would do something: draw a due frame, or take in an arrived one. */
    boolean waiting() {
        return mAwaiting ? mPending.get() > 0 : mDue;
    }
}
