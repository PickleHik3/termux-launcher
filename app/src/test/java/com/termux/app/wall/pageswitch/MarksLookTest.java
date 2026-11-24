package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MarksLookTest {

    private static final int SIZE = 48;
    private static final float BOX = 40f;
    private static final int INK = Color.WHITE;

    private interface Paint {
        void on(Canvas canvas);
    }

    private static Bitmap render(Paint paint) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        paint.on(new Canvas(bitmap));
        return bitmap;
    }

    private static int alphaAt(Bitmap bitmap, int x, int y) {
        return Color.alpha(bitmap.getPixel(x, y));
    }

    private static int inkPixels(Bitmap bitmap) {
        int n = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) if (alphaAt(bitmap, x, y) > 128) n++;
        }
        return n;
    }

    private final MarksLook look = new MarksLook();

    @Test public void everyKindAtRestDrawsInkAndLeavesTheBoxsCornersBare() {
        for (PageKind kind : PageKind.values()) {
            Bitmap rest = render(c -> look.draw(c, kind, 1, false, 0f, 24f, 24f, BOX, INK));
            assertTrue(kind + " draws", inkPixels(rest) > 4);
            assertEquals(kind + " bottom-left corner", 0, alphaAt(rest, 5, 42));
        }
    }

    @Test public void aLinuxWindowWearsTheScreenMarkNotTheSpark() {
        for (boolean current : new boolean[] {false, true}) {
            for (int number : new int[] {0, 2}) {
                Bitmap linux = render(c -> look.draw(c, PageKind.LINUX, number, current, 0f, 24f, 24f, BOX, INK));
                Bitmap screen = render(c -> look.draw(c, PageKind.DISPLAY, number, current, 0f, 24f, 24f, BOX, INK));
                Bitmap spark = render(c -> look.draw(c, PageKind.ASSISTANT, number, current, 0f, 24f, 24f, BOX, INK));
                assertTrue("current=" + current + " number=" + number, linux.sameAs(screen));
                if (number == 0) assertFalse(linux.sameAs(spark));
            }
        }
    }

    @Test public void homeIsADotSmallerThanTheCurrentDisc() {
        Bitmap dot = render(c -> look.draw(c, PageKind.HOME, 1, false, 0f, 24f, 24f, BOX, INK));
        Bitmap disc = render(c -> look.draw(c, PageKind.HOME, 1, true, 0f, 24f, 24f, BOX, INK));
        assertTrue(inkPixels(dot) < inkPixels(disc));
        assertTrue("the dot is at the centre", alphaAt(dot, 24, 24) > 200);
    }

    @Test public void theCurrentDiscHasItsSignCutOutSoTheGroundShowsThrough() {
        for (PageKind kind : new PageKind[] {PageKind.ASSISTANT, PageKind.DISPLAY, PageKind.TERMINAL}) {
            Bitmap disc = render(c -> look.draw(c, kind, 0, true, 0f, 24f, 24f, BOX, INK));
            double full = Math.PI * PageMarkShapes.discRadius(BOX) * PageMarkShapes.discRadius(BOX);
            assertTrue(kind + " is a disc with a hole", inkPixels(disc) < full * 0.97);
            assertTrue(kind + " has ink in its ring", alphaAt(disc, 24 + 15, 24) > 200);
        }
        // The spark's centre is cut out: clear, not a colour.
        Bitmap spark = render(c -> look.draw(c, PageKind.ASSISTANT, 0, true, 0f, 24f, 24f, BOX, INK));
        assertEquals(0, alphaAt(spark, 24, 24));
    }

    @Test public void aCutOutNumberLeavesClearPixelsInsideTheDisc() {
        Bitmap one = render(c -> look.draw(c, PageKind.HOME, 1, true, 0f, 24f, 24f, BOX, INK));
        int clearInside = 0;
        for (int y = 16; y <= 32; y++) {
            for (int x = 16; x <= 32; x++) {
                if (alphaAt(one, x, y) == 0) clearInside++;
            }
        }
        assertTrue("the 1 is cut out of the disc", clearInside > 6);
        assertTrue(alphaAt(one, 24 + 14, 24) > 200);
    }

    @Test public void theNumberDecidesWhatIsCut() {
        Bitmap two = render(c -> look.draw(c, PageKind.TERMINAL, 2, true, 0f, 24f, 24f, BOX, INK));
        Bitmap sign = render(c -> look.draw(c, PageKind.TERMINAL, 0, true, 0f, 24f, 24f, BOX, INK));
        assertFalse(two.sameAs(sign));
    }

    @Test public void theBurstIsDrawnAtTheTopRight() {
        Bitmap burst = render(c -> look.drawBurst(c, 24f, 24f, BOX, Color.RED));
        assertTrue(alphaAt(burst, 24 + 13, 24 - 13) > 200);
        assertEquals(0, alphaAt(burst, 24 - 13, 24 + 13));
        assertEquals(Color.RED, burst.getPixel(24 + 13, 24 - 13));
    }

    @Test public void aFoldedStackDrawsTheFrontAndABackCard() {
        Bitmap single = render(c -> look.draw(c, PageKind.TERMINAL, 0, false, 0f, 24f, 24f, BOX, INK));
        Bitmap stack = render(c ->
            look.drawStack(c, PageKind.TERMINAL, 3, false, 0f, 24f, 24f, BOX, INK,
                Color.argb(160, 255, 255, 255)));
        assertFalse(single.sameAs(stack));
        assertTrue(inkPixels(stack) > 0);
    }

    @Test public void theSettingPicksTheLookAndKeepsItWhileItHolds() {
        PageMarkLook glyphs = PageMarkLooks.chosen(PageBarLook.GLYPHS, null);
        assertTrue(glyphs instanceof PageBarGlyphPainter);
        assertSame(glyphs, PageMarkLooks.chosen(PageBarLook.GLYPHS, glyphs));
        PageMarkLook marks = PageMarkLooks.chosen(PageBarLook.MARKS, glyphs);
        assertTrue(marks instanceof MarksLook);
        assertSame(marks, PageMarkLooks.chosen(PageBarLook.MARKS, marks));
        assertNotSame(glyphs, marks);
        assertTrue(PageMarkLooks.chosen(null) instanceof PageBarGlyphPainter);
    }
}
