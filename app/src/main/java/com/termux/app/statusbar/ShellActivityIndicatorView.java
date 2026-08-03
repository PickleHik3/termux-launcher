package com.termux.app.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Three pulsing dots in the status row, shown while a shell in the current session's windows is
 * producing output. Scoped to the current session on purpose: the row describes the session the user
 * is looking at, and a build running in another session is that session's business — its own window
 * pill carries the indication.
 *
 * <p>Reads its phase from {@link ShellActivityPulse}, the same clock the window pill's underline
 * uses, so the two surfaces move together.
 */
public final class ShellActivityIndicatorView extends View {

    private static final int SIZE_DP = 14;
    private static final float DOT_RADIUS_DP = 1.6f;
    private static final float DOT_GAP_DP = 3.4f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private ValueAnimator mAnimator;
    private boolean mBusy;
    @NonNull private StatusBarWidgetView.ColorRole mColorRole =
        StatusBarWidgetView.ColorRole.PRIMARY;
    private long mStartMs;
    /**
     * Window visibility as last reported rather than read back: the framework dispatches it during
     * attach, so the field is accurate before anything can start animating.
     */
    private boolean mWindowVisible = true;
    private boolean mAttached;

    public ShellActivityIndicatorView(@NonNull Context context) {
        this(context, null);
    }

    public ShellActivityIndicatorView(@NonNull Context context,
                                      @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.FILL);
        setVisibility(GONE);
        applyColors();
    }

    /** Whether any shell in the current session's windows is working right now. */
    public void setBusy(boolean busy) {
        if (mBusy == busy) return;
        mBusy = busy;
        setVisibility(busy ? VISIBLE : GONE);
        updateAnimator();
    }

    public boolean isBusy() {
        return mBusy;
    }

    public void setColorRole(@NonNull StatusBarWidgetView.ColorRole colorRole) {
        if (mColorRole == colorRole) return;
        mColorRole = colorRole;
        applyColors();
    }

    /** Re-resolve the wallpaper-derived role colour after a theme or wallpaper change. */
    public void refreshAppearance() {
        applyColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(SIZE_DP * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSizeAndState(size, widthMeasureSpec, 0),
            resolveSizeAndState(size, heightMeasureSpec, 0));
    }

    // The animator runs only while the indication is actually on screen: busy, attached, visible and
    // in a visible window. Without all four, a backgrounded activity would keep waking the
    // Choreographer for dots nobody can see.

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        updateAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimator();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        updateAnimator();
    }

    private boolean shouldAnimate() {
        return mBusy && mAttached && getVisibility() == VISIBLE && mWindowVisible;
    }

    private void updateAnimator() {
        if (shouldAnimate()) startAnimator();
        else stopAnimator();
    }

    private void startAnimator() {
        if (mAnimator != null) return;
        mStartMs = 0L;
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ShellActivityPulse.CYCLE_MS);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> invalidate());
        mAnimator.start();
    }

    private void stopAnimator() {
        if (mAnimator == null) return;
        mAnimator.cancel();
        mAnimator = null;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!mBusy) return;
        float density = getResources().getDisplayMetrics().density;
        float radius = DOT_RADIUS_DP * density;
        float gap = DOT_GAP_DP * density;
        float span = (ShellActivityPulse.DOT_COUNT - 1) * gap;
        float centerY = getHeight() / 2f;
        float x = getWidth() / 2f - span / 2f;
        // A stopped animator still draws one frame, so a non-animating indicator shows the dots at
        // rest rather than an empty gap in the row.
        float phase = ShellActivityPulse.phase(elapsedMs());
        for (int i = 0; i < ShellActivityPulse.DOT_COUNT; i++) {
            mPaint.setAlpha(Math.round(255f * ShellActivityPulse.dotWeight(i, phase)));
            canvas.drawCircle(x + i * gap, centerY, radius, mPaint);
        }
    }

    private long elapsedMs() {
        long now = android.os.SystemClock.uptimeMillis();
        if (mStartMs == 0L) mStartMs = now;
        return now - mStartMs;
    }

    private void applyColors() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        int roleColor = mColorRole == StatusBarWidgetView.ColorRole.SECONDARY ? secondary
            : mColorRole == StatusBarWidgetView.ColorRole.TERTIARY ? tertiary : primary;
        mPaint.setColor(ColorUtils.setAlphaComponent(roleColor, 255));
        invalidate();
    }

    /** For tests: whether the pulse animator is running right now. */
    public boolean isPulsing() {
        return mAnimator != null && mAnimator.isStarted();
    }
}
