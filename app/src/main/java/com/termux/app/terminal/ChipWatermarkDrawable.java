package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
 * watermarked behind the title, and the outline — which becomes the ring while a shell works, and
 * takes the mark's colour, fill and all, while the window has news.
 *
 * <p>The chip's label is the title alone, so everything the label used to spend characters on lives
 * here instead, out of the text's way. Nothing is stored per frame: the breath reads the bar's one
 * clock at draw time, so the bar only has to invalidate the chips that are moving.
 *
 * <p>All geometry comes from {@link ChipWatermarkGeometry}; this class is the paint.
 */
final class ChipWatermarkDrawable extends Drawable {

    /**
     * Which mark, if any, the chip wears: this window wants the user, has just finished, or has
     * just failed. The whole chip carries it, as a working chip carries its tint, rather than a
     * dot in one corner.
     */
    enum Mark {
        NONE, ATTENTION, DONE, FAILED;

        /**
         * Whether the mark fills the chip as well as edging it. A failure is edge only: it shares
         * the attention colour, and hollow against filled is what keeps "this went wrong" from
         * reading as "this wants you", as the dot's ring against its disc did.
         */
        boolean filled() {
            return this == ATTENTION || this == DONE;
        }
    }

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    /** Lazy mode: a working chip is a still tint rather than a breath. */
    private boolean mTinted;
    private int mRingColor;

    @NonNull private Mark mMark = Mark.NONE;
    private int mMarkColor;

    private boolean mRtl;
    /**
     * Invalidated with the outline; the percentage fill needs the outline's length, not its shape.
     * Set together with {@link #mMeasure}'s path, so a measure that has a length is a measure of
     * the current outline and a draw never has to hand it the path again.
     */
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

    void setActivity(boolean busy, int progress, int ringColor, boolean tinted) {
        if (mBusy == busy && mProgress == progress && mRingColor == ringColor
            && mTinted == tinted) return;
        mBusy = busy;
        mProgress = progress;
        mRingColor = ringColor;
        mTinted = tinted;
        invalidateSelf();
    }

    /**
     * Whether this chip draws its working state as a still tint (lazy mode, no percentage). A mark
     * outranks it: the news is what the user has to act on.
     */
    boolean workingTint() {
        return mBusy && mTinted && mProgress == TerminalWindowBar.WindowItem.NO_PERCENTAGE
            && mMark == Mark.NONE;
    }

    /** Whether this chip breathes: working, not tinted, unmarked, and with no percentage to fill. */
    boolean breathing() {
        return mBusy && !mTinted && mProgress == TerminalWindowBar.WindowItem.NO_PERCENTAGE
            && mMark == Mark.NONE;
    }

    void setMark(@NonNull Mark mark, int markColor) {
        if (mMark == mark && mMarkColor == markColor) return;
        mMark = mark;
        mMarkColor = markColor;
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

    /** The colour the mark edges the chip in, and tints it with when the mark is filled. */
    int markColor() {
        return mMarkColor;
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

        // The resting surface fades out as the chip becomes the current one: the strip draws the
        // selected pill beneath, over the chip and its × together, and a second fill and outline
        // ending at the title's edge on top of it is the two crossing pills the user saw.
        mFillPaint.setColor(restingAlpha(mFillColor));
        canvas.drawPath(mOutline, mFillPaint);
        if (mMark != Mark.NONE) {
            // Lazy mode's working tint in the mark's colour: still, so a marked chip costs no
            // frames, and the same strength, so news and work read with the same weight.
            if (mMark.filled()) {
                mFillPaint.setColor(ColorUtils.setAlphaComponent(mMarkColor,
                    ChipWatermarkGeometry.BUSY_TINT_ALPHA));
                canvas.drawPath(mOutline, mFillPaint);
            }
        } else if (workingTint()) {
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mRingColor, ChipWatermarkGeometry.BUSY_TINT_ALPHA));
            canvas.drawPath(mOutline, mFillPaint);
        } else if (breathing()) {
            // The breath fills the whole chip, not just its edge: lazy mode's working tint,
            // brightening and dimming with the outline.
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mRingColor, Math.round(
                ChipWatermarkGeometry.BUSY_TINT_ALPHA
                    * WindowActivityRing.breatheLevel(SystemClock.uptimeMillis()))));
            canvas.drawPath(mOutline, mFillPaint);
        }

        drawWatermark(canvas);
        drawOutline(canvas);
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

    /** A resting colour scaled by how far this chip is from being the selected one. */
    private int restingAlpha(int color) {
        return ColorUtils.setAlphaComponent(color,
            Math.round(Color.alpha(color) * (1f - mSelection)));
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
     * The chip's edge, and what it becomes: the whole edge in the mark's colour while the window
     * has news, which outranks any ring; otherwise, while a shell is working, the whole edge
     * breathing in the ring colour when the shell reports no number (in lazy mode, the same colour,
     * still, over a tinted fill), a clockwise fill over a faint track when it does.
     */
    private void drawOutline(@NonNull Canvas canvas) {
        if (mStrokePx <= 0f) return;
        int restingStroke = restingAlpha(mStrokeColor);
        if (Color.alpha(restingStroke) > 0) {
            mStrokePaint.setColor(restingStroke);
            canvas.drawPath(mOutline, mStrokePaint);
        }
        if (mMark != Mark.NONE) {
            mStrokePaint.setColor(mMarkColor);
            canvas.drawPath(mOutline, mStrokePaint);
            return;
        }
        if (!mBusy) return;

        if (workingTint()) {
            // The still form of the ring: the whole edge in the ring colour, drawn once.
            mStrokePaint.setColor(mRingColor);
            canvas.drawPath(mOutline, mStrokePaint);
            return;
        }
        float length = pathLengthPx();
        if (length <= 0f) return;
        if (breathing()) {
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(mRingColor, Math.round(
                Color.alpha(mRingColor)
                    * WindowActivityRing.breatheLevel(SystemClock.uptimeMillis()))));
            canvas.drawPath(mOutline, mStrokePaint);
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

    /**
     * One run along the outline, continued from the start when it runs off the end. Relies on
     * {@link #pathLengthPx()} having produced {@code length}, which leaves the measure on the
     * current outline.
     */
    private void drawSegment(@NonNull Canvas canvas, float length, float startPx, float sweepPx) {
        mArc.rewind();
        float tail = ChipWatermarkGeometry.segmentTailPx(length, startPx, sweepPx);
        mMeasure.getSegment(startPx, Math.min(length, startPx + sweepPx), mArc, true);
        if (tail > 0f) mMeasure.getSegment(0f, tail, mArc, true);
        canvas.drawPath(mArc, mStrokePaint);
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
