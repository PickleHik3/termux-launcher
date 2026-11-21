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

    static final String HOME_PATH = "M-1.4 -9.6 Q0 -10.8 1.4 -9.6 L9.6 -2.6 Q10.5 -1.8 10.5 -0.6"
        + " V8 Q10.5 10.5 8 10.5 H-8 Q-10.5 10.5 -10.5 8 V-0.6 Q-10.5 -1.8 -9.6 -2.6 Z";
    /** The Terminal body, and the shape of a folded stack's plates: x, y, w, h, radius. */
    static final float[] TERMINAL_BODY = {-11f, -9.5f, 22f, 19f, 5f};
    static final String CHEVRON_PATH = "M-6.5 -3.5 L-3 0 L-6.5 3.5";
    static final float CHEVRON_STROKE = 2f;
    /** The Display's three rects: x, y, w, h, radius. */
    static final float[][] DISPLAY_RECTS = {
        {-12f, -10f, 24f, 15.5f, 4f}, {-1.25f, 5f, 2.5f, 4f, 0f}, {-6f, 8f, 12f, 2.5f, 1.25f}};
    static final String SPARK_PATH = "M0 -10 C1.2 -3.5 3.5 -1.2 10 0 C3.5 1.2 1.2 3.5 0 10"
        + " C-1.2 3.5 -3.5 1.2 -10 0 C-3.5 -1.2 -1.2 -3.5 0 -10 Z";
    /** The Assistant's two sparks: translate x, translate y, scale. */
    static final float[][] SPARKS = {{-2f, 2f, 0.85f}, {7.5f, -7.5f, 0.36f}};

    /** The back and front plate of a folded stack: the plate's x and y, and its alpha. */
    static final float[][] PLATES = {{-5f, -13.5f, 0.5f}, {-8f, -11.5f, 0.8f}};

    /** Ground left showing between neighbouring cards of a stack, in units. */
    static final float CARD_GAP = 1.5f;

    /** How far a folded stack's plates reach past the glyph box's right edge, in units. */
    static final float STACK_OVERHANG_RIGHT = overhangRight();
    /** The same past the left edge; the plates sit inside it, so 0. */
    static final float STACK_OVERHANG_LEFT = 0f;

    private static float overhangRight() {
        float right = 0f;
        for (float[] plate : PLATES) right = Math.max(right, plate[0] + TERMINAL_BODY[2]);
        return Math.max(0f, right - GLYPH_UNITS / 2f);
    }

    /** The number's font size in units and its weight; per kind its slot centre x, width, baseline. */
    static final float NUMBER_SIZE = 10.5f;
    static final int NUMBER_WEIGHT = 600;
    static final float HOME_SLOT_CX = 0f;
    static final float HOME_SLOT_W = 15f;
    static final float HOME_BASELINE = 7.3f;
    static final float TERMINAL_SLOT_CX = 4.5f;
    static final float TERMINAL_SLOT_W = 10f;
    static final float TERMINAL_BASELINE = 3.9f;

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
        mPlate.addRoundRect(0f, 0f, TERMINAL_BODY[2], TERMINAL_BODY[3], TERMINAL_BODY[4],
            TERMINAL_BODY[4], Path.Direction.CW);
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
        canvas.clipOutPath(grown(kind));
        int rgb = plateInk & 0x00FFFFFF;
        float gain = Color.alpha(plateInk);
        for (int i = 0; i < PLATES.length; i++) {
            float[] plate = PLATES[i];
            mFill.setColor(rgb | (Math.round(plate[2] * gain) << 24));
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
        draw(canvas, kind, count, cx, cy, size, fill);
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

    /** {@code kind}'s silhouette grown by {@link #CARD_GAP}; the same instance every time. */
    @NonNull private Path grown(@NonNull PageKind kind) {
        Path path = mGrown[kind.ordinal()];
        if (path == null) {
            path = grow(silhouette(kind));
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

    /** The number's outline in units, its ink centred on {@code cx} and fitted to {@code slotW}. */
    private Path numberPath(String text, boolean overflow, float cx, float slotW, float baseline) {
        Path path = new Path();
        mText.getTextPath(text, 0, text.length(), 0f, 0f, path);
        path.computeBounds(mBounds, true);
        PageGlyphPainter.fit(mBounds.width() / REFERENCE_SCALE, slotW, overflow, mFit);
        float sy = mFit[0] / REFERENCE_SCALE;
        mMatrix.setTranslate(-mBounds.centerX(), 0f);
        mMatrix.postScale(sy * mFit[1], sy);
        mMatrix.postTranslate(cx, baseline);
        path.transform(mMatrix);
        return path;
    }
}
