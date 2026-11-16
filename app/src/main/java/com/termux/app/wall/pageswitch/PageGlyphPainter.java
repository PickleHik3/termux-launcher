package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
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
        mBlurPaint.setStyle(Paint.Style.STROKE);
        mBlurPaint.setStrokeCap(Paint.Cap.ROUND);
        mBlurPaint.setStrokeJoin(Paint.Join.ROUND);
        mBlurPaint.setStrokeWidth(STROKE_WIDTH);
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
    public void draw(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                     float left, float top, float size, int color) {
        drawGlyph(canvas, kind, left, top, size, color);
        drawNumber(canvas, kind, number, left, top, size, color, null);
    }

    /**
     * {@link #draw} for a glyph that floats over content with no surface behind it. With
     * {@code glowColor} set (the focused glyph) a soft glow of that colour is blurred outside the
     * stroke and number, and nothing else sits under it. Otherwise {@code shadowColor} gives a
     * faint blurred lift under the stroke and number, in the colour away from the ink. Either is
     * drawn from the glyph's own paths on one reused paint, once per draw; the glyph itself stays
     * the crisp icon on top. A colour with no alpha draws nothing.
     */
    public void drawFloating(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                             float left, float top, float size, int color, int shadowColor,
                             int glowColor) {
        boolean glow = Color.alpha(glowColor) != 0;
        if (glow || Color.alpha(shadowColor) != 0) {
            float unit = size / PageGlyphSlots.VIEWPORT;
            int save = canvas.save();
            canvas.translate(left, top + (glow ? 0f : SHADOW_DY * unit));
            canvas.scale(unit, unit);
            MaskFilter blur = glow ? mGlowBlur : mShadowBlur;
            int tint = glow ? glowColor : shadowColor;
            mBlurPaint.setMaskFilter(blur);
            mBlurPaint.setColor(tint);
            Path[] paths = mPaths[kind.ordinal()];
            for (int i = 0; i < paths.length; i++) canvas.drawPath(paths[i], mBlurPaint);
            drawNumber(canvas, kind, number, 0f, 0f, PageGlyphSlots.VIEWPORT, tint, blur);
            canvas.restoreToCount(save);
        }
        draw(canvas, kind, number, left, top, size, color);
    }

    /** The blurs' radii and the shadow's drop, in the glyph's 24-unit viewport. */
    private static final float SHADOW_BLUR = 0.9f;
    private static final float GLOW_BLUR = 2.6f;
    private static final float SHADOW_DY = 0.7f;
    private static final float STROKE_WIDTH = 1.65f;
    /**
     * The glyphs' outlines as the drawables draw them, per kind in {@link PageSwitchModel.Kind}
     * order, for the blurred passes; keep in step with {@code ic_page_*.xml}.
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
    private final Paint mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MaskFilter mShadowBlur = new BlurMaskFilter(SHADOW_BLUR, BlurMaskFilter.Blur.NORMAL);
    private final MaskFilter mGlowBlur = new BlurMaskFilter(GLOW_BLUR, BlurMaskFilter.Blur.NORMAL);

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

    /** The number in its slot; a {@code blur} draws it soft, for the glow and the shadow. */
    private void drawNumber(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind, int number,
                            float left, float top, float size, int color, @Nullable MaskFilter blur) {
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
        mText.setMaskFilter(blur);
        // Centre the ink, not the line box: horizontally this number's ink, vertically the digits' caps.
        float x = left + cx * unit - (mInkLeft[index] + mInkWidth[index] / 2f) * textSize * scaleX;
        float baseline = top + cy * unit - (mCapTop + mCapHeight / 2f) * textSize;
        canvas.drawText(mNumbers[index], x, baseline, mText);
        mText.setMaskFilter(null);
    }
}
