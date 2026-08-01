package com.termux.terminal;

import android.view.KeyEvent;

/**
 * The kitty keyboard protocol: mode negotiation on the emulator, and the encoding of key events.
 */
public class KittyKeyboardProtocolTest extends TerminalTestCase {

    private static final int DISAMBIGUATE = KittyKeyEncoder.FLAG_DISAMBIGUATE;

    private static final int EVENTS = KittyKeyEncoder.FLAG_REPORT_EVENTS;

    private static final int ALTERNATES = KittyKeyEncoder.FLAG_REPORT_ALTERNATE_KEYS;

    private static final int ALL_KEYS = KittyKeyEncoder.FLAG_REPORT_ALL_KEYS;

    private static final int TEXT = KittyKeyEncoder.FLAG_REPORT_TEXT;

    /** Encode a press of a key with no modifiers and no text of its own. */
    private String press(int keyCode, int flags) {
        return KittyKeyEncoder.encode(keyCode, 0, 0, 0, 0, KittyKeyEncoder.EVENT_PRESS, flags);
    }

    private String pressLetter(char letter, int modifiers, int flags) {
        return KittyKeyEncoder.encode(KeyEvent.KEYCODE_A + (letter - 'a'), letter,
            Character.toUpperCase(letter), letter, modifiers, KittyKeyEncoder.EVENT_PRESS, flags);
    }

    // Mode negotiation.

    public void testFlagsStartOffAndAreSetReplacingAll() {
        withTerminalSized(4, 4);
        assertEquals(0, mTerminal.getKeyboardFlags());
        enterString("\033[=5;1u");
        assertEquals(5, mTerminal.getKeyboardFlags());
        // Mode 1 replaces, so the previously set bit 4 is gone.
        enterString("\033[=1;1u");
        assertEquals(1, mTerminal.getKeyboardFlags());
    }

    public void testSetAndClearIndividualBits() {
        withTerminalSized(4, 4).enterString("\033[=1;1u");
        enterString("\033[=8;2u");
        assertEquals(9, mTerminal.getKeyboardFlags());
        enterString("\033[=1;3u");
        assertEquals(8, mTerminal.getKeyboardFlags());
    }

    public void testModeDefaultsToReplace() {
        withTerminalSized(4, 4).enterString("\033[=3u");
        assertEquals(3, mTerminal.getKeyboardFlags());
    }

    public void testUndefinedFlagBitsAreNotStored() {
        withTerminalSized(4, 4).enterString("\033[=255;1u");
        assertEquals(KittyKeyEncoder.FLAGS_MASK, mTerminal.getKeyboardFlags());
    }

    public void testQueryReportsTheFlags() {
        withTerminalSized(4, 4);
        assertEnteringStringGivesResponse("\033[?u", "\033[?0u");
        enterString("\033[=9;1u");
        assertEnteringStringGivesResponse("\033[?u", "\033[?9u");
    }

    public void testPushAndPop() {
        withTerminalSized(4, 4).enterString("\033[=1;1u");
        enterString("\033[>17u");
        assertEquals(17, mTerminal.getKeyboardFlags());
        enterString("\033[<u");
        assertEquals(1, mTerminal.getKeyboardFlags());
    }

    public void testPushWithoutFlagsPushesZero() {
        withTerminalSized(4, 4).enterString("\033[=5;1u\033[>u");
        assertEquals(0, mTerminal.getKeyboardFlags());
        enterString("\033[<u");
        assertEquals(5, mTerminal.getKeyboardFlags());
    }

    public void testPoppingAnEmptyStackResetsTheFlags() {
        withTerminalSized(4, 4).enterString("\033[=9;1u\033[<3u");
        assertEquals(0, mTerminal.getKeyboardFlags());
    }

    public void testPopCountPopsSeveralEntries() {
        withTerminalSized(4, 4).enterString("\033[=1;1u\033[>2u\033[>4u\033[>8u");
        assertEquals(8, mTerminal.getKeyboardFlags());
        enterString("\033[<2u");
        assertEquals(2, mTerminal.getKeyboardFlags());
    }

    /** A full stack evicts its oldest entry instead of growing or refusing the push. */
    public void testStackIsBounded() {
        withTerminalSized(4, 4).enterString("\033[=1;1u");
        for (int i = 0; i < 40; i++) enterString("\033[>2u");
        assertEquals(2, mTerminal.getKeyboardFlags());
        for (int i = 0; i < 40; i++) enterString("\033[<u");
        // Every remembered entry was a 2, and the original 1 was evicted long ago.
        assertEquals(0, mTerminal.getKeyboardFlags());
    }

    /** The alternate screen keeps its own flags, so an editor cannot disturb the shell's mode. */
    public void testMainAndAlternateScreensHaveSeparateState() {
        withTerminalSized(4, 4).enterString("\033[=1;1u");
        enterString("\033[?1049h");
        assertEquals(0, mTerminal.getKeyboardFlags());
        enterString("\033[=17;1u");
        assertEquals(17, mTerminal.getKeyboardFlags());
        enterString("\033[?1049l");
        assertEquals(1, mTerminal.getKeyboardFlags());
    }

    public void testResetClearsTheFlags() {
        withTerminalSized(4, 4).enterString("\033[=9;1u\033[>1u");
        mTerminal.reset();
        assertEquals(0, mTerminal.getKeyboardFlags());
    }

    // Encoding.

    public void testNoFlagsMeansLegacyEncoding() {
        assertNull(press(KeyEvent.KEYCODE_ESCAPE, 0));
        assertNull(pressLetter('a', 0, 0));
    }

    public void testEscapeIsDisambiguated() {
        assertEquals("\033[27u", press(KeyEvent.KEYCODE_ESCAPE, DISAMBIGUATE));
    }

    /** Enter, Tab and Backspace keep legacy bytes so a shell stays usable after a crash. */
    public void testC0KeysStayLegacyUntilAllKeysAreReported() {
        assertNull(press(KeyEvent.KEYCODE_ENTER, DISAMBIGUATE));
        assertNull(press(KeyEvent.KEYCODE_TAB, DISAMBIGUATE));
        assertNull(press(KeyEvent.KEYCODE_DEL, DISAMBIGUATE));
        assertEquals("\033[13u", press(KeyEvent.KEYCODE_ENTER, DISAMBIGUATE | ALL_KEYS));
        assertEquals("\033[9u", press(KeyEvent.KEYCODE_TAB, DISAMBIGUATE | ALL_KEYS));
        assertEquals("\033[127u", press(KeyEvent.KEYCODE_DEL, DISAMBIGUATE | ALL_KEYS));
    }

    public void testArrowsUseTheLetterFormAndOmitTheDefaultNumber() {
        assertEquals("\033[A", press(KeyEvent.KEYCODE_DPAD_UP, DISAMBIGUATE));
        assertEquals("\033[D", press(KeyEvent.KEYCODE_DPAD_LEFT, DISAMBIGUATE));
        assertEquals("\033[1;5A", KittyKeyEncoder.encode(KeyEvent.KEYCODE_DPAD_UP, 0, 0, 0,
            KittyKeyEncoder.MOD_CTRL, KittyKeyEncoder.EVENT_PRESS, DISAMBIGUATE));
    }

    public void testTildeFormKeys() {
        assertEquals("\033[3~", press(KeyEvent.KEYCODE_FORWARD_DEL, DISAMBIGUATE));
        assertEquals("\033[5~", press(KeyEvent.KEYCODE_PAGE_UP, DISAMBIGUATE));
        // F3 must not be "CSI R", which is the Cursor Position Report.
        assertEquals("\033[13~", press(KeyEvent.KEYCODE_F3, DISAMBIGUATE));
        assertEquals("\033[15~", press(KeyEvent.KEYCODE_F5, DISAMBIGUATE));
    }

    public void testPlainTypingStaysLegacyUnderDisambiguate() {
        assertNull(pressLetter('a', 0, DISAMBIGUATE));
        assertNull(pressLetter('a', KittyKeyEncoder.MOD_SHIFT, DISAMBIGUATE));
    }

    public void testCtrlAndAltCombinationsAreDisambiguated() {
        assertEquals("\033[97;5u", pressLetter('a', KittyKeyEncoder.MOD_CTRL, DISAMBIGUATE));
        assertEquals("\033[97;3u", pressLetter('a', KittyKeyEncoder.MOD_ALT, DISAMBIGUATE));
        assertEquals("\033[97;7u", pressLetter('a',
            KittyKeyEncoder.MOD_CTRL | KittyKeyEncoder.MOD_ALT, DISAMBIGUATE));
    }

    /** The key's identity is always the unshifted code point, never the shifted one. */
    public void testTheKeyNumberIsTheUnshiftedCodePoint() {
        String encoded = KittyKeyEncoder.encode(KeyEvent.KEYCODE_A, 'a', 'A', 'A',
            KittyKeyEncoder.MOD_CTRL | KittyKeyEncoder.MOD_SHIFT, KittyKeyEncoder.EVENT_PRESS, DISAMBIGUATE);
        assertEquals("\033[97;6u", encoded);
    }

    public void testAllKeysReportingTakesOverPlainTyping() {
        assertEquals("\033[97u", pressLetter('a', 0, ALL_KEYS));
    }

    public void testEventTypesAreOnlyReportedWhenAsked() {
        // Without the flag, a repeat is a press and a release produces nothing at all.
        assertEquals("\033[27u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ESCAPE, 0, 0, 0, 0,
            KittyKeyEncoder.EVENT_REPEAT, DISAMBIGUATE));
        assertEquals("", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ESCAPE, 0, 0, 0, 0,
            KittyKeyEncoder.EVENT_RELEASE, DISAMBIGUATE));
        assertEquals("\033[27;1:2u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ESCAPE, 0, 0, 0, 0,
            KittyKeyEncoder.EVENT_REPEAT, DISAMBIGUATE | EVENTS));
        assertEquals("\033[27;1:3u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ESCAPE, 0, 0, 0, 0,
            KittyKeyEncoder.EVENT_RELEASE, DISAMBIGUATE | EVENTS));
        assertEquals("\033[27;5:3u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ESCAPE, 0, 0, 0,
            KittyKeyEncoder.MOD_CTRL, KittyKeyEncoder.EVENT_RELEASE, DISAMBIGUATE | EVENTS));
    }

    public void testAlternateKeysAreReportedOnlyWithShift() {
        assertEquals("\033[97:65;6u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_A, 'a', 'A', 'A',
            KittyKeyEncoder.MOD_CTRL | KittyKeyEncoder.MOD_SHIFT, KittyKeyEncoder.EVENT_PRESS,
            DISAMBIGUATE | ALTERNATES));
        // No shift, so no shifted key is reported even though one exists.
        assertEquals("\033[97;5u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_A, 'a', 'A', 'a',
            KittyKeyEncoder.MOD_CTRL, KittyKeyEncoder.EVENT_PRESS, DISAMBIGUATE | ALTERNATES));
    }

    public void testAssociatedTextIsReported() {
        assertEquals("\033[97;;97u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_A, 'a', 'A', 'a', 0,
            KittyKeyEncoder.EVENT_PRESS, ALL_KEYS | TEXT));
        assertEquals("\033[97;2;65u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_A, 'a', 'A', 'A',
            KittyKeyEncoder.MOD_SHIFT, KittyKeyEncoder.EVENT_PRESS, ALL_KEYS | TEXT));
    }

    /** Control characters must never appear in the text field. */
    public void testControlTextIsNotReported() {
        assertEquals("\033[13u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_ENTER, 0, 0, '\r', 0,
            KittyKeyEncoder.EVENT_PRESS, ALL_KEYS | TEXT));
    }

    /** Text reporting is defined as an enhancement of all-keys reporting, not on its own. */
    public void testTextReportingWithoutAllKeysDoesNothing() {
        assertNull(pressLetter('a', 0, DISAMBIGUATE | TEXT));
    }

    public void testModifierKeysAreOnlyReportedWithAllKeys() {
        assertEquals("", press(KeyEvent.KEYCODE_CTRL_LEFT, DISAMBIGUATE | EVENTS));
        assertEquals("\033[57442;5u", KittyKeyEncoder.encode(KeyEvent.KEYCODE_CTRL_LEFT, 0, 0, 0,
            KittyKeyEncoder.MOD_CTRL, KittyKeyEncoder.EVENT_PRESS, ALL_KEYS));
    }

    /** Lock modifiers stay out of text keys unless the program asked for all keys. */
    public void testLockModifiersAreStrippedFromTextKeys() {
        assertEquals("\033[97;5u", pressLetter('a',
            KittyKeyEncoder.MOD_CTRL | KittyKeyEncoder.MOD_CAPS_LOCK, DISAMBIGUATE));
        assertEquals("\033[97;69u", pressLetter('a',
            KittyKeyEncoder.MOD_CTRL | KittyKeyEncoder.MOD_CAPS_LOCK, ALL_KEYS));
    }

    public void testUnknownKeyFallsBackToLegacy() {
        assertNull(press(KeyEvent.KEYCODE_CAMERA, DISAMBIGUATE | ALL_KEYS));
    }
}
