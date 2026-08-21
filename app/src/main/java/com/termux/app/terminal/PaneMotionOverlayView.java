package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pane layer's transient motion, drawn above every pane and float: a contracting ghost where a
 * pane just closed, and the cursor's smear when focus moves from one pane to another.
 *
 * <p>Neither belongs to a pane. A closing pane cannot animate itself — by the time layout knows it
 * is gone its view is detached — and the smear spans two panes, so it cannot live inside either.
 *
 * <p>The smear is kitty's and neovide's shape, not a trail of copies: <em>one</em> quad whose four
 * corners chase the cursor at different rates, so the leading edge arrives first and the shape
 * shears along the direction of travel. The rates come from kitty's first-order law
 * ({@link PaneMotionMath#step}), which carries no velocity and therefore cannot overshoot when a
 * frame is dropped; it runs until every corner has caught up rather than for a fixed duration,
 * which is why it is driven from a {@link Choreographer} callback instead of a
 * {@link ValueAnimator}.
 */
public final class PaneMotionOverlayView extends View {

    /** Hyprland/niri-ish close: fast off the mark, settling out, and a legible contraction. */
    private static final long GHOST_DURATION_MS = 230L;
    private static final float GHOST_END_SCALE = 0.82f;
    /** More ghosts than this on screen at once is a burst nobody can read; drop the oldest. */
    private static final int MAX_GHOSTS = 4;

    /** Corner order around the quad. Any other winding makes the path self-intersect. */
    private static final int TOP_LEFT = 0;
    private static final int TOP_RIGHT = 1;
    private static final int BOTTOM_RIGHT = 2;
    private static final int BOTTOM_LEFT = 3;
    private static final int CORNERS = 4;

    /**
     * Two curves for the whole layer, built once (PathInterpolator bakes a lookup table, and these
     * used to be constructed per ghost and per flight).
     *
     * <p>A disappearance eases <em>out</em>: quick off the mark, settling as it goes. The first cut
     * used an ease-in here, which made the ghost hang and then snap away — the opposite reading of
     * a window leaving. niri closes on EaseOutQuad for the same reason.
     */
    private static final Interpolator EXIT = interpolator(0.2f, 0.9f, 0.3f, 1f, 1.6f);
    private static final Interpolator STANDARD = interpolator(0.2f, 0.8f, 0.2f, 1f, 1.8f);

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mSmearPath = new Path();
    private final RectF mScratch = new RectF();
    private final Rect mDirty = new Rect();
    private final List<Ghost> mGhosts = new ArrayList<>();

    // The smear's live state: where each corner is, and where it is heading.
    private final float[] mCornerX = new float[CORNERS];
    private final float[] mCornerY = new float[CORNERS];
    private final float[] mDecay = new float[CORNERS];
    private final float[] mSettleProbe = new float[CORNERS * 2];
    private final RectF mCursorTarget = new RectF();
    private int mCursorColor;
    private boolean mSmearActive;
    private long mLastFrameNanos;
    private boolean mFrameScheduled;
    @Nullable private Runnable mOnSmearFinished;

    private final Choreographer.FrameCallback mFrameCallback = this::onFrame;

    private static final class Ghost {
        final RectF bounds = new RectF();
        final float radiusPx;
        final int fillColor;
        final int rimColor;
        float progress;
        @Nullable ValueAnimator animator;

        Ghost(RectF bounds, float radiusPx, int fillColor, int rimColor) {
            this.bounds.set(bounds);
            this.radiusPx = radiusPx;
            this.fillColor = fillColor;
            this.rimColor = rimColor;
        }
    }

    public PaneMotionOverlayView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        // Purely decorative: every touch belongs to the panes and the interaction overlay below.
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * A pane that has just been removed, contracting out of the space it held.
     *
     * @param bounds in this overlay's coordinates
     * @param fillColor the pane's surface tint, or 0 for an outline-only ghost
     */
    public void ghostPane(@NonNull RectF bounds, float radiusPx, int fillColor, int rimColor) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return;
        while (mGhosts.size() >= MAX_GHOSTS) {
            Ghost oldest = mGhosts.remove(0);
            if (oldest.animator != null) oldest.animator.cancel();
        }
        Ghost ghost = new Ghost(bounds, radiusPx, fillColor, rimColor);
        mGhosts.add(ghost);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(GHOST_DURATION_MS);
        animator.setInterpolator(EXIT);
        animator.addUpdateListener(a -> {
            ghost.progress = (float) a.getAnimatedValue();
            invalidateRect(ghost.bounds);
        });
        // A listener, not withEndAction: this has to run on cancel too, or a cancelled ghost is
        // painted forever.
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                mGhosts.remove(ghost);
                invalidateRect(ghost.bounds);
            }
        });
        ghost.animator = animator;
        animator.start();
    }

    /**
     * Smear the cursor from one pane's cursor cell to another's.
     *
     * @param cellWidthPx target pane's cell size, for the travel threshold and the settle test
     * @param onFinished  run when the smear settles or is cancelled — the caller restores whatever
     *                    it suppressed for the flight
     */
    public void flyCursor(@NonNull RectF from, @NonNull RectF to, int color,
                          float cellWidthPx, float cellHeightPx, @Nullable Runnable onFinished) {
        float distance = (float) Math.hypot(to.centerX() - from.centerX(),
            to.centerY() - from.centerY());
        if (from.isEmpty() || to.isEmpty()
            || !PaneMotionMath.isTravelWorthAnimating(distance, cellWidthPx)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        finishSmear();
        mCursorTarget.set(to);
        mCursorColor = color;
        mOnSmearFinished = onFinished;
        for (int i = 0; i < CORNERS; i++) {
            mCornerX[i] = cornerX(from, i);
            mCornerY[i] = cornerY(from, i);
        }
        computeCornerDecays(from, to);
        mSmearActive = true;
        mLastFrameNanos = 0L;
        scheduleFrame();
    }

    /**
     * How much each corner leads the travel, normalised across the four, then mapped to a decay
     * time. The leading corners get the fast decay and arrive first; the trailing ones drag, and
     * that difference is the whole smear — the quad shears rather than sliding rigidly.
     */
    private void computeCornerDecays(@NonNull RectF from, @NonNull RectF to) {
        float travelX = to.centerX() - from.centerX();
        float travelY = to.centerY() - from.centerY();
        float travelLength = (float) Math.hypot(travelX, travelY);
        float[] alignments = new float[CORNERS];
        for (int i = 0; i < CORNERS; i++) {
            // The corner's outward radial direction from the rect's centre.
            float radialX = cornerX(from, i) - from.centerX();
            float radialY = cornerY(from, i) - from.centerY();
            float radialLength = (float) Math.hypot(radialX, radialY);
            alignments[i] = travelLength <= 0f || radialLength <= 0f
                ? 0f : (radialX * travelX + radialY * travelY) / (radialLength * travelLength);
        }
        PaneMotionMath.normaliseAlignments(alignments);
        for (int i = 0; i < CORNERS; i++) mDecay[i] = PaneMotionMath.cornerDecay(alignments[i]);
    }

    private void onFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        if (!mSmearActive) return;
        float dt = mLastFrameNanos == 0L
            ? 1f / 60f : (frameTimeNanos - mLastFrameNanos) / 1_000_000_000f;
        mLastFrameNanos = frameTimeNanos;
        // A long gap (the view was off screen, the app was paused) would otherwise be integrated as
        // one huge step; the law is stable there, but the smear would visibly teleport.
        dt = Math.min(dt, 1f / 20f);
        RectF previous = new RectF(smearBounds());
        for (int i = 0; i < CORNERS; i++) {
            float step = PaneMotionMath.step(dt, mDecay[i]);
            mCornerX[i] += (cornerX(mCursorTarget, i) - mCornerX[i]) * step;
            mCornerY[i] += (cornerY(mCursorTarget, i) - mCornerY[i]) * step;
            mSettleProbe[i * 2] = cornerX(mCursorTarget, i) - mCornerX[i];
            mSettleProbe[i * 2 + 1] = cornerY(mCursorTarget, i) - mCornerY[i];
        }
        RectF current = smearBounds();
        previous.union(current);
        invalidateRect(previous);
        if (PaneMotionMath.hasSettled(mSettleProbe, mCursorTarget.height())) {
            finishSmear();
            return;
        }
        scheduleFrame();
    }

    private void scheduleFrame() {
        if (mFrameScheduled || !mSmearActive) return;
        mFrameScheduled = true;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    /** Settle the smear and hand control back to whoever suppressed the real cursors. */
    private void finishSmear() {
        boolean wasActive = mSmearActive;
        mSmearActive = false;
        if (mFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(mFrameCallback);
            mFrameScheduled = false;
        }
        Runnable finished = mOnSmearFinished;
        mOnSmearFinished = null;
        if (wasActive) invalidate();
        if (finished != null) finished.run();
    }

    /** Drop everything in flight, for a re-render that invalidates the coordinates we captured. */
    public void clearMotion() {
        finishSmear();
        for (Ghost ghost : new ArrayList<>(mGhosts)) {
            if (ghost.animator != null) ghost.animator.cancel();
        }
        mGhosts.clear();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        // The frame callback outlives the view otherwise, and so does the suppression the smear's
        // completion is supposed to lift.
        clearMotion();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mGhosts.size(); i++) drawGhost(canvas, mGhosts.get(i));
        if (mSmearActive) drawSmear(canvas);
    }

    private void drawGhost(@NonNull Canvas canvas, @NonNull Ghost ghost) {
        float eased = ghost.progress;
        float scale = 1f - (1f - GHOST_END_SCALE) * eased;
        float alpha = 1f - eased;
        float cx = ghost.bounds.centerX();
        float cy = ghost.bounds.centerY();
        mScratch.set(
            cx - ghost.bounds.width() * scale / 2f, cy - ghost.bounds.height() * scale / 2f,
            cx + ghost.bounds.width() * scale / 2f, cy + ghost.bounds.height() * scale / 2f);
        if (Color.alpha(ghost.fillColor) > 0) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(ghost.fillColor);
            mPaint.setAlpha(Math.round(Color.alpha(ghost.fillColor) * alpha));
            canvas.drawRoundRect(mScratch, ghost.radiusPx, ghost.radiusPx, mPaint);
        }
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        mPaint.setColor(ghost.rimColor);
        mPaint.setAlpha(Math.round(Color.alpha(ghost.rimColor) * alpha));
        canvas.drawRoundRect(mScratch, ghost.radiusPx, ghost.radiusPx, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    /** One filled quad through the four corners — the same primitive kitty and neovide draw. */
    private void drawSmear(@NonNull Canvas canvas) {
        mSmearPath.reset();
        mSmearPath.moveTo(mCornerX[TOP_LEFT], mCornerY[TOP_LEFT]);
        mSmearPath.lineTo(mCornerX[TOP_RIGHT], mCornerY[TOP_RIGHT]);
        mSmearPath.lineTo(mCornerX[BOTTOM_RIGHT], mCornerY[BOTTOM_RIGHT]);
        mSmearPath.lineTo(mCornerX[BOTTOM_LEFT], mCornerY[BOTTOM_LEFT]);
        mSmearPath.close();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mCursorColor);
        mPaint.setAlpha(215);
        canvas.drawPath(mSmearPath, mPaint);
    }

    @NonNull
    private RectF smearBounds() {
        float left = Math.min(Math.min(mCornerX[0], mCornerX[1]), Math.min(mCornerX[2], mCornerX[3]));
        float right = Math.max(Math.max(mCornerX[0], mCornerX[1]), Math.max(mCornerX[2], mCornerX[3]));
        float top = Math.min(Math.min(mCornerY[0], mCornerY[1]), Math.min(mCornerY[2], mCornerY[3]));
        float bottom = Math.max(Math.max(mCornerY[0], mCornerY[1]), Math.max(mCornerY[2], mCornerY[3]));
        mScratch.set(left, top, right, bottom);
        return mScratch;
    }

    /** Repaint only what moved: this overlay is the size of the whole pane area. */
    private void invalidateRect(@NonNull RectF rect) {
        float slack = getResources().getDisplayMetrics().density * 2f;
        mDirty.set((int) Math.floor(rect.left - slack), (int) Math.floor(rect.top - slack),
            (int) Math.ceil(rect.right + slack), (int) Math.ceil(rect.bottom + slack));
        invalidate(mDirty);
    }

    private static float cornerX(@NonNull RectF rect, int corner) {
        return corner == TOP_LEFT || corner == BOTTOM_LEFT ? rect.left : rect.right;
    }

    private static float cornerY(@NonNull RectF rect, int corner) {
        return corner == TOP_LEFT || corner == TOP_RIGHT ? rect.top : rect.bottom;
    }

    private static Interpolator interpolator(float x1, float y1, float x2, float y2,
                                             float decelerateFallback) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new PathInterpolator(x1, y1, x2, y2) : new DecelerateInterpolator(decelerateFallback);
    }

    /** The standard curve, exposed so the pane frames' own animations share this family. */
    public static Interpolator standardInterpolator() {
        return STANDARD;
    }
}
