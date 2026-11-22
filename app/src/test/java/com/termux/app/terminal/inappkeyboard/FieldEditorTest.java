package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClipboardText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.KeyValue;

/** The editing actions on a focusless field: whole-field copy and cut, select all, undo and redo. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FieldEditorTest {

    @Test public void copyAndCutTakeTheWholeFieldAndPasteInsertsOneLine() {
        Clip clip = new Clip();
        Field field = new Field("grep");
        FieldEditor editor = new FieldEditor(clip);

        assertTrue(editor.handle(field, key("copy"), false, false, false));
        assertEquals("grep", field.text);
        assertEquals("grep", clip.last());

        assertTrue(editor.handle(field, key("cut"), false, false, false));
        assertEquals("", field.text);
        assertEquals(2, clip.written.size());

        clip.text = "a\r\nb";
        assertTrue(editor.handle(field, key("paste"), false, false, false));
        assertEquals("a b", field.text);
    }

    @Test public void selectAllMakesTheNextStrokeReplaceOrDeleteTheWholeField() {
        Field field = new Field("old");
        FieldEditor editor = new FieldEditor(new Clip());

        editor.handle(field, key("selectAll"), false, false, false);
        assertTrue(editor.isAllSelected());
        // The character is the field's own to type, onto the emptied field.
        assertFalse(editor.handle(field, KeyValue.makeCharKey('n'), false, false, false));
        assertEquals("", field.text);
        assertFalse(editor.isAllSelected());

        field.text = "old";
        editor.handle(field, key("selectAll"), false, false, false);
        // A delete is spent on the selection.
        assertTrue(editor.handle(field, key("backspace"), false, false, false));
        assertEquals("", field.text);

        field.text = "old";
        editor.handle(field, key("selectAll"), false, false, false);
        // A caret move drops the selection and leaves the text.
        assertFalse(editor.handle(field, KeyValue.sliderKey(KeyValue.Slider.Cursor_left, 1),
            false, false, false));
        assertEquals("old", field.text);
        assertFalse(editor.isAllSelected());
    }

    @Test public void undoAndRedoStepThroughTheTextAsItStoodBeforeEachChange() {
        Field field = new Field("a");
        FieldEditor editor = new FieldEditor(new Clip());

        type(editor, field, "b");
        type(editor, field, "c");
        assertEquals("abc", field.text);

        assertTrue(editor.handle(field, key("undo"), false, false, false));
        assertEquals("ab", field.text);
        editor.handle(field, KeyValue.makeCharKey('z'), true, false, false);
        assertEquals("a", field.text);
        editor.handle(field, key("undo"), false, false, false);
        assertEquals("a", field.text);

        editor.handle(field, key("redo"), false, false, false);
        assertEquals("ab", field.text);
        editor.handle(field, KeyValue.makeCharKey('Z'), true, false, true);
        assertEquals("abc", field.text);

        editor.handle(field, KeyValue.makeCharKey('z'), true, false, false);
        type(editor, field, "x");
        // A new change after an undo ends the redo trail.
        editor.handle(field, KeyValue.makeCharKey('y'), true, false, false);
        assertEquals("abx", field.text);

        editor.reset();
        editor.handle(field, key("undo"), false, false, false);
        assertEquals("abx", field.text);
    }

    @Test public void unmappedChordsAndToolKeysAreSpentOnNothing() {
        Clip clip = new Clip();
        Field field = new Field("ab");
        FieldEditor editor = new FieldEditor(clip);

        assertTrue(editor.handle(field, KeyValue.makeCharKey('q'), true, false, false));
        assertTrue(editor.handle(field, KeyValue.makeCharKey('c'), false, true, false));
        assertTrue(editor.handle(field,
            KeyValue.makeLauncherToolKey("app.command_palette", "⌘", 0), false, false, false));
        assertEquals("ab", field.text);
        assertTrue(clip.written.isEmpty());
    }

    @Test public void aFieldWhoseTextIsOutOfReachStillPastes() {
        Clip clip = new Clip();
        clip.text = "x";
        StringBuilder typed = new StringBuilder();
        FieldEditor.Field sheet = new FieldEditor.Field() {
            @Nullable @Override public String fieldText() { return null; }
            @Override public void replaceFieldText(@NonNull String text) {}
            @Override public void insertFieldText(@NonNull String text) { typed.append(text); }
        };
        FieldEditor editor = new FieldEditor(clip);

        editor.handle(sheet, KeyValue.makeCharKey('v'), true, false, false);
        editor.handle(sheet, key("copy"), false, false, false);
        editor.handle(sheet, key("undo"), false, false, false);
        assertEquals("x", typed.toString());
        assertTrue(clip.written.isEmpty());
    }

    private static void type(FieldEditor editor, Field field, String text) {
        KeyValue value = KeyValue.makeStringKey(text);
        if (!editor.handle(field, value, false, false, false)) field.insertFieldText(text);
    }

    private static KeyValue key(String name) {
        return KeyValue.getKeyByName(name);
    }

    static final class Clip implements ClipboardText {
        @Nullable String text;
        final List<String> written = new ArrayList<>();

        @Nullable @Override public String read() { return text; }

        @Override public void write(@NonNull String value) { written.add(value); }

        String last() { return written.get(written.size() - 1); }
    }

    private static final class Field implements FieldEditor.Field {
        String text;

        Field(String text) { this.text = text; }

        @Override public String fieldText() { return text; }

        @Override public void replaceFieldText(@NonNull String value) { text = value; }

        @Override public void insertFieldText(@NonNull String value) { text = text + value; }
    }
}
