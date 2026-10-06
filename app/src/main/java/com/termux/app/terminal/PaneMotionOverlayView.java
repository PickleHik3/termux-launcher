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

import com.termux.view.KittyCursorTrail;

import java.util.ArrayList;
import java.util.List;

/**
 * The pane layer's transient motion, drawn above every pane and float: a contracting ghost where a
 * pane just closed, and the one cursor trail every pane's own view used to draw for itself.
 *
 * <p>Neither belongs to a pane. A closing pane cannot animate itself — by the time layout knows it
 * is gone its view is detached — and the trail can target any pane, including one that is not the
 * view it is drawn over while focus is settling, so it cannot live inside any one of them either.
 *
 * <p>The trail is a single line-for-line port of kitty's own ({@link KittyCursorTrail}): a quad
 * whose four corners chase the cursor's shape rect at different rates, so the leading edge arrives
 * first and the shape shears along the direction of travel. To this engine a pane switch is no
 * different from a cursor jumping across one pane — both are just the target rect changing — so a
 * focus change animates through exactly the same law as an in-pane move; see
 * {@link TerminalPaneController#focusSession}. The engine carries no velocity and therefore cannot
 * overshoot when a frame is dropped; it runs until every corner has caught up rather than for a
 * fixed duration, which is why the whole layer is driven from a {@link Choreographer} callback
 * instead of a {@link ValueAnimator}.
 */
public final class PaneMotionOverlayView extends View {

    /** Hyprland/niri-ish close: fast off the mark, settling out, and a legible contraction. */
    private static final long GHOST_DURATION_MS = 230L;
    private static final float GHOST_END_SCALE = 0.82f;
    /** More ghosts than this on screen at once is a burst nobody can read; drop the oldest. */
    private static final int MAX_GHOSTS = 4;

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

    /** kitty's own cursor-trail law; see the class doc. */
    private final KittyCursorTrail mCursorTrail = new KittyCursorTrail();
    private KittyCursorTrail.Config mCursorTrailConfig = DEFAULT_TRAIL_CONFIG;
    private boolean mCursorTrailEnabled = true;
    @Nullable private CursorTargetProvider mCursorTargetProvider;
    /** The last target the provider gave, kept so {@link #onDraw} can mask the live cursor cell. */
    private final CursorTarget mCursorTarget = new CursorTarget();
    private boolean mCursorTargetValid;
    private boolean mCursorFrameScheduled;

    private final Choreographer.FrameCallback mFrameCallback = this::onFrame;

    private CursorTrailStyle mCursorTrailStyle = CursorTrailStyle.DEFAULT;
    private final CursorTrailParticles mParticles = new CursorTrailParticles();
    private final CursorTrailMotionBlur mMotionBlur = new CursorTrailMotionBlur();
    private final CursorTrailComet mComet = new CursorTrailComet();
    private final float[] mParticleBuf = new float[CursorTrailParticles.OUT_SIZE];
    private boolean mParticlesWereAlive;
    private boolean mCometWasActive;
    /**
     * Seconds on a clock that restarts whenever no particles live, so float precision never
     * degrades with device uptime. Starts at 1 s so a move's time is always above 0.
     */
    private long mClockBaseMs;
    private long mFrameMs;
    private float mFrameSeconds;

    /** App defaults, per the design brief: delay 10ms, decay 0.1/0.4s, threshold 2/2 cells. */
    private static final KittyCursorTrail.Config DEFAULT_TRAIL_CONFIG =
        new KittyCursorTrail.Config(10L, 0.10f, 0.40f, 2, 2);

    /**
     * Fills in the focused pane's current cursor target, in this overlay's own pixel coordinates.
     * Pulled fresh every animated frame rather than pushed, since the trail must keep tracking a
     * cursor that is still moving (or a blink state that changed) without every pane view having to
     * notify on every one of those; see {@code TerminalView.CursorTrailListener} for what does push.
     */
    public interface CursorTargetProvider {
        /** @return false when there is no pane to track right now (no focus, or it left the screen). */
        boolean provideCursorTarget(@NonNull CursorTarget out);
    }

    /** One frame's worth of the focused pane's cursor, in overlay pixel coordinates. */
    public static final class CursorTarget {
        public float left, top, right, bottom;
        public float cellWidthPx, cellHeightPx;
        public boolean dectcemOn;
        public long positionChangedAtMillis;
        /**
         * Which pane the cursor belongs to, never 0 for a real pane. A move into another pane
         * always trails, whatever the start threshold, as kitty always trails a move into another
         * window.
         */
        public long ownerId;
        public int color;
        /**
         * The focused pane's card, in this overlay's pixels. Particles and the comet stay inside
         * it, as kitty's stay inside its window; the trail itself is not clipped, so a flight
         * between panes still crosses the gap.
         */
        public boolean hasClip;
        public float clipLeft, clipTop, clipRight, clipBottom;
    }

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

    /** Sets the tunables read from {@code kitty.conf}, or the app defaults when it names none. */
    public void setCursorTrailConfig(@NonNull KittyCursorTrail.Config config) {
        mCursorTrailConfig = config;
    }

    /** Picks how the trail looks; resets any particles or comet in flight. */
    public void setCursorTrailStyle(@NonNull CursorTrailStyle style) {
        if (mCursorTrailStyle == style) return;
        mCursorTrailStyle = style;
        resetStyleState();
        invalidate();
    }

    private void resetStyleState() {
        mParticles.reset();
        mComet.reset();
        mParticlesWereAlive = false;
        mCometWasActive = false;
    }

    /**
     * The single on/off switch for the whole trail: the preference, power save and reduce-motion
     * folded together by the caller. Off means no trail at all, for either kind of move.
     */
    public void setCursorTrailEnabled(boolean enabled) {
        if (mCursorTrailEnabled == enabled) return;
        mCursorTrailEnabled = enabled;
        if (!enabled) {
            mCursorTrail.reset();
            resetStyleState();
            mCursorTargetValid = false;
            stopCursorTrailFrames();
            invalidate();
        } else {
            requestCursorTrailFrame();
        }
    }

    /** Where the engine pulls this frame's cursor target from; see {@link CursorTargetProvider}. */
    public void setCursorTargetProvider(@Nullable CursorTargetProvider provider) {
        mCursorTargetProvider = provider;
    }

    /** Scratch for {@link #onFrame}, so the per-frame path allocates nothing. */
    private final RectF mPreviousTrailBounds = new RectF();

    /**
     * The cursor may have moved (an in-pane jump), or focus may have moved to another pane — to the
     * engine these are the same event, since both just change what {@link #mCursorTargetProvider}
     * returns. Idempotent and cheap to call every frame: it only arms the {@link Choreographer}
     * loop, which decides for itself whether anything needs to keep animating.
     */
    public void requestCursorTrailFrame() {
        if (!mCursorTrailEnabled || mCursorFrameScheduled) return;
        mCursorFrameScheduled = true;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    /**
     * A discontinuity the trail must not be smeared across — kitty's own example is a live resize;
     * ours adds a whole-window switch, where {@link TerminalPaneController} already snaps the panes
     * themselves rather than sliding them. The next target change snaps instead of animating; the
     * trail keeps its opacity and keeps following, unlike {@link #clearMotion()}.
     */
    public void snapCursorTrailOnNextFrame() {
        mCursorTrail.requestSnapOnNextUpdate();
        requestCursorTrailFrame();
    }

    private void stopCursorTrailFrames() {
        if (mCursorFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(mFrameCallback);
            mCursorFrameScheduled = false;
        }
    }

    private void onFrame(long frameTimeNanos) {
        mCursorFrameScheduled = false;
        if (!mCursorTrailEnabled) return;
        CursorTargetProvider provider = mCursorTargetProvider;
        boolean hasTarget = provider != null && provider.provideCursorTarget(mCursorTarget);
        if (!hasTarget) {
            // Nothing to track (no focused pane on screen): let the trail sit wherever it was:
            // resuming later finds a stale target invalid rather than smearing in from it.
            if (mCursorTargetValid) invalidate();
            mCursorTargetValid = false;
            return;
        }
        mCursorTargetValid = true;
        RectF previous = mPreviousTrailBounds;
        previous.set(cursorTrailBounds());
        // The frame's own vsync time, so every step is one even frame interval rather than
        // whenever this callback happened to run. It is System.nanoTime's clock, the same one
        // {@code TerminalEmulator#monotonicMillis()} stamps getCursorPositionChangedAtMillis() on, so the
        // delay gate still compares like with like.
        long now = frameTimeNanos / 1_000_000L;
        mFrameMs = now;
        boolean needsFrame = mCursorTrail.update(now, mCursorTarget.left, mCursorTarget.top,
            mCursorTarget.right, mCursorTarget.bottom, mCursorTarget.dectcemOn,
            mCursorTarget.positionChangedAtMillis, false, mCursorTarget.ownerId,
            mCursorTarget.cellWidthPx, mCursorTarget.cellHeightPx, mCursorTrailConfig);
        previous.union(cursorTrailBounds());
        invalidateRect(previous);
        boolean styleLive = updateStyleEffects(now);
        if (needsFrame || styleLive) requestCursorTrailFrame();
    }

    /** Feeds accepted moves to the particle and comet styles; true while either still shows. */
    private boolean updateStyleEffects(long nowMs) {
        CursorTrailStyle style = mCursorTrailStyle;
        boolean particleStyle = style == CursorTrailStyle.RAILGUN
            || style == CursorTrailStyle.TORPEDO || style == CursorTrailStyle.PIXIEDUST;
        boolean comet = style == CursorTrailStyle.COMET;
        if (!particleStyle && !comet) return false;
        if (!mParticlesWereAlive && !mComet.active()) mClockBaseMs = nowMs - 1000L;
        mFrameSeconds = (nowMs - mClockBaseMs) / 1000f;
        boolean moved = mCursorTrail.moveStartedOnLastUpdate();
        if (moved && particleStyle) {
            mParticles.record(mCursorTrail.moveFromEdge(0), mCursorTrail.moveFromEdge(1),
                mCursorTrail.moveFromEdge(2), mCursorTrail.moveFromEdge(3),
                mCursorTrail.moveToEdge(0), mCursorTrail.moveToEdge(1),
                mCursorTrail.moveToEdge(2), mCursorTrail.moveToEdge(3), mFrameSeconds);
        }
        if (moved && comet) {
            mComet.start(mCursorTrail.moveFromEdge(0), mCursorTrail.moveFromEdge(1),
                mCursorTrail.moveFromEdge(2), mCursorTrail.moveFromEdge(3),
                mCursorTrail.moveToEdge(0), mCursorTrail.moveToEdge(1),
                mCursorTrail.moveToEdge(2), mCursorTrail.moveToEdge(3), nowMs);
        }
        boolean particlesAlive = particleStyle && mParticles.alive(mFrameSeconds);
        boolean cometAlive = comet && mComet.alive(nowMs);
        // Particles can wander anywhere near the path; repaint the whole layer while they live,
        // and once more on the frame they die so the last ones are erased.
        if (particlesAlive || mParticlesWereAlive) invalidate();
        if (cometAlive || mCometWasActive) invalidateRect(mComet.bounds());
        mParticlesWereAlive = particlesAlive;
        mCometWasActive = cometAlive;
        return particlesAlive || cometAlive;
    }

    /** Drop everything in flight, for a re-render that invalidates the coordinates we captured. */
    public void clearMotion() {
        stopCursorTrailFrames();
        mCursorTrail.reset();
        resetStyleState();
        mCursorTargetValid = false;
        for (Ghost ghost : new ArrayList<>(mGhosts)) {
            if (ghost.animator != null) ghost.animator.cancel();
        }
        mGhosts.clear();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        // The frame callback outlives the view otherwise.
        clearMotion();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mGhosts.size(); i++) drawGhost(canvas, mGhosts.get(i));
        if (mCursorTargetValid && mCursorTrail.opacity() > 0f) drawCursorTrail(canvas);
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

    /**
     * One filled quad through the trail's four corners — the same primitive kitty draws. The live
     * cursor cell is clipped out first, exactly as kitty masks its own trail: without it the streak
     * paints a second, dimmer cursor on top of whichever pane is now focused.
     */
    private void drawCursorTrail(@NonNull Canvas canvas) {
        boolean didClip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (didClip) {
            canvas.save();
            canvas.clipOutRect(mCursorTarget.left, mCursorTarget.top,
                mCursorTarget.right, mCursorTarget.bottom);
        }
        int color = mCursorTrailConfig.hasColor ? mCursorTrailConfig.color : mCursorTarget.color;
        int baseAlpha = Color.alpha(color) > 0 ? Color.alpha(color) : 255;
        float opacity = clamp01(mCursorTrail.opacity());
        CursorTrailStyle style = mCursorTrailStyle;
        if (style == CursorTrailStyle.COMET) {
            boolean clipped = clipToPane(canvas);
            mComet.draw(canvas, color, opacity, mFrameMs, getResources().getDisplayMetrics().density);
            if (clipped) canvas.restore();
        } else {
            // kitty draws its trail only while needs_render holds (shaders.c draw_cursor_trail
            // call; cursor_trail_color.a is zero for custom shaders otherwise): a settled trail
            // leaves nothing behind, however much opacity it still has.
            if (mCursorTrail.needsRender()) {
                boolean blurred = style == CursorTrailStyle.MOTION_BLUR
                    && mMotionBlur.draw(canvas, mCursorTrail, color, baseAlpha / 255f * opacity,
                        mCursorTarget.left, mCursorTarget.top, mCursorTarget.right,
                        mCursorTarget.bottom);
                if (!blurred) drawQuad(canvas, color, baseAlpha, opacity);
            }
            boolean clipped = style != CursorTrailStyle.DEFAULT && style != CursorTrailStyle.MOTION_BLUR
                && clipToPane(canvas);
            if (style == CursorTrailStyle.RAILGUN) {
                drawParticles(canvas, CursorTrailParticles.MODE_RAILGUN, color, baseAlpha, opacity);
            } else if (style == CursorTrailStyle.TORPEDO) {
                drawParticles(canvas, CursorTrailParticles.MODE_TORPEDO, color, baseAlpha, opacity);
            } else if (style == CursorTrailStyle.PIXIEDUST) {
                drawParticles(canvas, CursorTrailParticles.MODE_PIXIEDUST, color, baseAlpha, opacity);
            }
            if (clipped) canvas.restore();
        }
        if (didClip) canvas.restore();
    }

    /** Saves and clips to the focused pane's card; false (nothing saved) when there is none. */
    private boolean clipToPane(@NonNull Canvas canvas) {
        if (!mCursorTarget.hasClip) return false;
        canvas.save();
        canvas.clipRect(mCursorTarget.clipLeft, mCursorTarget.clipTop,
            mCursorTarget.clipRight, mCursorTarget.clipBottom);
        return true;
    }

    private void drawQuad(@NonNull Canvas canvas, int color, int baseAlpha, float opacity) {
        mSmearPath.reset();
        mSmearPath.moveTo(mCursorTrail.cornerX(0), mCursorTrail.cornerY(0));
        mSmearPath.lineTo(mCursorTrail.cornerX(1), mCursorTrail.cornerY(1));
        mSmearPath.lineTo(mCursorTrail.cornerX(2), mCursorTrail.cornerY(2));
        mSmearPath.lineTo(mCursorTrail.cornerX(3), mCursorTrail.cornerY(3));
        mSmearPath.close();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        mPaint.setAlpha(Math.round(baseAlpha * opacity));
        canvas.drawPath(mSmearPath, mPaint);
    }

    private void drawParticles(@NonNull Canvas canvas, int mode, int color, int baseAlpha,
                               float opacity) {
        int count = mParticles.collect(mFrameSeconds, mode, mParticleBuf);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            mPaint.setAlpha(Math.round(baseAlpha * opacity * mParticleBuf[o + 3]));
            canvas.drawCircle(mParticleBuf[o], mParticleBuf[o + 1], mParticleBuf[o + 2], mPaint);
        }
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    @NonNull
    private RectF cursorTrailBounds() {
        float c0x = mCursorTrail.cornerX(0), c1x = mCursorTrail.cornerX(1);
        float c2x = mCursorTrail.cornerX(2), c3x = mCursorTrail.cornerX(3);
        float c0y = mCursorTrail.cornerY(0), c1y = mCursorTrail.cornerY(1);
        float c2y = mCursorTrail.cornerY(2), c3y = mCursorTrail.cornerY(3);
        float left = Math.min(Math.min(c0x, c1x), Math.min(c2x, c3x));
        float right = Math.max(Math.max(c0x, c1x), Math.max(c2x, c3x));
        float top = Math.min(Math.min(c0y, c1y), Math.min(c2y, c3y));
        float bottom = Math.max(Math.max(c0y, c1y), Math.max(c2y, c3y));
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
