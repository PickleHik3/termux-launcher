package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;

import org.junit.Test;

public class PageMarkShapesTest {

    private static final float DELTA = 1e-4f;

    @Test public void shapesScaleWithTheBox() {
        assertEquals(PageMarkShapes.DOT_R, PageMarkShapes.dotRadius(24f), DELTA);
        assertEquals(2f * PageMarkShapes.dotRadius(24f), PageMarkShapes.dotRadius(48f), DELTA);
        assertEquals(2f * PageMarkShapes.terminalCorner(16f), PageMarkShapes.terminalCorner(32f), DELTA);
        assertEquals(PageMarkShapes.STROKE, PageMarkShapes.strokeWidth(24f), DELTA);
    }

    @Test public void theSquareAndTheScreenAreCentredFiguresOfTheSixteenUnitBox() {
        float[] r = new float[PageMarkShapes.RECT_FLOATS];
        PageMarkShapes.terminal(16f, r);
        assertArrayEquals(new float[] {-7.5f, -6.5f, 7.5f, 6.5f}, r, DELTA);
        PageMarkShapes.screenFrame(16f, r);
        assertArrayEquals(new float[] {-7.5f, -7f, 7.5f, 3.5f}, r, DELTA);
        PageMarkShapes.screenStand(16f, r);
        assertArrayEquals(new float[] {-3f, 5f, 3f, 6.8f}, r, DELTA);
        PageMarkShapes.screenFrame(32f, r);
        assertArrayEquals(new float[] {-15f, -14f, 15f, 7f}, r, DELTA);
    }

    @Test public void overviewIsTwoUprightCardsSideBySide() {
        float[] l = new float[PageMarkShapes.RECT_FLOATS];
        float[] r = new float[PageMarkShapes.RECT_FLOATS];
        PageMarkShapes.overviewCard(16f, 0, l);
        PageMarkShapes.overviewCard(16f, 1, r);
        assertTrue("upright", l[3] - l[1] > l[2] - l[0]);
        assertTrue("side by side, not overlapping", l[2] < r[0]);
        assertEquals(l[1], r[1], DELTA);
        assertEquals(l[3], r[3], DELTA);
        assertEquals(-l[0], r[2], DELTA);
    }

    @Test public void aNumberIsSmallerWithTwoDigitsAndOnAScreen() {
        float one = PageMarkShapes.numberTextSize(PageKind.TERMINAL, 1, 16f);
        assertEquals(9f, one, DELTA);
        assertEquals(one * 0.8f, PageMarkShapes.numberTextSize(PageKind.TERMINAL, 2, 16f), DELTA);
        assertEquals(8f, PageMarkShapes.numberTextSize(PageKind.DISPLAY, 1, 16f), DELTA);
    }

    @Test public void thePromptIsAChevronAndAnUnderscoreInsideTheBox() {
        float[] p = new float[PageMarkShapes.PROMPT_FLOATS];
        PageMarkShapes.prompt(24f, 1f, p);
        // The chevron points right: its tip is right of both ends and level with the middle.
        assertTrue(p[2] > p[0]);
        assertEquals(p[0], p[4], DELTA);
        assertEquals(-p[1], p[5], DELTA);
        assertEquals(0f, p[3], DELTA);
        // The underscore is level, sits right of the chevron and on the chevron's base line.
        assertEquals(p[7], p[9], DELTA);
        assertEquals(p[5], p[7], DELTA);
        assertTrue(p[6] > p[2]);
        assertTrue(p[8] > p[6]);
        for (float v : p) assertTrue(Math.abs(v) + PageMarkShapes.STROKE / 2f <= 12f);
    }

    @Test public void theSparkIsAFourPointedStar() {
        float[] s = new float[PageMarkShapes.STAR_FLOATS];
        PageMarkShapes.spark(24f, 1f, s);
        // N, E, S, W points are the outer ones; the points between are nearer the centre.
        assertEquals(-PageMarkShapes.SPARK_OUTER, s[1], DELTA);
        assertEquals(PageMarkShapes.SPARK_OUTER, s[4], DELTA);
        assertEquals(PageMarkShapes.SPARK_OUTER, s[9], DELTA);
        assertEquals(-PageMarkShapes.SPARK_OUTER, s[12], DELTA);
        assertEquals(PageMarkShapes.SPARK_INNER, (float) Math.hypot(s[2], s[3]), DELTA);
    }

    @Test public void aSignInTheDiscIsSmallerThanTheMark() {
        float[] full = new float[PageMarkShapes.STAR_FLOATS];
        float[] cut = new float[PageMarkShapes.STAR_FLOATS];
        PageMarkShapes.spark(24f, 1f, full);
        PageMarkShapes.spark(24f, PageMarkShapes.SIGN_SCALE, cut);
        assertEquals(full[1] * PageMarkShapes.SIGN_SCALE, cut[1], DELTA);
    }

    @Test public void theBurstSitsAtTheTopRightAndStaysInsideTheBox() {
        float[] b = new float[PageMarkShapes.STAR_FLOATS];
        PageMarkShapes.burst(24f, b);
        for (int i = 0; i < b.length; i += 2) {
            assertTrue("right of centre", b[i] > 0f);
            assertTrue("above centre", b[i + 1] < 0f);
            assertTrue(Math.abs(b[i]) <= 12f && Math.abs(b[i + 1]) <= 12f);
        }
    }

    @Test public void aWindowCutsItsNumberAndABareTerminalItsPrompt() {
        assertFalse(PageMarkShapes.cutsNumber(PageKind.HOME, 1));
        assertTrue(PageMarkShapes.cutsNumber(PageKind.TERMINAL, 2));
        assertFalse(PageMarkShapes.cutsNumber(PageKind.TERMINAL, 0));
        assertTrue(PageMarkShapes.cutsPrompt(PageKind.TERMINAL, 0));
        assertFalse(PageMarkShapes.cutsPrompt(PageKind.TERMINAL, 2));
        assertFalse(PageMarkShapes.cutsPrompt(PageKind.DISPLAY, 0));
        assertTrue(PageMarkShapes.cutsNumber(PageKind.DISPLAY, 2));
        assertTrue(PageMarkShapes.cutsNumber(PageKind.LINUX, 2));
        assertFalse(PageMarkShapes.cutsNumber(PageKind.ASSISTANT, 2));
    }
}
