package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusRowFitPolicyTest {

    @Test public void theChipsKeepWhatIsLeftOfTheGapAfterTheReadingsOwnInset() {
        // A CPU figure with 5px of padding before its icon: the chips keep 1px, 6 in all.
        assertEquals(1, StatusRowFitPolicy.chipsEndPaddingPx(6, 5));
        assertEquals(6, StatusRowFitPolicy.chipsEndPaddingPx(6, 0));
    }

    @Test public void aReadingAlreadyWiderThanTheGapIsNotPushedFurther() {
        assertEquals(0, StatusRowFitPolicy.chipsEndPaddingPx(6, 8));
    }

    @Test public void noReadingMeansNoGapOfTheChipsOwn() {
        assertEquals(0, StatusRowFitPolicy.chipsEndPaddingPx(6, -1));
    }

    @Test public void readingsStandTightOnlyWhenTheChipsWouldNotFitBesideSpaciousOnes() {
        // A 200px bar with 1px of end padding has 199px for the chips.
        assertFalse(StatusRowFitPolicy.readingsTight(199, 200, false, 20, 0, 1));
        assertTrue(StatusRowFitPolicy.readingsTight(200, 200, false, 20, 0, 1));
    }

    @Test public void aTightRowJudgesFromTheRoomItWouldHaveSpacious() {
        // Tight, the bar is 20px wider than it would be spacious: 220 - 20 - 1 = 199.
        assertFalse(StatusRowFitPolicy.readingsTight(199, 220, true, 20, 0, 1));
        // Chips that fit only thanks to the tightening keep it: no flip back and forth.
        assertTrue(StatusRowFitPolicy.readingsTight(210, 220, true, 20, 0, 1));
        assertTrue(StatusRowFitPolicy.readingsTight(210, 200, false, 20, 0, 1));
    }

    @Test public void freedRoomIsSharedWithTheCentringSpacer() {
        assertEquals(20, StatusRowFitPolicy.stripShareOf(20, 1f, 1f));
        assertEquals(10, StatusRowFitPolicy.stripShareOf(20, 1f, 2f));
        assertEquals(0, StatusRowFitPolicy.stripShareOf(20, 0f, 1f));
    }
}
