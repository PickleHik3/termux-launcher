package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * The Marks look: Home a dot, a Terminal window a prompt with its underscore, a Display a screen,
 * the Assistant a spark. The current page is a solid disc with its number (or its kind's sign) cut
 * out of it, so the ground shows through the cut; attention is a small burst beside the mark in
 * the colour the caller gives it. The shapes are {@link PageMarkShapes}'s numbers; this class only
 * applies them.
 *
 * <p>Nothing is allocated while drawing: the paints, the path and the point buffers are held, and
 * the numbers' strings are made once for the class.
 */
public final class MarksLook implements PageMarkLook {

    /** A folded stack's front mark is scaled by this and moved toward the bottom-left. */
    static final float STACK_SCALE = 0.85f;
    static final float STACK_SHIFT = 0.1f;
    /** The back card's ink, as a share of the plate ink's alpha. */
    static final float BACK_ALPHA = 0.6f;
    /** The biggest number a disc shows. */
    static final int MAX_NUMBER = 99;

    private static final String[] NUMBERS = numbers();

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final float[] mPoints = new float[PageMarkShapes.STAR_FLOATS];

    public MarksLook() {
        mFill.setStyle(Paint.Style.FILL);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mCut.setStyle(Paint.Style.FILL);
        mCut.setStrokeCap(Paint.Cap.ROUND);
        mCut.setStrokeJoin(Paint.Join.ROUND);
        mCut.setColor(0xFF000000);
        mCut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mCut.setTextAlign(Paint.Align.CENTER);
        mCut.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number, boolean current,
                     float lift, float cx, float cy, float size, int ink) {
        int save = canvas.save();
        canvas.translate(cx, cy);
        mark(canvas, kind, number, current, size, ink);
        canvas.restoreToCount(save);
    }

    @Override
    public void drawStack(@NonNull Canvas canvas, @NonNull PageKind kind, int count,
                          boolean current, float lift, float cx, float cy, float size, int ink,
                          int plateInk) {
        float shift = size * STACK_SHIFT;
        int save = canvas.save();
        canvas.translate(cx, cy);
        int back = canvas.save();
        canvas.translate(shift, -shift);
        mark(canvas, kind, 0, false, size * STACK_SCALE,
            PageGlyphPainter.scaleAlpha(plateInk, BACK_ALPHA));
        canvas.restoreToCount(back);
        canvas.translate(-shift, shift);
        mark(canvas, kind, count, current, size * STACK_SCALE, ink);
        canvas.restoreToCount(save);
    }

    /**
     * The attention burst for a mark in the box centred on ({@code cx}, {@code cy}), in
     * {@code ink}: drawn after the mark, at the box's top-right corner.
     */
    public void drawBurst(@NonNull Canvas canvas, float cx, float cy, float size,
                          @ColorInt int ink) {
        int save = canvas.save();
        canvas.translate(cx, cy);
        PageMarkShapes.burst(size, mPoints);
        polygon(mPoints);
        mFill.setColor(ink);
        canvas.drawPath(mPath, mFill);
        canvas.restoreToCount(save);
    }

    /** One mark centred on the canvas origin. */
    private void mark(Canvas canvas, PageKind kind, int number, boolean current, float size,
                      int ink) {
        if (current) {
            disc(canvas, kind, number, size, ink);
            return;
        }
        switch (kind) {
            case HOME:
                mFill.setColor(ink);
                canvas.drawCircle(0f, 0f, PageMarkShapes.dotRadius(size), mFill);
                break;
            case TERMINAL:
                mStroke.setColor(ink);
                mStroke.setStrokeWidth(PageMarkShapes.strokeWidth(size));
                promptStroke(canvas, size, 1f, mStroke);
                break;
            case DISPLAY:
            case LINUX:
                mStroke.setColor(ink);
                mStroke.setStrokeWidth(PageMarkShapes.strokeWidth(size));
                screenStroke(canvas, size, 1f, mStroke);
                break;
            default:
                mFill.setColor(ink);
                PageMarkShapes.spark(size, 1f, mPoints);
                polygon(mPoints);
                canvas.drawPath(mPath, mFill);
                break;
        }
    }

    /** The current page: a solid disc, its number or sign cut out of a layer of its own. */
    private void disc(Canvas canvas, PageKind kind, int number, float size, int ink) {
        float r = PageMarkShapes.discRadius(size);
        int layer = canvas.saveLayer(-r, -r, r, r, null);
        mFill.setColor(ink);
        canvas.drawCircle(0f, 0f, r, mFill);
        if (PageMarkShapes.cutsNumber(kind, number)) {
            String text = NUMBERS[Math.max(0, Math.min(MAX_NUMBER, number))];
            mCut.setTextSize(size * PageMarkShapes.NUMBER_TEXT * (text.length() > 1 ? 0.8f : 1f));
            canvas.drawText(text, 0f, -(mCut.ascent() + mCut.descent()) / 2f, mCut);
        } else if (PageMarkShapes.hasSign(kind)) {
            mCut.setStyle(Paint.Style.STROKE);
            mCut.setStrokeWidth(PageMarkShapes.strokeWidth(size) * PageMarkShapes.SIGN_SCALE);
            switch (kind) {
                case TERMINAL:
                    promptStroke(canvas, size, PageMarkShapes.SIGN_SCALE, mCut);
                    break;
                case DISPLAY:
                case LINUX:
                    screenStroke(canvas, size, PageMarkShapes.SIGN_SCALE, mCut);
                    break;
                default:
                    mCut.setStyle(Paint.Style.FILL);
                    PageMarkShapes.spark(size, PageMarkShapes.SIGN_SCALE, mPoints);
                    polygon(mPoints);
                    canvas.drawPath(mPath, mCut);
                    break;
            }
            mCut.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(layer);
    }

    private void promptStroke(Canvas canvas, float size, float scale, Paint paint) {
        float[] p = mPoints;
        PageMarkShapes.prompt(size, scale, p);
        mPath.reset();
        mPath.moveTo(p[0], p[1]);
        mPath.lineTo(p[2], p[3]);
        mPath.lineTo(p[4], p[5]);
        canvas.drawPath(mPath, paint);
        canvas.drawLine(p[6], p[7], p[8], p[9], paint);
    }

    private void screenStroke(Canvas canvas, float size, float scale, Paint paint) {
        float[] s = mPoints;
        PageMarkShapes.screen(size, scale, s);
        float corner = PageMarkShapes.screenCorner(size) * scale;
        canvas.drawRoundRect(s[0], s[1], s[2], s[3], corner, corner, paint);
        canvas.drawLine(s[4], s[5], s[6], s[7], paint);
    }

    private void polygon(float[] points) {
        mPath.reset();
        mPath.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) mPath.lineTo(points[i], points[i + 1]);
        mPath.close();
    }

    private static String[] numbers() {
        String[] out = new String[MAX_NUMBER + 1];
        for (int i = 0; i <= MAX_NUMBER; i++) out[i] = Integer.toString(i);
        return out;
    }
}
