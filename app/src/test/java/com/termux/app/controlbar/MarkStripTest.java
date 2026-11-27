package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The marks' cells along the bar's top row, and what a finger at x is on (#424). */
public class MarkStripTest {

    private static final float DELTA = 0.001f;

    @Test
    public void cellsPackFromTheStartAtTheirOwnWidth() {
        MarkStrip strip = new MarkStrip(3, 32f, 2f, 300f);
        assertEquals(0f, strip.cellStart(0), DELTA);
        assertEquals(32f, strip.cellEnd(0), DELTA);
        assertEquals(34f, strip.cellStart(1), DELTA);
        assertEquals(66f, strip.cellEnd(1), DELTA);
        assertEquals(100f, strip.cellEnd(2), DELTA);
        assertEquals(100f, strip.packedLength(), DELTA);
        assertEquals(32f, strip.cellWidth(), DELTA);
    }

    @Test
    public void cellsShrinkTogetherWhenTheyDoNotFit() {
        MarkStrip strip = new MarkStrip(10, 32f, 2f, 200f);
        assertEquals(200f, strip.packedLength(), DELTA);
        // Ten cells and nine gaps share the length; the gaps keep their size.
        assertEquals((200f - 18f) / 10f, strip.cellWidth(), DELTA);
        assertEquals(200f, strip.cellEnd(9), DELTA);
    }

    @Test
    public void aFingerOnACellOrTheGapBesideItIsOnThatMark() {
        MarkStrip strip = new MarkStrip(3, 32f, 2f, 300f);
        assertEquals(0, strip.indexAt(0f));
        assertEquals(0, strip.indexAt(31f));
        assertEquals(0, strip.indexAt(32.5f));
        assertEquals(1, strip.indexAt(33.5f));
        assertEquals(2, strip.indexAt(99f));
    }

    @Test
    public void aFingerPastTheMarksIsOnNone() {
        MarkStrip strip = new MarkStrip(3, 32f, 2f, 300f);
        assertEquals(MarkStrip.NONE, strip.indexAt(-1f));
        assertEquals(MarkStrip.NONE, strip.indexAt(101f));
        assertEquals(MarkStrip.NONE, new MarkStrip(0, 32f, 2f, 300f).indexAt(0f));
    }

    @Test
    public void theNearestMarkClampsToTheEnds() {
        MarkStrip strip = new MarkStrip(3, 32f, 2f, 300f);
        assertEquals(0, strip.nearestIndex(-50f));
        assertEquals(2, strip.nearestIndex(250f));
        assertEquals(1, strip.nearestIndex(50f));
        assertEquals(MarkStrip.NONE, new MarkStrip(0, 32f, 2f, 300f).nearestIndex(10f));
    }

    @Test
    public void aCellsCentreIsHalfwayAlongIt() {
        MarkStrip strip = new MarkStrip(2, 32f, 2f, 300f);
        assertEquals(16f, strip.cellCentre(0), DELTA);
        assertEquals(50f, strip.cellCentre(1), DELTA);
    }
}
