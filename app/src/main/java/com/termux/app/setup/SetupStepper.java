package com.termux.app.setup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * One dot per section joined by short lines; the current dot is an elongated pill. Which dots
 * answer a tap is {@link SetupStepPolicy}'s.
 */
final class SetupStepper extends View {

    interface Listener {
        void onStepTapped(int index);
    }

    private static final float DOT_DP = 8f;
    private static final float PILL_DP = 24f;
    private static final float LINE_DP = 14f;
    private static final float HEIGHT_DP = 32f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mDensity;
    private int mCount = 1;
    private int mCurrent;
    private int mFurthest;
    @Nullable private Listener mListener;

    SetupStepper(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    void setSteps(int count, int current, int furthest) {
        mCount = Math.max(1, count);
        mCurrent = current;
        mFurthest = furthest;
        requestLayout();
        invalidate();
    }

    private float dotWidth(int index) {
        return (index == mCurrent ? PILL_DP : DOT_DP) * mDensity;
    }

    private float contentWidth() {
        float width = 0f;
        for (int i = 0; i < mCount; i++)
            width += dotWidth(i);
        return width + (mCount - 1) * LINE_DP * mDensity;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int) Math.ceil(contentWidth()), widthSpec),
            resolveSize(Math.round(HEIGHT_DP * mDensity), heightSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int accent = SetupUi.accent(this);
        int quiet = ColorUtils.setAlphaComponent(SetupUi.onSurface(this), 70);
        float height = DOT_DP * mDensity;
        float top = (getHeight() - height) / 2f;
        float x = 0f;
        for (int i = 0; i < mCount; i++) {
            float width = dotWidth(i);
            mPaint.setColor(i <= mFurthest ? accent : quiet);
            mRect.set(x, top, x + width, top + height);
            canvas.drawRoundRect(mRect, height / 2f, height / 2f, mPaint);
            x += width;
            if (i < mCount - 1) {
                float line = LINE_DP * mDensity;
                mPaint.setColor(i < mFurthest ? accent : quiet);
                mRect.set(x + 2f * mDensity, top + height / 2f - mDensity / 2f,
                    x + line - 2f * mDensity, top + height / 2f + mDensity / 2f);
                canvas.drawRect(mRect, mPaint);
                x += line;
            }
        }
    }

    /** The dot under {@code touchX}, with a little slack either side, or -1. */
    private int dotAt(float touchX) {
        float slack = (LINE_DP * mDensity) / 2f;
        float x = 0f;
        for (int i = 0; i < mCount; i++) {
            float width = dotWidth(i);
            if (touchX >= x - slack && touchX <= x + width + slack)
                return i;
            x += width + LINE_DP * mDensity;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int hit = dotAt(event.getX());
            return hit >= 0 && SetupStepPolicy.canJump(mCurrent, mFurthest, mCount, hit);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int hit = dotAt(event.getX());
            if (hit >= 0 && SetupStepPolicy.canJump(mCurrent, mFurthest, mCount, hit)
                && mListener != null) {
                performClick();
                mListener.onStepTapped(hit);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
