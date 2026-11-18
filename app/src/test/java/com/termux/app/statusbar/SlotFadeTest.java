package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The alpha across the slot's right edge: opaque, a linear ramp, then nothing. */
public class SlotFadeTest {

    private static final float RIGHT = 100f;
    private static final float WIDTH = 40f;

    @Test public void wellInsideIsOpaque() {
        assertEquals(1f, SlotFade.alphaAt(0f, RIGHT, WIDTH), 0f);
    }

    @Test public void theFadeStartIsOpaque() {
        assertEquals(1f, SlotFade.alphaAt(60f, RIGHT, WIDTH), 0f);
    }

    @Test public void theRampIsLinear() {
        assertEquals(0.75f, SlotFade.alphaAt(70f, RIGHT, WIDTH), 1e-6f);
        assertEquals(0.5f, SlotFade.alphaAt(80f, RIGHT, WIDTH), 1e-6f);
        assertEquals(0.25f, SlotFade.alphaAt(90f, RIGHT, WIDTH), 1e-6f);
    }

    @Test public void theEdgeAndBeyondAreClear() {
        assertEquals(0f, SlotFade.alphaAt(100f, RIGHT, WIDTH), 0f);
        assertEquals(0f, SlotFade.alphaAt(250f, RIGHT, WIDTH), 0f);
    }

    @Test public void zeroWidthIsAHardEdge() {
        assertEquals(1f, SlotFade.alphaAt(99.9f, RIGHT, 0f), 0f);
        assertEquals(0f, SlotFade.alphaAt(100f, RIGHT, 0f), 0f);
    }
}
