package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import juloo.keyboard2.KeyValue;

/** The one rule for what an in-app keyboard key means while a launcher text field has it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FieldKeysTest {

    /** Every event is placed on purpose: one added upstream fails here rather than falling through. */
    @Test public void everyEventIsClassifiedExplicitly() {
        for (KeyValue.Event event : KeyValue.Event.values()) {
            boolean owned = FieldKeys.isKeyboardOwned(event);
            if (event == KeyValue.Event.ACTION) {
                assertFalse("the action key is the field's commit", owned);
                assertEquals(FieldKeys.Kind.CONTENT, FieldKeys.classify(event(event), false, false));
            } else {
                assertTrue(event + " acts on the keyboard", owned);
                assertEquals(FieldKeys.Kind.KEYBOARD_OWNED,
                    FieldKeys.classify(event(event), false, false));
            }
        }
    }

    @Test public void keyboardOwnedMatchesWhatTheDisplayPassesOnLessTheToolKeys() {
        assertTrue(FieldKeys.isKeyboardOwned(KeyValue.getKeyByName("config")));
        assertTrue(FieldKeys.isKeyboardOwned(KeyValue.getKeyByName("switch_clipboard")));
        assertTrue(FieldKeys.isKeyboardOwned(KeyValue.makeMacro("m", new KeyValue[] {
            KeyValue.makeCharKey('a') }, 0)));
        assertFalse(FieldKeys.isKeyboardOwned(KeyValue.makeLauncherToolKey("app.command_palette",
            "⌘", 0)));
        assertFalse(FieldKeys.isKeyboardOwned(KeyValue.makeCharKey('a')));
    }

    @Test public void kindsOfTheOtherValues() {
        assertEquals(FieldKeys.Kind.TOOL, FieldKeys.classify(
            KeyValue.makeLauncherToolKey("window.next", "»", 0), false, false));
        for (String name : new String[] {"copy", "cut", "paste", "pasteAsPlainText", "selectAll",
            "undo", "redo"}) {
            assertEquals(name, FieldKeys.Kind.EDITING,
                FieldKeys.classify(KeyValue.getKeyByName(name), false, false));
        }
        assertEquals(FieldKeys.Kind.CONTENT,
            FieldKeys.classify(KeyValue.getKeyByName("space"), false, false));
        assertEquals(FieldKeys.Kind.CONTENT,
            FieldKeys.classify(KeyValue.getKeyByName("backspace"), false, false));
        assertEquals(FieldKeys.Kind.CONTENT,
            FieldKeys.classify(KeyValue.makeCharKey('q'), false, false));
        assertEquals(FieldKeys.Kind.CHORD,
            FieldKeys.classify(KeyValue.makeCharKey('q'), true, false));
        assertEquals(FieldKeys.Kind.CHORD,
            FieldKeys.classify(KeyValue.makeCharKey('q'), false, true));
        // A named key under Ctrl stays the field's own (arrows keep each field's meaning).
        assertEquals(FieldKeys.Kind.CONTENT, FieldKeys.classify(
            KeyValue.keyeventKey("left", KeyEvent.KEYCODE_DPAD_LEFT, 0), true, false));
    }

    @Test public void ctrlLettersAreTheStandardEditingShortcuts() {
        assertEquals(FieldKeys.Edit.SELECT_ALL, FieldKeys.chordEdit('a', true, false, false));
        assertEquals(FieldKeys.Edit.COPY, FieldKeys.chordEdit('c', true, false, false));
        assertEquals(FieldKeys.Edit.CUT, FieldKeys.chordEdit('x', true, false, false));
        assertEquals(FieldKeys.Edit.PASTE, FieldKeys.chordEdit('v', true, false, false));
        assertEquals(FieldKeys.Edit.UNDO, FieldKeys.chordEdit('z', true, false, false));
        assertEquals(FieldKeys.Edit.REDO, FieldKeys.chordEdit('y', true, false, false));
        assertEquals(FieldKeys.Edit.REDO, FieldKeys.chordEdit('Z', true, false, true));
        assertEquals(FieldKeys.Edit.COPY, FieldKeys.chordEdit('C', true, false, true));

        assertEquals(FieldKeys.Edit.NONE, FieldKeys.chordEdit('q', true, false, false));
        assertEquals(FieldKeys.Edit.NONE, FieldKeys.chordEdit('c', true, true, false));
        assertEquals(FieldKeys.Edit.NONE, FieldKeys.chordEdit('c', false, true, false));
        assertEquals(FieldKeys.Edit.NONE, FieldKeys.chordEdit('c', false, false, false));
    }

    @Test public void editKeysMapToTheirActionsAndTheRestAreContent() {
        assertEquals(FieldKeys.Edit.PASTE_PLAIN,
            FieldKeys.editFor(KeyValue.getKeyByName("pasteAsPlainText"), false, false, false));
        assertEquals(FieldKeys.Edit.UNDO,
            FieldKeys.editFor(KeyValue.makeStringKey("z"), true, false, false));
        for (KeyValue.Editing editing : KeyValue.Editing.values()) {
            FieldKeys.Edit edit = FieldKeys.editFor(editing);
            switch (editing) {
                case COPY: case CUT: case PASTE: case PASTE_PLAIN: case SELECT_ALL: case UNDO:
                case REDO:
                    assertTrue(editing.name(), edit != FieldKeys.Edit.NONE);
                    break;
                default:
                    assertEquals(editing.name(), FieldKeys.Edit.NONE, edit);
            }
        }
    }

    private static KeyValue event(KeyValue.Event event) {
        return new KeyValue(event.name(), KeyValue.Kind.Event, event.ordinal(), 0);
    }
}
