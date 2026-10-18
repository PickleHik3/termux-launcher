package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Home palette and its keyboard as one overlay, and the typed-key door: which close takes the
 * keyboard down with the palette, and when a key typed on Home opens the palette.
 */
public class CommandPaletteHomeOverlayTest {

    @Test
    public void closing_lowersTheKeyboardThePaletteRaised() {
        CommandPaletteHomeOverlay overlay = new CommandPaletteHomeOverlay();
        overlay.opened(false);
        assertTrue(overlay.isPaired());
        assertTrue(overlay.closed(true, false));
        assertFalse(overlay.isPaired());
    }

    @Test
    public void closing_twice_lowersOnce() {
        CommandPaletteHomeOverlay overlay = new CommandPaletteHomeOverlay();
        overlay.opened(false);
        assertTrue(overlay.closed(true, false));
        assertFalse(overlay.closed(true, false));
    }

    @Test
    public void closing_leavesAKeyboardThatWasAlreadyUp() {
        CommandPaletteHomeOverlay overlay = new CommandPaletteHomeOverlay();
        overlay.opened(true);
        assertFalse(overlay.isPaired());
        assertFalse(overlay.closed(true, false));
    }

    @Test
    public void closing_withAlwaysOn_keepsTheKeyboard() {
        CommandPaletteHomeOverlay overlay = new CommandPaletteHomeOverlay();
        overlay.opened(false);
        assertFalse(overlay.closed(true, true));
        assertFalse(overlay.isPaired());
    }

    @Test
    public void closing_offHome_keepsTheKeyboard() {
        CommandPaletteHomeOverlay overlay = new CommandPaletteHomeOverlay();
        overlay.opened(false);
        assertFalse(overlay.closed(false, false));
    }

    @Test
    public void closing_aPaletteNotOpenedByTheGesture_lowersNothing() {
        assertFalse(new CommandPaletteHomeOverlay().closed(true, false));
    }

    @Test
    public void typing_opensOnHomeWithAlwaysOn() {
        assertTrue(CommandPaletteHomeOverlay.typingOpens(true, true, false, false, "a"));
    }

    @Test
    public void typing_doesNotOpenWithoutAlwaysOn() {
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, false, false, false, "a"));
    }

    @Test
    public void typing_doesNotOpenOffHome() {
        assertFalse(CommandPaletteHomeOverlay.typingOpens(false, true, false, false, "a"));
    }

    @Test
    public void typing_isLeftToAnOpenPalette() {
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, true, true, false, "a"));
    }

    @Test
    public void typing_isLeftToAFocusedHomeFloat() {
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, true, false, true, "a"));
    }

    @Test
    public void typing_spaceOrControl_doesNotOpen() {
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, true, false, false, " "));
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, true, false, false, "\r"));
        assertFalse(CommandPaletteHomeOverlay.typingOpens(true, true, false, false, null));
    }

    // Geometry from the 1080x2400 emulator at 2.625 density: the page's room runs 136..2337, the
    // dock's pinned row tops the bottom stack at 1550 (A-Z row and keyboard below it), the strip
    // reserve is 46dp = 120.75, the top gap 14dp = 36.75 and the tallest palette 296dp = 777.

    @Test
    public void stack_isTheDockAndTheKeyboard_fromTheRoomsBottom() {
        assertEquals(787f, CommandPaletteHomeOverlay.stackHeightPx(136f, 2337f, 1550f), 0.01f);
    }

    @Test
    public void stack_absentOrBelowTheRoom_isNothing() {
        assertEquals(0f, CommandPaletteHomeOverlay.stackHeightPx(136f, 2337f, Float.NaN), 0f);
        assertEquals(0f, CommandPaletteHomeOverlay.stackHeightPx(136f, 2337f, 2400f), 0f);
    }

    @Test
    public void stack_neverTallerThanTheRoom() {
        assertEquals(2201f, CommandPaletteHomeOverlay.stackHeightPx(136f, 2337f, 0f), 0f);
    }

    @Test
    public void portrait_glassEndsAboveTheDock_andGrowsUpToItsFullHeight() {
        float room = 2337f - 136f;
        float stack = CommandPaletteHomeOverlay.stackHeightPx(136f, 2337f, 1550f);
        float inset = CommandPaletteHomeOverlay.bottomInsetPx(room, stack, 120.75f);
        float bottom = 2337f - inset;
        // Glass bottom, then the strip, then the dock's pinned row at 1550: nothing over the dock.
        assertEquals(1429.25f, bottom, 0.01f);
        assertEquals(1550f, bottom + 120.75f, 0.01f);
        float height = CommandPaletteHomeOverlay.availableHeightPx(room, inset, 36.75f, 777f);
        assertEquals(777f, height, 0.01f);
        assertTrue(bottom - height >= 136f);
    }

    @Test
    public void landscape_shortRoom_shrinksTheList_ratherThanReachingUnderTheDock() {
        // 2400x1080: room 63..1080, the keyboard and the dock's two rows stand from 480 down.
        float room = 1080f - 63f;
        float stack = CommandPaletteHomeOverlay.stackHeightPx(63f, 1080f, 480f);
        float inset = CommandPaletteHomeOverlay.bottomInsetPx(room, stack, 120.75f);
        float bottom = 1080f - inset;
        assertEquals(359.25f, bottom, 0.01f);
        float height = CommandPaletteHomeOverlay.availableHeightPx(room, inset, 36.75f, 777f);
        assertEquals(259.5f, height, 0.01f);
        assertEquals(63f + 36.75f, bottom - height, 0.01f);
    }

    @Test
    public void noStack_glassEndsAboveTheStripReserve() {
        assertEquals(120.75f, CommandPaletteHomeOverlay.bottomInsetPx(2201f, 0f, 120.75f), 0.01f);
    }

    @Test
    public void insetAndHeight_neverPassTheRoom() {
        assertEquals(500f, CommandPaletteHomeOverlay.bottomInsetPx(500f, 480f, 120.75f), 0f);
        assertEquals(0f, CommandPaletteHomeOverlay.availableHeightPx(500f, 500f, 36.75f, 777f), 0f);
    }
}
