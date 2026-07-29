package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Clock widget for the modular top-pane slot. All four renderers share one grid: a left-aligned time
 * band, a meta column holding seconds over AM/PM, and a date row beneath. The widget reports its
 * content width so the slot can hand the remaining space to media or pinned notifications, and it
 * compresses through {@link TopPaneClockForm} instead of ever changing the pane height.
 */
public final class TerminalClockWidget extends View {

    private static final String[] WEEKDAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final long FLIP_DURATION_MS = 560L;
    private static final long LCD_DURATION_MS = 280L;
    private static final long MINIMAL_DURATION_MS = 350L;
    private static final long LED_DURATION_MS = 320L;

    /** Shared grid: gap between the time band and the meta column, and inside the meta column. */
    private static final float META_GAP_DP = 8f;
    private static final float META_STACK_GAP_DP = 4f;
    private static final float DATE_ROW_DP = 14f;
    private static final float SLOT_HEIGHT_DP = 68f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final Camera mCamera = new Camera();
    private final RectF mRect = new RectF();
    private final char[] mDigits = new char[6];
    private final char[] mOldDigits = new char[6];
    private final long[] mChangedAt = new long[6];
    private final Runnable mTicker = new Runnable() {
        @Override public void run() {
            if (!mTickerRunning || !isAttachedToWindow()) return;
            updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
            long delay = 1000L - Math.floorMod(System.currentTimeMillis(), 1000L);
            postDelayed(this, Math.max(16L, delay));
        }
    };
    private final Runnable mSyncTicker = this::syncTicker;

    private String mStyle = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE;
    private TopPaneClockForm mForm = TopPaneClockForm.FULL;
    private ClockSnapshot mSnapshot;
    private boolean mTickerRunning;
    private boolean mUseAmPm;
    private int mChromeOnSurface;
    private int mChromeSecondary;

    public TerminalClockWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        resolveChromeColors();
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
    }

    public void setStyle(@Nullable String style) {
        String normalized;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(style)) {
            normalized = style;
        } else {
            normalized = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP;
        }
        if (normalized.equals(mStyle)) return;
        mStyle = normalized;
        requestLayout();
        invalidate();
        updateContentDescription();
    }

    @NonNull
    String getStyle() {
        return mStyle;
    }

    /** Which grid form renders. Changing it re-measures, since the content width changes with it. */
    public void setForm(@NonNull TopPaneClockForm form) {
        if (mForm == form) return;
        mForm = form;
        requestLayout();
        invalidate();
    }

    @NonNull
    public TopPaneClockForm getForm() {
        return mForm;
    }

    public void setUseAmPm(boolean useAmPm) {
        if (mUseAmPm == useAmPm) return;
        mUseAmPm = useAmPm;
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
        requestLayout();
    }

    boolean isUsingAmPm() {
        return mUseAmPm;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(mSyncTicker);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicker();
        removeCallbacks(mSyncTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mSyncTicker == null) return;
        removeCallbacks(mSyncTicker);
        post(mSyncTicker);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mSyncTicker == null) return;
        removeCallbacks(mSyncTicker);
        post(mSyncTicker);
    }

    private void syncTicker() {
        if (isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE) startTicker();
        else stopTicker();
    }

    private void startTicker() {
        if (mTickerRunning) return;
        mTickerRunning = true;
        removeCallbacks(mTicker);
        mTicker.run();
    }

    private void stopTicker() {
        mTickerRunning = false;
        removeCallbacks(mTicker);
    }

    private void resolveChromeColors() {
        Context context = getContext();
        mChromeOnSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mChromeSecondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
    }

    // ---- Measurement ------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = resolveSize(Math.round(dp(SLOT_HEIGHT_DP)), heightMeasureSpec);
        int width;
        if (mForm == TopPaneClockForm.FULL
            && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        } else {
            width = resolveSize(Math.round(contentWidth()), widthMeasureSpec);
        }
        setMeasuredDimension(width, height);
    }

    /** Width the current form actually paints, so the slot can place content beside it. */
    public float contentWidth() {
        if (mSnapshot == null) return 0f;
        switch (mForm) {
            case MONO_CHIP:
                return monoContentWidth();
            case COMPACT:
                return compactContentWidth();
            default:
                return fullContentWidth();
        }
    }

    private float fullContentWidth() {
        float timeRow;
        float dateRow;
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                timeRow = dp(98f) + dp(META_GAP_DP) + dp(22f);
                dateRow = spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 11f, .14f)
                    + dp(6f) + dp(120f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                timeRow = spacedTextWidth(timeText(), thinTypeface(), 42f, -.01f)
                    + dp(META_GAP_DP) + minimalMetaWidth(15f, 9f);
                dateRow = trackedTextWidth(dateSpacedText(), lightTypeface(), 11f, 5f)
                    + dp(8f) + dp(110f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                timeRow = dotTextWidth(timeText(), dp(3f)) + dp(META_GAP_DP) + ledMetaWidth(1.8f, 1.2f);
                dateRow = dotTextWidth(mSnapshot.date, dp(1.3f)) + dp(8f) + dp(90f);
                break;
            default:
                timeRow = dp(125f) + dp(META_GAP_DP) + dp(32f);
                dateRow = dp(86f);
                break;
        }
        return Math.max(timeRow, dateRow);
    }

    private float compactContentWidth() {
        float timeRow;
        float dateRow;
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                timeRow = dp(68f) + dp(6f)
                    + Math.max(dp(17.5f), spacedTextWidth(mSnapshot.period, Typeface.MONOSPACE, 7f, .1f));
                dateRow = spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 9f, .1f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                timeRow = spacedTextWidth(timeText(), thinTypeface(), 30f, 0f)
                    + dp(5f) + minimalMetaWidth(12f, 8f);
                dateRow = trackedTextWidth(mSnapshot.date, lightTypeface(), 10f, 3f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                timeRow = dotTextWidth(timeText(), dp(2.1f)) + dp(5f) + ledMetaWidth(1.4f, 1f);
                dateRow = dotTextWidth(mSnapshot.date, dp(1f));
                break;
            default:
                timeRow = dp(90f) + dp(4f) + Math.max(
                    spacedTextWidth(mSnapshot.ss, Typeface.DEFAULT_BOLD, 9f, 0f),
                    spacedTextWidth(mSnapshot.period, Typeface.DEFAULT_BOLD, 7f, 0f));
                dateRow = 0f;
                break;
        }
        return Math.max(timeRow, dateRow);
    }

    private float monoContentWidth() {
        return spacedTextWidth(timeText(), Typeface.MONOSPACE, 15f, .04f) + dp(4f)
            + spacedTextWidth(monoSecondsText(), Typeface.MONOSPACE, 10f, 0f) + dp(4f)
            + spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 10f, .14f);
    }

    private float minimalMetaWidth(float secondsDp, float periodDp) {
        float seconds = spacedTextWidth(mSnapshot.ss, lightTypeface(), secondsDp, 0f);
        float period = mSnapshot.period.isEmpty() ? 0f
            : spacedTextWidth(mSnapshot.period, mediumTypeface(), periodDp, .14f);
        return Math.max(dp(20f), Math.max(seconds, period));
    }

    private float ledMetaWidth(float secondsCellDp, float periodCellDp) {
        float seconds = dotTextWidth(mSnapshot.ss, dp(secondsCellDp));
        float period = mSnapshot.period.isEmpty() ? 0f
            : dotTextWidth(mSnapshot.period, dp(periodCellDp));
        return Math.max(seconds, period);
    }

    // ---- Drawing ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSnapshot == null || getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        switch (mForm) {
            case MONO_CHIP:
                drawMonoChip(canvas);
                break;
            case COMPACT:
                drawCompact(canvas, now);
                break;
            default:
                drawFull(canvas, now);
                break;
        }
        if (hasRunningAnimation(now)) postInvalidateOnAnimation();
    }

    private void drawFull(Canvas canvas, long now) {
        float bandDp = fullBandHeightDp();
        float dateGapDp = fullDateGapDp();
        float columnDp = bandDp + dateGapDp + DATE_ROW_DP;
        float scale = Math.min(1f, getHeight() / dp(columnDp));
        canvas.save();
        canvas.translate(0f, Math.max(0f, (getHeight() - dp(columnDp) * scale) / 2f));
        canvas.scale(scale, scale);
        float dateTop = dp(bandDp + dateGapDp);
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawFullLcd(canvas, now, dateTop);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawFullMinimal(canvas, now, dateTop);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawFullLed(canvas, now, dateTop);
                break;
            default:
                drawFullFlip(canvas, now, dateTop);
                break;
        }
        canvas.restore();
    }

    private void drawCompact(Canvas canvas, long now) {
        float bandDp = compactBandHeightDp();
        float dateDp = compactDateHeightDp();
        float columnDp = bandDp + (dateDp > 0f ? 3f + dateDp : 0f);
        float scale = Math.min(1f, getHeight() / dp(columnDp));
        canvas.save();
        canvas.translate(0f, Math.max(0f, (getHeight() - dp(columnDp) * scale) / 2f));
        canvas.scale(scale, scale);
        float dateTop = dp(bandDp + 3f);
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawCompactLcd(canvas, now, dateTop);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawCompactMinimal(canvas, now, dateTop);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawCompactLed(canvas, now, dateTop);
                break;
            default:
                drawCompactFlip(canvas, now);
                break;
        }
        canvas.restore();
    }

    private float fullBandHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return 34f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 21f;
            default:
                return 38f;
        }
    }

    private float fullDateGapDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return 7f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 8f;
            default:
                return 6f;
        }
    }

    private float compactBandHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return 24f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 14.7f;
            default:
                return 26f;
        }
    }

    /** Flip's date is a row of chips, which does not survive the compact 10dp line. */
    private float compactDateHeightDp() {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(mStyle) ? 0f : 10f;
    }

    // ---- Split-flap -------------------------------------------------------

    private void drawFullFlip(Canvas canvas, long now, float dateTop) {
        float x = 0f;
        x = drawFlipDigit(canvas, 0, x, 0f, 26f, 38f, 31f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 1, x, 0f, 26f, 38f, 31f, now);
        x += dp(4f);
        drawFlipColon(canvas, x + dp(6.5f), dp(19f), 2.5f, 5.5f);
        x += dp(13f);
        x = drawFlipDigit(canvas, 2, x, 0f, 26f, 38f, 31f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 3, x, 0f, 26f, 38f, 31f, now);
        x += dp(META_GAP_DP);
        float metaX = x;
        float seconds = drawFlipDigit(canvas, 4, metaX, 0f, 15f, 20f, 15f, now);
        drawFlipDigit(canvas, 5, seconds + dp(2f), 0f, 15f, 20f, 15f, now);
        if (!mSnapshot.period.isEmpty()) {
            drawFlipChip(canvas, mSnapshot.period, metaX, dp(20f + META_STACK_GAP_DP), 32f, 12f,
                3f, 8f, .08f, Color.rgb(176, 208, 202));
        }
        drawFlipDateRow(canvas, dateTop);
    }

    private void drawCompactFlip(Canvas canvas, long now) {
        float x = 0f;
        x = drawFlipDigit(canvas, 0, x, 0f, 18f, 26f, 21f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 1, x, 0f, 18f, 26f, 21f, now);
        x += dp(2f);
        drawFlipColon(canvas, x + dp(5f), dp(13f), 2f, 4f);
        x += dp(10f) + dp(2f);
        x = drawFlipDigit(canvas, 2, x, 0f, 18f, 26f, 21f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 3, x, 0f, 18f, 26f, 21f, now);
        x += dp(4f);
        drawLabel(canvas, mSnapshot.ss, x, baseline(0f, dp(11f), Typeface.DEFAULT_BOLD, 9f), 9f,
            Typeface.DEFAULT_BOLD, 0f, Color.rgb(199, 207, 202));
        if (!mSnapshot.period.isEmpty()) {
            drawLabel(canvas, mSnapshot.period, x, baseline(dp(13f), dp(9f), Typeface.DEFAULT_BOLD, 7f),
                7f, Typeface.DEFAULT_BOLD, 0f, Color.rgb(176, 208, 202));
        }
    }

    private float drawFlipDigit(Canvas canvas, int digit, float x, float y,
                                float widthDp, float heightDp, float textDp, long now) {
        float w = dp(widthDp), h = dp(heightDp), half = h / 2f;
        RectF card = new RectF(x, y, x + w, y + h);
        float p = progress(digit, now, FLIP_DURATION_MS);
        boolean animating = p < 1f;
        char bottom = animating ? mOldDigits[digit] : mDigits[digit];

        drawFlipHalf(canvas, card, true, mDigits[digit], textDp);
        drawFlipHalf(canvas, card, false, bottom, textDp);
        if (animating && p < .5f) {
            float local = p * 2f;
            drawRotatedFlipHalf(canvas, card, true, mOldDigits[digit], textDp,
                -90f * local * local);
        } else if (animating) {
            float local = (p - .5f) * 2f;
            float eased = 1f - (1f - local) * (1f - local);
            drawRotatedFlipHalf(canvas, card, false, mDigits[digit], textDp,
                90f * (1f - eased));
        }

        mFillPaint.setShader(null);
        mFillPaint.setColor(Color.argb(235, 8, 12, 14));
        canvas.drawRect(x, y + half - dp(.75f), x + w, y + half + dp(.75f), mFillPaint);
        mFillPaint.setColor(Color.argb(13, 255, 255, 255));
        canvas.drawRect(x, y + half + dp(.75f), x + w, y + half + dp(1.75f), mFillPaint);
        mFillPaint.setColor(Color.rgb(17, 23, 26));
        canvas.drawRoundRect(new RectF(x - dp(1f), y + half - dp(4f),
            x + dp(2f), y + half + dp(4f)), dp(2f), dp(2f), mFillPaint);
        canvas.drawRoundRect(new RectF(x + w - dp(2f), y + half - dp(4f),
            x + w + dp(1f), y + half + dp(4f)), dp(2f), dp(2f), mFillPaint);
        return x + w;
    }

    private void drawFlipHalf(Canvas canvas, RectF card, boolean top, char digit, float textDp) {
        float half = card.height() / 2f;
        RectF clip = top
            ? new RectF(card.left, card.top, card.right, card.top + half)
            : new RectF(card.left, card.top + half, card.right, card.bottom);
        canvas.save();
        canvas.clipRect(clip);
        mFillPaint.setShader(new LinearGradient(0f, clip.top, 0f, clip.bottom,
            top ? Color.rgb(51, 61, 63) : Color.rgb(35, 44, 46),
            top ? Color.rgb(42, 51, 53) : Color.rgb(27, 35, 37), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, dp(5f), dp(5f), mFillPaint);
        mFillPaint.setShader(null);
        if (top) {
            mFillPaint.setShader(new LinearGradient(0f, clip.top, 0f, clip.bottom,
                Color.TRANSPARENT, Color.argb(95, 0, 0, 0), Shader.TileMode.CLAMP));
        } else {
            mFillPaint.setShader(new LinearGradient(0f, clip.top, 0f, clip.bottom,
                Color.argb(70, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(clip, mFillPaint);
        mFillPaint.setShader(null);
        mPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(Color.rgb(238, 242, 238));
        mPaint.setTextAlign(Paint.Align.CENTER);
        float baseline = card.centerY() - (mPaint.ascent() + mPaint.descent()) / 2f;
        canvas.drawText(String.valueOf(digit), card.centerX(), baseline, mPaint);
        canvas.restore();
    }

    private void drawRotatedFlipHalf(Canvas canvas, RectF card, boolean top, char digit,
                                     float textDp, float angle) {
        float pivotY = card.centerY();
        mCamera.save();
        mCamera.rotateX(angle);
        mCamera.getMatrix(mMatrix);
        mCamera.restore();
        mMatrix.preTranslate(-card.centerX(), -pivotY);
        mMatrix.postTranslate(card.centerX(), pivotY);
        canvas.save();
        canvas.concat(mMatrix);
        drawFlipHalf(canvas, card, top, digit, textDp);
        canvas.restore();
    }

    private void drawFlipColon(Canvas canvas, float centerX, float centerY, float radiusDp,
                               float offsetDp) {
        mFillPaint.setShader(null);
        mFillPaint.setColor(Color.rgb(199, 207, 202));
        canvas.drawCircle(centerX, centerY - dp(offsetDp), dp(radiusDp), mFillPaint);
        canvas.drawCircle(centerX, centerY + dp(offsetDp), dp(radiusDp), mFillPaint);
    }

    private void drawFlipDateRow(Canvas canvas, float top) {
        String[] tags = {mSnapshot.weekday, mSnapshot.day, mSnapshot.month};
        float[] widths = {28f, 22f, 28f};
        float x = 0f;
        for (int i = 0; i < tags.length; i++) {
            drawFlipChip(canvas, tags[i], x, top, widths[i], DATE_ROW_DP, 4f, 10f, 0f,
                Color.rgb(233, 237, 233));
            x += dp(widths[i]) + dp(4f);
        }
    }

    private void drawFlipChip(Canvas canvas, String text, float left, float top, float widthDp,
                              float heightDp, float radiusDp, float textDp, float letterSpacing,
                              int textColor) {
        mRect.set(left, top, left + dp(widthDp), top + dp(heightDp));
        mFillPaint.setShader(new LinearGradient(0f, mRect.top, 0f, mRect.bottom,
            new int[] {Color.rgb(44, 53, 55), Color.rgb(12, 16, 17), Color.rgb(35, 43, 45)},
            new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mRect, dp(radiusDp), dp(radiusDp), mFillPaint);
        mFillPaint.setShader(null);
        mPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(textColor);
        mPaint.setAlpha(255);
        mPaint.setTextAlign(Paint.Align.CENTER);
        float baseline = mRect.centerY() - (mPaint.ascent() + mPaint.descent()) / 2f;
        canvas.drawText(text, mRect.centerX(), baseline, mPaint);
        mPaint.setLetterSpacing(0f);
    }

    // ---- LCD --------------------------------------------------------------

    private void drawFullLcd(Canvas canvas, long now, float dateTop) {
        float x = 0f;
        x = drawSevenDigit(canvas, 0, x, 0f, 19f, 34f, dp(3f), now);
        x += dp(3f);
        x = drawSevenDigit(canvas, 1, x, 0f, 19f, 34f, dp(3f), now);
        x += dp(3f);
        drawLcdColon(canvas, x + dp(5f), dp(17f), 2f, 3.5f);
        x += dp(10f) + dp(3f);
        x = drawSevenDigit(canvas, 2, x, 0f, 19f, 34f, dp(3f), now);
        x += dp(3f);
        x = drawSevenDigit(canvas, 3, x, 0f, 19f, 34f, dp(3f), now);
        x += dp(META_GAP_DP);
        float metaX = x;
        float seconds = drawSevenDigit(canvas, 4, metaX, 0f, 10f, 18f, dp(1.8f), now);
        drawSevenDigit(canvas, 5, seconds + dp(2f), 0f, 10f, 18f, dp(1.8f), now);
        if (!mSnapshot.period.isEmpty()) {
            drawLabel(canvas, mSnapshot.period, metaX,
                baseline(dp(18f + META_STACK_GAP_DP), dp(9f), Typeface.MONOSPACE, 8f), 8f,
                Typeface.MONOSPACE, .1f, Color.rgb(255, 178, 82));
        }
        float dateWidth = spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 11f, .14f);
        drawLabel(canvas, mSnapshot.date, 0f, baseline(dateTop, dp(DATE_ROW_DP), Typeface.MONOSPACE, 11f),
            11f, Typeface.MONOSPACE, .14f, Color.rgb(255, 138, 30));
        drawRule(canvas, dateWidth + dp(6f), dateTop + dp(DATE_ROW_DP / 2f), 120f,
            withAlpha(Color.rgb(255, 138, 30), 71));
    }

    private void drawCompactLcd(Canvas canvas, long now, float dateTop) {
        float x = 0f;
        x = drawSevenDigit(canvas, 0, x, 0f, 13f, 24f, dp(2.2f), now);
        x += dp(2f);
        x = drawSevenDigit(canvas, 1, x, 0f, 13f, 24f, dp(2.2f), now);
        x += dp(2f);
        drawLcdColon(canvas, x + dp(4f), dp(12f), 1.5f, 2.5f);
        x += dp(8f) + dp(2f);
        x = drawSevenDigit(canvas, 2, x, 0f, 13f, 24f, dp(2.2f), now);
        x += dp(2f);
        x = drawSevenDigit(canvas, 3, x, 0f, 13f, 24f, dp(2.2f), now);
        x += dp(6f);
        float metaX = x;
        float seconds = drawSevenDigit(canvas, 4, metaX, 0f, 8f, 14f, dp(1.4f), now);
        drawSevenDigit(canvas, 5, seconds + dp(1.5f), 0f, 8f, 14f, dp(1.4f), now);
        if (!mSnapshot.period.isEmpty()) {
            drawLabel(canvas, mSnapshot.period, metaX, baseline(dp(16f), dp(8f), Typeface.MONOSPACE, 7f),
                7f, Typeface.MONOSPACE, .1f, Color.rgb(255, 178, 82));
        }
        drawLabel(canvas, mSnapshot.date, 0f, baseline(dateTop, dp(10f), Typeface.MONOSPACE, 9f), 9f,
            Typeface.MONOSPACE, .1f, Color.rgb(255, 138, 30));
    }

    private float drawSevenDigit(Canvas canvas, int index, float x, float y, float widthDp,
                                 float heightDp, float thickness, long now) {
        float alpha = lcdAlpha(progress(index, now, LCD_DURATION_MS));
        drawSevenSegments(canvas, mDigits[index], new RectF(x, y, x + dp(widthDp), y + dp(heightDp)),
            thickness, Color.rgb(255, 138, 30), alpha);
        return x + dp(widthDp);
    }

    private void drawLcdColon(Canvas canvas, float x, float y, float radiusDp, float offsetDp) {
        mFillPaint.setShader(null);
        mFillPaint.setColor(Color.rgb(255, 138, 30));
        canvas.drawCircle(x, y - dp(offsetDp), dp(radiusDp), mFillPaint);
        canvas.drawCircle(x, y + dp(offsetDp), dp(radiusDp), mFillPaint);
    }

    private void drawSevenSegments(Canvas canvas, char digit, RectF r, float thickness,
                                   int color, float alpha) {
        boolean[] on = sevenSegments(digit);
        float half = thickness / 2f;
        RectF[] segments = {
            new RectF(r.left + thickness, r.top, r.right - thickness, r.top + thickness),
            new RectF(r.right - thickness, r.top + thickness, r.right, r.centerY() - half),
            new RectF(r.right - thickness, r.centerY() + half, r.right, r.bottom - thickness),
            new RectF(r.left + thickness, r.bottom - thickness, r.right - thickness, r.bottom),
            new RectF(r.left, r.centerY() + half, r.left + thickness, r.bottom - thickness),
            new RectF(r.left, r.top + thickness, r.left + thickness, r.centerY() - half),
            new RectF(r.left + thickness, r.centerY() - half, r.right - thickness, r.centerY() + half)
        };
        mFillPaint.setShader(null);
        for (int i = 0; i < segments.length; i++) {
            if (!on[i]) {
                // Off segments give the panel a body instead of leaving holes in the glass.
                mFillPaint.setColor(withAlpha(color, 23));
                canvas.drawRoundRect(segments[i], half, half, mFillPaint);
                continue;
            }
            RectF glow = new RectF(segments[i]);
            glow.inset(-dp(1.4f), -dp(1.4f));
            mFillPaint.setColor(withAlpha(color, Math.round(80f * alpha)));
            canvas.drawRoundRect(glow, thickness, thickness, mFillPaint);
            mFillPaint.setColor(withAlpha(color, Math.round(255f * alpha)));
            canvas.drawRoundRect(segments[i], half, half, mFillPaint);
        }
    }

    private static boolean[] sevenSegments(char c) {
        int bits;
        switch (c) {
            case '0': bits = 0x3F; break;
            case '1': bits = 0x06; break;
            case '2': bits = 0x5B; break;
            case '3': bits = 0x4F; break;
            case '4': bits = 0x66; break;
            case '5': bits = 0x6D; break;
            case '6': bits = 0x7D; break;
            case '7': bits = 0x07; break;
            case '8': bits = 0x7F; break;
            case '9': bits = 0x6F; break;
            default: bits = 0;
        }
        boolean[] result = new boolean[7];
        for (int i = 0; i < result.length; i++) result[i] = (bits & (1 << i)) != 0;
        return result;
    }

    private static float lcdAlpha(float progress) {
        if (progress >= 1f) return 1f;
        return .18f + .82f * Math.min(1f, progress / .55f);
    }

    // ---- Minimal ----------------------------------------------------------

    private void drawFullMinimal(Canvas canvas, long now, float dateTop) {
        float metaX = drawMinimalTime(canvas, now, 42f, dp(34f), -.01f) + dp(META_GAP_DP);
        drawMinimalMeta(canvas, now, metaX, 15f, 9f, .14f);
        float dateWidth = trackedTextWidth(dateSpacedText(), lightTypeface(), 11f, 5f);
        drawTrackedLabel(canvas, dateSpacedText(), 0f,
            baseline(dateTop, dp(DATE_ROW_DP), lightTypeface(), 11f), 11f, 5f,
            Color.rgb(79, 214, 201), lightTypeface());
        drawRule(canvas, dateWidth + dp(8f), dateTop + dp(DATE_ROW_DP / 2f), 110f,
            withAlpha(Color.rgb(79, 214, 201), 77));
    }

    private void drawCompactMinimal(Canvas canvas, long now, float dateTop) {
        float metaX = drawMinimalTime(canvas, now, 30f, dp(22f), 0f) + dp(5f);
        drawMinimalMeta(canvas, now, metaX, 12f, 8f, .12f);
        drawTrackedLabel(canvas, mSnapshot.date, 0f, baseline(dateTop, dp(10f), lightTypeface(), 10f),
            10f, 3f, Color.rgb(79, 214, 201), lightTypeface());
    }

    private float drawMinimalTime(Canvas canvas, long now, float textDp, float bandHeight,
                                  float letterSpacing) {
        String hm = timeText();
        mPaint.setTypeface(thinTypeface());
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(dp(textDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float baseline = bandHeight / 2f - (mPaint.ascent() + mPaint.descent()) / 2f;
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            float p = c == ':' ? 1f : progress(digitIndex++, now, MINIMAL_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            mPaint.setColor(Color.rgb(242, 245, 242));
            mPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(String.valueOf(c), x, baseline + dp(6f) * (1f - eased), mPaint);
            x += mPaint.measureText(String.valueOf(c));
        }
        mPaint.setAlpha(255);
        mPaint.setLetterSpacing(0f);
        return x;
    }

    private void drawMinimalMeta(Canvas canvas, long now, float x, float secondsDp, float periodDp,
                                 float periodSpacing) {
        mPaint.setTypeface(lightTypeface());
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(secondsDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float baseline = baseline(0f, dp(secondsDp), lightTypeface(), secondsDp);
        mPaint.setTextSize(dp(secondsDp));
        float cursor = x;
        for (int i = 0; i < 2; i++) {
            float p = progress(4 + i, now, MINIMAL_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            mPaint.setColor(Color.rgb(242, 245, 242));
            mPaint.setAlpha(Math.round(209f * eased));
            String c = String.valueOf(mSnapshot.ss.charAt(i));
            canvas.drawText(c, cursor, baseline + dp(6f) * (1f - eased), mPaint);
            cursor += mPaint.measureText(c);
        }
        mPaint.setAlpha(255);
        if (mSnapshot.period.isEmpty()) return;
        drawLabel(canvas, mSnapshot.period, x,
            baseline(dp(secondsDp + META_STACK_GAP_DP), dp(periodDp), mediumTypeface(), periodDp),
            periodDp, mediumTypeface(), periodSpacing, Color.rgb(79, 214, 201));
    }

    // ---- LED matrix -------------------------------------------------------

    private void drawFullLed(Canvas canvas, long now, float dateTop) {
        float metaX = drawLedTime(canvas, now, 3f) + dp(META_GAP_DP);
        drawLedMeta(canvas, now, metaX, 1.8f, 1.2f);
        float dateCell = dp(1.3f);
        drawLedText(canvas, mSnapshot.date, 0f, dateTop + (dp(DATE_ROW_DP) - dateCell * 7f) / 2f,
            dateCell, Color.rgb(255, 176, 32), 255);
        drawRule(canvas, dotTextWidth(mSnapshot.date, dateCell) + dp(8f),
            dateTop + dp(DATE_ROW_DP / 2f), 90f, withAlpha(Color.rgb(255, 176, 32), 66));
    }

    private void drawCompactLed(Canvas canvas, long now, float dateTop) {
        float metaX = drawLedTime(canvas, now, 2.1f) + dp(5f);
        drawLedMeta(canvas, now, metaX, 1.4f, 1f);
        float dateCell = dp(1f);
        drawLedText(canvas, mSnapshot.date, 0f, dateTop + (dp(10f) - dateCell * 7f) / 2f, dateCell,
            Color.rgb(255, 176, 32), 255);
    }

    private float drawLedTime(Canvas canvas, long now, float cellDp) {
        float cell = dp(cellDp);
        String hm = timeText();
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            if (c == ':') {
                drawDotGlyph(canvas, c, x, 0f, cell, Color.rgb(63, 224, 224), 1f, 1f);
            } else {
                float p = progress(digitIndex++, now, LED_DURATION_MS);
                float eased = 1f - (1f - p) * (1f - p);
                drawDotGlyph(canvas, c, x, 0f, cell, Color.rgb(63, 224, 224),
                    .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            }
            x += dotGlyphAdvance(cell);
        }
        return x - cell;
    }

    private void drawLedMeta(Canvas canvas, long now, float x, float secondsCellDp,
                             float periodCellDp) {
        float secondsCell = dp(secondsCellDp);
        float cursor = x;
        for (int i = 0; i < 2; i++) {
            float p = progress(4 + i, now, LED_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            drawDotGlyph(canvas, mSnapshot.ss.charAt(i), cursor, 0f, secondsCell,
                Color.rgb(176, 108, 255), .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            cursor += dotGlyphAdvance(secondsCell);
        }
        if (mSnapshot.period.isEmpty()) return;
        drawLedText(canvas, mSnapshot.period, x, secondsCell * 7f + dp(META_STACK_GAP_DP),
            dp(periodCellDp), Color.rgb(176, 108, 255), 217);
    }

    private void drawLedText(Canvas canvas, String text, float x, float y, float cell, int color,
                             int alpha) {
        float cursor = x;
        int tinted = withAlpha(color, alpha);
        for (int i = 0; i < text.length(); i++) {
            drawDotGlyph(canvas, text.charAt(i), cursor, y, cell, tinted, 1f, 1f);
            cursor += dotGlyphAdvance(cell);
        }
    }

    private void drawDotGlyph(Canvas canvas, char c, float x, float y, float cell, int color,
                              float scale, float brightness) {
        String[] rows = dotPattern(c);
        float width = cell * 5f, height = cell * 7f;
        canvas.save();
        canvas.scale(scale, scale, x + width / 2f, y + height / 2f);
        mFillPaint.setShader(null);
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) != '1') continue;
                float cx = x + col * cell + cell / 2f;
                float cy = y + row * cell + cell / 2f;
                mFillPaint.setColor(withAlpha(color, Math.min(150, Math.round(65f * brightness))));
                canvas.drawCircle(cx, cy, cell * .62f, mFillPaint);
                mFillPaint.setColor(brighten(color, brightness));
                canvas.drawCircle(cx, cy, cell * .34f, mFillPaint);
            }
        }
        canvas.restore();
    }

    private static String[] dotPattern(char c) {
        switch (c) {
            case '0': return rows("01110","10001","10011","10101","11001","10001","01110");
            case '1': return rows("00100","01100","00100","00100","00100","00100","01110");
            case '2': return rows("01110","10001","00001","00010","00100","01000","11111");
            case '3': return rows("11110","00001","00001","01110","00001","00001","11110");
            case '4': return rows("00010","00110","01010","10010","11111","00010","00010");
            case '5': return rows("11111","10000","10000","11110","00001","00001","11110");
            case '6': return rows("01110","10000","10000","11110","10001","10001","01110");
            case '7': return rows("11111","00001","00010","00100","01000","01000","01000");
            case '8': return rows("01110","10001","10001","01110","10001","10001","01110");
            case '9': return rows("01110","10001","10001","01111","00001","00001","01110");
            case ':': return rows("00000","00100","00100","00000","00100","00100","00000");
            case 'A': return rows("01110","10001","10001","11111","10001","10001","10001");
            case 'B': return rows("11110","10001","10001","11110","10001","10001","11110");
            case 'C': return rows("01111","10000","10000","10000","10000","10000","01111");
            case 'D': return rows("11110","10001","10001","10001","10001","10001","11110");
            case 'E': return rows("11111","10000","10000","11110","10000","10000","11111");
            case 'F': return rows("11111","10000","10000","11110","10000","10000","10000");
            case 'G': return rows("01111","10000","10000","10111","10001","10001","01111");
            case 'H': return rows("10001","10001","10001","11111","10001","10001","10001");
            case 'I': return rows("01110","00100","00100","00100","00100","00100","01110");
            case 'J': return rows("00001","00001","00001","00001","10001","10001","01110");
            case 'L': return rows("10000","10000","10000","10000","10000","10000","11111");
            case 'M': return rows("10001","11011","10101","10101","10001","10001","10001");
            case 'N': return rows("10001","11001","10101","10011","10001","10001","10001");
            case 'O': return rows("01110","10001","10001","10001","10001","10001","01110");
            case 'P': return rows("11110","10001","10001","11110","10000","10000","10000");
            case 'R': return rows("11110","10001","10001","11110","10100","10010","10001");
            case 'S': return rows("01111","10000","10000","01110","00001","00001","11110");
            case 'T': return rows("11111","00100","00100","00100","00100","00100","00100");
            case 'U': return rows("10001","10001","10001","10001","10001","10001","01110");
            case 'V': return rows("10001","10001","10001","10001","10001","01010","00100");
            case 'W': return rows("10001","10001","10001","10101","10101","10101","01010");
            case 'Y': return rows("10001","10001","01010","00100","00100","00100","00100");
            default: return rows("00000","00000","00000","00000","00000","00000","00000");
        }
    }

    private static String[] rows(String... rows) {
        return rows;
    }

    private static float dotGlyphAdvance(float cell) {
        return cell * 6f;
    }

    private static float dotTextWidth(String text, float cell) {
        return text.isEmpty() ? 0f : dotGlyphAdvance(cell) * text.length() - cell;
    }

    // ---- Mono chip --------------------------------------------------------

    private void drawMonoChip(Canvas canvas) {
        float rowHeight = dp(17f);
        float top = Math.max(0f, (getHeight() - rowHeight) / 2f);
        float x = 0f;
        drawLabel(canvas, timeText(), x, baseline(top, rowHeight, Typeface.MONOSPACE, 15f), 15f,
            Typeface.MONOSPACE, .04f, mChromeOnSurface);
        x += spacedTextWidth(timeText(), Typeface.MONOSPACE, 15f, .04f) + dp(4f);
        drawLabel(canvas, monoSecondsText(), x, baseline(top, rowHeight, Typeface.MONOSPACE, 10f), 10f,
            Typeface.MONOSPACE, 0f, ColorUtils.setAlphaComponent(mChromeOnSurface, 153));
        x += spacedTextWidth(monoSecondsText(), Typeface.MONOSPACE, 10f, 0f) + dp(4f);
        drawLabel(canvas, mSnapshot.date, x, baseline(top, rowHeight, Typeface.MONOSPACE, 10f), 10f,
            Typeface.MONOSPACE, .14f, ColorUtils.setAlphaComponent(mChromeSecondary, 191));
    }

    // ---- Shared drawing/data helpers -------------------------------------

    private void drawLabel(Canvas canvas, String text, float x, float baseline, float textDp,
                           Typeface typeface, float letterSpacing, int color) {
        if (text.isEmpty()) return;
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(color);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, mPaint);
        mPaint.setLetterSpacing(0f);
    }

    /** Letter tracking expressed in dp rather than em, which the minimal date row uses. */
    private void drawTrackedLabel(Canvas canvas, String text, float x, float baseline, float textDp,
                                  float trackingDp, int color, Typeface typeface) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(color);
        mPaint.setAlpha(Color.alpha(color));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            canvas.drawText(c, cursor, baseline, mPaint);
            cursor += mPaint.measureText(c) + dp(trackingDp);
        }
    }

    private void drawRule(Canvas canvas, float x, float centerY, float widthDp, int color) {
        mFillPaint.setShader(null);
        mFillPaint.setColor(color);
        canvas.drawRect(x, centerY - dp(.5f), x + dp(widthDp), centerY + dp(.5f), mFillPaint);
    }

    /** Baseline that vertically centres {@code textDp} inside a band of {@code height}. */
    private float baseline(float top, float height, Typeface typeface, float textDp) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(textDp));
        return top + height / 2f - (mPaint.ascent() + mPaint.descent()) / 2f;
    }

    private float spacedTextWidth(String text, Typeface typeface, float textDp, float letterSpacing) {
        if (text.isEmpty()) return 0f;
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(dp(textDp));
        float width = mPaint.measureText(text);
        mPaint.setLetterSpacing(0f);
        return width;
    }

    private float trackedTextWidth(String text, Typeface typeface, float textDp, float trackingDp) {
        if (text.isEmpty()) return 0f;
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(textDp));
        return mPaint.measureText(text) + dp(trackingDp) * Math.max(0, text.length() - 1);
    }

    private String timeText() {
        return mSnapshot.hh + ":" + mSnapshot.mm;
    }

    private String monoSecondsText() {
        return mSnapshot.period.isEmpty() ? mSnapshot.ss : mSnapshot.ss + " " + mSnapshot.period;
    }

    private String dateSpacedText() {
        return mSnapshot.weekday + " " + mSnapshot.day + " " + mSnapshot.month;
    }

    private static Typeface thinTypeface() {
        return Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    private static Typeface lightTypeface() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    private static Typeface mediumTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private void updateTime(long wallTime, long animationTime) {
        ClockSnapshot next = snapshot(wallTime, TimeZone.getDefault(), mUseAmPm);
        char[] nextDigits = next.digits();
        if (mSnapshot == null) {
            System.arraycopy(nextDigits, 0, mDigits, 0, mDigits.length);
            System.arraycopy(nextDigits, 0, mOldDigits, 0, mOldDigits.length);
        } else {
            for (int i = 0; i < mDigits.length; i++) {
                if (mDigits[i] != nextDigits[i]) {
                    mOldDigits[i] = mDigits[i];
                    mDigits[i] = nextDigits[i];
                    mChangedAt[i] = animationTime;
                }
            }
        }
        mSnapshot = next;
        updateContentDescription();
        invalidate();
    }

    private void updateContentDescription() {
        if (mSnapshot == null) return;
        setContentDescription(mSnapshot.hh + ":" + mSnapshot.mm + ":" + mSnapshot.ss
            + (mSnapshot.period.isEmpty() ? "" : " " + mSnapshot.period)
            + ", " + mSnapshot.date + ", " + styleName() + " clock");
    }

    private String styleName() {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(mStyle)) return "LCD";
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(mStyle)) return "Minimal";
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(mStyle)) return "LED matrix";
        return "Flip";
    }

    private boolean hasRunningAnimation(long now) {
        long duration = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(mStyle)
            ? FLIP_DURATION_MS : TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(mStyle)
            ? LCD_DURATION_MS : TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(mStyle)
            ? MINIMAL_DURATION_MS : LED_DURATION_MS;
        for (long changedAt : mChangedAt) {
            if (changedAt > 0L && now - changedAt < duration) return true;
        }
        return false;
    }

    private float progress(int digit, long now, long duration) {
        if (mChangedAt[digit] <= 0L) return 1f;
        return clamp01((now - mChangedAt[digit]) / (float) duration);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int brighten(int color, float amount) {
        float scale = Math.max(1f, amount);
        return Color.rgb(Math.min(255, Math.round(Color.red(color) * scale)),
            Math.min(255, Math.round(Color.green(color) * scale)),
            Math.min(255, Math.round(Color.blue(color) * scale)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @NonNull
    static ClockSnapshot snapshot(long wallTime, @NonNull TimeZone zone) {
        return snapshot(wallTime, zone, false);
    }

    @NonNull
    static ClockSnapshot snapshot(long wallTime, @NonNull TimeZone zone, boolean useAmPm) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(wallTime);
        String hh = twoDigits(useAmPm ? calendar.get(Calendar.HOUR) == 0
            ? 12 : calendar.get(Calendar.HOUR) : calendar.get(Calendar.HOUR_OF_DAY));
        String mm = twoDigits(calendar.get(Calendar.MINUTE));
        String ss = twoDigits(calendar.get(Calendar.SECOND));
        String weekday = WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1];
        String day = twoDigits(calendar.get(Calendar.DAY_OF_MONTH));
        String month = MONTHS[calendar.get(Calendar.MONTH)];
        String period = useAmPm
            ? (calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM") : "";
        return new ClockSnapshot(hh, mm, ss, weekday, day, month, period);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    static final class ClockSnapshot {
        final String hh;
        final String mm;
        final String ss;
        final String weekday;
        final String day;
        final String month;
        final String date;
        final String period;

        ClockSnapshot(String hh, String mm, String ss, String weekday, String day, String month,
                      String period) {
            this.hh = hh;
            this.mm = mm;
            this.ss = ss;
            this.weekday = weekday;
            this.day = day;
            this.month = month;
            this.date = weekday + " " + day + " " + month;
            this.period = period;
        }

        char[] digits() {
            return (hh + mm + ss).toCharArray();
        }
    }
}
