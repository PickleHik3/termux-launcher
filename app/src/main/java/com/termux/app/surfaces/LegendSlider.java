package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.LabelFormatter;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical Material 3 slider for the glass strips of the Appearance editor's cards (the
 * previous editor's Custom row, brought back on 2026-10-25), drawn by hand so the track is
 * exactly the column's height: 0 at the bottom edge, the maximum at the top edge, nothing
 * clipped, and the legend ("Frost · 20%") standing beside the track as vertical text that reads
 * upward. A marked slider ({@link #setMarked}) carries the card's "set here" dot after its legend. It follows the M3 slider spec's "M" size: a 28dp track with full outer corners and 2dp
 * inside corners at the handle, a 4 x 44dp handle with a 6dp gap to either track, and a 4dp stop
 * indicator at the far end of the inactive track. Active track and handle are {@code colorPrimary},
 * the inactive track {@code colorSecondaryContainer}, the legend {@code colorOnSurfaceVariant}.
 *
 * <p>The legend sits outside the track (2026-10-05, the developer's call after the inset legend
 * proved hard to read): the track takes the column's start and the legend the rest, the pair
 * centred in the column. The track narrows (never under 20dp) when the column cannot hold both at
 * 28dp. A legend that would overrun the column's height shrinks to 0.8x of LabelMedium first, then
 * gives up its name's tail.</p>
 *
 * <p>The API is the subset of Material's {@code Slider} the panel used: value, range, step,
 * listeners of the same shape, and a {@link LabelFormatter} as the legend's source. Accessibility
 * presents it as a SeekBar with range info; the legend is its content description's value.</p>
 */
public final class LegendSlider extends View {

    /** The same shape as Material's {@code Slider.OnChangeListener}. */
    public interface OnChangeListener {
        void onValueChange(@NonNull LegendSlider slider, float value, boolean fromUser);
    }

    /** The same shape as Material's {@code Slider.OnSliderTouchListener}. */
    public interface OnSliderTouchListener {
        void onStartTrackingTouch(@NonNull LegendSlider slider);

        void onStopTrackingTouch(@NonNull LegendSlider slider);
    }

    /**
     * The smallest a legend shrinks to, as a share of LabelMedium at the default font scale: a
     * long legend ("Corner radius · 40 dp") shrinks to fit its track rather than losing its name
     * to an ellipsis. At a larger font scale the floor stays the default reader's, so the legend
     * can give back some of the enlargement before it is cut, and never reads smaller than it does
     * for everyone else.
     */
    private static final float MIN_TEXT_SCALE = 0.8f;
    /**
     * Glyph advances do not scale exactly with the text size, so a legend shrunk in proportion
     * can still overrun by a fraction of a pixel; it steps down by at least this much, a few
     * times, until the measured width fits.
     */
    private static final float SHRINK_STEP_PX = 0.25f;
    private static final int SHRINK_PASSES = 8;
    /**
     * How long a finger has to rest on the column, off the handle, before the slider takes the
     * gesture from the card's scroll. Before then a scroll is the likelier intent; a finger that
     * has not left the slop by then means the slider.
     */
    static final long HOLD_TO_CLAIM_MS = 150L;
    /** The handle's touch target along the track: M3's 48dp minimum, centred on the handle. */
    private static final float HANDLE_TOUCH_DP = 48f;
    private static final int DISABLED_ALPHA = 97;
    private static final int DISABLED_TRACK_ALPHA = 31;

    private float mValueFrom = 0f;
    private float mValueTo = 100f;
    private float mStepSize = 1f;
    private float mValue = 0f;

    @Nullable private LabelFormatter mLegend;
    private final List<OnChangeListener> mChangeListeners = new ArrayList<>();
    private final List<OnSliderTouchListener> mTouchListeners = new ArrayList<>();

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private int mActiveColor;
    private int mInactiveColor;
    private int mLegendColor;
    private int mOnSurface;

    private final float mTrackWidthPx;
    private final float mMinTrackWidthPx;
    private final float mInsideCornerPx;
    private final float mHandleWidthPx;
    private final float mHandleOverhangPx;
    private final float mGapPx;
    private final float mStopPx;
    private final float mLegendGapPx;
    private final float mEndInsetPx;
    private float mBaseTextSizePx;
    /** The least text size a legend is drawn at: {@link #MIN_TEXT_SCALE} of the unscaled base. */
    private float mMinTextSizePx;
    /** The legend's line height at its base size, before any fitting shrinks it. */
    private float mBaseBandPx;

    /** The "set here" dot after the legend: its diameter and the gap before it. */
    private final float mDotPx;
    private final float mDotGapPx;
    private boolean mMarked;
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** The legend as last drawn, fitted to the column's height. */
    @NonNull private CharSequence mShown = "";
    private float mFitRoomPx;
    private boolean mDragging;

    /** A touch off the handle, waiting for {@link #HOLD_TO_CLAIM_MS} or the slop to decide it. */
    private boolean mClaimPending;
    private float mDownX;
    private float mDownY;
    private long mDownTimeMs;
    private float mLastY;
    private final float mTouchSlopPx;
    private final float mHandleTouchHalfPx;
    private final Runnable mHoldClaim = () -> {
        if (mClaimPending)
            claimGesture(mLastY);
    };

    /** What a touch on the column has decided so far. */
    enum Claim {
        /** The slider takes the gesture: it tracks the finger and the scroll is held off. */
        CLAIM,
        /** The gesture is the card's scroll; the slider lets go of it untouched. */
        SCROLL,
        /** Not decided yet: the finger is inside the slop and the hold has not elapsed. */
        WAIT
    }

    /**
     * The claim rule for a vertical slider inside a vertically scrolling card, where the slider's
     * axis is the scroll's: a touch on the handle (or anywhere, outside a scrolling container)
     * drags at once; anywhere else on the column the slider takes the gesture only after the
     * finger has rested {@link #HOLD_TO_CLAIM_MS} inside the touch slop, and a finger that leaves
     * the slop before then is scrolling.
     *
     * @param onHandle   the touch went down on the handle's touch target
     * @param inScroller an ancestor scrolls ({@link ViewGroup#shouldDelayChildPressedState})
     * @param movedPx    how far the finger has travelled from where it went down
     * @param slopPx     the touch slop
     * @param heldMs     how long since it went down
     */
    @NonNull
    static Claim claim(boolean onHandle, boolean inScroller, float movedPx, float slopPx,
                       long heldMs) {
        if (onHandle || !inScroller)
            return Claim.CLAIM;
        if (movedPx > slopPx && heldMs < HOLD_TO_CLAIM_MS)
            return Claim.SCROLL;
        if (heldMs >= HOLD_TO_CLAIM_MS)
            return Claim.CLAIM;
        return Claim.WAIT;
    }

    /** Whether a touch at {@code y} lands on the handle's touch target. */
    static boolean onHandle(float y, float handleCentreY, float touchHalfPx) {
        return Math.abs(y - handleCentreY) <= touchHalfPx;
    }

    public LegendSlider(@NonNull Context context) {
        this(context, null);
    }

    public LegendSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LegendSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        mTrackWidthPx = context.getResources().getDimension(R.dimen.appearance_editor_slider_track);
        mMinTrackWidthPx = 16f * density;
        mInsideCornerPx = context.getResources()
            .getDimension(R.dimen.appearance_editor_slider_inside_corner);
        mHandleWidthPx = context.getResources()
            .getDimension(R.dimen.appearance_editor_slider_thumb_width);
        mHandleOverhangPx = 2f * density;
        mGapPx = context.getResources().getDimension(R.dimen.appearance_editor_slider_gap);
        mStopPx = context.getResources().getDimension(R.dimen.appearance_editor_slider_stop);
        mLegendGapPx = 4f * density;
        mEndInsetPx = 4f * density;
        mDotPx = 6f * density;
        mDotGapPx = 6f * density;
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mHandleTouchHalfPx = HANDLE_TOUCH_DP * density / 2f;
        setClickable(true);
        setFocusable(true);
        resolvePaint();
        resolveColors();
    }

    // ------------------------------------------------------------------------------- the value

    public void setValueFrom(float valueFrom) {
        mValueFrom = valueFrom;
        setValue(mValue);
    }

    public float getValueFrom() {
        return mValueFrom;
    }

    public void setValueTo(float valueTo) {
        mValueTo = valueTo;
        setValue(mValue);
    }

    public float getValueTo() {
        return mValueTo;
    }

    public void setStepSize(float stepSize) {
        mStepSize = Math.max(0f, stepSize);
    }

    public float getStepSize() {
        return mStepSize;
    }

    public float getValue() {
        return mValue;
    }

    /** Sets the value from code: listeners hear it with {@code fromUser == false}. */
    public void setValue(float value) {
        setValueInternal(value, false);
    }

    private void setValueInternal(float value, boolean fromUser) {
        float lo = Math.min(mValueFrom, mValueTo);
        float hi = Math.max(mValueFrom, mValueTo);
        float clamped = Math.max(lo, Math.min(hi, value));
        if (mStepSize > 0f)
            clamped = lo + Math.round((clamped - lo) / mStepSize) * mStepSize;
        clamped = Math.max(lo, Math.min(hi, clamped));
        if (clamped == mValue && !fromUser)
            return;
        boolean changed = clamped != mValue;
        mValue = clamped;
        if (changed) {
            for (OnChangeListener listener : new ArrayList<>(mChangeListeners))
                listener.onValueChange(this, mValue, fromUser);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
        invalidate();
    }

    public void addOnChangeListener(@NonNull OnChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void addOnSliderTouchListener(@NonNull OnSliderTouchListener listener) {
        mTouchListeners.add(listener);
    }

    /** Always vertical: the question the layout test asks a column. */
    public boolean isVertical() {
        return true;
    }

    // ------------------------------------------------------------------------------ the legend

    /**
     * The legend at a value. Passing null clears it; the legend's words are also what
     * accessibility reads as the value.
     */
    public void setLegend(@Nullable LabelFormatter legend) {
        mLegend = legend;
        invalidate();
    }

    /**
     * Whether the value is the surface's own: the "set here" dot after the legend, and the words
     * accessibility reads after the value. Compares before it redraws.
     */
    public void setMarked(boolean marked) {
        if (mMarked == marked)
            return;
        mMarked = marked;
        invalidate();
    }

    public boolean isMarked() {
        return mMarked;
    }

    /** The legend as it reads now, before it is fitted to the column. */
    @NonNull
    public CharSequence legendText() {
        return mLegend == null ? "" : mLegend.getFormattedValue(mValue);
    }

    /** The legend's text size as last drawn, for a test to read. */
    float legendTextSizePx() {
        return mTextPaint.getTextSize();
    }

    /** Whether the legend as last drawn fits the room it was fitted to, for a test to read. */
    boolean legendFits() {
        return mTextPaint.measureText(mShown, 0, mShown.length()) <= mFitRoomPx + 0.5f;
    }

    /**
     * Whether the track, the legend's band beside it and the column's edges agree as last laid
     * out: the pair stands inside the column's width, for a test to read.
     */
    boolean legendInsideColumn() {
        float trackWidth = trackWidthPx();
        float right = trackLeftPx(trackWidth) + trackWidth + mLegendGapPx + legendBandPx();
        return trackLeftPx(trackWidth) - mHandleOverhangPx >= -0.5f && right <= getWidth() + 0.5f;
    }

    /** The legend as drawn at the last pass: fitted to the column. */
    @NonNull
    CharSequence shownLegend() {
        return mShown;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resolvePaint();
        resolveColors();
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        invalidate();
    }

    private void resolvePaint() {
        TextView probe = new TextView(getContext());
        TypedValue value = new TypedValue();
        int appearance = com.google.android.material.R.style.TextAppearance_Material3_LabelMedium;
        if (getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.textAppearanceLabelMedium, value, true)
            && value.resourceId != 0)
            appearance = value.resourceId;
        probe.setTextAppearance(appearance);
        mTextPaint.set(probe.getPaint());
        mBaseTextSizePx = mTextPaint.getTextSize();
        Paint.FontMetrics base = mTextPaint.getFontMetrics();
        mBaseBandPx = base.descent - base.ascent;
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        mMinTextSizePx = mBaseTextSizePx * MIN_TEXT_SCALE / fontScale;
    }

    private void resolveColors() {
        mActiveColor = MaterialColors.getColor(this,
            androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4);
        mInactiveColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSecondaryContainer, 0xFFE8DEF8);
        mLegendColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF49454F);
        mOnSurface = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface, 0xFF1D1B20);
        mDotPaint.setColor(mActiveColor);
    }

    // ----------------------------------------------------------------------------- the geometry

    /** The legend's line height: the room the text takes beside the track. */
    private float legendBandPx() {
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    /**
     * The narrowest column that still holds the legend at its full size beside the narrowest
     * track: what a strip asks before it decides how many columns stand on a line. It follows
     * the font scale, so a larger text size puts fewer columns on a line instead of shrinking
     * every legend.
     */
    public int minColumnWidthPx() {
        return (int) Math.ceil(2f * mHandleOverhangPx + mMinTrackWidthPx + mLegendGapPx
            + mBaseBandPx);
    }

    /** The track's width in this column: 28dp where the legend still fits beside it. */
    private float trackWidthPx() {
        float band = legendBandPx() + mLegendGapPx + 2f * mHandleOverhangPx;
        float room = getWidth() - band;
        return Math.max(mMinTrackWidthPx, Math.min(mTrackWidthPx, room));
    }

    /** The track's left edge: the track and legend pair centred in the column. */
    private float trackLeftPx(float trackWidth) {
        float group = mHandleOverhangPx + trackWidth + mLegendGapPx + legendBandPx();
        return Math.max(mHandleOverhangPx, (getWidth() - group) / 2f + mHandleOverhangPx);
    }

    /** The track's centre across this view, as it is drawn: what a leader line points at. */
    float trackCentreX() {
        float trackWidth = trackWidthPx();
        return trackLeftPx(trackWidth) + trackWidth / 2f;
    }

    private float fraction() {
        float span = mValueTo - mValueFrom;
        if (span == 0f)
            return 0f;
        return Math.max(0f, Math.min(1f, (mValue - mValueFrom) / span));
    }

    /** The handle's centre on the track axis: the bottom edge at the minimum, the top at the maximum. */
    private float handleCentreY() {
        float height = getHeight();
        float half = mHandleWidthPx / 2f;
        return height - half - fraction() * (height - mHandleWidthPx);
    }

    // --------------------------------------------------------------------------------- drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f)
            return;
        boolean enabled = isEnabled();
        // The legend is sized first: the track takes the width its band leaves.
        fitLegend(width, height);
        float trackWidth = trackWidthPx();
        float left = trackLeftPx(trackWidth);
        float right = left + trackWidth;
        float outer = trackWidth / 2f;
        float handleY = handleCentreY();
        float half = mHandleWidthPx / 2f;

        int active = enabled ? mActiveColor
            : MaterialColors.compositeARGBWithAlpha(mOnSurface, DISABLED_TRACK_ALPHA + 66);
        int inactive = enabled ? mInactiveColor
            : MaterialColors.compositeARGBWithAlpha(mOnSurface, DISABLED_TRACK_ALPHA);

        // Inactive track: from the top edge down to the gap above the handle.
        float inactiveBottom = handleY - half - mGapPx;
        if (inactiveBottom > 0f) {
            mRect.set(left, 0f, right, inactiveBottom);
            mTrackPaint.setColor(inactive);
            drawTrackPiece(canvas, mRect, outer, mInsideCornerPx, true);
            // The stop indicator at the far end of the inactive track.
            mTrackPaint.setColor(active);
            float stopY = Math.min(outer, inactiveBottom - mStopPx);
            if (stopY > mStopPx)
                canvas.drawCircle((left + right) / 2f, stopY, mStopPx / 2f, mTrackPaint);
        }
        // Active track: from the gap under the handle down to the bottom edge.
        float activeTop = handleY + half + mGapPx;
        if (activeTop < height) {
            mRect.set(left, activeTop, right, height);
            mTrackPaint.setColor(active);
            drawTrackPiece(canvas, mRect, outer, mInsideCornerPx, false);
        }
        // The handle: a 4dp bar standing 2dp past the track on either side.
        mRect.set(left - mHandleOverhangPx, handleY - half, right + mHandleOverhangPx,
            handleY + half);
        mTrackPaint.setColor(active);
        canvas.drawRoundRect(mRect, half, half, mTrackPaint);

        drawLegend(canvas, right + mLegendGapPx, height, enabled);
    }

    /**
     * One track piece with the outer end fully rounded and the end facing the handle at the
     * inside corner (M3's "inset" shape): {@code outerAtTop} says which end is which.
     */
    private void drawTrackPiece(@NonNull Canvas canvas, @NonNull RectF rect, float outer,
                                float inside, boolean outerAtTop) {
        float h = rect.height();
        if (h <= 0f)
            return;
        float top = Math.min(outerAtTop ? outer : inside, h / 2f);
        float bottom = Math.min(outerAtTop ? inside : outer, h / 2f);
        float w = rect.width();
        top = Math.min(top, w / 2f);
        bottom = Math.min(bottom, w / 2f);
        android.graphics.Path path = new android.graphics.Path();
        float[] radii = {top, top, top, top, bottom, bottom, bottom, bottom};
        path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW);
        canvas.drawPath(path, mTrackPaint);
    }

    /**
     * Sizes the legend for a column of {@code width} by {@code height}: along the track it shrinks
     * to fit the height (to {@link #MIN_TEXT_SCALE} at least, then gives up its name's tail);
     * across, its band never takes more than the narrowest track leaves (a strip of seven at a
     * large font scale), so the pair stays inside the column even under that floor.
     */
    private void fitLegend(float width, float height) {
        if (mLegend == null) {
            mShown = "";
            return;
        }
        CharSequence legend = mLegend.getFormattedValue(mValue);
        // The dot takes its room from the legend's, so a marked legend still fits whole.
        float room = height - 2f * mEndInsetPx - dotRoomPx();
        mFitRoomPx = room;
        mTextPaint.setTextSize(mBaseTextSizePx);
        float maxBand = width - mMinTrackWidthPx - mLegendGapPx - 2f * mHandleOverhangPx;
        float band = legendBandPx();
        float ceiling = band > maxBand && maxBand > 0f
            ? mBaseTextSizePx * maxBand / band : mBaseTextSizePx;
        float floor = Math.min(mMinTextSizePx, ceiling);
        mTextPaint.setTextSize(ceiling);
        float natural = mTextPaint.measureText(legend, 0, legend.length());
        if (natural > room) {
            float size = Math.max(floor, ceiling * room / natural);
            mTextPaint.setTextSize(size);
            for (int pass = 0; pass < SHRINK_PASSES && size > floor; pass++) {
                float measured = mTextPaint.measureText(legend, 0, legend.length());
                if (measured <= room)
                    break;
                size = Math.max(floor, Math.min(size * room / measured, size - SHRINK_STEP_PX));
                mTextPaint.setTextSize(size);
            }
        }
        mShown = fit(legend, room);
    }

    private float dotRoomPx() {
        return mMarked ? mDotGapPx + mDotPx : 0f;
    }

    private void drawLegend(@NonNull Canvas canvas, float x, float height, boolean enabled) {
        if (mLegend == null)
            return;
        float dotRoom = dotRoomPx();
        float textWidth = mTextPaint.measureText(mShown, 0, mShown.length());
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        // Rotated to read upward: the glyphs' tops face the track, so the baseline stands at
        // the band's far side, descent-first from the track.
        float baselineX = x - metrics.ascent;
        float cy = height / 2f;
        mTextPaint.setColor(mLegendColor);
        mTextPaint.setAlpha(enabled ? 255 : DISABLED_ALPHA);
        float run = textWidth + dotRoom;
        canvas.save();
        canvas.rotate(-90f, baselineX, cy);
        canvas.drawText(mShown, 0, mShown.length(), baselineX - run / 2f, cy, mTextPaint);
        if (mMarked) {
            // After the legend's last glyph, at the middle of its line.
            mDotPaint.setAlpha(enabled ? 255 : DISABLED_ALPHA);
            canvas.drawCircle(baselineX - run / 2f + textWidth + mDotGapPx + mDotPx / 2f,
                cy + (metrics.ascent + metrics.descent) / 2f, mDotPx / 2f, mDotPaint);
        }
        canvas.restore();
    }

    /**
     * The legend inside {@code available} px: whole when it fits; otherwise the name's tail is
     * ellipsised and the value after the separator kept; failing that, the whole is.
     */
    @NonNull
    private CharSequence fit(@NonNull CharSequence legend, float available) {
        if (mTextPaint.measureText(legend, 0, legend.length()) <= available)
            return legend;
        String text = legend.toString();
        int cut = text.indexOf(" · ");
        if (cut > 0) {
            String tail = text.substring(cut);
            float room = available - mTextPaint.measureText(tail);
            if (room > mTextPaint.measureText("…")) {
                CharSequence name = TextUtils.ellipsize(text.substring(0, cut), mTextPaint, room,
                    TextUtils.TruncateAt.END);
                return name.toString() + tail;
            }
        }
        return TextUtils.ellipsize(legend, mTextPaint, available, TextUtils.TruncateAt.END);
    }

    // ----------------------------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled())
            return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                abandonPendingClaim();
                mDownX = event.getX();
                mDownY = event.getY();
                mLastY = mDownY;
                mDownTimeMs = event.getEventTime();
                Claim decision = claim(onHandle(mDownY, handleCentreY(), mHandleTouchHalfPx),
                    isInScrollingContainer(), 0f, mTouchSlopPx, 0L);
                if (decision == Claim.CLAIM) {
                    claimGesture(mDownY);
                } else {
                    // The scroll keeps its say until the hold or the slop decides: no disallow
                    // yet, and nothing moves.
                    mClaimPending = true;
                    postDelayed(mHoldClaim, HOLD_TO_CLAIM_MS);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    setValueInternal(valueAt(event.getY()), true);
                    return true;
                }
                if (!mClaimPending)
                    return false;
                mLastY = event.getY();
                Claim decision = claim(false, true,
                    (float) Math.hypot(event.getX() - mDownX, event.getY() - mDownY),
                    mTouchSlopPx, event.getEventTime() - mDownTimeMs);
                if (decision == Claim.SCROLL) {
                    abandonPendingClaim();
                    return false;
                }
                if (decision == Claim.CLAIM)
                    claimGesture(mLastY);
                return true;
            case MotionEvent.ACTION_UP:
                // A tap off the handle, inside the slop and the hold: the value jumps there, as a
                // horizontal slider's tap does.
                if (mClaimPending)
                    claimGesture(event.getY());
                if (!mDragging)
                    return false;
                setValueInternal(valueAt(event.getY()), true);
                endGesture();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                abandonPendingClaim();
                if (!mDragging)
                    return false;
                endGesture();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /** The slider takes the gesture: the scroll is held off from here, and the value follows. */
    private void claimGesture(float y) {
        abandonPendingClaim();
        mDragging = true;
        ViewParent parent = getParent();
        if (parent != null)
            parent.requestDisallowInterceptTouchEvent(true);
        for (OnSliderTouchListener listener : new ArrayList<>(mTouchListeners))
            listener.onStartTrackingTouch(this);
        setValueInternal(valueAt(y), true);
    }

    private void endGesture() {
        mDragging = false;
        for (OnSliderTouchListener listener : new ArrayList<>(mTouchListeners))
            listener.onStopTrackingTouch(this);
    }

    private void abandonPendingClaim() {
        mClaimPending = false;
        removeCallbacks(mHoldClaim);
    }

    /** Material's own test: an ancestor that delays its children's pressed state scrolls. */
    private boolean isInScrollingContainer() {
        ViewParent parent = getParent();
        while (parent instanceof ViewGroup) {
            if (((ViewGroup) parent).shouldDelayChildPressedState())
                return true;
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        abandonPendingClaim();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** The value a touch at {@code y} asks for: the bottom edge is the minimum, the top the maximum. */
    private float valueAt(float y) {
        float height = getHeight();
        float travel = Math.max(1f, height - mHandleWidthPx);
        float fraction = 1f - (y - mHandleWidthPx / 2f) / travel;
        fraction = Math.max(0f, Math.min(1f, fraction));
        return mValueFrom + fraction * (mValueTo - mValueFrom);
    }

    // ------------------------------------------------------------------------------ accessibility

    @Override
    public CharSequence getAccessibilityClassName() {
        return SeekBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, mValueFrom, mValueTo, mValue));
        if (mLegend != null) {
            CharSequence value = mLegend.getFormattedValue(mValue);
            CharSequence state = mMarked ? value + ", "
                + getContext().getString(R.string.appearance_row_own_value) : value;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                info.setStateDescription(state);
            else
                info.setContentDescription(getContentDescription() == null ? state
                    : getContentDescription() + ", " + state);
        }
        if (isEnabled()) {
            if (mValue < Math.max(mValueFrom, mValueTo))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            if (mValue > Math.min(mValueFrom, mValueTo))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (!isEnabled())
            return super.performAccessibilityAction(action, arguments);
        float step = mStepSize > 0f ? mStepSize : (mValueTo - mValueFrom) / 20f;
        // One accessibility step is a twentieth of the range, or the step itself when coarser.
        step = Math.max(step, Math.abs(mValueTo - mValueFrom) / 20f);
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            setValueInternal(mValue + step, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            setValueInternal(mValue - step, true);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
