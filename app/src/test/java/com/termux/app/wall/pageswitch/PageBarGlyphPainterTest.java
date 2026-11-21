package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Path;
import android.graphics.RectF;

import androidx.core.graphics.PathParser;

import com.termux.app.page.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PageBarGlyphPainterTest {

    @Test public void thePathStringsParse() {
        assertNotNull(PathParser.createPathFromPathData(PageBarGlyphPainter.HOME_PATH));
        assertNotNull(PathParser.createPathFromPathData(PageBarGlyphPainter.CHEVRON_PATH));
        assertNotNull(PathParser.createPathFromPathData(PageBarGlyphPainter.SPARK_PATH));
    }

    @Test public void aCutPathIsBuiltOnceAndReused() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        for (PageKind kind : PageKind.values()) {
            assertSame(painter.cut(kind, 12), painter.cut(kind, 12));
            assertSame(painter.silhouette(kind), painter.silhouette(kind));
        }
    }

    @Test public void numbersAboveNinetyNineShareOnePath() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        assertSame(painter.cut(PageKind.HOME, 100), painter.cut(PageKind.HOME, 250));
        assertFalse(painter.cut(PageKind.HOME, 100) == painter.cut(PageKind.HOME, 99));
    }

    private static final float BOX = PageBarGlyphPainter.GLYPH_UNITS / 2f;

    private static RectF bounds(Path path) {
        RectF r = new RectF();
        path.computeBounds(r, true);
        return r;
    }

    @Test public void everyGlyphLiesInsideTheCommonBox() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        for (PageKind kind : PageKind.values()) {
            RectF r = bounds(painter.silhouette(kind));
            assertTrue(kind + " " + r, r.left >= -BOX && r.top >= -BOX && r.right <= BOX && r.bottom <= BOX);
        }
    }

    @Test public void homeAndTerminalNumbersShareOneSizeAndBaseline() {
        // One font size and baseline in both slots, so the count reads the same beside either glyph.
        assertTrue(PageBarGlyphPainter.HOME_BASELINE == PageBarGlyphPainter.TERMINAL_BASELINE);
        assertTrue(PageBarGlyphPainter.NUMBER_SIZE == 9.5f);
    }

    @Test public void twoDigitsFitBothSlotsWithoutCondensingBelowTheFloor() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        for (int n = 10; n <= 99; n++) {
            String text = Integer.toString(n);
            assertTrue(text, painter.numberScale(text, PageBarGlyphPainter.HOME_SLOT_W) >= PageGlyphSlots.MIN_CONDENSE);
            assertTrue(text, painter.numberScale(text, PageBarGlyphPainter.TERMINAL_SLOT_W) >= PageGlyphSlots.MIN_CONDENSE);
        }
    }

    @Test public void theFoldedStackLiesInsideTheBox() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        for (PageKind kind : PageKind.values()) {
            RectF r = bounds(painter.folded(kind));
            assertTrue(kind + " " + r, r.left >= -BOX && r.top >= -BOX && r.right <= BOX && r.bottom <= BOX);
        }
        for (float[] plate : PageBarGlyphPainter.PLATES) {
            assertTrue(plate[0] >= -BOX && plate[1] >= -BOX
                && plate[0] + plate[2] <= BOX && plate[1] + plate[3] <= BOX);
        }
        // Nothing reaches past the box, so a folded last slot needs no extra end padding.
        assertTrue(PageBarGlyphPainter.STACK_OVERHANG_RIGHT == 0f);
        assertTrue(PageBarGlyphPainter.STACK_OVERHANG_LEFT == 0f);
    }
}
