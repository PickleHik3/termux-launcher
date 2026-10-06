package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * A clock's big numerals: a main run ("13:40") and an optional smaller tail in another colour
 * (":12", " PM"), drawn on one baseline with the design's tight {@code line-height:1}.
 *
 * <p>It shrinks to fit the width it is given rather than ellipsising, so a 46sp time in a 2×2
 * card at font scale 1.3 reads "13:40" a little smaller instead of "13:4…". Changing the text
 * only invalidates, unless the run got wider or narrower (a 12-hour clock going from 9:59 to
 * 10:00), so the once-a-second tail never relayouts the card.</p>
 */
final class ClockNumeralView extends View {
    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final float mainSizePx;
    private float tailSizePx;
    private int horizontalGravity = Gravity.START;
    @NonNull private String main = "";
    @NonNull private String tail = "";
    private float naturalWidth;
    private float scale = 1f;
    private int baseline = -1;

    ClockNumeralView(@NonNull Context context, @NonNull Typeface face, float sizeSp,
                     @ColorInt int color, float letterSpacingEm) {
        super(context);
        mainSizePx = sp(sizeSp);
        tailSizePx = mainSizePx;
        mainPaint.setTypeface(face);
        mainPaint.setColor(color);
        mainPaint.setLetterSpacing(letterSpacingEm);
        tailPaint.setTypeface(face);
        tailPaint.setColor(color);
        tailPaint.setLetterSpacing(letterSpacingEm);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The tail's size and colour: the seconds and the AM/PM marker are smaller and quieter. */
    void setTailStyle(float sizeSp, @ColorInt int color) {
        tailSizePx = sp(sizeSp);
        tailPaint.setColor(color);
    }

    void setHorizontalGravity(int gravity) {
        horizontalGravity = gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
    }

    void setText(@NonNull String main, @NonNull String tail) {
        if (main.equals(this.main) && tail.equals(this.tail)) return;
        this.main = main;
        this.tail = tail;
        float natural = measureNatural();
        if (Math.abs(natural - naturalWidth) > 0.5f) {
            naturalWidth = natural;
            requestLayout();
        }
        invalidate();
    }

    /** The text's width at full size; leaves the paints at the size the last measure chose. */
    private float measureNatural() {
        mainPaint.setTextSize(mainSizePx);
        tailPaint.setTextSize(tailSizePx);
        float width = mainPaint.measureText(main) + (tail.isEmpty() ? 0f : tailPaint.measureText(tail));
        mainPaint.setTextSize(mainSizePx * scale);
        tailPaint.setTextSize(tailSizePx * scale);
        return width;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        naturalWidth = measureNatural();
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        scale = widthMode != MeasureSpec.UNSPECIFIED && available > 0 && naturalWidth > available
            ? available / naturalWidth : 1f;
        mainPaint.setTextSize(mainSizePx * scale);
        tailPaint.setTextSize(tailSizePx * scale);
        int contentWidth = (int) Math.ceil(naturalWidth * scale);
        int width = resolveSize(contentWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int lineHeight = (int) Math.ceil(mainSizePx * scale);
        int height = resolveSize(lineHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        baseline = baselineFor(height);
    }

    /** Where the text sits: the design's one-em line box, centred in whatever height we got. */
    private int baselineFor(int height) {
        Paint.FontMetrics metrics = mainPaint.getFontMetrics();
        float line = mainSizePx * scale;
        float inner = height - getPaddingTop() - getPaddingBottom();
        float lineTop = getPaddingTop() + (inner - line) / 2f;
        float content = metrics.descent - metrics.ascent;
        return Math.round(lineTop + (line - content) / 2f - metrics.ascent);
    }

    @Override public int getBaseline() { return baseline; }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        if (main.isEmpty() && tail.isEmpty()) return;
        float mainWidth = mainPaint.measureText(main);
        float total = mainWidth + (tail.isEmpty() ? 0f : tailPaint.measureText(tail));
        float left = getPaddingLeft();
        float room = getWidth() - getPaddingLeft() - getPaddingRight();
        int gravity = Gravity.getAbsoluteGravity(horizontalGravity, getLayoutDirection())
            & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (gravity == Gravity.CENTER_HORIZONTAL) left += (room - total) / 2f;
        else if (gravity == Gravity.RIGHT) left += room - total;
        int y = baseline >= 0 ? baseline : baselineFor(getHeight());
        canvas.drawText(main, left, y, mainPaint);
        if (!tail.isEmpty()) canvas.drawText(tail, left + mainWidth, y, tailPaint);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }
}
