/*
 * Copyright 2016 Tu Yimin
 * Licensed under the Apache License, Version 2.0.
 * Modified by Termux Launcher in 2026 for the Termux:Monet-derived blur implementation.
 */
package com.github.mmin18.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import com.termux.R;

/**
 * Realtime blur overlay that captures activity decor, blurs it, and draws the result.
 * Restores pre-placeholder behavior used by Termux-Monet lineage.
 */
public class RealtimeBlurView extends View {

    private static int RENDERING_COUNT;
    private static int BLUR_IMPL;

    /**
     * Whether the GPU can blur this view's own content, making the software pass unnecessary.
     *
     * <p>The software path captures the decor view into a bitmap and runs ScriptIntrinsicBlur over
     * it on the UI thread. RenderScript was deprecated at API 31 and its GPU backends are gone on
     * current devices, so that intrinsic executes on the CPU. From API 31 the same capture is drawn
     * unblurred and {@link RenderEffect} blurs the view during compositing instead, which is what
     * the platform accelerates. The RenderScript path stays for API 26-30.
     */
    private static final boolean BLUR_ON_GPU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    private static final StopException STOP_EXCEPTION = new StopException();

    private float mDownsampleFactor;
    private int mOverlayColor;
    private float mBlurRadius;
    private boolean mDownsampleFactorOptimization;

    private final BlurImpl mBlurImpl;
    private boolean mDirty;
    private Bitmap mBitmapToBlur;
    private Bitmap mBlurredBitmap;
    private Canvas mBlurringCanvas;
    private boolean mIsRendering;
    private final Paint mPaint;
    private final Paint mBitmapPaint;
    private final Rect mRectSrc = new Rect();
    private final Rect mRectDst = new Rect();

    private float mAppliedEffectRadius = -1f;

    private View mDecorView;
    private boolean mDifferentRoot;

    public RealtimeBlurView(Context context) {
        this(context, null);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mBlurImpl = getBlurImpl();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RealtimeBlurView);
        mBlurRadius = a.getDimension(
            R.styleable.RealtimeBlurView_realtimeBlurRadius,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics())
        );
        mDownsampleFactor = a.getFloat(R.styleable.RealtimeBlurView_realtimeDownsampleFactor, 4f);
        mOverlayColor = a.getColor(R.styleable.RealtimeBlurView_realtimeOverlayColor, 0xAAFFFFFF);
        mDownsampleFactorOptimization = a.getBoolean(
            R.styleable.RealtimeBlurView_downsampleFactorOptimization,
            true
        );
        a.recycle();

        mPaint = new Paint();
        mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    }

    protected BlurImpl getBlurImpl() {
        // Never spin up a RenderScript context on devices that blur on the GPU: creating one costs
        // a driver handle and an Allocation pair for a pass that never runs.
        if (BLUR_ON_GPU) return new EmptyBlurImpl();
        if (BLUR_IMPL == 0) {
            try {
                AndroidStockBlurImpl impl = new AndroidStockBlurImpl();
                Bitmap bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
                impl.prepare(getContext(), bmp, 4f);
                impl.release();
                bmp.recycle();
                BLUR_IMPL = 1;
            } catch (Throwable ignored) {
                BLUR_IMPL = -1;
            }
        }

        if (BLUR_IMPL == 1) return new AndroidStockBlurImpl();
        return new EmptyBlurImpl();
    }

    public void setBlurRadius(float radius) {
        if (mBlurRadius != radius) {
            mBlurRadius = radius;
            mDirty = true;
            invalidate();
        }
    }

    public void setDownsampleFactor(float factor) {
        if (factor <= 0f) {
            throw new IllegalArgumentException("Downsample factor must be greater than 0.");
        }
        if (mDownsampleFactor != factor) {
            mDownsampleFactor = factor;
            mDirty = true;
            releaseBitmap();
            invalidate();
        }
    }

    public void setOverlayColor(int color) {
        if (mOverlayColor != color) {
            mOverlayColor = color;
            invalidate();
        }
    }

    private void releaseBitmap() {
        if (mBitmapToBlur != null) {
            mBitmapToBlur.recycle();
            mBitmapToBlur = null;
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap.recycle();
            mBlurredBitmap = null;
        }
    }

    protected void release() {
        releaseBitmap();
        clearRenderEffect();
        mBlurImpl.release();
    }

    protected boolean prepare() {
        if (mBlurRadius == 0) {
            release();
            return false;
        }

        float downsampleFactor = mDownsampleFactor;
        float radius = mBlurRadius / downsampleFactor;
        if (radius > 25f) {
            if (mDownsampleFactorOptimization) {
                downsampleFactor = (int) (radius / 25f) + 1f;
                radius = radius / downsampleFactor;
            } else {
                downsampleFactor = downsampleFactor * radius / 25f;
                radius = 25f;
            }
        }

        int width = getWidth();
        int height = getHeight();
        int scaledWidth = Math.max(1, (int) (width / downsampleFactor));
        int scaledHeight = Math.max(1, (int) (height / downsampleFactor));

        boolean dirty = mDirty;

        Bitmap sizeReference = BLUR_ON_GPU ? mBitmapToBlur : mBlurredBitmap;
        if (mBlurringCanvas == null || sizeReference == null
            || sizeReference.getWidth() != scaledWidth
            || sizeReference.getHeight() != scaledHeight) {
            dirty = true;
            releaseBitmap();

            boolean success = false;
            try {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                if (mBitmapToBlur == null) return false;
                mBlurringCanvas = new Canvas(mBitmapToBlur);

                if (!BLUR_ON_GPU) {
                    mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                    if (mBlurredBitmap == null) return false;
                }

                success = true;
            } catch (OutOfMemoryError ignored) {
            } finally {
                if (!success) {
                    release();
                    return false;
                }
            }
        }

        if (BLUR_ON_GPU) {
            applyRenderEffect(radius * downsampleFactor);
            mDirty = false;
            return true;
        }

        if (dirty) {
            if (mBlurImpl.prepare(getContext(), mBitmapToBlur, radius)) {
                mDirty = false;
            } else {
                return false;
            }
        }

        return true;
    }

    protected void blur(Bitmap bitmapToBlur, Bitmap blurredBitmap) {
        if (BLUR_ON_GPU) return; // The capture is drawn as-is; RenderEffect blurs it.
        mBlurImpl.blur(bitmapToBlur, blurredBitmap);
    }

    /**
     * Keeps the view's blur effect in step with the requested radius.
     *
     * <p>The radius is in view pixels because the effect runs on this view's rendered output, after
     * the downsampled capture has been scaled back up to full size.
     */
    private void applyRenderEffect(float radiusPx) {
        if (!BLUR_ON_GPU) return;
        float radius = Math.max(0.1f, radiusPx);
        if (mAppliedEffectRadius == radius) return;
        setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        mAppliedEffectRadius = radius;
    }

    private void clearRenderEffect() {
        if (!BLUR_ON_GPU || mAppliedEffectRadius < 0f) return;
        setRenderEffect(null);
        mAppliedEffectRadius = -1f;
    }

    /**
     * While true, the pre-draw hook skips the capture+blur entirely and the view keeps drawing its
     * last blurred frame. The capture is a full software rasterization of the decor hierarchy on
     * every frame the window draws, which is exactly what a scrolling list above this view pays
     * for; a host whose backdrop is effectively static (a settled drawer over a resting terminal)
     * can rest it and resume when its own geometry starts moving again.
     */
    private boolean mUpdatesPaused;

    public void setUpdatesPaused(boolean paused) {
        if (mUpdatesPaused == paused) return;
        mUpdatesPaused = paused;
        if (!paused) invalidate();
    }

    private final ViewTreeObserver.OnPreDrawListener mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            int[] locations = new int[2];
            Bitmap oldBmp = BLUR_ON_GPU ? mBitmapToBlur : mBlurredBitmap;
            View decor = mDecorView;
            if (decor != null && !mUpdatesPaused && isShown() && prepare()) {
                boolean redrawBitmap = (BLUR_ON_GPU ? mBitmapToBlur : mBlurredBitmap) != oldBmp;

                decor.getLocationOnScreen(locations);
                int x = -locations[0];
                int y = -locations[1];

                getLocationOnScreen(locations);
                x += locations[0];
                y += locations[1];

                mBitmapToBlur.eraseColor(mOverlayColor & 0x00FFFFFF);

                int rc = mBlurringCanvas.save();
                mIsRendering = true;
                RENDERING_COUNT++;
                try {
                    mBlurringCanvas.scale(
                        1f * mBitmapToBlur.getWidth() / getWidth(),
                        1f * mBitmapToBlur.getHeight() / getHeight()
                    );
                    mBlurringCanvas.translate(-x, -y);
                    if (decor.getBackground() != null) {
                        decor.getBackground().draw(mBlurringCanvas);
                    }
                    decor.draw(mBlurringCanvas);
                } catch (StopException ignored) {
                } finally {
                    mIsRendering = false;
                    RENDERING_COUNT--;
                    mBlurringCanvas.restoreToCount(rc);
                }

                blur(mBitmapToBlur, mBlurredBitmap);

                if (redrawBitmap || mDifferentRoot) {
                    invalidate();
                }
            }
            return true;
        }
    };

    protected View getActivityDecorView() {
        Context ctx = getContext();
        for (int i = 0; i < 4 && ctx != null && !(ctx instanceof Activity) && ctx instanceof ContextWrapper; i++) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        if (ctx instanceof Activity) {
            return ((Activity) ctx).getWindow().getDecorView();
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDecorView = getActivityDecorView();
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
            mDifferentRoot = mDecorView.getRootView() != getRootView();
            if (mDifferentRoot) {
                mDecorView.postInvalidate();
            }
        } else {
            mDifferentRoot = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
        }
        release();
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mIsRendering) {
            throw STOP_EXCEPTION;
        } else if (RENDERING_COUNT > 0) {
            // Overlapping blur views are not supported.
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBlurredBitmap(canvas, BLUR_ON_GPU ? mBitmapToBlur : mBlurredBitmap, mOverlayColor);
    }

    protected void drawBlurredBitmap(Canvas canvas, Bitmap blurredBitmap, int overlayColor) {
        if (blurredBitmap != null) {
            mRectSrc.right = blurredBitmap.getWidth();
            mRectSrc.bottom = blurredBitmap.getHeight();
            mRectDst.right = getWidth();
            mRectDst.bottom = getHeight();
            canvas.drawBitmap(blurredBitmap, mRectSrc, mRectDst, mBitmapPaint);
        }
        mPaint.setColor(overlayColor);
        canvas.drawRect(mRectDst, mPaint);
    }

    private static class StopException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
