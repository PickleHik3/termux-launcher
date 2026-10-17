package com.termux.app.terminal;

import org.junit.Test;

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
}
