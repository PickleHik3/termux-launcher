package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusRowControllerTest {

    @Test
    public void sessionsIndicator_standsOnlyInAGlyphsStrip() {
        assertTrue(StatusRowController.sessionsIndicatorShown(true, false, false));
        // The Marks line stands alone in the strip: no sessions box left of the marks.
        assertFalse(StatusRowController.sessionsIndicatorShown(true, false, true));
        // The thin row holds the line or the readings only, under either look.
        assertFalse(StatusRowController.sessionsIndicatorShown(true, true, false));
        assertFalse(StatusRowController.sessionsIndicatorShown(true, true, true));
        assertFalse(StatusRowController.sessionsIndicatorShown(false, false, false));
    }
}
