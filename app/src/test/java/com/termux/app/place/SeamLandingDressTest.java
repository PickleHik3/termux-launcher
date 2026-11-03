package com.termux.app.place;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * The landing split: a slide whose stack changed into the arriving half's dress on the way
 * ({@link SeamCrossing.Landing#stackDressedFor}) lets the page's commit leave its whole re-dress
 * (58-88 ms on pong) for after the landed frame. Anything else re-dresses in that frame, as before.
 */
public class SeamLandingDressTest {

    private static SeamCrossing.Landing landing(boolean dressDiffered, PaneWallPage dressedFor) {
        SeamCrossing.Landing landing = new SeamCrossing.Landing();
        landing.dressDiffered = dressDiffered;
        landing.dressedFor = dressedFor;
        return landing;
    }

    @Test
    public void aStackAlreadyInTheLandedHalfsDressDefersTheReDress() {
        assertTrue(landing(true, PaneWallPage.TERMINAL).stackDressedFor(PaneWallPage.TERMINAL));
        assertTrue(landing(true, PaneWallPage.WIDGETS).stackDressedFor(PaneWallPage.WIDGETS));
        assertTrue("the display shares the Workspace's half",
            landing(true, PaneWallPage.TERMINAL).stackDressedFor(PaneWallPage.DISPLAY));
    }

    @Test
    public void aStackStillInTheOtherHalfsDressReDressesInTheLandedFrame() {
        assertFalse("released before the swap frame: never changed dress",
            landing(true, null).stackDressedFor(PaneWallPage.TERMINAL));
        assertFalse("came back past the swap",
            landing(true, PaneWallPage.TERMINAL).stackDressedFor(PaneWallPage.WIDGETS));
        assertFalse("halves that dress the stack alike change nothing mid-slide",
            landing(false, PaneWallPage.TERMINAL).stackDressedFor(PaneWallPage.TERMINAL));
    }
}
