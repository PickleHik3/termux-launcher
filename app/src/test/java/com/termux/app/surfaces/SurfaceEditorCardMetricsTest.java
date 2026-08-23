package com.termux.app.surfaces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The surface editor card's height, in the situations that used to get it wrong.
 *
 * <p>The numbers are a real 1080x2412 phone at 420dpi: the space above the accessory stack with the
 * in-app keyboard up and with it hidden, and the chrome the card carries outside its slider region.
 * Two shapes are pinned here because both were bugs — the card must spend the room the keyboard
 * frees instead of staying its old height in the bottom corner, and it must not shrink below the
 * height it already had when the room is tight.
 */
public class SurfaceEditorCardMetricsTest {

    private static final float PX_PER_DP = 2.625f;
    private static final int SCREEN = 2412;
    /** Card padding plus the measured header, tab row and action row. */
    private static final int CHROME = 388;
    /** Space above the accessory stack with the in-app keyboard shown. */
    private static final int AVAILABLE_KEYBOARD_UP = 1144;
    /** The same space once the keyboard is hidden. */
    private static final int AVAILABLE_KEYBOARD_DOWN = 1739;

    private int maxCard(int available) {
        return SurfaceEditorCardMetrics.maxCardPx(available, SCREEN, PX_PER_DP);
    }

    private int scroll(int available, int content) {
        return SurfaceEditorCardMetrics.scrollHeightPx(available, CHROME, content, SCREEN,
            PX_PER_DP);
    }

    @Test
    public void hidingTheKeyboardGrowsTheCard() {
        int up = maxCard(AVAILABLE_KEYBOARD_UP);
        int down = maxCard(AVAILABLE_KEYBOARD_DOWN);
        assertTrue("freed space must reach the card: " + up + " -> " + down, down > up + 200);
    }

    @Test
    public void grownCardStillLeavesTheEditedSurfaceOnScreen() {
        int card = maxCard(AVAILABLE_KEYBOARD_DOWN);
        assertTrue("card must stop short of the space: " + card, card < AVAILABLE_KEYBOARD_DOWN);
        assertTrue("preview slice too thin: " + (AVAILABLE_KEYBOARD_DOWN - card),
            AVAILABLE_KEYBOARD_DOWN - card >= Math.round(88 * PX_PER_DP));
        assertTrue("card over the display ceiling: " + card, card <= Math.round(SCREEN * 0.62f));
    }

    @Test
    public void tightRoomKeepsTheHeightItAlreadyHad() {
        // 45% of the display is what the fixed-height card used to get; with the keyboard up there
        // is barely more room than that, and spending it on a preview slice would have cost the
        // cramped case a slider row.
        assertEquals(Math.round(SCREEN * 0.45f), maxCard(AVAILABLE_KEYBOARD_UP));
    }

    @Test
    public void shortSectionLeavesNoEmptyGlass() {
        int content = 420;
        assertEquals(content, scroll(AVAILABLE_KEYBOARD_DOWN, content));
    }

    @Test
    public void tallSectionScrollsInsteadOfOverflowing() {
        int scroll = scroll(AVAILABLE_KEYBOARD_DOWN, 4000);
        assertEquals(maxCard(AVAILABLE_KEYBOARD_DOWN) - CHROME, scroll);
        assertTrue("card must fit the space: " + (scroll + CHROME),
            scroll + CHROME <= AVAILABLE_KEYBOARD_DOWN);
    }

    @Test
    public void unmeasuredSectionTakesTheCeiling() {
        assertEquals(SurfaceEditorCardMetrics.maxScrollPx(AVAILABLE_KEYBOARD_DOWN, CHROME, SCREEN,
            PX_PER_DP), scroll(AVAILABLE_KEYBOARD_DOWN, 0));
    }

    @Test
    public void crampedScreenNeverPushesTheHeaderOffTheTop() {
        // A short screen with the keyboard up: whatever the minimums say, chrome plus sliders has
        // to fit the room, or the card grows upward through the launcher's own status bar.
        for (int available = 200; available <= 1200; available += 50) {
            int scroll = SurfaceEditorCardMetrics.scrollHeightPx(available, CHROME, 4000, 1280,
                PX_PER_DP);
            assertTrue("overflowed at available=" + available, scroll + CHROME <= available
                || available <= CHROME);
        }
    }

    @Test
    public void noRoomAtAllIsNotNegative() {
        assertEquals(0, SurfaceEditorCardMetrics.maxCardPx(0, SCREEN, PX_PER_DP));
        assertEquals(0, SurfaceEditorCardMetrics.maxCardPx(-40, SCREEN, PX_PER_DP));
        assertTrue(scroll(0, 4000) >= 0);
    }
}
