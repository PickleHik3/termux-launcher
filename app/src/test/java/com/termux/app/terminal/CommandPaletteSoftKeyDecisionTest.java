package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * What the palette does with text committed by a system IME. The controller needs a live
 * TermuxActivity and cannot be unit-tested — see CommandPaletteCaptureModelTest's own header — so
 * the decision lives in a helper and the routing is all the controller keeps.
 */
public class CommandPaletteSoftKeyDecisionTest {

    private static CommandPaletteSoftKeyDecision.Action open(int codePoint) {
        return CommandPaletteSoftKeyDecision.decide(true, false, codePoint, false);
    }

    @Test
    public void closedPalette_letsEverythingThroughToTheShell() {
        assertEquals(CommandPaletteSoftKeyDecision.Action.IGNORE,
            CommandPaletteSoftKeyDecision.decide(false, false, 'a', false));
        assertEquals(CommandPaletteSoftKeyDecision.Action.IGNORE,
            CommandPaletteSoftKeyDecision.decide(false, false, '\n', false));
        assertEquals(CommandPaletteSoftKeyDecision.Action.IGNORE,
            CommandPaletteSoftKeyDecision.decide(false, true, 27, false));
    }

    @Test
    public void ctrlHeld_isSwallowedRatherThanTyped() {
        // An extra-keys CTRL latch is applied before the code point arrives, so a latched ctrl+c
        // must not append "c" to the query — the hardware path swallows the same stroke.
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, false, 'c', true));
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, false, '\n', true));
    }

    @Test
    public void enterAsText_commits() {
        // The AOSP keyboard and its descendants send ⏎ as text, never as KEYCODE_ENTER.
        assertEquals(CommandPaletteSoftKeyDecision.Action.COMMIT, open('\r'));
        assertEquals(CommandPaletteSoftKeyDecision.Action.COMMIT, open('\n'));
    }

    @Test
    public void printableCharactersAppend() {
        assertEquals(CommandPaletteSoftKeyDecision.Action.APPEND, open('a'));
        assertEquals(CommandPaletteSoftKeyDecision.Action.APPEND, open('Z'));
        assertEquals(CommandPaletteSoftKeyDecision.Action.APPEND, open('7'));
        assertEquals(CommandPaletteSoftKeyDecision.Action.APPEND, open(' '));
    }

    @Test
    public void astralCodePointAppends() {
        // Emoji arrive as a single code point above the BMP; the controller widens it with
        // Character.toChars, so the decision must not be char-shaped.
        int grinningFace = 0x1F600;
        assertEquals(CommandPaletteSoftKeyDecision.Action.APPEND, open(grinningFace));
        assertEquals(2, Character.toChars(grinningFace).length);
    }

    @Test
    public void deleteAndBackspaceCodePointsErase() {
        assertEquals(CommandPaletteSoftKeyDecision.Action.BACKSPACE, open(127));
        assertEquals(CommandPaletteSoftKeyDecision.Action.BACKSPACE, open(8));
    }

    @Test
    public void escapeCollapses() {
        assertEquals(CommandPaletteSoftKeyDecision.Action.COLLAPSE, open(27));
    }

    @Test
    public void captureMode_swallowsEverything() {
        // A binding needs a key code and a modifier state; committed text carries neither, so
        // nothing may be captured from it — including the enter that would otherwise save.
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, true, 'w', false));
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, true, '\n', false));
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, true, 27, false));
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW,
            CommandPaletteSoftKeyDecision.decide(true, true, 127, false));
    }

    @Test
    public void unhandledControlCharacters_areSwallowedNotLeaked() {
        // Nothing may reach the shell behind an open palette, matching the interceptor's default.
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW, open('\t'));
        assertEquals(CommandPaletteSoftKeyDecision.Action.SWALLOW, open(3));
    }
}
