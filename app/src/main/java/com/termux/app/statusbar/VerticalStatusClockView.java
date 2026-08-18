package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Compact upright clock face for the landscape status rail. */
public final class VerticalStatusClockView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mSurface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final SimpleDateFormat mHours = new SimpleDateFormat("HH", Locale.getDefault());
    private final SimpleDateFormat mMinutes = new SimpleDateFormat("mm", Locale.getDefault());
    private final SimpleDateFormat mPeriod = new SimpleDateFormat("a", Locale.getDefault());
    private final Runnable mTicker = new Runnable() {
        @Override public void run() {
            if (!isAttachedToWindow()) return;
            invalidate();
            long delay = 60_000L - Math.floorMod(System.currentTimeMillis(), 60_000L);
            postDelayed(this, Math.max(1_000L, delay));
        }
    };

    @NonNull private String mStyle =
        TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE;
    private boolean mUseAmPm;
    private int mPrimary;
    private int mOnSurface;
    private int mPanel;

    public VerticalStatusClockView(Context context) { this(context, null); }

    public VerticalStatusClockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mPanel = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            ContextCompat.getColor(context, R.color.termux_surface_panel_high));
        setContentDescription("Clock");
    }

    public void setStyle(@Nullable String style, boolean useAmPm) {
        mStyle = style == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE : style;
        mUseAmPm = useAmPm;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(mTicker);
        mTicker.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(mTicker);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(50), widthMeasureSpec),
            resolveSize(dp(112), heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Date now = new Date();
        String hour = mHours.format(now);
        String minute = mMinutes.format(now);
        if (mUseAmPm) {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            int h = calendar.get(java.util.Calendar.HOUR);
            hour = String.format(Locale.getDefault(), "%02d", h == 0 ? 12 : h);
        }

        boolean minimal = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(mStyle);
        boolean lcd = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(mStyle);
        boolean led = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(mStyle);
        boolean tape = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(mStyle);
        boolean slab = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(mStyle);

        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create(lcd || tape ? "monospace" : "sans-serif-medium",
            led ? Typeface.BOLD : Typeface.NORMAL));
        mPaint.setTextSize(dp(minimal ? 19 : 23));
        mPaint.setColor(lcd || led ? mPrimary : mOnSurface);

        float cx = getWidth() / 2f;
        float firstY = dp(40);
        float secondY = dp(77);
        if (!minimal) {
            mSurface.setColor(ColorUtils.setAlphaComponent(mPanel, slab ? 205 : 145));
            float radius = dp(slab ? 5 : 9);
            mRect.set(dp(5), dp(13), getWidth() - dp(5), dp(49));
            canvas.drawRoundRect(mRect, radius, radius, mSurface);
            mRect.set(dp(5), dp(53), getWidth() - dp(5), dp(89));
            canvas.drawRoundRect(mRect, radius, radius, mSurface);
        }
        canvas.drawText(hour, cx, firstY, mPaint);
        canvas.drawText(minute, cx, secondY, mPaint);
        if (mUseAmPm) {
            mPaint.setTextSize(dp(8));
            mPaint.setColor(ColorUtils.setAlphaComponent(mPrimary, 210));
            canvas.drawText(mPeriod.format(now), cx, getHeight() - dp(3), mPaint);
        } else if (tape) {
            mSurface.setColor(ColorUtils.setAlphaComponent(mPrimary, 120));
            canvas.drawRect(dp(10), getHeight() - dp(5), getWidth() - dp(10),
                getHeight() - dp(4), mSurface);
        }
        setContentDescription(hour + ":" + minute + (mUseAmPm ? " " + mPeriod.format(now) : ""));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
