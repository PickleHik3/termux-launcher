package com.termux.app.wall.pageswitch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.material.M3;

import java.util.List;

/**
 * The page switch panel: the glass it sits on is its background, one row of page glyphs on it,
 * the A-Z bar's wave around the finger, and the highlighted page's full name above the row. The
 * controller feeds it state; it draws nothing at rest after the wave has settled and allocates
 * nothing while drawing.
 */
public final class PageSwitchPanelView extends View {

    private static final long WAVE_SETTLE_MS = 180L;
    private static final int WEAK_ALPHA = 0xB0;
    private static final int HIGHLIGHT_ALPHA = 0x2E;

    private final PageGlyphPainter mPainter;
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mHighlightRect = new RectF();
    private final float[] mWave = new float[2];
    private final float mDensity;
    private final ValueAnimator mSettle = ValueAnimator.ofFloat(1f, 0f);

    @Nullable private PageSwitchModel mModel;
    @Nullable private PageSwitchLayout mLayout;
    @ColorInt private int mInk = 0xFFFFFFFF;
    private int mHighlight = -2;
    private float mFingerX;
    private float mStrength;
    private boolean mReducedMotion;

    /** The highlighted cell's name, already cut to the panel's width. */
    @Nullable private CharSequence mTitle;
    private float mTitleWidth;

    public PageSwitchPanelView(@NonNull Context context) {
        super(context);
        mPainter = new PageGlyphPainter(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        // The title reads like the other labels: the theme's label size, scaled with the font.
        TextView probe = new TextView(context);
        M3.textAppearance(probe, com.google.android.material.R.attr.textAppearanceLabelLarge);
        mTitlePaint.setTextSize(probe.getTextSize());
        mTitlePaint.setTypeface(probe.getTypeface());
        mTitlePaint.setColor(mInk);
        mSettle.setDuration(WAVE_SETTLE_MS);
        mSettle.addUpdateListener(a -> {
            mStrength = (float) a.getAnimatedValue();
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The height of one title line, for the layout the controller builds. */
    public float titleLineHeightPx() {
        Paint.FontMetrics fm = mTitlePaint.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    public void setInk(@ColorInt int ink) {
        if (mInk == ink) return;
        mInk = ink;
        mTitlePaint.setColor(ink);
        invalidate();
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** A new page list and its geometry; called as the panel opens. */
    public void bind(@NonNull PageSwitchModel model, @NonNull PageSwitchLayout layout,
                     float fingerX) {
        mModel = model;
        mLayout = layout;
        mHighlight = -2;
        mStrength = 0f;
        setState(model.currentIndex(), fingerX, true);
    }

    /**
     * The gesture's answer: the highlighted cell (-1 for none), the finger's x in panel
     * coordinates, and whether the finger is down. A lift lets the wave settle.
     */
    public void setState(int highlight, float fingerX, boolean touching) {
        if (mModel == null || mLayout == null) return;
        if (highlight != mHighlight) {
            mHighlight = highlight;
            refreshTitle();
        }
        mFingerX = fingerX;
        if (touching) {
            mSettle.cancel();
            mStrength = 1f;
        } else if (mStrength > 0f && !mSettle.isRunning()) {
            if (mReducedMotion) {
                mStrength = 0f;
            } else {
                mSettle.start();
            }
        }
        invalidate();
    }

    /** Drop the state; nothing draws until the next bind. */
    public void clear() {
        mSettle.cancel();
        mStrength = 0f;
        mHighlight = -2;
        mModel = null;
        mLayout = null;
        mTitle = null;
        if (getContentDescription() != null) setContentDescription(null);
    }

    private void refreshTitle() {
        PageSwitchModel model = mModel;
        PageSwitchLayout layout = mLayout;
        mTitle = null;
        mTitleWidth = 0f;
        String description = null;
        if (model != null && layout != null) {
            List<PageSwitchModel.Cell> cells = model.cells();
            if (mHighlight >= 0 && mHighlight < cells.size()) {
                description = cells.get(mHighlight).fullName;
                mTitle = TextUtils.ellipsize(description, mTitlePaint, layout.maxTitleWidthPx(),
                    TextUtils.TruncateAt.END);
                mTitleWidth = mTitlePaint.measureText(mTitle, 0, mTitle.length());
            }
        }
        if (!TextUtils.equals(getContentDescription(), description)) {
            setContentDescription(description);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        PageSwitchModel model = mModel;
        PageSwitchLayout layout = mLayout;
        if (model == null || layout == null) return;
        List<PageSwitchModel.Cell> cells = model.cells();
        float glyph = layout.glyphPx();
        float restCentreY = layout.cellTopRestPx() + layout.cellHeightPx() / 2f;
        int weakInk = (mInk & 0x00FFFFFF) | (WEAK_ALPHA << 24);

        if (mTitle != null && mHighlight >= 0) {
            float centre = layout.titleCentreX(layout.cellCentreX(mHighlight), mTitleWidth);
            float baseline = PageSwitchLayout.PANEL_VERTICAL_PADDING_DP * mDensity
                - mTitlePaint.getFontMetrics().ascent;
            canvas.drawText(mTitle, 0, mTitle.length(), centre - mTitleWidth / 2f, baseline,
                mTitlePaint);
        }
        for (int i = 0; i < cells.size(); i++) {
            layout.wave(i, mFingerX, mStrength, mWave);
            float size = glyph * mWave[1];
            float cx = layout.cellCentreX(i);
            float cy = restCentreY - mWave[0];
            if (i == mHighlight) {
                float half = Math.max(size, layout.cellWidthPx()) / 2f;
                mHighlightPaint.setColor((mInk & 0x00FFFFFF) | (HIGHLIGHT_ALPHA << 24));
                mHighlightRect.set(cx - half, cy - size / 2f - 4f * mDensity,
                    cx + half, cy + size / 2f + 4f * mDensity);
                canvas.drawRoundRect(mHighlightRect, 10f * mDensity, 10f * mDensity,
                    mHighlightPaint);
            }
            PageSwitchModel.Cell cell = cells.get(i);
            mPainter.draw(canvas, cell.kind, cell.number, cx - size / 2f, cy - size / 2f, size,
                i == mHighlight ? mInk : weakInk);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mSettle.cancel();
        super.onDetachedFromWindow();
    }
}
