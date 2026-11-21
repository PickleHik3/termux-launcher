package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import com.termux.app.page.PageKind;

/**
 * Draws a page glyph: its Roundline outline ({@link PageGlyphSlots}) stroked in one colour and,
 * in the Home and Terminal glyphs, the page number in that colour, bold, centred in the glyph's
 * number box by its ink and sized to fit the box (a single digit stands the box's height, two
 * digits its width). The current page's glyph also lays its interior in its colour at
 * {@link #ACTIVE_FILL_ALPHA}: the one rule for telling it apart, on the page bar, in the key's
 * pop-up and wherever else a glyph is drawn. Numbers above 99 draw as "99+".
 *
 * <p>The paths and every number's ink metrics are built once, here; drawing allocates nothing.
 */
public final class PageGlyphPainter {

    /** The current page's interior wash, as a share of its colour's alpha. */
    static final float ACTIVE_FILL_ALPHA = 0.22f;

    private static final int MAX_NUMBER = 99;
    private static final String OVERFLOW = "99+";
    /** The text size the ink is measured at; every metric below is per unit of text size. */
    private static final float REFERENCE_SIZE = 100f;

    private final Path[] mOutline = new Path[PageKind.values().length];
    /** Each outline's contours filled and joined: what the active wash and the glow's clip cover. */
    private final Path[] mInterior = new Path[PageKind.values().length];
    /** Each kind's filled marks, null where it has none. */
    private final Path[] mDot = new Path[PageKind.values().length];
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG
        | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    /** Index 1..99 the numbers, index 0 "99+". */
    private final String[] mNumbers = new String[MAX_NUMBER + 1];
    private final float[] mInkLeft = new float[MAX_NUMBER + 1];
    private final float[] mInkWidth = new float[MAX_NUMBER + 1];
    /** The digits' common ink: its top (negative, above the baseline) and its height. */
    private final float mCapTop;
    private final float mCapHeight;
    private final float[] mLayout = new float[3];

    public PageGlyphPainter() {
        for (PageKind kind : PageKind.values()) {
            int k = kind.ordinal();
            String data = PageGlyphSlots.outline(kind);
            mOutline[k] = PathParser.createPathFromPathData(data);
            mInterior[k] = interior(data);
            String dot = PageGlyphSlots.dot(kind);
            mDot[k] = dot == null ? null : PathParser.createPathFromPathData(dot);
        }
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mFill.setStyle(Paint.Style.FILL);
        Typeface family = Typeface.create(PageGlyphSlots.DIGIT_FAMILY, Typeface.BOLD);
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
            mInkWidth[n] = Math.max(1e-3f, bounds.width() / REFERENCE_SIZE);
        }
        mText.getTextBounds("0123456789", 0, 10, bounds);
        mCapTop = bounds.top / REFERENCE_SIZE;
        mCapHeight = Math.max(1e-3f, bounds.height() / REFERENCE_SIZE);
    }

    /** Every contour of {@code data} filled on its own, then joined, whichever way it winds. */
    @NonNull
    private static Path interior(@NonNull String data) {
        Path out = new Path();
        for (String contour : data.split("(?=M)")) {
            if (contour.trim().isEmpty()) continue;
            Path one = PathParser.createPathFromPathData(contour);
            if (!out.op(one, Path.Op.UNION)) out.addPath(one);
        }
        return out;
    }

    /** Draws {@code kind}'s glyph in the square at ({@code left}, {@code top}) of side {@code size}. */
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                     float left, float top, float size, int color) {
        draw(canvas, kind, number, left, top, size, color, false);
    }

    /** {@link #draw}, as the current page's glyph when {@code active}. */
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                     float left, float top, float size, int color, boolean active) {
        int save = canvas.save();
        canvas.translate(left, top);
        float unit = size / PageGlyphSlots.VIEWPORT;
        canvas.scale(unit, unit);
        drawUnits(canvas, kind, number, color, active, PageGlyphSlots.STROKE);
        canvas.restoreToCount(save);
    }

    /**
     * The glyph on a canvas already in the 24-unit viewport, its outline {@code stroke} units
     * wide (a caller that scales the canvas further keeps the stroke's on-screen width this way).
     */
    void drawUnits(@NonNull Canvas canvas, @NonNull PageKind kind, int number, int color,
                   boolean active, float stroke) {
        if (active) {
            mFill.setColor(scaleAlpha(color, ACTIVE_FILL_ALPHA));
            canvas.drawPath(mInterior[kind.ordinal()], mFill);
        }
        drawOutline(canvas, kind, color, stroke);
        if (!numberLayout(kind, number, mLayout)) return;
        mText.setTextSize(mLayout[2]);
        mText.setColor(color);
        int index = number > MAX_NUMBER ? 0 : number;
        canvas.drawText(mNumbers[index], mLayout[0], mLayout[1], mText);
    }

    /** The outline and its filled marks alone, no number and no wash, in the viewport's units. */
    void drawOutline(@NonNull Canvas canvas, @NonNull PageKind kind, int color, float stroke) {
        int k = kind.ordinal();
        mStroke.setColor(color);
        mStroke.setStrokeWidth(stroke);
        canvas.drawPath(mOutline[k], mStroke);
        Path dot = mDot[k];
        if (dot != null) {
            mFill.setColor(color);
            canvas.drawPath(dot, mFill);
        }
    }

    /**
     * The glyph's silhouette alone (its interior's edge: no inner marks, ears or stand), stroked
     * {@code stroke} units wide; what a folded stack's back card draws.
     */
    void drawSilhouette(@NonNull Canvas canvas, @NonNull PageKind kind, int color, float stroke) {
        mStroke.setColor(color);
        mStroke.setStrokeWidth(stroke);
        canvas.drawPath(mInterior[kind.ordinal()], mStroke);
    }

    /**
     * Where {@code number} is drawn in {@code kind}'s box, in units: {@code out[0]} the text's x,
     * {@code out[1]} its baseline, {@code out[2]} its size. False when the glyph shows no number.
     * The ink is centred on the box: horizontally this number's, vertically the digits' caps.
     */
    boolean numberLayout(@NonNull PageKind kind, int number, @NonNull float[] out) {
        if (number <= 0 || !PageGlyphSlots.showsNumber(kind)) return false;
        int index = number > MAX_NUMBER ? 0 : number;
        float[] box = PageGlyphSlots.box(kind);
        float boxW = box[2] - box[0];
        float boxH = box[3] - box[1];
        float size = Math.min(boxH / mCapHeight, boxW / mInkWidth[index]);
        out[0] = (box[0] + box[2]) / 2f - (mInkLeft[index] + mInkWidth[index] / 2f) * size;
        out[1] = (box[1] + box[3]) / 2f - (mCapTop + mCapHeight / 2f) * size;
        out[2] = size;
        return true;
    }

    /** The paint the numbers are drawn with, for measuring them. */
    @NonNull Paint textPaint() {
        return mText;
    }

    @NonNull Path outline(@NonNull PageKind kind) {
        return mOutline[kind.ordinal()];
    }

    @NonNull Path interior(@NonNull PageKind kind) {
        return mInterior[kind.ordinal()];
    }

    @Nullable Path dot(@NonNull PageKind kind) {
        return mDot[kind.ordinal()];
    }

    /**
     * {@link #draw}, with a glow when {@code glowColor} has alpha (the focused glyph): the glyph's
     * outline stroked in that colour wider and wider at falling alpha, widest first, clipped to
     * outside the glyph's interior so the inside stays clear. The number gets no glow, only the
     * crisp ink. Plain strokes, so it draws the same on every renderer, from reused paths and paint.
     */
    public void drawFloating(@NonNull Canvas canvas, @NonNull PageKind kind, int number,
                             float left, float top, float size, int color, int glowColor) {
        if (Color.alpha(glowColor) != 0) {
            float unit = size / PageGlyphSlots.VIEWPORT;
            int save = canvas.save();
            canvas.translate(left, top);
            canvas.scale(unit, unit);
            canvas.clipOutPath(mInterior[kind.ordinal()]);
            int rgb = glowColor & 0x00FFFFFF;
            float gain = Color.alpha(glowColor) / 255f;
            for (int i = 0; i < GLOW_WIDTH.length; i++) {
                mStroke.setColor(rgb | (Math.round(GLOW_ALPHA[i] * gain) << 24));
                mStroke.setStrokeWidth(PageGlyphSlots.STROKE + GLOW_WIDTH[i]);
                canvas.drawPath(mOutline[kind.ordinal()], mStroke);
            }
            canvas.restoreToCount(save);
        }
        draw(canvas, kind, number, left, top, size, color);
    }

    /** The glow's passes, widest and faintest first, in the glyph's 24-unit viewport. */
    private static final float[] GLOW_WIDTH = {6f, 4f, 2f};
    private static final int[] GLOW_ALPHA = {0x1C, 0x40, 0x70};

    /** {@code color} with its alpha scaled by {@code share}. */
    static int scaleAlpha(int color, float share) {
        return (color & 0x00FFFFFF) | (Math.round(Color.alpha(color) * share) << 24);
    }
}
