package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * The Marks look: Home a dot, a Terminal window a filled rounded square, a Display a screen, the
 * Assistant a spark, Overview two cards. A window's number (or the Terminal's prompt, with none) is
 * cut out of its square or screen, so the ground shows through the cut. The current page is told by
 * ink alone (an app's icon, which keeps its colours, by strength); attention is a small burst beside the mark in the
 * colour the caller gives it. The shapes are {@link PageMarkShapes}'s numbers; this class only
 * applies them.
 *
 * <p>Nothing is allocated while drawing: the paints, the path, the rectangles and the point buffers
 * are held, and the numbers' strings are made once for the class.
 */
public final class MarksLook implements PageMarkLook {

    /** A folded stack's front mark is scaled by this and moved toward the bottom-left. */
    static final float STACK_SCALE = 0.85f;
    static final float STACK_SHIFT = 0.1f;
    /** The back card's ink, as a share of the plate ink's alpha. */
    static final float BACK_ALPHA = 0.6f;
    /** The biggest number a mark shows. */
    static final int MAX_NUMBER = 99;

    private static final String[] NUMBERS = numbers();

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mPoints = new float[PageMarkShapes.STAR_FLOATS];
    private final float[] mRect = new float[PageMarkShapes.RECT_FLOATS];
    private final float[] mStand = new float[PageMarkShapes.RECT_FLOATS];
    private final Rect mTextBounds = new Rect();
    private final Rect mIconBounds = new Rect();

    public MarksLook() {
        mFill.setStyle(Paint.Style.FILL);
        mCut.setStyle(Paint.Style.FILL);
        mCut.setStrokeCap(Paint.Cap.ROUND);
        mCut.setStrokeJoin(Paint.Join.ROUND);
        mCut.setColor(0xFF000000);
        mCut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        // Numbers are centred on their ink bounds, so they are drawn from the left at an offset.
        mCut.setTextAlign(Paint.Align.LEFT);
        mCut.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setStyle(Paint.Style.FILL);
        mText.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number, boolean current,
                     float lift, float cx, float cy, float size, int ink) {
        int save = canvas.save();
        canvas.translate(cx, cy);
        mark(canvas, kind, number, size, ink);
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
        mark(canvas, kind, 0, size * STACK_SCALE, PageGlyphPainter.scaleAlpha(plateInk, BACK_ALPHA));
        canvas.restoreToCount(back);
        canvas.translate(-shift, shift);
        mark(canvas, kind, count, size * STACK_SCALE, ink);
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

    /** Overview's mark: two upright cards side by side, filled in {@code ink}. */
    public void drawOverview(@NonNull Canvas canvas, float cx, float cy, float size,
                             @ColorInt int ink) {
        int save = canvas.save();
        canvas.translate(cx, cy);
        mFill.setColor(ink);
        float corner = PageMarkShapes.cardCorner(size);
        for (int i = 0; i < PageMarkShapes.OVERVIEW_CARDS; i++) {
            PageMarkShapes.overviewCard(size, i, mRect);
            canvas.drawRoundRect(mRect[0], mRect[1], mRect[2], mRect[3], corner, corner, mFill);
        }
        canvas.restoreToCount(save);
    }

    /**
     * An app's icon in its own colours, fitted into a square of the box, at full strength when
     * {@code current} (the page, or the mark in focus) and at {@link PageMarkShapes#ICON_REST_ALPHA}
     * otherwise. The drawable's bounds and alpha are left as they were found.
     */
    public void drawIcon(@NonNull Canvas canvas, @NonNull Drawable icon, boolean current,
                         float cx, float cy, float size) {
        int half = Math.round(size * PageMarkShapes.ICON_SHARE / 2f);
        icon.copyBounds(mIconBounds);
        int save = canvas.save();
        canvas.translate(cx, cy);
        icon.setBounds(-half, -half, half, half);
        int alpha = icon.getAlpha();
        icon.setAlpha(current ? PageMarkShapes.ICON_FULL_ALPHA : PageMarkShapes.ICON_REST_ALPHA);
        icon.draw(canvas);
        icon.setAlpha(alpha);
        icon.setBounds(mIconBounds);
        canvas.restoreToCount(save);
    }

    /** One text glyph in {@code ink}, centred on its ink. */
    public void drawGlyph(@NonNull Canvas canvas, @NonNull String glyph, Typeface face,
                          float cx, float cy, float size, @ColorInt int ink) {
        int save = canvas.save();
        canvas.translate(cx, cy);
        mText.setColor(ink);
        mText.setTypeface(face);
        mText.setTextSize(size * PageMarkShapes.ICON_SHARE);
        centredText(canvas, glyph, mText);
        canvas.restoreToCount(save);
    }

    /** The width of {@code text} at the chip's size, in the look's bold face; for measuring once. */
    public float nameWidth(@NonNull String text, float size) {
        mCut.setTextSize(size * MarksNameChip.TEXT_SHARE);
        return mCut.measureText(text);
    }

    /**
     * The name chip: one pill of the current mark's height with fully round ends, filled in
     * {@code ink}, holding the window's number (when {@code cutNumber} and {@code number} is above
     * 0) and then {@code name}, both cut out like a mark's number. {@code cx} is the leading zone's
     * centre; {@code dir} is 1 when the name runs to the right, -1 mirrored. The caller draws an
     * icon or glyph over the leading zone afterwards. Allocates nothing.
     */
    public void drawChip(@NonNull Canvas canvas, float cx, float cy, float size, @ColorInt int ink,
                         int number, boolean cutNumber, @NonNull String name, float namePx, int dir) {
        float lead = MarksNameChip.leadPx(size);
        float h = MarksNameChip.pillHeight(size);
        float width = MarksNameChip.pillWidthPx(size, namePx);
        float left = dir > 0 ? cx - lead / 2f : cx + lead / 2f - width;
        float pad = PageMarkShapes.LAYER_PAD_PX;
        int layer = canvas.saveLayer(left - pad, cy - h / 2f - pad, left + width + pad,
            cy + h / 2f + pad, null);
        mFill.setColor(ink);
        canvas.drawRoundRect(left, cy - h / 2f, left + width, cy + h / 2f, h / 2f, h / 2f, mFill);
        if (cutNumber && number > 0) {
            String text = NUMBERS[Math.min(MAX_NUMBER, number)];
            mCut.setTextSize(PageMarkShapes.numberTextSize(PageKind.TERMINAL, text.length(), size));
            canvas.save();
            canvas.translate(cx, cy);
            centredText(canvas, text, mCut);
            canvas.restore();
        }
        mCut.setTextSize(size * MarksNameChip.TEXT_SHARE);
        mCut.getTextBounds(name, 0, name.length(), mTextBounds);
        float x = dir > 0 ? cx + MarksNameChip.textOffsetPx(size)
            : cx - MarksNameChip.textOffsetPx(size) - namePx;
        canvas.drawText(name, x, cy - mTextBounds.exactCenterY(), mCut);
        canvas.restoreToCount(layer);
    }

    /** One mark centred on the canvas origin. */
    private void mark(Canvas canvas, PageKind kind, int number, float size, int ink) {
        switch (kind) {
            case HOME:
                mFill.setColor(ink);
                canvas.drawCircle(0f, 0f, PageMarkShapes.dotRadius(size), mFill);
                break;
            case TERMINAL:
                PageMarkShapes.terminal(size, mRect);
                cutShape(canvas, kind, number, size, ink, PageMarkShapes.terminalCorner(size));
                break;
            case DISPLAY:
            case LINUX:
                PageMarkShapes.screenFrame(size, mRect);
                PageMarkShapes.screenStand(size, mStand);
                cutShape(canvas, kind, number, size, ink, PageMarkShapes.screenCorner(size));
                break;
            default:
                mFill.setColor(ink);
                PageMarkShapes.spark(size, 1f, mPoints);
                polygon(mPoints);
                canvas.drawPath(mPath, mFill);
                break;
        }
    }

    /**
     * The filled shape in {@link #mRect} (and the screen's stand in {@link #mStand}), with the
     * number or the prompt cut out of a layer of its own, padded so the soft edge is not clipped.
     */
    private void cutShape(Canvas canvas, PageKind kind, int number, float size, int ink,
                          float corner) {
        boolean screen = kind != PageKind.TERMINAL;
        boolean cutsNumber = PageMarkShapes.cutsNumber(kind, number);
        boolean prompt = PageMarkShapes.cutsPrompt(kind, number);
        float pad = PageMarkShapes.LAYER_PAD_PX;
        int layer = (cutsNumber || prompt)
            ? canvas.saveLayer(mRect[0] - pad, mRect[1] - pad, mRect[2] + pad, mRect[3] + pad, null)
            : -1;
        mFill.setColor(ink);
        canvas.drawRoundRect(mRect[0], mRect[1], mRect[2], mRect[3], corner, corner, mFill);
        float cutX = (mRect[0] + mRect[2]) / 2f;
        float cutY = (mRect[1] + mRect[3]) / 2f;
        if (cutsNumber) {
            String text = NUMBERS[Math.max(0, Math.min(MAX_NUMBER, number))];
            mCut.setTextSize(PageMarkShapes.numberTextSize(kind, text.length(), size));
            canvas.save();
            canvas.translate(cutX, cutY);
            centredText(canvas, text, mCut);
            canvas.restore();
        } else if (prompt) {
            mCut.setStyle(Paint.Style.STROKE);
            mCut.setStrokeWidth(PageMarkShapes.strokeWidth(size) * PageMarkShapes.SIGN_SCALE);
            canvas.save();
            canvas.translate(cutX, cutY);
            promptStroke(canvas, size, PageMarkShapes.SIGN_SCALE, mCut);
            canvas.restore();
            mCut.setStyle(Paint.Style.FILL);
        }
        if (layer >= 0) canvas.restoreToCount(layer);
        if (screen) {
            float standCorner = PageMarkShapes.standCorner(size);
            canvas.drawRoundRect(mStand[0], mStand[1], mStand[2], mStand[3], standCorner,
                standCorner, mFill);
        }
    }

    /** {@code text} centred on the origin by its ink bounds, not its advance and font metrics. */
    private void centredText(Canvas canvas, String text, Paint paint) {
        paint.getTextBounds(text, 0, text.length(), mTextBounds);
        canvas.drawText(text, -mTextBounds.exactCenterX(), -mTextBounds.exactCenterY(), paint);
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
