package com.termux.app.tips;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where a situation's hint stands (review of round 3). */
public class TipCardViewTest {

    @Test public void aSituationsHintClearsTheCutoutWhereTheStatusBarIsHidden() {
        assertEquals("immersive: the bar reports nothing, the cutout still stands", 98,
            TipCardView.topClearPx(0, 90, 8));
        assertEquals(71, TipCardView.topClearPx(63, 0, 8));
        assertEquals(98, TipCardView.topClearPx(63, 90, 8));
    }
}
