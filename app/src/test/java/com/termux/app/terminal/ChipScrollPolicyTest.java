package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChipScrollPolicyTest {

    @Test public void aChipAlreadyInViewLeavesTheRowWhereItIs() {
        assertEquals(40, ChipScrollPolicy.scrollToShow(100, 150, 40, 200, 16, 400));
    }

    @Test public void aChipPastTheEndComesInJustClearOfTheFade() {
        // Right edge 300 plus the 16px fade must sit at the viewport's end: 316 - 200.
        assertEquals(116, ChipScrollPolicy.scrollToShow(260, 300, 0, 200, 16, 400));
    }

    @Test public void aChipBeforeTheStartComesInJustClearOfTheFade() {
        assertEquals(84, ChipScrollPolicy.scrollToShow(100, 140, 150, 200, 16, 400));
    }

    @Test public void theEndsOfTheStripNeedNoRoomForAFadeThatIsNotDrawn() {
        assertEquals(0, ChipScrollPolicy.scrollToShow(0, 40, 90, 200, 16, 400));
        assertEquals(400, ChipScrollPolicy.scrollToShow(560, 600, 0, 200, 16, 400));
    }

    @Test public void aChipWiderThanTheRoomShowsItsStart() {
        assertEquals(84, ChipScrollPolicy.scrollToShow(100, 400, 0, 200, 16, 400));
    }
}
