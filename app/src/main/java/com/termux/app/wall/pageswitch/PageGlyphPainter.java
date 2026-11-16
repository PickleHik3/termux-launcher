package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.termux.R;

/**
 * Draws a page glyph (a stroked outline, tinted one colour) and, in the Home and Terminal glyphs,
 * the page number in the slot {@link PageGlyphSlots} gives: the digits' ink stands the slot's cap
 * height and is centred on the slot by its measured ink, condensed when wider than the slot.
 * The drawables and every number's ink metrics are loaded once; {@link #draw} allocates nothing.
 * Numbers above 99 draw as "99+".
 */
public final class PageGlyphPainter {

    private static final int MAX_NUMBER = 99;
    private static final String OVERFLOW = "99+";
    /** The text size the ink is measured at; every metric below is per unit of text size. */
    private static final float REFERENCE_SIZE = 100f;

    private final Drawable[] mGlyphs = new Drawable[PageSwitchModel.Kind.values().length];
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Index 1..99 the numbers, index 0 "99+". */
    private final String[] mNumbers = new String[MAX_NUMBER + 1];
    private final float[] mInkLeft = new float[MAX_NUMBER + 1];
    private final float[] mInkWidth = new float[MAX_NUMBER + 1];
    /** The digits' common ink: its top (negative, above the baseline) and its height. */
    private final float mCapTop;
    private final float mCapHeight;

    public PageGlyphPainter(@NonNull Context context) {
        mGlyphs[PageSwitchModel.Kind.HOME.ordinal()] = load(context, R.drawable.ic_page_home);
        mGlyphs[PageSwitchModel.Kind.TERMINAL.ordinal()] = load(context, R.drawable.ic_page_terminal);
        mGlyphs[PageSwitchModel.Kind.ASSISTANT.ordinal()] = load(context, R.drawable.ic_page_assistant);
        mGlyphs[PageSwitchModel.Kind.DISPLAY.ordinal()] = load(context, R.drawable.ic_page_display);
        Typeface family = Typeface.create(PageGlyphSlots.DIGIT_FAMILY, Typeface.NORMAL);
        mText.setTypeface(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? Typeface.create(family, PageGlyphSlots.DIGIT_WEIGHT, false) : family);
        mText.setTextAlign(Paint.Align.LEFT);
        mText.setStrokeJoin(Paint.Join.ROUND);
        // Tabular lining digits where the font has them, so 1 and 11 sit alike.
        mText.setFontFeatureSettings("tnum");
        mText.setTextSize(REFERENCE_SIZE);
        Rect bounds = new Rect();
        for (int n = 0; n <= MAX_NUMBER; n++) {
            String s = n == 0 ? OVERFLOW : Integer.toString(n);
            mNumbers[n] = s;
            mText.getTextBounds(s, 0, s.length(), bounds);
            mInkLeft[n] = bounds.left / REFERENCE_SIZE;
            mInkWidth[n] = bounds.width() / REFERENCE_SIZE;
        }
        mText.getTextBounds("0123456789", 0, 10, bounds);
        mCapTop = bounds.top / REFERENCE_SIZE;
        mCapHeight = Math.max(1e-3f, bounds.height() / REFERENCE_SIZE);
    }

    @Nullable
    private static Drawable load(Context context, int res) {
        Drawable d = AppCompatResources.getDrawable(context, res);
        return d == null ? null : d.mutate();
    }

    /** Draws {@code kind}'s glyph in the square at ({@code left}, {@code top}) of side {@code size}. */
    public void draw(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                     float left, float top, float size, int color) {
        drawGlyph(canvas, kind, left, top, size, color);
        drawNumber(canvas, kind, number, left, top, size, color, 0f);
    }

    /**
     * {@link #draw} for a glyph that floats over content with no surface behind it: the A-Z
     * bar's halo under it, the stroke ring that gives it an edge wherever the content matches its
     * ink. The outline is a ring of copies at {@code haloRadiusPx}; the number's is a text stroke.
     */
    public void drawFloating(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                             float left, float top, float size, int color, int haloColor,
                             float haloRadiusPx) {
        if (Color.alpha(haloColor) != 0 && haloRadiusPx > 0f) {
            for (int k = 0; k < RING_DX.length; k++) {
                drawGlyph(canvas, kind, left + RING_DX[k] * haloRadiusPx,
                    top + RING_DY[k] * haloRadiusPx, size, haloColor);
            }
            drawNumber(canvas, kind, number, left, top, size, haloColor, haloRadiusPx * 2f);
        }
        draw(canvas, kind, number, left, top, size, color);
    }

    private static final float[] RING_DX = {1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f};
    private static final float[] RING_DY = {0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f};

    private void drawGlyph(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, float left,
                           float top, float size, int color) {
        Drawable d = mGlyphs[kind.ordinal()];
        if (d == null) return;
        // The tint covers the whole drawable, the stroke included.
        d.setTint(color);
        int l = Math.round(left);
        int t = Math.round(top);
        int s = Math.round(size);
        d.setBounds(l, t, l + s, t + s);
        d.draw(canvas);
    }

    /** The number in its slot; {@code strokePx} above 0 draws it as an outline of that width. */
    private void drawNumber(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                            float left, float top, float size, int color, float strokePx) {
        if (number <= 0) return;
        float cx, cy, slotW, slotH;
        if (kind == PageSwitchModel.Kind.HOME) {
            cx = PageGlyphSlots.HOME_SLOT_CX;
            cy = PageGlyphSlots.HOME_SLOT_CY;
            slotW = PageGlyphSlots.HOME_SLOT_W;
            slotH = PageGlyphSlots.HOME_SLOT_H;
        } else if (kind == PageSwitchModel.Kind.TERMINAL) {
            cx = PageGlyphSlots.TERMINAL_SLOT_CX;
            cy = PageGlyphSlots.TERMINAL_SLOT_CY;
            slotW = PageGlyphSlots.TERMINAL_SLOT_W;
            slotH = PageGlyphSlots.TERMINAL_SLOT_H;
        } else {
            return;
        }
        int index = number > MAX_NUMBER ? 0 : number;
        float unit = size / PageGlyphSlots.VIEWPORT;
        float maxWidth = slotW * unit;
        // The cap height is the slot's: the text size follows from the digits' measured ink.
        float textSize = slotH * unit / mCapHeight;
        float inkWidth = mInkWidth[index] * textSize;
        float scaleX = 1f;
        if (inkWidth > maxWidth) {
            scaleX = maxWidth / inkWidth;
            // Three characters and more: condense only so far, then shrink evenly.
            if (index == 0 && scaleX < PageGlyphSlots.MIN_CONDENSE) {
                textSize *= scaleX / PageGlyphSlots.MIN_CONDENSE;
                scaleX = PageGlyphSlots.MIN_CONDENSE;
            }
        }
        mText.setTextSize(textSize);
        mText.setTextScaleX(scaleX);
        mText.setColor(color);
        if (strokePx > 0f) {
            mText.setStyle(Paint.Style.STROKE);
            mText.setStrokeWidth(strokePx);
        }
        // Centre the ink, not the line box: horizontally this number's ink, vertically the digits' caps.
        float x = left + cx * unit - (mInkLeft[index] + mInkWidth[index] / 2f) * textSize * scaleX;
        float baseline = top + cy * unit - (mCapTop + mCapHeight / 2f) * textSize;
        canvas.drawText(mNumbers[index], x, baseline, mText);
        if (strokePx > 0f) mText.setStyle(Paint.Style.FILL);
    }
}
