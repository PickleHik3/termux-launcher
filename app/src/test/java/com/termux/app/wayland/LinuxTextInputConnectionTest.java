package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** What the phone keyboard's edits become in a Linux app's text field. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxTextInputConnectionTest {

    private final List<String> sent = new ArrayList<>();
    private final LinuxTextInputConnection c = new LinuxTextInputConnection(
        new View(ApplicationProvider.getApplicationContext()), new LinuxTextInputConnection.Sink() {
            @Override public void commit(@NonNull String text) { sent.add("commit " + text); }
            @Override public void preedit(@NonNull String text, int cursor) { sent.add("preedit " + text + " " + cursor); }
            @Override public void delete(int before, int after) { sent.add("delete " + before + " " + after); }
            @Override public void key(int keyCode, boolean down) { sent.add((down ? "+" : "-") + keyCode); }
        });

    @Test
    public void typedTextIsCommitted() {
        assertTrue(c.commitText("héllo", 1));
        assertEquals(Arrays.asList("commit héllo"), sent);
    }

    @Test
    public void composingTextIsThePreeditAndFinishingKeepsIt() {
        c.setComposingText("ni", 1);
        c.setComposingText("nih", 1);
        c.finishComposingText();
        c.finishComposingText();
        assertEquals(Arrays.asList("preedit ni 2", "preedit nih 3", "commit nih"), sent);
    }

    @Test
    public void aCommitReplacesTheComposingText() {
        c.setComposingText("nih", 1);
        c.commitText("你", 1);
        c.finishComposingText();
        assertEquals(Arrays.asList("preedit nih 3", "commit 你"), sent);
    }

    @Test
    public void anEmptyCommitClearsThePreedit() {
        c.setComposingText("a", 1);
        c.commitText("", 1);
        assertEquals(Arrays.asList("preedit a 1", "preedit  -1"), sent);
    }

    @Test
    public void deletionsAreDeleteSurroundingText() {
        c.deleteSurroundingText(1, 0);
        c.deleteSurroundingText(0, 0);
        assertEquals(Arrays.asList("delete 1 0"), sent);
    }

    @Test
    public void keyEventsAreKeysAndTheActionIsEnter() {
        c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP));
        c.performEditorAction(0);
        assertEquals(Arrays.asList("+" + KeyEvent.KEYCODE_DEL, "-" + KeyEvent.KEYCODE_DEL,
            "+" + KeyEvent.KEYCODE_ENTER, "-" + KeyEvent.KEYCODE_ENTER), sent);
    }
}
