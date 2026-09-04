package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wall's place switch: one pill in the expanded status bar with a segment per place —
 * Widgets, Terminal, Display — and a thumb under the one on screen. The thumb is not animated on
 * its own: {@link #setThumbPosition} is fed from the wall's offset, so it moves with a drag and
 * with the spring, and always lands where the wall came to rest. Tapping a segment goes there.
 *
 * <p>Glass, like every other chip in the bar: a translucent fill with a thin rim, the thumb a
 * filled slab in the primary container. The Display segment carries a dot while a display runs and
 * reads quieter while none does; a long press on it while running asks to stop the display.
 */
public final class WallPlaceSwitchView extends View {

    private static final float LABEL_SP = 12f;
    private static final float SEGMENT_PADDING_DP = 12f;
    private static final float RIM_DP = 1f;
    private static final float DOT_DP = 3f;
    private static final int FILL_ALPHA = 64;
    private static final int THUMB_ALPHA = 148;

    public interface Listener {
        /** A segment was tapped. */
        void onPlaceSelected(@NonNull PaneWallPage page);
        /** The Display segment was long-pressed while a display runs. */
        default void onDisplayStopRequested() { }
    }

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mShape = new RectF();
    private final RectF mThumbShape = new RectF();

    @NonNull private List<PaneWallPage> mPages = Collections.emptyList();
    @NonNull private List<String> mLabels = Collections.emptyList();
    /** Each segment's left edge, plus a final entry for the right edge; in px, from measure. */
    @NonNull private float[] mEdges = new float[0];
    @Nullable private Listener mListener;
    private float mThumbPosition;
    private float mRadiusPx;
    private boolean mDisplayRunning;
    private int mPressedSegment = -1;
    private boolean mLongPressed;
    private final Runnable mLongPress = () -> {
        if (mPressedSegment < 0 || mPressedSegment >= mPages.size()) return;
        if (mPages.get(mPressedSegment) != PaneWallPage.DISPLAY || !mDisplayRunning) return;
        mLongPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (mListener != null) mListener.onDisplayStopRequested();
    };

    public WallPlaceSwitchView(@NonNull Context context) {
        this(context, null);
    }

    public WallPlaceSwitchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        mRim.setStyle(Paint.Style.STROKE);
        mLabel.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** The places, in spatial order, and what to call them. */
    public void setPlaces(@NonNull List<PaneWallPage> pages, @NonNull List<String> labels) {
        if (pages.equals(mPages) && labels.equals(mLabels)) return;
        mPages = new ArrayList<>(pages);
        mLabels = new ArrayList<>(labels);
        setContentDescription(String.join(", ", labels));
        requestLayout();
        invalidate();
    }

    @NonNull
    public List<PaneWallPage> places() {
        return Collections.unmodifiableList(mPages);
    }

    /** See {@link com.termux.app.wall.WallPlaceSwitchPolicy#thumbPosition}. */
    public void setThumbPosition(float position) {
        float clamped = Math.max(0f, Math.min(Math.max(0, mPages.size() - 1), position));
        if (mThumbPosition == clamped) return;
        mThumbPosition = clamped;
        invalidate();
    }

    public float thumbPosition() {
        return mThumbPosition;
    }

    /** The status bar's resolved corner radius, capped to a full pill. */
    public void setCornerRadiusPx(float radiusPx) {
        float resolved = Math.max(0f, radiusPx);
        if (mRadiusPx == resolved) return;
        mRadiusPx = resolved;
        invalidate();
    }

    /** A display runs: the Display segment carries a dot and can be long-pressed to stop it. */
    public void setDisplayRunning(boolean running) {
        if (mDisplayRunning == running) return;
        mDisplayRunning = running;
        requestLayout();
        invalidate();
    }

    // ---- Measure ------------------------------------------------------------------------------

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private void prepareLabelPaint() {
        mLabel.setTextSize(LABEL_SP * getResources().getDisplayMetrics().scaledDensity);
    }

    /** The label's width plus the dot's, for the Display segment while a display runs. */
    private float contentWidth(int index) {
        float width = mLabel.measureText(mLabels.get(index));
        if (mPages.get(index) == PaneWallPage.DISPLAY && mDisplayRunning) {
            width += DOT_DP * density() * 4f;
        }
        return width;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        prepareLabelPaint();
        float padding = SEGMENT_PADDING_DP * density();
        int count = mPages.size();
        mEdges = new float[count + 1];
        float x = 0f;
        for (int i = 0; i < count; i++) {
            mEdges[i] = x;
            x += contentWidth(i) + padding * 2f;
        }
        mEdges[count] = x;
        int desiredWidth = (int) Math.ceil(x);
        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
            ? Math.round(36f * density()) : MeasureSpec.getSize(heightMeasureSpec);
        // Given less than it asked for, the segments share the shortfall equally.
        if (width < desiredWidth && count > 0) {
            float scale = width / (float) desiredWidth;
            for (int i = 0; i <= count; i++) mEdges[i] *= scale;
        }
        setMeasuredDimension(width, height);
    }

    // ---- Touch --------------------------------------------------------------------------------

    private int segmentAt(float x) {
        for (int i = 0; i < mPages.size(); i++) {
            if (x >= mEdges[i] && x < mEdges[i + 1]) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressedSegment = segmentAt(event.getX());
                mLongPressed = false;
                removeCallbacks(mLongPress);
                postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
                return super.onTouchEvent(event);
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mLongPress);
                int released = segmentAt(event.getX());
                boolean tap = !mLongPressed && released >= 0 && released == mPressedSegment;
                mPressedSegment = -1;
                if (tap) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (mListener != null) mListener.onPlaceSelected(mPages.get(released));
                    return true;
                }
                if (mLongPressed) return true;
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPress);
                mPressedSegment = -1;
                return super.onTouchEvent(event);
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        // Keyboard and accessibility activation: the next place round.
        if (mListener != null && !mPages.isEmpty()) {
            int next = (Math.round(mThumbPosition) + 1) % mPages.size();
            mListener.onPlaceSelected(mPages.get(next));
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
    }

    // ---- Draw ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int count = mPages.size();
        if (width <= 0 || height <= 0 || count == 0 || mEdges.length != count + 1) return;
        float density = density();
        float rimPx = RIM_DP * density;
        prepareLabelPaint();

        int fill = themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHighest,
            R.color.termux_surface_panel_highest);
        int rim = themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant);
        int thumb = themeColor(com.termux.shared.R.attr.termuxColorPrimaryContainer, R.color.termux_primary);
        int onThumb = themeColor(com.termux.shared.R.attr.termuxColorOnPrimaryContainer, R.color.termux_on_surface);
        int text = themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        int accent = themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);

        mShape.set(rimPx / 2f, rimPx / 2f, width - rimPx / 2f, height - rimPx / 2f);
        float radius = Math.min(mRadiusPx, mShape.height() / 2f);
        mFill.setColor(fill);
        mFill.setAlpha(FILL_ALPHA);
        canvas.drawRoundRect(mShape, radius, radius, mFill);

        // The thumb: the segment the wall is on, or the span between two while it moves.
        int from = (int) Math.floor(mThumbPosition);
        int to = Math.min(count - 1, from + 1);
        float t = mThumbPosition - from;
        float inset = rimPx * 2f;
        float left = lerp(mEdges[from], mEdges[to], t) + inset;
        float right = lerp(mEdges[from + 1], mEdges[to + 1], t) - inset;
        mThumbShape.set(left, inset, right, height - inset);
        float thumbRadius = Math.max(0f, radius - inset);
        mThumb.setStyle(Paint.Style.FILL);
        mThumb.setColor(thumb);
        mThumb.setAlpha(THUMB_ALPHA);
        canvas.drawRoundRect(mThumbShape, thumbRadius, thumbRadius, mThumb);

        mRim.setColor(rim);
        mRim.setAlpha(128);
        mRim.setStrokeWidth(rimPx);
        canvas.drawRoundRect(mShape, radius, radius, mRim);

        float baseline = height / 2f - (mLabel.descent() + mLabel.ascent()) / 2f;
        for (int i = 0; i < count; i++) {
            // How much of the thumb sits under this segment decides its label colour, so the
            // label the thumb is leaving fades while the one it reaches lights up.
            float under = Math.max(0f, 1f - Math.abs(mThumbPosition - i));
            boolean display = mPages.get(i) == PaneWallPage.DISPLAY;
            int colour = blend(text, onThumb, under);
            int alpha = display && !mDisplayRunning ? 150 + Math.round(105 * under) : 255;
            mLabel.setColor(colour);
            mLabel.setAlpha(alpha);
            float centre = (mEdges[i] + mEdges[i + 1]) / 2f;
            float dotSpace = display && mDisplayRunning ? DOT_DP * density * 4f : 0f;
            canvas.drawText(mLabels.get(i), centre + dotSpace / 2f, baseline, mLabel);
            if (dotSpace > 0f) {
                float labelWidth = mLabel.measureText(mLabels.get(i));
                float dot = DOT_DP * density;
                mThumb.setColor(under > 0.5f ? onThumb : accent);
                mThumb.setAlpha(255);
                canvas.drawCircle(centre + dotSpace / 2f - labelWidth / 2f - dot * 2.5f,
                    height / 2f, dot, mThumb);
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int blend(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int a = Math.round(lerp(android.graphics.Color.alpha(from), android.graphics.Color.alpha(to), clamped));
        int r = Math.round(lerp(android.graphics.Color.red(from), android.graphics.Color.red(to), clamped));
        int g = Math.round(lerp(android.graphics.Color.green(from), android.graphics.Color.green(to), clamped));
        int b = Math.round(lerp(android.graphics.Color.blue(from), android.graphics.Color.blue(to), clamped));
        return android.graphics.Color.argb(a, r, g, b);
    }

    private int themeColor(int attr, int fallbackRes) {
        return MaterialColors.getColor(this, attr, ContextCompat.getColor(getContext(), fallbackRes));
    }
}
