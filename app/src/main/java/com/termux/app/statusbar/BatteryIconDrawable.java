package com.termux.app.statusbar;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The battery the status bar's battery chip shows before its percentage: a rounded body with a
 * terminal nub on the right, a fill in proportion to the level, and for the states that have one a
 * mark (bolt, check, thermometer) cut into the body. It is drawn from a {@link BatteryChipState}
 * and nothing else; the ink is the widget's own icon colour (it arrives as the image tint, so the
 * role colours, the themes and the scheme chrome all reach it) and {@link #setErrorColor} is the
 * colour for the low and hot states.
 *
 * <p>Geometry is derived from the bounds once per bounds change and the paths are reused, so
 * {@link #draw} allocates nothing. It invalidates only when the state or the fill's pixel width
 * changes, so a level ticking inside one pixel costs nothing.</p>
 */
final class BatteryIconDrawable extends Drawable {

    /** The body's share of the bounds' height, and its outline and gap as shares of the body's. */
    private static final float BODY_HEIGHT = 0.62f;
    private static final float OUTLINE = 0.11f;
    private static final float GAP = 0.09f;
    /** The nub's width and height as shares of the bounds' width and the body's height. */
    private static final float NUB_WIDTH = 0.09f;
    private static final float NUB_HEIGHT = 0.36f;
    /** The body's corner radius, as a share of its height. */
    private static final float CORNER = 0.24f;
    /** The mark's box as a share of the body's interior height, centred in the interior. */
    private static final float MARK_SIZE = 0.86f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBody = new RectF();
    private final RectF mInner = new RectF();
    private final RectF mNub = new RectF();
    private final RectF mFillRect = new RectF();
    private final Path mMark = new Path();

    @ColorInt private int mInk = 0xFFFFFFFF;
    @ColorInt private int mErrorInk = 0xFFFF5449;
    @NonNull private BatteryChipState mState = BatteryChipState.UNKNOWN;
    /** The fill's width in whole pixels as last laid out, so a sub-pixel change redraws nothing. */
    private int mFillPx;
    private float mStroke;
    private float mCorner;

    BatteryIconDrawable() {
        mCut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    /** The colour of the low and hot states; the theme's error. */
    void setErrorColor(@ColorInt int color) {
        if (mErrorInk == color) return;
        mErrorInk = color;
        invalidateSelf();
    }

    /** Shows {@code state}; redraws only when the picture would change. */
    void setState(@NonNull BatteryChipState state) {
        BatteryChipState.Kind before = mState.kind();
        mState = state;
        int fillPx = fillPixels();
        boolean markChanged = before != state.kind();
        if (markChanged) buildMark();
        if (!markChanged && fillPx == mFillPx) return;
        mFillPx = fillPx;
        invalidateSelf();
    }

    /** The image tint the widget sets for its ink. */
    @Override public void setTintList(@Nullable ColorStateList tint) {
        if (tint == null) return;
        int ink = tint.getColorForState(getState(), tint.getDefaultColor());
        if (ink == mInk) return;
        mInk = ink;
        invalidateSelf();
    }

    @Override protected void onBoundsChange(@NonNull Rect bounds) {
        float width = bounds.width();
        float height = bounds.height();
        if (width <= 0f || height <= 0f) return;
        float bodyHeight = height * BODY_HEIGHT;
        float nubWidth = width * NUB_WIDTH;
        float top = bounds.top + (height - bodyHeight) / 2f;
        mBody.set(bounds.left, top, bounds.right - nubWidth, top + bodyHeight);
        mStroke = Math.max(1f, bodyHeight * OUTLINE);
        mCorner = bodyHeight * CORNER;
        float inset = mStroke + Math.max(0.5f, bodyHeight * GAP);
        mInner.set(mBody.left + inset, mBody.top + inset, mBody.right - inset, mBody.bottom - inset);
        float nubHeight = bodyHeight * NUB_HEIGHT;
        float centreY = mBody.centerY();
        mNub.set(mBody.right, centreY - nubHeight / 2f, bounds.right, centreY + nubHeight / 2f);
        mFillPx = fillPixels();
        buildMark();
    }

    private int fillPixels() {
        return Math.round(mInner.width() * mState.fill());
    }

    /** The mark for the current kind, in the interior's centre; empty for the kinds without one. */
    private void buildMark() {
        mMark.rewind();
        if (mInner.isEmpty()) return;
        float size = mInner.height() * MARK_SIZE;
        float left = mInner.centerX() - size / 2f;
        float top = mInner.centerY() - size / 2f;
        switch (mState.kind()) {
            case CHARGING: {
                // A bolt: the usual zig-zag, in the mark's box.
                mMark.moveTo(left + size * 0.62f, top);
                mMark.lineTo(left + size * 0.18f, top + size * 0.58f);
                mMark.lineTo(left + size * 0.46f, top + size * 0.58f);
                mMark.lineTo(left + size * 0.36f, top + size);
                mMark.lineTo(left + size * 0.82f, top + size * 0.38f);
                mMark.lineTo(left + size * 0.54f, top + size * 0.38f);
                mMark.close();
                break;
            }
            case FULL: {
                // A check, stroked: its width is set where it is drawn.
                mMark.moveTo(left + size * 0.16f, top + size * 0.54f);
                mMark.lineTo(left + size * 0.42f, top + size * 0.80f);
                mMark.lineTo(left + size * 0.86f, top + size * 0.22f);
                break;
            }
            case HOT: {
                // A thermometer: a stem over a bulb.
                float cx = left + size / 2f;
                float stem = size * 0.13f;
                float bulb = size * 0.2f;
                mMark.addRoundRect(cx - stem, top, cx + stem, top + size * 0.68f,
                    stem, stem, Path.Direction.CW);
                mMark.addCircle(cx, top + size - bulb, bulb, Path.Direction.CW);
                break;
            }
            default:
                break;
        }
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (mBody.isEmpty()) return;
        BatteryChipState.Kind kind = mState.kind();
        int ink = kind == BatteryChipState.Kind.LOW || kind == BatteryChipState.Kind.HOT
            ? mErrorInk : mInk;

        mPaint.setColor(ink);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStroke);
        float half = mStroke / 2f;
        mBody.inset(half, half);
        canvas.drawRoundRect(mBody, mCorner - half, mCorner - half, mPaint);
        mBody.inset(-half, -half);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(mNub, mNub.width() / 2f, mNub.width() / 2f, mPaint);

        boolean marked = !mMark.isEmpty();
        if (mFillPx <= 0) {
            if (marked) drawMark(canvas, mPaint, ink);
            return;
        }
        mFillRect.set(mInner.left, mInner.top, mInner.left + mFillPx, mInner.bottom);
        float radius = Math.max(0f, mCorner - mStroke);
        if (!marked) {
            canvas.drawRoundRect(mFillRect, radius, radius, mPaint);
            return;
        }
        // The mark is cut out of the fill, and drawn in the ink over the part still empty.
        int layer = canvas.saveLayer(mBody, null);
        canvas.drawRoundRect(mFillRect, radius, radius, mPaint);
        drawMark(canvas, mCut, ink);
        canvas.restoreToCount(layer);
        if (mFillRect.right >= mInner.right) return;
        int clip = canvas.save();
        canvas.clipRect(mFillRect.right, mBody.top, mBody.right, mBody.bottom);
        drawMark(canvas, mPaint, ink);
        canvas.restoreToCount(clip);
    }

    private void drawMark(Canvas canvas, Paint paint, @ColorInt int ink) {
        boolean check = mState.kind() == BatteryChipState.Kind.FULL;
        if (paint != mCut) paint.setColor(ink);
        if (check) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, mInner.height() * 0.17f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        } else {
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.drawPath(mMark, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
