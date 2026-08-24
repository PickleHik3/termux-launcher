package com.termux.app.surfaces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The surface editor card's height. One rule now: the slider region gets everything between the
 * status pills and the accessory stack, minus the card's own chrome — no display-fraction ceiling,
 * no reserved preview band. The numbers are a real 1080x2412 phone at 420dpi.
 */
public class SurfaceEditorCardMetricsTest {

    /** Card padding plus the measured header and action row. */
    private static final int CHROME = 388;
    /** Space above the accessory stack with the in-app keyboard shown. */
    private static final int AVAILABLE_KEYBOARD_UP = 1144;
    /** The same space once the keyboard is hidden. */
    private static final int AVAILABLE_KEYBOARD_DOWN = 1739;

    @Test
    public void cardFillsAllRoomBetweenStatusAndDock() {
        assertEquals(AVAILABLE_KEYBOARD_DOWN - CHROME,
            SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_DOWN, CHROME));
        assertEquals(AVAILABLE_KEYBOARD_UP - CHROME,
            SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_UP, CHROME));
    }

    @Test
    public void hidingTheKeyboardGrowsTheCardByExactlyTheFreedSpace() {
        int up = SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_UP, CHROME);
        int down = SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_DOWN, CHROME);
        assertEquals(AVAILABLE_KEYBOARD_DOWN - AVAILABLE_KEYBOARD_UP, down - up);
    }

    @Test
    public void chromeTallerThanTheRoomClampsToZero() {
        // A cramped screen with the keyboard up: the scroll region collapses before the card's
        // header is ever pushed up behind the launcher's status bar.
        assertEquals(0, SurfaceEditorCardMetrics.scrollHeightPx(CHROME - 1, CHROME));
    }

    @Test
    public void noRoomAtAllIsNotNegative() {
        assertEquals(0, SurfaceEditorCardMetrics.scrollHeightPx(0, CHROME));
        assertTrue(SurfaceEditorCardMetrics.scrollHeightPx(-40, CHROME) >= 0);
    }
}
