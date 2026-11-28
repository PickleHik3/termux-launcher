package com.termux.app.tiling.navigation;

import androidx.annotation.NonNull;

/**
 * A carried window held against the screen's edge keeps the view moving that way (#433; spec
 * "Moving around": across Workspaces too). Within {@code bandPx} of an edge, after the finger has
 * stayed in the band for {@code dwellMs}, each tick moves the view toward that edge by the time
 * since the last, faster the deeper in, up to {@code maxPxPerMs} at the edge itself. Left and right scroll a Workspace's
 * strip; top and bottom move through the places.
 *
 * <p>Pure: the view calls {@link #step} on a short tick while a window is carried.
 */
public final class NavigationEdgePan {

    /** The longest tick counted, in ms: a late tick moves no further than this. */
    static final long MAX_FRAME_MS = 32L;

    private final float mBandPx;
    private final float mMaxPxPerMs;
    private final long mDwellMs;
    /** When the finger came into the band; -1 while it is out. */
    private long mEnteredMs = -1L;
    private long mLastMs;

    public NavigationEdgePan(float bandPx, float maxPxPerMs, long dwellMs) {
        mBandPx = bandPx;
        mMaxPxPerMs = maxPxPerMs;
        mDwellMs = dwellMs;
    }

    /**
     * One tick with the finger at ({@code x}, {@code y}) in a screen from ({@code left},
     * {@code top}) to ({@code right}, {@code bottom}): {@code out} takes how far the view moves,
     * {dx, dy} in px, toward the right and toward the places below when positive. True while the
     * finger is in the band, so the ticks go on.
     */
    public boolean step(float x, float y, float left, float top, float right, float bottom, long nowMs,
                        @NonNull float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        float across = depth(x, left, right);
        float down = depth(y, top, bottom);
        if (across == 0f && down == 0f) {
            mEnteredMs = -1L;
            return false;
        }
        if (mEnteredMs < 0L) {
            mEnteredMs = nowMs;
            mLastMs = nowMs;
            return true;
        }
        long frame = Math.max(0L, Math.min(MAX_FRAME_MS, nowMs - mLastMs));
        mLastMs = nowMs;
        if (nowMs - mEnteredMs < mDwellMs) return true;
        out[0] = across * mMaxPxPerMs * frame;
        out[1] = down * mMaxPxPerMs * frame;
        return true;
    }

    /** The carry ended: the next time in the band waits its dwell again. */
    public void reset() {
        mEnteredMs = -1L;
    }

    /** How deep {@code at} is in the band at {@code from}'s end (negative) or {@code to}'s, 0 to 1. */
    private float depth(float at, float from, float to) {
        if (mBandPx <= 0f) return 0f;
        if (at < from + mBandPx) return -Math.min(1f, (from + mBandPx - at) / mBandPx);
        if (at > to - mBandPx) return Math.min(1f, (at - (to - mBandPx)) / mBandPx);
        return 0f;
    }
}
