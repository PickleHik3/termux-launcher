package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The control bar's full-screen states (#426): normal, a handle, raised over the window. */
public class FullScreenBarTest {

    @Test public void startsNormalWithTheBarShownAndNoHandle() {
        FullScreenBar bar = new FullScreenBar();
        assertEquals(FullScreenBar.Mode.NORMAL, bar.mode());
        assertTrue(bar.barShown());
        assertFalse(bar.handleShown());
        assertFalse(bar.fullScreen());
    }

    @Test public void fullScreenHidesTheBarDownToTheHandle() {
        FullScreenBar bar = new FullScreenBar();
        assertTrue(bar.enter());
        assertEquals(FullScreenBar.Mode.HIDDEN_HANDLE, bar.mode());
        assertFalse(bar.barShown());
        assertTrue(bar.handleShown());
        assertTrue(bar.fullScreen());
    }

    @Test public void enteringTwiceChangesNothing() {
        FullScreenBar bar = new FullScreenBar();
        bar.enter();
        assertFalse(bar.enter());
    }

    @Test public void aSlideUpRaisesTheBarOverTheWindow() {
        FullScreenBar bar = new FullScreenBar();
        bar.enter();
        assertTrue(bar.slideUp());
        assertEquals(FullScreenBar.Mode.RAISED, bar.mode());
        assertTrue(bar.barShown());
        assertFalse(bar.handleShown());
        assertTrue(bar.fullScreen());
    }

    @Test public void aSlideUpOutsideFullScreenDoesNothing() {
        FullScreenBar bar = new FullScreenBar();
        assertFalse(bar.slideUp());
        assertEquals(FullScreenBar.Mode.NORMAL, bar.mode());
    }

    @Test public void aToolUsedATapOutsideOrTheIdleTimeDropTheBarBack() {
        for (int how = 0; how < 3; how++) {
            FullScreenBar bar = new FullScreenBar();
            bar.enter();
            bar.slideUp();
            boolean changed = how == 0 ? bar.used() : how == 1 ? bar.tapOutside() : bar.idle();
            assertTrue(changed);
            assertEquals(FullScreenBar.Mode.HIDDEN_HANDLE, bar.mode());
        }
    }

    @Test public void theDropsDoNothingWhileTheBarIsNotRaised() {
        FullScreenBar bar = new FullScreenBar();
        assertFalse(bar.used() || bar.tapOutside() || bar.idle());
        bar.enter();
        assertFalse(bar.used() || bar.tapOutside() || bar.idle());
        assertEquals(FullScreenBar.Mode.HIDDEN_HANDLE, bar.mode());
    }

    @Test public void leavingFullScreenFromAnyStateRestoresTheBar() {
        FullScreenBar bar = new FullScreenBar();
        bar.enter();
        assertTrue(bar.leave());
        assertEquals(FullScreenBar.Mode.NORMAL, bar.mode());
        bar.enter();
        bar.slideUp();
        assertTrue(bar.leave());
        assertEquals(FullScreenBar.Mode.NORMAL, bar.mode());
        assertFalse(bar.leave());
    }

    @Test public void enteringWhileRaisedKeepsTheBarRaised() {
        FullScreenBar bar = new FullScreenBar();
        bar.enter();
        bar.slideUp();
        assertFalse(bar.enter());
        assertEquals(FullScreenBar.Mode.RAISED, bar.mode());
    }
}
