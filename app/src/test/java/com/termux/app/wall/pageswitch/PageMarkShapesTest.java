package com.termux.app.wall.pageswitch;

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
        assertEquals(PageMarkShapes.DISC_R * 2f, PageMarkShapes.discRadius(48f), DELTA);
        assertEquals(PageMarkShapes.STROKE, PageMarkShapes.strokeWidth(24f), DELTA);
    }

    @Test public void theDotIsSmallerThanTheDiscAndTheDiscFitsTheBox() {
        assertTrue(PageMarkShapes.dotRadius(24f) < PageMarkShapes.discRadius(24f));
        assertTrue(PageMarkShapes.discRadius(24f) <= 12f);
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

    @Test public void theScreenIsAFrameOverAStand() {
        float[] s = new float[PageMarkShapes.SCREEN_FLOATS];
        PageMarkShapes.screen(24f, 1f, s);
        assertTrue(s[0] < s[2] && s[1] < s[3]);
        assertEquals(s[5], s[7], DELTA);
        assertTrue("the stand is under the frame", s[5] > s[3]);
        assertEquals(-s[4], s[6], DELTA);
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
        assertTrue(PageMarkShapes.SPARK_OUTER * PageMarkShapes.SIGN_SCALE < PageMarkShapes.DISC_R);
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

    @Test public void theDiscCutsANumberOrItsKindsSign() {
        assertTrue(PageMarkShapes.cutsNumber(PageKind.HOME, 1));
        assertTrue(PageMarkShapes.cutsNumber(PageKind.TERMINAL, 2));
        assertFalse("a terminal with no window shows its sign",
            PageMarkShapes.cutsNumber(PageKind.TERMINAL, 0));
        assertFalse(PageMarkShapes.cutsNumber(PageKind.DISPLAY, 0));
        assertTrue(PageMarkShapes.cutsNumber(PageKind.DISPLAY, 2));
        assertFalse("the spark is the assistant's sign", PageMarkShapes.cutsNumber(PageKind.ASSISTANT, 0));
        assertFalse(PageMarkShapes.hasSign(PageKind.HOME));
        assertTrue(PageMarkShapes.hasSign(PageKind.ASSISTANT));
    }
}
