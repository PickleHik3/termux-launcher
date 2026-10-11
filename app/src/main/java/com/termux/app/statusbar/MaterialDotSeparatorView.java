package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Small wallpaper-derived Material color dot separating compact status values.
 *
 * <p>Six pixels of rhythm and nothing else: remove one and no information goes with it. That is
 * why it is held to {@code OnGlass.TARGET_DECORATION} rather than to the graphics floor — the user
 * was offered the promotion to 3:1 and did not take it — and why it is held to anything at all,
 * having measured 1.01:1 on the reporting device's light mode, which is not a quiet separator but
 * an absent one.</p>
 */
public final class MaterialDotSeparatorView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private StatusBarWidgetView.ColorRole mRole = StatusBarWidgetView.ColorRole.SECONDARY;
    /** What the chrome measured; null until the bar has been measured, and then the old colour. */
    @Nullable private Integer mInk;
    /** Whether a row short of room has taken this dot out: it keeps no width and draws nothing. */
    private boolean mTucked;
    /** The width the dot takes when it is not tucked, as last measured. */
    private int mUntuckedWidth;

    public MaterialDotSeparatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        applyColor();
    }

    public void setColorRole(@NonNull StatusBarWidgetView.ColorRole role) {
        if (mRole == role) return;
        mRole = role;
        applyColor();
        invalidate();
    }

    /** Which tier this dot belongs to, for a caller resolving the bar's ink. */
    @NonNull
    public StatusBarWidgetView.ColorRole colorRole() {
        return mRole;
    }

    /** The colour the chrome resolved for this dot on the band it sits on, at its own tier. */
    public void setInk(@ColorInt int ink) {
        if (mInk != null && mInk == ink) return;
        mInk = ink;
        applyColor();
        invalidate();
    }

    private void applyColor() {
        if (mInk != null) {
            mPaint.setColor(mInk);
            return;
        }
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorTertiary, primary);
        mPaint.setColor(mRole == StatusBarWidgetView.ColorRole.TERTIARY ? tertiary
            : mRole == StatusBarWidgetView.ColorRole.PRIMARY ? primary : secondary);
    }

    /**
     * Takes the dot out of a row that is short of room, without touching its visibility: that
     * stays the business of whoever decides there are two things to separate. Its margins stay,
     * as a sliver of the gap it stood in.
     */
    public void setTucked(boolean tucked) {
        if (mTucked == tucked) return;
        mTucked = tucked;
        requestLayout();
        invalidate();
    }

    /** The width the dot takes along a row, tucked or not. */
    public int widthPx(boolean tucked) {
        return tucked ? 0 : mUntuckedWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mUntuckedWidth = getMeasuredWidth();
        if (mTucked) setMeasuredDimension(0, getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mTucked) return;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
            Math.min(getWidth(), getHeight()) / 2f, mPaint);
    }
}
