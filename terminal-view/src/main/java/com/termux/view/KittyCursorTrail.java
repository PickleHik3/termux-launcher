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
     * Which edge of the target rect each corner tracks, exactly kitty's {@code corner_index[2][4]}.
     * Corner 0 is top-right, 1 bottom-right, 2 bottom-left, 3 top-left — the winding does not
     * matter to the engine itself, only that {@link #cornerX} and {@link #cornerY} agree with it.
     */
    private static final int[] CORNER_X_EDGE = {1, 1, 0, 0};
    private static final int[] CORNER_Y_EDGE = {0, 1, 1, 0};

    /** How close every corner must be to its edge, in pixels, before the trail stops rendering. */
    private static final float SETTLED_PX = 0.5f;

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
    // The corners as they stood before the latest update; the motion-blur sweep starts here.
    private final float[] mPrevCornerX = new float[CORNERS];
    private final float[] mPrevCornerY = new float[CORNERS];
    // The latest accepted move's from/to rects: left, top, right, bottom each.
    private final float[] mMoveFrom = new float[4];
    private final float[] mMoveTo = new float[4];
    private boolean mMoveStarted;
    // A target replacement waiting for updateCorners to say whether it snaps or smears.
    private boolean mTargetReplaced;
    private final float[] mReplacedFrom = new float[4];
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
    /** The last {@link #update} asked for no further frame: the caller's loop has gone quiet. */
    private boolean mIdle = true;
    private boolean mSnapPending;

    /** Forget everything: the next {@link #update} snaps to its target with no visible trail. */
    public void reset() {
        mOpacity = 0f;
        mNeedsRender = false;
        mCornersInitialized = false;
        mHasPreviousFrame = false;
        mIdle = true;
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
        System.arraycopy(mCornerX, 0, mPrevCornerX, 0, CORNERS);
        System.arraycopy(mCornerY, 0, mPrevCornerY, 0, CORNERS);
        mMoveStarted = false;
        mTargetReplaced = false;
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
            dt = Math.max(0L, nowMillis - clockFrom(nowMillis, positionChangedAtMillis)) / 1000f;
        }

        boolean forceSnap = mSnapPending;
        mSnapPending = false;
        updateCorners(dt, dectcemOn, cellWidthPx, cellHeightPx, config, forceSnap);
        if (mMoveStarted) {
            System.arraycopy(mReplacedFrom, 0, mMoveFrom, 0, 4);
            mMoveTo[0] = mEdgeLeft;
            mMoveTo[1] = mEdgeTop;
            mMoveTo[2] = mEdgeRight;
            mMoveTo[3] = mEdgeBottom;
        }
        updateOpacity(dt, dectcemOn, config);

        boolean needsRenderPrev = mNeedsRender;
        updateNeedsRender();

        mPreviousFrameMillis = nowMillis;
        mHasPreviousFrame = true;

        boolean wantsFrame = mNeedsRender || needsRenderPrev || pendingDelay;
        mIdle = !wantsFrame;
        return wantsFrame;
    }

    /**
     * Where this update's time step starts. kitty integrates {@code now - updated_at} with no clamp
     * ({@code update_cursor_trail_corners}), and runs {@code update_cursor_trail} on every wakeup of
     * its loop ({@code render()} → {@code prepare_to_render_os_window}), including the one that
     * reads the output that moved the cursor — always inside the {@code cursor_trail} delay, so
     * that update only advances {@code updated_at}. The move is then integrated from about when
     * the cursor moved, never from whenever the loop last ran.
     * <p>
     * This caller only runs frames while something is moving, so once it has gone quiet the last
     * update can be seconds old; the client's move stands in for the wakeup kitty would have had.
     * While frames are running the previous frame is that wakeup, as it is in kitty.
     */
    private long clockFrom(long nowMillis, long positionChangedAtMillis) {
        if (mIdle && positionChangedAtMillis > mPreviousFrameMillis)
            return Math.min(nowMillis, positionChangedAtMillis);
        return mPreviousFrameMillis;
    }

    private void updateTarget(float left, float top, float right, float bottom) {
        if (mCornersInitialized && (left != mEdgeLeft || top != mEdgeTop
            || right != mEdgeRight || bottom != mEdgeBottom)) {
            mReplacedFrom[0] = mEdgeLeft;
            mReplacedFrom[1] = mEdgeTop;
            mReplacedFrom[2] = mEdgeRight;
            mReplacedFrom[3] = mEdgeBottom;
            mTargetReplaced = true;
        }
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
            // A snap has no smear, so previous equals current.
            System.arraycopy(mCornerX, 0, mPrevCornerX, 0, CORNERS);
            System.arraycopy(mCornerY, 0, mPrevCornerY, 0, CORNERS);
            return;
        }
        if (mTargetReplaced) mMoveStarted = true;
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

    /**
     * {@code update_cursor_trail_needs_render}: any corner still at least half a pixel off. kitty
     * writes the threshold as {@code g.dx / cell_size.width * 0.5}: one cell's NDC width over its
     * width in pixels is one pixel in NDC, so this is half a pixel, not half a cell. Stopping half a
     * cell early froze the quad with its trailing edge still up to half a cell behind the cursor.
     */
    private void updateNeedsRender() {
        boolean needs = false;
        for (int i = 0; i < CORNERS; i++) {
            float dx = Math.abs(edgeX(CORNER_X_EDGE[i]) - mCornerX[i]);
            float dy = Math.abs(edgeY(CORNER_Y_EDGE[i]) - mCornerY[i]);
            if (dx >= SETTLED_PX || dy >= SETTLED_PX) {
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

    /** The corner's x before the latest {@link #update}; equals {@link #cornerX} after a snap. */
    public float prevCornerX(int corner) {
        return mPrevCornerX[corner];
    }

    public float prevCornerY(int corner) {
        return mPrevCornerY[corner];
    }

    /** True only on the update where a target change was accepted as a real move (no snap/skip). */
    public boolean moveStartedOnLastUpdate() {
        return mMoveStarted;
    }

    /** The accepted move's source rect edge; index 0 left, 1 top, 2 right, 3 bottom. */
    public float moveFromEdge(int index) {
        return mMoveFrom[index];
    }

    /** The accepted move's destination rect edge; index 0 left, 1 top, 2 right, 3 bottom. */
    public float moveToEdge(int index) {
        return mMoveTo[index];
    }

    public float opacity() {
        return mOpacity;
    }

    /**
     * Whether any corner is still half a pixel or more from its target, as of the last update. The
     * quad is drawn only while this holds, as kitty draws its trail only while {@code needs_render}.
     */
    public boolean needsRender() {
        return mNeedsRender;
    }
}
