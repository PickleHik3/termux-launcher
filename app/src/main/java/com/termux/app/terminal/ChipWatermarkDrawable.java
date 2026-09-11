package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.app.statusbar.WindowActivityRing;

/**
 * One window chip's whole surface: its fill, the process glyph (or the window's own icon)
 * watermarked behind the title, the outline — which becomes the ring while a shell works — and the
 * two corner dots that carry the marks and the agent reading.
 *
 * <p>The chip's label is the title alone, so everything the label used to spend characters on lives
 * here instead, out of the text's way. Nothing is stored per frame: the arc and the breath read the
 * bar's one clock at draw time, so the bar only has to invalidate the chips that are moving.
 *
 * <p>All geometry comes from {@link ChipWatermarkGeometry}; this class is the paint.
 */
final class ChipWatermarkDrawable extends Drawable {

    /** Which mark, if any, the top-trailing dot is carrying. */
    enum Mark { NONE, BELL, DONE, FAILED }

    /** How the agent dot on the bottom-leading corner is drawn, or that there is none. */
    enum Agent { NONE, IDLE, WAITING, WORKING }

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mOutline = new Path();
    private final Path mArc = new Path();
    private final PathMeasure mMeasure = new PathMeasure();
    private final RectF mRect = new RectF();
    private final RectF mCorner = new RectF();
    private final Rect mIconSource = new Rect();
    private final RectF mIconTarget = new RectF();

    private final float mDensity;
    private float mCornerRadiusPx;
    private float mStrokePx = 1f;
    private int mFillColor;
    private int mStrokeColor;

    @Nullable private String mGlyph;
    @Nullable private Typeface mGlyphFace;
    @Nullable private Bitmap mIcon;
    private int mGlyphColor;
    /** Rebuilt only when the accent moves; a filter per frame would be a new object per chip. */
    @Nullable private PorterDuffColorFilter mIconTint;
    private int mIconTintColor;
    private float mSelection;

    private boolean mBusy;
    private int mProgress = TerminalWindowBar.WindowItem.NO_PERCENTAGE;
    private boolean mStepped;
    private int mRingColor;

    @NonNull private Mark mMark = Mark.NONE;
    private int mMarkColor;
    private int mGroundColor;

    @NonNull private Agent mAgent = Agent.NONE;
    private int mAgentColor;

    private boolean mRtl;
    /** Invalidated with the bounds; the travelling arc needs the outline's length, not its shape. */
    private float mPathLengthPx = -1f;

    ChipWatermarkDrawable(float density) {
        mDensity = density;
        mFillPaint.setStyle(Paint.Style.FILL);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        // Filled, as the Nerd Font face draws it: the watermark is a printed mark, not an outline.
        mGlyphPaint.setStyle(Paint.Style.FILL);
        mIconPaint.setFilterBitmap(true);
        mDotPaint.setStyle(Paint.Style.FILL);
    }

    /** The chip's own shape and colours; the selected chip's fill is the strip's, drawn beneath. */
    void setSurface(float cornerRadiusPx, float strokePx, int fillColor, int strokeColor) {
        if (mCornerRadiusPx == cornerRadiusPx && mStrokePx == strokePx && mFillColor == fillColor
            && mStrokeColor == strokeColor) return;
        mCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        mStrokePx = Math.max(0f, strokePx);
        mFillColor = fillColor;
        mStrokeColor = strokeColor;
        mStrokePaint.setStrokeWidth(mStrokePx);
        rebuildOutline();
        invalidateSelf();
    }

    /** The process glyph and the face that has it, or null for a chip with nothing to watermark. */
    void setGlyph(@Nullable String glyph, @Nullable Typeface face) {
        if (equal(mGlyph, glyph) && mGlyphFace == face) return;
        mGlyph = glyph;
        mGlyphFace = face;
        invalidateSelf();
    }

    /**
     * The window's own icon, which stands in for the process glyph when there is one — a Display
     * window wearing its app's mark. Already a silhouette; only its alpha is ours.
     */
    void setIcon(@Nullable Bitmap icon) {
        if (mIcon == icon) return;
        mIcon = icon;
        invalidateSelf();
    }

    /**
     * The colour the glyph — or the window's icon silhouette — is printed in: the place accent,
     * not the title's colour. Only its alpha moves with the selection.
     */
    void setGlyphColor(int color) {
        if (mGlyphColor == color) return;
        mGlyphColor = color;
        invalidateSelf();
    }

    /** 0 while the chip is unselected, 1 when it is, and the way between during a selection slide. */
    void setSelection(float selection) {
        float clamped = selection < 0f ? 0f : selection > 1f ? 1f : selection;
        if (mSelection == clamped) return;
        mSelection = clamped;
        invalidateSelf();
    }

    void setActivity(boolean busy, int progress, int ringColor, boolean stepped) {
        if (mBusy == busy && mProgress == progress && mRingColor == ringColor
            && mStepped == stepped) return;
        mBusy = busy;
        mProgress = progress;
        mRingColor = ringColor;
        mStepped = stepped;
        invalidateSelf();
    }

    void setMark(@NonNull Mark mark, int markColor, int groundColor) {
        if (mMark == mark && mMarkColor == markColor && mGroundColor == groundColor) return;
        mMark = mark;
        mMarkColor = markColor;
        mGroundColor = groundColor;
        invalidateSelf();
    }

    void setAgent(@NonNull Agent agent, int color) {
        if (mAgent == agent && mAgentColor == color) return;
        mAgent = agent;
        mAgentColor = color;
        invalidateSelf();
    }

    void setRtl(boolean rtl) {
        if (mRtl == rtl) return;
        mRtl = rtl;
        rebuildOutline();
        invalidateSelf();
    }

    /** For tests and for the bar's own palette pass. */
    float cornerRadiusPx() {
        return mCornerRadiusPx;
    }

    @Nullable
    String glyph() {
        return mGlyph;
    }

    @Nullable
    Typeface glyphFace() {
        return mGlyphFace;
    }

    @Nullable
    Bitmap icon() {
        return mIcon;
    }

    @NonNull
    Mark mark() {
        return mMark;
    }

    @NonNull
    Agent agent() {
        return mAgent;
    }

    boolean busy() {
        return mBusy;
    }

    float selection() {
        return mSelection;
    }

    /** The colour the watermark is printed in right now, alpha included; also what draw() uses. */
    int watermarkColor() {
        return ColorUtils.setAlphaComponent(
            mGlyphColor, ChipWatermarkGeometry.glyphAlpha(mSelection));
    }

    @Override protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildOutline();
    }

    private void rebuildOutline() {
        Rect bounds = getBounds();
        mPathLengthPx = -1f;
        mOutline.rewind();
        if (bounds.isEmpty()) return;
        float inset = mStrokePx / 2f;
        mRect.set(bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset);
        if (mRect.width() <= 0f || mRect.height() <= 0f) return;
        float radius = Math.min(mCornerRadiusPx,
            Math.min(mRect.width(), mRect.height()) / 2f);
        // Built by hand rather than through addRoundRect so the path is known to start at the
        // top-leading corner and travel away from the title's first letter: that start is where a
        // reported percentage fills from, and it mirrors with the layout direction.
        float left = mRect.left;
        float top = mRect.top;
        float right = mRect.right;
        float bottom = mRect.bottom;
        float sweep = mRtl ? -90f : 90f;
        if (!mRtl) {
            mOutline.moveTo(left + radius, top);
            mOutline.lineTo(right - radius, top);
            arc(right - 2f * radius, top, right, top + 2f * radius, 270f, sweep);
            mOutline.lineTo(right, bottom - radius);
            arc(right - 2f * radius, bottom - 2f * radius, right, bottom, 0f, sweep);
            mOutline.lineTo(left + radius, bottom);
            arc(left, bottom - 2f * radius, left + 2f * radius, bottom, 90f, sweep);
            mOutline.lineTo(left, top + radius);
            arc(left, top, left + 2f * radius, top + 2f * radius, 180f, sweep);
        } else {
            mOutline.moveTo(right - radius, top);
            mOutline.lineTo(left + radius, top);
            arc(left, top, left + 2f * radius, top + 2f * radius, 270f, sweep);
            mOutline.lineTo(left, bottom - radius);
            arc(left, bottom - 2f * radius, left + 2f * radius, bottom, 180f, sweep);
            mOutline.lineTo(right - radius, bottom);
            arc(right - 2f * radius, bottom - 2f * radius, right, bottom, 90f, sweep);
            mOutline.lineTo(right, top + radius);
            arc(right - 2f * radius, top, right, top + 2f * radius, 0f, sweep);
        }
        mOutline.close();
    }

    private void arc(float left, float top, float right, float bottom, float startDeg,
                     float sweepDeg) {
        if (right <= left || bottom <= top) return;
        mCorner.set(left, top, right, bottom);
        mOutline.arcTo(mCorner, startDeg, sweepDeg, false);
    }

    private float pathLengthPx() {
        if (mPathLengthPx < 0f) {
            mMeasure.setPath(mOutline, false);
            mPathLengthPx = mMeasure.getLength();
        }
        return mPathLengthPx;
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || mOutline.isEmpty()) return;

        mFillPaint.setColor(mFillColor);
        canvas.drawPath(mOutline, mFillPaint);

        drawWatermark(canvas);
        drawOutline(canvas);
        drawCornerDots(canvas);
    }

    /**
     * The glyph or icon: 21dp in the place accent, hugging the chip's leading edge and vertically
     * centred, clipped by the outline — which is 20dp tall, so the mark is cropped top and bottom.
     */
    private void drawWatermark(@NonNull Canvas canvas) {
        boolean hasIcon = mIcon != null && !mIcon.isRecycled();
        if (!hasIcon && (mGlyph == null || mGlyph.isEmpty())) return;
        int color = watermarkColor();
        float size = ChipWatermarkGeometry.GLYPH_SIZE_DP * mDensity;
        float leading = mRtl ? mRect.right : mRect.left;
        float trailing = mRtl ? mRect.left : mRect.right;
        float cx = ChipWatermarkGeometry.glyphCentreOnAxis(leading, trailing,
            ChipWatermarkGeometry.GLYPH_LEADING_INSET_DP * mDensity, size);
        float cy = mRect.centerY();

        int save = canvas.save();
        canvas.clipPath(mOutline);
        if (hasIcon) {
            mIconSource.set(0, 0, mIcon.getWidth(), mIcon.getHeight());
            mIconTarget.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            // The silhouette carries shape in its alpha and nothing in its colour, so the accent
            // is painted through it — the same mark the process glyph would have drawn.
            mIconPaint.setColorFilter(iconTint(ColorUtils.setAlphaComponent(color, 255)));
            mIconPaint.setAlpha(android.graphics.Color.alpha(color));
            canvas.drawBitmap(mIcon, mIconSource, mIconTarget, mIconPaint);
        } else {
            mGlyphPaint.setTypeface(mGlyphFace);
            mGlyphPaint.setTextSize(size);
            mGlyphPaint.setColor(color);
            Paint.FontMetrics metrics = mGlyphPaint.getFontMetrics();
            canvas.drawText(mGlyph, cx, cy - (metrics.ascent + metrics.descent) / 2f, mGlyphPaint);
        }
        canvas.restoreToCount(save);
    }

    @NonNull
    private PorterDuffColorFilter iconTint(int opaqueColor) {
        if (mIconTint == null || mIconTintColor != opaqueColor) {
            mIconTintColor = opaqueColor;
            mIconTint = new PorterDuffColorFilter(opaqueColor, PorterDuff.Mode.SRC_IN);
        }
        return mIconTint;
    }

    /**
     * The chip's edge, and — while a shell is working — the ring it becomes: an arc travelling the
     * outline once a turn when the shell reports no number, a clockwise fill over a faint track
     * when it does.
     */
    private void drawOutline(@NonNull Canvas canvas) {
        if (mStrokePx <= 0f) return;
        mStrokePaint.setColor(mStrokeColor);
        canvas.drawPath(mOutline, mStrokePaint);
        if (!mBusy) return;

        float length = pathLengthPx();
        if (length <= 0f) return;
        if (mProgress == TerminalWindowBar.WindowItem.NO_PERCENTAGE) {
            float phase = ChipWatermarkGeometry.ringStartFraction(
                WindowActivityRing.phase(SystemClock.uptimeMillis()), mStepped);
            mStrokePaint.setColor(mRingColor);
            drawSegment(canvas, length, ChipWatermarkGeometry.segmentStartPx(length, phase),
                ChipWatermarkGeometry.segmentSweepPx(length,
                    ChipWatermarkGeometry.RING_SWEEP_FRACTION));
            return;
        }
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(
            mRingColor, ChipWatermarkGeometry.RING_TRACK_ALPHA));
        canvas.drawPath(mOutline, mStrokePaint);
        float filled = ChipWatermarkGeometry.segmentSweepPx(length,
            ChipWatermarkGeometry.determinateFraction(mProgress));
        if (filled <= 0f) return;
        mStrokePaint.setColor(mRingColor);
        if (filled >= length) {
            canvas.drawPath(mOutline, mStrokePaint);
            return;
        }
        // The outline is built to start at the top-leading corner, so the fill simply runs from
        // the path's own beginning, whichever way the row reads.
        drawSegment(canvas, length, 0f, filled);
    }

    /** One run along the outline, continued from the start when it runs off the end. */
    private void drawSegment(@NonNull Canvas canvas, float length, float startPx, float sweepPx) {
        mMeasure.setPath(mOutline, false);
        mArc.rewind();
        float tail = ChipWatermarkGeometry.segmentTailPx(length, startPx, sweepPx);
        mMeasure.getSegment(startPx, Math.min(length, startPx + sweepPx), mArc, true);
        if (tail > 0f) mMeasure.getSegment(0f, tail, mArc, true);
        canvas.drawPath(mArc, mStrokePaint);
    }

    /** The mark on the top-trailing corner and the agent reading on the bottom-leading one. */
    private void drawCornerDots(@NonNull Canvas canvas) {
        if (mMark != Mark.NONE) {
            drawDot(canvas, !mRtl, true, mMarkColor, 255);
        }
        if (mAgent != Agent.NONE) {
            drawDot(canvas, mRtl, false, mAgentColor, agentAlpha());
        }
    }

    private int agentAlpha() {
        switch (mAgent) {
            case IDLE: return ChipWatermarkGeometry.AGENT_IDLE_ALPHA;
            case WORKING: return ChipWatermarkGeometry.breathAlpha(
                ChipWatermarkGeometry.ringStartFraction(
                    WindowActivityRing.phase(SystemClock.uptimeMillis()), mStepped));
            default: return 255;
        }
    }

    private void drawDot(@NonNull Canvas canvas, boolean right, boolean top, int color, int alpha) {
        float radius = ChipWatermarkGeometry.DOT_DIAMETER_DP * mDensity / 2f;
        float gap = ChipWatermarkGeometry.DOT_GAP_DP * mDensity;
        float halo = ChipWatermarkGeometry.DOT_HALO_DP * mDensity;
        float cx = ChipWatermarkGeometry.dotCentreOnAxis(right ? mRect.right : mRect.left,
            right ? mRect.left : mRect.right, mCornerRadiusPx, gap, radius, halo);
        float cy = ChipWatermarkGeometry.dotCentreOnAxis(top ? mRect.top : mRect.bottom,
            top ? mRect.bottom : mRect.top, mCornerRadiusPx, gap, radius, halo);
        mDotPaint.setColor(mGroundColor);
        canvas.drawCircle(cx, cy, radius + halo, mDotPaint);
        mDotPaint.setColor(ColorUtils.setAlphaComponent(color, alpha));
        canvas.drawCircle(cx, cy, radius, mDotPaint);
    }

    @Override public void setAlpha(int alpha) {
        // The chip owns its own alphas; a view-level fade is a transform, not a repaint.
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mGlyphPaint.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static boolean equal(@Nullable Object left, @Nullable Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
