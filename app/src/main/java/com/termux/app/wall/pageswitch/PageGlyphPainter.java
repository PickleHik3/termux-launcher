package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.PathParser;

import com.termux.R;
import com.termux.app.page.PageKind;

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

    private final Drawable[] mGlyphs = new Drawable[PageKind.values().length];
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Index 1..99 the numbers, index 0 "99+". */
    private final String[] mNumbers = new String[MAX_NUMBER + 1];
    private final float[] mInkLeft = new float[MAX_NUMBER + 1];
    private final float[] mInkWidth = new float[MAX_NUMBER + 1];
    /** The digits' common ink: its top (negative, above the baseline) and its height. */
    private final float mCapTop;
    private final float mCapHeight;

    public PageGlyphPainter(@NonNull Context context) {
        mGlyphs[PageKind.HOME.ordinal()] = load(context, R.drawable.ic_page_home);
        mGlyphs[PageKind.TERMINAL.ordinal()] = load(context, R.drawable.ic_page_terminal);
        mGlyphs[PageKind.ASSISTANT.ordinal()] = load(context, R.drawable.ic_page_assistant);
        mGlyphs[PageKind.DISPLAY.ordinal()] = load(context, R.drawable.ic_page_display);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        for (int k = 0; k < PATH_DATA.length; k++) {
            mPaths[k] = new Path[PATH_DATA[k].length];
            for (int j = 0; j < PATH_DATA[k].length; j++) {
                mPaths[k][j] = PathParser.createPathFromPathData(PATH_DATA[k][j]);
            }
        }
        Typeface family = Typeface.create(PageGlyphSlots.DIGIT_FAMILY, Typeface.NORMAL);
        mText.setTypeface(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? Typeface.create(family, PageGlyphSlots.DIGIT_WEIGHT, false) : family);
        mText.setTextAlign(Paint.Align.LEFT);
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
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                     float left, float top, float size, int color) {
        drawGlyph(canvas, kind, left, top, size, color);
        drawNumber(canvas, kind, number, left, top, size, color);
    }

    /**
     * {@link #draw}, with a glow when {@code glowColor} has alpha (the focused glyph): the glyph's
     * outlines stroked in that colour wider and wider at falling alpha, widest first, clipped to
     * outside the glyph's filled silhouette so the frame's inside stays clear. The number gets no
     * glow, only the crisp ink. Plain strokes, so it draws the same on every renderer, from reused
     * paths and paint.
     */
    public void drawFloating(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                             float left, float top, float size, int color, int glowColor) {
        if (Color.alpha(glowColor) != 0) {
            float unit = size / PageGlyphSlots.VIEWPORT;
            int save = canvas.save();
            canvas.translate(left, top);
            canvas.scale(unit, unit);
            Path[] paths = mPaths[kind.ordinal()];
            // The first outline of every glyph is its closed outer contour.
            canvas.clipOutPath(paths[0]);
            int rgb = glowColor & 0x00FFFFFF;
            float gain = Color.alpha(glowColor) / 255f;
            for (int i = 0; i < GLOW_WIDTH.length; i++) {
                mStrokePaint.setColor(rgb | (Math.round(GLOW_ALPHA[i] * gain) << 24));
                mStrokePaint.setStrokeWidth(STROKE_WIDTH + GLOW_WIDTH[i]);
                for (int j = 0; j < paths.length; j++) canvas.drawPath(paths[j], mStrokePaint);
            }
            canvas.restoreToCount(save);
        }
        draw(canvas, kind, number, left, top, size, color);
    }

    /** The glow's passes, widest and faintest first, in the glyph's 24-unit viewport. */
    private static final float[] GLOW_WIDTH = {6f, 4f, 2f};
    private static final int[] GLOW_ALPHA = {0x1C, 0x40, 0x70};
    private static final float STROKE_WIDTH = 1.65f;
    /**
     * The glyphs' outlines as the drawables draw them, per kind in {@link PageKind}
     * order, for the glow passes; keep in step with {@code ic_page_*.xml}.
     */
    private static final String[][] PATH_DATA = {
        {"M3.25 9L12 2.75L20.75 9V21.25H3.25Z"},
        {"M5 2.75H19Q21.5 2.75 21.5 5.25V18.75Q21.5 21.25 19 21.25H5Q2.5 21.25 2.5 18.75V5.25Q2.5 2.75 5 2.75Z",
            "M5.25 5.5H7.5"},
        {"M10 5L12.35 11.65L19 14L12.35 16.35L10 23L7.65 16.35L1 14L7.65 11.65Z",
            "M19 1.5V7.5M16 4.5H22"},
        {"M3.75 3.75H20.25Q21.5 3.75 21.5 5V16.5Q21.5 17.75 20.25 17.75H3.75Q2.5 17.75 2.5 16.5V5Q2.5 3.75 3.75 3.75Z",
            "M6 21H18"},
    };

    private final Path[][] mPaths = new Path[PATH_DATA.length][];
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mFit = new float[2];

    /**
     * How a number of ink width {@code inkWidth} fits a slot {@code maxWidth} wide: {@code out[0]}
     * the share of the text size to keep and {@code out[1]} the horizontal scale. One wider than
     * the slot is condensed; three characters and more ({@code overflow}) only to
     * {@link PageGlyphSlots#MIN_CONDENSE}, then shrunk evenly. Shared with {@link PageBarGlyphPainter}.
     */
    static void fit(float inkWidth, float maxWidth, boolean overflow, @NonNull float[] out) {
        out[0] = 1f;
        out[1] = 1f;
        if (inkWidth <= maxWidth) return;
        float scaleX = maxWidth / inkWidth;
        if (overflow && scaleX < PageGlyphSlots.MIN_CONDENSE) {
            out[0] = scaleX / PageGlyphSlots.MIN_CONDENSE;
            scaleX = PageGlyphSlots.MIN_CONDENSE;
        }
        out[1] = scaleX;
    }

    private void drawGlyph(@NonNull Canvas canvas, @NonNull PageKind kind, float left,
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

    /** The number in its slot. */
    private void drawNumber(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                            float left, float top, float size, int color) {
        if (number <= 0) return;
        float cx, cy, slotW, slotH;
        if (kind == PageKind.HOME) {
            cx = PageGlyphSlots.HOME_SLOT_CX;
            cy = PageGlyphSlots.HOME_SLOT_CY;
            slotW = PageGlyphSlots.HOME_SLOT_W;
            slotH = PageGlyphSlots.HOME_SLOT_H;
        } else if (kind == PageKind.TERMINAL) {
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
        fit(mInkWidth[index] * textSize, maxWidth, index == 0, mFit);
        textSize *= mFit[0];
        float scaleX = mFit[1];
        mText.setTextSize(textSize);
        mText.setTextScaleX(scaleX);
        mText.setColor(color);
        // Centre the ink, not the line box: horizontally this number's ink, vertically the digits' caps.
        float x = left + cx * unit - (mInkLeft[index] + mInkWidth[index] / 2f) * textSize * scaleX;
        float baseline = top + cy * unit - (mCapTop + mCapHeight / 2f) * textSize;
        canvas.drawText(mNumbers[index], x, baseline, mText);
    }
}
