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
 * A vertical Material 3 slider for the Look editor's Custom row, drawn by hand so the track is
 * exactly the column's height: 0 at the bottom edge, the maximum at the top edge, nothing
 * clipped, and the legend ("Blur · 12 dp") standing beside the track as vertical text that reads
 * upward. It follows the M3 slider spec's "M" size: a 28dp track with full outer corners and 2dp
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

    private static final float MIN_TEXT_SCALE = 0.8f;
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

    /** The legend as last drawn, fitted to the column's height. */
    @NonNull private CharSequence mShown = "";
    private float mFitRoomPx;
    private boolean mDragging;

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
        mMinTrackWidthPx = 20f * density;
        mInsideCornerPx = context.getResources()
            .getDimension(R.dimen.appearance_editor_slider_inside_corner);
        mHandleWidthPx = context.getResources()
            .getDimension(R.dimen.appearance_editor_slider_thumb_width);
        mHandleOverhangPx = 2f * density;
        mGapPx = context.getResources().getDimension(R.dimen.appearance_editor_slider_gap);
        mStopPx = context.getResources().getDimension(R.dimen.appearance_editor_slider_stop);
        mLegendGapPx = 4f * density;
        mEndInsetPx = 4f * density;
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
    }

    // ----------------------------------------------------------------------------- the geometry

    /** The legend's line height: the room the text takes beside the track. */
    private float legendBandPx() {
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        return metrics.descent - metrics.ascent;
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

    private void drawLegend(@NonNull Canvas canvas, float x, float height, boolean enabled) {
        if (mLegend == null) {
            mShown = "";
            return;
        }
        CharSequence legend = mLegend.getFormattedValue(mValue);
        float room = height - 2f * mEndInsetPx;
        mFitRoomPx = room;
        mTextPaint.setTextSize(mBaseTextSizePx);
        float natural = mTextPaint.measureText(legend, 0, legend.length());
        if (natural > room)
            mTextPaint.setTextSize(Math.max(mBaseTextSizePx * MIN_TEXT_SCALE,
                mBaseTextSizePx * room / natural));
        mShown = fit(legend, room);
        float textWidth = mTextPaint.measureText(mShown, 0, mShown.length());
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        // Rotated to read upward: the glyphs' tops face the track, so the baseline stands at
        // the band's far side, descent-first from the track.
        float baselineX = x - metrics.ascent;
        float cy = height / 2f;
        mTextPaint.setColor(mLegendColor);
        mTextPaint.setAlpha(enabled ? 255 : DISABLED_ALPHA);
        canvas.save();
        canvas.rotate(-90f, baselineX, cy);
        canvas.drawText(mShown, 0, mShown.length(), baselineX - textWidth / 2f, cy, mTextPaint);
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
            case MotionEvent.ACTION_DOWN:
                mDragging = true;
                ViewParent parent = getParent();
                if (parent != null)
                    parent.requestDisallowInterceptTouchEvent(true);
                for (OnSliderTouchListener listener : new ArrayList<>(mTouchListeners))
                    listener.onStartTrackingTouch(this);
                setValueInternal(valueAt(event.getY()), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging)
                    return false;
                setValueInternal(valueAt(event.getY()), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging)
                    return false;
                if (event.getActionMasked() == MotionEvent.ACTION_UP)
                    setValueInternal(valueAt(event.getY()), true);
                mDragging = false;
                for (OnSliderTouchListener listener : new ArrayList<>(mTouchListeners))
                    listener.onStopTrackingTouch(this);
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
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
        if (mLegend != null)
            info.setStateDescription(mLegend.getFormattedValue(mValue));
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
