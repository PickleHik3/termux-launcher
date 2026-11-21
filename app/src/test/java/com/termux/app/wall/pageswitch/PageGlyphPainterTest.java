package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Paint;
import android.graphics.Path;
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

    private static final int[] NUMBERS = {1, 2, 12};
    /** A quarter pixel, for the outline's float error. */
    private static final float SLACK = 0.25f;
    private static final float[] DENSITIES = {2f, 2.625f, 3f, 3.5f};

    /**
     * The digits as drawn, measured at the glyph's real pixel size, stay inside the number box at
     * the bar's resting and raised sizes (measured from the digits' outlines).
     */
    @Test public void theNumberStaysInsideItsBoxAtRestAndRaised() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] layout = new float[3];
        RectF ink = new RectF();
        Path glyph = new Path();
        Paint paint = new Paint(painter.textPaint());
        for (float density : DENSITIES) {
            for (float scale : new float[] {1f, PageBarLayout.RAISED_SCALE}) {
                float size = PageBarLayout.GLYPH_DP * density * scale;
                float unit = size / PageGlyphSlots.VIEWPORT;
                for (PageKind kind : new PageKind[] {PageKind.HOME, PageKind.TERMINAL}) {
                    float[] box = PageGlyphSlots.box(kind);
                    for (int n : NUMBERS) {
                        assertTrue(painter.numberLayout(kind, n, layout));
                        String text = Integer.toString(n);
                        paint.setTextSize(layout[2] * unit);
                        paint.getTextPath(text, 0, text.length(), layout[0] * unit,
                            layout[1] * unit, glyph);
                        glyph.computeBounds(ink, false);
                        float left = ink.left;
                        float right = ink.right;
                        float top = ink.top;
                        float bottom = ink.bottom;
                        String at = kind + " " + n + " at " + size + "px: " + left + "," + top
                            + "-" + right + "," + bottom;
                        assertTrue(at, left >= box[0] * unit - SLACK && right <= box[2] * unit + SLACK);
                        assertTrue(at, top >= box[1] * unit - SLACK && bottom <= box[3] * unit + SLACK);
                    }
                }
            }
        }
    }

    @Test public void aSingleDigitStandsTallerThanTwo() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] one = new float[3];
        float[] twelve = new float[3];
        for (PageKind kind : new PageKind[] {PageKind.HOME, PageKind.TERMINAL}) {
            painter.numberLayout(kind, 1, one);
            painter.numberLayout(kind, 12, twelve);
            assertTrue(kind.name(), one[2] > twelve[2]);
        }
    }

    @Test public void displayAndAssistantShowNoNumber() {
        PageGlyphPainter painter = new PageGlyphPainter();
        float[] layout = new float[3];
        assertFalse(painter.numberLayout(PageKind.DISPLAY, 2, layout));
        assertFalse(painter.numberLayout(PageKind.ASSISTANT, 2, layout));
        assertFalse(painter.numberLayout(PageKind.HOME, 0, layout));
    }

    @Test public void theActiveWashIsTheInkAtItsShare() {
        int wash = PageGlyphPainter.scaleAlpha(0xFF336699, PageGlyphPainter.ACTIVE_FILL_ALPHA);
        assertTrue((wash & 0x00FFFFFF) == 0x336699);
        assertTrue((wash >>> 24) == Math.round(255 * PageGlyphPainter.ACTIVE_FILL_ALPHA));
    }
}
