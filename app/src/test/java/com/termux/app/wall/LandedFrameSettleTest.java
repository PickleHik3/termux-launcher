package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LandedFrameSettleTest {

    @Test
    public void onlyASlidesLastFrameIntoADrawingWindowDefers() {
        assertTrue(LandedFrameSettle.defers(true, true, PaneWallPage.TERMINAL));
        assertTrue(LandedFrameSettle.defers(true, true, PaneWallPage.WIDGETS));
        // A jump, reduced motion or a page taken away at rest settles at once.
        assertFalse(LandedFrameSettle.defers(false, true, PaneWallPage.TERMINAL));
        // No window drawing frames: nothing would run the post.
        assertFalse(LandedFrameSettle.defers(true, false, PaneWallPage.TERMINAL));
        // The display's landing reads the settle's attachment straight after it.
        assertFalse(LandedFrameSettle.defers(true, true, PaneWallPage.DISPLAY));
    }

    @Test
    public void aHeldSettleIsTakenOnceAndThePostFindsNothingLeft() {
        LandedFrameSettle settle = new LandedFrameSettle();
        assertNull(settle.hold(PaneWallPage.TERMINAL));
        assertTrue(settle.pending());
        // A drag beginning before the post runs takes it first.
        assertEquals(PaneWallPage.TERMINAL, settle.take());
        assertFalse(settle.pending());
        // The post then runs nothing.
        assertNull(settle.take());
    }

    @Test
    public void aSecondLandingHandsTheFirstBackToRunFirst() {
        LandedFrameSettle settle = new LandedFrameSettle();
        settle.hold(PaneWallPage.WIDGETS);
        assertEquals(PaneWallPage.WIDGETS, settle.hold(PaneWallPage.TERMINAL));
        assertEquals(PaneWallPage.TERMINAL, settle.take());
    }

    @Test
    public void droppedSettleRunsNowhere() {
        LandedFrameSettle settle = new LandedFrameSettle();
        settle.hold(PaneWallPage.WIDGETS);
        settle.drop();
        assertFalse(settle.pending());
        assertNull(settle.take());
    }
}
