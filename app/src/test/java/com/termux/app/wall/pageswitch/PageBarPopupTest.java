package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.pageswitch.PageBarPopup.State;

import org.junit.Test;

public class PageBarPopupTest {

    @Test public void startsAwayAndTakesNoTouch() {
        PageBarPopup p = new PageBarPopup();
        assertEquals(State.AWAY, p.state());
        assertFalse(p.shown());
        assertFalse(p.touching());
    }

    @Test public void keyDownShowsItUnderTheFinger() {
        PageBarPopup p = new PageBarPopup();
        p.keyDown();
        assertTrue(p.shown());
        assertTrue(p.touching());
    }

    @Test public void aTapOnTheKeyLeavesItOpenForOneUse() {
        PageBarPopup p = new PageBarPopup();
        p.keyDown();
        p.keyReleased(false);
        assertEquals(State.OPEN, p.state());
        assertTrue(p.shown());
        assertFalse(p.touching());
    }

    @Test public void aSlideReleasePutsItAway() {
        PageBarPopup p = new PageBarPopup();
        p.keyDown();
        p.keyReleased(true);
        assertEquals(State.AWAY, p.state());
    }

    @Test public void aTapOutsideBackAPageChangeAndACancelPutItAway() {
        // All of them reach the policy as putAway(), from an open popup or one under a finger.
        PageBarPopup open = new PageBarPopup();
        open.keyDown();
        open.keyReleased(false);
        open.putAway();
        assertFalse(open.shown());
        PageBarPopup touching = new PageBarPopup();
        touching.keyDown();
        touching.putAway();
        assertFalse(touching.shown());
        assertFalse(touching.touching());
    }

    @Test public void aReleaseAfterItWasPutAwayDoesNotReopenIt() {
        PageBarPopup p = new PageBarPopup();
        p.keyDown();
        p.putAway();
        p.keyReleased(false);
        assertEquals(State.AWAY, p.state());
    }

    @Test public void aSecondKeyDownRestartsAnOpenPopup() {
        PageBarPopup p = new PageBarPopup();
        p.keyDown();
        p.keyReleased(false);
        p.keyDown();
        assertTrue(p.touching());
    }
}
