package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.page.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Pins what today's look (the filled glyphs) answers, through the look interface. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageMarkLookTest {

    private static final int SIZE = 48;
    private static final int INK = Color.WHITE;

    private interface Paint {
        void on(Canvas canvas);
    }

    private static Bitmap render(Paint paint) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        paint.on(new Canvas(bitmap));
        return bitmap;
    }

    private static boolean drawsInk(Bitmap bitmap) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) if (Color.alpha(bitmap.getPixel(x, y)) != 0) return true;
        }
        return false;
    }

    @Test public void everyKindAtRestIsTheFilledGlyph() {
        PageMarkLook look = PageMarkLooks.chosen(null);
        PageBarGlyphPainter legacy = new PageBarGlyphPainter();
        for (PageKind kind : PageKind.values()) {
            Bitmap viaLook = render(c -> look.draw(c, kind, 2, false, 0f, 24f, 24f, 40f, INK));
            Bitmap direct = render(c -> legacy.draw(c, kind, 2, 24f, 24f, 40f, INK));
            assertTrue(kind + " draws", drawsInk(viaLook));
            assertTrue(kind + " at rest", viaLook.sameAs(direct));
        }
    }

    @Test public void currentAndLiftChangeNothingButTheInkTheViewGives() {
        PageMarkLook look = PageMarkLooks.chosen(null);
        for (PageKind kind : PageKind.values()) {
            Bitmap rest = render(c -> look.draw(c, kind, 1, false, 0f, 24f, 24f, 40f, INK));
            Bitmap current = render(c -> look.draw(c, kind, 1, true, 0f, 24f, 24f, 40f, INK));
            Bitmap lifted = render(c -> look.draw(c, kind, 1, true, 1f, 24f, 24f, 40f, INK));
            assertTrue(kind + " current", rest.sameAs(current));
            assertTrue(kind + " lift", rest.sameAs(lifted));
        }
    }

    @Test public void aFoldedStackIsTheFilledStack() {
        PageMarkLook look = PageMarkLooks.chosen(null);
        PageBarGlyphPainter legacy = new PageBarGlyphPainter();
        int plate = Color.argb(160, 255, 255, 255);
        for (PageKind kind : PageKind.values()) {
            Bitmap viaLook = render(c ->
                look.drawStack(c, kind, 3, true, 0f, 24f, 24f, 40f, INK, plate));
            Bitmap direct = render(c -> legacy.drawStack(c, kind, 3, 24f, 24f, 40f, INK, plate));
            assertTrue(kind + " stack", viaLook.sameAs(direct));
        }
    }
}
