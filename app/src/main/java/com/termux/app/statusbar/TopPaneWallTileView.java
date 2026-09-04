package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * One of the top slot's navigation tiles: a rounded rectangle the size of the clock that says
 * where it goes — "Widgets" or "Display" — and shows whether the wall is already there.
 *
 * <p>The Display tile also carries a stop control while a display is running: a running dot, and
 * a × at its trailing edge with a touch target of its own, so a tap that navigates and a tap that
 * stops a desktop are never confused.
 */
public final class TopPaneWallTileView extends View {

    /** Smallest comfortable target for the stop control, per the platform's own guidance. */
    private static final float STOP_TARGET_DP = 40f;
    private static final float STOP_GLYPH_DP = 7f;
    private static final float RUNNING_DOT_DP = 4f;
    private static final float LABEL_SP = 12f;
    private static final float RIM_DP = 1f;
    /** Keeps the slab off the band's own edges, so it reads as a chip and not as a second bar. */
    private static final float VERTICAL_INSET_DP = 8f;
    /** The slab is glass over the status surface, like every other chip in the bar. */
    private static final int FILL_ALPHA = 64;
    private static final int SELECTED_FILL_ALPHA = 148;

    public interface Listener {
        /** The tile was tapped: show its place, or return to the terminal if it already shows. */
        void onTileSelected();
        /** The × was tapped. Only reachable while {@link #setRunning} is true. */
        default void onTileStopRequested() { }
    }

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mShape = new RectF();
    private final Rect mLabelBounds = new Rect();
    private final Rect mStopTarget = new Rect();

    @Nullable private Listener mListener;
    private String mText = "";
    private float mRadiusPx;
    private boolean mRunning;
    private boolean mStopArmed;

    public TopPaneWallTileView(@NonNull Context context) {
        this(context, null);
    }

    public TopPaneWallTileView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        mRim.setStyle(Paint.Style.STROKE);
        mGlyph.setStyle(Paint.Style.STROKE);
        mGlyph.setStrokeCap(Paint.Cap.ROUND);
        mLabel.setTextAlign(Paint.Align.LEFT);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** The tile's label, which is also what a screen reader announces. */
    public void setLabel(@NonNull String text) {
        if (mText.equals(text)) return;
        mText = text;
        setContentDescription(text);
        invalidate();
    }

    /** The status bar's resolved corner radius, so a tile is shaped like the pane it sits on. */
    public void setCornerRadiusPx(float radiusPx) {
        float resolved = Math.max(0f, radiusPx);
        if (mRadiusPx == resolved) return;
        mRadiusPx = resolved;
        invalidate();
    }

    /** A display is running behind this tile: show the dot and the stop control. */
    public void setRunning(boolean running) {
        if (mRunning == running) return;
        mRunning = running;
        invalidate();
    }

    public boolean isRunning() {
        return mRunning;
    }

    @Override
    public void setSelected(boolean selected) {
        if (isSelected() == selected) return;
        super.setSelected(selected);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mStopArmed = mRunning && !mStopTarget.isEmpty()
                    && mStopTarget.contains((int) event.getX(), (int) event.getY());
                return super.onTouchEvent(event);
            case MotionEvent.ACTION_UP: {
                boolean stop = mStopArmed && !mStopTarget.isEmpty()
                    && mStopTarget.contains((int) event.getX(), (int) event.getY());
                mStopArmed = false;
                if (stop) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    if (mListener != null) mListener.onTileStopRequested();
                    return true;
                }
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_CANCEL:
                mStopArmed = false;
                return super.onTouchEvent(event);
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        if (mListener != null) {
            mListener.onTileSelected();
            playSoundEffect(android.view.SoundEffectConstants.CLICK);
            return true;
        }
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
        info.setSelected(isSelected());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        boolean selected = isSelected();
        float density = getResources().getDisplayMetrics().density;
        float rimPx = RIM_DP * density;
        // The tile is glass over the status surface, not a card on top of it: a translucent fill
        // and a thin rim, the same treatment the window chips beside it wear. The rim brightens
        // and the fill fills in while this is the place on screen — the same "this one has the
        // focus" cue a pane's rim carries.
        int fill = themeColor(selected ? com.termux.shared.R.attr.termuxColorPrimaryContainer
            : com.termux.shared.R.attr.termuxColorSurfacePanelHighest,
            selected ? R.color.termux_primary : R.color.termux_surface_panel_highest);
        int text = themeColor(selected ? com.termux.shared.R.attr.termuxColorOnPrimaryContainer
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            selected ? R.color.termux_on_surface : R.color.termux_on_surface_variant);
        int rim = themeColor(selected ? com.termux.shared.R.attr.termuxColorPrimary
            : com.termux.shared.R.attr.termuxColorOutlineVariant,
            selected ? R.color.termux_primary : R.color.termux_outline_variant);
        float inset = Math.min(VERTICAL_INSET_DP * density, height * 0.12f);
        mShape.set(rimPx / 2f, inset + rimPx / 2f, width - rimPx / 2f, height - inset - rimPx / 2f);
        float radius = Math.min(mRadiusPx, Math.min(mShape.width(), mShape.height()) / 2f);
        mFill.setColor(fill);
        mFill.setAlpha(selected ? SELECTED_FILL_ALPHA : FILL_ALPHA);
        canvas.drawRoundRect(mShape, radius, radius, mFill);
        mRim.setColor(rim);
        mRim.setAlpha(selected ? 255 : 128);
        mRim.setStrokeWidth(rimPx);
        canvas.drawRoundRect(mShape, radius, radius, mRim);

        float padding = 12f * density;
        float stopTarget = STOP_TARGET_DP * density;
        boolean showStop = mRunning && width >= stopTarget + padding * 2f;
        if (showStop) {
            int left = Math.round(width - stopTarget);
            mStopTarget.set(left, Math.round(mShape.top), width, Math.round(mShape.bottom));
        } else {
            mStopTarget.setEmpty();
        }

        mLabel.setColor(text);
        mLabel.setAlpha(255);
        mLabel.setTextSize(LABEL_SP * getResources().getDisplayMetrics().scaledDensity);
        float dotSpace = mRunning ? RUNNING_DOT_DP * density * 3f : 0f;
        float labelLeft = padding + dotSpace;
        float labelRight = showStop ? mStopTarget.left : width - padding;
        float available = Math.max(0f, labelRight - labelLeft);
        String label = ellipsize(mText, available);
        mLabel.getTextBounds(label, 0, label.length(), mLabelBounds);
        float baseline = height / 2f - (mLabel.descent() + mLabel.ascent()) / 2f;
        // Centred in whatever the running state leaves it, so the word does not shift when a
        // display comes up beside it.
        float x = labelLeft + Math.max(0f, (available - mLabel.measureText(label)) / 2f);
        canvas.drawText(label, x, baseline, mLabel);

        if (mRunning) {
            mGlyph.setStyle(Paint.Style.FILL);
            mGlyph.setColor(themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                R.color.termux_primary));
            float dot = RUNNING_DOT_DP * density;
            canvas.drawCircle(padding + dot, height / 2f, dot, mGlyph);
        }
        if (showStop) {
            mGlyph.setStyle(Paint.Style.STROKE);
            mGlyph.setStrokeWidth(1.5f * density);
            mGlyph.setColor(text);
            float cx = mStopTarget.centerX();
            float cy = mStopTarget.centerY();
            float arm = STOP_GLYPH_DP * density / 2f;
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, mGlyph);
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, mGlyph);
        }
    }

    /**
     * One line, always. A tile is a third of the slot and the label is a single word at any font
     * scale, so it is trimmed to fit rather than allowed to overrun its neighbour.
     */
    private String ellipsize(@NonNull String text, float availablePx) {
        if (text.isEmpty() || availablePx <= 0f) return "";
        if (mLabel.measureText(text) <= availablePx) return text;
        String ellipsis = "…";
        float ellipsisPx = mLabel.measureText(ellipsis);
        int end = text.length();
        while (end > 0 && mLabel.measureText(text, 0, end) + ellipsisPx > availablePx) end--;
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    private int themeColor(int attr, int fallbackRes) {
        return MaterialColors.getColor(this, attr,
            ContextCompat.getColor(getContext(), fallbackRes));
    }
}
