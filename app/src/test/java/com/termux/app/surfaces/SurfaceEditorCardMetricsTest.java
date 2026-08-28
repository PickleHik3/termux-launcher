package com.termux.app.surfaces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The surface editor card's height. The rule: the slider region gets everything between the status
 * pills and the accessory stack, minus the card's own chrome — no display-fraction ceiling, no
 * reserved preview band — but never less than a usable strip, and never more than the parent can
 * hold. The numbers are a real 1080x2412 phone at 420dpi.
 */
public class SurfaceEditorCardMetricsTest {

    /** Card padding plus the measured header and action row. */
    private static final int CHROME = 388;
    /** Space above the accessory stack with the in-app keyboard shown. */
    private static final int AVAILABLE_KEYBOARD_UP = 1144;
    /** The same space once the keyboard is hidden. */
    private static final int AVAILABLE_KEYBOARD_DOWN = 1739;
    /** 132dp at 420dpi: the floor the card refuses to go below. */
    private static final int MIN_SCROLL = 347;
    /** Everything above the card's bottom edge, keyboard up. */
    private static final int MAX_SCROLL = 1144 - 388 + 250;

    @Test
    public void cardFillsAllRoomBetweenStatusAndDock() {
        assertEquals(AVAILABLE_KEYBOARD_DOWN - CHROME,
            SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_DOWN, CHROME, MIN_SCROLL,
                AVAILABLE_KEYBOARD_DOWN - CHROME + 250));
        assertEquals(AVAILABLE_KEYBOARD_UP - CHROME,
            SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_UP, CHROME, MIN_SCROLL,
                MAX_SCROLL));
    }

    @Test
    public void hidingTheKeyboardGrowsTheCardByExactlyTheFreedSpace() {
        int up = SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_UP, CHROME, MIN_SCROLL,
            AVAILABLE_KEYBOARD_DOWN);
        int down = SurfaceEditorCardMetrics.scrollHeightPx(AVAILABLE_KEYBOARD_DOWN, CHROME,
            MIN_SCROLL, AVAILABLE_KEYBOARD_DOWN);
        assertEquals(AVAILABLE_KEYBOARD_DOWN - AVAILABLE_KEYBOARD_UP, down - up);
    }

    /**
     * Issue #20: a Samsung One UI phone with a system IME left less room than the card's own header
     * and action row. The body used to clamp to zero, leaving a title with Reset and Done under it.
     */
    @Test
    public void chromeTallerThanTheRoomKeepsAUsableBody() {
        assertEquals(MIN_SCROLL,
            SurfaceEditorCardMetrics.scrollHeightPx(CHROME - 1, CHROME, MIN_SCROLL, MAX_SCROLL));
        assertEquals(MIN_SCROLL,
            SurfaceEditorCardMetrics.scrollHeightPx(0, CHROME, MIN_SCROLL, MAX_SCROLL));
        assertEquals(MIN_SCROLL,
            SurfaceEditorCardMetrics.scrollHeightPx(-800, CHROME, MIN_SCROLL, MAX_SCROLL));
    }

    /** The floor stops at the card's bottom edge: a header pushed off the top helps nobody. */
    @Test
    public void theCeilingWinsWhenEvenTheFloorWillNotFit() {
        assertEquals(120, SurfaceEditorCardMetrics.scrollHeightPx(-800, CHROME, MIN_SCROLL, 120));
        assertEquals(0, SurfaceEditorCardMetrics.scrollHeightPx(-800, CHROME, MIN_SCROLL, 0));
        assertEquals(0, SurfaceEditorCardMetrics.scrollHeightPx(2000, CHROME, MIN_SCROLL, -40));
    }

    /** Room to spare is still bounded by the parent, not by what the anchors claim. */
    @Test
    public void roomBeyondTheParentIsNotSpent() {
        assertEquals(MAX_SCROLL,
            SurfaceEditorCardMetrics.scrollHeightPx(9000, CHROME, MIN_SCROLL, MAX_SCROLL));
    }

    @Test
    public void noRoomAtAllIsNotNegative() {
        assertTrue(SurfaceEditorCardMetrics.scrollHeightPx(-40, CHROME, MIN_SCROLL, MAX_SCROLL) >= 0);
        assertTrue(SurfaceEditorCardMetrics.scrollHeightPx(-40, CHROME, 0, 0) >= 0);
    }
}
