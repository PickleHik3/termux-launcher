/*
 * Adapted from kitty/cursor_trail.c, Copyright Kovid Goyal and kitty contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Modified for Termux Launcher: Java/Android adaptation with pane geometry and snap handling.
 * See the repository LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.termux.view;

/**
 * A line-for-line port of kitty's {@code cursor_trail.c}: the "trail" that chases the text cursor's
 * shape rect around the screen, its four corners arriving at different rates so the quad shears
 * along the direction of travel instead of sliding as a rigid copy.
 * <p>
 * Generalised from kitty's one-per-OS-window trail to any moving target rect, so the same instance
 * can track a cursor jumping inside one pane or focus moving from one pane to another — to the
 * engine both are just the target rect changing. It knows nothing about Android, cells, panes or
 * pixels beyond the plain floats it is handed, so it can be driven and tested on a bare JVM.
 * <p>
 * Every method here mirrors a function of the same shape in kitty's source, kept close enough to
 * read side by side with it: {@link #update} is {@code update_cursor_trail}, the corner law in
 * {@link #updateCorners} is {@code update_cursor_trail_corners}, and so on. The one deliberate
 * addition is {@link #requestSnapOnNextUpdate()}, which stands in for kitty's own
 * {@code live_resize.in_progress} skip test: kitty snaps its trail during a live resize because the
 * geometry underneath it is changing for a reason that has nothing to do with the cursor, and a
 * pane-to-pane focus jump driven by a window switch is the same kind of discontinuity — the caller
 * asks for one snap instead of turning the whole update off.
 */
public final class KittyCursorTrail {

    /** How many corners the trail's quad has; always four, but named for the loops below. */
    public static final int CORNERS = 4;

    /**
     * A stalled render loop (backgrounded app, dropped frames) must not be integrated as one huge
     * step once it resumes, or the trail would visibly teleport across the screen. Matches the
     * clamp the previous per-pane implementations each applied at their call site; owned by the
     * engine now that there is only one of them.
     */
    private static final float MAX_DT_SECONDS = 1f / 20f;

    /**
     * Which edge of the target rect each corner tracks, exactly kitty's {@code corner_index[2][4]}.
     * Corner 0 is top-right, 1 bottom-right, 2 bottom-left, 3 top-left — the winding does not
     * matter to the engine itself, only that {@link #cornerX} and {@link #cornerY} agree with it.
     */
    private static final int[] CORNER_X_EDGE = {1, 1, 0, 0};
    private static final int[] CORNER_Y_EDGE = {0, 1, 1, 0};

    /** Below this, a corner is considered to have arrived; guards a division by a near-zero delta. */
    private static final float EPSILON = 1e-6f;

    /**
     * The tunables of {@code cursor_trail}/{@code cursor_trail_decay}/{@code cursor_trail_start_threshold}.
     * {@code color} is carried only as data for whoever draws the trail; the engine itself never
     * reads it.
     */
    public static final class Config {
        public final long delayMs;
        public final float decayFast;
        public final float decaySlow;
        public final int thresholdXCells;
        public final int thresholdYCells;
        public final boolean hasColor;
        public final int color;

        public Config(long delayMs, float decayFast, float decaySlow,
                      int thresholdXCells, int thresholdYCells) {
            this(delayMs, decayFast, decaySlow, thresholdXCells, thresholdYCells, false, 0);
        }

        public Config(long delayMs, float decayFast, float decaySlow,
                      int thresholdXCells, int thresholdYCells, boolean hasColor, int color) {
            this.delayMs = delayMs;
            this.decayFast = decayFast;
            this.decaySlow = decaySlow;
            this.thresholdXCells = thresholdXCells;
            this.thresholdYCells = thresholdYCells;
            this.hasColor = hasColor;
            this.color = color;
        }
    }

    // The target's edges, in the caller's pixel space. left <= right, top <= bottom.
    private float mEdgeLeft, mEdgeTop, mEdgeRight, mEdgeBottom;

    // The quad's four corners, indexed as CORNER_X_EDGE/CORNER_Y_EDGE describe.
    private final float[] mCornerX = new float[CORNERS];
    private final float[] mCornerY = new float[CORNERS];
    // Scratch for one frame's per-corner deltas and alignments; instance fields so a frame draws
    // no arrays of its own.
    private final float[] mScratchDx = new float[CORNERS];
    private final float[] mScratchDy = new float[CORNERS];
    private final float[] mScratchDot = new float[CORNERS];

    private float mOpacity;
    private boolean mNeedsRender;
    /** False until the first {@link #update} call has placed the corners anywhere at all. */
    private boolean mCornersInitialized;
    private boolean mHasPreviousFrame;
    private long mPreviousFrameMillis;
    private boolean mSnapPending;

    /** Forget everything: the next {@link #update} snaps to its target with no visible trail. */
    public void reset() {
        mOpacity = 0f;
        mNeedsRender = false;
        mCornersInitialized = false;
        mHasPreviousFrame = false;
        mSnapPending = false;
    }

    /**
     * The next {@link #update} snaps every corner straight to the target, once, instead of
     * animating the usual smear. For a discontinuity that is not a live cursor move — kitty's own
     * example is a live resize — but that should not simply be dropped as {@link #reset()} would:
     * the trail keeps its opacity and keeps following, it just does not draw a streak across the
     * jump itself.
     */
    public void requestSnapOnNextUpdate() {
        mSnapPending = true;
    }

    /**
     * Advance the trail by one frame.
     *
     * @param nowMillis                 the current time, any monotonic millisecond clock
     * @param targetLeft/Top/Right/Bottom the cursor's current shape rect, in the caller's pixels
     * @param dectcemOn                  whether the terminal program is showing a cursor at all
     * @param positionChangedAtMillis    when the client last actually moved the cursor
     * @param paused                     true while rendering is paused (kitty's live-resize skip
     *                                   folds into this too: the caller simply does not advance the
     *                                   target while a resize is in progress)
     * @return true if another frame should be scheduled: a corner is still catching up, or one just
     *         stopped needing to (kitty's own {@code needs_render || needs_render_prev}, so the
     *         final settled frame still repaints once), or the trail is only waiting out the
     *         {@code cursor_trail} delay before it may pick up the new target at all.
     */
    public boolean update(long nowMillis, float targetLeft, float targetTop,
                          float targetRight, float targetBottom, boolean dectcemOn,
                          long positionChangedAtMillis, boolean paused,
                          float cellWidthPx, float cellHeightPx, Config config) {
        boolean pendingDelay = false;
        if (!paused) {
            long sinceMoved = nowMillis - positionChangedAtMillis;
            if (sinceMoved >= config.delayMs) {
                updateTarget(targetLeft, targetTop, targetRight, targetBottom);
            } else {
                pendingDelay = true;
            }
        }

        float dt = 0f;
        if (mHasPreviousFrame) {
            dt = (nowMillis - mPreviousFrameMillis) / 1000f;
            if (dt < 0f) dt = 0f;
            if (dt > MAX_DT_SECONDS) dt = MAX_DT_SECONDS;
        }

        boolean forceSnap = mSnapPending;
        mSnapPending = false;
        updateCorners(dt, dectcemOn, cellWidthPx, cellHeightPx, config, forceSnap);
        updateOpacity(dt, dectcemOn, config);

        boolean needsRenderPrev = mNeedsRender;
        updateNeedsRender(cellWidthPx, cellHeightPx);

        mPreviousFrameMillis = nowMillis;
        mHasPreviousFrame = true;

        return mNeedsRender || needsRenderPrev || pendingDelay;
    }

    private void updateTarget(float left, float top, float right, float bottom) {
        mEdgeLeft = left;
        mEdgeTop = top;
        mEdgeRight = right;
        mEdgeBottom = bottom;
    }

    /** {@code should_skip_cursor_trail_update}: whether the corners should snap rather than ease. */
    private boolean shouldSkipUpdate(boolean dectcemOn, float cellWidthPx, float cellHeightPx,
                                     Config config) {
        if (!dectcemOn && mOpacity <= 0f) return true;
        if ((config.thresholdXCells > 0 || config.thresholdYCells > 0) && !mNeedsRender
            && cellWidthPx > 0f && cellHeightPx > 0f) {
            int dx = Math.round((mCornerX[0] - mEdgeRight) / cellWidthPx);
            int dy = Math.round((mCornerY[0] - mEdgeTop) / cellHeightPx);
            if (Math.abs(dx) <= config.thresholdXCells && Math.abs(dy) <= config.thresholdYCells)
                return true;
        }
        return false;
    }

    /** {@code update_cursor_trail_corners}. */
    private void updateCorners(float dt, boolean dectcemOn, float cellWidthPx, float cellHeightPx,
                               Config config, boolean forceSnap) {
        boolean skip = !mCornersInitialized || forceSnap
            || shouldSkipUpdate(dectcemOn, cellWidthPx, cellHeightPx, config);
        if (skip) {
            for (int i = 0; i < CORNERS; i++) {
                mCornerX[i] = edgeX(CORNER_X_EDGE[i]);
                mCornerY[i] = edgeY(CORNER_Y_EDGE[i]);
            }
            mCornersInitialized = true;
            return;
        }
        if (dt <= 0f) return;

        float centerX = (mEdgeLeft + mEdgeRight) * 0.5f;
        float centerY = (mEdgeTop + mEdgeBottom) * 0.5f;
        float halfDiag = norm(mEdgeRight - mEdgeLeft, mEdgeBottom - mEdgeTop) * 0.5f;
        if (halfDiag <= 0f) halfDiag = EPSILON;

        float minDot = Float.MAX_VALUE, maxDot = -Float.MAX_VALUE;
        boolean anyMoving = false;
        for (int i = 0; i < CORNERS; i++) {
            float targetX = edgeX(CORNER_X_EDGE[i]);
            float targetY = edgeY(CORNER_Y_EDGE[i]);
            float dx = targetX - mCornerX[i];
            float dy = targetY - mCornerY[i];
            if (Math.abs(dx) < EPSILON && Math.abs(dy) < EPSILON) {
                mScratchDx[i] = 0f;
                mScratchDy[i] = 0f;
                mScratchDot[i] = 0f;
                continue;
            }
            anyMoving = true;
            mScratchDx[i] = dx;
            mScratchDy[i] = dy;
            // Dot product of the corner's direction of travel with its own radial direction from
            // the target's centre: how much this corner leads rather than trails the move.
            float dot = (dx * (targetX - centerX) + dy * (targetY - centerY))
                / halfDiag / norm(dx, dy);
            mScratchDot[i] = dot;
            if (dot < minDot) minDot = dot;
            if (dot > maxDot) maxDot = dot;
        }
        if (!anyMoving) return;

        for (int i = 0; i < CORNERS; i++) {
            if (mScratchDx[i] == 0f && mScratchDy[i] == 0f) continue;
            float decay = (minDot == maxDot) ? config.decaySlow
                : config.decaySlow
                    + (config.decayFast - config.decaySlow) * (mScratchDot[i] - minDot) / (maxDot - minDot);
            float step = 1f - (float) Math.pow(2.0, -10.0 * dt / decay);
            mCornerX[i] += mScratchDx[i] * step;
            mCornerY[i] += mScratchDy[i] * step;
        }
    }

    /** {@code update_cursor_trail_opacity}. */
    private void updateOpacity(float dt, boolean dectcemOn, Config config) {
        if (dectcemOn) {
            mOpacity = Math.min(1f, mOpacity + dt / config.decaySlow);
        } else {
            mOpacity = Math.max(0f, mOpacity - dt / config.decaySlow);
        }
    }

    /** {@code update_cursor_trail_needs_render}: any corner still at least half a cell off. */
    private void updateNeedsRender(float cellWidthPx, float cellHeightPx) {
        float dxThreshold = cellWidthPx * 0.5f;
        float dyThreshold = cellHeightPx * 0.5f;
        boolean needs = false;
        for (int i = 0; i < CORNERS; i++) {
            float dx = Math.abs(edgeX(CORNER_X_EDGE[i]) - mCornerX[i]);
            float dy = Math.abs(edgeY(CORNER_Y_EDGE[i]) - mCornerY[i]);
            if (dx >= dxThreshold || dy >= dyThreshold) {
                needs = true;
                break;
            }
        }
        mNeedsRender = needs;
    }

    private float edgeX(int index) {
        return index == 0 ? mEdgeLeft : mEdgeRight;
    }

    private float edgeY(int index) {
        return index == 0 ? mEdgeTop : mEdgeBottom;
    }

    private static float norm(float x, float y) {
        return (float) Math.sqrt((double) x * x + (double) y * y);
    }

    public float cornerX(int corner) {
        return mCornerX[corner];
    }

    public float cornerY(int corner) {
        return mCornerY[corner];
    }

    public float opacity() {
        return mOpacity;
    }

    /** Whether any corner is still more than half a cell from its target, as of the last update. */
    public boolean needsRender() {
        return mNeedsRender;
    }
}
