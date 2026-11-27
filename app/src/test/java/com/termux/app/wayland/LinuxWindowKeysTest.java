package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import juloo.keyboard2.KeyValue;

/** What the launcher's keyboard presses in a Linux window for each value it produces. */
public class LinuxWindowKeysTest {

    private static final class Recorder implements LinuxWindowKeys.Sink {
        final List<String> presses = new ArrayList<>();

        @Override
        public void key(int keyCode, boolean down) {
            presses.add((down ? "+" : "-") + keyCode);
        }
    }

    private static List<String> type(KeyValue value, boolean ctrl, boolean alt, boolean shift,
                                     boolean meta) {
        Recorder r = new Recorder();
        LinuxWindowKeys.send(value, ctrl, alt, shift, meta, r);
        return r.presses;
    }

    private static List<String> type(KeyValue value) {
        return type(value, false, false, false, false);
    }

    private static List<String> tap(int code) {
        return Arrays.asList("+" + code, "-" + code);
    }

    private static List<String> around(int modifier, List<String> inner) {
        List<String> out = new ArrayList<>();
        out.add("+" + modifier);
        out.addAll(inner);
        out.add("-" + modifier);
        return out;
    }

    @Test
    public void aLetterIsItsKey() {
        assertEquals(tap(KeyEvent.KEYCODE_A), type(KeyValue.makeCharKey('a')));
    }

    @Test
    public void aCapitalHoldsShift() {
        assertEquals(around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_A)),
            type(KeyValue.makeCharKey('A'), false, false, true, false));
    }

    @Test
    public void aShiftedSymbolHoldsShiftOnItsUsKey() {
        assertEquals(around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_1)),
            type(KeyValue.makeCharKey('!')));
        assertEquals(around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_SLASH)),
            type(KeyValue.makeCharKey('?')));
    }

    @Test
    public void theKeyboardsShiftDoesNotShiftAnUnshiftedCharacterTwice() {
        // Shift held on a key whose shifted value is itself: ',' must not become '<'.
        assertEquals(tap(KeyEvent.KEYCODE_COMMA),
            type(KeyValue.makeCharKey(','), false, false, true, false));
    }

    @Test
    public void ctrlIsPressedAroundTheKey() {
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT, tap(KeyEvent.KEYCODE_C)),
            type(KeyValue.makeCharKey('c'), true, false, false, false));
    }

    @Test
    public void ctrlShiftIsAFullChord() {
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT,
                around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_C))),
            type(KeyValue.makeCharKey('C'), true, false, true, false));
    }

    @Test
    public void altAndMetaArePressedAroundTheKey() {
        assertEquals(around(KeyEvent.KEYCODE_ALT_LEFT, tap(KeyEvent.KEYCODE_F)),
            type(KeyValue.makeCharKey('f'), false, true, false, false));
        assertEquals(around(KeyEvent.KEYCODE_META_LEFT, tap(KeyEvent.KEYCODE_D)),
            type(KeyValue.makeCharKey('d'), false, false, false, true));
    }

    @Test
    public void namedKeysPassThrough() {
        assertEquals(tap(KeyEvent.KEYCODE_ESCAPE), type(KeyValue.getKeyByName("esc")));
        assertEquals(tap(KeyEvent.KEYCODE_TAB), type(KeyValue.getKeyByName("tab")));
        assertEquals(tap(KeyEvent.KEYCODE_DPAD_LEFT), type(KeyValue.getKeyByName("left")));
        assertEquals(tap(KeyEvent.KEYCODE_MOVE_HOME), type(KeyValue.getKeyByName("home")));
        assertEquals(tap(KeyEvent.KEYCODE_PAGE_DOWN), type(KeyValue.getKeyByName("page_down")));
        assertEquals(tap(KeyEvent.KEYCODE_F1), type(KeyValue.getKeyByName("f1")));
        assertEquals(tap(KeyEvent.KEYCODE_F12), type(KeyValue.getKeyByName("f12")));
        assertEquals(tap(KeyEvent.KEYCODE_ENTER), type(KeyValue.getKeyByName("enter")));
    }

    @Test
    public void shiftRidesAlongOnANamedKey() {
        assertEquals(around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_DPAD_RIGHT)),
            type(KeyValue.getKeyByName("right"), false, false, true, false));
    }

    @Test
    public void backspaceSpaceAndTheActionKey() {
        assertEquals(tap(KeyEvent.KEYCODE_DEL), type(KeyValue.getKeyByName("backspace")));
        assertEquals(tap(KeyEvent.KEYCODE_SPACE), type(KeyValue.getKeyByName("space")));
        assertEquals(tap(KeyEvent.KEYCODE_ENTER), type(KeyValue.getKeyByName("action")));
    }

    @Test
    public void editKeysAreDesktopChords() {
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT, tap(KeyEvent.KEYCODE_V)),
            type(KeyValue.getKeyByName("paste")));
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT, tap(KeyEvent.KEYCODE_C)),
            type(KeyValue.getKeyByName("copy")));
    }

    @Test
    public void aStringTypesEachCharacter() {
        List<String> expected = new ArrayList<>(tap(KeyEvent.KEYCODE_L));
        expected.addAll(tap(KeyEvent.KEYCODE_S));
        assertEquals(expected, type(KeyValue.makeStringKey("ls")));
    }

    @Test
    public void charactersWithNoUsKeyAreDropped() {
        assertEquals(Collections.emptyList(), type(KeyValue.makeCharKey('é')));
        assertEquals(Collections.emptyList(), type(KeyValue.makeStringKey("😀")));
        // The ASCII around an emoji still types.
        assertEquals(tap(KeyEvent.KEYCODE_A), type(KeyValue.makeStringKey("😀a")));
    }

    @Test
    public void aSliderTickIsAnArrowPerStep() {
        List<String> expected = new ArrayList<>(tap(KeyEvent.KEYCODE_DPAD_RIGHT));
        expected.addAll(tap(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(expected, type(KeyValue.sliderKey(KeyValue.Slider.Cursor_right, 2)));
        assertEquals(tap(KeyEvent.KEYCODE_DPAD_LEFT),
            type(KeyValue.sliderKey(KeyValue.Slider.Cursor_right, -1)));
    }

    @Test
    public void theKeyboardsOwnKeysTypeNothing() {
        assertEquals(Collections.emptyList(), type(KeyValue.getKeyByName("switch_numeric")));
        assertEquals(Collections.emptyList(), type(KeyValue.getKeyByName("config")));
        assertEquals(Collections.emptyList(), type(KeyValue.getKeyByName("ctrl")));
        assertEquals(Collections.emptyList(), type(null));
    }

    @Test
    public void onlyKeysTheHostMapsAreForwarded() {
        assertTrue(LinuxWindowKeys.isForwarded(KeyEvent.KEYCODE_CTRL_LEFT));
        assertTrue(LinuxWindowKeys.isForwarded(KeyEvent.KEYCODE_SHIFT_LEFT));
        assertTrue(LinuxWindowKeys.isForwarded(KeyEvent.KEYCODE_META_LEFT));
        assertFalse(LinuxWindowKeys.isForwarded(KeyEvent.KEYCODE_BACK));
        assertFalse(LinuxWindowKeys.isForwarded(KeyEvent.KEYCODE_VOLUME_UP));
    }

    @Test
    public void inATextFieldCharactersAreText() {
        assertEquals("a", LinuxWindowKeys.typedText(KeyValue.makeCharKey('a'), false, false));
        assertEquals("é", LinuxWindowKeys.typedText(KeyValue.makeCharKey('é'), false, false));
        assertEquals("😀", LinuxWindowKeys.typedText(KeyValue.makeStringKey("😀"), false, false));
        assertEquals("ls", LinuxWindowKeys.typedText(KeyValue.makeStringKey("ls"), false, false));
    }

    @Test
    public void inATextFieldChordsAndKeysStayKeys() {
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.makeCharKey('c'), true, false));
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.makeCharKey('f'), false, true));
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.getKeyByName("backspace"), false, false));
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.getKeyByName("enter"), false, false));
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.getKeyByName("left"), false, false));
        assertEquals(null, LinuxWindowKeys.typedText(KeyValue.makeCharKey('\t'), false, false));
        assertEquals(null, LinuxWindowKeys.typedText(null, false, false));
    }

    @Test
    public void pasteFollowsTheAppsPasteShortcut() {
        Recorder plain = new Recorder();
        LinuxWindowKeys.send(KeyValue.getKeyByName("paste"), false, false, false, false, false, plain);
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT, tap(KeyEvent.KEYCODE_V)), plain.presses);

        Recorder shifted = new Recorder();
        LinuxWindowKeys.send(KeyValue.getKeyByName("paste"), false, false, false, false, true, shifted);
        assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT,
            around(KeyEvent.KEYCODE_SHIFT_LEFT, tap(KeyEvent.KEYCODE_V))), shifted.presses);
    }

    @Test
    public void copyCutAndSelectAllAreTheAppsChordsWhateverPasteIs() {
        for (String[] pair : new String[][] {{"copy", "" + KeyEvent.KEYCODE_C},
                {"cut", "" + KeyEvent.KEYCODE_X}, {"selectAll", "" + KeyEvent.KEYCODE_A}}) {
            Recorder r = new Recorder();
            LinuxWindowKeys.send(KeyValue.getKeyByName(pair[0]), false, false, false, false, true, r);
            assertEquals(around(KeyEvent.KEYCODE_CTRL_LEFT, tap(Integer.parseInt(pair[1]))), r.presses);
        }
    }
}
