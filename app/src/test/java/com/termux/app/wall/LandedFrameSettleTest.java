package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.termux.app.page.PageKind;

public class LandedFrameSettleTest {

    @Test
    public void onlyASlidesLastFrameIntoADrawingWindowDefers() {
        assertTrue(LandedFrameSettle.defers(true, true, PageKind.TERMINAL));
        assertTrue(LandedFrameSettle.defers(true, true, PageKind.HOME));
        // A jump, reduced motion or a page taken away at rest settles at once.
        assertFalse(LandedFrameSettle.defers(false, true, PageKind.TERMINAL));
        // No window drawing frames: nothing would run the post.
        assertFalse(LandedFrameSettle.defers(true, false, PageKind.TERMINAL));
        // The display's landing reads the settle's attachment straight after it.
        assertFalse(LandedFrameSettle.defers(true, true, PageKind.DISPLAY));
    }

    @Test
    public void aHeldSettleIsTakenOnceAndThePostFindsNothingLeft() {
        LandedFrameSettle settle = new LandedFrameSettle();
        assertNull(settle.hold(PageKind.TERMINAL));
        assertTrue(settle.pending());
        // A drag beginning before the post runs takes it first.
        assertEquals(PageKind.TERMINAL, settle.take());
        assertFalse(settle.pending());
        // The post then runs nothing.
        assertNull(settle.take());
    }

    @Test
    public void aSecondLandingHandsTheFirstBackToRunFirst() {
        LandedFrameSettle settle = new LandedFrameSettle();
        settle.hold(PageKind.HOME);
        assertEquals(PageKind.HOME, settle.hold(PageKind.TERMINAL));
        assertEquals(PageKind.TERMINAL, settle.take());
    }

    @Test
    public void droppedSettleRunsNowhere() {
        LandedFrameSettle settle = new LandedFrameSettle();
        settle.hold(PageKind.HOME);
        settle.drop();
        assertFalse(settle.pending());
        assertNull(settle.take());
    }
}
