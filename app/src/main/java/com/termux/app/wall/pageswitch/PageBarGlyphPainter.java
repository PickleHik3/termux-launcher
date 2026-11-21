package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.PathParser;

import com.termux.app.page.PageKind;

/**
 * Draws the page bar's glyphs as filled shapes, the mock's geometry in units centred on the
 * glyph's centre, {@link #GLYPH_UNITS} wide. The page number is a cut-out of the shape (and the
 * Terminal's chevron too), so what lies behind the glyph shows through on every ground.
 *
 * <p>Every (kind, number) cut path is built once, on first use, in unit coordinates from text
 * measured at a large reference size, and kept; {@link #draw} then only scales the canvas, so it
 * allocates nothing after a number's first draw. All the geometry lives in the constants below.
 */
public final class PageBarGlyphPainter {

    /** The glyph's width in the geometry's units. */
    public static final float GLYPH_UNITS = 22f;

    // ---- Geometry (units centred on the glyph) ----------------------------------------------

    static final String HOME_PATH = "M-1.6 -9.8 Q0 -11.1 1.6 -9.8 L9.1 -3.8 Q10.5 -2.7 10.5 -0.9"
        + " V6 A4.5 4.5 0 0 1 6 10.5 H-6 A4.5 4.5 0 0 1 -10.5 6 V-0.9 Q-10.5 -2.7 -9.1 -3.8 Z";
    /** The Terminal body: x, y, w, h, radius. */
    static final float[] TERMINAL_BODY = {-10.5f, -9.5f, 21f, 19f, 4.5f};
    static final String CHEVRON_PATH = "M-7.3 -2.6 L-4.7 0 L-7.3 2.6";
    static final float CHEVRON_STROKE = 1.8f;
    /** The Display's three rects: x, y, w, h, radius. */
    static final float[][] DISPLAY_RECTS = {
        {-10.5f, -9.5f, 21f, 15f, 4.5f}, {-1.5f, 4.5f, 3f, 4f, 1.5f}, {-6f, 7.5f, 12f, 3f, 1.5f}};
    static final String SPARK_PATH = "M-0.95 -9.45 A1.0 1.0 0 0 1 0.95 -9.45"
        + " C1.7 -4.3 4.3 -1.7 9.45 -0.95 A1.0 1.0 0 0 1 9.45 0.95"
        + " C4.3 1.7 1.7 4.3 0.95 9.45 A1.0 1.0 0 0 1 -0.95 9.45"
        + " C-1.7 4.3 -4.3 1.7 -9.45 0.95 A1.0 1.0 0 0 1 -9.45 -0.95"
        + " C-4.3 -1.7 -1.7 -4.3 -0.95 -9.45 Z";
    /** The Assistant's spark: translate x, translate y, scale. */
    static final float[][] SPARKS = {{0f, 0f, 1f}};

    /** A folded stack's front glyph is scaled by this, then moved by the translation. */
    static final float STACK_SCALE = 0.85f;
    static final float STACK_TX = -1.5f;
    static final float STACK_TY = 1.5f;
    /** The back and front plate of a folded stack: x, y, w, h, radius, alpha. */
    static final float[][] PLATES = {
        {-6.925f, -10.925f, 17.85f, 16.15f, 3.825f, 0.5f},
        {-8.425f, -9.425f, 17.85f, 16.15f, 3.825f, 0.8f}};

    /** Ground left showing between neighbouring cards of a stack, in units. */
    static final float CARD_GAP = 1.5f;

    /** How far a folded stack's plates reach past the glyph box's right edge, in units. */
    static final float STACK_OVERHANG_RIGHT = overhang(true);
    /** The same past the left edge. */
    static final float STACK_OVERHANG_LEFT = overhang(false);

    private static float overhang(boolean right) {
        float edge = right ? -GLYPH_UNITS : GLYPH_UNITS;
        for (float[] plate : PLATES) {
            edge = right ? Math.max(edge, plate[0] + plate[2]) : Math.min(edge, plate[0]);
        }
        return Math.max(0f, right ? edge - GLYPH_UNITS / 2f : -GLYPH_UNITS / 2f - edge);
    }

    /** The number's font size in units and its weight; per kind its slot centre x, width, baseline. */
    static final float NUMBER_SIZE = 9.5f;
    static final int NUMBER_WEIGHT = 600;
    static final float HOME_SLOT_CX = 0f;
    static final float HOME_SLOT_W = 15f;
    static final float HOME_BASELINE = 4.2f;
    static final float TERMINAL_SLOT_CX = 3.15f;
    static final float TERMINAL_SLOT_W = 10.8f;
    static final float TERMINAL_BASELINE = 4.2f;

    // ---- State ------------------------------------------------------------------------------

    private static final int MAX_NUMBER = 99;
    private static final String OVERFLOW = "99+";
    /** Cache index 1..99 the numbers, 0 "99+", {@link #NONE} no number. */
    private static final int NONE = MAX_NUMBER + 1;
    /** Text is measured at this many times its size in units, so small sizes are not hinted. */
    private static final float REFERENCE_SCALE = 16f;

    private final Path[][] mCut = new Path[PageKind.values().length][MAX_NUMBER + 2];
    private final Path[] mSilhouette = new Path[PageKind.values().length];
    private final Path mPlate = new Path();
    private final Path mPlateGrown;
    private final Path[] mFolded = new Path[PageKind.values().length];
    private final Path[] mGrown = new Path[PageKind.values().length];
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final RectF mBounds = new RectF();
    private final float[] mFit = new float[2];

    public PageBarGlyphPainter() {
        Typeface family = Typeface.create(PageGlyphSlots.DIGIT_FAMILY, Typeface.NORMAL);
        mText.setTypeface(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? Typeface.create(family, NUMBER_WEIGHT, false) : family);
        mText.setTextAlign(Paint.Align.LEFT);
        mText.setFontFeatureSettings("tnum");
        mText.setTextSize(NUMBER_SIZE * REFERENCE_SCALE);
        float[] plate = PLATES[0];
        mPlate.addRoundRect(0f, 0f, plate[2], plate[3], plate[4], plate[4], Path.Direction.CW);
        mPlateGrown = grow(mPlate);
    }

    /** {@code kind}'s glyph, filled in {@code fill} with its number cut out, centred on ({@code cx}, {@code cy}). */
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number, float cx,
                     float cy, float size, int fill) {
        int save = canvas.save();
        place(canvas, cx, cy, size);
        mFill.setColor(fill);
        canvas.drawPath(cut(kind, number), mFill);
        canvas.restoreToCount(save);
    }

    /**
     * A run of {@code count} pages folded into one slot: two plates behind the front glyph, drawn
     * only outside its silhouette so nothing shows through the number, then the glyph with
     * {@code count} as its number. {@code plateInk} is the plates' colour; its alpha scales the
     * plates' own.
     */
    public void drawStack(@NonNull Canvas canvas, @NonNull PageKind kind, int count, float cx,
                          float cy, float size, int fill, int plateInk) {
        int save = canvas.save();
        place(canvas, cx, cy, size);
        // The plates stay out of the folded front's silhouette, so its cut-outs show the ground.
        canvas.clipOutPath(grown(kind));
        int rgb = plateInk & 0x00FFFFFF;
        float gain = Color.alpha(plateInk);
        for (int i = 0; i < PLATES.length; i++) {
            float[] plate = PLATES[i];
            mFill.setColor(rgb | (Math.round(plate[5] * gain) << 24));
            int inner = canvas.save();
            if (i + 1 < PLATES.length) {
                // The plate in front of this one cuts it out, a gap wider than itself.
                float[] front = PLATES[i + 1];
                canvas.translate(front[0], front[1]);
                canvas.clipOutPath(mPlateGrown);
                canvas.translate(plate[0] - front[0], plate[1] - front[1]);
            } else {
                canvas.translate(plate[0], plate[1]);
            }
            canvas.drawPath(mPlate, mFill);
            canvas.restoreToCount(inner);
        }
        canvas.restoreToCount(save);
        save = canvas.save();
        place(canvas, cx, cy, size);
        canvas.translate(STACK_TX, STACK_TY);
        canvas.scale(STACK_SCALE, STACK_SCALE);
        mFill.setColor(fill);
        canvas.drawPath(cut(kind, count), mFill);
        canvas.restoreToCount(save);
    }

    private static void place(Canvas canvas, float cx, float cy, float size) {
        canvas.translate(cx, cy);
        float unit = size / GLYPH_UNITS;
        canvas.scale(unit, unit);
    }

    /** The glyph's cut path in units; the same instance every time for one (kind, number). */
    @NonNull Path cut(@NonNull PageKind kind, int number) {
        int index = number <= 0 || !hasNumber(kind) ? NONE : number > MAX_NUMBER ? 0 : number;
        Path[] row = mCut[kind.ordinal()];
        Path path = row[index];
        if (path == null) {
            path = buildCut(kind, index);
            row[index] = path;
        }
        return path;
    }

    @NonNull Path silhouette(@NonNull PageKind kind) {
        Path path = mSilhouette[kind.ordinal()];
        if (path == null) {
            path = buildSilhouette(kind);
            mSilhouette[kind.ordinal()] = path;
        }
        return path;
    }

    /** {@code kind}'s silhouette as the folded stack's front draws it, in box units. */
    @NonNull Path folded(@NonNull PageKind kind) {
        Path path = mFolded[kind.ordinal()];
        if (path == null) {
            path = new Path();
            mMatrix.setScale(STACK_SCALE, STACK_SCALE);
            mMatrix.postTranslate(STACK_TX, STACK_TY);
            silhouette(kind).transform(mMatrix, path);
            mFolded[kind.ordinal()] = path;
        }
        return path;
    }

    /** The folded silhouette grown by {@link #CARD_GAP}; the same instance every time. */
    @NonNull private Path grown(@NonNull PageKind kind) {
        Path path = mGrown[kind.ordinal()];
        if (path == null) {
            path = grow(folded(kind));
            mGrown[kind.ordinal()] = path;
        }
        return path;
    }

    private static Path grow(Path shape) {
        Path band = new Path();
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2f * CARD_GAP);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.getFillPath(shape, band);
        return union(shape, band);
    }

    private static boolean hasNumber(PageKind kind) {
        return kind == PageKind.HOME || kind == PageKind.TERMINAL;
    }

    private static Path rounded(float[] r) {
        Path p = new Path();
        p.addRoundRect(r[0], r[1], r[0] + r[2], r[1] + r[3], r[4], r[4], Path.Direction.CW);
        return p;
    }

    private static Path union(Path a, Path b) {
        Path out = new Path();
        if (!out.op(a, b, Path.Op.UNION)) {
            out.set(a);
            out.addPath(b);
        }
        return out;
    }

    private Path buildSilhouette(PageKind kind) {
        switch (kind) {
            case HOME:
                return PathParser.createPathFromPathData(HOME_PATH);
            case TERMINAL:
                return rounded(TERMINAL_BODY);
            case DISPLAY: {
                Path p = rounded(DISPLAY_RECTS[0]);
                for (int i = 1; i < DISPLAY_RECTS.length; i++) p = union(p, rounded(DISPLAY_RECTS[i]));
                return p;
            }
            default: {
                Path star = PathParser.createPathFromPathData(SPARK_PATH);
                Path p = null;
                for (float[] s : SPARKS) {
                    Path one = new Path();
                    mMatrix.setScale(s[2], s[2]);
                    mMatrix.postTranslate(s[0], s[1]);
                    star.transform(mMatrix, one);
                    p = p == null ? one : union(p, one);
                }
                return p;
            }
        }
    }

    private Path buildCut(PageKind kind, int index) {
        Path base = silhouette(kind);
        if (kind == PageKind.TERMINAL) {
            Path stroke = new Path();
            Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(CHEVRON_STROKE);
            outline.setStrokeCap(Paint.Cap.ROUND);
            outline.setStrokeJoin(Paint.Join.ROUND);
            outline.getFillPath(PathParser.createPathFromPathData(CHEVRON_PATH), stroke);
            base = difference(base, stroke);
        }
        if (index == NONE) return base;
        boolean home = kind == PageKind.HOME;
        return difference(base, numberPath(index == 0 ? OVERFLOW : Integer.toString(index),
            index == 0, home ? HOME_SLOT_CX : TERMINAL_SLOT_CX,
            home ? HOME_SLOT_W : TERMINAL_SLOT_W, home ? HOME_BASELINE : TERMINAL_BASELINE));
    }

    private static Path difference(Path a, Path b) {
        Path out = new Path();
        if (!out.op(a, b, Path.Op.DIFFERENCE)) out.set(a);
        return out;
    }

    /** The number's outline in units, its advance centred on {@code cx} and fitted to {@code slotW}. */
    private Path numberPath(String text, boolean overflow, float cx, float slotW, float baseline) {
        Path path = new Path();
        mText.getTextPath(text, 0, text.length(), 0f, 0f, path);
        float advance = mText.measureText(text) / REFERENCE_SCALE;
        PageGlyphPainter.fit(advance, slotW, overflow, mFit);
        float sy = mFit[0] / REFERENCE_SCALE;
        mMatrix.setTranslate(-advance * REFERENCE_SCALE / 2f, 0f);
        mMatrix.postScale(sy * mFit[1], sy);
        mMatrix.postTranslate(cx, baseline);
        path.transform(mMatrix);
        return path;
    }

    /** The horizontal scale the number {@code text} gets in a slot {@code slotW} wide. */
    float numberScale(String text, float slotW) {
        PageGlyphPainter.fit(mText.measureText(text) / REFERENCE_SCALE, slotW, text.length() > 2, mFit);
        return mFit[1];
    }
}
