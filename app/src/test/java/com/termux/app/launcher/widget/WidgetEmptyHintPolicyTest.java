package com.termux.app.launcher.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The empty page's hint steps out from under every see-through overlay on Home. */
public class WidgetEmptyHintPolicyTest {

    @Test
    public void bareHomeShowsTheHint() {
        assertTrue(WidgetEmptyHintPolicy.shown(false, false, false));
    }

    @Test
    public void aFloatOnThePageHidesIt() {
        assertFalse("the float's terminal is see-through",
            WidgetEmptyHintPolicy.shown(true, false, false));
    }

    @Test
    public void theDrawerHidesIt() {
        assertFalse(WidgetEmptyHintPolicy.shown(false, true, false));
    }

    @Test
    public void thePaletteHidesIt() {
        assertFalse(WidgetEmptyHintPolicy.shown(false, false, true));
    }
}
