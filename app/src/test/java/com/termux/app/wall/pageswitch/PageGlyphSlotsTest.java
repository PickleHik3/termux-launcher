package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageGlyphSlotsTest {

    private static void inside(float cx, float cy, float w, float h) {
        float v = PageGlyphSlots.VIEWPORT;
        assertTrue(cx - w / 2f >= 0f && cx + w / 2f <= v);
        assertTrue(cy - h / 2f >= 0f && cy + h / 2f <= v);
        assertTrue(w > 0f && h > 0f);
    }

    @Test public void slotsAreInsideTheViewport() {
        inside(PageGlyphSlots.HOME_SLOT_CX, PageGlyphSlots.HOME_SLOT_CY,
            PageGlyphSlots.HOME_SLOT_W, PageGlyphSlots.HOME_SLOT_H);
        inside(PageGlyphSlots.TERMINAL_SLOT_CX, PageGlyphSlots.TERMINAL_SLOT_CY,
            PageGlyphSlots.TERMINAL_SLOT_W, PageGlyphSlots.TERMINAL_SLOT_H);
    }
}
