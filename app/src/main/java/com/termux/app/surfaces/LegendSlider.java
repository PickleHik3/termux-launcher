package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

/**
 * A Material 3 vertical slider that wears its name inside the track (M3 sliders spec: the "M"
 * size, a 40dp full-corner track, a 4 x 44dp handle, a stop indicator). The legend, such as
 * "Blur · 12 dp", reads upward along the track and is drawn twice, each pass clipped to one part
 * of the track: in {@code colorOnPrimary} over the filled part and in
 * {@code colorOnSecondaryContainer} over the rest, so the text inverts as the fill passes it. The
 * track colours are the stock ones (active {@code colorPrimary}, inactive
 * {@code colorSecondaryContainer}).
 *
 * <p>The geometry (vertical, 40dp track, full outer corners, 2dp inside corners, the 4 x 44dp
 * handle, the 6dp gap, no value label) is the style's, {@code Widget.Termux.LegendSlider}; the
 * constructor only turns the value label off for a slider made in code. The legend callback is
 * also the slider's label formatter, so the value is what accessibility announces. A legend that
 * does not fit the track gives up its name's tail first, then its end.</p>
 */
public final class LegendSlider extends Slider {

    /** The text of the legend at a value, from the legend's owner. */
    @Nullable private LabelFormatter mLegend;
    private final TextPaint mPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private int mActiveInk;
    private int mInactiveInk;
    /** Room left clear at each end of the track, so the legend never touches the rounded ends. */
    private final float mEndInsetPx;
    /** The legend, ellipsised for the track length it was last measured against. */
    @NonNull private CharSequence mShown = "";
    private float mFitRoomPx;

    public LegendSlider(@NonNull Context context) {
        this(context, null);
    }

    public LegendSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.sliderStyle);
    }

    public LegendSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLabelBehavior(LabelFormatter.LABEL_GONE);
        mEndInsetPx = 4f * context.getResources().getDisplayMetrics().density;
        resolvePaint();
        resolveInk();
    }

    /**
     * The legend at a value. The same callback is the label formatter, which accessibility reads
     * the value from; passing null clears the legend.
     */
    public void setLegend(@Nullable LabelFormatter legend) {
        mLegend = legend;
        if (legend != null)
            setLabelFormatter(legend);
        invalidate();
    }

    /** The legend as it reads now, before it is fitted to the track. */
    @NonNull
    public CharSequence legendText() {
        return mLegend == null ? "" : mLegend.getFormattedValue(getValue());
    }

    /** The legend's text size, from {@code textAppearanceLabelMedium}, for a test to read. */
    float legendTextSizePx() {
        return mPaint.getTextSize();
    }

    /** Whether the legend as last drawn fits the room it was fitted to, for a test to read. */
    boolean legendFits() {
        return mPaint.measureText(mShown, 0, mShown.length()) <= mFitRoomPx + 0.5f;
    }

    /** The legend as drawn at the last pass: fitted to the track. */
    @NonNull
    CharSequence shownLegend() {
        return mShown;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resolvePaint();
        resolveInk();
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
        mPaint.set(probe.getPaint());
    }

    private void resolveInk() {
        mActiveInk = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
        mInactiveInk = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFF000000);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLegend == null || getWidth() <= 0 || getHeight() <= 0)
            return;
        float width = getWidth();
        float height = getHeight();
        float pad = getTrackSidePadding();
        float top = pad;
        float bottom = height - pad;
        float length = bottom - top;
        if (length <= 2f * mEndInsetPx)
            return;
        float span = getValueTo() - getValueFrom();
        float fraction = span <= 0f ? 0f
            : Math.max(0f, Math.min(1f, (getValue() - getValueFrom()) / span));
        // A vertical slider fills from the bottom: the thumb stands higher as the value grows.
        float thumb = bottom - fraction * length;
        float half = Math.min(getThumbWidth(), getThumbHeight()) / 2f + getThumbTrackGapSize();
        float cx = width / 2f;
        float cy = (top + bottom) / 2f;

        mFitRoomPx = length - 2f * mEndInsetPx;
        mShown = fit(mLegend.getFormattedValue(getValue()), mFitRoomPx);
        float textWidth = mPaint.measureText(mShown, 0, mShown.length());
        Paint.FontMetrics metrics = mPaint.getFontMetrics();
        float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
        int alpha = isEnabled() ? 255 : 97;

        // Over the filled part: from just under the handle's gap down to the track's end.
        drawPass(canvas, cx, cy, baseline, textWidth, 0f, Math.min(height, thumb + half), height,
            mActiveInk, alpha);
        // Over the rest: from the track's end down to just above the handle's gap.
        drawPass(canvas, cx, cy, baseline, textWidth, 0f, 0f, Math.max(0f, thumb - half),
            mInactiveInk, alpha);
    }

    private void drawPass(@NonNull Canvas canvas, float cx, float cy, float baseline,
                          float textWidth, float left, float clipTop, float clipBottom, int ink,
                          int alpha) {
        if (clipBottom <= clipTop)
            return;
        canvas.save();
        canvas.clipRect(left, clipTop, getWidth(), clipBottom);
        // Reads upward: the text is laid along the track, its start at the bottom.
        canvas.rotate(-90f, cx, cy);
        mPaint.setColor(ink);
        mPaint.setAlpha(alpha);
        canvas.drawText(mShown, 0, mShown.length(), cx - textWidth / 2f, baseline, mPaint);
        canvas.restore();
    }

    /**
     * The legend inside {@code available} px: whole when it fits; otherwise the name's tail is
     * ellipsised and the value after the separator kept; failing that, the whole is.
     */
    @NonNull
    private CharSequence fit(@NonNull CharSequence legend, float available) {
        if (mPaint.measureText(legend, 0, legend.length()) <= available)
            return legend;
        String text = legend.toString();
        int cut = text.indexOf(" · ");
        if (cut > 0) {
            String tail = text.substring(cut);
            float room = available - mPaint.measureText(tail);
            if (room > mPaint.measureText("…")) {
                CharSequence name = TextUtils.ellipsize(text.substring(0, cut), mPaint, room,
                    TextUtils.TruncateAt.END);
                return name.toString() + tail;
            }
        }
        return TextUtils.ellipsize(legend, mPaint, available, TextUtils.TruncateAt.END);
    }
}
