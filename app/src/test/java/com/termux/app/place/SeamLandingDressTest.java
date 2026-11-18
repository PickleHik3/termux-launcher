package com.termux.app.place;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


import org.junit.Test;
import com.termux.app.page.PageKind;

/**
 * The landing split: a slide whose stack changed into the arriving half's dress on the way
 * ({@link SeamCrossing.Landing#stackDressedFor}) lets the page's commit leave its whole re-dress
 * (58-88 ms on pong) for after the landed frame. Anything else re-dresses in that frame, as before.
 */
public class SeamLandingDressTest {

    private static SeamCrossing.Landing landing(boolean dressDiffered, PageKind dressedFor) {
        SeamCrossing.Landing landing = new SeamCrossing.Landing();
        landing.dressDiffered = dressDiffered;
        landing.dressedFor = dressedFor;
        return landing;
    }

    @Test
    public void aStackAlreadyInTheLandedHalfsDressDefersTheReDress() {
        assertTrue(landing(true, PageKind.TERMINAL).stackDressedFor(PageKind.TERMINAL));
        assertTrue(landing(true, PageKind.HOME).stackDressedFor(PageKind.HOME));
        assertTrue("the display shares the Workspace's half",
            landing(true, PageKind.TERMINAL).stackDressedFor(PageKind.DISPLAY));
    }

    @Test
    public void aStackStillInTheOtherHalfsDressReDressesInTheLandedFrame() {
        assertFalse("released before the swap frame: never changed dress",
            landing(true, null).stackDressedFor(PageKind.TERMINAL));
        assertFalse("came back past the swap",
            landing(true, PageKind.TERMINAL).stackDressedFor(PageKind.HOME));
        assertFalse("halves that dress the stack alike change nothing mid-slide",
            landing(false, PageKind.TERMINAL).stackDressedFor(PageKind.TERMINAL));
    }
}
