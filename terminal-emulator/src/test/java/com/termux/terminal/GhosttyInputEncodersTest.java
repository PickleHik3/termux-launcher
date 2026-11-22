package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Keys, mouse and focus bytes on the Fast engine come from libghostty-vt's encoders. Runs only in
 * builds made with {@code -Pghostty=true}, like {@link GhosttyCoreTest}; elsewhere every test
 * returns at once. The expected bytes come from a second core fed the same mode-setting escapes and
 * asked through {@link GhosttyNative} directly, so these tests pin the Java side: the mapping, the
 * clamping, the gate and the property.
 */
public class GhosttyInputEncodersTest extends TerminalTestCase {

    private static final int CW = INITIAL_CELL_WIDTH_PIXELS, CH = INITIAL_CELL_HEIGHT_PIXELS;

    private static boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    @Override
    protected void tearDown() throws Exception {
        TerminalEmulator.setInputPropertyReaderForTests(null);
        super.tearDown();
    }

    private TerminalEmulator fastEmulator(int columns, int rows) {
        TerminalEmulator emulator = new TerminalEmulator(mOutput, false, columns, rows, CW, CH, rows * 2, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), emulator.useGhosttyCore());
        return emulator;
    }

    private static GhosttyCore referenceCore(int columns, int rows) {
        GhosttyCore core = GhosttyCore.create(columns, rows, CW, CH, null, rows * 2);
        assertNotNull("library failed to load: " + GhosttyNative.loadError(), core);
        return core;
    }

    private static void feed(TerminalEmulator emulator, GhosttyCore reference, String escapes) {
        if (escapes.isEmpty()) return;
        byte[] bytes = escapes.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
        reference.write(bytes, 0, bytes.length);
    }

    // ---- keys -------------------------------------------------------------------------------

    /** One row of the key table: the physical key, what it types with no modifier, and with Shift. */
    private static final class KeyRow {
        final String name;
        final int key, unshifted;
        final String text, shiftText;
        final boolean allActions;

        KeyRow(String name, int key, int unshifted, String text, String shiftText, boolean allActions) {
            this.name = name;
            this.key = key;
            this.unshifted = unshifted;
            this.text = text;
            this.shiftText = shiftText;
            this.allActions = allActions;
        }
    }

    private static KeyRow key(String name, int key, int unshifted, String text, String shiftText, boolean all) {
        return new KeyRow(name, key, unshifted, text, shiftText, all);
    }

    private static KeyRow plain(String name, int key, boolean all) {
        return key(name, key, 0, null, null, all);
    }

    private static final KeyRow[] KEYS = {
        key("a", GhosttyKeys.KEY_A, 'a', "a", "A", true),
        key("A", GhosttyKeys.KEY_A, 'a', "A", "A", false),
        key("1", GhosttyKeys.KEY_DIGIT_1, '1', "1", "!", false),
        key("!", GhosttyKeys.KEY_DIGIT_1, '1', "!", "!", false),
        key("space", GhosttyKeys.KEY_SPACE, ' ', " ", " ", false),
        plain("Enter", GhosttyKeys.KEY_ENTER, true),
        plain("Tab", GhosttyKeys.KEY_TAB, false),
        plain("Escape", GhosttyKeys.KEY_ESCAPE, false),
        plain("Backspace", GhosttyKeys.KEY_BACKSPACE, false),
        plain("Delete", GhosttyKeys.KEY_DELETE, false),
        plain("Up", GhosttyKeys.KEY_ARROW_UP, true),
        plain("Down", GhosttyKeys.KEY_ARROW_DOWN, false),
        plain("Left", GhosttyKeys.KEY_ARROW_LEFT, false),
        plain("Right", GhosttyKeys.KEY_ARROW_RIGHT, false),
        plain("Home", GhosttyKeys.KEY_HOME, false),
        plain("End", GhosttyKeys.KEY_END, false),
        plain("PageUp", GhosttyKeys.KEY_PAGE_UP, false),
        plain("F1", GhosttyKeys.KEY_F1, false),
        plain("F5", GhosttyKeys.KEY_F5, true),
        plain("F12", GhosttyKeys.KEY_F12, false),
        key("numpad5", GhosttyKeys.KEY_NUMPAD_5, '5', "5", "5", false),
        plain("numpadEnter", GhosttyKeys.KEY_NUMPAD_ENTER, false),
        key("é", GhosttyKeys.KEY_UNIDENTIFIED, 'é', "é", "É", false),
    };

    private static final int[] KEY_MODS = {0, GhosttyKeys.MOD_SHIFT, GhosttyKeys.MOD_CTRL, GhosttyKeys.MOD_ALT,
        GhosttyKeys.MOD_CTRL | GhosttyKeys.MOD_SHIFT, GhosttyKeys.MOD_CTRL | GhosttyKeys.MOD_ALT,
        GhosttyKeys.MOD_SUPER};

    private static final int[] KITTY_FLAG_SETS = {0, 1, 3, 5, 15, 31};

    /** Keys of every row, modifier and action under one terminal state; returns how many were non-empty. */
    private int checkKeys(TerminalEmulator emulator, GhosttyCore reference, String state) {
        int nonEmpty = 0;
        for (KeyRow row : KEYS) {
            for (int mods : KEY_MODS) {
                boolean shifted = (mods & GhosttyKeys.MOD_SHIFT) != 0;
                String text = shifted ? row.shiftText : row.text;
                int consumed = shifted && text != null && !text.equals(row.text) ? GhosttyKeys.MOD_SHIFT : 0;
                int[] actions = row.allActions
                    ? new int[] {GhosttyKeys.ACTION_PRESS, GhosttyKeys.ACTION_RELEASE, GhosttyKeys.ACTION_REPEAT}
                    : new int[] {GhosttyKeys.ACTION_PRESS};
                for (int action : actions) {
                    mOutput.baos.reset();
                    int written = emulator.sendLibraryKey(row.key, action, mods, consumed, row.unshifted, text, false);
                    byte[] expected = reference.encodeKey(row.key, action, mods, consumed, row.unshifted,
                        text == null ? null : text.getBytes(StandardCharsets.UTF_8), false);
                    assertNotNull(expected);
                    String label = state + " key=" + row.name + " mods=" + mods + " action=" + action;
                    assertEquals(label + " count", expected.length, written);
                    assertEquals(label, StandardMouseGoldenTest.printable(expected),
                        StandardMouseGoldenTest.printable(mOutput.baos.toByteArray()));
                    if (expected.length > 0) nonEmpty++;
                }
            }
        }
        return nonEmpty;
    }

    public void testKeyBytesMatchTheLibraryEncoder() {
        if (!hostLibrary()) return;
        int nonEmpty = 0;
        for (int flags : KITTY_FLAG_SETS) {
            for (boolean cursorKeys : new boolean[] {false, true}) {
                for (boolean keypad : new boolean[] {false, true}) {
                    TerminalEmulator emulator = fastEmulator(80, 24);
                    GhosttyCore reference = referenceCore(80, 24);
                    assertTrue(emulator.usesLibraryInputEncoders());
                    feed(emulator, reference, (flags == 0 ? "" : "\033[>" + flags + "u")
                        + (cursorKeys ? "\033[?1h" : "") + (keypad ? "\033=" : ""));
                    nonEmpty += checkKeys(emulator, reference,
                        "flags=" + flags + " decckm=" + cursorKeys + " deckpam=" + keypad);
                    reference.release();
                }
            }
        }
        assertTrue("the table should produce bytes", nonEmpty > 1000);
    }

    public void testKeyBytesOnTheAlternateScreenUseItsOwnKittyFlags() {
        if (!hostLibrary()) return;
        TerminalEmulator emulator = fastEmulator(80, 24);
        GhosttyCore reference = referenceCore(80, 24);
        // The flags are pushed after the switch, so only the alternate screen holds them.
        feed(emulator, reference, "\033[?1049h\033[>5u");
        int alt = checkKeys(emulator, reference, "alt flags=5");
        feed(emulator, reference, "\033[?1049l");
        int primary = checkKeys(emulator, reference, "primary after alt");
        reference.release();
        assertTrue(alt > 0 && primary > 0);
        // Escape alone tells the screens apart: CSI 27 u under the disambiguate flag, a bare ESC without.
        TerminalEmulator second = fastEmulator(80, 24);
        enterStringOn(second, "\033[?1049h\033[>1u");
        mOutput.baos.reset();
        second.sendLibraryKey(GhosttyKeys.KEY_ESCAPE, GhosttyKeys.ACTION_PRESS, 0, 0, 0, null, false);
        assertEquals("\033[27u", mOutput.getOutputAndClear());
        enterStringOn(second, "\033[?1049l");
        second.sendLibraryKey(GhosttyKeys.KEY_ESCAPE, GhosttyKeys.ACTION_PRESS, 0, 0, 0, null, false);
        assertEquals("\033", mOutput.getOutputAndClear());
    }

    public void testKnownKeysGiveTheUsualBytes() {
        if (!hostLibrary()) return;
        TerminalEmulator emulator = fastEmulator(80, 24);
        assertEquals(1, emulator.sendLibraryKey(GhosttyKeys.KEY_ENTER, GhosttyKeys.ACTION_PRESS, 0, 0, 0, null, false));
        assertEquals("\r", mOutput.getOutputAndClear());
        emulator.sendLibraryKey(GhosttyKeys.KEY_ARROW_UP, GhosttyKeys.ACTION_PRESS, 0, 0, 0, null, false);
        assertEquals("\033[A", mOutput.getOutputAndClear());
        enterStringOn(emulator, "\033[?1h");
        emulator.sendLibraryKey(GhosttyKeys.KEY_ARROW_UP, GhosttyKeys.ACTION_PRESS, 0, 0, 0, null, false);
        assertEquals("\033OA", mOutput.getOutputAndClear());
        // A lone C0 character as text is dropped; the key still encodes.
        emulator.sendLibraryKey(GhosttyKeys.KEY_ENTER, GhosttyKeys.ACTION_PRESS, 0, 0, 0, "\r", false);
        assertEquals("\r", mOutput.getOutputAndClear());
        emulator.sendLibraryKey(GhosttyKeys.KEY_A, GhosttyKeys.ACTION_PRESS, 0, 0, 'a', "a", false);
        assertEquals("a", mOutput.getOutputAndClear());
    }

    private static void enterStringOn(TerminalEmulator emulator, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    // ---- mouse ------------------------------------------------------------------------------

    private static int libraryButton(int mouseButton) {
        switch (mouseButton) {
            case TerminalEmulator.MOUSE_LEFT_BUTTON:
            case TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED: return 1;
            case 1: return 3;
            case 2: return 2;
            case TerminalEmulator.MOUSE_MOVED_NO_BUTTON: return 0;
            case TerminalEmulator.MOUSE_WHEELUP_BUTTON: return 4;
            case TerminalEmulator.MOUSE_WHEELDOWN_BUTTON: return 5;
            case TerminalEmulator.MOUSE_WHEEL_LEFT: return 6;
            default: return 7;
        }
    }

    private static boolean isWheel(int mouseButton) {
        return mouseButton >= TerminalEmulator.MOUSE_WHEELUP_BUTTON;
    }

    private static int mouseMods(int modifiers) {
        int mods = 0;
        if ((modifiers & TerminalEmulator.MOUSE_MODIFIER_SHIFT) != 0) mods |= GhosttyKeys.MOD_SHIFT;
        if ((modifiers & TerminalEmulator.MOUSE_MODIFIER_ALT) != 0) mods |= GhosttyKeys.MOD_ALT;
        if ((modifiers & TerminalEmulator.MOUSE_MODIFIER_CTRL) != 0) mods |= GhosttyKeys.MOD_CTRL;
        return mods;
    }

    public void testMouseBytesMatchTheLibraryEncoder() {
        if (!hostLibrary()) return;
        int columns = StandardMouseGoldenTest.COLUMNS, rows = StandardMouseGoldenTest.ROWS;
        int nonEmpty = 0;
        for (int tracking : StandardMouseGoldenTest.TRACKINGS_ALL) {
            for (int[] format : StandardMouseGoldenTest.FORMATS_ALL) {
                TerminalEmulator emulator = fastEmulator(columns, rows);
                GhosttyCore reference = referenceCore(columns, rows);
                StringBuilder modes = new StringBuilder();
                if (tracking != 0) modes.append("\033[?").append(tracking).append('h');
                for (int mode : format) modes.append("\033[?").append(mode).append('h');
                feed(emulator, reference, modes.toString());
                boolean down = false;
                for (int button : StandardMouseGoldenTest.BUTTONS) {
                    for (boolean pressed : new boolean[] {true, false}) {
                        for (int modifiers : StandardMouseGoldenTest.MODIFIERS) {
                            for (int pos = 0; pos < StandardMouseGoldenTest.POSITIONS.length; pos++) {
                                int[] p = StandardMouseGoldenTest.POSITIONS[pos];
                                mOutput.baos.reset();
                                emulator.sendMouseEvent(button, p[0], p[1], p[2], p[3], pressed, modifiers);
                                byte[] expected;
                                if (isWheel(button) && !pressed) {
                                    expected = new byte[0];
                                } else {
                                    boolean motion = button == TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED
                                        || button == TerminalEmulator.MOUSE_MOVED_NO_BUTTON;
                                    int action = motion ? 2 : pressed ? 0 : 1;
                                    // One button is held at a time here: down on a down row, gone after the
                                    // up row. Wheels and motion without a button hold none; a drag holds left.
                                    boolean any = button == TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED
                                        || (button <= 2 && pressed);
                                    int x = Math.max(0, Math.min(columns * CW - 1, p[2]));
                                    int y = Math.max(0, Math.min(rows * CH - 1, p[3]));
                                    expected = reference.encodeMouse(action, libraryButton(button),
                                        mouseMods(modifiers), x, y, columns * CW, rows * CH, CW, CH, any);
                                }
                                assertNotNull(expected);
                                String label = StandardMouseGoldenTest.label(tracking, format, button, pressed,
                                    modifiers, pos);
                                assertEquals(label, StandardMouseGoldenTest.printable(expected),
                                    StandardMouseGoldenTest.printable(mOutput.baos.toByteArray()));
                                if (expected.length > 0) nonEmpty++;
                            }
                        }
                    }
                }
                reference.release();
            }
        }
        assertTrue("the table should produce bytes", nonEmpty > 1000);
    }

    public void testMotionInsideOneCellIsReportedOnce() {
        if (!hostLibrary()) return;
        TerminalEmulator emulator = fastEmulator(80, 24);
        enterStringOn(emulator, "\033[?1003h\033[?1006h");
        mOutput.baos.reset();
        emulator.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 5, 3, 4 * CW + 1, 2 * CH + 1, true, 0);
        emulator.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 5, 3, 4 * CW + 5, 2 * CH + 4, true, 0);
        assertEquals("\033[<35;5;3M", mOutput.getOutputAndClear());
        emulator.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 6, 3, 5 * CW + 1, 2 * CH + 1, true, 0);
        assertEquals("\033[<35;6;3M", mOutput.getOutputAndClear());
    }

    // ---- focus ------------------------------------------------------------------------------

    public void testFocusReportsFollowMode1004() {
        if (!hostLibrary()) return;
        TerminalEmulator emulator = fastEmulator(80, 24);
        emulator.onFocusChanged(true);
        emulator.onFocusChanged(false);
        assertEquals("", mOutput.getOutputAndClear());
        enterStringOn(emulator, "\033[?1004h");
        emulator.onFocusChanged(true);
        assertEquals("\033[I", mOutput.getOutputAndClear());
        emulator.onFocusChanged(false);
        assertEquals("\033[O", mOutput.getOutputAndClear());
        assertEquals("\033[I", new String(GhosttyCore.encodeFocus(true), StandardCharsets.UTF_8));
        assertEquals("\033[O", new String(GhosttyCore.encodeFocus(false), StandardCharsets.UTF_8));
        enterStringOn(emulator, "\033[?1004l");
        emulator.onFocusChanged(true);
        assertEquals("", mOutput.getOutputAndClear());
    }

    // ---- the property -----------------------------------------------------------------------

    public void testPropertyJavaKeepsTheJavaEncodersOnFast() {
        if (!hostLibrary()) return;
        TerminalEmulator.setInputPropertyReaderForTests(key -> "java");
        int columns = StandardMouseGoldenTest.COLUMNS, rows = StandardMouseGoldenTest.ROWS;
        TerminalEmulator fast = fastEmulator(columns, rows);
        assertTrue(fast.isGhosttyCoreActive());
        assertFalse(fast.usesLibraryInputEncoders());
        assertEquals(-1, fast.sendLibraryKey(GhosttyKeys.KEY_A, GhosttyKeys.ACTION_PRESS, 0, 0, 'a', "a", false));
        assertEquals("", mOutput.getOutputAndClear());
        int compared = 0;
        for (int tracking : StandardMouseGoldenTest.TRACKINGS_JAVA) {
            for (int[] format : StandardMouseGoldenTest.FORMATS_JAVA) {
                MockTerminalOutput standardOut = new MockTerminalOutput();
                TerminalEmulator standard = StandardMouseGoldenTest.newEmulator(standardOut, tracking, format);
                assertFalse(standard.isGhosttyCoreActive());
                TerminalEmulator onFast = fastEmulator(columns, rows);
                StandardMouseGoldenTest.configure(onFast, tracking, format);
                mOutput.baos.reset();
                for (int button : StandardMouseGoldenTest.BUTTONS) {
                    for (boolean pressed : new boolean[] {true, false}) {
                        for (int modifiers : StandardMouseGoldenTest.MODIFIERS) {
                            for (int pos = 0; pos < StandardMouseGoldenTest.POSITIONS.length; pos++) {
                                byte[] expected = StandardMouseGoldenTest.send(standardOut, standard, button, pressed,
                                    modifiers, pos);
                                byte[] actual = StandardMouseGoldenTest.send(mOutput, onFast, button, pressed,
                                    modifiers, pos);
                                assertTrue(StandardMouseGoldenTest.label(tracking, format, button, pressed, modifiers, pos)
                                    + " standard=" + StandardMouseGoldenTest.printable(expected)
                                    + " fast=" + StandardMouseGoldenTest.printable(actual), Arrays.equals(expected, actual));
                                compared++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(2160, compared);
        // Focus keeps the literal too.
        mOutput.baos.reset();
        enterStringOn(fast, "\033[?1004h");
        fast.onFocusChanged(true);
        assertEquals("\033[I", mOutput.getOutputAndClear());
    }

    public void testStandardEmulatorHasNoLibraryEncoders() {
        TerminalEmulator standard = new TerminalEmulator(mOutput, false, 80, 24, CW, CH, 48, null);
        assertFalse(standard.usesLibraryInputEncoders());
        assertEquals(-1, standard.sendLibraryKey(GhosttyKeys.KEY_A, GhosttyKeys.ACTION_PRESS, 0, 0, 'a', "a", false));
        assertEquals("", mOutput.getOutputAndClear());
    }
}
