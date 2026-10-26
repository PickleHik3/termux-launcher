package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Who a touch on a vertical slider column belongs to, inside the card's vertical scroll: the
 * handle drags at once, anywhere else waits for a hold, and a finger that leaves the slop first
 * is scrolling the card (the drag that set Edge to 100% on its way down the card).
 */
public class LegendSliderClaimTest {

    private static final float SLOP = 24f;
    private static final long HOLD = LegendSlider.HOLD_TO_CLAIM_MS;

    @Test public void aTouchOnTheHandleClaimsAtOnce() {
        assertEquals(LegendSlider.Claim.CLAIM, LegendSlider.claim(true, true, 0f, SLOP, 0L));
        // Even one that is already moving fast along the track.
        assertEquals(LegendSlider.Claim.CLAIM, LegendSlider.claim(true, true, 200f, SLOP, 10L));
    }

    @Test public void offTheHandleAMoveBeyondTheSlopBeforeTheHoldIsAScroll() {
        assertEquals(LegendSlider.Claim.SCROLL,
            LegendSlider.claim(false, true, SLOP + 1f, SLOP, HOLD - 1L));
        assertEquals(LegendSlider.Claim.SCROLL, LegendSlider.claim(false, true, 300f, SLOP, 16L));
    }

    @Test public void offTheHandleAHoldInsideTheSlopClaims() {
        assertEquals(LegendSlider.Claim.CLAIM, LegendSlider.claim(false, true, 0f, SLOP, HOLD));
        assertEquals(LegendSlider.Claim.CLAIM,
            LegendSlider.claim(false, true, SLOP, SLOP, HOLD + 40L));
    }

    @Test public void offTheHandleInsideTheSlopAndTheHoldIsStillUndecided() {
        assertEquals(LegendSlider.Claim.WAIT, LegendSlider.claim(false, true, 0f, SLOP, 0L));
        assertEquals(LegendSlider.Claim.WAIT,
            LegendSlider.claim(false, true, SLOP, SLOP, HOLD - 1L));
    }

    @Test public void outsideAScrollingContainerEveryTouchClaimsAtOnce() {
        assertEquals(LegendSlider.Claim.CLAIM, LegendSlider.claim(false, false, 0f, SLOP, 0L));
    }

    @Test public void theHandleTargetIsCentredOnTheHandle() {
        assertTrue(LegendSlider.onHandle(100f, 100f, 24f));
        assertTrue(LegendSlider.onHandle(124f, 100f, 24f));
        assertTrue(LegendSlider.onHandle(76f, 100f, 24f));
        assertFalse(LegendSlider.onHandle(125f, 100f, 24f));
        assertFalse(LegendSlider.onHandle(70f, 100f, 24f));
    }
}
