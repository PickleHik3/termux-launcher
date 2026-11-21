package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

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
}
