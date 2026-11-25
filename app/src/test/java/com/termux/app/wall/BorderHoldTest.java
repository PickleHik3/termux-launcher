package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BorderHoldTest {

    private static final float SLOP = 8f;

    @Test
    public void stepsFollowTheSideAndVerticalBordersHaveNone() {
        assertEquals(-1, BorderHold.steps(BorderDrag.Border.LEFT));
        assertEquals(1, BorderHold.steps(BorderDrag.Border.RIGHT));
        assertEquals(0, BorderHold.steps(BorderDrag.Border.TOP));
        assertEquals(0, BorderHold.steps(BorderDrag.Border.BOTTOM));
    }

    @Test
    public void aStillFingerFiresOnceAfterTheHold() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.RIGHT, 100f, 200f, 1000L);
        assertFalse(hold.fires(1000L + BorderHold.HOLD_MS - 1, true));
        assertTrue(hold.fires(1000L + BorderHold.HOLD_MS, true));
        assertFalse(hold.fires(1000L + BorderHold.HOLD_MS + 500, true));
        assertFalse(hold.isFilling());
    }

    @Test
    public void aTopOrBottomBorderNeverHolds() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.TOP, 0f, 0f, 0L);
        assertFalse(hold.isFilling());
        assertFalse(hold.fires(BorderHold.HOLD_MS * 2, true));
    }

    @Test
    public void withoutAWindowOnThatSideItFillsButDoesNotPage() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.LEFT, 0f, 0f, 0L);
        assertFalse(hold.fires(BorderHold.HOLD_MS, false));
        assertTrue(hold.isFilling());
        assertTrue(hold.fires(BorderHold.HOLD_MS, true));
    }

    @Test
    public void aMovingFingerEndsTheHoldForGoodUnlessItRestarts() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.LEFT, 0f, 0f, 0L);
        hold.moved(SLOP, 0f, SLOP, false, 100L);
        assertTrue("inside the slop it is still a hold", hold.isFilling());
        hold.moved(SLOP + 1f, 0f, SLOP, false, 200L);
        assertFalse(hold.isFilling());
        assertFalse(hold.fires(BorderHold.HOLD_MS * 3, true));
    }

    @Test
    public void aRestartingHoldBeginsAgainWhereTheFingerSettlesAndPagesOncePerHold() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.RIGHT, 0f, 0f, 0L);
        assertTrue(hold.fires(BorderHold.HOLD_MS, true));
        hold.moved(50f, 0f, SLOP, true, 2000L);
        assertTrue(hold.isFilling());
        assertFalse(hold.fires(2000L + BorderHold.HOLD_MS - 1, true));
        assertTrue(hold.fires(2000L + BorderHold.HOLD_MS, true));
    }

    @Test
    public void theGlyphFillsFromAfterAShortRestUntilFull() {
        BorderHold hold = new BorderHold();
        hold.begin(BorderDrag.Border.LEFT, 0f, 0f, 0L);
        assertEquals(0f, hold.glyph(100L), 0f);
        assertEquals(0.5f, hold.fill(BorderHold.HOLD_MS / 2), 1e-4f);
        assertTrue(hold.glyph(BorderHold.HOLD_MS / 2) > 0f);
        assertEquals(1f, hold.glyph(BorderHold.HOLD_MS), 1e-4f);
        hold.end();
        assertEquals(0f, hold.glyph(BorderHold.HOLD_MS), 0f);
    }
}
