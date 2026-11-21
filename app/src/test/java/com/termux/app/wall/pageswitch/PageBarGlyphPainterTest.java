package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Matrix;
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
public class PageBarGlyphPainterTest {

    private static final float V = PageGlyphSlots.VIEWPORT;

    /** {@code path}'s ink bounds with its stroke, moved by {@code matrix}. */
    private static RectF ink(Path path, Matrix matrix) {
        RectF r = new RectF();
        path.computeBounds(r, true);
        float half = PageGlyphSlots.STROKE / 2f;
        r.inset(-half, -half);
        matrix.mapRect(r);
        return r;
    }

    private static boolean inside(RectF r) {
        return r.left >= 0f && r.top >= 0f && r.right <= V && r.bottom <= V;
    }

    @Test public void everyGlyphLiesInsideTheViewport() {
        PageGlyphPainter glyphs = new PageBarGlyphPainter().glyphs();
        Matrix identity = new Matrix();
        for (PageKind kind : PageKind.values()) {
            RectF r = ink(glyphs.outline(kind), identity);
            assertTrue(kind + " " + r, inside(r));
        }
    }

    @Test public void theFoldedStackLiesInsideTheViewport() {
        // Nothing reaches past the glyph's box, so a folded last slot needs no extra end padding.
        PageGlyphPainter glyphs = new PageBarGlyphPainter().glyphs();
        Matrix front = new Matrix();
        PageBarGlyphPainter.frontMatrix(front);
        Matrix back = new Matrix(front);
        back.postTranslate(PageBarGlyphPainter.BACK_DX, PageBarGlyphPainter.BACK_DY);
        for (PageKind kind : PageKind.values()) {
            RectF f = ink(glyphs.outline(kind), front);
            RectF b = ink(glyphs.outline(kind), back);
            assertTrue(kind + " front " + f, inside(f));
            assertTrue(kind + " back " + b, inside(b));
        }
    }

    @Test public void theBackOutlineShowsPastTheClearGround() {
        // Moved far enough that a whole stroke of it stands past the front's stroke and the clear
        // ground beside it, so the stack reads as two outlines.
        float reach = Math.min(Math.abs(PageBarGlyphPainter.BACK_DX),
            Math.abs(PageBarGlyphPainter.BACK_DY));
        assertTrue(reach >= PageGlyphSlots.STROKE + PageBarGlyphPainter.CLEAR);
    }

    @Test public void theClearRegionIsBuiltOnceAndCoversTheFront() {
        PageBarGlyphPainter painter = new PageBarGlyphPainter();
        Matrix front = new Matrix();
        PageBarGlyphPainter.frontMatrix(front);
        for (PageKind kind : PageKind.values()) {
            Path clear = painter.clear(kind);
            assertSame(clear, painter.clear(kind));
            RectF c = new RectF();
            clear.computeBounds(c, true);
            RectF f = ink(painter.glyphs().outline(kind), front);
            f.inset(-PageBarGlyphPainter.CLEAR + 0.01f, -PageBarGlyphPainter.CLEAR + 0.01f);
            assertTrue(kind + " " + c + " " + f, c.contains(f));
        }
    }
}
