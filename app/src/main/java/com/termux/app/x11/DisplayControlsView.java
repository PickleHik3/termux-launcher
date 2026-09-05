package com.termux.app.x11;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * The Display page's controls: the tab a terminal pane drops from its top edge when its border is
 * tapped, drawn here over the X surface with two buttons - power, which starts, stops or turns the
 * display on, and a cog for its settings. Same size, same fill and stroke, same motion as the
 * panes', so the wall's places answer a border tap the same way.
 */
public final class DisplayControlsView extends View {

    /** What the two buttons do. */
    public interface Listener {
        void onPower();
        void onSettings();
    }

    public static final int ACTION_NONE = -1;
    public static final int ACTION_POWER = 0;
    public static final int ACTION_SETTINGS = 1;

    /** nf-fa-power_off and nf-fa-cog. */
    private static final String GLYPH_POWER = "";
    private static final String GLYPH_SETTINGS = "";

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mTab = new RectF();
    private final RectF[] mButtons = {new RectF(), new RectF()};
    @Nullable private ValueAnimator mAnimator;
    private float mProgress;
    private boolean mShown;
    private boolean mRunning;
    @Nullable private Listener mListener;

    public DisplayControlsView(@NonNull Context context) {
        super(context);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mGlyphPaint.setTextSize(dp(11.5f));
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Whether a display is running: the power glyph turns to the error colour, as a close does. */
    public void setRunning(boolean running) {
        if (mRunning == running) return;
        mRunning = running;
        invalidate();
    }

    public boolean isControlsShown() {
        return mShown;
    }

    public void show() {
        if (mShown) return;
        mShown = true;
        animateTo(1f, false);
    }

    public void dismiss() {
        if (!mShown) return;
        animateTo(0f, true);
    }

    /** Run the button under {@code (x, y)}, in this view's coordinates; false when none is. */
    public boolean activate(int action) {
        if (mListener == null) return false;
        if (action == ACTION_POWER) mListener.onPower();
        else if (action == ACTION_SETTINGS) mListener.onSettings();
        else return false;
        return true;
    }

    /** The button at {@code (x, y)}, or {@link #ACTION_NONE}; nothing answers while half shown. */
    public int actionAt(float x, float y) {
        if (!mShown || mProgress < .35f) return ACTION_NONE;
        computeGeometry();
        if (mButtons[0].contains(x, y)) return ACTION_POWER;
        if (mButtons[1].contains(x, y)) return ACTION_SETTINGS;
        return ACTION_NONE;
    }

    private void animateTo(float target, boolean clearOnEnd) {
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = ValueAnimator.ofFloat(mProgress, target);
        mAnimator.setDuration(190L);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (clearOnEnd && mProgress <= 0f) mShown = false;
            }
        });
        mAnimator.start();
    }

    /** The pane's tab, at the top-trailing corner: two buttons of 22.4dp in a 24dp tab. */
    private void computeGeometry() {
        float button = dp(22.4f);
        float width = button * 2 + dp(4.8f);
        float right = getWidth() - dp(3);
        float left = Math.max(dp(3), right - width);
        float height = dp(24);
        float top = -height * (1f - mProgress);
        mTab.set(left, top, right, top + height);
        float x = left + dp(2.4f);
        for (RectF b : mButtons) {
            b.set(x, top, x + button, top + dp(22));
            x += button;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!mShown || mProgress <= 0f || getWidth() <= 0) return;
        computeGeometry();
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        int error = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError, Color.RED);
        float radius = dp(4);
        int save = canvas.save();
        // Revealed through the page's top edge, so the closing motion disappears into the frame.
        canvas.clipRect(0f, -dp(1), getWidth(), getHeight());

        mPath.reset();
        mPath.moveTo(mTab.left, 0f);
        mPath.lineTo(mTab.right, 0f);
        mPath.lineTo(mTab.right, mTab.bottom - radius);
        mPath.quadTo(mTab.right, mTab.bottom, mTab.right - radius, mTab.bottom);
        mPath.lineTo(mTab.left + radius, mTab.bottom);
        mPath.quadTo(mTab.left, mTab.bottom, mTab.left, mTab.bottom - radius);
        mPath.close();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ColorUtils.setAlphaComponent(surface, Math.round(232f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        mPath.reset();
        mPath.moveTo(mTab.left - dp(5), 0f);
        mPath.lineTo(mTab.left, 0f);
        mPath.lineTo(mTab.left, mTab.bottom - radius);
        mPath.quadTo(mTab.left, mTab.bottom, mTab.left + radius, mTab.bottom);
        mPath.lineTo(mTab.right - radius, mTab.bottom);
        mPath.quadTo(mTab.right, mTab.bottom, mTab.right, mTab.bottom - radius);
        mPath.lineTo(mTab.right, 0f);
        mPath.lineTo(mTab.right + dp(5), 0f);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(225f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        int alpha = Math.round(255f * mProgress);
        drawGlyph(canvas, mButtons[0], GLYPH_POWER,
            ColorUtils.setAlphaComponent(mRunning ? error : primary, alpha));
        drawGlyph(canvas, mButtons[1], GLYPH_SETTINGS, ColorUtils.setAlphaComponent(primary, alpha));
        canvas.restoreToCount(save);
    }

    private void drawGlyph(@NonNull Canvas canvas, @NonNull RectF button, @NonNull String glyph, int color) {
        mGlyphPaint.setColor(color);
        float baseline = button.centerY() - (mGlyphPaint.ascent() + mGlyphPaint.descent()) / 2f;
        canvas.drawText(glyph, button.centerX(), baseline, mGlyphPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) mAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
