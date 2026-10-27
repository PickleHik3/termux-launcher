package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;

import com.termux.app.surfaces.rows.StripTouchPolicy.Decision;

import org.junit.Test;

/**
 * Who a touch on a strip belongs to, inside the card's scroll (editor-redesign §9): a swipe that
 * starts on a new strip scrolls the card, its handle included; a rest inside the slop takes the
 * strip. Today's glass strips keep their handle grabbing at once.
 */
public class StripTouchPolicyTest {

    private static final float SLOP = 24f;
    private static final long HOLD = StripTouchPolicy.HOLD_TO_CLAIM_MS;

    @Test public void aSwipeThatStartsOnANewStripScrollsTheCardEvenFromItsHandle() {
        assertEquals(Decision.SCROLL,
            StripTouchPolicy.decide(true, false, true, SLOP + 1f, SLOP, 16L));
        assertEquals(Decision.SCROLL,
            StripTouchPolicy.decide(false, false, true, 300f, SLOP, HOLD - 1L));
    }

    @Test public void aRestInsideTheSlopTakesTheStrip() {
        assertEquals(Decision.CLAIM, StripTouchPolicy.decide(true, false, true, 0f, SLOP, HOLD));
        assertEquals(Decision.CLAIM,
            StripTouchPolicy.decide(false, false, true, SLOP, SLOP, HOLD + 40L));
    }

    @Test public void insideTheSlopAndTheHoldNothingIsDecided() {
        assertEquals(Decision.WAIT, StripTouchPolicy.decide(true, false, true, 0f, SLOP, 0L));
        assertEquals(Decision.WAIT,
            StripTouchPolicy.decide(false, false, true, SLOP, SLOP, HOLD - 1L));
    }

    @Test public void aMoveAfterTheHoldStillBelongsToTheStrip() {
        assertEquals(Decision.CLAIM,
            StripTouchPolicy.decide(false, false, true, 300f, SLOP, HOLD + 1L));
    }

    @Test public void todaysGlassStripsTakeATouchOnTheirHandleAtOnce() {
        assertEquals(Decision.CLAIM, StripTouchPolicy.decide(true, true, true, 0f, SLOP, 0L));
        assertEquals(Decision.CLAIM, StripTouchPolicy.decide(true, true, true, 200f, SLOP, 10L));
        assertEquals(Decision.SCROLL,
            StripTouchPolicy.decide(false, true, true, SLOP + 1f, SLOP, 10L));
    }

    @Test public void outsideAScrollingContainerEveryTouchClaimsAtOnce() {
        assertEquals(Decision.CLAIM, StripTouchPolicy.decide(false, false, false, 0f, SLOP, 0L));
        assertEquals(Decision.CLAIM,
            StripTouchPolicy.decide(true, false, false, 300f, SLOP, 1L));
    }
}
