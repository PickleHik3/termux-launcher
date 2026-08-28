package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.KeyValue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FocuslessKeyIntakeTest {

    @Test
    public void nothingIsClaimedWhileInactive() {
        Recorder intake = new Recorder();
        assertFalse(intake.interceptKeyValue(KeyValue.makeCharKey('a'), false, false, false));
        assertFalse(intake.handleKeyDown(KeyEvent.KEYCODE_A, down(KeyEvent.KEYCODE_A, 0)));
        assertTrue(intake.edits.isEmpty());
    }

    @Test
    public void inAppKeysReduceToEdits() {
        Recorder intake = new Recorder();
        intake.active = true;
        assertTrue(intake.interceptKeyValue(KeyValue.makeCharKey('a'), false, false, false));
        assertTrue(intake.interceptKeyValue(KeyValue.makeStringKey("bc"), true, false, false));
        assertTrue(intake.interceptKeyValue(KeyValue.getKeyByName("space"), false, false, false));
        assertTrue(intake.interceptKeyValue(KeyValue.getKeyByName("backspace"), false, false, false));
        assertTrue(intake.interceptKeyValue(KeyValue.getKeyByName("action"), false, false, false));
        assertTrue(intake.interceptKeyValue(
            KeyValue.sliderKey(KeyValue.Slider.Cursor_left, 3), false, false, false));
        assertTrue(intake.interceptKeyValue(
            KeyValue.sliderKey(KeyValue.Slider.Cursor_right, 0), false, false, false));
        assertTrue(intake.interceptKeyValue(
            KeyValue.keyeventKey("home", KeyEvent.KEYCODE_MOVE_HOME, 0), false, false, false));
        // A value the editor has no use for is still swallowed rather than typed into the shell.
        assertTrue(intake.interceptKeyValue(
            KeyValue.makeInternalModifier(KeyValue.Modifier.FN), false, false, false));
        assertEquals(List.of("text a", "text bc ctrl", "text  ", "backspace", "commit",
            "cursor -3", "cursor 1", "key " + KeyEvent.KEYCODE_MOVE_HOME), intake.edits);
    }

    @Test
    public void hardwareChordsReportTheLetterBesideItsModifiers() {
        Recorder intake = new Recorder();
        intake.active = true;
        assertTrue(intake.handleKeyDown(KeyEvent.KEYCODE_V,
            down(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)));
        assertTrue(intake.handleKeyDown(KeyEvent.KEYCODE_V,
            down(KeyEvent.KEYCODE_V, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON)));
        assertTrue(intake.handleKeyDown(KeyEvent.KEYCODE_DEL, down(KeyEvent.KEYCODE_DEL, 0)));
        // Key-ups are claimed without becoming edits.
        assertTrue(intake.handleKeyDown(KeyEvent.KEYCODE_A,
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)));
        assertEquals(List.of("text v ctrl", "text V", "key " + KeyEvent.KEYCODE_DEL), intake.edits);
    }

    private static KeyEvent down(int keyCode, int metaState) {
        return new KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
    }

    private static final class Recorder extends FocuslessKeyIntake {
        boolean active;
        final List<String> edits = new ArrayList<>();

        @Override public boolean isActive() { return active; }

        @Override public boolean handleCodePoint(int codePoint, boolean ctrlDown) { return active; }

        @Override protected void onText(@NonNull String text, boolean ctrl, boolean alt) {
            edits.add("text " + text + (ctrl ? " ctrl" : "") + (alt ? " alt" : ""));
        }

        @Override protected void onBackspace() { edits.add("backspace"); }
        @Override protected void onCommit() { edits.add("commit"); }
        @Override protected void onCursor(int delta) { edits.add("cursor " + delta); }

        @Override protected boolean handleKeyCode(int keyCode) {
            if (keyCode != KeyEvent.KEYCODE_DEL && keyCode != KeyEvent.KEYCODE_MOVE_HOME) return false;
            edits.add("key " + keyCode);
            return true;
        }
    }
}
