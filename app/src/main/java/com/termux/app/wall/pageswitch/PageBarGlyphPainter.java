package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * Draws the page bar's glyphs, centred on a point, {@code size} the side of the glyph's 24-unit
 * viewport: the Roundline outlines and numbers of {@link PageGlyphPainter}, the current page's
 * with its wash. A folded stack (several windows of one kind) draws its front glyph smaller,
 * moved down and left, and behind it a card: the glyph's silhouette stroked, moved up and right,
 * kept {@link #CLEAR} units clear of the front's stroke and inside so no stroke crosses another.
 *
 * <p>The stack's clear region is built once per kind, on its first draw, and kept; drawing then
 * only moves the canvas, so it allocates nothing after a kind's first stack.
 */
public final class PageBarGlyphPainter {

    /** A folded stack's front glyph is scaled by this about the centre, then moved by the translation. */
    static final float STACK_SCALE = 0.85f;
    static final float STACK_TX = -2f;
    static final float STACK_TY = 2f;
    /** The back card: the front moved by this, in units. */
    static final float BACK_DX = 4f;
    static final float BACK_DY = -4f;
    /** The back card's ink, as a share of the plate ink's alpha. */
    static final float BACK_ALPHA = 0.6f;
    /** Ground left between the front glyph's stroke and the back card, in units. */
    static final float CLEAR = 2f;

    private static final float CENTRE = PageGlyphSlots.VIEWPORT / 2f;

    private final PageGlyphPainter mGlyphs = new PageGlyphPainter();
    private final Path[] mClear = new Path[PageKind.values().length];

    /** {@code kind}'s glyph in {@code ink}, as the current page's when {@code current}, centred on ({@code cx}, {@code cy}). */
    public void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number, float cx,
                     float cy, float size, int ink, boolean current) {
        int save = canvas.save();
        place(canvas, cx, cy, size);
        mGlyphs.drawUnits(canvas, kind, number, ink, current, PageGlyphSlots.STROKE);
        canvas.restoreToCount(save);
    }

    /**
     * A run of {@code count} pages folded into one slot: the back card in {@code plateInk}
     * (its alpha scaled by {@link #BACK_ALPHA}), drawn only outside the front glyph's clear region,
     * then the front glyph with {@code count} as its number.
     */
    public void drawStack(@NonNull Canvas canvas, @NonNull PageKind kind, int count, float cx,
                          float cy, float size, int ink, boolean current, int plateInk) {
        float stroke = PageGlyphSlots.STROKE / STACK_SCALE;
        int save = canvas.save();
        place(canvas, cx, cy, size);
        int back = canvas.save();
        canvas.clipOutPath(clear(kind));
        canvas.translate(BACK_DX, BACK_DY);
        front(canvas);
        mGlyphs.drawSilhouette(canvas, kind, PageGlyphPainter.scaleAlpha(plateInk, BACK_ALPHA),
            stroke);
        canvas.restoreToCount(back);
        front(canvas);
        mGlyphs.drawUnits(canvas, kind, count, ink, current, stroke);
        canvas.restoreToCount(save);
    }

    private static void place(Canvas canvas, float cx, float cy, float size) {
        canvas.translate(cx - size / 2f, cy - size / 2f);
        float unit = size / PageGlyphSlots.VIEWPORT;
        canvas.scale(unit, unit);
    }

    /** Moves the canvas from the glyph's box to the folded front's. */
    private static void front(Canvas canvas) {
        canvas.translate(CENTRE + STACK_TX, CENTRE + STACK_TY);
        canvas.scale(STACK_SCALE, STACK_SCALE);
        canvas.translate(-CENTRE, -CENTRE);
    }

    /** The folded front's matrix, from its own units to the glyph box's. */
    static void frontMatrix(@NonNull Matrix out) {
        out.setTranslate(-CENTRE, -CENTRE);
        out.postScale(STACK_SCALE, STACK_SCALE);
        out.postTranslate(CENTRE + STACK_TX, CENTRE + STACK_TY);
    }

    /**
     * Where the back card may not draw, in the glyph box's units: the folded front's interior,
     * stroke and marks, grown by {@link #CLEAR}. The same instance every time for one kind.
     */
    @NonNull Path clear(@NonNull PageKind kind) {
        Path path = mClear[kind.ordinal()];
        if (path == null) {
            path = buildClear(kind);
            mClear[kind.ordinal()] = path;
        }
        return path;
    }

    private Path buildClear(PageKind kind) {
        Matrix matrix = new Matrix();
        frontMatrix(matrix);
        Paint grow = new Paint(Paint.ANTI_ALIAS_FLAG);
        grow.setStyle(Paint.Style.STROKE);
        grow.setStrokeCap(Paint.Cap.ROUND);
        grow.setStrokeJoin(Paint.Join.ROUND);
        Path out = new Path();
        Path moved = new Path();
        Path band = new Path();
        mGlyphs.interior(kind).transform(matrix, moved);
        out.set(moved);
        // The outline's stroke is STROKE wide on screen; its band reaches CLEAR past either side.
        grow.setStrokeWidth(PageGlyphSlots.STROKE + 2f * CLEAR);
        mGlyphs.outline(kind).transform(matrix, moved);
        grow.getFillPath(moved, band);
        union(out, band);
        Path dot = mGlyphs.dot(kind);
        if (dot != null) {
            dot.transform(matrix, moved);
            union(out, moved);
            grow.setStrokeWidth(2f * CLEAR);
            grow.getFillPath(moved, band);
            union(out, band);
        }
        return out;
    }

    private static void union(Path into, Path add) {
        if (!into.op(add, Path.Op.UNION)) into.addPath(add);
    }

    /** The glyphs' own painter, for tests. */
    @NonNull PageGlyphPainter glyphs() {
        return mGlyphs;
    }
}
