package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxFullscreenPolicyTest {

    @Test
    public void theWindowInFrontEntersAtOnce() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        assertEquals(LinuxFullscreenPolicy.ENTER, p.request(7, true, 7));
        assertEquals(7, p.fullscreenId());
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.request(7, true, 7));
    }

    @Test
    public void aWindowThatAskedBehindEntersWhenItComesInFront() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.request(7, true, 0));
        assertFalse(p.isFullscreen());
        assertEquals(LinuxFullscreenPolicy.ENTER, p.frontChanged(7));
        assertEquals(7, p.fullscreenId());
    }

    @Test
    public void anAskIsDroppedWhenAnotherWindowComesInFront() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.request(7, true, 0);
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(8));
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(7));
        assertFalse(p.isFullscreen());
    }

    @Test
    public void pagingAwayLeaves() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.request(7, true, 7);
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(7));
        assertEquals(LinuxFullscreenPolicy.LEAVE, p.frontChanged(0));
        assertFalse(p.isFullscreen());
        // Coming back does not take the screen again: the app asks again if it wants it.
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(7));
    }

    @Test
    public void backLeavesOnceAndThenPassesOn() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.request(7, true, 7);
        assertTrue(p.back());
        assertFalse(p.isFullscreen());
        assertFalse(p.back());
    }

    @Test
    public void theAppLeavingOnItsOwnLeaves() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.request(7, true, 7);
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.request(8, false, 7));
        assertEquals(LinuxFullscreenPolicy.LEAVE, p.request(7, false, 7));
        assertFalse(p.isFullscreen());
    }

    @Test
    public void aGoneWindowLeavesAndForgetsItsAsk() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.request(7, true, 7);
        assertFalse(p.gone(8));
        assertTrue(p.gone(7));
        assertFalse(p.isFullscreen());
        p.request(9, true, 0);
        assertFalse(p.gone(9));
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(9));
    }

    @Test
    public void theRowHidesThreeSecondsAfterTheLastPress() {
        assertEquals(1000L + 3000L, LinuxFullscreenPolicy.revealHidesAt(1000L));
    }

    @Test
    public void onlyASwipeUpFromTheBottomEdgeShowsTheRow() {
        float density = 2f;
        assertTrue(LinuxFullscreenPolicy.startsAtBottomEdge(1990f, 2000f, density));
        assertTrue(LinuxFullscreenPolicy.startsAtBottomEdge(2000f - 64f, 2000f, density));
        assertFalse(LinuxFullscreenPolicy.startsAtBottomEdge(1900f, 2000f, density));
        assertFalse(LinuxFullscreenPolicy.startsAtBottomEdge(10f, 0f, density));
        assertTrue(LinuxFullscreenPolicy.swipedUp(1990f, 1990f - 80f, density));
        assertFalse(LinuxFullscreenPolicy.swipedUp(1990f, 1990f - 79f, density));
        assertFalse(LinuxFullscreenPolicy.swipedUp(1990f, 1995f, density));
    }

    @Test
    public void aFieldHoldingTheKeyboardSizesTheWindowAboveIt() {
        assertEquals(1200, LinuxFullscreenPolicy.windowHeight(2000, true, 1200));
    }

    @Test
    public void withoutAFieldOrAKeyboardTheWindowTakesTheWholeLayer() {
        assertEquals(2000, LinuxFullscreenPolicy.windowHeight(2000, false, 1200));
        assertEquals(2000, LinuxFullscreenPolicy.windowHeight(2000, true, 0));
        assertEquals(2000, LinuxFullscreenPolicy.windowHeight(2000, true, 2000));
        assertEquals(2000, LinuxFullscreenPolicy.windowHeight(2000, true, 2400));
        assertEquals(2000, LinuxFullscreenPolicy.windowHeight(2000, true, -5));
    }

    @Test
    public void aPaneInFullScreenIsNotDroppedByTheRowFrontChanging() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        assertEquals(LinuxFullscreenPolicy.ENTER, p.requestPane(9));
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(0));
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.frontChanged(4));
        assertEquals(9, p.fullscreenId());
    }

    @Test
    public void aPaneInFullScreenLeavesOnBackOrWhenTheTerminalLeaves() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.requestPane(9);
        assertEquals(LinuxFullscreenPolicy.NOTHING, p.paneShownChanged(true));
        assertTrue(p.back());
        p.requestPane(9);
        assertEquals(LinuxFullscreenPolicy.LEAVE, p.paneShownChanged(false));
        assertFalse(p.isFullscreen());
    }

    @Test
    public void aRowWindowStillFollowsTheFrontAfterAPaneLeft() {
        LinuxFullscreenPolicy p = new LinuxFullscreenPolicy();
        p.requestPane(9);
        p.back();
        assertEquals(LinuxFullscreenPolicy.ENTER, p.request(9, true, 9));
        assertEquals(LinuxFullscreenPolicy.LEAVE, p.frontChanged(0));
    }
}
