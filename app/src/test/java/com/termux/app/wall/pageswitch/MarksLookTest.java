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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

    @Test public void homeIsADotAndItsCurrentPageIsTheDotInsideTheRing() {
        Bitmap dot = render(c -> look.draw(c, PageKind.HOME, 0, false, 0f, 24f, 24f, BOX, INK));
        Bitmap ring = render(c -> look.draw(c, PageKind.HOME, 0, true, 0f, 24f, 24f, BOX, INK));
        assertTrue(inkPixels(dot) < inkPixels(ring));
        assertTrue("the dot is at the centre", alphaAt(dot, 24, 24) > 200);
        assertTrue("and stays with the ring", alphaAt(ring, 24, 24) > 200);
    }

    @Test public void aWindowsNumberIsCutOutOfItsSquare() {
        Bitmap one = render(c -> look.draw(c, PageKind.TERMINAL, 1, false, 0f, 24f, 24f, BOX, INK));
        Bitmap bare = render(c -> look.draw(c, PageKind.TERMINAL, 0, false, 0f, 24f, 24f, BOX, INK));
        int clearInside = 0;
        for (int y = 18; y <= 30; y++) {
            for (int x = 18; x <= 30; x++) if (alphaAt(one, x, y) == 0) clearInside++;
        }
        assertTrue("the 1 is cut out", clearInside > 6);
        assertTrue("the square's rim is ink", alphaAt(one, 24 + 17, 24) > 200);
        assertFalse("a bare Terminal cuts its prompt, not a number", one.sameAs(bare));
    }

    @Test public void aCutNumberIsCentredOnItsInk() {
        Bitmap two = render(c -> look.draw(c, PageKind.TERMINAL, 2, false, 0f, 24f, 24f, BOX, INK));
        int minX = SIZE, maxX = -1, minY = SIZE, maxY = -1;
        for (int y = 16; y <= 32; y++) {
            for (int x = 16; x <= 32; x++) {
                if (alphaAt(two, x, y) < 128) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }
        assertEquals(24f, (minX + maxX + 1) / 2f, 1.5f);
        assertEquals(24f, (minY + maxY + 1) / 2f, 1.5f);
    }

    @Test public void theRingOnlyAddsInkToTheSpark() {
        Bitmap rest = render(c -> look.draw(c, PageKind.ASSISTANT, 0, false, 0f, 24f, 24f, BOX, INK));
        Bitmap ring = render(c -> look.draw(c, PageKind.ASSISTANT, 0, true, 0f, 24f, 24f, BOX, INK));
        assertTrue(inkPixels(ring) > inkPixels(rest));
        assertTrue("the spark is filled", alphaAt(rest, 24, 24) > 200);
    }

    @Test public void aScreenKeepsItsStandBelowTheFrame() {
        Bitmap two = render(c -> look.draw(c, PageKind.DISPLAY, 2, false, 0f, 24f, 24f, BOX, INK));
        assertTrue(alphaAt(two, 24, 24 + 15) > 200);
    }

    @Test public void overviewIsTwoCardsWithAGapBetween() {
        Bitmap cards = render(c -> look.drawOverview(c, 24f, 24f, BOX, INK));
        assertEquals("the gap between the cards", 0, alphaAt(cards, 24, 24));
        assertTrue(alphaAt(cards, 24 - 8, 24) > 200);
        assertTrue(alphaAt(cards, 24 + 8, 24) > 200);
    }

    @Test public void anIconKeepsItsBoundsAndTheRingFollowsCurrent() {
        ColorDrawable icon = new ColorDrawable(Color.RED);
        icon.setBounds(1, 2, 3, 4);
        Bitmap rest = render(c -> look.drawIcon(c, icon, false, 24f, 24f, BOX, INK));
        Bitmap ring = render(c -> look.drawIcon(c, icon, true, 24f, 24f, BOX, INK));
        assertEquals(new Rect(1, 2, 3, 4), icon.getBounds());
        assertEquals(Color.RED, rest.getPixel(24, 24));
        assertTrue(inkPixels(ring) > inkPixels(rest));
    }

    @Test public void aGlyphIsDrawnInInkAndGetsTheRingWhenCurrent() {
        Bitmap plain = render(c -> look.drawGlyph(c, "A", Typeface.DEFAULT, false, 24f, 24f, BOX, INK));
        Bitmap ring = render(c -> look.drawGlyph(c, "A", Typeface.DEFAULT, true, 24f, 24f, BOX, INK));
        assertTrue(inkPixels(plain) > 4);
        assertTrue(inkPixels(ring) > inkPixels(plain));
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

    @Test public void aFoldedStackStaysInsideItsBoxForEveryKind() {
        int ground = Color.argb(160, 255, 255, 255);
        for (PageKind kind : PageKind.values()) {
            Bitmap stack = render(c ->
                look.drawStack(c, kind, 3, false, 0f, 24f, 24f, BOX, INK, ground));
            int max = -1;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (alphaAt(stack, x, y) > 0) max = Math.max(max, Math.max(x - 24, 24 - y));
                }
            }
            assertTrue(kind + " reaches " + max, max <= BOX / 2f);
        }
    }

    @Test public void aCurrentStackWearsTheRingAroundItsFront() {
        int ground = Color.argb(160, 255, 255, 255);
        Bitmap rest = render(c ->
            look.drawStack(c, PageKind.TERMINAL, 3, false, 0f, 24f, 24f, BOX, INK, ground));
        Bitmap ring = render(c ->
            look.drawStack(c, PageKind.TERMINAL, 3, true, 0f, 24f, 24f, BOX, INK, ground));
        assertTrue(inkPixels(ring) > inkPixels(rest));
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
