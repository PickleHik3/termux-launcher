package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.Build;

import com.termux.app.page.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageGlyphPainterTest {

    private static final int[] NUMBERS = {1, 2, 7, 9, 10, 12, 88, 99};
    /** A twentieth of a unit, for the digits' overshoot and the outline's float error. */
    private static final float SLACK = 0.05f;
    /** A lone digit's cap height, in units of the glyph's 24: over a quarter of it. */
    private static final float MIN_CAP_UNITS = 6.5f;
    private static final float[] DENSITIES = {2f, 2.625f, 3f, 3.5f};

    /** The distance from ({@code x}, {@code y}) to the rect, 0 inside it. */
    private static float distance(float x, float y, RectF r) {
        float dx = Math.max(Math.max(r.left - x, 0f), x - r.right);
        float dy = Math.max(Math.max(r.top - y, 0f), y - r.bottom);
        return (float) Math.hypot(dx, dy);
    }

    /**
     * The digits as drawn, measured from their outlines at the glyph's real pixel size, keep
     * {@link PageGlyphSlots#NUMBER_AIR} units of air from every stroke's edge, in all four glyphs
     * at the bar's resting and raised sizes, and stand one cap height in every kind.
     */
    @Test public void theNumberKeepsItsAirFromEveryStrokeAtRestAndRaised() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] layout = new float[3];
        float[] pos = new float[2];
        RectF ink = new RectF();
        Path glyph = new Path();
        Paint paint = new Paint(painter.textPaint());
        float need = PageGlyphSlots.STROKE / 2f + PageGlyphSlots.NUMBER_AIR;
        for (float density : DENSITIES) {
            for (float scale : new float[] {1f, PageBarLayout.RAISED_SCALE}) {
                float size = PageBarLayout.GLYPH_DP * density * scale;
                float unit = size / PageGlyphSlots.VIEWPORT;
                for (int n : NUMBERS) {
                    String text = Integer.toString(n);
                    float textSize = -1f;
                    for (PageKind kind : PageKind.values()) {
                        painter.placeNumber(kind, n, layout);
                        paint.setTextSize(layout[2] * unit);
                        paint.getTextPath(text, 0, text.length(), layout[0] * unit,
                            layout[1] * unit, glyph);
                        glyph.computeBounds(ink, false);
                        // Back to units, where the outline lives.
                        ink.set(ink.left / unit, ink.top / unit, ink.right / unit, ink.bottom / unit);
                        if (textSize < 0f) textSize = layout[2];
                        assertEquals(kind + " " + n, textSize, layout[2], 1e-4f);
                        if (n < 10) {
                            assertTrue(kind + " " + n + " cap height " + ink.height(),
                                ink.height() >= MIN_CAP_UNITS - SLACK);
                        }
                        PathMeasure measure = new PathMeasure(painter.outline(kind), false);
                        do {
                            float length = measure.getLength();
                            for (float d = 0f; d <= length; d += 0.05f) {
                                measure.getPosTan(d, pos, null);
                                float gap = distance(pos[0], pos[1], ink);
                                assertTrue(kind + " " + n + " at " + size + "px: ink " + ink
                                    + " is " + (gap - PageGlyphSlots.STROKE / 2f)
                                    + " from the stroke at " + pos[0] + "," + pos[1],
                                    gap >= need - SLACK);
                            }
                        } while (measure.nextContour());
                    }
                }
            }
        }
    }

    @Test public void aLoneDigitStandsLargerThanTwoAndEachCountOneSize() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] one = new float[3];
        float[] two = new float[3];
        painter.placeNumber(PageKind.HOME, 1, one);
        painter.placeNumber(PageKind.TERMINAL, 12, two);
        assertTrue(one[2] + " vs " + two[2], one[2] > two[2] * 1.2f);
        for (int n = 1; n < 10; n++) {
            painter.placeNumber(PageKind.TERMINAL, n, two);
            assertEquals(one[2], two[2], 1e-4f);
        }
        painter.placeNumber(PageKind.HOME, 10, one);
        for (int n = 10; n < 100; n++) {
            painter.placeNumber(PageKind.TERMINAL, n, two);
            assertEquals(one[2], two[2], 1e-4f);
        }
    }

    @Test public void displayAndAssistantShowNoNumber() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] layout = new float[3];
        assertFalse(painter.numberLayout(PageKind.DISPLAY, 2, layout));
        assertFalse(painter.numberLayout(PageKind.ASSISTANT, 2, layout));
        assertFalse(painter.numberLayout(PageKind.HOME, 0, layout));
    }
}
