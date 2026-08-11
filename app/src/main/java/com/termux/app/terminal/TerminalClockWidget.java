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
 * Clock widget for the modular top-pane slot. Every face shares one grid: a left-aligned time band
 * with the seconds and period folded onto its baseline, and — in the full form — a date row that
 * ends in a hairline running to the right gutter, so the clock lines up with the status row below
 * instead of stopping at an arbitrary width. Colors come from the Material roles
 * ({@code termuxColorPrimary} / {@code termuxColorSecondary} / {@code termuxColorOnSurface}), which
 * are wallpaper-derived, rather than per-face literals.
 *
 * <p>The widget reports its content width so the slot can hand the remaining space to media or
 * pinned notifications, and it compresses through {@link TopPaneClockForm} instead of ever changing
 * the pane height.
 */
public final class TerminalClockWidget extends View {

    private static final String[] WEEKDAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final long FLIP_DURATION_MS = 560L;
    private static final long LCD_DURATION_MS = 280L;
    private static final long TEXT_DURATION_MS = 350L;
    private static final long LED_DURATION_MS = 320L;

    /** Shared grid: the date row, and the gap between the date text and its trailing hairline. */
    private static final float DATE_ROW_DP = 11f;
    private static final float RULE_GAP_DP = 7f;
    private static final float SLOT_HEIGHT_DP = 68f;
    private static final float TAPE_TRACK_BLOCK_DP = 12.5f;

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
    private float mFullPresentationProgress;

    private int mPrimary;
    private int mSecondary;
    private int mOnSurface;
    private int mPrimaryLine;
    private int mSecondaryQuiet;
    private int mDateInk;
    private int mRuleColor;
    private int mTrackColor;
    private int mTrackLabel;

    public TerminalClockWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        resolveChromeColors();
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
    }

    public void setStyle(@Nullable String style) {
        String normalized = isKnownStyle(style)
            ? style : TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP;
        if (normalized.equals(mStyle)) return;
        mStyle = normalized;
        requestLayout();
        invalidate();
        updateContentDescription();
    }

    private static boolean isKnownStyle(@Nullable String style) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(style);
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

    /** Host-owned presentation channel; the clock itself owns no competing animation loop. */
    public void setFullPresentationProgress(float progress) {
        float clamped = Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
        if (Math.abs(clamped - mFullPresentationProgress) < .0001f) return;
        mFullPresentationProgress = clamped;
        requestLayout();
        invalidate();
    }

    public float getFullPresentationProgress() { return mFullPresentationProgress; }

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

    /** Every face paints out of these three roles, so a wallpaper change re-tints the whole grid. */
    private void resolveChromeColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mSecondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mPrimary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mPrimaryLine = alpha(mPrimary, .45f);
        mSecondaryQuiet = alpha(mSecondary, .5f);
        mDateInk = alpha(mOnSurface, .62f);
        mRuleColor = alpha(mPrimary, .22f);
        mTrackColor = alpha(mSecondary, .18f);
        mTrackLabel = alpha(mSecondary, .45f);
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
                timeRow = dp(94f) + dp(6f)
                    + metaWidth(12f, 7f, 4f, Typeface.MONOSPACE, Typeface.MONOSPACE, .16f, true);
                dateRow = spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 9.5f, .2f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                timeRow = spacedTextWidth(timeText(), thinTypeface(), 38f, -.02f) + dp(6f)
                    + metaWidth(15f, 7.5f, 6f, lightTypeface(), mediumTypeface(), .18f, false);
                dateRow = spacedTextWidth(mSnapshot.date, Typeface.DEFAULT, 9.5f, .22f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                timeRow = dotTextWidth(timeText(), dp(3.4f)) + dp(7f) + ledMetaWidth(1.6f, 6.5f, 4f);
                dateRow = dotTextWidth(mSnapshot.date, dp(1.3f));
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                timeRow = spacedTextWidth(timeText(), Typeface.MONOSPACE, 28f, -.02f) + dp(5f)
                    + metaWidth(11f, 7f, 5f, Typeface.MONOSPACE, Typeface.MONOSPACE, .16f, false)
                    + dp(10f) + spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 9f, .2f);
                dateRow = spacedTextWidth(trackLabelText(), Typeface.MONOSPACE, 6f, .2f);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                timeRow = spacedTextWidth(timeText(), Typeface.DEFAULT_BOLD, 39f, -.045f) + dp(6f)
                    + stackedMetaWidth(11f, 7f, mediumTypeface(), Typeface.DEFAULT_BOLD, .16f);
                dateRow = spacedTextWidth(mSnapshot.date, Typeface.DEFAULT_BOLD, 8.5f, .26f);
                break;
            default:
                timeRow = dp(94f) + dp(5f)
                    + metaWidth(13f, 7.5f, 4f, Typeface.DEFAULT, mediumTypeface(), .16f, true);
                dateRow = spacedTextWidth(mSnapshot.date, mediumTypeface(), 9.5f, .22f);
                break;
        }
        return Math.max(timeRow, dateRow + dp(RULE_GAP_DP) + dp(24f));
    }

    private float compactContentWidth() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return dp(61f) + dp(5f)
                    + metaWidth(9f, 6f, 3f, Typeface.MONOSPACE, Typeface.MONOSPACE, .16f, false);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return spacedTextWidth(timeText(), thinTypeface(), 26f, -.02f) + dp(5f)
                    + metaWidth(10.5f, 6.5f, 5f, lightTypeface(), mediumTypeface(), .18f, false);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return dotTextWidth(timeText(), dp(2.2f)) + dp(5f) + ledMetaWidth(1.2f, 5.5f, 3f);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return Math.max(dp(100f),
                    spacedTextWidth(timeText(), Typeface.MONOSPACE, 18f, -.02f) + dp(4f)
                        + metaWidth(8f, 6f, 4f, Typeface.MONOSPACE, Typeface.MONOSPACE, .16f, false));
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return spacedTextWidth(timeText(), Typeface.DEFAULT_BOLD, 27f, -.045f) + dp(5f)
                    + stackedMetaWidth(9f, 6f, mediumTypeface(), Typeface.DEFAULT_BOLD, .16f);
            default:
                return dp(64.5f) + dp(4f)
                    + metaWidth(9.5f, 6.5f, 3f, Typeface.DEFAULT, mediumTypeface(), .16f, false);
        }
    }

    private float monoContentWidth() {
        float width = spacedTextWidth(timeText(), Typeface.MONOSPACE, 14f, .02f) + dp(5f)
            + spacedTextWidth(mSnapshot.ss, Typeface.MONOSPACE, 9f, 0f) + dp(5f);
        if (!mSnapshot.period.isEmpty()) {
            width += spacedTextWidth(mSnapshot.period, Typeface.MONOSPACE, 6.5f, .16f) + dp(5f);
        }
        return width + dp(2f) + dp(5f)
            + spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 9f, .16f);
    }

    /** Width of the folded meta run: seconds, then the period, sharing the time baseline. */
    private float metaWidth(float secondsDp, float periodDp, float gapDp, Typeface secondsFace,
                            Typeface periodFace, float periodSpacing, boolean boxedPeriod) {
        float width = spacedTextWidth(mSnapshot.ss, secondsFace, secondsDp, 0f);
        if (mSnapshot.period.isEmpty()) return width;
        width += dp(gapDp) + spacedTextWidth(mSnapshot.period, periodFace, periodDp, periodSpacing);
        if (boxedPeriod) width += dp(8f);
        return width;
    }

    /**
     * Width of the slab's stacked meta column: the wider of the seconds and the period, since they
     * share a left edge instead of following each other along the baseline.
     */
    private float stackedMetaWidth(float secondsDp, float periodDp, Typeface secondsFace,
                                   Typeface periodFace, float periodSpacing) {
        float seconds = spacedTextWidth(mSnapshot.ss, secondsFace, secondsDp, 0f);
        if (mSnapshot.period.isEmpty()) return seconds;
        return Math.max(seconds,
            spacedTextWidth(mSnapshot.period, periodFace, periodDp, periodSpacing));
    }

    private float ledMetaWidth(float secondsCellDp, float periodDp, float gapDp) {
        float width = dotTextWidth(mSnapshot.ss, dp(secondsCellDp));
        if (mSnapshot.period.isEmpty()) return width;
        return width + dp(gapDp)
            + spacedTextWidth(mSnapshot.period, mediumTypeface(), periodDp, .16f);
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
        float columnDp = bandDp + dateGapDp + fullDateBlockDp();
        float scale = Math.min(1f, getHeight() / dp(columnDp));
        canvas.save();
        canvas.translate(0f, Math.max(0f, (getHeight() - dp(columnDp) * scale) / 2f));
        canvas.scale(scale, scale);
        float dateTop = dp(bandDp + dateGapDp);
        // The hairline and the tape track run to the pane gutter, so they need the unscaled edge.
        float right = getWidth() / Math.max(.01f, scale);
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawFullLcd(canvas, now, dateTop, right);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawFullMinimal(canvas, now, dateTop, right);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawFullLed(canvas, now, dateTop, right);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                drawFullTape(canvas, now, dateTop, right);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                drawFullSlab(canvas, now, dateTop, right);
                break;
            default:
                drawFullFlip(canvas, now, dateTop, right);
                break;
        }
        canvas.restore();
    }

    private void drawCompact(Canvas canvas, long now) {
        float columnDp = compactColumnHeightDp();
        float scale = Math.min(1f, getHeight() / dp(columnDp));
        canvas.save();
        canvas.translate(0f, Math.max(0f, (getHeight() - dp(columnDp) * scale) / 2f));
        canvas.scale(scale, scale);
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawCompactLcd(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawCompactMinimal(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawCompactLed(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                drawCompactTape(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                drawCompactSlab(canvas, now);
                break;
            default:
                drawCompactFlip(canvas, now);
                break;
        }
        canvas.restore();
    }

    private float fullBandHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 26f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 22f;
            default:
                return 34f;
        }
    }

    private float fullDateGapDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return 3f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 6f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 7f;
            default:
                return 5f;
        }
    }

    /** Tape trades the date row for a minute track plus its label; everyone else gets the date row. */
    private float fullDateBlockDp() {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(mStyle)
            ? TAPE_TRACK_BLOCK_DP : DATE_ROW_DP;
    }

    /** Compact drops the date row on every face; tape keeps a short minute track instead. */
    private float compactColumnHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 16f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 21f;
            default:
                return 24f;
        }
    }

    // ---- Split-flap -------------------------------------------------------

    private void drawFullFlip(Canvas canvas, long now, float dateTop, float right) {
        float x = drawFlipCards(canvas, now, 22f, 34f, 27f, 2f);
        drawMetaRow(canvas, x + dp(5f), dp(29f), 13f, 7.5f, 4f, Typeface.DEFAULT, mediumTypeface(),
            .16f, true, now, FLIP_DURATION_MS);
        drawDateRow(canvas, dateTop, right, mSnapshot.date, mediumTypeface(), 9.5f, .22f);
    }

    private void drawCompactFlip(Canvas canvas, long now) {
        float x = drawFlipCards(canvas, now, 15f, 24f, 19f, 1.5f);
        drawMetaRow(canvas, x + dp(4f), dp(21f), 9.5f, 6.5f, 3f, Typeface.DEFAULT, mediumTypeface(),
            .16f, false, now, FLIP_DURATION_MS);
    }

    /** Four evenly spaced cards: the refined face drops the colon so the row reads as one block. */
    private float drawFlipCards(Canvas canvas, long now, float widthDp, float heightDp,
                                float textDp, float gapDp) {
        float x = 0f;
        for (int digit = 0; digit < 4; digit++) {
            x = drawFlipDigit(canvas, digit, x, 0f, widthDp, heightDp, textDp, now);
            if (digit < 3) x += dp(gapDp);
        }
        return x;
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
        mFillPaint.setColor(Color.argb(140, 0, 0, 0));
        canvas.drawRect(x, y + half - dp(.5f), x + w, y + half + dp(.5f), mFillPaint);
        return x + w;
    }

    private void drawFlipHalf(Canvas canvas, RectF card, boolean top, char digit, float textDp) {
        float half = card.height() / 2f;
        RectF clip = top
            ? new RectF(card.left, card.top, card.right, card.top + half)
            : new RectF(card.left, card.top + half, card.right, card.bottom);
        canvas.save();
        canvas.clipRect(clip);
        mFillPaint.setShader(new LinearGradient(0f, card.top, 0f, card.bottom,
            alpha(mOnSurface, top ? .13f : .06f), alpha(mOnSurface, top ? .08f : .03f),
            Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, dp(4f), dp(4f), mFillPaint);
        mFillPaint.setShader(null);
        mPaint.setTypeface(mediumTypeface());
        mPaint.setLetterSpacing(-.02f);
        mPaint.setTextSize(dp(textDp));
        mPaint.setColor(mOnSurface);
        mPaint.setTextAlign(Paint.Align.CENTER);
        float baseline = card.centerY() - (mPaint.ascent() + mPaint.descent()) / 2f;
        canvas.drawText(String.valueOf(digit), card.centerX(), baseline, mPaint);
        mPaint.setLetterSpacing(0f);
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

    // ---- LCD --------------------------------------------------------------

    private void drawFullLcd(Canvas canvas, long now, float dateTop, float right) {
        float x = drawLcdDigits(canvas, now, 19f, 34f, 3f, 3f, 6f, 17f, 4f, 1.75f);
        drawMetaRow(canvas, x + dp(6f), dp(30f), 12f, 7f, 4f, Typeface.MONOSPACE,
            Typeface.MONOSPACE, .16f, true, now, LCD_DURATION_MS);
        drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.MONOSPACE, 9.5f, .2f);
    }

    private void drawCompactLcd(Canvas canvas, long now) {
        float x = drawLcdDigits(canvas, now, 12f, 22f, 2f, 2f, 5f, 11f, 2.75f, 1.25f);
        drawMetaRow(canvas, x + dp(5f), dp(20f), 9f, 6f, 3f, Typeface.MONOSPACE, Typeface.MONOSPACE,
            .16f, false, now, LCD_DURATION_MS);
    }

    private float drawLcdDigits(Canvas canvas, long now, float widthDp, float heightDp,
                                float thicknessDp, float gapDp, float colonWidthDp,
                                float colonCenterDp, float colonOffsetDp, float colonRadiusDp) {
        float x = 0f;
        x = drawSevenDigit(canvas, 0, x, widthDp, heightDp, dp(thicknessDp), now) + dp(gapDp);
        x = drawSevenDigit(canvas, 1, x, widthDp, heightDp, dp(thicknessDp), now) + dp(gapDp);
        drawLcdColon(canvas, x + dp(colonWidthDp) / 2f, dp(colonCenterDp), colonRadiusDp,
            colonOffsetDp);
        x += dp(colonWidthDp) + dp(gapDp);
        x = drawSevenDigit(canvas, 2, x, widthDp, heightDp, dp(thicknessDp), now) + dp(gapDp);
        return drawSevenDigit(canvas, 3, x, widthDp, heightDp, dp(thicknessDp), now);
    }

    private float drawSevenDigit(Canvas canvas, int index, float x, float widthDp,
                                 float heightDp, float thickness, long now) {
        float alpha = lcdAlpha(progress(index, now, LCD_DURATION_MS));
        drawSevenSegments(canvas, mDigits[index], new RectF(x, 0f, x + dp(widthDp), dp(heightDp)),
            thickness, mPrimary, alpha);
        return x + dp(widthDp);
    }

    private void drawLcdColon(Canvas canvas, float x, float y, float radiusDp, float offsetDp) {
        mFillPaint.setShader(null);
        mFillPaint.setColor(mPrimary);
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
                mFillPaint.setColor(alpha(color, .08f));
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

    private void drawFullMinimal(Canvas canvas, long now, float dateTop, float right) {
        float baseline = baseline(0f, dp(34f), thinTypeface(), 38f);
        float x = drawFadingTime(canvas, now, thinTypeface(), 38f, -.02f, baseline);
        drawMetaRow(canvas, x + dp(6f), baseline, 15f, 7.5f, 6f, lightTypeface(), mediumTypeface(),
            .18f, false, now, TEXT_DURATION_MS);
        drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.DEFAULT, 9.5f, .22f);
    }

    private void drawCompactMinimal(Canvas canvas, long now) {
        float baseline = baseline(0f, dp(22f), thinTypeface(), 26f);
        float x = drawFadingTime(canvas, now, thinTypeface(), 26f, -.02f, baseline);
        drawMetaRow(canvas, x + dp(5f), baseline, 10.5f, 6.5f, 5f, lightTypeface(),
            mediumTypeface(), .18f, false, now, TEXT_DURATION_MS);
    }

    // ---- Slab -------------------------------------------------------------

    private void drawFullSlab(Canvas canvas, long now, float dateTop, float right) {
        float baseline = baseline(0f, dp(34f), Typeface.DEFAULT_BOLD, 39f);
        float x = drawFadingTime(canvas, now, Typeface.DEFAULT_BOLD, 39f, -.045f, baseline);
        drawStackedMetaColumn(canvas, x + dp(6f), baseline, 11f, 7f, mediumTypeface(),
            Typeface.DEFAULT_BOLD, .16f, now, TEXT_DURATION_MS);
        drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.DEFAULT_BOLD, 8.5f, .26f);
    }

    private void drawCompactSlab(Canvas canvas, long now) {
        float baseline = baseline(0f, dp(22f), Typeface.DEFAULT_BOLD, 27f);
        float x = drawFadingTime(canvas, now, Typeface.DEFAULT_BOLD, 27f, -.045f, baseline);
        drawStackedMetaColumn(canvas, x + dp(5f), baseline, 9f, 6f, mediumTypeface(),
            Typeface.DEFAULT_BOLD, .16f, now, TEXT_DURATION_MS);
    }

    // ---- Tape -------------------------------------------------------------

    private void drawFullTape(Canvas canvas, long now, float trackTop, float right) {
        float baseline = baseline(0f, dp(22f), Typeface.MONOSPACE, 28f);
        float x = drawFadingTime(canvas, now, Typeface.MONOSPACE, 28f, -.02f, baseline);
        drawMetaRow(canvas, x + dp(5f), baseline, 11f, 7f, 5f, Typeface.MONOSPACE,
            Typeface.MONOSPACE, .16f, false, now, TEXT_DURATION_MS);
        float dateWidth = spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, 9f, .2f);
        drawLabel(canvas, mSnapshot.date, Math.max(x, right - dateWidth), baseline, 9f,
            Typeface.MONOSPACE, .2f, mDateInk);
        drawMinuteTrack(canvas, trackTop, right, 1.5f);
        drawLabel(canvas, trackLabelText(), 0f,
            baseline(trackTop + dp(5.5f), dp(7f), Typeface.MONOSPACE, 6f), 6f, Typeface.MONOSPACE,
            .2f, mTrackLabel);
    }

    private void drawCompactTape(Canvas canvas, long now) {
        float baseline = baseline(0f, dp(15f), Typeface.MONOSPACE, 18f);
        float x = drawFadingTime(canvas, now, Typeface.MONOSPACE, 18f, -.02f, baseline);
        drawMetaRow(canvas, x + dp(4f), baseline, 8f, 6f, 4f, Typeface.MONOSPACE, Typeface.MONOSPACE,
            .16f, false, now, TEXT_DURATION_MS);
        drawMinuteTrack(canvas, dp(20f), dp(100f), 1f);
    }

    /** The minute as a fill: tape's stand-in for a seconds readout. */
    private void drawMinuteTrack(Canvas canvas, float top, float right, float heightDp) {
        float height = dp(heightDp);
        mFillPaint.setShader(null);
        mRect.set(0f, top, right, top + height);
        mFillPaint.setColor(mTrackColor);
        canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFillPaint);
        float fill = right * minuteFraction();
        if (fill <= 0f) return;
        mRect.set(0f, top, fill, top + height);
        mFillPaint.setColor(mPrimary);
        canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFillPaint);
    }

    // ---- LED matrix -------------------------------------------------------

    private void drawFullLed(Canvas canvas, long now, float dateTop, float right) {
        float x = drawLedTime(canvas, now, 3.4f);
        drawLedMeta(canvas, now, x + dp(7f), 26f, 1.6f, 6.5f, 4f);
        float dateCell = dp(1.3f);
        int dateColor = alpha(mOnSurface, .6f);
        drawLedText(canvas, mSnapshot.date, 0f, dateTop + (dp(DATE_ROW_DP) - dateCell * 7f) / 2f,
            dateCell, dateColor);
        drawRule(canvas, dotTextWidth(mSnapshot.date, dateCell) + dp(RULE_GAP_DP),
            dateTop + dp(DATE_ROW_DP / 2f), right, mRuleColor);
    }

    private void drawCompactLed(Canvas canvas, long now) {
        float x = drawLedTime(canvas, now, 2.2f);
        drawLedMeta(canvas, now, x + dp(5f), 16f, 1.2f, 5.5f, 3f);
    }

    private float drawLedTime(Canvas canvas, long now, float cellDp) {
        float cell = dp(cellDp);
        String hm = timeText();
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            if (c == ':') {
                drawDotGlyph(canvas, c, x, 0f, cell, mOnSurface, 1f, 1f);
            } else {
                float eased = ease(progress(digitIndex++, now, LED_DURATION_MS));
                drawDotGlyph(canvas, c, x, 0f, cell, mOnSurface,
                    .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            }
            x += dotGlyphAdvance(cell);
        }
        return x - cell;
    }

    /** Seconds dots and the period label sit on the time band's bottom edge, not in a column. */
    private void drawLedMeta(Canvas canvas, long now, float x, float bandDp, float secondsCellDp,
                             float periodDp, float gapDp) {
        float secondsCell = dp(secondsCellDp);
        float top = dp(bandDp) - dp(1f) - secondsCell * 7f;
        float cursor = x;
        for (int i = 0; i < 2; i++) {
            float eased = ease(progress(4 + i, now, LED_DURATION_MS));
            drawDotGlyph(canvas, mSnapshot.ss.charAt(i), cursor, top, secondsCell, mSecondary,
                .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            cursor += dotGlyphAdvance(secondsCell);
        }
        if (mSnapshot.period.isEmpty()) return;
        cursor = cursor - secondsCell + dp(gapDp);
        drawLabel(canvas, mSnapshot.period, cursor,
            baseline(dp(bandDp) - dp(periodDp) - dp(1f), dp(periodDp), mediumTypeface(), periodDp),
            periodDp, mediumTypeface(), .16f, mPrimary);
    }

    private void drawLedText(Canvas canvas, String text, float x, float y, float cell, int color) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            drawDotGlyph(canvas, text.charAt(i), cursor, y, cell, color, 1f, 1f);
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
        drawLabel(canvas, timeText(), x, baseline(top, rowHeight, Typeface.MONOSPACE, 14f), 14f,
            Typeface.MONOSPACE, .02f, mOnSurface);
        x += spacedTextWidth(timeText(), Typeface.MONOSPACE, 14f, .02f) + dp(5f);
        drawLabel(canvas, mSnapshot.ss, x, baseline(top, rowHeight, Typeface.MONOSPACE, 9f), 9f,
            Typeface.MONOSPACE, 0f, mSecondaryQuiet);
        x += spacedTextWidth(mSnapshot.ss, Typeface.MONOSPACE, 9f, 0f) + dp(5f);
        if (!mSnapshot.period.isEmpty()) {
            drawLabel(canvas, mSnapshot.period, x,
                baseline(top, rowHeight, Typeface.MONOSPACE, 6.5f), 6.5f, Typeface.MONOSPACE, .16f,
                mPrimary);
            x += spacedTextWidth(mSnapshot.period, Typeface.MONOSPACE, 6.5f, .16f) + dp(5f);
        }
        mFillPaint.setShader(null);
        mFillPaint.setColor(mRuleColor);
        canvas.drawCircle(x + dp(1f), top + rowHeight / 2f, dp(1f), mFillPaint);
        x += dp(2f) + dp(5f);
        drawLabel(canvas, mSnapshot.date, x, baseline(top, rowHeight, Typeface.MONOSPACE, 9f), 9f,
            Typeface.MONOSPACE, .16f, mDateInk);
    }

    // ---- Shared refined grid ---------------------------------------------

    /**
     * Seconds and period folded onto the time baseline. Seconds run around 40% of the time scale in
     * the secondary role at half strength, so they read as meta rather than as a third number.
     */
    private float drawMetaRow(Canvas canvas, float x, float baseline, float secondsDp,
                              float periodDp, float gapDp, Typeface secondsFace,
                              Typeface periodFace, float periodSpacing, boolean boxedPeriod,
                              long now, long duration) {
        mPaint.setTypeface(secondsFace);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(secondsDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float cursor = x;
        for (int i = 0; i < 2; i++) {
            float eased = ease(progress(4 + i, now, duration));
            mPaint.setColor(mSecondaryQuiet);
            mPaint.setAlpha(Math.round(Color.alpha(mSecondaryQuiet) * eased));
            String c = String.valueOf(mSnapshot.ss.charAt(i));
            canvas.drawText(c, cursor, baseline + dp(4f) * (1f - eased), mPaint);
            cursor += mPaint.measureText(c);
        }
        mPaint.setAlpha(255);
        if (mSnapshot.period.isEmpty()) return cursor - x;
        cursor += dp(gapDp);
        float periodWidth = spacedTextWidth(mSnapshot.period, periodFace, periodDp, periodSpacing);
        if (boxedPeriod) {
            mPaint.setTypeface(periodFace);
            mPaint.setTextSize(dp(periodDp));
            mRect.set(cursor, baseline + mPaint.ascent() - dp(1.5f),
                cursor + periodWidth + dp(6f), baseline + mPaint.descent() + dp(1.5f));
            mFillPaint.setShader(null);
            mFillPaint.setColor(mPrimaryLine);
            mFillPaint.setStyle(Paint.Style.STROKE);
            mFillPaint.setStrokeWidth(dp(1f));
            canvas.drawRoundRect(mRect, dp(2.5f), dp(2.5f), mFillPaint);
            mFillPaint.setStyle(Paint.Style.FILL);
            cursor += dp(3f);
        }
        drawLabel(canvas, mSnapshot.period, cursor, baseline, periodDp, periodFace, periodSpacing,
            mPrimary);
        cursor += periodWidth + (boxedPeriod ? dp(3f) : 0f);
        return cursor - x;
    }

    /**
     * The slab's meta as a column: seconds on the time baseline, period stacked directly above them
     * and sharing their left edge.
     *
     * <p>The slab's time band is its widest, so folding the period in beside the seconds pushed the
     * meta run out past the width the widget slot hands the clock — and the period, being last, was
     * the part the media strip clipped. Stacked, the column is only as wide as the wider of the two,
     * and the period is inside the block instead of hanging off its end.
     */
    private void drawStackedMetaColumn(Canvas canvas, float x, float baseline, float secondsDp,
                                       float periodDp, Typeface secondsFace, Typeface periodFace,
                                       float periodSpacing, long now, long duration) {
        mPaint.setTypeface(secondsFace);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(dp(secondsDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float secondsAscent = mPaint.ascent();
        float cursor = x;
        for (int i = 0; i < 2; i++) {
            float eased = ease(progress(4 + i, now, duration));
            mPaint.setColor(mSecondaryQuiet);
            mPaint.setAlpha(Math.round(Color.alpha(mSecondaryQuiet) * eased));
            String c = String.valueOf(mSnapshot.ss.charAt(i));
            canvas.drawText(c, cursor, baseline + dp(4f) * (1f - eased), mPaint);
            cursor += mPaint.measureText(c);
        }
        mPaint.setAlpha(255);
        if (mSnapshot.period.isEmpty()) return;
        // Clear of the seconds' own ascent, so the two never touch at any font scale.
        drawLabel(canvas, mSnapshot.period, x, baseline + secondsAscent - dp(2.5f), periodDp,
            periodFace, periodSpacing, mPrimary);
    }

    /** Time digits rising into place; shared by every text-drawn face. */
    private float drawFadingTime(Canvas canvas, long now, Typeface typeface, float textDp,
                                 float letterSpacing, float baseline) {
        String hm = timeText();
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(dp(textDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            float eased = c == ':' ? 1f : ease(progress(digitIndex++, now, TEXT_DURATION_MS));
            mPaint.setColor(mOnSurface);
            mPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(String.valueOf(c), x, baseline + dp(5f) * (1f - eased), mPaint);
            x += mPaint.measureText(String.valueOf(c));
        }
        mPaint.setAlpha(255);
        mPaint.setLetterSpacing(0f);
        return x;
    }

    /** Date text plus the hairline that carries it out to the right gutter. */
    private void drawDateRow(Canvas canvas, float top, float right, String text, Typeface typeface,
                             float textDp, float letterSpacing) {
        drawLabel(canvas, text, 0f, baseline(top, dp(DATE_ROW_DP), typeface, textDp), textDp,
            typeface, letterSpacing, mDateInk);
        drawRule(canvas, spacedTextWidth(text, typeface, textDp, letterSpacing) + dp(RULE_GAP_DP),
            top + dp(DATE_ROW_DP / 2f), right, mRuleColor);
    }

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

    private void drawRule(Canvas canvas, float x, float centerY, float right, int color) {
        if (right - x <= 0f) return;
        mFillPaint.setShader(null);
        mFillPaint.setColor(color);
        canvas.drawRect(x, centerY - dp(.5f), right, centerY + dp(.5f), mFillPaint);
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

    private String timeText() {
        return mSnapshot.hh + ":" + mSnapshot.mm;
    }

    private float minuteFraction() {
        return clamp01(secondsValue() / 60f);
    }

    private String trackLabelText() {
        return "MINUTE " + Math.round(minuteFraction() * 100f) + "%";
    }

    private int secondsValue() {
        try {
            return Integer.parseInt(mSnapshot.ss);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD: return "LCD";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL: return "Minimal";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED: return "LED matrix";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE: return "Tape";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB: return "Slab";
            default: return "Flip";
        }
    }

    private boolean hasRunningAnimation(long now) {
        long duration = styleDurationMs();
        for (long changedAt : mChangedAt) {
            if (changedAt > 0L && now - changedAt < duration) return true;
        }
        return false;
    }

    private long styleDurationMs() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return LCD_DURATION_MS;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return LED_DURATION_MS;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return FLIP_DURATION_MS;
            default:
                return TEXT_DURATION_MS;
        }
    }

    private float progress(int digit, long now, long duration) {
        if (mChangedAt[digit] <= 0L) return 1f;
        return clamp01((now - mChangedAt[digit]) / (float) duration);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float ease(float progress) {
        return 1f - (1f - progress) * (1f - progress);
    }

    private static int alpha(int color, float fraction) {
        return ColorUtils.setAlphaComponent(color,
            Math.round(Color.alpha(color) * clamp01(fraction)));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int brighten(int color, float amount) {
        float scale = Math.max(1f, amount);
        return Color.argb(Color.alpha(color),
            Math.min(255, Math.round(Color.red(color) * scale)),
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
