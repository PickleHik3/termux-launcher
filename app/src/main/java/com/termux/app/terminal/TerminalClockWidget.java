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

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Clock widget for the modular top-pane slot. It owns no gestures and sizes all four renderers from
 * its measured bounds, so the slot can later be moved or resized independently.
 */
public final class TerminalClockWidget extends View {

    private static final String[] WEEKDAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final long FLIP_DURATION_MS = 560L;
    private static final long LCD_DURATION_MS = 280L;
    private static final long MINIMAL_DURATION_MS = 350L;
    private static final long LED_DURATION_MS = 320L;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final Camera mCamera = new Camera();
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
    private ClockSnapshot mSnapshot;
    private boolean mTickerRunning;
    private boolean mUseAmPm;

    public TerminalClockWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
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
        invalidate();
        updateContentDescription();
    }

    @NonNull
    String getStyle() {
        return mStyle;
    }

    public void setUseAmPm(boolean useAmPm) {
        if (mUseAmPm == useAmPm) return;
        mUseAmPm = useAmPm;
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSnapshot == null || getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawLcd(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawMinimal(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawLed(canvas, now);
                break;
            default:
                drawFlip(canvas, now);
                break;
        }
        if (hasRunningAnimation(now)) postInvalidateOnAnimation();
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

    // ---- Split-flap -------------------------------------------------------

    private void drawFlip(Canvas canvas, long now) {
        float scale = Math.min(1f, getHeight() / dp(64f));
        float width = dp(196f) * scale;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - dp(64f) * scale) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);

        float x = 0f;
        x = drawFlipDigit(canvas, 0, x, 0f, 30f, 44f, 36f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 1, x, 0f, 30f, 44f, 36f, now);
        x += dp(5f);
        drawFlipColon(canvas, x, dp(22f));
        x += dp(13f);
        x = drawFlipDigit(canvas, 2, x, 0f, 30f, 44f, 36f, now);
        x += dp(2f);
        x = drawFlipDigit(canvas, 3, x, 0f, 30f, 44f, 36f, now);
        x += dp(8f);
        x = drawFlipDigit(canvas, 4, x, dp(7f), 20f, 30f, 24f, now);
        x += dp(2f);
        drawFlipDigit(canvas, 5, x, dp(7f), 20f, 30f, 24f, now);
        if (!mSnapshot.period.isEmpty()) {
            drawCenteredLabel(canvas, mSnapshot.period, dp(186f), dp(45f), 7f,
                Color.rgb(176, 208, 202), Typeface.DEFAULT_BOLD, Paint.Align.CENTER);
        }
        drawFlipDate(canvas, dp(98f), dp(58f));
        canvas.restore();
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

    private void drawFlipColon(Canvas canvas, float centerX, float centerY) {
        mFillPaint.setColor(Color.rgb(199, 207, 202));
        canvas.drawCircle(centerX, centerY - dp(5.5f), dp(2.5f), mFillPaint);
        canvas.drawCircle(centerX, centerY + dp(5.5f), dp(2.5f), mFillPaint);
    }

    private void drawFlipDate(Canvas canvas, float centerX, float centerY) {
        String[] tags = {mSnapshot.weekday, mSnapshot.day, mSnapshot.month};
        float[] widths = {28f, 22f, 28f};
        float gap = dp(4f), total = dp(widths[0] + widths[1] + widths[2]) + gap * 2f;
        float x = centerX - total / 2f;
        for (int i = 0; i < tags.length; i++) {
            float w = dp(widths[i]);
            RectF tag = new RectF(x, centerY - dp(7f), x + w, centerY + dp(7f));
            mFillPaint.setShader(new LinearGradient(0f, tag.top, 0f, tag.bottom,
                new int[] {Color.rgb(44, 53, 55), Color.rgb(12, 16, 17), Color.rgb(35, 43, 45)},
                new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(tag, dp(4f), dp(4f), mFillPaint);
            mFillPaint.setShader(null);
            mPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mPaint.setTextSize(dp(10f));
            mPaint.setColor(Color.rgb(233, 237, 233));
            mPaint.setTextAlign(Paint.Align.CENTER);
            float baseline = tag.centerY() - (mPaint.ascent() + mPaint.descent()) / 2f;
            canvas.drawText(tags[i], tag.centerX(), baseline, mPaint);
            x += w + gap;
        }
    }

    // ---- LCD --------------------------------------------------------------

    private void drawLcd(Canvas canvas, long now) {
        float scale = Math.min(1f, getHeight() / dp(62f));
        float left = getWidth() / 2f - dp(70f) * scale;
        float top = (getHeight() - dp(62f) * scale) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        float x = 0f;
        x = drawSevenDigit(canvas, 0, x, 0f, 20f, 38f, dp(3.2f), now);
        x += dp(3f);
        x = drawSevenDigit(canvas, 1, x, 0f, 20f, 38f, dp(3.2f), now);
        drawLcdColon(canvas, x + dp(5f), dp(19f));
        x += dp(13f);
        x = drawSevenDigit(canvas, 2, x, 0f, 20f, 38f, dp(3.2f), now);
        x += dp(3f);
        x = drawSevenDigit(canvas, 3, x, 0f, 20f, 38f, dp(3.2f), now);
        x += dp(6f);
        x = drawSevenDigit(canvas, 4, x, dp(18f), 11f, 20f, dp(2f), now);
        x += dp(2f);
        drawSevenDigit(canvas, 5, x, dp(18f), 11f, 20f, dp(2f), now);
        if (!mSnapshot.period.isEmpty()) {
            drawCenteredLabel(canvas, mSnapshot.period, dp(132f), dp(48f), 8f,
                Color.rgb(255, 178, 82), Typeface.DEFAULT_BOLD, Paint.Align.CENTER);
        }
        drawCenteredLabel(canvas, mSnapshot.date, dp(70f), dp(59f), 13f,
            Color.rgb(255, 138, 30), Typeface.MONOSPACE, Paint.Align.CENTER);
        canvas.restore();
    }

    private float drawSevenDigit(Canvas canvas, int index, float x, float y, float widthDp,
                                 float heightDp, float thickness, long now) {
        float alpha = lcdAlpha(progress(index, now, LCD_DURATION_MS));
        drawSevenSegments(canvas, mDigits[index], new RectF(x, y, x + dp(widthDp), y + dp(heightDp)),
            thickness, Color.rgb(255, 138, 30), alpha);
        return x + dp(widthDp);
    }

    private void drawLcdColon(Canvas canvas, float x, float y) {
        mFillPaint.setColor(Color.rgb(255, 138, 30));
        canvas.drawCircle(x, y - dp(6f), dp(2f), mFillPaint);
        canvas.drawCircle(x, y + dp(6f), dp(2f), mFillPaint);
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
        for (int i = 0; i < segments.length; i++) {
            if (!on[i]) continue;
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

    private void drawMinimal(Canvas canvas, long now) {
        float scale = Math.min(1f, getHeight() / dp(60f));
        float top = (getHeight() - dp(60f) * scale) / 2f;
        canvas.save();
        canvas.translate(getWidth() / 2f, top);
        canvas.scale(scale, scale);
        mPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        mPaint.setTextSize(dp(48f));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float[] widths = new float[5];
        String hm = mSnapshot.hh + ":" + mSnapshot.mm;
        float total = 0f;
        for (int i = 0; i < hm.length(); i++) {
            widths[i] = mPaint.measureText(String.valueOf(hm.charAt(i)));
            total += widths[i];
        }
        mPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mPaint.setTextSize(dp(16f));
        float secondsWidth = mPaint.measureText(mSnapshot.ss) + dp(3f);
        float x = -(total + secondsWidth) / 2f;
        mPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        mPaint.setTextSize(dp(48f));
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            float p = c == ':' ? 1f : progress(digitIndex++, now, MINIMAL_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            mPaint.setColor(Color.rgb(242, 245, 242));
            mPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(String.valueOf(c), x, dp(41f) + dp(6f) * (1f - eased), mPaint);
            x += widths[i];
        }
        mPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mPaint.setTextSize(dp(16f));
        for (int i = 0; i < 2; i++) {
            float p = progress(4 + i, now, MINIMAL_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            mPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(String.valueOf(mSnapshot.ss.charAt(i)), x + dp(3f),
                dp(15f) + dp(6f) * (1f - eased), mPaint);
            x += mPaint.measureText(String.valueOf(mSnapshot.ss.charAt(i)));
        }
        if (!mSnapshot.period.isEmpty()) {
            mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mPaint.setTextSize(dp(9f));
            mPaint.setColor(Color.rgb(79, 214, 201));
            canvas.drawText(mSnapshot.period, x + dp(6f), dp(28f), mPaint);
        }
        mPaint.setAlpha(255);
        drawSpacedLabel(canvas, mSnapshot.weekday + "  " + mSnapshot.day + "  " + mSnapshot.month,
            0f, dp(59f), 12f, 7f, Color.rgb(79, 214, 201),
            Typeface.create("sans-serif-light", Typeface.NORMAL));
        canvas.restore();
    }

    // ---- LED matrix -------------------------------------------------------

    private void drawLed(Canvas canvas, long now) {
        float scale = Math.min(1f, getHeight() / dp(60f));
        float top = (getHeight() - dp(60f) * scale) / 2f;
        canvas.save();
        canvas.translate(getWidth() / 2f, top);
        canvas.scale(scale, scale);
        float largeCell = dp(3.3f), smallCell = dp(2.15f);
        float mainWidth = dotTextWidth(mSnapshot.hh + ":" + mSnapshot.mm, largeCell);
        float secondsWidth = dotTextWidth(mSnapshot.ss, smallCell);
        float periodCell = dp(1.35f);
        float periodWidth = mSnapshot.period.isEmpty() ? 0f
            : dp(4f) + dotTextWidth(mSnapshot.period, periodCell);
        float x = -(mainWidth + dp(5f) + secondsWidth + periodWidth) / 2f;
        int digitIndex = 0;
        String hm = mSnapshot.hh + ":" + mSnapshot.mm;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            float advance = dotGlyphAdvance(largeCell);
            if (c == ':') {
                drawDotGlyph(canvas, c, x, 0f, largeCell, Color.rgb(63, 224, 224), 1f, 1f);
            } else {
                float p = progress(digitIndex++, now, LED_DURATION_MS);
                float eased = 1f - (1f - p) * (1f - p);
                drawDotGlyph(canvas, c, x, 0f, largeCell, Color.rgb(63, 224, 224),
                    .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            }
            x += advance;
        }
        x += dp(5f);
        for (int i = 0; i < 2; i++) {
            float p = progress(4 + i, now, LED_DURATION_MS);
            float eased = 1f - (1f - p) * (1f - p);
            drawDotGlyph(canvas, mSnapshot.ss.charAt(i), x, dp(1f), smallCell,
                Color.rgb(176, 108, 255), .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            x += dotGlyphAdvance(smallCell);
        }
        if (!mSnapshot.period.isEmpty()) {
            x += dp(4f);
            for (int i = 0; i < mSnapshot.period.length(); i++) {
                drawDotGlyph(canvas, mSnapshot.period.charAt(i), x, dp(9f), periodCell,
                    Color.rgb(176, 108, 255), 1f, 1f);
                x += dotGlyphAdvance(periodCell);
            }
        }

        float dateCell = dp(1.1f);
        String date = ">>> " + mSnapshot.date + " <<<";
        float dateX = -dotTextWidth(date, dateCell) / 2f;
        for (int i = 0; i < date.length(); i++) {
            char c = date.charAt(i);
            int color = c == '>' || c == '<' ? Color.rgb(63, 127, 224) : Color.rgb(255, 176, 32);
            drawDotGlyph(canvas, c, dateX, dp(35f), dateCell, color, 1f, 1f);
            dateX += dotGlyphAdvance(dateCell);
        }
        canvas.restore();
    }

    private void drawDotGlyph(Canvas canvas, char c, float x, float y, float cell, int color,
                              float scale, float brightness) {
        String[] rows = dotPattern(c);
        float width = cell * 5f, height = cell * 7f;
        canvas.save();
        canvas.scale(scale, scale, x + width / 2f, y + height / 2f);
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
            case '>': return rows("10000","01000","00100","00010","00100","01000","10000");
            case '<': return rows("00001","00010","00100","01000","00100","00010","00001");
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

    // ---- Shared drawing/data helpers -------------------------------------

    private void drawCenteredLabel(Canvas canvas, String text, float x, float baseline, float textDp,
                                   int color, Typeface typeface, Paint.Align align) {
        mPaint.setTypeface(typeface);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(color);
        mPaint.setAlpha(255);
        mPaint.setTextAlign(align);
        canvas.drawText(text, x, baseline, mPaint);
    }

    private void drawSpacedLabel(Canvas canvas, String text, float centerX, float baseline,
                                 float textDp, float spacingDp, int color, Typeface typeface) {
        mPaint.setTypeface(typeface);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(color);
        mPaint.setAlpha(255);
        mPaint.setTextAlign(Paint.Align.LEFT);
        float spacing = dp(spacingDp), total = 0f;
        for (int i = 0; i < text.length(); i++) total += mPaint.measureText(String.valueOf(text.charAt(i)));
        total += spacing * Math.max(0, text.length() - 1);
        float x = centerX - total / 2f;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            canvas.drawText(c, x, baseline, mPaint);
            x += mPaint.measureText(c) + spacing;
        }
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
