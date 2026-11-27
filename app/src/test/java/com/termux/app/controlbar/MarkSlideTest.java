package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A tap on a mark goes to its window; sliding along the marks changes window (#424). */
public class MarkSlideTest {

    /** Cells at 0-32, 34-66, 68-100, 102-134. */
    private final MarkStrip mStrip = new MarkStrip(4, 32f, 2f, 300f);

    @Test
    public void aTapGoesToTheMarkUnderTheFinger() {
        MarkSlide slide = new MarkSlide(8f);
        assertTrue(slide.down(mStrip, 50f));
        assertEquals(MarkStrip.NONE, slide.move(53f));
        assertEquals(1, slide.up());
    }

    @Test
    public void aDownPastTheMarksIsNotTaken() {
        MarkSlide slide = new MarkSlide(8f);
        assertFalse(slide.down(mStrip, 200f));
        assertEquals(MarkStrip.NONE, slide.move(10f));
        assertEquals(MarkStrip.NONE, slide.up());
    }

    @Test
    public void slidingGoesToEachMarkItReachesOnce() {
        MarkSlide slide = new MarkSlide(8f);
        slide.down(mStrip, 10f);
        assertEquals(MarkStrip.NONE, slide.move(20f));
        assertTrue(slide.sliding());
        assertEquals(1, slide.move(40f));
        assertEquals(MarkStrip.NONE, slide.move(45f));
        assertEquals(2, slide.move(80f));
        assertEquals(3, slide.move(500f));
        assertEquals(1, slide.move(50f));
        // A slide is no tap: letting go goes nowhere more.
        assertEquals(MarkStrip.NONE, slide.up());
    }

    @Test
    public void aSlideBeforeTheMarksSettlesOnTheFirst() {
        MarkSlide slide = new MarkSlide(8f);
        slide.down(mStrip, 70f);
        assertEquals(0, slide.move(-40f));
    }

    @Test
    public void cancelGoesNowhere() {
        MarkSlide slide = new MarkSlide(8f);
        slide.down(mStrip, 10f);
        slide.cancel();
        assertEquals(MarkStrip.NONE, slide.move(100f));
        assertEquals(MarkStrip.NONE, slide.up());
    }
}
