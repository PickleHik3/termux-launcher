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
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Fast Canvas wave rendered below the dock's material tint, grain, rim, and content layers. */
public final class DockLaunchRippleView extends View {

    public interface CollisionListener {
        void onCollisionFrame(int color, float level);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path mClipPath = new Path();
    private final RectF mClip = new RectF();
    private float mOriginX;
    private float mOriginY;
    private float mProgress;
    private float mMaxRadius;
    private float mClipRadius;
    private int mColor;
    private boolean mCapsule;
    private ValueAnimator mAnimator;
    @Nullable private CollisionListener mCollisionListener;

    public DockLaunchRippleView(Context context) {
        this(context, null);
    }

    public DockLaunchRippleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setVisibility(INVISIBLE);
    }

    public void startRipple(int color, float originX, float originY, boolean capsule,
                            @NonNull RectF surfaceBounds, float cornerRadius,
                            @Nullable CollisionListener collisionListener) {
        if (mAnimator != null) mAnimator.cancel();
        mColor = color;
        mOriginX = originX;
        mOriginY = originY;
        mCapsule = capsule;
        mClip.set(surfaceBounds);
        mClipRadius = Math.max(0f, cornerRadius);
        mCollisionListener = collisionListener;
        float left = Math.abs(originX - surfaceBounds.left);
        float right = Math.abs(surfaceBounds.right - originX);
        float top = Math.abs(originY - surfaceBounds.top);
        float bottom = Math.abs(surfaceBounds.bottom - originY);
        mMaxRadius = (float) Math.hypot(Math.max(left, right), Math.max(top, bottom));
        setVisibility(VISIBLE);
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(capsule ? 420L : 340L);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.45f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (Float) animation.getAnimatedValue();
            if (mCollisionListener != null) {
                float collision = capsule && mProgress > 0.48f
                    ? (float) (Math.sin((mProgress - 0.48f) * Math.PI / 0.52f)
                        * (1f - (mProgress - 0.48f) * 0.45f)) : 0f;
                mCollisionListener.onCollisionFrame(mColor, Math.max(0f, collision));
            }
            invalidate();
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCollisionListener != null) mCollisionListener.onCollisionFrame(mColor, 0f);
                setVisibility(INVISIBLE);
            }
        });
        mAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mCollisionListener = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mProgress <= 0f || mClip.isEmpty()) return;
        int save = canvas.save();
        if (mCapsule) {
            mClipPath.reset();
            mClipPath.addRoundRect(mClip, mClipRadius, mClipRadius, Path.Direction.CW);
            canvas.clipPath(mClipPath);
        } else {
            canvas.clipRect(mClip);
        }
        float radius = Math.max(1f, mMaxRadius * (0.08f + 0.92f * mProgress));
        float fade = 1f - mProgress;
        int centerAlpha = Math.round(54f * fade);
        int waveAlpha = Math.round(150f * (float) Math.sin(Math.PI * mProgress));
        int[] colors = {
            withAlpha(mColor, centerAlpha),
            withAlpha(mColor, Math.round(centerAlpha * 0.55f)),
            withAlpha(mColor, waveAlpha),
            Color.TRANSPARENT
        };
        float[] stops = {0f, 0.72f, 0.9f, 1f};
        mPaint.setShader(new RadialGradient(mOriginX, mOriginY, radius, colors, stops,
            Shader.TileMode.CLAMP));
        canvas.drawCircle(mOriginX, mOriginY, radius, mPaint);
        mPaint.setShader(null);
        canvas.restoreToCount(save);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
