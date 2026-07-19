package com.termux.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A single app-launch wave rendered below the dock's material tint, grain, rim, and content. */
public final class DockLaunchRippleView extends View {

    public interface CollisionListener {
        void onCollisionFrame(int color, float level);
    }

    /** Lets the host end the wave when the dock style or keyboard state changes. */
    public interface StateValidator {
        boolean isRippleStateValid();
    }

    private static final long PRESS_FLASH_MS = 84L;
    private static final long TRAVEL_MS = 250L;
    private static final long TOTAL_MS = 400L;
    private static final long STATE_FADE_MS = 80L;
    private static final long COLLISION_START_MS = 210L;
    private static final long COLLISION_DURATION_MS = 150L;
    private static final long COLLISION_RISE_MS = 28L;
    private static final int WAVE_SAMPLES = 128;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path mWavePath = new Path();
    private final RectF mSurface = new RectF();
    private final float[] mBoundaryDistances = new float[WAVE_SAMPLES];
    private float mOriginX;
    private float mOriginY;
    private float mElapsedMs;
    private float mOpacity;
    private float mWaveRadius;
    private float mMaxRadius;
    private float mSurfaceRadius;
    private float mRingWidth;
    private float mCollisionLevel;
    private int mColor;
    private boolean mCapsule;
    private int mAnimationGeneration;
    @Nullable private ValueAnimator mAnimator;
    @Nullable private CollisionListener mCollisionListener;
    @Nullable private StateValidator mStateValidator;
    @Nullable private Runnable mStateInvalidatedListener;

    public DockLaunchRippleView(Context context) {
        this(context, null);
    }

    public DockLaunchRippleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
        setVisibility(INVISIBLE);
    }

    public void startRipple(int color, float originX, float originY, boolean capsule,
                            @NonNull RectF surfaceBounds, float cornerRadius,
                            @Nullable CollisionListener collisionListener,
                            @Nullable StateValidator stateValidator,
                            @Nullable Runnable stateInvalidatedListener) {
        dispatchCollision(0f);
        cancelAnimator();
        mColor = color;
        mCapsule = capsule;
        mSurface.set(surfaceBounds);
        mSurfaceRadius = Math.max(0f, Math.min(cornerRadius,
            Math.min(mSurface.width(), mSurface.height()) * 0.5f));
        // Preserve the mapped icon centre exactly whenever it lies on the dock surface.
        mOriginX = clamp(originX, mSurface.left, mSurface.right);
        mOriginY = clamp(originY, mSurface.top, mSurface.bottom);
        mRingWidth = getResources().getDisplayMetrics().density * 22f;
        mCollisionListener = collisionListener;
        mStateValidator = stateValidator;
        mStateInvalidatedListener = stateInvalidatedListener;
        computeWaveGeometry();
        mElapsedMs = 0f;
        mWaveRadius = 0f;
        mCollisionLevel = 0f;
        mOpacity = 1f;
        setVisibility(VISIBLE);

        final int generation = ++mAnimationGeneration;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, TOTAL_MS);
        mAnimator = animator;
        animator.setDuration(TOTAL_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            if (generation != mAnimationGeneration) return;
            if (mStateValidator != null && !mStateValidator.isRippleStateValid()) {
                Runnable invalidated = mStateInvalidatedListener;
                mStateValidator = null;
                mStateInvalidatedListener = null;
                if (invalidated != null) invalidated.run();
                fadeOut();
                return;
            }
            mElapsedMs = (Float) animation.getAnimatedValue();
            updateWaveState();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (generation == mAnimationGeneration) finishRipple();
            }
        });
        animator.start();
    }

    /** Fades the current frame in place; used when another dock transition takes ownership. */
    public void fadeOut() {
        if (getVisibility() != VISIBLE || mOpacity <= 0f) return;
        float startOpacity = mOpacity;
        cancelAnimator();
        final int generation = ++mAnimationGeneration;
        ValueAnimator animator = ValueAnimator.ofFloat(startOpacity, 0f);
        mAnimator = animator;
        animator.setDuration(STATE_FADE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            if (generation != mAnimationGeneration) return;
            mOpacity = (Float) animation.getAnimatedValue();
            dispatchCollision(mCollisionLevel * mOpacity);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (generation == mAnimationGeneration) finishRipple();
            }
        });
        animator.start();
    }

    private void computeWaveGeometry() {
        mMaxRadius = 1f;
        for (int i = 0; i < WAVE_SAMPLES; i++) {
            double angle = Math.PI * 2.0 * i / WAVE_SAMPLES;
            float distance = distanceToRoundedBoundary(
                (float) Math.cos(angle), (float) Math.sin(angle));
            mBoundaryDistances[i] = distance;
            mMaxRadius = Math.max(mMaxRadius, distance);
        }
        // Full-width default dock and keyboard use the same width-based radius over the same 250 ms,
        // keeping their independently drawn portions aligned to one conceptual screen-space front.
        mMaxRadius = Math.max(mMaxRadius, mSurface.width());
    }

    private void updateWaveState() {
        float travel = clamp01(mElapsedMs / TRAVEL_MS);
        // Smoothstep gives the press flash a moment to register, then carries one monotonic crest out.
        float easedTravel = travel * travel * (3f - 2f * travel);
        mWaveRadius = Math.max(1f, mMaxRadius * easedTravel);
        float decay = mElapsedMs <= TRAVEL_MS
            ? 1f : 1f - (mElapsedMs - TRAVEL_MS) / (TOTAL_MS - TRAVEL_MS);
        mOpacity = clamp01(decay);

        if (mCapsule) {
            int contacts = 0;
            float contactStart = mRingWidth * 0.5f;
            for (float boundary : mBoundaryDistances) {
                if (mWaveRadius + contactStart >= boundary) contacts++;
            }
            float coverage = contacts / (float) WAVE_SAMPLES;
            float collisionAge = mElapsedMs - COLLISION_START_MS;
            float envelope;
            if (collisionAge < 0f || collisionAge >= COLLISION_DURATION_MS) {
                envelope = 0f;
            } else if (collisionAge < COLLISION_RISE_MS) {
                envelope = collisionAge / COLLISION_RISE_MS;
            } else {
                envelope = 1f - (collisionAge - COLLISION_RISE_MS)
                    / (COLLISION_DURATION_MS - COLLISION_RISE_MS);
            }
            // The visible compression starts on contact; accumulated energy drives one 150 ms wall
            // pulse near the end of travel and then gets out before the ripple itself resolves.
            mCollisionLevel = clamp01(coverage * 1.75f) * clamp01(envelope);
        } else {
            mCollisionLevel = 0f;
        }
        dispatchCollision(mCollisionLevel * mOpacity);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpacity <= 0f || mSurface.isEmpty()) return;

        float flashProgress = clamp01(mElapsedMs / PRESS_FLASH_MS);
        if (flashProgress < 1f) drawPressFlash(canvas, flashProgress);

        float waveFadeIn = clamp01(mElapsedMs / 34f);
        float strength = waveFadeIn * mOpacity;
        if (strength <= 0f) return;
        buildWavePath(mWaveRadius);

        // Overlapping strokes form one 22dp annulus. The brighter crest sits slightly ahead of the
        // broad halo, leaving a soft low-alpha trail on the origin-facing side of the same band.
        mPaint.setShader(null);
        mPaint.setStrokeWidth(mRingWidth);
        mPaint.setColor(withAlpha(mColor, Math.round(34f * strength)));
        canvas.drawPath(mWavePath, mPaint);
        buildWavePath(mWaveRadius + mRingWidth * 0.18f);
        mPaint.setStrokeWidth(mRingWidth * 0.43f);
        mPaint.setColor(withAlpha(mColor, Math.round(104f * strength)));
        canvas.drawPath(mWavePath, mPaint);
        mPaint.setStrokeWidth(Math.max(1f,
            getResources().getDisplayMetrics().density * 2.1f));
        mPaint.setColor(withAlpha(mColor, Math.round(178f * strength)));
        canvas.drawPath(mWavePath, mPaint);
    }

    private void drawPressFlash(Canvas canvas, float progress) {
        float density = getResources().getDisplayMetrics().density;
        float eased = 1f - (1f - progress) * (1f - progress);
        float radius = density * (4f + 14f * eased);
        int alpha = Math.round(118f * (1f - progress) * mOpacity);
        int[] colors = {withAlpha(mColor, alpha), withAlpha(mColor, alpha / 3), Color.TRANSPARENT};
        float[] stops = {0f, 0.56f, 1f};
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new RadialGradient(mOriginX, mOriginY, radius, colors, stops,
            Shader.TileMode.CLAMP));
        canvas.drawCircle(mOriginX, mOriginY, radius, mPaint);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    private void buildWavePath(float radius) {
        mWavePath.reset();
        float edgeInset = mRingWidth * 0.55f;
        for (int i = 0; i < WAVE_SAMPLES; i++) {
            double angle = Math.PI * 2.0 * i / WAVE_SAMPLES;
            // Once a ray reaches the wall it stops just inside it. Adjacent stopped samples spread
            // tangentially along the rounded edge, visibly compressing the formerly circular front.
            float rayRadius = Math.min(radius,
                Math.max(0f, mBoundaryDistances[i] - edgeInset));
            float x = mOriginX + (float) Math.cos(angle) * rayRadius;
            float y = mOriginY + (float) Math.sin(angle) * rayRadius;
            if (i == 0) mWavePath.moveTo(x, y); else mWavePath.lineTo(x, y);
        }
        mWavePath.close();
    }

    /** Binary-searches a ray against the rounded-rect signed-distance field. */
    private float distanceToRoundedBoundary(float dx, float dy) {
        float low = 0f;
        float high = (float) Math.hypot(mSurface.width(), mSurface.height()) + mRingWidth;
        for (int i = 0; i < 14; i++) {
            float mid = (low + high) * 0.5f;
            if (isInsideRoundedSurface(mOriginX + dx * mid, mOriginY + dy * mid)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private boolean isInsideRoundedSurface(float x, float y) {
        float halfWidth = mSurface.width() * 0.5f;
        float halfHeight = mSurface.height() * 0.5f;
        float centerX = mSurface.centerX();
        float centerY = mSurface.centerY();
        float qx = Math.abs(x - centerX) - (halfWidth - mSurfaceRadius);
        float qy = Math.abs(y - centerY) - (halfHeight - mSurfaceRadius);
        float outside = (float) Math.hypot(Math.max(qx, 0f), Math.max(qy, 0f));
        float inside = Math.min(Math.max(qx, qy), 0f);
        return outside + inside - mSurfaceRadius <= 0f;
    }

    private void dispatchCollision(float level) {
        if (mCollisionListener != null)
            mCollisionListener.onCollisionFrame(mColor, clamp01(level));
    }

    private void cancelAnimator() {
        mAnimationGeneration++;
        ValueAnimator animator = mAnimator;
        mAnimator = null;
        if (animator != null) animator.cancel();
    }

    private void finishRipple() {
        dispatchCollision(0f);
        mAnimator = null;
        mCollisionListener = null;
        mStateValidator = null;
        mStateInvalidatedListener = null;
        mOpacity = 0f;
        setVisibility(INVISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimator();
        finishRipple();
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return (min + max) * 0.5f;
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }
}
