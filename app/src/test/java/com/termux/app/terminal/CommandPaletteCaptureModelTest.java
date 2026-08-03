package com.termux.app.terminal;

import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every raw-key decision the capture overlay makes lives here, because the controller needs a live
 * TermuxActivity and cannot be unit-tested — see TerminalActionDispatcherTest's own header. The
 * controller is left holding only routing.
 */
@RunWith(RobolectricTestRunner.class)
public class CommandPaletteCaptureModelTest {

    @Test
    public void hardwareKey_spellsTheStrokeAConfigFileWouldUse() {
        assertEquals("ctrl+alt+w",
            CommandPaletteCaptureModel.strokeFor(KeyEvent.KEYCODE_W, true, true, false));
        assertEquals("ctrl+alt+shift+w",
            CommandPaletteCaptureModel.strokeFor(KeyEvent.KEYCODE_W, true, true, true));
        assertEquals("alt+space",
            CommandPaletteCaptureModel.strokeFor(KeyEvent.KEYCODE_SPACE, false, true, false));
        assertEquals("w",
            CommandPaletteCaptureModel.strokeFor(KeyEvent.KEYCODE_W, false, false, false));
    }

    @Test
    public void modifiersAreEmittedInCanonicalOrderHoweverTheyArrive() {
        // The resolver's table is keyed on one spelling, so shift+ctrl+alt must not produce a
        // different string from ctrl+alt+shift.
        String expected = "ctrl+alt+shift+k";
        assertEquals(expected,
            CommandPaletteCaptureModel.strokeFor(KeyEvent.KEYCODE_K, true, true, true));
        assertEquals(expected,
            CommandPaletteCaptureModel.strokeForChar('k', true, true, true));
    }

    @Test
    public void aModifierAloneIsNotAStroke() {
        // No special rule needed: keyToken already returns null for the modifier key codes, so
        // "wait for a non-modifier key" falls out of the existing table.
        assertNull(CommandPaletteCaptureModel.strokeFor(
            KeyEvent.KEYCODE_CTRL_LEFT, true, false, false));
        assertNull(CommandPaletteCaptureModel.strokeFor(
            KeyEvent.KEYCODE_ALT_LEFT, false, true, false));
        assertNull(CommandPaletteCaptureModel.strokeFor(
            KeyEvent.KEYCODE_SHIFT_RIGHT, false, false, true));
    }

    @Test
    public void anUnmappableKeyCodeIsNotAStroke() {
        assertNull(CommandPaletteCaptureModel.strokeFor(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true, true, false));
    }

    @Test
    public void inAppKeyboardCharacters_lowercaseIntoTheTokenAndTakeShiftFromTheFlag() {
        // A shifted letter arrives as an uppercase char, not as a shift flag. Folding the case into
        // the token would spell a stroke no key press could ever match.
        assertEquals("ctrl+alt+w",
            CommandPaletteCaptureModel.strokeForChar('W', true, true, false));
        assertEquals("ctrl+alt+w",
            CommandPaletteCaptureModel.strokeForChar('w', true, true, false));
        assertEquals("ctrl+space",
            CommandPaletteCaptureModel.strokeForChar(' ', true, false, false));
        assertNull(CommandPaletteCaptureModel.strokeForChar('\n', true, true, false));
        assertNull(CommandPaletteCaptureModel.strokeForChar('\t', true, true, false));
    }

    @Test
    public void isBindable_refusesABareKeyAndAcceptsAModifiedOne() {
        // Binding a bare w would swallow typing that character everywhere.
        assertFalse(CommandPaletteCaptureModel.isBindable("w"));
        assertFalse(CommandPaletteCaptureModel.isBindable(null));
        assertFalse(CommandPaletteCaptureModel.isBindable(""));
        assertFalse(CommandPaletteCaptureModel.isBindable("space"));
        assertTrue(CommandPaletteCaptureModel.isBindable("ctrl+alt+w"));
        assertTrue(CommandPaletteCaptureModel.isBindable("alt+w"));
        assertTrue(CommandPaletteCaptureModel.isBindable("shift+f1"));
    }

    @Test
    public void aCapturedStrokeSurvivesTheWriterAndTheResolversNormalization() {
        // End to end for the one thing that must hold: what capture produces is what the file
        // spells, and what the resolver keys its table on.
        String stroke = CommandPaletteCaptureModel.strokeFor(
            KeyEvent.KEYCODE_W, true, true, false);
        assertEquals(stroke, TerminalKeyBindingResolver.normalizeSequenceSpec(stroke));
        assertEquals("map ctrl+alt+w app.launch org.mozilla.firefox",
            TerminalBindingConfigWriter.formatMapLine(stroke, "app.launch",
                java.util.Collections.singletonList("org.mozilla.firefox")));
    }
}
